package com.tapflow.feature.home

import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.PostActionDelay
import com.tapflow.core.model.condition.ColorMatchMode
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionNode
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import java.io.File
import java.util.UUID

internal object StarterScriptTemplates {
  fun createAll(nowMs: Long = System.currentTimeMillis()): List<Script> {
    val tapLoopScript =
      Script(
        id = UUID.randomUUID().toString(),
        title = "Example: Basic Tap Loop",
        description =
          "Taps the screen center 5 times with humanized coordinate variance and a 400ms wait.",
        tags = listOf("Example", "Tap Loop"),
        globalDelay = DelayConfig.Fixed(250L),
        actions =
          listOf(
            ScriptAction.LoopBlock(
              id = UUID.randomUUID().toString(),
              name = "Repeat 5 Taps",
              mode = LoopMode.Count(count = NumericInput.Literal(5)),
              bodyActions =
                listOf(
                  ScriptAction.Tap(
                    id = UUID.randomUUID().toString(),
                    x = NumericInput.Literal(540),
                    y = NumericInput.Literal(960),
                    jitterPx = 6,
                    postDelay = PostActionDelay.Override(DelayConfig.Fixed(400L)),
                  ),
                ),
            ),
          ),
        createdAtMs = nowMs,
        updatedAtMs = nowMs,
      )

    val colorDecisionScript =
      Script(
        id = UUID.randomUUID().toString(),
        title = "Example: Color Match Decision",
        description =
          "Checks sample pixel colors to branch between tap, swipe, or wait. Preview it in [Flow] or edit coordinates.",
        tags = listOf("Example", "Decision"),
        globalDelay = DelayConfig.Fixed(200L),
        actions =
          listOf(
            ScriptAction.IfBlock(
              id = UUID.randomUUID().toString(),
              name = "Check Sample Pixel",
              conditionRoot =
                ConditionGroup(
                  id = UUID.randomUUID().toString(),
                  name = "Primary Color Check",
                  operator = LogicOperator.ALL,
                  children =
                    listOf(
                      ConditionLeaf(
                        id = UUID.randomUUID().toString(),
                        label = "Green Target",
                        predicate =
                          ConditionPredicate.ColorMatch(
                            mode = ColorMatchMode.SINGLE_POINT,
                            targets =
                              listOf(
                                ColorTarget(
                                  x = 540,
                                  y = 960,
                                  hexColor = "#4CAF50",
                                  tolerance = 15,
                                ),
                              ),
                          ),
                      ),
                    ),
                ),
              thenActions =
                listOf(
                  ScriptAction.Tap(
                    id = UUID.randomUUID().toString(),
                    x = NumericInput.Literal(540),
                    y = NumericInput.Literal(960),
                    jitterPx = 4,
                    postDelay = PostActionDelay.Override(DelayConfig.Fixed(250L)),
                  ),
                ),
              elseIfBranches =
                listOf(
                  ScriptAction.ElseIfBranch(
                    id = UUID.randomUUID().toString(),
                    name = "Blue Fallback",
                    conditionRoot =
                      ConditionGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Secondary Color Check",
                        operator = LogicOperator.ALL,
                        children =
                          listOf(
                            ConditionLeaf(
                              id = UUID.randomUUID().toString(),
                              label = "Blue Target",
                              predicate =
                                ConditionPredicate.ColorMatch(
                                  mode = ColorMatchMode.SINGLE_POINT,
                                  targets =
                                    listOf(
                                      ColorTarget(
                                        x = 540,
                                        y = 1200,
                                        hexColor = "#2196F3",
                                        tolerance = 15,
                                      ),
                                    ),
                                ),
                            ),
                          ),
                      ),
                    actions =
                      listOf(
                        ScriptAction.SwipeDrag(
                          id = UUID.randomUUID().toString(),
                          startX = NumericInput.Literal(540),
                          startY = NumericInput.Literal(1400),
                          endX = NumericInput.Literal(540),
                          endY = NumericInput.Literal(600),
                          durationMs = 320L,
                        ),
                      ),
                  ),
                ),
              elseActions =
                listOf(
                  ScriptAction.Delay(
                    id = UUID.randomUUID().toString(),
                    config = DelayConfig.Fixed(300L),
                  ),
                ),
            ),
          ),
        createdAtMs = nowMs + 1L,
        updatedAtMs = nowMs + 1L,
      )

    return listOf(tapLoopScript, colorDecisionScript)
  }
}

internal object ScriptCloningUtils {
  suspend fun cloneScriptForDuplicate(
    source: Script,
    assetManager: ScriptAssetManager,
    nowMs: Long = System.currentTimeMillis(),
  ): Script {
    val newScriptId = UUID.randomUUID().toString()
    val clonedActions =
      cloneActions(
        actions = source.actions,
        copyTemplatePath = { existingPath ->
          if (existingPath.isBlank()) {
            existingPath
          } else {
            val file = File(existingPath)
            if (file.exists() && file.isFile) {
              runCatching { assetManager.copyCropToScriptAssets(existingPath, newScriptId) }
                .getOrDefault(existingPath)
            } else {
              existingPath
            }
          }
        },
      )
    val baseTitle = source.title.trim().ifBlank { "Untitled Script" }
    return source.copy(
      id = newScriptId,
      title = "$baseTitle (Copy)",
      actions = clonedActions,
      createdAtMs = nowMs,
      updatedAtMs = nowMs,
    )
  }

  fun clonePipelineForDuplicate(
    source: Pipeline,
    nowMs: Long = System.currentTimeMillis(),
  ): Pipeline {
    val baseTitle = source.title.trim().ifBlank { "Untitled Pipeline" }
    return source.copy(
      id = UUID.randomUUID().toString(),
      title = "$baseTitle (Copy)",
      steps = source.steps.map { step -> step.copy(id = UUID.randomUUID().toString()) },
      createdAtMs = nowMs,
      updatedAtMs = nowMs,
    )
  }

  internal suspend fun cloneActions(
    actions: List<ScriptAction>,
    copyTemplatePath: suspend (String) -> String = { it },
  ): List<ScriptAction> = actions.map { cloneAction(it, copyTemplatePath) }

  private suspend fun cloneAction(
    action: ScriptAction,
    copyTemplatePath: suspend (String) -> String,
  ): ScriptAction {
    val newId = UUID.randomUUID().toString()
    return when (action) {
      is ScriptAction.Tap -> action.copy(id = newId)
      is ScriptAction.DoubleTap -> action.copy(id = newId)
      is ScriptAction.LongPress ->
        action.copy(
          id = newId,
          holdUntilCondition = action.holdUntilCondition?.let { cloneConditionGroup(it, copyTemplatePath) },
        )
      is ScriptAction.MultiTouch -> action.copy(id = newId)
      is ScriptAction.SwipeDrag -> action.copy(id = newId)
      is ScriptAction.InputText -> action.copy(id = newId)
      is ScriptAction.IfBlock ->
        action.copy(
          id = newId,
          conditionRoot = cloneConditionGroup(action.conditionRoot, copyTemplatePath),
          thenActions = cloneActions(action.thenActions, copyTemplatePath),
          elseIfBranches =
            action.elseIfBranches.map { branch ->
              branch.copy(
                id = UUID.randomUUID().toString(),
                conditionRoot = cloneConditionGroup(branch.conditionRoot, copyTemplatePath),
                actions = cloneActions(branch.actions, copyTemplatePath),
              )
            },
          elseActions = cloneActions(action.elseActions, copyTemplatePath),
        )
      is ScriptAction.LoopBlock ->
        action.copy(
          id = newId,
          mode =
            when (val mode = action.mode) {
              is LoopMode.WhileCondition ->
                mode.copy(conditionRoot = cloneConditionGroup(mode.conditionRoot, copyTemplatePath))
              is LoopMode.UntilCondition ->
                mode.copy(conditionRoot = cloneConditionGroup(mode.conditionRoot, copyTemplatePath))
              is LoopMode.Count,
              LoopMode.Unlimited -> mode
            },
          bodyActions = cloneActions(action.bodyActions, copyTemplatePath),
        )
      is ScriptAction.Delay -> action.copy(id = newId)
      is ScriptAction.Break -> ScriptAction.Break(id = newId)
      is ScriptAction.Continue -> ScriptAction.Continue(id = newId)
      is ScriptAction.EndScript -> ScriptAction.EndScript(id = newId)
      is ScriptAction.KeyEvent -> action.copy(id = newId)
      is ScriptAction.LaunchApp -> action.copy(id = newId)
      is ScriptAction.ForceStopApp -> action.copy(id = newId)
      is ScriptAction.ClipboardOp -> action.copy(id = newId)
      is ScriptAction.Vibrate -> action.copy(id = newId)
      is ScriptAction.PlayAlertSound -> action.copy(id = newId)
      is ScriptAction.SetVariable -> action.copy(id = newId)
      is ScriptAction.MathOp -> action.copy(id = newId)
      is ScriptAction.RandomOp -> action.copy(id = newId)
      is ScriptAction.StringOp -> action.copy(id = newId)
      is ScriptAction.BooleanOp -> action.copy(id = newId)
      is ScriptAction.TypeCastOp -> action.copy(id = newId)
    }
  }

  private suspend fun cloneConditionGroup(
    group: ConditionGroup,
    copyTemplatePath: suspend (String) -> String,
  ): ConditionGroup {
    return group.copy(
      id = UUID.randomUUID().toString(),
      children =
        group.children.map { node ->
          cloneConditionNode(node, copyTemplatePath)
        },
    )
  }

  private suspend fun cloneConditionNode(
    node: ConditionNode,
    copyTemplatePath: suspend (String) -> String,
  ): ConditionNode {
    return when (node) {
      is ConditionGroup -> cloneConditionGroup(node, copyTemplatePath)
      is ConditionLeaf -> {
        val nextPredicate =
          when (val predicate = node.predicate) {
            is ConditionPredicate.ImageMatch ->
              predicate.copy(templatePngPath = copyTemplatePath(predicate.templatePngPath))
            else -> predicate
          }
        node.copy(
          id = UUID.randomUUID().toString(),
          predicate = nextPredicate,
        )
      }
    }
  }
}
