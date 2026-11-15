package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.script.Script
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ScriptFlowUiState(
  val isLoading: Boolean = true,
  val scriptTitle: String = "",
  val actionCount: Int = 0,
  val layoutResult: FlowLayoutResult? = null,
)

@HiltViewModel
class ScriptFlowViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val scriptRepository: ScriptRepository,
  private val flowPreviewBridge: ScriptFlowPreviewBridge,
) : ViewModel() {

  private val argScriptId: String? = savedStateHandle["scriptId"]

  private val _uiState = MutableStateFlow(ScriptFlowUiState())
  val uiState: StateFlow<ScriptFlowUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val draft = flowPreviewBridge.draftScript.value
      val resolvedScript: Script? =
        when {
          draft != null && (argScriptId == null || draft.id == argScriptId) -> draft
          argScriptId != null -> scriptRepository.getScriptById(argScriptId)
          else -> draft
        }

      val title = resolvedScript?.title?.trim().orEmpty().ifBlank { "Untitled Script" }
      val actions = resolvedScript?.actions.orEmpty()
      val layout =
        withContext(Dispatchers.Default) {
          ScriptFlowLayoutEngine.computeLayout(actions)
        }

      _uiState.update {
        it.copy(
          isLoading = false,
          scriptTitle = title,
          actionCount = ActionTreeUtils.countAllActions(actions),
          layoutResult = layout,
        )
      }
    }
  }
}
