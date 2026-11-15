package com.tapflow.feature.editor

import com.tapflow.core.model.script.Script
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ScriptFlowPreviewBridge @Inject constructor() {
  private val _draftScript = MutableStateFlow<Script?>(null)
  val draftScript: StateFlow<Script?> = _draftScript.asStateFlow()

  fun publishDraft(script: Script) {
    _draftScript.value = script
  }

  fun clear() {
    _draftScript.value = null
  }
}
