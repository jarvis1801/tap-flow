package com.tapflow.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.model.action.LoopMode
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val MIN_FLOW_ZOOM = 0.25f
private const val MAX_FLOW_ZOOM = 2.5f

@Composable
fun ScriptFlowScreen(
  onNavigateBack: () -> Unit,
  viewModel: ScriptFlowViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  BackHandler {
    onNavigateBack()
  }

  ScriptFlowContent(
    state = state,
    onNavigateBack = onNavigateBack,
  )
}

@Composable
fun ScriptFlowContent(
  state: ScriptFlowUiState,
  onNavigateBack: () -> Unit,
) {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()

  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  var hasAppliedInitialFit by remember { mutableStateOf(false) }

  val scaleAnim = remember { Animatable(1f) }
  val panXAnim = remember { Animatable(0f) }
  val panYAnim = remember { Animatable(0f) }

  fun computeFitTransform(
    viewport: IntSize,
    layout: FlowLayoutResult,
  ): Triple<Float, Float, Float> {
    if (viewport.width <= 0 || viewport.height <= 0) return Triple(1f, 0f, 0f)
    val diagramW = with(density) { layout.totalWidth.dp.toPx() }.coerceAtLeast(1f)
    val diagramH = with(density) { layout.totalHeight.dp.toPx() }.coerceAtLeast(1f)
    val padPx = with(density) { 20.dp.toPx() }
    val availW = (viewport.width - padPx * 2f).coerceAtLeast(64f)
    val availH = (viewport.height - padPx * 2f).coerceAtLeast(64f)
    val fitScale = min(availW / diagramW, availH / diagramH).coerceIn(MIN_FLOW_ZOOM, 1.35f)
    val targetX = (viewport.width - diagramW * fitScale) / 2f
    val targetY = (viewport.height - diagramH * fitScale) / 2f
    return Triple(fitScale, targetX, targetY)
  }

  fun compute100Transform(
    viewport: IntSize,
    layout: FlowLayoutResult,
  ): Triple<Float, Float, Float> {
    if (viewport.width <= 0 || viewport.height <= 0) return Triple(1f, 0f, 0f)
    val diagramW = with(density) { layout.totalWidth.dp.toPx() }
    val diagramH = with(density) { layout.totalHeight.dp.toPx() }
    val padPx = with(density) { 20.dp.toPx() }
    val targetX = (viewport.width - diagramW) / 2f
    val targetY =
      if (diagramH + padPx * 2f <= viewport.height) {
        (viewport.height - diagramH) / 2f
      } else {
        padPx
      }
    return Triple(1f, targetX, targetY)
  }

  fun clampPan(
    candidateX: Float,
    candidateY: Float,
    scale: Float,
    viewport: IntSize,
    layout: FlowLayoutResult,
  ): Offset {
    if (viewport.width <= 0 || viewport.height <= 0) return Offset(candidateX, candidateY)
    val scaledW = with(density) { layout.totalWidth.dp.toPx() } * scale
    val scaledH = with(density) { layout.totalHeight.dp.toPx() } * scale
    val marginPx = with(density) { 96.dp.toPx() }
    val minX = -(scaledW - marginPx).coerceAtLeast(0f)
    val maxX = (viewport.width - marginPx).coerceAtLeast(0f)
    val minY = -(scaledH - marginPx).coerceAtLeast(0f)
    val maxY = (viewport.height - marginPx).coerceAtLeast(0f)
    return Offset(
      x = candidateX.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
      y = candidateY.coerceIn(minOf(minY, maxY), maxOf(minY, maxY)),
    )
  }

  fun animateToTransform(target: Triple<Float, Float, Float>) {
    val spec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    coroutineScope.launch { scaleAnim.animateTo(target.first, spec) }
    coroutineScope.launch { panXAnim.animateTo(target.second, spec) }
    coroutineScope.launch { panYAnim.animateTo(target.third, spec) }
  }

  LaunchedEffect(state.layoutResult, viewportSize) {
    val layout = state.layoutResult ?: return@LaunchedEffect
    if (!hasAppliedInitialFit && viewportSize.width > 0 && viewportSize.height > 0) {
      val (fitScale, fitX, fitY) = computeFitTransform(viewportSize, layout)
      scaleAnim.snapTo(fitScale)
      panXAnim.snapTo(fitX)
      panYAnim.snapTo(fitY)
      hasAppliedInitialFit = true
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      ScriptFlowTopBar(
        title = state.scriptTitle,
        onNavigateBack = onNavigateBack,
      )
    },
  ) { innerPadding ->
    val layout = state.layoutResult
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.surface)
          .clipToBounds()
          .onSizeChanged { viewportSize = it },
    ) {
      if (state.isLoading || layout == null) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      } else {
        // Subtle dotted engineering grid background
        val gridDotColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        Canvas(modifier = Modifier.fillMaxSize()) {
          val spacing = 24.dp.toPx()
          val radius = 1.1.dp.toPx()
          var gx = spacing / 2f
          while (gx < size.width) {
            var gy = spacing / 2f
            while (gy < size.height) {
              drawCircle(color = gridDotColor, radius = radius, center = Offset(gx, gy))
              gy += spacing
            }
            gx += spacing
          }
        }

        // Interactive 1-finger pan + 2-finger pinch-to-zoom read-only diagram surface
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .pointerInput(layout, viewportSize) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                  coroutineScope.launch {
                    val oldScale = scaleAnim.value
                    val newScale = (oldScale * zoom).coerceIn(MIN_FLOW_ZOOM, MAX_FLOW_ZOOM)
                    val zoomRatio = newScale / oldScale
                    val rawX = centroid.x - (centroid.x - panXAnim.value) * zoomRatio + pan.x
                    val rawY = centroid.y - (centroid.y - panYAnim.value) * zoomRatio + pan.y
                    val clamped = clampPan(rawX, rawY, newScale, viewportSize, layout)
                    scaleAnim.snapTo(newScale)
                    panXAnim.snapTo(clamped.x)
                    panYAnim.snapTo(clamped.y)
                  }
                }
              }
              .testTag("flow_diagram_viewport"),
        ) {
          Box(
            modifier =
              Modifier
                .size(width = layout.totalWidth.dp, height = layout.totalHeight.dp)
                .graphicsLayer {
                  transformOrigin = TransformOrigin(0f, 0f)
                  scaleX = scaleAnim.value
                  scaleY = scaleAnim.value
                  translationX = panXAnim.value
                  translationY = panYAnim.value
                },
          ) {
            FlowDiagramConnectorsCanvas(
              layout = layout,
              modifier = Modifier.fillMaxSize(),
            )

            layout.edges.forEach { edge ->
              val anchor = edge.labelAnchor
              val labelKind = edge.labelKind
              if (anchor != null && labelKind != null) {
                key("lbl_${edge.id}") {
                  FlowEdgeLabelBadge(
                    kind = labelKind,
                    anchor = anchor,
                  )
                }
              }
            }

            layout.nodes.forEach { node ->
              key(node.id) {
                FlowNodeRenderer(
                  node = node,
                  modifier =
                    Modifier
                      .offset(x = node.x.dp, y = node.y.dp)
                      .size(width = node.width.dp, height = node.height.dp),
                )
              }
            }
          }
        }

        // Floating bottom-right zoom & fit controls
        Row(
          modifier =
            Modifier
              .align(Alignment.BottomEnd)
              .navigationBarsPadding()
              .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Text(
              text = "${(scaleAnim.value * 100f).roundToInt()}%",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
          }

          FilledTonalButton(
            onClick = { animateToTransform(computeFitTransform(viewportSize, layout)) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 42.dp)
                .testTag("flow_fit_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.FitScreen,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.flow_fit_button),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }

          FilledTonalButton(
            onClick = { animateToTransform(compute100Transform(viewportSize, layout)) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 42.dp)
                .testTag("flow_zoom_100_button"),
          ) {
            Text(
              text = stringResource(R.string.flow_zoom_100_button),
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ScriptFlowTopBar(
  title: String,
  onNavigateBack: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.size(48.dp).testTag("flow_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.flow_navigate_back_cd),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title.ifBlank { "Untitled Script" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = stringResource(R.string.flow_preview_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = stringResource(R.string.flow_read_only_badge),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
  }
}

@Composable
private fun FlowDiagramConnectorsCanvas(
  layout: FlowLayoutResult,
  modifier: Modifier = Modifier,
) {
  val normalWireColor = MaterialTheme.colorScheme.outline
  val yesWireColor = MaterialTheme.colorScheme.primary
  val noWireColor = MaterialTheme.colorScheme.tertiary
  val mergeWireColor = MaterialTheme.colorScheme.outline
  val loopWireColor = MaterialTheme.colorScheme.secondary
  val loopFillColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.14f)
  val loopBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)

  Canvas(modifier = modifier) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()), 0f)
    val containerStrokePx = 1.6.dp.toPx()
    val containerRadiusPx = 18.dp.toPx()

    // 1. Dashed Loop Containers
    layout.containers.forEach { container ->
      val topLeft = Offset(container.x.dp.toPx(), container.y.dp.toPx())
      val size = Size(container.width.dp.toPx(), container.height.dp.toPx())
      drawRoundRect(
        color = loopFillColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(containerRadiusPx, containerRadiusPx),
      )
      drawRoundRect(
        color = loopBorderColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(containerRadiusPx, containerRadiusPx),
        style = Stroke(width = containerStrokePx, pathEffect = dashEffect),
      )
    }

    // 2. Orthogonal/Rounded Edges & Arrowheads
    val wireStrokePx = 2.dp.toPx()
    val cornerRadiusPx = 8.dp.toPx()
    val arrowLenPx = 8.dp.toPx()
    val dotRadiusPx = 3.5.dp.toPx()

    layout.edges.forEach { edge ->
      if (edge.points.size < 2) return@forEach
      val color =
        when (edge.kind) {
          FlowEdgeKind.NORMAL -> normalWireColor
          FlowEdgeKind.BRANCH_YES -> yesWireColor
          FlowEdgeKind.BRANCH_NO -> noWireColor
          FlowEdgeKind.MERGE_RAIL -> mergeWireColor
          FlowEdgeKind.LOOP_BACK -> loopWireColor
        }
      val pxPoints = edge.points.map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
      val path = buildRoundedPolylinePath(pxPoints, cornerRadiusPx)
      drawPath(
        path = path,
        color = color,
        style = Stroke(width = wireStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )

      if (edge.hasArrowhead) {
        val prev = pxPoints[pxPoints.lastIndex - 1]
        val tip = pxPoints.last()
        drawArrowHead(from = prev, tip = tip, lengthPx = arrowLenPx, color = color)
      }

      if (edge.hasEndDot) {
        drawCircle(
          color = color,
          radius = dotRadiusPx,
          center = pxPoints.last(),
        )
      }
    }
  }
}

private fun buildRoundedPolylinePath(points: List<Offset>, cornerRadius: Float): Path {
  val path = Path()
  if (points.isEmpty()) return path
  path.moveTo(points.first().x, points.first().y)
  if (points.size == 2) {
    path.lineTo(points[1].x, points[1].y)
    return path
  }

  for (i in 1 until points.lastIndex) {
    val pPrev = points[i - 1]
    val pCurr = points[i]
    val pNext = points[i + 1]

    val v1 = pCurr - pPrev
    val v2 = pNext - pCurr
    val len1 = v1.getDistance()
    val len2 = v2.getDistance()
    if (len1 < 0.5f || len2 < 0.5f) {
      path.lineTo(pCurr.x, pCurr.y)
      continue
    }

    val r = min(cornerRadius, min(len1, len2) / 2f)
    val startArc = Offset(pCurr.x - (v1.x / len1) * r, pCurr.y - (v1.y / len1) * r)
    val endArc = Offset(pCurr.x + (v2.x / len2) * r, pCurr.y + (v2.y / len2) * r)
    path.lineTo(startArc.x, startArc.y)
    path.quadraticTo(pCurr.x, pCurr.y, endArc.x, endArc.y)
  }
  path.lineTo(points.last().x, points.last().y)
  return path
}

private fun DrawScope.drawArrowHead(
  from: Offset,
  tip: Offset,
  lengthPx: Float,
  color: Color,
) {
  val dx = tip.x - from.x
  val dy = tip.y - from.y
  if (dx * dx + dy * dy < 0.5f) return
  val angle = atan2(dy.toDouble(), dx.toDouble())
  val spread = Math.toRadians(28.0)
  val p1 =
    Offset(
      x = (tip.x - lengthPx * cos(angle - spread)).toFloat(),
      y = (tip.y - lengthPx * sin(angle - spread)).toFloat(),
    )
  val p2 =
    Offset(
      x = (tip.x - lengthPx * cos(angle + spread)).toFloat(),
      y = (tip.y - lengthPx * sin(angle + spread)).toFloat(),
    )
  val arrowPath =
    Path().apply {
      moveTo(tip.x, tip.y)
      lineTo(p1.x, p1.y)
      lineTo(p2.x, p2.y)
      close()
    }
  drawPath(path = arrowPath, color = color)
}

@Composable
private fun FlowEdgeLabelBadge(
  kind: FlowBranchLabelKind,
  anchor: FlowPoint,
) {
  val density = LocalDensity.current
  val (containerColor, contentColor, text) =
    when (kind) {
      FlowBranchLabelKind.YES ->
        Triple(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          "✓ ${stringResource(R.string.flow_branch_yes)}",
        )
      FlowBranchLabelKind.NO ->
        Triple(
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
          "✗ ${stringResource(R.string.flow_branch_no)}",
        )
      FlowBranchLabelKind.LOOP_RETURN ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          "⟲",
        )
    }

  val anchorXPx = with(density) { anchor.x.dp.toPx() }
  val anchorYPx = with(density) { anchor.y.dp.toPx() }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = containerColor,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(0, 0) {
          placeable.placeRelative(
            x = anchorXPx.roundToInt() - placeable.width / 2,
            y = anchorYPx.roundToInt() - placeable.height / 2,
          )
        }
      },
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = contentColor,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun FlowNodeRenderer(
  node: FlowNode,
  modifier: Modifier = Modifier,
) {
  when (node) {
    is FlowNode.StartAnchor -> {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.flow_start_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }

    is FlowNode.EndAnchor -> {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.flow_end_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    is FlowNode.EmptyPlaceholder -> {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.flow_no_steps_yet),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    is FlowNode.ActionCard -> {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ActionIconBadge(
            icon = node.action.actionIcon(),
            category = node.action.categoryGroup(),
            boxSize = 32.dp,
            iconSize = 17.dp,
            cornerRadius = 9.dp,
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
          ) {
            Text(
              text = node.title,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = node.summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    is FlowNode.DecisionCard -> {
      val containerColor =
        if (node.isElseIf) {
          MaterialTheme.colorScheme.secondaryContainer
        } else {
          MaterialTheme.colorScheme.primaryContainer
        }
      val contentColor =
        if (node.isElseIf) {
          MaterialTheme.colorScheme.onSecondaryContainer
        } else {
          MaterialTheme.colorScheme.onPrimaryContainer
        }
      val borderColor =
        if (node.isElseIf) {
          MaterialTheme.colorScheme.secondary
        } else {
          MaterialTheme.colorScheme.primary
        }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.25.dp, borderColor.copy(alpha = 0.7f)),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = borderColor.copy(alpha = 0.16f),
            modifier = Modifier.size(30.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.AltRoute,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
              )
            }
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
          ) {
            Text(
              text = node.title,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = contentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = node.conditionSummary,
              style = MaterialTheme.typography.bodySmall,
              color = contentColor.copy(alpha = 0.88f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    is FlowNode.ElseHeaderPill -> {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        ) {
          Text(
            text = stringResource(R.string.flow_else_header),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    is FlowNode.LoopHeaderCard -> {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = node.title,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = node.summary,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    is FlowNode.EarlyTerminationPill -> {
      val (bg, fg, icon, text) =
        when (node.terminationType) {
          FlowTerminationType.BREAK ->
            Quad(
              MaterialTheme.colorScheme.tertiaryContainer,
              MaterialTheme.colorScheme.onTertiaryContainer,
              Icons.AutoMirrored.Rounded.ExitToApp,
              stringResource(R.string.flow_break_loop),
            )
          FlowTerminationType.CONTINUE ->
            Quad(
              MaterialTheme.colorScheme.secondaryContainer,
              MaterialTheme.colorScheme.onSecondaryContainer,
              Icons.Rounded.SkipNext,
              stringResource(R.string.flow_next_cycle),
            )
          FlowTerminationType.END_SCRIPT ->
            Quad(
              MaterialTheme.colorScheme.errorContainer,
              MaterialTheme.colorScheme.onErrorContainer,
              Icons.Rounded.OutlinedFlag,
              stringResource(R.string.flow_end_script),
            )
        }

      Surface(
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
        modifier = modifier.testTag("flow_node_${node.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

private data class Quad<A, B, C, D>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
)

@Preview(showBackground = true)
@Composable
private fun ScriptFlowContentPreview() {
  val sampleActions =
    listOf(
      ScriptAction.Tap(
        id = "tap_1",
        x = NumericInput.Literal(540),
        y = NumericInput.Literal(960),
      ),
      ScriptAction.IfBlock(
        id = "if_1",
        name = "Popup Check",
        conditionRoot =
          ConditionGroup(
            id = "g1",
            children =
              listOf(
                ConditionLeaf(
                  id = "l1",
                  predicate =
                    ConditionPredicate.ColorMatch(
                      targets = listOf(ColorTarget(x = 900, y = 220, hexColor = "#FF4444")),
                    ),
                ),
              ),
          ),
        thenActions =
          listOf(
            ScriptAction.Tap(id = "tap_close", x = NumericInput.Literal(900), y = NumericInput.Literal(220)),
            ScriptAction.Break(id = "brk_1"),
          ),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei_1",
              name = "Skip Ad",
              conditionRoot =
                ConditionGroup(
                  id = "g2",
                  children =
                    listOf(
                      ConditionLeaf(
                        id = "l2",
                        predicate = ConditionPredicate.AppMatch(packageName = "com.game", appLabel = "Game"),
                      ),
                    ),
                ),
              actions = listOf(ScriptAction.Delay(id = "d_1", config = DelayConfig.Fixed(1000L))),
            ),
          ),
        elseActions =
          listOf(
            ScriptAction.SwipeDrag(
              id = "sw_1",
              startX = NumericInput.Literal(540),
              startY = NumericInput.Literal(1400),
              endX = NumericInput.Literal(540),
              endY = NumericInput.Literal(500),
            ),
          ),
      ),
      ScriptAction.LoopBlock(
        id = "loop_1",
        mode = LoopMode.Count(NumericInput.Literal(5)),
        bodyActions =
          listOf(
            ScriptAction.Tap(id = "tap_farm", x = NumericInput.Literal(320), y = NumericInput.Literal(1100)),
          ),
      ),
    )

  MaterialTheme {
    ScriptFlowContent(
      state =
        ScriptFlowUiState(
          isLoading = false,
          scriptTitle = "Daily Quest Auto",
          actionCount = 7,
          layoutResult = ScriptFlowLayoutEngine.computeLayout(sampleActions),
        ),
      onNavigateBack = {},
    )
  }
}
