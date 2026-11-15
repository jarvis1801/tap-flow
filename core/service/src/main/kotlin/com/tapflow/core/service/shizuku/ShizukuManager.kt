package com.tapflow.core.service.shizuku

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

@Immutable
data class ShizukuDiagnosticState(
  val isInstalled: Boolean = false,
  val isServiceRunning: Boolean = false,
  val isPermissionGranted: Boolean = false,
  val isUserServiceBound: Boolean = false,
  val isProjectMediaAllowed: Boolean = false,
) {
  val isFullyReady: Boolean
    get() = isInstalled && isServiceRunning && isPermissionGranted
}

@Singleton
class ShizukuManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val REQUEST_CODE_PERMISSION = 4201
    private const val USER_SERVICE_TAG = "TapFlowShizukuShell"
    private const val APP_OP_PROJECT_MEDIA_STR = "android:project_media"
    private const val EXIT_SENTINEL_PREFIX = "__TAPFLOW_EXIT_CODE__:"
    private val PNG_MAGIC =
      byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val RESUMED_ACTIVITY_PKG_REGEX =
      Regex("""ActivityRecord\{[^}]*\s+(?:u\d+\s+)?([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)/""")

    internal fun parseAppOpsGetAllowed(output: String): Boolean =
      output.contains("allow", ignoreCase = true)

    internal fun parseResumedActivityPackage(output: String): String? {
      if (output.isBlank()) return null
      val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
      val preferredLine =
        lines.firstOrNull { it.contains("topResumedActivity", ignoreCase = true) }
          ?: lines.firstOrNull { it.contains("mResumedActivity", ignoreCase = true) }
          ?: lines.firstOrNull()
          ?: return null
      return RESUMED_ACTIVITY_PKG_REGEX.find(preferredLine)?.groupValues?.getOrNull(1)
    }

    internal fun isAsciiPrintable(text: String): Boolean =
      text.all { it.code in 0x20..0x7E }

    internal fun hasValidPngHeader(bytes: ByteArray): Boolean {
      if (bytes.size < PNG_MAGIC.size) return false
      for (i in PNG_MAGIC.indices) {
        if (bytes[i] != PNG_MAGIC[i]) return false
      }
      return true
    }
  }

  private val bindMutex = Mutex()

  private val _diagnosticState = MutableStateFlow(ShizukuDiagnosticState())
  val diagnosticState: StateFlow<ShizukuDiagnosticState> = _diagnosticState.asStateFlow()

  @Volatile
  private var shellService: IShizukuShellService? = null

  @Volatile
  private var pendingBindDeferred: CompletableDeferred<Boolean>? = null

  private val userServiceArgs by lazy {
    Shizuku.UserServiceArgs(
      ComponentName(context.packageName, TapFlowShizukuUserService::class.java.name),
    )
      .daemon(false)
      .processNameSuffix("tapflow_shizuku")
      .tag(USER_SERVICE_TAG)
      .version(1)
  }

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val service =
          if (binder != null && binder.isBinderAlive) {
            IShizukuShellService.Stub.asInterface(binder)
          } else {
            null
          }
        shellService = service
        _diagnosticState.update { it.copy(isUserServiceBound = service != null) }
        pendingBindDeferred?.complete(service != null)
        pendingBindDeferred = null
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        shellService = null
        _diagnosticState.update { it.copy(isUserServiceBound = false) }
      }
    }

  private val binderReceivedListener =
    Shizuku.OnBinderReceivedListener { refreshDiagnostics() }

  private val binderDeadListener =
    Shizuku.OnBinderDeadListener {
      shellService = null
      _diagnosticState.update {
        it.copy(isServiceRunning = false, isPermissionGranted = false, isUserServiceBound = false)
      }
    }

  private val permissionResultListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == REQUEST_CODE_PERMISSION) {
        refreshDiagnostics()
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
          bindUserServiceIfReady()
        }
      }
    }

  init {
    Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    Shizuku.addBinderDeadListener(binderDeadListener)
    Shizuku.addRequestPermissionResultListener(permissionResultListener)
    refreshDiagnostics()
  }

  /**
   * Performs a live synchronous check of Shizuku binder reachability and permission grant,
   * automatically updating [diagnosticState] if Shizuku stopped or permission was revoked.
   */
  fun isReadyNow(): Boolean {
    val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val granted =
      if (running) {
        runCatching {
          Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
      } else {
        false
      }
    if (!running || !granted) {
      if (!running) {
        shellService = null
      }
    }
    val binderAlive = running && granted && (shellService?.asBinder()?.isBinderAlive == true)
    val current = _diagnosticState.value
    val installed = if (running) true else current.isInstalled
    if (
      current.isInstalled != installed ||
      current.isServiceRunning != running ||
      current.isPermissionGranted != granted ||
      current.isUserServiceBound != binderAlive
    ) {
      _diagnosticState.value =
        current.copy(
          isInstalled = installed,
          isServiceRunning = running,
          isPermissionGranted = granted,
          isUserServiceBound = binderAlive,
        )
    }
    return running && granted
  }

  fun refreshDiagnostics(): ShizukuDiagnosticState {
    val installed = isShizukuInstalled()
    val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val granted =
      if (running) {
        runCatching {
          Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
      } else {
        false
      }
    if (!running) {
      shellService = null
    }
    val updated =
      ShizukuDiagnosticState(
        isInstalled = installed,
        isServiceRunning = running,
        isPermissionGranted = granted,
        isUserServiceBound = running && granted && (shellService?.asBinder()?.isBinderAlive == true),
        isProjectMediaAllowed =
          checkProjectMediaViaAppOpsManager() || _diagnosticState.value.isProjectMediaAllowed,
      )
    _diagnosticState.value = updated
    return updated
  }

  fun requestPermission() {
    val state = refreshDiagnostics()
    if (state.isServiceRunning && !state.isPermissionGranted) {
      runCatching {
        Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
      }
    }
  }

  fun bindUserServiceIfReady() {
    val state = refreshDiagnostics()
    if (state.isFullyReady && shellService?.asBinder()?.isBinderAlive != true) {
      runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
    }
  }

  fun unbindUserService() {
    runCatching { Shizuku.unbindUserService(userServiceArgs, serviceConnection, true) }
    shellService = null
    _diagnosticState.update { it.copy(isUserServiceBound = false) }
  }

  suspend fun tap(x: Int, y: Int): Result<Unit> =
    execShellCodeResult("input tap $x $y")

  suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Result<Unit> =
    execShellCodeResult("input swipe $x1 $y1 $x2 $y2 ${durationMs.coerceAtLeast(1L)}")

  suspend fun inputText(text: String): Result<Unit> {
    if (!isAsciiPrintable(text)) {
      return Result.failure(
        IllegalArgumentException("Shizuku shell 'input text' supports ASCII printable characters only."),
      )
    }
    if (text.isEmpty()) {
      return if (isReadyNow()) Result.success(Unit)
      else Result.failure(IllegalStateException("Shizuku is not ready."))
    }
    val escaped = shellEscapeForInputText(text)
    return execShellCodeResult("input text $escaped")
  }

  suspend fun sendKeyEvent(androidKeyCode: Int): Result<Unit> =
    execShellCodeResult("input keyevent $androidKeyCode")

  suspend fun forceStopApp(packageName: String): Result<Unit> {
    if (packageName.isBlank()) {
      return Result.failure(IllegalArgumentException("Package name is blank."))
    }
    val safePkg = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    if (safePkg.isBlank()) {
      return Result.failure(IllegalArgumentException("Package name contains no valid characters."))
    }
    return execShellCodeResult("am force-stop $safePkg")
  }

  suspend fun readClipboardText(): Result<String> {
    val rawResult =
      execShellOutputResult(
        "(cmd clipboard get-text); printf '\\n$EXIT_SENTINEL_PREFIX%d' \$?",
      )
    val raw = rawResult.getOrElse { return Result.failure(it) }
    val sentinelIdx = raw.lastIndexOf(EXIT_SENTINEL_PREFIX)
    if (sentinelIdx >= 0) {
      val code =
        raw.substring(sentinelIdx + EXIT_SENTINEL_PREFIX.length)
          .lineSequence()
          .firstOrNull()
          ?.trim()
          ?.toIntOrNull()
          ?: -1
      val text = raw.substring(0, sentinelIdx).trimEnd('\r', '\n')
      if (code != 0) {
        return Result.failure(IllegalStateException("Shizuku clipboard read failed (exit=$code): $text"))
      }
      if (
        text.startsWith("Error", ignoreCase = true) ||
        text.contains("Unknown command", ignoreCase = true) ||
        text.contains("SecurityException", ignoreCase = true)
      ) {
        return Result.failure(IllegalStateException("Shizuku clipboard read returned error: $text"))
      }
      return Result.success(text)
    }
    if (
      raw.startsWith("Error", ignoreCase = true) ||
      raw.contains("Unknown command", ignoreCase = true) ||
      raw.contains("SecurityException", ignoreCase = true)
    ) {
      return Result.failure(IllegalStateException("Shizuku clipboard read returned error: $raw"))
    }
    return Result.success(raw)
  }

  suspend fun queryResumedActivityPackage(): String? {
    if (!isReadyNow()) return null
    val output =
      withTimeoutOrNull(450L) {
        execShellOutputResult(
          "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity'",
        ).getOrNull()
      } ?: return null
    return parseResumedActivityPackage(output)
  }

  suspend fun grantProjectMediaAppOp(): Result<Boolean> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!isReadyNow()) {
          error("Shizuku is not ready.")
        }
        val pkg = context.packageName
        var cmdResult = execShellCodeResult("cmd appops set $pkg PROJECT_MEDIA allow")
        if (cmdResult.isFailure) {
          cmdResult = execShellCodeResult("appops set $pkg PROJECT_MEDIA allow")
        }
        cmdResult.getOrThrow()

        val output =
          execShellOutputResult("cmd appops get $pkg PROJECT_MEDIA")
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: execShellOutputResult("appops get $pkg PROJECT_MEDIA")
              .getOrNull()
              .orEmpty()
        val allowed = parseAppOpsGetAllowed(output) || checkProjectMediaViaAppOpsManager()
        _diagnosticState.update { it.copy(isProjectMediaAllowed = allowed) }
        allowed
      }
    }

  suspend fun revokeProjectMediaAppOp(): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!isReadyNow()) {
          error("Shizuku is not ready.")
        }
        val pkg = context.packageName
        var cmdResult = execShellCodeResult("cmd appops set $pkg PROJECT_MEDIA default")
        if (cmdResult.isFailure) {
          cmdResult = execShellCodeResult("appops set $pkg PROJECT_MEDIA default")
        }
        cmdResult.getOrThrow()

        val stillAllowed =
          execShellOutputResult("cmd appops get $pkg PROJECT_MEDIA")
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { parseAppOpsGetAllowed(it) }
            ?: checkProjectMediaViaAppOpsManager()
        _diagnosticState.update { it.copy(isProjectMediaAllowed = stillAllowed) }
      }
    }

  suspend fun isProjectMediaAllowed(): Boolean =
    withContext(Dispatchers.IO) {
      val pkg = context.packageName
      val allowed =
        if (isReadyNow()) {
          val output =
            execShellOutputResult("cmd appops get $pkg PROJECT_MEDIA")
              .getOrNull()
              ?.takeIf { it.isNotBlank() }
              ?: execShellOutputResult("appops get $pkg PROJECT_MEDIA").getOrNull()
          if (!output.isNullOrBlank()) {
            parseAppOpsGetAllowed(output)
          } else {
            checkProjectMediaViaAppOpsManager()
          }
        } else {
          checkProjectMediaViaAppOpsManager()
        }
      _diagnosticState.update { it.copy(isProjectMediaAllowed = allowed) }
      allowed
    }

  private fun checkProjectMediaViaAppOpsManager(): Boolean =
    runCatching {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
      val mode = appOps.checkOpNoThrow(APP_OP_PROJECT_MEDIA_STR, Process.myUid(), context.packageName)
      mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

  suspend fun captureSilentScreenshotPngBytes(): Result<ByteArray> =
    withContext(Dispatchers.IO) {
      if (!isReadyNow()) {
        return@withContext Result.failure(IllegalStateException("Shizuku is not ready."))
      }
      val service =
        ensureService()
          ?: return@withContext Result.failure(IllegalStateException("Shizuku user service unavailable."))
      try {
        val pfd =
          service.captureScreencapPipe()
            ?: return@withContext Result.failure(IllegalStateException("Shizuku screencap pipe returned null."))
        val bytes =
          ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val buffer = ByteArrayOutputStream(256 * 1024)
            input.copyTo(buffer)
            buffer.toByteArray()
          }
        if (!hasValidPngHeader(bytes)) {
          Result.failure(IllegalStateException("Shizuku screencap did not return a valid PNG stream (size=${bytes.size})."))
        } else {
          Result.success(bytes)
        }
      } catch (e: DeadObjectException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: RemoteException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: SecurityException) {
        refreshDiagnostics()
        Result.failure(e)
      } catch (t: Throwable) {
        Result.failure(t)
      }
    }

  private suspend fun execShellCodeResult(command: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      if (!isReadyNow()) {
        return@withContext Result.failure(IllegalStateException("Shizuku is not ready."))
      }
      val service =
        ensureService()
          ?: return@withContext Result.failure(IllegalStateException("Shizuku user service unavailable."))
      try {
        val exitCode = service.execCommand(command)
        if (exitCode == 0) {
          Result.success(Unit)
        } else {
          Result.failure(IllegalStateException("Shizuku command exited with code $exitCode: $command"))
        }
      } catch (e: DeadObjectException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: RemoteException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: SecurityException) {
        refreshDiagnostics()
        Result.failure(e)
      } catch (t: Throwable) {
        Result.failure(t)
      }
    }

  private suspend fun execShellOutputResult(command: String): Result<String> =
    withContext(Dispatchers.IO) {
      if (!isReadyNow()) {
        return@withContext Result.failure(IllegalStateException("Shizuku is not ready."))
      }
      val service =
        ensureService()
          ?: return@withContext Result.failure(IllegalStateException("Shizuku user service unavailable."))
      try {
        val output = service.execCommandOutput(command)
        if (output == null) {
          Result.failure(IllegalStateException("Shizuku command returned null output."))
        } else {
          Result.success(output)
        }
      } catch (e: DeadObjectException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: RemoteException) {
        handleBinderDeadException()
        Result.failure(e)
      } catch (e: SecurityException) {
        refreshDiagnostics()
        Result.failure(e)
      } catch (t: Throwable) {
        Result.failure(t)
      }
    }

  private fun handleBinderDeadException() {
    shellService = null
    pendingBindDeferred?.complete(false)
    pendingBindDeferred = null
    refreshDiagnostics()
  }

  private suspend fun ensureService(): IShizukuShellService? {
    if (!isReadyNow()) return null
    val existing = shellService
    if (existing != null && existing.asBinder().isBinderAlive) return existing

    return bindMutex.withLock {
      if (!isReadyNow()) return@withLock null
      val recheck = shellService
      if (recheck != null && recheck.asBinder().isBinderAlive) return@withLock recheck

      val state = refreshDiagnostics()
      if (!state.isFullyReady) return@withLock null

      val deferred = CompletableDeferred<Boolean>()
      pendingBindDeferred = deferred
      runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
        .onFailure {
          pendingBindDeferred = null
          return@withLock null
        }

      val bound = withTimeoutOrNull(3_000L) { deferred.await() } ?: false
      if (bound) shellService else null
    }
  }

  private fun isShizukuInstalled(): Boolean {
    return runCatching {
      context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
      true
    }.getOrDefault(false)
  }

  private fun shellEscapeForInputText(raw: String): String {
    val sb = StringBuilder("'")
    for (ch in raw) {
      when (ch) {
        '\'' -> sb.append("'\\''")
        ' ' -> sb.append("%s")
        else -> sb.append(ch)
      }
    }
    sb.append("'")
    return sb.toString()
  }
}
