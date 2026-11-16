package com.tapflow.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineStep

@Composable
fun PipelinesTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasActiveFilters = state.searchQuery.isNotBlank() || state.selectedPipelineTags.isNotEmpty()

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (state.availablePipelineTags.isNotEmpty()) {
      TagFilterChipBar(
        availableTags = state.availablePipelineTags,
        selectedTags = state.selectedPipelineTags,
        testTagPrefix = "pipeline",
        onToggleTag = { tag -> onIntent(HomeUiIntent.TogglePipelineTagFilter(tag)) },
        onClearTags = { onIntent(HomeUiIntent.ClearPipelineTagFilters) },
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    Box(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentAlignment = Alignment.TopCenter,
    ) {
      if (state.pipelines.isEmpty()) {
        EmptyTabStateCard(
          icon = Icons.Filled.AccountTree,
          title = if (!hasActiveFilters) "No Pipelines Yet" else "No Matching Pipelines",
          subtitle =
            if (!hasActiveFilters) {
              "Chain multiple scripts sequentially with repeat counts and error recovery."
            } else {
              "Try clearing the search bar or selected tag filters."
            },
          modifier = Modifier.padding(24.dp).align(Alignment.Center),
        )
      } else {
        LazyColumn(
          modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(
            items = state.pipelines,
            key = { it.pipeline.id },
          ) { item ->
            PipelineItemCard(
              item = item,
              selectedTags = state.selectedPipelineTags,
              onToggleTag = { tag -> onIntent(HomeUiIntent.TogglePipelineTagFilter(tag)) },
              onPlay = { onIntent(HomeUiIntent.PlayPipelineClicked(item.pipeline.id)) },
              onEdit = { onIntent(HomeUiIntent.EditPipelineClicked(item.pipeline.id)) },
              onLogs = { onIntent(HomeUiIntent.ViewPipelineLogsClicked(item.pipeline.id)) },
              onDelete = { onIntent(HomeUiIntent.RequestDeletePipeline(item.pipeline.id)) },
            )
          }
        }
      }

      ExtendedFloatingActionButton(
        onClick = { onIntent(HomeUiIntent.AddPipelineClicked) },
        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
        text = { Text("Add Pipeline") },
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .testTag("fab_add_pipeline"),
      )
    }
  }
}

@Composable
private fun PipelineItemCard(
  item: PipelineCardUi,
  selectedTags: Set<String>,
  onToggleTag: (String) -> Unit,
  onPlay: () -> Unit,
  onEdit: () -> Unit,
  onLogs: () -> Unit,
  onDelete: () -> Unit,
) {
  val pipeline = item.pipeline
  Card(
    modifier = Modifier.fillMaxWidth().testTag("pipeline_card_${pipeline.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border =
      if (item.missingScriptCount > 0) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
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
          text = pipeline.title.ifBlank { "Untitled Pipeline" },
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

      if (pipeline.description.isNotBlank()) {
        Text(
          text = pipeline.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (pipeline.tags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          pipeline.tags.forEach { tag ->
            val selected = HomeLibraryFilter.isTagSelected(tag, selectedTags)
            FilterChip(
              selected = selected,
              onClick = { onToggleTag(tag) },
              leadingIcon =
                if (selected) {
                  {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = null,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                } else {
                  null
                },
              label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.testTag("pipeline_card_${pipeline.id}_tag_$tag"),
            )
          }
        }
      }

      if (item.missingScriptCount > 0) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "[Missing Script] ×${item.missingScriptCount} referenced step(s) removed",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontWeight = FontWeight.SemiBold,
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
          text = "${pipeline.steps.size} Scripts • ${item.totalRunsCount} Total Runs",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp).testTag("play_pipeline_${pipeline.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Run pipeline",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onEdit,
            modifier = Modifier.size(48.dp).testTag("edit_pipeline_${pipeline.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Edit pipeline",
            )
          }
          IconButton(
            onClick = onLogs,
            modifier = Modifier.size(48.dp).testTag("logs_pipeline_${pipeline.id}"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
              contentDescription = "Pipeline logs",
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp).testTag("delete_pipeline_${pipeline.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete pipeline",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PipelinesTabPreview() {
  MaterialTheme {
    PipelinesTabContent(
      state =
        HomeUiState(
          entitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
          totalPipelinesCount = 1,
          availablePipelineTags = listOf("Farming"),
          selectedPipelineTags = setOf("Farming"),
          pipelines =
            listOf(
              PipelineCardUi(
                pipeline =
                  Pipeline(
                    id = "p1",
                    title = "Farm + Sell Loop",
                    description = "Runs battle loop 5x then opens shop cleanup.",
                    tags = listOf("Farming"),
                    steps =
                      listOf(
                        PipelineStep("st1", "s1", repeatCount = 5),
                        PipelineStep("st2", "s2", repeatCount = 1),
                      ),
                  ),
                totalRunsCount = 6,
                missingScriptCount = 1,
                requiresVision = true,
                proBlockers = emptyList(),
              ),
            ),
        ),
      onIntent = {},
    )
  }
}
