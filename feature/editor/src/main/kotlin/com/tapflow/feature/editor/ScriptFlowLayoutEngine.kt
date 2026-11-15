package com.tapflow.feature.editor

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.script.Script
import kotlin.math.max
import kotlin.math.min

@Immutable
data class FlowPoint(
  val x: Float,
  val y: Float,
)

enum class FlowTerminationType {
  BREAK,
  CONTINUE,
  END_SCRIPT,
}

enum class FlowEdgeKind {
  NORMAL,
  BRANCH_YES,
  BRANCH_NO,
  MERGE_RAIL,
  LOOP_BACK,
}

@Immutable
sealed interface FlowNode {
  val id: String
  val x: Float
  val y: Float
  val width: Float
  val height: Float

  @Immutable
  data class StartAnchor(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
  ) : FlowNode

  @Immutable
  data class EndAnchor(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
  ) : FlowNode

  @Immutable
  data class EmptyPlaceholder(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val isScriptEmpty: Boolean = false,
  ) : FlowNode

  @Immutable
  data class ActionCard(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val action: ScriptAction,
    val title: String,
    val summary: String,
  ) : FlowNode

  @Immutable
  data class DecisionCard(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val isElseIf: Boolean,
    val title: String,
    val conditionSummary: String,
  ) : FlowNode

  @Immutable
  data class ElseHeaderPill(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
  ) : FlowNode

  @Immutable
  data class LoopHeaderCard(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val title: String,
    val summary: String,
  ) : FlowNode

  @Immutable
  data class EarlyTerminationPill(
    override val id: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val terminationType: FlowTerminationType,
  ) : FlowNode
}

@Immutable
data class FlowContainer(
  val id: String,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val gutterX: Float,
)

@Immutable
data class FlowEdge(
  val id: String,
  val points: List<FlowPoint>,
  val kind: FlowEdgeKind,
  val hasArrowhead: Boolean = true,
  val hasEndDot: Boolean = false,
  val labelKind: FlowBranchLabelKind? = null,
  val labelAnchor: FlowPoint? = null,
)

enum class FlowBranchLabelKind {
  YES,
  NO,
  LOOP_RETURN,
}

@Immutable
data class FlowLayoutResult(
  val nodes: List<FlowNode>,
  val edges: List<FlowEdge>,
  val containers: List<FlowContainer>,
  val totalWidth: Float,
  val totalHeight: Float,
)

/**
 * Deterministic 2-pass recursive AST bounding-box flow layout calculator.
 * All coordinates are expressed in Float dp units so Compose Canvas and Node Cards align 1:1.
 *
 * Pass 1: Bottom-up bounding-box & entry/exit spine measurement.
 * Pass 2: Top-down absolute positioning emitting nodes, containers, and orthogonal/routed edges.
 */
object ScriptFlowLayoutEngine {

  const val OUTER_PAD = 32f
  const val V_GAP = 28f
  const val ANCHOR_WIDTH = 132f
  const val ANCHOR_HEIGHT = 38f
  const val EMPTY_SCRIPT_WIDTH = 184f
  const val EMPTY_SCRIPT_HEIGHT = 44f
  const val EMPTY_BRANCH_WIDTH = 138f
  const val EMPTY_BRANCH_HEIGHT = 34f
  const val ACTION_WIDTH = 192f
  const val ACTION_HEIGHT = 56f
  const val DECISION_WIDTH = 192f
  const val DECISION_HEIGHT = 60f
  const val ELSE_HEADER_WIDTH = 160f
  const val ELSE_HEADER_HEIGHT = 36f
  const val TERM_WIDTH = 164f
  const val TERM_HEIGHT = 40f
  const val LOOP_HEADER_WIDTH = 192f
  const val LOOP_HEADER_HEIGHT = 48f

  const val COL_GAP = 32f
  const val LADDER_STEP_Y = 54f
  const val MERGE_GAP = 24f
  const val BYPASS_COL_WIDTH = 44f

  const val LOOP_PAD_LEFT = 16f
  const val LOOP_PAD_TOP = 14f
  const val LOOP_BODY_TOP_GAP = 26f
  const val LOOP_BODY_BOTTOM_GAP = 22f
  const val LOOP_GUTTER_RIGHT = 26f
  const val LOOP_PAD_RIGHT = 14f
  const val LOOP_PAD_BOTTOM = 12f

  private sealed interface Recipe {
    val width: Float
    val height: Float
    val entryX: Float
    val exitX: Float?
    val terminatesEarly: Boolean
  }

  private data class SequenceSlot(
    val child: Recipe,
    val relX: Float,
    val relY: Float,
  )

  private data class SequenceRecipe(
    override val width: Float,
    override val height: Float,
    override val entryX: Float,
    override val exitX: Float?,
    override val terminatesEarly: Boolean,
    val slots: List<SequenceSlot>,
  ) : Recipe

  private data class SingleNodeRecipe(
    override val width: Float,
    override val height: Float,
    override val entryX: Float,
    override val exitX: Float?,
    override val terminatesEarly: Boolean,
    val factory: (absX: Float, absY: Float) -> FlowNode,
  ) : Recipe

  private data class MeasuredIfColumn(
    val key: String,
    val isBypassOnly: Boolean,
    val isElseColumn: Boolean,
    val isElseIf: Boolean,
    val headerTitle: String,
    val headerSubtitle: String,
    val headerWidth: Float,
    val headerHeight: Float,
    val decCenterRelX: Float,
    val colWidth: Float,
    val relX: Float,
    val relTopY: Float,
    val bodyOffsetY: Float,
    val body: Recipe?,
    val branchBottomRelY: Float,
    val continues: Boolean,
  )

  private data class IfBlockRecipe(
    val blockId: String,
    override val width: Float,
    override val height: Float,
    override val entryX: Float,
    override val exitX: Float?,
    override val terminatesEarly: Boolean,
    val columns: List<MeasuredIfColumn>,
    val mergeRailY: Float?,
  ) : Recipe

  private data class LoopBlockRecipe(
    val blockId: String,
    override val width: Float,
    override val height: Float,
    override val entryX: Float,
    override val exitX: Float?,
    override val terminatesEarly: Boolean,
    val headerTitle: String,
    val headerSummary: String,
    val headerRelX: Float,
    val headerRelY: Float,
    val bodyRelX: Float,
    val bodyRelY: Float,
    val body: Recipe,
    val loopReturnRelY: Float,
    val gutterRelX: Float,
  ) : Recipe

  fun computeLayout(script: Script): FlowLayoutResult {
    return computeLayout(script.actions)
  }

  fun computeLayout(actions: List<ScriptAction>): FlowLayoutResult {
    // Pass 1: Measure Start -> Body -> End spine
    val startRecipe =
      SingleNodeRecipe(
        width = ANCHOR_WIDTH,
        height = ANCHOR_HEIGHT,
        entryX = ANCHOR_WIDTH / 2f,
        exitX = ANCHOR_WIDTH / 2f,
        terminatesEarly = false,
      ) { x, y ->
        FlowNode.StartAnchor(id = "start", x = x, y = y, width = ANCHOR_WIDTH, height = ANCHOR_HEIGHT)
      }

    val bodyRecipe =
      if (actions.isEmpty()) {
        SingleNodeRecipe(
          width = EMPTY_SCRIPT_WIDTH,
          height = EMPTY_SCRIPT_HEIGHT,
          entryX = EMPTY_SCRIPT_WIDTH / 2f,
          exitX = EMPTY_SCRIPT_WIDTH / 2f,
          terminatesEarly = false,
        ) { x, y ->
          FlowNode.EmptyPlaceholder(
            id = "empty_script_placeholder",
            x = x,
            y = y,
            width = EMPTY_SCRIPT_WIDTH,
            height = EMPTY_SCRIPT_HEIGHT,
            isScriptEmpty = true,
          )
        }
      } else {
        measureSequence(actions, branchOwnerId = "root")
      }

    val endRecipe =
      SingleNodeRecipe(
        width = ANCHOR_WIDTH,
        height = ANCHOR_HEIGHT,
        entryX = ANCHOR_WIDTH / 2f,
        exitX = ANCHOR_WIDTH / 2f,
        terminatesEarly = true,
      ) { x, y ->
        FlowNode.EndAnchor(id = "end", x = x, y = y, width = ANCHOR_WIDTH, height = ANCHOR_HEIGHT)
      }

    val rootRecipes =
      if (bodyRecipe.terminatesEarly) {
        listOf(startRecipe, bodyRecipe)
      } else {
        listOf(startRecipe, bodyRecipe, endRecipe)
      }
    val rootSequence = combineSequence(rootRecipes)

    // Pass 2: Absolute Positioning
    val nodes = mutableListOf<FlowNode>()
    val edges = mutableListOf<FlowEdge>()
    val containers = mutableListOf<FlowContainer>()
    var edgeSeq = 0
    fun nextEdgeId(tag: String): String = "edge_${tag}_${edgeSeq++}"

    emitRecipe(
      recipe = rootSequence,
      originX = OUTER_PAD,
      originY = OUTER_PAD,
      nodes = nodes,
      edges = edges,
      containers = containers,
      nextEdgeId = ::nextEdgeId,
    )

    val totalWidth = rootSequence.width + (OUTER_PAD * 2f)
    val totalHeight = rootSequence.height + (OUTER_PAD * 2f)

    return FlowLayoutResult(
      nodes = nodes,
      edges = edges,
      containers = containers,
      totalWidth = totalWidth,
      totalHeight = totalHeight,
    )
  }

  // ---------------------------------------------------------------------------
  // Pass 1: Bottom-Up Measurement
  // ---------------------------------------------------------------------------

  private fun measureSequence(actions: List<ScriptAction>, branchOwnerId: String): Recipe {
    if (actions.isEmpty()) {
      return SingleNodeRecipe(
        width = EMPTY_BRANCH_WIDTH,
        height = EMPTY_BRANCH_HEIGHT,
        entryX = EMPTY_BRANCH_WIDTH / 2f,
        exitX = EMPTY_BRANCH_WIDTH / 2f,
        terminatesEarly = false,
      ) { x, y ->
        FlowNode.EmptyPlaceholder(
          id = "empty_branch_$branchOwnerId",
          x = x,
          y = y,
          width = EMPTY_BRANCH_WIDTH,
          height = EMPTY_BRANCH_HEIGHT,
          isScriptEmpty = false,
        )
      }
    }
    val children = actions.map { measureAction(it) }
    return combineSequence(children)
  }

  private fun combineSequence(children: List<Recipe>): SequenceRecipe {
    val spineX = children.maxOf { it.entryX }
    val maxRight = children.maxOf { it.width - it.entryX }
    val totalWidth = spineX + maxRight

    var cursorY = 0f
    val slots = mutableListOf<SequenceSlot>()
    children.forEachIndexed { idx, child ->
      val relX = spineX - child.entryX
      slots += SequenceSlot(child = child, relX = relX, relY = cursorY)
      cursorY += child.height
      if (idx < children.lastIndex) {
        cursorY += V_GAP
      }
    }

    val lastChild = children.last()
    val terminatesEarly = children.any { it.terminatesEarly }
    val exitX = if (terminatesEarly) null else lastChild.exitX?.let { spineX - lastChild.entryX + it }

    return SequenceRecipe(
      width = totalWidth,
      height = cursorY,
      entryX = spineX,
      exitX = exitX,
      terminatesEarly = terminatesEarly,
      slots = slots,
    )
  }

  private fun measureAction(action: ScriptAction): Recipe =
    when (action) {
      is ScriptAction.IfBlock -> measureIfBlock(action)
      is ScriptAction.LoopBlock -> measureLoopBlock(action)
      is ScriptAction.Break ->
        SingleNodeRecipe(
          width = TERM_WIDTH,
          height = TERM_HEIGHT,
          entryX = TERM_WIDTH / 2f,
          exitX = null,
          terminatesEarly = true,
        ) { x, y ->
          FlowNode.EarlyTerminationPill(
            id = "action_${action.id}",
            x = x,
            y = y,
            width = TERM_WIDTH,
            height = TERM_HEIGHT,
            terminationType = FlowTerminationType.BREAK,
          )
        }
      is ScriptAction.Continue ->
        SingleNodeRecipe(
          width = TERM_WIDTH,
          height = TERM_HEIGHT,
          entryX = TERM_WIDTH / 2f,
          exitX = null,
          terminatesEarly = true,
        ) { x, y ->
          FlowNode.EarlyTerminationPill(
            id = "action_${action.id}",
            x = x,
            y = y,
            width = TERM_WIDTH,
            height = TERM_HEIGHT,
            terminationType = FlowTerminationType.CONTINUE,
          )
        }
      is ScriptAction.EndScript ->
        SingleNodeRecipe(
          width = TERM_WIDTH,
          height = TERM_HEIGHT,
          entryX = TERM_WIDTH / 2f,
          exitX = null,
          terminatesEarly = true,
        ) { x, y ->
          FlowNode.EarlyTerminationPill(
            id = "action_${action.id}",
            x = x,
            y = y,
            width = TERM_WIDTH,
            height = TERM_HEIGHT,
            terminationType = FlowTerminationType.END_SCRIPT,
          )
        }
      else ->
        SingleNodeRecipe(
          width = ACTION_WIDTH,
          height = ACTION_HEIGHT,
          entryX = ACTION_WIDTH / 2f,
          exitX = ACTION_WIDTH / 2f,
          terminatesEarly = false,
        ) { x, y ->
          FlowNode.ActionCard(
            id = "action_${action.id}",
            x = x,
            y = y,
            width = ACTION_WIDTH,
            height = ACTION_HEIGHT,
            action = action,
            title = ActionTreeUtils.actionTypeTitle(action),
            summary = conciseActionSummary(action),
          )
        }
    }

  private fun measureIfBlock(action: ScriptAction.IfBlock): IfBlockRecipe {
    val rawColumns = mutableListOf<MeasuredIfColumn>()

    // Column 0: Primary "If:" branch
    val thenBody = measureSequence(action.thenActions, branchOwnerId = "${action.id}_then")
    val thenDecCenter = max(DECISION_WIDTH / 2f, thenBody.entryX)
    val thenColWidth = thenDecCenter + max(DECISION_WIDTH / 2f, thenBody.width - thenBody.entryX)
    val thenClause =
      PlainLanguageFormatter.formatConditionClause(action.conditionRoot).ifBlank { "No rule set" }
    rawColumns +=
      MeasuredIfColumn(
        key = "if_dec_${action.id}",
        isBypassOnly = false,
        isElseColumn = false,
        isElseIf = false,
        headerTitle = action.name.trim().ifBlank { "If:" },
        headerSubtitle = thenClause,
        headerWidth = DECISION_WIDTH,
        headerHeight = DECISION_HEIGHT,
        decCenterRelX = thenDecCenter,
        colWidth = thenColWidth,
        relX = 0f,
        relTopY = 0f,
        bodyOffsetY = DECISION_HEIGHT + V_GAP,
        body = thenBody,
        branchBottomRelY = DECISION_HEIGHT + V_GAP + thenBody.height,
        continues = !thenBody.terminatesEarly,
      )

    // Columns 1..M: Ordered "If not, check:" (ElseIf) branches
    action.elseIfBranches.forEachIndexed { idx, branch ->
      val colIdx = idx + 1
      val body = measureSequence(branch.actions, branchOwnerId = "${action.id}_ei_${branch.id}")
      val decCenter = max(DECISION_WIDTH / 2f, body.entryX)
      val colWidth = decCenter + max(DECISION_WIDTH / 2f, body.width - body.entryX)
      val topY = colIdx * LADDER_STEP_Y
      val clause =
        PlainLanguageFormatter.formatConditionClause(branch.conditionRoot).ifBlank { "No rule set" }
      rawColumns +=
        MeasuredIfColumn(
          key = "elseif_dec_${branch.id}",
          isBypassOnly = false,
          isElseColumn = false,
          isElseIf = true,
          headerTitle = branch.name.trim().ifBlank { "If not, check:" },
          headerSubtitle = clause,
          headerWidth = DECISION_WIDTH,
          headerHeight = DECISION_HEIGHT,
          decCenterRelX = decCenter,
          colWidth = colWidth,
          relX = 0f,
          relTopY = topY,
          bodyOffsetY = DECISION_HEIGHT + V_GAP,
          body = body,
          branchBottomRelY = topY + DECISION_HEIGHT + V_GAP + body.height,
          continues = !body.terminatesEarly,
        )
    }

    // Final Column: Either populated Else column OR clean bypass line
    val finalIdx = rawColumns.size
    val finalTopY = finalIdx * LADDER_STEP_Y
    if (action.elseActions.isNotEmpty()) {
      val elseBody = measureSequence(action.elseActions, branchOwnerId = "${action.id}_else")
      val hdrCenter = max(ELSE_HEADER_WIDTH / 2f, elseBody.entryX)
      val colWidth = hdrCenter + max(ELSE_HEADER_WIDTH / 2f, elseBody.width - elseBody.entryX)
      rawColumns +=
        MeasuredIfColumn(
          key = "else_hdr_${action.id}",
          isBypassOnly = false,
          isElseColumn = true,
          isElseIf = false,
          headerTitle = "If none match",
          headerSubtitle = "",
          headerWidth = ELSE_HEADER_WIDTH,
          headerHeight = ELSE_HEADER_HEIGHT,
          decCenterRelX = hdrCenter,
          colWidth = colWidth,
          relX = 0f,
          relTopY = finalTopY,
          bodyOffsetY = ELSE_HEADER_HEIGHT + V_GAP,
          body = elseBody,
          branchBottomRelY = finalTopY + ELSE_HEADER_HEIGHT + V_GAP + elseBody.height,
          continues = !elseBody.terminatesEarly,
        )
    } else {
      val prevDecCenterY = rawColumns.last().relTopY + (DECISION_HEIGHT / 2f)
      rawColumns +=
        MeasuredIfColumn(
          key = "else_bypass_${action.id}",
          isBypassOnly = true,
          isElseColumn = true,
          isElseIf = false,
          headerTitle = "",
          headerSubtitle = "",
          headerWidth = 0f,
          headerHeight = 0f,
          decCenterRelX = BYPASS_COL_WIDTH / 2f,
          colWidth = BYPASS_COL_WIDTH,
          relX = 0f,
          relTopY = prevDecCenterY,
          bodyOffsetY = 0f,
          body = null,
          branchBottomRelY = prevDecCenterY + 18f,
          continues = true,
        )
    }

    // Assign non-overlapping horizontal X offsets across sibling columns
    var cursorX = 0f
    val positionedColumns =
      rawColumns.mapIndexed { idx, col ->
        val placed = col.copy(relX = cursorX)
        cursorX += col.colWidth
        if (idx < rawColumns.lastIndex) {
          cursorX += COL_GAP
        }
        placed
      }

    val totalWidth = cursorX
    val entryX = positionedColumns.first().relX + positionedColumns.first().decCenterRelX
    val maxBranchBottom = positionedColumns.maxOf { it.branchBottomRelY }
    val anyContinues = positionedColumns.any { it.continues }
    val mergeRailY = if (anyContinues) maxBranchBottom + MERGE_GAP else null
    val totalHeight = mergeRailY ?: maxBranchBottom

    return IfBlockRecipe(
      blockId = action.id,
      width = totalWidth,
      height = totalHeight,
      entryX = entryX,
      exitX = if (anyContinues) entryX else null,
      terminatesEarly = !anyContinues,
      columns = positionedColumns,
      mergeRailY = mergeRailY,
    )
  }

  private fun measureLoopBlock(action: ScriptAction.LoopBlock): LoopBlockRecipe {
    val body = measureSequence(action.bodyActions, branchOwnerId = "loop_${action.id}")
    val spineOffsetInInner = max(LOOP_HEADER_WIDTH / 2f, body.entryX)
    val rightExtentInInner = max(LOOP_HEADER_WIDTH / 2f, body.width - body.entryX)
    val innerWidth = spineOffsetInInner + rightExtentInInner

    val totalWidth = LOOP_PAD_LEFT + innerWidth + LOOP_GUTTER_RIGHT + LOOP_PAD_RIGHT
    val entryX = LOOP_PAD_LEFT + spineOffsetInInner
    val headerRelX = entryX - (LOOP_HEADER_WIDTH / 2f)
    val headerRelY = LOOP_PAD_TOP
    val bodyRelX = entryX - body.entryX
    val bodyRelY = headerRelY + LOOP_HEADER_HEIGHT + LOOP_BODY_TOP_GAP
    val bodyBottomRelY = bodyRelY + body.height
    val loopReturnRelY = bodyBottomRelY + LOOP_BODY_BOTTOM_GAP
    val totalHeight = loopReturnRelY + LOOP_PAD_BOTTOM
    val gutterRelX = totalWidth - LOOP_PAD_RIGHT - (LOOP_GUTTER_RIGHT / 2f)

    val (title, summary) = loopHeaderTexts(action)

    return LoopBlockRecipe(
      blockId = action.id,
      width = totalWidth,
      height = totalHeight,
      entryX = entryX,
      exitX = entryX,
      terminatesEarly = false,
      headerTitle = title,
      headerSummary = summary,
      headerRelX = headerRelX,
      headerRelY = headerRelY,
      bodyRelX = bodyRelX,
      bodyRelY = bodyRelY,
      body = body,
      loopReturnRelY = loopReturnRelY,
      gutterRelX = gutterRelX,
    )
  }

  // ---------------------------------------------------------------------------
  // Pass 2: Top-Down Absolute Positioning & Edge Emission
  // ---------------------------------------------------------------------------

  private fun emitRecipe(
    recipe: Recipe,
    originX: Float,
    originY: Float,
    nodes: MutableList<FlowNode>,
    edges: MutableList<FlowEdge>,
    containers: MutableList<FlowContainer>,
    nextEdgeId: (String) -> String,
  ) {
    when (recipe) {
      is SingleNodeRecipe -> {
        nodes += recipe.factory(originX, originY)
      }

      is SequenceRecipe -> {
        recipe.slots.forEachIndexed { idx, slot ->
          val childX = originX + slot.relX
          val childY = originY + slot.relY
          emitRecipe(
            recipe = slot.child,
            originX = childX,
            originY = childY,
            nodes = nodes,
            edges = edges,
            containers = containers,
            nextEdgeId = nextEdgeId,
          )
          if (idx < recipe.slots.lastIndex && !slot.child.terminatesEarly) {
            val nextSlot = recipe.slots[idx + 1]
            val exitOffset = slot.child.exitX
            if (exitOffset != null) {
              val fromX = childX + exitOffset
              val fromY = childY + slot.child.height
              val toX = originX + nextSlot.relX + nextSlot.child.entryX
              val toY = originY + nextSlot.relY
              edges +=
                FlowEdge(
                  id = nextEdgeId("seq"),
                  points =
                    if (fromX == toX) {
                      listOf(FlowPoint(fromX, fromY), FlowPoint(toX, toY))
                    } else {
                      val midY = (fromY + toY) / 2f
                      listOf(
                        FlowPoint(fromX, fromY),
                        FlowPoint(fromX, midY),
                        FlowPoint(toX, midY),
                        FlowPoint(toX, toY),
                      )
                    },
                  kind = FlowEdgeKind.NORMAL,
                  hasArrowhead = true,
                )
            }
          }
        }
      }

      is IfBlockRecipe -> {
        val continuingRailXs = mutableListOf<Float>()
        val mergeRailAbsY = recipe.mergeRailY?.let { originY + it }

        recipe.columns.forEachIndexed { idx, col ->
          val colAbsX = originX + col.relX
          val colTopAbsY = originY + col.relTopY
          val colSpineAbsX = colAbsX + col.decCenterRelX

          if (col.isBypassOnly) {
            // Bypass vertical wire down from last decision's right-exit turn into the Merge Rail
            if (mergeRailAbsY != null) {
              edges +=
                FlowEdge(
                  id = nextEdgeId("if_bypass"),
                  points =
                    listOf(
                      FlowPoint(colSpineAbsX, colTopAbsY),
                      FlowPoint(colSpineAbsX, mergeRailAbsY),
                    ),
                  kind = FlowEdgeKind.BRANCH_NO,
                  hasArrowhead = false,
                  hasEndDot = true,
                )
              continuingRailXs += colSpineAbsX
            }
          } else {
            val hdrAbsX = colSpineAbsX - (col.headerWidth / 2f)
            if (col.isElseColumn) {
              nodes +=
                FlowNode.ElseHeaderPill(
                  id = col.key,
                  x = hdrAbsX,
                  y = colTopAbsY,
                  width = col.headerWidth,
                  height = col.headerHeight,
                )
            } else {
              nodes +=
                FlowNode.DecisionCard(
                  id = col.key,
                  x = hdrAbsX,
                  y = colTopAbsY,
                  width = col.headerWidth,
                  height = col.headerHeight,
                  isElseIf = col.isElseIf,
                  title = col.headerTitle,
                  conditionSummary = col.headerSubtitle,
                )
            }

            // Downward branch wire (✓ Yes for Decision nodes, normal down for Else header)
            val body = col.body
            if (body != null) {
              val bodyOriginX = colSpineAbsX - body.entryX
              val bodyOriginY = colTopAbsY + col.bodyOffsetY
              val headerBottomY = colTopAbsY + col.headerHeight

              edges +=
                FlowEdge(
                  id = nextEdgeId("if_down"),
                  points =
                    listOf(
                      FlowPoint(colSpineAbsX, headerBottomY),
                      FlowPoint(colSpineAbsX, bodyOriginY),
                    ),
                  kind = if (col.isElseColumn) FlowEdgeKind.NORMAL else FlowEdgeKind.BRANCH_YES,
                  hasArrowhead = true,
                  labelKind = if (col.isElseColumn) null else FlowBranchLabelKind.YES,
                  labelAnchor =
                    if (col.isElseColumn) {
                      null
                    } else {
                      FlowPoint(colSpineAbsX, (headerBottomY + bodyOriginY) / 2f)
                    },
                )

              emitRecipe(
                recipe = body,
                originX = bodyOriginX,
                originY = bodyOriginY,
                nodes = nodes,
                edges = edges,
                containers = containers,
                nextEdgeId = nextEdgeId,
              )

              // Downward continuation wire from branch bottom to shared Merge Rail
              if (col.continues && mergeRailAbsY != null && body.exitX != null) {
                val exitAbsX = bodyOriginX + body.exitX!!
                val bodyBottomAbsY = bodyOriginY + body.height
                edges +=
                  FlowEdge(
                    id = nextEdgeId("if_to_merge"),
                    points =
                      listOf(
                        FlowPoint(exitAbsX, bodyBottomAbsY),
                        FlowPoint(exitAbsX, mergeRailAbsY),
                      ),
                    kind = FlowEdgeKind.MERGE_RAIL,
                    hasArrowhead = false,
                    hasEndDot = true,
                  )
                continuingRailXs += exitAbsX
              }
            }

            // Rightward (✗ No) wire from this Decision node to the next column
            if (!col.isElseColumn && idx < recipe.columns.lastIndex) {
              val nextCol = recipe.columns[idx + 1]
              val decRightX = hdrAbsX + col.headerWidth
              val decCenterY = colTopAbsY + (col.headerHeight / 2f)
              val nextCenterX = originX + nextCol.relX + nextCol.decCenterRelX
              val nextTopY = originY + nextCol.relTopY

              val edgePoints =
                if (nextCol.isBypassOnly) {
                  listOf(
                    FlowPoint(decRightX, decCenterY),
                    FlowPoint(nextCenterX, decCenterY),
                  )
                } else {
                  listOf(
                    FlowPoint(decRightX, decCenterY),
                    FlowPoint(nextCenterX, decCenterY),
                    FlowPoint(nextCenterX, nextTopY),
                  )
                }

              edges +=
                FlowEdge(
                  id = nextEdgeId("if_no"),
                  points = edgePoints,
                  kind = FlowEdgeKind.BRANCH_NO,
                  hasArrowhead = !nextCol.isBypassOnly,
                  labelKind = FlowBranchLabelKind.NO,
                  labelAnchor = FlowPoint((decRightX + nextCenterX) / 2f, decCenterY),
                )
            }
          }
        }

        // Emit horizontal Merge Rail connecting all continuing branches back to the main spine
        if (mergeRailAbsY != null && continuingRailXs.isNotEmpty() && recipe.exitX != null) {
          val spineExitAbsX = originX + recipe.exitX
          val minRailX = min(continuingRailXs.min(), spineExitAbsX)
          val maxRailX = max(continuingRailXs.max(), spineExitAbsX)
          if (maxRailX > minRailX) {
            edges +=
              FlowEdge(
                id = nextEdgeId("merge_rail"),
                points =
                  listOf(
                    FlowPoint(minRailX, mergeRailAbsY),
                    FlowPoint(maxRailX, mergeRailAbsY),
                  ),
                kind = FlowEdgeKind.MERGE_RAIL,
                hasArrowhead = false,
                hasEndDot = false,
              )
          }
        }
      }

      is LoopBlockRecipe -> {
        val headerAbsX = originX + recipe.headerRelX
        val headerAbsY = originY + recipe.headerRelY
        val bodyAbsX = originX + recipe.bodyRelX
        val bodyAbsY = originY + recipe.bodyRelY
        val gutterAbsX = originX + recipe.gutterRelX
        val loopReturnAbsY = originY + recipe.loopReturnRelY

        containers +=
          FlowContainer(
            id = "loop_box_${recipe.blockId}",
            x = originX,
            y = originY,
            width = recipe.width,
            height = recipe.height,
            gutterX = gutterAbsX,
          )

        nodes +=
          FlowNode.LoopHeaderCard(
            id = "loop_hdr_${recipe.blockId}",
            x = headerAbsX,
            y = headerAbsY,
            width = LOOP_HEADER_WIDTH,
            height = LOOP_HEADER_HEIGHT,
            title = recipe.headerTitle,
            summary = recipe.headerSummary,
          )

        val headerCenterX = headerAbsX + (LOOP_HEADER_WIDTH / 2f)
        val headerBottomY = headerAbsY + LOOP_HEADER_HEIGHT
        val headerRightX = headerAbsX + LOOP_HEADER_WIDTH
        val headerCenterY = headerAbsY + (LOOP_HEADER_HEIGHT / 2f)
        val bodyEntryAbsX = bodyAbsX + recipe.body.entryX

        // Header -> Body down arrow
        edges +=
          FlowEdge(
            id = nextEdgeId("loop_enter"),
            points =
              listOf(
                FlowPoint(headerCenterX, headerBottomY),
                FlowPoint(bodyEntryAbsX, bodyAbsY),
              ),
            kind = FlowEdgeKind.NORMAL,
            hasArrowhead = true,
          )

        emitRecipe(
          recipe = recipe.body,
          originX = bodyAbsX,
          originY = bodyAbsY,
          nodes = nodes,
          edges = edges,
          containers = containers,
          nextEdgeId = nextEdgeId,
        )

        // Upward Loop-Back Arrow (⟲) inside the dedicated right-side 26.dp gutter
        val bodyExitOffset = recipe.body.exitX
        if (!recipe.body.terminatesEarly && bodyExitOffset != null) {
          val bodyExitAbsX = bodyAbsX + bodyExitOffset
          val bodyBottomAbsY = bodyAbsY + recipe.body.height
          edges +=
            FlowEdge(
              id = nextEdgeId("loop_back"),
              points =
                listOf(
                  FlowPoint(bodyExitAbsX, bodyBottomAbsY),
                  FlowPoint(bodyExitAbsX, loopReturnAbsY),
                  FlowPoint(gutterAbsX, loopReturnAbsY),
                  FlowPoint(gutterAbsX, headerCenterY),
                  FlowPoint(headerRightX, headerCenterY),
                ),
              kind = FlowEdgeKind.LOOP_BACK,
              hasArrowhead = true,
              labelKind = FlowBranchLabelKind.LOOP_RETURN,
              labelAnchor = FlowPoint(gutterAbsX, (headerCenterY + loopReturnAbsY) / 2f),
            )
        }
      }
    }
  }

  private fun loopHeaderTexts(action: ScriptAction.LoopBlock): Pair<String, String> {
    val customName = action.name.trim().takeIf { it.isNotBlank() && it != "Loop" }
    val modeDesc =
      when (val m = action.mode) {
        is LoopMode.Count -> "Repeat ${ActionTreeUtils.formatNumericInput(m.count)}×"
        is LoopMode.WhileCondition -> {
          val clause = PlainLanguageFormatter.formatConditionClause(m.conditionRoot)
          if (clause.isBlank()) "Repeat While…" else "While: $clause"
        }
        is LoopMode.UntilCondition -> {
          val clause = PlainLanguageFormatter.formatConditionClause(m.conditionRoot)
          if (clause.isBlank()) "Repeat Until…" else "Until: $clause"
        }
        LoopMode.Unlimited -> "Repeat Forever"
      }
    return if (customName != null) {
      customName to modeDesc
    } else {
      modeDesc to "${action.bodyActions.size} steps"
    }
  }

  private fun conciseActionSummary(action: ScriptAction): String =
    when (action) {
      is ScriptAction.Tap -> {
        val base = "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)})"
        if (action.repeatCount != NumericInput.Literal(1)) {
          "$base ×${ActionTreeUtils.formatNumericInput(action.repeatCount)}"
        } else {
          base
        }
      }
      is ScriptAction.DoubleTap ->
        "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)}) • ${action.tapIntervalMs}ms"
      is ScriptAction.LongPress ->
        "(${ActionTreeUtils.formatNumericInput(action.x)}, ${ActionTreeUtils.formatNumericInput(action.y)}) • ${ActionTreeUtils.formatDurationCompact(action.pressDurationMs)}"
      is ScriptAction.MultiTouch ->
        when (action.mode) {
          com.tapflow.core.model.action.MultiTouchMode.CUSTOM_POINTS ->
            "${action.customPoints.size} fingers • ${ActionTreeUtils.formatDurationCompact(action.durationMs)}"
          com.tapflow.core.model.action.MultiTouchMode.PINCH_SPREAD ->
            "Pinch / Zoom • ${ActionTreeUtils.formatDurationCompact(action.durationMs)}"
          com.tapflow.core.model.action.MultiTouchMode.ROTATE ->
            "Rotate • ${ActionTreeUtils.formatDurationCompact(action.durationMs)}"
        }
      is ScriptAction.SwipeDrag ->
        "(${ActionTreeUtils.formatNumericInput(action.startX)},${ActionTreeUtils.formatNumericInput(action.startY)}) ➔ (${ActionTreeUtils.formatNumericInput(action.endX)},${ActionTreeUtils.formatNumericInput(action.endY)})"
      is ScriptAction.InputText ->
        "\"${action.textTemplate.take(20)}\""
      is ScriptAction.Delay ->
        "Wait ${ActionTreeUtils.formatDelayConfig(action.config)}"
      is ScriptAction.KeyEvent ->
        "Press ${PlainLanguageFormatter.systemKeyLabel(action.key)}"
      is ScriptAction.LaunchApp ->
        action.appLabel.ifBlank { action.packageName }.ifBlank { "Select app" }
      is ScriptAction.ForceStopApp ->
        "Stop ${action.appLabel.ifBlank { action.packageName }.ifBlank { "app" }}"
      is ScriptAction.ClipboardOp -> {
        val op = PlainLanguageFormatter.clipboardOpLabel(action.operation)
        val detail = action.targetVariableKey?.let { "{$it}" } ?: action.valueTemplate.take(16)
        if (detail.isBlank()) op else "$op: $detail"
      }
      is ScriptAction.Vibrate -> PlainLanguageFormatter.vibratePatternLabel(action.pattern)
      is ScriptAction.PlayAlertSound -> "Play ${action.soundUriOrType}"
      is ScriptAction.SetVariable -> "{${action.targetKey}} = \"${action.valueTemplate.take(14)}\""
      is ScriptAction.MathOp ->
        "{${action.targetKey}} = ${action.leftOperand} ${PlainLanguageFormatter.mathOpSymbol(action.operator)} ${action.rightOperand}"
      is ScriptAction.RandomOp ->
        "{${action.targetKey}} = random ${PlainLanguageFormatter.randomTargetLabel(action.targetType)}"
      is ScriptAction.StringOp ->
        "{${action.targetKey}} = ${PlainLanguageFormatter.stringOpLabel(action.operator)}"
      is ScriptAction.BooleanOp ->
        "{${action.targetKey}} = ${PlainLanguageFormatter.booleanOpLabel(action.operator)}"
      is ScriptAction.TypeCastOp ->
        "{${action.targetKey}} = ${PlainLanguageFormatter.typeCastLabel(action.operator)}"
      is ScriptAction.IfBlock,
      is ScriptAction.LoopBlock,
      is ScriptAction.Break,
      is ScriptAction.Continue,
      is ScriptAction.EndScript -> ""
    }
}
