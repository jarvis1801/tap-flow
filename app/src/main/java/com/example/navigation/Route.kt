package com.jarvis.tapflow.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class HomeTab {
  SCRIPT,
  PIPELINE,
  INSPECT,
  CONSOLE,
  SEAMLESS,
  SETTINGS,
}

@Serializable
sealed interface Route {
  @Serializable
  data object Onboarding : Route

  @Serializable
  data class Home(val initialTab: HomeTab = HomeTab.SCRIPT) : Route

  @Serializable
  data class Paywall(val sourceFeature: String? = null) : Route

  @Serializable
  data class ScriptEditor(val scriptId: String? = null) : Route

  @Serializable
  data class ScriptFlow(val scriptId: String? = null) : Route

  @Serializable
  data class PipelineEditor(val pipelineId: String? = null) : Route

  @Serializable
  data class ScriptLog(val scriptId: String) : Route

  @Serializable
  data class PipelineLog(val pipelineId: String) : Route

  @Serializable
  data class InspectStudio(val inspectItemId: String) : Route
}
