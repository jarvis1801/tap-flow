package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapflow.core.database.repository.ScriptRepository
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.script.Script
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ScriptFlowUiState(
  val isLoading: Boolean = true,
  val script: Script? = null,
  val scriptTitle: String = "",
  val actionCount: Int = 0,
  val globalDelay: DelayConfig = DelayConfig.Fixed(0L),
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

  private var recomputeJob: Job? = null

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
      val globalDelay = resolvedScript?.globalDelay ?: DelayConfig.Fixed(0L)
      val layout =
        withContext(Dispatchers.Default) {
          ScriptFlowLayoutEngine.computeLayout(
            actions = actions,
            globalDelay = globalDelay,
          )
        }

      _uiState.update {
        it.copy(
          isLoading = false,
          script = resolvedScript,
          scriptTitle = title,
          actionCount = ActionTreeUtils.countAllActions(actions),
          globalDelay = globalDelay,
          layoutResult = layout,
        )
      }
    }
  }

  fun recomputeLayout(
    collapsedBlockIds: Set<String>,
    decisionHeightsByNodeId: Map<String, Float>,
  ) {
    val script = _uiState.value.script ?: return
    recomputeJob?.cancel()
    recomputeJob =
      viewModelScope.launch {
        val layout =
          withContext(Dispatchers.Default) {
            ScriptFlowLayoutEngine.computeLayout(
              script = script,
              collapsedBlockIds = collapsedBlockIds,
              decisionHeightsByNodeId = decisionHeightsByNodeId,
            )
          }
        _uiState.update { it.copy(layoutResult = layout) }
      }
  }
}
