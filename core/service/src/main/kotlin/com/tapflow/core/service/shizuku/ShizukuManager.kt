package com.tapflow.core.service.shizuku

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
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
    val updated =
      ShizukuDiagnosticState(
        isInstalled = installed,
        isServiceRunning = running,
        isPermissionGranted = granted,
        isUserServiceBound = shellService?.asBinder()?.isBinderAlive == true,
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
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
          Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        } else {
          Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        }
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

  suspend fun tap(x: Int, y: Int): Boolean =
    execShellCode("input tap $x $y") == 0

  suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
    execShellCode("input swipe $x1 $y1 $x2 $y2 ${durationMs.coerceAtLeast(1L)}") == 0

  suspend fun inputText(text: String): Boolean {
    val escaped = shellEscapeForInputText(text)
    return execShellCode("input text $escaped") == 0
  }

  suspend fun sendKeyEvent(androidKeyCode: Int): Boolean =
    execShellCode("input keyevent $androidKeyCode") == 0

  suspend fun forceStopApp(packageName: String): Boolean {
    if (packageName.isBlank()) return false
    val safePkg = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    return execShellCode("am force-stop $safePkg") == 0
  }

  suspend fun readClipboardText(): String? {
    val output = execShellOutput("cmd clipboard get-text")
    return output?.takeIf { !it.startsWith("Error") && !it.contains("Unknown command") }
  }

  suspend fun queryResumedActivityPackage(): String? {
    if (!diagnosticState.value.isFullyReady) return null
    val output =
      withTimeoutOrNull(450L) {
        execShellOutput(
          "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity'",
        )
      } ?: return null
    return parseResumedActivityPackage(output)
  }

  suspend fun grantProjectMediaAppOp(): Result<Boolean> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!refreshDiagnostics().isFullyReady) {
          error("Shizuku is not ready.")
        }
        val pkg = context.packageName
        var exitCode = execShellCode("cmd appops set $pkg PROJECT_MEDIA allow")
        if (exitCode != 0) {
          exitCode = execShellCode("appops set $pkg PROJECT_MEDIA allow")
        }
        if (exitCode != 0) {
          error("Shizuku shell failed to grant PROJECT_MEDIA (exit=$exitCode).")
        }
        val output =
          execShellOutput("cmd appops get $pkg PROJECT_MEDIA")
            ?.takeIf { it.isNotBlank() }
            ?: execShellOutput("appops get $pkg PROJECT_MEDIA")
            .orEmpty()
        val allowed = parseAppOpsGetAllowed(output) || checkProjectMediaViaAppOpsManager()
        _diagnosticState.update { it.copy(isProjectMediaAllowed = allowed) }
        allowed
      }
    }

  suspend fun revokeProjectMediaAppOp(): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!refreshDiagnostics().isFullyReady) {
          error("Shizuku is not ready.")
        }
        val pkg = context.packageName
        var exitCode = execShellCode("cmd appops set $pkg PROJECT_MEDIA default")
        if (exitCode != 0) {
          exitCode = execShellCode("appops set $pkg PROJECT_MEDIA default")
        }
        if (exitCode != 0) {
          error("Shizuku shell failed to revoke PROJECT_MEDIA (exit=$exitCode).")
        }
        val stillAllowed =
          execShellOutput("cmd appops get $pkg PROJECT_MEDIA")
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
        if (refreshDiagnostics().isFullyReady) {
          val output =
            execShellOutput("cmd appops get $pkg PROJECT_MEDIA")
              ?.takeIf { it.isNotBlank() }
              ?: execShellOutput("appops get $pkg PROJECT_MEDIA")
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
      val uid = Process.myUid()
      val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          appOps.unsafeCheckOpNoThrow(APP_OP_PROJECT_MEDIA_STR, uid, context.packageName)
        } else {
          @Suppress("DEPRECATION")
          appOps.checkOpNoThrow(APP_OP_PROJECT_MEDIA_STR, uid, context.packageName)
        }
      mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

  suspend fun captureSilentScreenshotPngBytes(): ByteArray? =
    withContext(Dispatchers.IO) {
      val service = ensureService() ?: return@withContext null
      runCatching {
        val pfd = service.captureScreencapPipe() ?: return@withContext null
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
          val buffer = ByteArrayOutputStream(256 * 1024)
          input.copyTo(buffer)
          buffer.toByteArray().takeIf { it.isNotEmpty() }
        }
      }.getOrNull()
    }

  private suspend fun execShellCode(command: String): Int =
    withContext(Dispatchers.IO) {
      val service = ensureService() ?: return@withContext -1
      runCatching { service.execCommand(command) }.getOrDefault(-1)
    }

  private suspend fun execShellOutput(command: String): String? =
    withContext(Dispatchers.IO) {
      val service = ensureService() ?: return@withContext null
      runCatching { service.execCommandOutput(command) }.getOrNull()
    }

  private suspend fun ensureService(): IShizukuShellService? {
    val existing = shellService
    if (existing != null && existing.asBinder().isBinderAlive) return existing

    return bindMutex.withLock {
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
