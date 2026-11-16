package com.tapflow.core.service.external

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.database.repository.PipelineRepository
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import com.tapflow.core.service.accessibility.AccessibilityManagerBridge
import com.tapflow.core.service.accessibility.TapFlowAccessibilityService
import com.tapflow.core.service.engine.RunnerControlHub
import com.tapflow.core.service.overlay.OverlayLaunchIntents
import com.tapflow.core.service.overlay.OverlayService
import com.tapflow.core.service.projection.MediaProjectionBridge
import com.tapflow.core.service.projection.MediaProjectionService
import com.tapflow.core.service.shizuku.ShizukuManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TapFlowExternalTriggerReceiver : BroadcastReceiver() {

  @Inject lateinit var entitlementRepository: EntitlementRepository
  @Inject lateinit var shizukuManager: ShizukuManager
  @Inject lateinit var preferencesRepository: TapFlowPreferencesRepository
  @Inject lateinit var scriptRepository: ScriptRepository
  @Inject lateinit var pipelineRepository: PipelineRepository
  @Inject lateinit var runnerControlHub: RunnerControlHub
  @Inject lateinit var accessibilityBridge: AccessibilityManagerBridge
  @Inject lateinit var mediaProjectionBridge: MediaProjectionBridge
  @Inject lateinit var externalBroadcastEmitter: ExternalBroadcastEmitter

  private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val pkg = context.packageName
    if (action !in ExternalBroadcastContract.allIncomingActions(pkg)) {
      return
    }

    val pendingResult = goAsync()
    receiverScope.launch {
      try {
        handleTriggerIntent(context.applicationContext, intent, action)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private suspend fun handleTriggerIntent(
    context: Context,
    intent: Intent,
    action: String,
  ) {
    val pkg = context.packageName
    val mode = resolveModeForAction(pkg, action)
    val hintTargetId =
      when (action) {
        ExternalBroadcastContract.actionPlayScript(pkg) ->
          intent.getStringExtra(ExternalBroadcastContract.EXTRA_SCRIPT_ID)?.trim().orEmpty()
        ExternalBroadcastContract.actionPlayPipeline(pkg) ->
          intent.getStringExtra(ExternalBroadcastContract.EXTRA_PIPELINE_ID)?.trim().orEmpty()
        else -> ""
      }
    val hintTargetTitle =
      when (action) {
        ExternalBroadcastContract.actionPlayScript(pkg) ->
          intent.getStringExtra(ExternalBroadcastContract.EXTRA_SCRIPT_NAME)?.trim().orEmpty()
        ExternalBroadcastContract.actionPlayPipeline(pkg) ->
          intent.getStringExtra(ExternalBroadcastContract.EXTRA_PIPELINE_NAME)?.trim().orEmpty()
        else -> ""
      }

    // 1. Entitlement Check: Pro Trial or Lifetime Pro required
    entitlementRepository.refreshEntitlement()
    if (!entitlementRepository.canUseFeature(ProFeatureGate.EXTERNAL_BROADCAST)) {
      externalBroadcastEmitter.emitExecutionFinished(
        status = ExternalBroadcastContract.STATUS_REJECTED,
        mode = mode,
        targetId = hintTargetId,
        targetTitle = hintTargetTitle,
        error = ExternalBroadcastContract.ERROR_PRO_REQUIRED,
      )
      externalBroadcastEmitter.postExternalTriggerNotification(
        title = "External Trigger Blocked",
        message = "External Trigger Blocked: TapFlow Pro or 7-Day Trial required",
        openSeamlessTab = true,
      )
      return
    }

    // 2. Shizuku Installed, Running, and Permitted Check (with cold-start binder wait)
    val shizukuState = shizukuManager.awaitShizukuReady(timeoutMs = 800L)
    if (!shizukuState.isFullyReady) {
      externalBroadcastEmitter.emitExecutionFinished(
        status = ExternalBroadcastContract.STATUS_FAILED,
        mode = mode,
        targetId = hintTargetId,
        targetTitle = hintTargetTitle,
        error = ExternalBroadcastContract.ERROR_SHIZUKU_NOT_READY,
      )
      externalBroadcastEmitter.postExternalTriggerNotification(
        title = "TapFlow Trigger Failed",
        message = "TapFlow Trigger Failed: Shizuku service is not running or permission not granted",
        openSeamlessTab = true,
      )
      return
    }

    // 3. External Broadcast Preference Toggle Check
    val prefs = preferencesRepository.getPreferencesOnce()
    if (!prefs.shizuku.externalBroadcastEnabled) {
      externalBroadcastEmitter.emitExecutionFinished(
        status = ExternalBroadcastContract.STATUS_REJECTED,
        mode = mode,
        targetId = hintTargetId,
        targetTitle = hintTargetTitle,
        error = ExternalBroadcastContract.ERROR_EXTERNAL_TRIGGER_DISABLED,
      )
      externalBroadcastEmitter.postExternalTriggerNotification(
        title = "External Broadcasts Disabled",
        message = "Enable External Broadcasts in TapFlow Seamless tab",
        openSeamlessTab = true,
      )
      return
    }

    // 4. Accessibility & Overlay Permissions Check
    if (!hasSystemAutomationPermissions(context)) {
      externalBroadcastEmitter.emitExecutionFinished(
        status = ExternalBroadcastContract.STATUS_FAILED,
        mode = mode,
        targetId = hintTargetId,
        targetTitle = hintTargetTitle,
        error = ExternalBroadcastContract.ERROR_PERMISSIONS_MISSING,
      )
      externalBroadcastEmitter.postExternalTriggerNotification(
        title = "TapFlow Trigger Failed",
        message = "TapFlow Trigger Failed: Accessibility or Overlay permission is missing",
        openSeamlessTab = false,
      )
      return
    }

    when (action) {
      ExternalBroadcastContract.actionPlayScript(pkg) -> {
        val script =
          resolveTargetScript(
            scriptId = hintTargetId,
            scriptName = hintTargetTitle,
          )
        if (script == null) {
          externalBroadcastEmitter.emitExecutionFinished(
            status = ExternalBroadcastContract.STATUS_FAILED,
            mode = ExternalBroadcastContract.MODE_SCRIPT,
            targetId = hintTargetId,
            targetTitle = hintTargetTitle,
            error = ExternalBroadcastContract.ERROR_TARGET_NOT_FOUND,
          )
          externalBroadcastEmitter.postExternalTriggerNotification(
            title = "TapFlow Trigger Failed",
            message = "Script not found (id='$hintTargetId', name='$hintTargetTitle')",
            openSeamlessTab = false,
          )
          return
        }

        runnerControlHub.markExternalTriggerPending()
        val overlayIntent =
          OverlayLaunchIntents.createRunnerScriptIntent(
            context = context,
            scriptId = script.id,
            autoStart = true,
          )
        val launchResult = startOverlayServiceSafely(context, overlayIntent)
        if (launchResult.isFailure) {
          runnerControlHub.consumeExternalTriggerPending()
          val reason = launchResult.exceptionOrNull()?.message ?: "Failed to start OverlayService"
          externalBroadcastEmitter.emitExecutionFinished(
            status = ExternalBroadcastContract.STATUS_FAILED,
            mode = ExternalBroadcastContract.MODE_SCRIPT,
            targetId = script.id,
            targetTitle = script.title,
            error = reason,
          )
          externalBroadcastEmitter.postExternalTriggerNotification(
            title = "TapFlow Trigger Failed",
            message = "Unable to start TapFlow overlay service: $reason",
            openSeamlessTab = true,
          )
        }
      }

      ExternalBroadcastContract.actionPlayPipeline(pkg) -> {
        val pipeline =
          resolveTargetPipeline(
            pipelineId = hintTargetId,
            pipelineName = hintTargetTitle,
          )
        if (pipeline == null) {
          externalBroadcastEmitter.emitExecutionFinished(
            status = ExternalBroadcastContract.STATUS_FAILED,
            mode = ExternalBroadcastContract.MODE_PIPELINE,
            targetId = hintTargetId,
            targetTitle = hintTargetTitle,
            error = ExternalBroadcastContract.ERROR_TARGET_NOT_FOUND,
          )
          externalBroadcastEmitter.postExternalTriggerNotification(
            title = "TapFlow Trigger Failed",
            message = "Pipeline not found (id='$hintTargetId', name='$hintTargetTitle')",
            openSeamlessTab = false,
          )
          return
        }

        runnerControlHub.markExternalTriggerPending()
        val overlayIntent =
          OverlayLaunchIntents.createRunnerPipelineIntent(
            context = context,
            pipelineId = pipeline.id,
            autoStart = true,
          )
        val launchResult = startOverlayServiceSafely(context, overlayIntent)
        if (launchResult.isFailure) {
          runnerControlHub.consumeExternalTriggerPending()
          val reason = launchResult.exceptionOrNull()?.message ?: "Failed to start OverlayService"
          externalBroadcastEmitter.emitExecutionFinished(
            status = ExternalBroadcastContract.STATUS_FAILED,
            mode = ExternalBroadcastContract.MODE_PIPELINE,
            targetId = pipeline.id,
            targetTitle = pipeline.title,
            error = reason,
          )
          externalBroadcastEmitter.postExternalTriggerNotification(
            title = "TapFlow Trigger Failed",
            message = "Unable to start TapFlow overlay service: $reason",
            openSeamlessTab = true,
          )
        }
      }

      ExternalBroadcastContract.actionResume(pkg) -> {
        runnerControlHub.play()
      }

      ExternalBroadcastContract.actionPause(pkg) -> {
        runnerControlHub.pause("Paused via external broadcast")
      }

      ExternalBroadcastContract.actionStop(pkg) -> {
        runnerControlHub.stopExecution()
      }

      ExternalBroadcastContract.actionNextAction(pkg) -> {
        runnerControlHub.requestNextStep()
      }

      ExternalBroadcastContract.actionPrevAction(pkg) -> {
        runnerControlHub.requestPreviousStep()
      }

      ExternalBroadcastContract.actionClose(pkg) -> {
        runnerControlHub.stopExecution()
        accessibilityBridge.forceReleaseActiveStroke()
        if (OverlayService.isServiceRunning) {
          val closeIntent = OverlayLaunchIntents.createCloseIntent(context)
          val closed =
            runCatching { context.startService(closeIntent) }.isSuccess ||
              startOverlayServiceSafely(context, closeIntent).isSuccess
          if (!closed) {
            runCatching { context.stopService(Intent(context, OverlayService::class.java)) }
          }
        }
        if (!mediaProjectionBridge.isDesktopLiveStreamActive.value) {
          runCatching { context.stopService(Intent(context, MediaProjectionService::class.java)) }
        }
      }
    }
  }

  private suspend fun resolveTargetScript(
    scriptId: String,
    scriptName: String,
  ): Script? {
    if (scriptId.isNotBlank()) {
      scriptRepository.getScriptById(scriptId)?.let { return it }
    }
    if (scriptName.isNotBlank()) {
      return findScriptByName(scriptRepository.getAllScripts(), scriptName)
    }
    return null
  }

  private suspend fun resolveTargetPipeline(
    pipelineId: String,
    pipelineName: String,
  ): Pipeline? {
    if (pipelineId.isNotBlank()) {
      pipelineRepository.getPipelineById(pipelineId)?.let { return it }
    }
    if (pipelineName.isNotBlank()) {
      return findPipelineByName(pipelineRepository.getAllPipelines(), pipelineName)
    }
    return null
  }

  private suspend fun startOverlayServiceSafely(
    context: Context,
    intent: Intent,
  ): Result<Unit> {
    val directResult = runCatching { ContextCompat.startForegroundService(context, intent) }
    if (directResult.isSuccess) {
      return Result.success(Unit)
    }
    return shizukuManager.startForegroundServiceViaShell(intent)
  }

  private fun hasSystemAutomationPermissions(context: Context): Boolean {
    if (!Settings.canDrawOverlays(context)) return false
    return isAccessibilityServiceEnabled(context)
  }

  private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val targetClass = TapFlowAccessibilityService::class.java.name
    val targetPackage = context.packageName
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabled =
      am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
    val foundInList =
      enabled.any { info ->
        val svc = info.resolveInfo?.serviceInfo
        svc != null && svc.packageName == targetPackage && svc.name == targetClass
      }
    if (foundInList) return true

    val enabledServices =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ).orEmpty()
    val expectedFlat = ComponentName(targetPackage, targetClass).flattenToString()
    val expectedShort = ComponentName(targetPackage, targetClass).flattenToShortString()
    return enabledServices.split(':').any { entry ->
      entry.equals(expectedFlat, ignoreCase = true) ||
        entry.equals(expectedShort, ignoreCase = true)
    }
  }

  companion object {
    internal fun resolveModeForAction(packageName: String, action: String): String =
      when (action) {
        ExternalBroadcastContract.actionPlayScript(packageName) ->
          ExternalBroadcastContract.MODE_SCRIPT
        ExternalBroadcastContract.actionPlayPipeline(packageName) ->
          ExternalBroadcastContract.MODE_PIPELINE
        else -> ExternalBroadcastContract.MODE_CONTROL
      }

    internal fun findScriptByName(scripts: List<Script>, name: String): Script? {
      val trimmed = name.trim()
      if (trimmed.isEmpty()) return null
      return scripts.firstOrNull { it.title.trim().equals(trimmed, ignoreCase = true) }
    }

    internal fun findPipelineByName(pipelines: List<Pipeline>, name: String): Pipeline? {
      val trimmed = name.trim()
      if (trimmed.isEmpty()) return null
      return pipelines.firstOrNull { it.title.trim().equals(trimmed, ignoreCase = true) }
    }
  }
}
