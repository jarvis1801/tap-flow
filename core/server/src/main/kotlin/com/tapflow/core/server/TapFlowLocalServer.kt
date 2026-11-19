package com.tapflow.core.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.repository.InspectRepository
import com.tapflow.core.database.repository.LogRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.AppPreferences
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.action.SystemKeyType
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.ipc.WsCaptureSnapshotResponse
import com.tapflow.core.model.ipc.WsDeviceStatusPayload
import com.tapflow.core.model.ipc.WsExecutionLogEventPayload
import com.tapflow.core.model.ipc.WsGetErrorScreenshotResponse
import com.tapflow.core.model.ipc.WsGetInspectImageResponse
import com.tapflow.core.model.ipc.WsHandshakeAckPayload
import com.tapflow.core.model.ipc.WsInspectGroupBundleDto
import com.tapflow.core.model.ipc.WsInstalledAppDto
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.ipc.WsRemoteInputPayload
import com.tapflow.core.model.ipc.WsRemoteInputType
import com.tapflow.core.model.ipc.WsSyncLogsResponse
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.bridge.LocalBridgeServerController
import com.tapflow.core.service.bridge.LocalBridgeStateBridge
import com.tapflow.core.service.engine.AutomationRunnerEngine
import com.tapflow.core.service.engine.PipelineExecutionEngine
import com.tapflow.core.service.engine.RunnerControlHub
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.shizuku.ShizukuManager
import com.tapflow.core.service.vision.ZeroAllocVisionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class TapFlowLocalServer @Inject constructor(
  @ApplicationContext private val context: Context,
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val entitlementRepository: EntitlementRepository,
  private val scriptRepository: ScriptRepository,
  private val pipelineRepository: PipelineRepository,
  private val inspectRepository: InspectRepository,
  private val logRepository: LogRepository,
  private val scriptAssetManager: ScriptAssetManager,
  private val automationRunnerEngine: AutomationRunnerEngine,
  private val pipelineExecutionEngine: PipelineExecutionEngine,
  private val runnerControlHub: RunnerControlHub,
  private val mediaProjectionBridge: MediaProjectionBridge,
  private val localBridgeStateBridge: LocalBridgeStateBridge,
  private val visionEngine: ZeroAllocVisionEngine,
  private val shizukuManager: ShizukuManager,
  private val accessibilityBridge: AccessibilityManagerBridge,
) : WsServerDelegate, LocalBridgeServerController {

  companion object {
    private const val LOCALHOST = "127.0.0.1"
    private const val LIVE_STREAM_INTERVAL_MS = 55L
    private const val LIVE_STREAM_MAX_LONG_SIDE_PX = 960
    private const val LIVE_STREAM_JPEG_QUALITY = 75
    private const val APP_ICON_SIZE_PX = 64

    internal fun encodeLiveStreamBinaryFrame(
      width: Int,
      height: Int,
      rotationDegrees: Int,
      jpegBytes: ByteArray,
    ): ByteArray =
      ByteBuffer.allocate(12 + jpegBytes.size)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(width)
        .putInt(height)
        .putInt(rotationDegrees)
        .put(jpegBytes)
        .array()
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val serverMutex = Mutex()
  private val json = TapFlowJson.instance

  private val _status = MutableStateFlow(ServerUiStatus())
  val status: StateFlow<ServerUiStatus> = _status.asStateFlow()

  private val _isServerRunning = MutableStateFlow(false)
  override val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

  private val _activePort = MutableStateFlow(AppPreferences.DEFAULT_KTOR_PORT)
  override val activePort: StateFlow<Int> = _activePort.asStateFlow()

  override val isLiveStreamingFromConsole: StateFlow<Boolean>
    get() = mediaProjectionBridge.isLiveStreamingFromConsole

  private val clientSessions = ConcurrentHashMap<String, ClientConnection>()
  private var embeddedEngine:
    EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
    null
  private var liveStreamJob: Job? = null
  private var runnerBroadcastJob: Job? = null
  private var deviceStatusBroadcastJob: Job? = null
  private var displayListener: DisplayManager.DisplayListener? = null
  private val displayChangedSignal = MutableStateFlow(0L)

  private data class ClientConnection(
    val id: String,
    val session: DefaultWebSocketServerSession,
    @Volatile var wantsLiveStream: Boolean = false,
  )

  init {
    scope.launch {
      preferencesRepository.preferencesFlow.collectLatest { prefs ->
        val port = prefs.ktorPort
        if (!_status.value.isRunning) {
          _activePort.value = port
        }
        _status.update { current ->
          if (current.isRunning) current
          else current.copy(port = port, adbCommand = buildAdbCommand(port))
        }
      }
    }

    scope.launch {
      entitlementRepository.entitlementState.collectLatest { state ->
        val hasLifetimePro =
          state is EntitlementState.ProLifetime &&
            EntitlementValidator.canUseFeature(state, ProFeatureGate.DESKTOP_CONSOLE)
        if (!hasLifetimePro && _status.value.isRunning) {
          stopServer("Desktop Studio server stopped: Lifetime Pro purchase is required.")
        }
      }
    }
  }

  override val connectedClients: Int
    get() = clientSessions.size

  override val currentRunnerPhaseName: String
    get() = runnerControlHub.snapshot.value.phase.name

  override suspend fun startServerFromService(requestedPort: Int?): Boolean =
    startServer(requestedPort)

  override suspend fun stopServerFromService(reason: String?) = stopServer(reason)

  override fun stopDesktopLiveStream() {
    for (client in clientSessions.values) {
      client.wantsLiveStream = false
    }
    updateLiveStreamLoopLocked()
  }

  suspend fun startServer(requestedPort: Int? = null): Boolean =
    serverMutex.withLock {
      if (!FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
        stopServerInternal(error = "Desktop Console is disabled in this release.")
        return@withLock false
      }
      val currentEntitlement = entitlementRepository.entitlementState.value
      if (currentEntitlement !is EntitlementState.ProLifetime ||
        !EntitlementValidator.canUseFeature(currentEntitlement, ProFeatureGate.DESKTOP_CONSOLE)
      ) {
        _isServerRunning.value = false
        _status.update {
          it.copy(
            isRunning = false,
            lastError =
              "Lifetime Pro purchase is required to start the Desktop Console server (excluded from 7-day trial).",
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
                      WsHandshakeAckPayload.serializer(),
                      WsHandshakeAckPayload(
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

                  val deviceStatus = getDeviceStatus()
                  sendEnvelope(
                    this,
                    WsMessageDispatcher.buildEnvelope(
                      type = WsMessageType.DEVICE_STATUS_EVENT,
                      requestId = null,
                      payload =
                        json.encodeToString(WsDeviceStatusPayload.serializer(), deviceStatus),
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
        startDeviceStatusObserver()
        _activePort.value = port
        _isServerRunning.value = true
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
    if (!FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
      stopServer()
      return false
    }
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
    _activePort.value = safePort
    val wasRunning = _status.value.isRunning
    _status.update {
      it.copy(port = safePort, adbCommand = buildAdbCommand(safePort))
    }
    if (wasRunning) {
      stopServer()
      startServer(safePort)
    }
  }

  override suspend fun getDeviceStatus(): WsDeviceStatusPayload {
    val prefs = preferencesRepository.getPreferencesOnce()
    val entitlement = entitlementRepository.entitlementState.value
    val isLifetimePro =
      entitlement is EntitlementState.ProLifetime &&
        EntitlementValidator.canUseFeature(entitlement, ProFeatureGate.DESKTOP_CONSOLE)
    val (width, height, rotationDegrees) = readDisplayMetricsAndRotation()
    val isShizukuReady = shizukuManager.isReadyNow()
    val isMediaProjectionActive = mediaProjectionBridge.isCaptureActive
    val isAccessibilityReady = accessibilityBridge.isAccessibilityConnected.value
    val foregroundPackage = accessibilityBridge.currentForegroundPackage.value

    return WsDeviceStatusPayload(
      deviceName = formatDeviceName(),
      androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      screenWidth = width,
      screenHeight = height,
      rotationDegrees = rotationDegrees,
      isAccessibilityReady = isAccessibilityReady,
      isShizukuReady = isShizukuReady,
      isMediaProjectionActive = isMediaProjectionActive,
      isSilentScreenshotEnabled = prefs.shizuku.silentScreenshots && isShizukuReady,
      currentForegroundPackage = foregroundPackage,
      isLifetimePro = isLifetimePro,
    )
  }

  override suspend fun getAllScripts(): List<Script> = scriptRepository.getAllScripts()

  override suspend fun pushScript(script: Script, forceOverwrite: Boolean): Result<List<Script>> =
    withContext(Dispatchers.IO) {
      val existing = scriptRepository.getScriptById(script.id)
      if (!forceOverwrite && existing != null && existing.updatedAtMs > script.updatedAtMs) {
        return@withContext Result.failure(
          WsProtocolException(
            code = "VERSION_CONFLICT",
            message =
              "Script '${existing.title}' has newer changes on the device (updatedAt=${existing.updatedAtMs}).",
            conflictUpdatedAtMs = existing.updatedAtMs,
          ),
        )
      }
      scriptRepository.saveScript(script)
      Result.success(scriptRepository.getAllScripts())
    }

  override suspend fun deleteScript(scriptId: String): List<Script> =
    withContext(Dispatchers.IO) {
      scriptRepository.deleteScript(scriptId)
      scriptRepository.getAllScripts()
    }

  override suspend fun getAllPipelines(): List<Pipeline> = pipelineRepository.getAllPipelines()

  override suspend fun pushPipeline(
    pipeline: Pipeline,
    forceOverwrite: Boolean,
  ): Result<List<Pipeline>> =
    withContext(Dispatchers.IO) {
      val existing = pipelineRepository.getPipelineById(pipeline.id)
      if (!forceOverwrite && existing != null && existing.updatedAtMs > pipeline.updatedAtMs) {
        return@withContext Result.failure(
          WsProtocolException(
            code = "VERSION_CONFLICT",
            message =
              "Pipeline '${existing.title}' has newer changes on the device (updatedAt=${existing.updatedAtMs}).",
            conflictUpdatedAtMs = existing.updatedAtMs,
          ),
        )
      }
      pipelineRepository.savePipeline(pipeline)
      Result.success(pipelineRepository.getAllPipelines())
    }

  override suspend fun deletePipeline(pipelineId: String): List<Pipeline> =
    withContext(Dispatchers.IO) {
      pipelineRepository.deletePipeline(pipelineId)
      pipelineRepository.getAllPipelines()
    }

  override suspend fun getAllInspectGroups(): List<WsInspectGroupBundleDto> =
    inspectRepository.getAllGroupsWithItems().map { bundle ->
      WsInspectGroupBundleDto(group = bundle.group, items = bundle.items)
    }

  override suspend fun getInspectImage(itemId: String): Result<WsGetInspectImageResponse> =
    withContext(Dispatchers.IO) {
      val item =
        inspectRepository.getItem(itemId)
          ?: return@withContext Result.failure(
            WsProtocolException(
              code = "INSPECT_IMAGE_NOT_FOUND",
              message = "Inspect item '$itemId' does not exist.",
            ),
          )
      val file = File(item.fullScreenPngPath)
      if (!file.exists() || !file.isFile) {
        return@withContext Result.failure(
          WsProtocolException(
            code = "INSPECT_IMAGE_NOT_FOUND",
            message = "Lossless PNG file missing for inspect item '$itemId'.",
          ),
        )
      }

      val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
      val width = boundsOpts.outWidth.takeIf { it > 0 } ?: item.screenWidth
      val height = boundsOpts.outHeight.takeIf { it > 0 } ?: item.screenHeight

      val pngBytes =
        if (boundsOpts.outMimeType == "image/png") {
          file.readBytes()
        } else {
          val decoded =
            BitmapFactory.decodeFile(file.absolutePath)
              ?: return@withContext Result.failure(
                WsProtocolException(
                  code = "INSPECT_IMAGE_NOT_FOUND",
                  message = "Failed to decode inspect screenshot '$itemId'.",
                ),
              )
          try {
            ByteArrayOutputStream().use { out ->
              decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
              out.toByteArray()
            }
          } finally {
            decoded.recycle()
          }
        }

      Result.success(
        WsGetInspectImageResponse(
          itemId = item.id,
          width = width,
          height = height,
          pngBase64 = Base64.getEncoder().encodeToString(pngBytes),
        ),
      )
    }

  override suspend fun pushInspectItem(
    groupTitle: String,
    item: InspectItem,
    pngBase64: String?,
  ): Result<List<WsInspectGroupBundleDto>> =
    withContext(Dispatchers.IO) {
      val existingGroup = inspectRepository.getGroup(item.groupId)
      if (existingGroup == null) {
        inspectRepository.saveGroup(
          InspectGroup(
            id = item.groupId,
            title = groupTitle.ifBlank { "Desktop Studio" },
            createdAtMs = System.currentTimeMillis(),
          ),
        )
      } else if (groupTitle.isNotBlank() && existingGroup.title != groupTitle) {
        inspectRepository.saveGroup(existingGroup.copy(title = groupTitle))
      }

      var resolvedPath = item.fullScreenPngPath
      var resolvedWidth = item.screenWidth
      var resolvedHeight = item.screenHeight

      if (!pngBase64.isNullOrBlank()) {
        val rawBytes =
          runCatching { Base64.getDecoder().decode(pngBase64) }.getOrNull()
            ?: return@withContext Result.failure(
              WsProtocolException(
                code = "INVALID_PAYLOAD",
                message = "Invalid Base64 image data in PUSH_INSPECT_ITEM.",
              ),
            )
        val decoded =
          BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return@withContext Result.failure(
              WsProtocolException(
                code = "INVALID_PAYLOAD",
                message = "Failed to decode image bytes in PUSH_INSPECT_ITEM.",
              ),
            )
        try {
          val safeId = item.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
          val destFile = File(scriptAssetManager.getInspectScreensDir(), "$safeId.png")
          FileOutputStream(destFile).use { fos ->
            decoded.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
          }
          resolvedPath = destFile.absolutePath
          if (decoded.width > 0) resolvedWidth = decoded.width
          if (decoded.height > 0) resolvedHeight = decoded.height
        } finally {
          decoded.recycle()
        }
      }

      inspectRepository.saveItem(
        item.copy(
          fullScreenPngPath = resolvedPath,
          screenWidth = resolvedWidth,
          screenHeight = resolvedHeight,
        ),
      )
      Result.success(getAllInspectGroups())
    }

  override suspend fun deleteInspectGroup(groupId: String): List<WsInspectGroupBundleDto> =
    withContext(Dispatchers.IO) {
      inspectRepository.deleteGroup(groupId)
      getAllInspectGroups()
    }

  override suspend fun deleteInspectItem(itemId: String): List<WsInspectGroupBundleDto> =
    withContext(Dispatchers.IO) {
      inspectRepository.deleteItem(itemId)
      getAllInspectGroups()
    }

  override suspend fun captureLosslessSnapshot(): Result<WsCaptureSnapshotResponse> =
    withContext(Dispatchers.Default) {
      val prefs = preferencesRepository.getPreferencesOnce()
      val bitmap =
        visionEngine.captureCleanBitmap(
          useShizukuFallback = prefs.shizuku.silentScreenshots,
        )
          ?: return@withContext Result.failure(
            WsProtocolException(
              code = "CAPTURE_UNAVAILABLE",
              message =
                "Unable to capture screen. Start MediaProjection, enable Shizuku Silent Screenshot, or enable Accessibility (Android 11+).",
            ),
          )

      val pngBytes: ByteArray
      val width: Int
      val height: Int
      try {
        width = bitmap.width
        height = bitmap.height
        val out = ByteArrayOutputStream(256 * 1024)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        pngBytes = out.toByteArray()
      } finally {
        bitmap.recycle()
      }

      val (_, _, rotationDegrees) = readDisplayMetricsAndRotation()
      val fgPackage = accessibilityBridge.resolveCurrentForegroundPackage().orEmpty()
      val fgAppName = resolveAppLabel(fgPackage)

      Result.success(
        WsCaptureSnapshotResponse(
          width = width,
          height = height,
          rotationDegrees = rotationDegrees,
          appName = fgAppName,
          packageName = fgPackage,
          pngBase64 = Base64.getEncoder().encodeToString(pngBytes),
        ),
      )
    }

  override suspend fun getInstalledApps(): List<WsInstalledAppDto> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val launcherIntent =
        Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_LAUNCHER)
        }
      val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          pm.queryIntentActivities(launcherIntent, 0)
        }

      val seenPackages = HashSet<String>()
      val results = ArrayList<WsInstalledAppDto>(resolveInfos.size)
      for (info in resolveInfos) {
        val actInfo = info.activityInfo ?: continue
        val pkgName = actInfo.packageName ?: continue
        if (!seenPackages.add(pkgName)) continue
        val label =
          runCatching { info.loadLabel(pm)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: resolveAppLabel(pkgName)
        val iconBase64 =
          runCatching {
            val drawable = info.loadIcon(pm) ?: pm.getApplicationIcon(pkgName)
            encodeDrawableToPngBase64(drawable)
          }.getOrNull()

        results +=
          WsInstalledAppDto(
            packageName = pkgName,
            appName = label,
            iconPngBase64 = iconBase64,
            isLaunchable = true,
          )
      }
      results.sortedBy { it.appName.lowercase() }
    }

  override suspend fun getAllLogs(): WsSyncLogsResponse =
    withContext(Dispatchers.IO) {
      WsSyncLogsResponse(
        scriptLogs = logRepository.getAllScriptLogs(),
        pipelineLogs = logRepository.getAllPipelineLogs(),
      )
    }

  override suspend fun getErrorScreenshot(
    filePath: String?,
    logId: String?,
  ): Result<WsGetErrorScreenshotResponse> =
    withContext(Dispatchers.IO) {
      val candidatePath =
        when {
          !filePath.isNullOrBlank() -> filePath
          !logId.isNullOrBlank() -> logRepository.findErrorScreenshotPathByLogId(logId)
          else -> null
        }
          ?: return@withContext Result.failure(
            WsProtocolException(
              code = "SCREENSHOT_NOT_FOUND",
              message = "Error screenshot not found for logId='$logId'.",
            ),
          )

      val file = File(candidatePath)
      val rootCanonical = runCatching { context.filesDir.canonicalPath }.getOrNull()
      val fileCanonical = runCatching { file.canonicalPath }.getOrNull()
      if (rootCanonical == null ||
        fileCanonical == null ||
        (!fileCanonical.startsWith(rootCanonical + File.separator) && fileCanonical != rootCanonical) ||
        !file.exists() ||
        !file.isFile
      ) {
        return@withContext Result.failure(
          WsProtocolException(
            code = "SCREENSHOT_NOT_FOUND",
            message = "Error screenshot file does not exist or is inaccessible.",
          ),
        )
      }

      val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
      val width = boundsOpts.outWidth.coerceAtLeast(0)
      val height = boundsOpts.outHeight.coerceAtLeast(0)
      val pngBytes =
        if (boundsOpts.outMimeType == "image/png") {
          file.readBytes()
        } else {
          val decoded =
            BitmapFactory.decodeFile(file.absolutePath)
              ?: return@withContext Result.failure(
                WsProtocolException(
                  code = "SCREENSHOT_NOT_FOUND",
                  message = "Failed to decode error screenshot.",
                ),
              )
          try {
            ByteArrayOutputStream().use { out ->
              decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
              out.toByteArray()
            }
          } finally {
            decoded.recycle()
          }
        }

      Result.success(
        WsGetErrorScreenshotResponse(
          filePath = file.absolutePath,
          logId = logId,
          width = width,
          height = height,
          pngBase64 = Base64.getEncoder().encodeToString(pngBytes),
        ),
      )
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

  override suspend fun startRemoteRunScript(scriptId: String): Result<Script> {
    val currentEntitlement = entitlementRepository.entitlementState.value
    if (currentEntitlement !is EntitlementState.ProLifetime ||
      !EntitlementValidator.canUseFeature(currentEntitlement, ProFeatureGate.DESKTOP_CONSOLE)
    ) {
      return Result.failure(
        WsProtocolException(
          code = "PRO_REQUIRED",
          message = "TapFlow Lifetime Pro is required for remote execution.",
        ),
      )
    }

    val script =
      scriptRepository.getScriptById(scriptId)
        ?: return Result.failure(
          WsProtocolException(
            code = "TARGET_NOT_FOUND",
            message = "Script '$scriptId' not found.",
          ),
        )

    val blockers = entitlementRepository.detectScriptProBlockers(script)
    if (blockers.isNotEmpty()) {
      return Result.failure(
        WsProtocolException(
          code = "PRO_REQUIRED",
          message = "Pro features required: ${blockers.joinToString { it.displayName }}",
        ),
      )
    }

    if (!accessibilityBridge.isAccessibilityConnected.value) {
      return Result.failure(
        WsProtocolException(
          code = "PERMISSIONS_OR_VISION_NOT_READY",
          message = "Accessibility service must be enabled before running a script remotely.",
        ),
      )
    }

    if (script.requiresVision() && !isCapturePermitted()) {
      return Result.failure(
        WsProtocolException(
          code = "PERMISSIONS_OR_VISION_NOT_READY",
          message =
            "Vision capture (MediaProjection, Shizuku Silent Screenshot, or Android 11+ Accessibility) is required for this script.",
        ),
      )
    }

    // Remote Desktop runs strictly force autoStart = true
    val overlayIntent =
      OverlayLaunchIntents.createRunnerScriptIntent(
        context = context,
        scriptId = script.id,
        autoStart = true,
      )
    val launchedOverlay = tryLaunchOverlayService(overlayIntent)
    if (!launchedOverlay) {
      automationRunnerEngine.startScript(script = script, startPaused = false, initialSingleStep = false)
    }
    return Result.success(script)
  }

  override suspend fun startRemoteRunPipeline(pipelineId: String): Result<Pipeline> {
    val currentEntitlement = entitlementRepository.entitlementState.value
    if (currentEntitlement !is EntitlementState.ProLifetime ||
      !EntitlementValidator.canUseFeature(currentEntitlement, ProFeatureGate.DESKTOP_CONSOLE)
    ) {
      return Result.failure(
        WsProtocolException(
          code = "PRO_REQUIRED",
          message = "TapFlow Lifetime Pro is required for remote execution.",
        ),
      )
    }

    val pipeline =
      pipelineRepository.getPipelineById(pipelineId)
        ?: return Result.failure(
          WsProtocolException(
            code = "TARGET_NOT_FOUND",
            message = "Pipeline '$pipelineId' not found.",
          ),
        )

    val allScripts = scriptRepository.getAllScripts().associateBy { it.id }
    val blockers = entitlementRepository.detectPipelineProBlockers(pipeline, allScripts)
    if (blockers.isNotEmpty()) {
      return Result.failure(
        WsProtocolException(
          code = "PRO_REQUIRED",
          message = "Pro features required: ${blockers.joinToString { it.displayName }}",
        ),
      )
    }

    if (!accessibilityBridge.isAccessibilityConnected.value) {
      return Result.failure(
        WsProtocolException(
          code = "PERMISSIONS_OR_VISION_NOT_READY",
          message = "Accessibility service must be enabled before running a pipeline remotely.",
        ),
      )
    }

    val needsVision = pipeline.steps.any { step -> allScripts[step.scriptId]?.requiresVision() == true }
    if (needsVision && !isCapturePermitted()) {
      return Result.failure(
        WsProtocolException(
          code = "PERMISSIONS_OR_VISION_NOT_READY",
          message =
            "Vision capture (MediaProjection, Shizuku Silent Screenshot, or Android 11+ Accessibility) is required for this pipeline.",
        ),
      )
    }

    // Remote Desktop runs strictly force autoStart = true
    val overlayIntent =
      OverlayLaunchIntents.createRunnerPipelineIntent(
        context = context,
        pipelineId = pipeline.id,
        autoStart = true,
      )
    val launchedOverlay = tryLaunchOverlayService(overlayIntent)
    if (!launchedOverlay) {
      pipelineExecutionEngine.startPipeline(
        pipeline = pipeline,
        startPaused = false,
        initialSingleStep = false,
      )
    }
    return Result.success(pipeline)
  }

  override fun stopRemoteRun() {
    runnerControlHub.stopExecution()
  }

  override suspend fun executeRemoteInput(input: WsRemoteInputPayload): Result<Unit> {
    val shizukuReady = shizukuManager.isReadyNow()
    val a11yReady = accessibilityBridge.isAccessibilityConnected.value
    if (!shizukuReady && !a11yReady) {
      return Result.failure(
        WsProtocolException(
          code = "PERMISSIONS_OR_VISION_NOT_READY",
          message = "Accessibility service or Shizuku is required to inject remote input.",
        ),
      )
    }

    val succeeded =
      when (input.inputType) {
        WsRemoteInputType.TAP -> {
          val shizukuOk = shizukuReady && shizukuManager.tap(input.x, input.y).isSuccess
          shizukuOk ||
            (a11yReady &&
              accessibilityBridge.tap(
                x = input.x.toFloat(),
                y = input.y.toFloat(),
                pressDurationMs = input.durationMs.coerceIn(40L, 250L),
              ))
        }

        WsRemoteInputType.SWIPE -> {
          val duration = input.durationMs.coerceIn(50L, 10_000L)
          val shizukuOk =
            shizukuReady &&
              shizukuManager.swipe(input.x, input.y, input.endX, input.endY, duration).isSuccess
          shizukuOk ||
            (a11yReady &&
              accessibilityBridge.dispatchPathSwipe(
                path =
                  Path().apply {
                    moveTo(input.x.toFloat().coerceAtLeast(0f), input.y.toFloat().coerceAtLeast(0f))
                    lineTo(
                      input.endX.toFloat().coerceAtLeast(0f),
                      input.endY.toFloat().coerceAtLeast(0f),
                    )
                  },
                durationMs = duration,
              ))
        }

        WsRemoteInputType.GLOBAL_BACK -> {
          val shizukuOk = shizukuReady && shizukuManager.sendKeyEvent(KeyEvent.KEYCODE_BACK).isSuccess
          shizukuOk || (a11yReady && accessibilityBridge.performGlobalAction(SystemKeyType.BACK))
        }

        WsRemoteInputType.GLOBAL_HOME -> {
          val shizukuOk = shizukuReady && shizukuManager.sendKeyEvent(KeyEvent.KEYCODE_HOME).isSuccess
          shizukuOk || (a11yReady && accessibilityBridge.performGlobalAction(SystemKeyType.HOME))
        }
      }

    return if (succeeded) {
      Result.success(Unit)
    } else {
      Result.failure(
        WsProtocolException(
          code = "REMOTE_INPUT_FAILED",
          message = "Remote input dispatch (${input.inputType.name}) failed.",
        ),
      )
    }
  }

  private suspend fun tryLaunchOverlayService(intent: Intent): Boolean {
    if (!Settings.canDrawOverlays(context)) return false
    val directOk = runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
    if (directOk) return true
    if (shizukuManager.isReadyNow()) {
      return shizukuManager.startForegroundServiceViaShell(intent).isSuccess
    }
    return false
  }

  private suspend fun isCapturePermitted(): Boolean {
    val prefs = preferencesRepository.getPreferencesOnce()
    return mediaProjectionBridge.isCaptureActive ||
      (prefs.shizuku.silentScreenshots && shizukuManager.isReadyNow()) ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && accessibilityBridge.isAccessibilityConnected.value)
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
                WsExecutionLogEventPayload.serializer(),
                WsExecutionLogEventPayload(
                  eventType = "ACTION_EXECUTED",
                  scriptTitle = snap.title,
                  phase = snap.phase.name,
                  currentStep = snap.currentTopLevelAction,
                  totalSteps = snap.totalTopLevelActions,
                  message = "${actionLog.stepLabel} ${actionLog.actionType}: ${actionLog.summary}",
                  actionLog = actionLog,
                  pipelineStep = snap.currentPipelineStep,
                  totalPipelineSteps = snap.totalPipelineSteps,
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
                WsExecutionLogEventPayload.serializer(),
                WsExecutionLogEventPayload(
                  eventType = "RUNNER_STATE",
                  scriptTitle = snap.title,
                  phase = snap.phase.name,
                  currentStep = snap.currentTopLevelAction,
                  totalSteps = snap.totalTopLevelActions,
                  message = snap.statusMessage,
                  pipelineStep = snap.currentPipelineStep,
                  totalPipelineSteps = snap.totalPipelineSteps,
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

  private fun startDeviceStatusObserver() {
    registerDisplayChangeListener()
    deviceStatusBroadcastJob?.cancel()
    deviceStatusBroadcastJob =
      scope.launch {
        val stateSignalsFlow =
          combine(
            accessibilityBridge.isAccessibilityConnected,
            accessibilityBridge.currentForegroundPackage,
            shizukuManager.diagnosticState,
            mediaProjectionBridge.captureState,
            preferencesRepository.preferencesFlow.map { it.shizuku.silentScreenshots },
          ) { a11y, fg, shizuku, capture, silent ->
            listOf(a11y, fg, shizuku.isFullyReady, capture, silent)
          }

        combine(
          stateSignalsFlow,
          entitlementRepository.entitlementState,
          displayChangedSignal,
        ) { _, _, _ -> Unit }
          .collectLatest {
            delay(80L)
            if (clientSessions.isEmpty() || !_isServerRunning.value) return@collectLatest
            val statusPayload = getDeviceStatus()
            broadcastEnvelope(
              WsMessageDispatcher.buildEnvelope(
                type = WsMessageType.DEVICE_STATUS_EVENT,
                requestId = null,
                payload =
                  json.encodeToString(WsDeviceStatusPayload.serializer(), statusPayload),
              ),
            )
          }
      }
  }

  private fun registerDisplayChangeListener() {
    unregisterDisplayChangeListener()
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
    val listener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
          if (displayId == Display.DEFAULT_DISPLAY) {
            displayChangedSignal.update { it + 1L }
          }
        }
      }
    displayListener = listener
    runCatching { displayManager.registerDisplayListener(listener, mainHandler) }
  }

  private fun unregisterDisplayChangeListener() {
    displayListener?.let { listener ->
      val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
      runCatching { displayManager?.unregisterDisplayListener(listener) }
    }
    displayListener = null
  }

  private fun updateLiveStreamLoopLocked() {
    val anyStreaming = clientSessions.values.any { it.wantsLiveStream }
    if (!anyStreaming) {
      liveStreamJob?.cancel()
      liveStreamJob = null
      mediaProjectionBridge.setDesktopLiveStreamActive(false)
      localBridgeStateBridge.setLiveStreamingFromConsole(false)
      _status.update { it.copy(isLiveStreaming = false) }
      return
    }
    if (liveStreamJob?.isActive == true) {
      mediaProjectionBridge.setDesktopLiveStreamActive(true)
      localBridgeStateBridge.setLiveStreamingFromConsole(true)
      _status.update { it.copy(isLiveStreaming = true) }
      return
    }

    mediaProjectionBridge.setDesktopLiveStreamActive(true)
    localBridgeStateBridge.setLiveStreamingFromConsole(true)
    _status.update { it.copy(isLiveStreaming = true) }
    liveStreamJob =
      scope.launch {
        try {
          while (isActive) {
            val frameStartMs = System.currentTimeMillis()
            val streamTargets = clientSessions.values.filter { it.wantsLiveStream }
            if (streamTargets.isEmpty()) break

            val prefs = preferencesRepository.getPreferencesOnce()
            val bitmap =
              visionEngine.captureCleanBitmap(
                useShizukuFallback = prefs.shizuku.silentScreenshots,
              )
            if (bitmap != null) {
              val frameBytes =
                try {
                  val (_, _, rotationDegrees) = readDisplayMetricsAndRotation()
                  encodeStreamJpegFrame(bitmap, rotationDegrees)
                } finally {
                  bitmap.recycle()
                }
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
            val elapsedMs = (System.currentTimeMillis() - frameStartMs).coerceAtLeast(0L)
            val remainingDelayMs = (LIVE_STREAM_INTERVAL_MS - elapsedMs).coerceAtLeast(10L)
            delay(remainingDelayMs)
          }
        } finally {
          mediaProjectionBridge.setDesktopLiveStreamActive(false)
          localBridgeStateBridge.setLiveStreamingFromConsole(false)
          _status.update { it.copy(isLiveStreaming = false) }
        }
      }
  }

  private fun encodeStreamJpegFrame(
    bitmap: Bitmap,
    rotationDegrees: Int,
  ): ByteArray? {
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
      scaled.compress(Bitmap.CompressFormat.JPEG, LIVE_STREAM_JPEG_QUALITY, out)
      val jpegBytes = out.toByteArray()
      encodeLiveStreamBinaryFrame(
        width = scaled.width,
        height = scaled.height,
        rotationDegrees = rotationDegrees,
        jpegBytes = jpegBytes,
      )
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
    deviceStatusBroadcastJob?.cancel()
    deviceStatusBroadcastJob = null
    unregisterDisplayChangeListener()

    val snapshotSessions = clientSessions.values.toList()
    clientSessions.clear()
    for (conn in snapshotSessions) {
      runCatching {
        conn.session.close(CloseReason(CloseReason.Codes.NORMAL, "Server stopped"))
      }
    }

    runCatching { embeddedEngine?.stop(gracePeriodMillis = 250L, timeoutMillis = 1_000L) }
    embeddedEngine = null
    mediaProjectionBridge.setDesktopLiveStreamActive(false)
    localBridgeStateBridge.setLiveStreamingFromConsole(false)
    _isServerRunning.value = false

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

  private fun formatDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    return when {
      model.isBlank() -> manufacturer.ifBlank { "Android Device" }
      manufacturer.isBlank() -> model
      model.startsWith(manufacturer, ignoreCase = true) -> model
      else -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
    }
  }

  private fun readDisplayMetricsAndRotation(): Triple<Int, Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    val rotationConst =
      display?.rotation
        ?: @Suppress("DEPRECATION") windowManager?.defaultDisplay?.rotation
        ?: Surface.ROTATION_0
    val rotationDegrees =
      when (rotationConst) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
      }

    if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = windowManager.maximumWindowMetrics.bounds
      return Triple(bounds.width(), bounds.height(), rotationDegrees)
    }
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager?.defaultDisplay?.getRealMetrics(metrics)
    return Triple(metrics.widthPixels, metrics.heightPixels, rotationDegrees)
  }

  private fun resolveAppLabel(packageName: String): String {
    if (packageName.isBlank()) return "Screen Capture"
    return runCatching {
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0L),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getApplicationInfo(packageName, 0)
        }
      context.packageManager.getApplicationLabel(info).toString().ifBlank { packageName }
    }.getOrDefault(packageName)
  }

  private fun encodeDrawableToPngBase64(drawable: Drawable): String {
    val bitmap =
      if (drawable is BitmapDrawable &&
        drawable.bitmap != null &&
        drawable.bitmap.width <= APP_ICON_SIZE_PX &&
        drawable.bitmap.height <= APP_ICON_SIZE_PX
      ) {
        val out = ByteArrayOutputStream(4 * 1024)
        drawable.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.getEncoder().encodeToString(out.toByteArray())
      } else {
        Bitmap.createBitmap(APP_ICON_SIZE_PX, APP_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
      }

    return try {
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      val out = ByteArrayOutputStream(4 * 1024)
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      Base64.getEncoder().encodeToString(out.toByteArray())
    } finally {
      bitmap.recycle()
    }
  }
}
