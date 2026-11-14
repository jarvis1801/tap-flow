package com.tapflow.feature.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tapflow.core.model.log.ActionLogEntry
import com.tapflow.core.model.log.ConditionEvalTrace
import com.tapflow.core.model.log.ExecutionSessionLog
import com.tapflow.core.model.log.ExecutionStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogSearchAndStatusFilter(
  searchQuery: String,
  statusFilter: LogStatusFilter,
  onQueryChange: (String) -> Unit,
  onFilterChange: (LogStatusFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onQueryChange,
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.Filled.Close, contentDescription = "Clear search")
          }
        }
      },
      placeholder = { Text("Search action, error, or session...") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("log_search_input"),
    )

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LogStatusFilter.entries.forEach { filter ->
        FilterChip(
          selected = statusFilter == filter,
          onClick = { onFilterChange(filter) },
          label = { Text(filter.label) },
          modifier = Modifier.testTag("log_filter_${filter.name.lowercase()}"),
        )
      }
    }
  }
}

@Composable
fun ExecutionStatusBadge(
  status: ExecutionStatus,
  modifier: Modifier = Modifier,
) {
  val (containerColor, contentColor, icon) =
    when (status) {
      ExecutionStatus.SUCCESS ->
        Triple(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          Icons.Filled.CheckCircle,
        )
      ExecutionStatus.FAILED ->
        Triple(
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
          Icons.Filled.ErrorOutline,
        )
      ExecutionStatus.STOPPED ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          Icons.Filled.StopCircle,
        )
    }

  Surface(
    shape = MaterialTheme.shapes.small,
    color = containerColor,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = status.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutionSessionDetailBody(
  session: ExecutionSessionLog,
  onOpenScreenshot: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val durationMs = (session.finishedAtMs - session.startedAtMs).coerceAtLeast(0L)
  val controlEvents =
    remember(session.entries) {
      session.entries.filter { entry ->
        val upper = entry.actionType.uppercase(Locale.US)
        upper.contains("PLAY") ||
          upper.contains("PAUSE") ||
          upper.contains("STEP") ||
          upper.contains("STOP") ||
          upper.contains("CONTROL") ||
          upper.contains("START") ||
          upper.contains("END")
      }
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

    // Lifecycle & Control Timestamps Summary
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Session & Control Events",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ControlEventChip(
            icon = Icons.Filled.PlayCircleOutline,
            label = "Started ${formatTimestampHms(session.startedAtMs)}",
          )
          controlEvents.forEach { ev ->
            val icon =
              when {
                ev.actionType.contains("PAUSE", ignoreCase = true) -> Icons.Filled.PauseCircleOutline
                ev.actionType.contains("STEP", ignoreCase = true) -> Icons.Filled.SkipNext
                ev.actionType.contains("STOP", ignoreCase = true) -> Icons.Filled.StopCircle
                else -> Icons.Filled.PlayCircleOutline
              }
            ControlEventChip(
              icon = icon,
              label = "${ev.actionType} ${formatTimestampHms(ev.timestampMs)}",
            )
          }
          ControlEventChip(
            icon = Icons.Filled.StopCircle,
            label = "Finished ${formatTimestampHms(session.finishedAtMs)} (${formatLogDuration(durationMs)})",
          )
        }
      }
    }

    // Crash & Error Snapshot (if any)
    if (!session.errorReason.isNullOrBlank() || !session.errorScreenshotPath.isNullOrBlank()) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Crash & Error Snapshot",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          val errorReason = session.errorReason
          if (!errorReason.isNullOrBlank()) {
            Text(
              text = errorReason,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
          val screenshotPath = session.errorScreenshotPath
          if (!screenshotPath.isNullOrBlank()) {
            val file = remember(screenshotPath) { File(screenshotPath) }
            Card(
              shape = MaterialTheme.shapes.small,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onOpenScreenshot(screenshotPath) }
                  .testTag("error_screenshot_preview_${session.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (file.exists()) {
                  AsyncImage(
                    model = file,
                    contentDescription = "Error screenshot thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier
                        .size(width = 72.dp, height = 108.dp)
                        .clip(MaterialTheme.shapes.small),
                  )
                } else {
                  Box(
                    modifier =
                      Modifier
                        .size(width = 72.dp, height = 72.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(Icons.Filled.BrokenImage, contentDescription = "Screenshot missing")
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      Icons.Filled.ZoomIn,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "Tap to Inspect Fullscreen",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Chronological Action Timeline
    Text(
      text = "Action Timeline (${session.entries.size} events)",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
    )

    if (session.entries.isEmpty()) {
      Text(
        text = "No action step entries recorded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        session.entries.forEach { entry ->
          ActionTimelineRow(entry = entry)
        }
      }
    }
  }
}

@Composable
private fun ControlEventChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = FontFamily.Monospace,
    )
  }
}

@Composable
private fun ActionTimelineRow(entry: ActionLogEntry) {
  var traceExpanded by remember(entry.id) { mutableStateOf(entry.conditionTrace != null && entry.status == ExecutionStatus.FAILED) }

  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.extraSmall,
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
            text = entry.stepLabel,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.actionType,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = entry.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = formatTimestampHms(entry.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${entry.durationMs}ms",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      if (!entry.errorMessage.isNullOrBlank()) {
        Text(
          text = "Error: ${entry.errorMessage}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Medium,
        )
      }

      val trace = entry.conditionTrace
      if (trace != null) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { traceExpanded = !traceExpanded },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = if (trace.passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = if (trace.passed) "Passed" else "Failed",
                tint =
                  if (trace.passed) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.error
                  },
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "IF Condition Trace (${if (trace.passed) "Matched" else "Did Not Match"})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Icon(
              imageVector = if (traceExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = "Toggle condition trace",
            )
          }
        }

        AnimatedVisibility(visible = traceExpanded) {
          ConditionTraceCardInspector(trace = trace, depth = 1)
        }
      }
    }
  }
}

@Composable
fun ConditionTraceCardInspector(
  trace: ConditionEvalTrace,
  depth: Int = 1,
) {
  var expanded by remember(trace.nodeId) { mutableStateOf(true) }
  val borderColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.outline
      2 -> MaterialTheme.colorScheme.primary
      else -> MaterialTheme.colorScheme.tertiary
    }
  val containerColor =
    when (depth) {
      1 -> MaterialTheme.colorScheme.surfaceContainerLow
      2 -> MaterialTheme.colorScheme.surfaceContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

  Card(
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.5.dp, borderColor),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = if (trace.passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
          contentDescription = if (trace.passed) "Condition passed" else "Condition failed",
          tint =
            if (trace.passed) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.error
            },
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = trace.label.ifBlank { "Condition Node" },
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = if (trace.passed) "✓ PASS" else "✗ FAIL",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          color =
            if (trace.passed) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.error
            },
        )
        if (trace.children.isNotEmpty()) {
          IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = "Expand or collapse group trace",
            )
          }
        }
      }

      if (trace.actualSummary.isNotBlank() || trace.expectedSummary.isNotBlank()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(6.dp)) {
              Text(
                text = "ACTUAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = trace.actualSummary.ifBlank { "-" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
          Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(6.dp)) {
              Text(
                text = "EXPECTED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = trace.expectedSummary.ifBlank { "-" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }

      if (expanded && trace.children.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          trace.children.forEach { child ->
            ConditionTraceCardInspector(trace = child, depth = (depth + 1).coerceAtMost(3))
          }
        }
      }
    }
  }
}

@Composable
fun FullscreenScreenshotViewerDialog(
  pngPath: String,
  onDismiss: () -> Unit,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.94f))
          .pointerInput(Unit) {
            detectTapGestures(
              onDoubleTap = {
                scale = if (scale > 1.5f) 1f else 2.5f
                offset = Offset.Zero
              },
            )
          }
          .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
              val nextScale = (scale * zoom).coerceIn(1f, 6f)
              scale = nextScale
              offset =
                if (nextScale <= 1.01f) {
                  Offset.Zero
                } else {
                  offset + pan
                }
            }
          },
    ) {
      AsyncImage(
        model = File(pngPath),
        contentDescription = "Fullscreen crash screenshot",
        contentScale = ContentScale.Fit,
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offset.x
              translationY = offset.y
            },
      )

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .align(Alignment.TopCenter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.65f),
        ) {
          Text(
            text = "Error Snapshot • ${(scale * 100).toInt()}% (Pinch/Drag/Double-tap)",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.65f),
        ) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp).testTag("close_screenshot_viewer_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close screenshot viewer",
              tint = Color.White,
            )
          }
        }
      }
    }
  }
}

internal fun formatTimestampDateTime(utcMs: Long): String {
  if (utcMs <= 0L) return "-"
  val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
  return sdf.format(Date(utcMs))
}

internal fun formatTimestampHms(utcMs: Long): String {
  if (utcMs <= 0L) return "--:--:--"
  val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
  return sdf.format(Date(utcMs))
}

internal fun formatLogDuration(ms: Long): String {
  val safe = ms.coerceAtLeast(0L)
  if (safe < 1000L) return "${safe}ms"
  val sec = safe / 1000L
  val remMs = safe % 1000L
  val mins = sec / 60L
  val remSec = sec % 60L
  return if (mins > 0L) "${mins}m ${remSec}s" else "$remSec.${remMs / 100}s"
}
