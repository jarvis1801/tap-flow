package com.tapflow.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.script.Script
import java.text.DateFormat
import java.util.Date

@Composable
fun ScriptsTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (state.scripts.isEmpty()) {
      EmptyTabStateCard(
        icon = Icons.Filled.TouchApp,
        title = if (state.searchQuery.isBlank()) "No Scripts Yet" else "No Matching Scripts",
        subtitle =
          if (state.searchQuery.isBlank()) {
            "Create a manual automation script or launch Live Recording over your target app."
          } else {
            "Try clearing the search query or searching by another tag."
          },
        modifier = Modifier.padding(24.dp).align(Alignment.Center),
      )
    } else {
      LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!state.isPro) {
          item(key = "free_script_quota") {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text =
                  "Free Plan: ${state.totalScriptsCount}/${EntitlementValidator.FREE_MAX_SCRIPTS} Scripts • Max ${EntitlementValidator.FREE_MAX_ACTIONS_PER_SCRIPT} actions each",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
            }
          }
        }

        items(
          items = state.scripts,
          key = { it.script.id },
        ) { item ->
          ScriptItemCard(
            item = item,
            onPlay = { onIntent(HomeUiIntent.PlayScriptClicked(item.script.id)) },
            onEdit = { onIntent(HomeUiIntent.EditScriptClicked(item.script.id)) },
            onLogs = { onIntent(HomeUiIntent.ViewScriptLogsClicked(item.script.id)) },
            onDelete = { onIntent(HomeUiIntent.RequestDeleteScript(item.script.id)) },
          )
        }
      }
    }

    ScriptSpeedDialFab(
      expanded = state.isScriptFabExpanded,
      onToggle = { onIntent(HomeUiIntent.ToggleScriptFab(!state.isScriptFabExpanded)) },
      onManual = { onIntent(HomeUiIntent.AddManualScriptClicked) },
      onRecord = { onIntent(HomeUiIntent.AddRecordScriptClicked) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    )
  }
}

@Composable
private fun ScriptItemCard(
  item: ScriptCardUi,
  onPlay: () -> Unit,
  onEdit: () -> Unit,
  onLogs: () -> Unit,
  onDelete: () -> Unit,
) {
  val script = item.script
  val updatedText =
    if (script.updatedAtMs > 0L) {
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(script.updatedAtMs))
    } else {
      "Just now"
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("script_card_${script.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border =
      if (item.proBlockers.isNotEmpty()) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f))
      } else {
        null
      },
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = script.title.ifBlank { "Untitled Script" },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (item.requiresVision) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "Requires screen vision",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Vision",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }
      }

      if (script.description.isNotBlank()) {
        Text(
          text = script.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (script.tags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          script.tags.forEach { tag ->
            AssistChip(
              onClick = {},
              label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
      }

      if (item.proBlockers.isNotEmpty()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Pro required: ${item.proBlockers.joinToString(", ")}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${script.actions.size} actions • $updatedText",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp).testTag("play_script_${script.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Run script",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onEdit,
            modifier = Modifier.size(48.dp).testTag("edit_script_${script.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Edit script",
            )
          }
          IconButton(
            onClick = onLogs,
            modifier = Modifier.size(48.dp).testTag("logs_script_${script.id}"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
              contentDescription = "Script logs",
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp).testTag("delete_script_${script.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete script",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ScriptSpeedDialFab(
  expanded: Boolean,
  onToggle: () -> Unit,
  onManual: () -> Unit,
  onRecord: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (expanded) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          tonalElevation = 4.dp,
          shadowElevation = 2.dp,
        ) {
          Text(
            text = "Manual Editor",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
        SmallFloatingActionButton(
          onClick = onManual,
          modifier = Modifier.size(48.dp).testTag("fab_manual_script"),
        ) {
          Icon(imageVector = Icons.Filled.Edit, contentDescription = "Create manual script")
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          tonalElevation = 4.dp,
          shadowElevation = 2.dp,
        ) {
          Text(
            text = "Live Record",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
        SmallFloatingActionButton(
          onClick = onRecord,
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.size(48.dp).testTag("fab_record_script"),
        ) {
          Icon(
            imageVector = Icons.Filled.FiberManualRecord,
            contentDescription = "Start live recording",
            tint = MaterialTheme.colorScheme.onErrorContainer,
          )
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = onToggle,
      icon = {
        Icon(
          imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
          contentDescription = null,
        )
      },
      text = { Text(if (expanded) "Close" else "Add Script") },
      modifier = Modifier.testTag("fab_add_script"),
    )
  }
}

@Composable
internal fun EmptyTabStateCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(42.dp),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ScriptsTabPreview() {
  MaterialTheme {
    ScriptsTabContent(
      state =
        HomeUiState(
          entitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
          totalScriptsCount = 1,
          scripts =
            listOf(
              ScriptCardUi(
                script =
                  Script(
                    id = "s1",
                    title = "Daily Arena Check-In",
                    description = "Collects daily rewards and dismisses popups.",
                    tags = listOf("RPG", "Daily"),
                    actions =
                      listOf(
                        ScriptAction.Tap(
                          id = "a1",
                          x = NumericInput.Literal(540),
                          y = NumericInput.Literal(960),
                        ),
                      ),
                    updatedAtMs = 1_730_000_000_000L,
                  ),
                requiresVision = false,
                proBlockers = emptyList(),
              ),
            ),
        ),
      onIntent = {},
    )
  }
}
