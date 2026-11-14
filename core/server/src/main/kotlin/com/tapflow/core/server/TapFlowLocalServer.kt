package com.tapflow.core.server

import android.graphics.Bitmap
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.engine.AutomationRunnerEngine
import com.tapflow.core.service.engine.RunnerControlHub
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.shizuku.ShizukuManager
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TapFlowLocalServer @Inject constructor(
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val entitlementRepository: EntitlementRepository,
  private val scriptRepository: ScriptRepository,
  private val automationRunnerEngine: AutomationRunnerEngine,
  private val runnerControlHub: RunnerControlHub,
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val visionEngine: ZeroAllocVisionEngine,
  private val shizukuManager: ShizukuManager,
) : WsServerDelegate {

  companion object {
    private const val LOCALHOST = "127.0.0.1"
    private const val LIVE_STREAM_INTERVAL_MS = 150L
    private const val LIVE_STREAM_MAX_LONG_SIDE_PX = 960
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val serverMutex = Mutex()
  private val json = TapFlowJson.instance

  private val _status = MutableStateFlow(ServerUiStatus())
  val status: StateFlow<ServerUiStatus> = _status.asStateFlow()

  private val clientSessions = ConcurrentHashMap<String, ClientConnection>()
  private var embeddedEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
  private var liveStreamJob: Job? = null
  private var runnerBroadcastJob: Job? = null

  private data class ClientConnection(
    val id: String,
    val session: DefaultWebSocketServerSession,
    @Volatile var wantsLiveStream: Boolean = false,
  )

  init {
    scope.launch {
      preferencesRepository.preferencesFlow.collectLatest { prefs ->
        val port = prefs.ktorPort
        _status.update { current ->
          if (current.isRunning) current
          else current.copy(port = port, adbCommand = buildAdbCommand(port))
        }
      }
    }

    scope.launch {
      entitlementRepository.entitlementState.collectLatest { state ->
        val hasPro = com.tapflow.core.model.entitlement.EntitlementValidator.canUseFeature(
          state,
          ProFeatureGate.DESKTOP_CONSOLE,
        )
        if (!hasPro && _status.value.isRunning) {
          stopServer("Desktop Studio server stopped: TapFlow Pro entitlement is no longer active.")
        }
      }
    }
  }

  override val connectedClients: Int
    get() = clientSessions.size

  override val currentRunnerPhaseName: String
    get() = runnerControlHub.snapshot.value.phase.name

  suspend fun startServer(requestedPort: Int? = null): Boolean =
    serverMutex.withLock {
      if (!entitlementRepository.canUseFeature(ProFeatureGate.DESKTOP_CONSOLE)) {
        _status.update {
          it.copy(
            isRunning = false,
            lastError = "TapFlow Pro (Trial or Lifetime) is required to start the Desktop Console server.",
          )
        }
        return@withLock false
      }

      if (embeddedEngine != null && _status.value.isRunning) {
        return@withLock true
      }

      val prefs = preferencesRepository.getPreferencesOnce()
      val port = (requestedPort ?: prefs.ktorPort).coerceIn(1024, 65535)
      if (requestedPort != null && port != prefs.ktorPort) {
        preferencesRepository.setKtorPort(port)
      }

      return@withLock runCatching {
        val engine =
          embeddedServer(CIO, port = port, host = LOCALHOST) {
            install(ContentNegotiation) {
              json(TapFlowJson.instance)
            }
            install(WebSockets) {
              pingPeriod = 15.seconds
              timeout = 30.seconds
              maxFrameSize = Long.MAX_VALUE
              masking = false
            }

            routing {
              get("/health") {
                val snap = _status.value
                call.respond(
                  ServerHealthResponse(
                    port = snap.port,
                    isRunning = snap.isRunning,
                    connectedClients = snap.connectedClientCount,
                  ),
                )
              }

              get("/scripts") {
                call.respond(scriptRepository.getAllScripts())
              }

              webSocket("/tapflow") {
                val clientId = UUID.randomUUID().toString()
                val connection = ClientConnection(clientId, this)
                clientSessions[clientId] = connection
                refreshClientCount()

                try {
                  val ackPayload =
                    json.encodeToString(
                      HandshakeAckPayload.serializer(),
                      HandshakeAckPayload(
                        port = port,
                        capturePermitted = isCapturePermitted(),
                      ),
                    )
                  sendEnvelope(
                    this,
                    WsMessageDispatcher.buildEnvelope(
                      type = WsMessageType.HANDSHAKE_ACK,
                      requestId = null,
                      payload = ackPayload,
                    ),
                  )

                  for (frame in incoming) {
                    if (frame is Frame.Text) {
                      val outcome =
                        WsMessageDispatcher.dispatchIncoming(
                          clientId = clientId,
                          rawText = frame.readText(),
                          delegate = this@TapFlowLocalServer,
                        )
                      outcome.directReply?.let { reply -> sendEnvelope(this, reply) }
                      outcome.broadcastMessage?.let { msg -> broadcastEnvelope(msg) }
                    }
                  }
                } finally {
                  clientSessions.remove(clientId)
                  refreshClientCount()
                  updateLiveStreamLoopLocked()
                }
              }
            }
          }

        engine.start(wait = false)
        embeddedEngine = engine
        startRunnerBroadcastObserver()
        _status.value =
          ServerUiStatus(
            isRunning = true,
            port = port,
            connectedClientCount = 0,
            adbCommand = buildAdbCommand(port),
            isLiveStreaming = false,
            lastError = null,
          )
        true
      }.getOrElse { err ->
        stopServerInternal(error = "Failed to start Ktor server on port $port: ${err.message}")
        false
      }
    }

  suspend fun stopServer(reason: String? = null) =
    serverMutex.withLock {
      stopServerInternal(error = reason)
    }

  suspend fun toggleServer(): Boolean {
    return if (_status.value.isRunning) {
      stopServer()
      false
    } else {
      startServer()
    }
  }

  suspend fun updatePort(port: Int) {
    val safePort = port.coerceIn(1024, 65535)
    preferencesRepository.setKtorPort(safePort)
    val wasRunning = _status.value.isRunning
    _status.update {
      it.copy(port = safePort, adbCommand = buildAdbCommand(safePort))
    }
    if (wasRunning) {
      stopServer()
      startServer(safePort)
    }
  }

  override suspend fun getAllScripts(): List<Script> = scriptRepository.getAllScripts()

  override suspend fun saveScript(script: Script) {
    scriptRepository.saveScript(script)
  }

  override suspend fun deleteScript(scriptId: String) {
    scriptRepository.deleteScript(scriptId)
  }

  override suspend fun setClientLiveStreaming(clientId: String, enabled: Boolean): Boolean {
    val conn = clientSessions[clientId] ?: return false
    if (!enabled) {
      conn.wantsLiveStream = false
      updateLiveStreamLoopLocked()
      return true
    }
    val permitted = isCapturePermitted()
    conn.wantsLiveStream = permitted
    updateLiveStreamLoopLocked()
    return permitted
  }

  override suspend fun startRemoteRun(scriptId: String): Result<Script> {
    if (!entitlementRepository.canUseFeature(ProFeatureGate.DESKTOP_CONSOLE)) {
      return Result.failure(IllegalStateException("TapFlow Pro is required for remote execution."))
    }
    val script = scriptRepository.getScriptById(scriptId)
      ?: return Result.failure(IllegalArgumentException("Script '$scriptId' not found."))
    val blockers = entitlementRepository.detectScriptProBlockers(script)
    if (blockers.isNotEmpty()) {
      return Result.failure(
        IllegalStateException("Pro features required: ${blockers.joinToString { it.displayName }}"),
      )
    }
    automationRunnerEngine.startScript(script)
    return Result.success(script)
  }

  override fun stopRemoteRun() {
    runnerControlHub.stopExecution()
  }

  private suspend fun isCapturePermitted(): Boolean {
    val prefs = preferencesRepository.getPreferencesOnce()
    return mediaProjectionBridge.isCaptureActive ||
      (prefs.shizuku.silentScreenshots && shizukuManager.diagnosticState.value.isFullyReady)
  }

  private fun startRunnerBroadcastObserver() {
    runnerBroadcastJob?.cancel()
    runnerBroadcastJob =
      scope.launch {
        launch {
          runnerControlHub.liveLogEvents.collect { actionLog ->
            if (clientSessions.isEmpty()) return@collect
            val snap = runnerControlHub.snapshot.value
            val payload =
              json.encodeToString(
                ExecutionLogEventPayload.serializer(),
                ExecutionLogEventPayload(
                  eventType = "ACTION_EXECUTED",
                  scriptTitle = snap.title,
                  phase = snap.phase.name,
                  currentStep = snap.currentTopLevelAction,
                  totalSteps = snap.totalTopLevelActions,
                  message = "${actionLog.stepLabel} ${actionLog.actionType}: ${actionLog.summary}",
                  actionLog = actionLog,
                ),
              )
            broadcastEnvelope(
              WsMessageDispatcher.buildEnvelope(
                type = WsMessageType.EXECUTION_LOG_EVENT,
                requestId = null,
                payload = payload,
              ),
            )
          }
        }

        launch {
          runnerControlHub.snapshot.collect { snap ->
            if (clientSessions.isEmpty()) return@collect
            val payload =
              json.encodeToString(
                ExecutionLogEventPayload.serializer(),
                ExecutionLogEventPayload(
                  eventType = "RUNNER_STATE",
                  scriptTitle = snap.title,
                  phase = snap.phase.name,
                  currentStep = snap.currentTopLevelAction,
                  totalSteps = snap.totalTopLevelActions,
                  message = snap.statusMessage,
                ),
              )
            broadcastEnvelope(
              WsMessageDispatcher.buildEnvelope(
                type = WsMessageType.EXECUTION_LOG_EVENT,
                requestId = null,
                payload = payload,
              ),
            )
          }
        }
      }
  }

  private fun updateLiveStreamLoopLocked() {
    val anyStreaming = clientSessions.values.any { it.wantsLiveStream }
    if (!anyStreaming) {
      liveStreamJob?.cancel()
      liveStreamJob = null
      _status.update { it.copy(isLiveStreaming = false) }
      return
    }
    if (liveStreamJob?.isActive == true) {
      _status.update { it.copy(isLiveStreaming = true) }
      return
    }

    _status.update { it.copy(isLiveStreaming = true) }
    liveStreamJob =
      scope.launch {
        while (isActive) {
          val streamTargets = clientSessions.values.filter { it.wantsLiveStream }
          if (streamTargets.isEmpty()) break

          val prefs = preferencesRepository.getPreferencesOnce()
          val bitmap =
            visionEngine.captureCleanBitmap(
              useShizukuFallback = prefs.shizuku.silentScreenshots,
            )
          if (bitmap != null) {
            val frameBytes = encodeStreamPngFrame(bitmap)
            bitmap.recycle()
            if (frameBytes != null) {
              for (target in streamTargets) {
                runCatching {
                  target.session.send(Frame.Binary(true, frameBytes))
                }.onFailure {
                  target.wantsLiveStream = false
                }
              }
            }
          }
          delay(LIVE_STREAM_INTERVAL_MS)
        }
        _status.update { it.copy(isLiveStreaming = false) }
      }
  }

  private fun encodeStreamPngFrame(bitmap: Bitmap): ByteArray? {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val scaled =
      if (maxSide > LIVE_STREAM_MAX_LONG_SIDE_PX) {
        val ratio = LIVE_STREAM_MAX_LONG_SIDE_PX.toFloat() / maxSide.toFloat()
        val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, w, h, true)
      } else {
        bitmap
      }

    return try {
      val out = ByteArrayOutputStream(64 * 1024)
      scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
      out.toByteArray()
    } finally {
      if (scaled !== bitmap) {
        scaled.recycle()
      }
    }
  }

  private suspend fun stopServerInternal(error: String?) {
    liveStreamJob?.cancel()
    liveStreamJob = null
    runnerBroadcastJob?.cancel()
    runnerBroadcastJob = null

    val snapshotSessions = clientSessions.values.toList()
    clientSessions.clear()
    for (conn in snapshotSessions) {
      runCatching {
        conn.session.close(CloseReason(CloseReason.Codes.NORMAL, "Server stopped"))
      }
    }

    runCatching { embeddedEngine?.stop(gracePeriodMillis = 250L, timeoutMillis = 1_000L) }
    embeddedEngine = null

    _status.update {
      it.copy(
        isRunning = false,
        connectedClientCount = 0,
        isLiveStreaming = false,
        lastError = error,
      )
    }
  }

  private suspend fun sendEnvelope(
    session: DefaultWebSocketServerSession,
    message: WsMessage,
  ) {
    runCatching {
      session.send(Frame.Text(WsMessageDispatcher.encodeEnvelope(message)))
    }
  }

  private suspend fun broadcastEnvelope(message: WsMessage) {
    val encoded = WsMessageDispatcher.encodeEnvelope(message)
    for (client in clientSessions.values) {
      runCatching {
        client.session.send(Frame.Text(encoded))
      }
    }
  }

  private fun refreshClientCount() {
    _status.update { it.copy(connectedClientCount = clientSessions.size) }
  }

  private fun buildAdbCommand(port: Int): String = "adb forward tcp:$port tcp:$port"
}
