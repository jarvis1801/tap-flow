package com.tapflow.core.service.external

import android.content.Intent

object ExternalBroadcastContract {
  const val SUFFIX_PLAY_SCRIPT = "ACTION_PLAY_SCRIPT"
  const val SUFFIX_PLAY_PIPELINE = "ACTION_PLAY_PIPELINE"
  const val SUFFIX_RESUME = "ACTION_RESUME"
  const val SUFFIX_PAUSE = "ACTION_PAUSE"
  const val SUFFIX_STOP = "ACTION_STOP"
  const val SUFFIX_CLOSE = "ACTION_CLOSE"
  const val SUFFIX_NEXT_ACTION = "ACTION_NEXT_ACTION"
  const val SUFFIX_PREV_ACTION = "ACTION_PREV_ACTION"
  const val SUFFIX_EXECUTION_FINISHED = "ACTION_EXECUTION_FINISHED"

  // Incoming extras (dot-free for Tasker %var auto-extraction)
  const val EXTRA_SCRIPT_ID = "script_id"
  const val EXTRA_SCRIPT_NAME = "script_name"
  const val EXTRA_PIPELINE_ID = "pipeline_id"
  const val EXTRA_PIPELINE_NAME = "pipeline_name"

  // Outgoing completion broadcast extras (dot-free for Tasker %status, %mode, etc.)
  const val EXTRA_STATUS = "status"
  const val EXTRA_MODE = "mode"
  const val EXTRA_TARGET_ID = "target_id"
  const val EXTRA_TARGET_TITLE = "target_title"
  const val EXTRA_DURATION_MS = "duration_ms"
  const val EXTRA_STEPS_EXECUTED = "steps_executed"
  const val EXTRA_ERROR = "error"

  // Status constants
  const val STATUS_COMPLETED = "COMPLETED"
  const val STATUS_FAILED = "FAILED"
  const val STATUS_STOPPED = "STOPPED"
  const val STATUS_REJECTED = "REJECTED"

  // Mode constants
  const val MODE_SCRIPT = "SCRIPT"
  const val MODE_PIPELINE = "PIPELINE"
  const val MODE_CONTROL = "CONTROL"

  // Standardized error codes
  const val ERROR_NONE = ""
  const val ERROR_SHIZUKU_NOT_READY = "SHIZUKU_NOT_READY"
  const val ERROR_PRO_REQUIRED = "PRO_REQUIRED"
  const val ERROR_PERMISSIONS_MISSING = "PERMISSIONS_MISSING"
  const val ERROR_TARGET_NOT_FOUND = "TARGET_NOT_FOUND"

  // Deep-link extra for notification click
  const val EXTRA_OPEN_HOME_TAB = "tapflow_open_home_tab"
  const val HOME_TAB_SEAMLESS = "SEAMLESS"

  fun actionPlayScript(packageName: String): String = "$packageName.$SUFFIX_PLAY_SCRIPT"

  fun actionPlayPipeline(packageName: String): String = "$packageName.$SUFFIX_PLAY_PIPELINE"

  fun actionResume(packageName: String): String = "$packageName.$SUFFIX_RESUME"

  fun actionPause(packageName: String): String = "$packageName.$SUFFIX_PAUSE"

  fun actionStop(packageName: String): String = "$packageName.$SUFFIX_STOP"

  fun actionClose(packageName: String): String = "$packageName.$SUFFIX_CLOSE"

  fun actionNextAction(packageName: String): String = "$packageName.$SUFFIX_NEXT_ACTION"

  fun actionPrevAction(packageName: String): String = "$packageName.$SUFFIX_PREV_ACTION"

  fun actionExecutionFinished(packageName: String): String =
    "$packageName.$SUFFIX_EXECUTION_FINISHED"

  fun allIncomingActions(packageName: String): List<String> =
    listOf(
      actionPlayScript(packageName),
      actionPlayPipeline(packageName),
      actionResume(packageName),
      actionPause(packageName),
      actionStop(packageName),
      actionClose(packageName),
      actionNextAction(packageName),
      actionPrevAction(packageName),
    )

  fun buildFinishedBroadcastIntent(
    packageName: String,
    status: String,
    mode: String,
    targetId: String = "",
    targetTitle: String = "",
    durationMs: Long = 0L,
    stepsExecuted: Int = 0,
    error: String = ERROR_NONE,
  ): Intent =
    Intent(actionExecutionFinished(packageName)).apply {
      addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
      putExtra(EXTRA_STATUS, status)
      putExtra(EXTRA_MODE, mode)
      putExtra(EXTRA_TARGET_ID, targetId)
      putExtra(EXTRA_TARGET_TITLE, targetTitle)
      putExtra(EXTRA_DURATION_MS, durationMs.coerceAtLeast(0L))
      putExtra(EXTRA_STEPS_EXECUTED, stepsExecuted.coerceAtLeast(0))
      putExtra(EXTRA_ERROR, error)
    }
}
