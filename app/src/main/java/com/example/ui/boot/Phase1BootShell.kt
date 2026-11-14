package com.jarvis.ui.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jarvis.R
import com.jarvis.ui.theme.TapFlowTheme

@Composable
fun Phase1BootScreen(
  onNavigateToHome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Phase1BootContent(
    modules = defaultBootModules,
    onContinueClick = onNavigateToHome,
    modifier = modifier,
  )
}

@Composable
fun Phase1BootContent(
  modules: List<String>,
  onContinueClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                  MaterialTheme.colorScheme.background,
                ),
            ),
          ),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = stringResource(R.string.app_name),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
              )
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.boot_shell_title),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = stringResource(R.string.boot_shell_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier.size(10.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
            )
            Text(
              text = stringResource(R.string.boot_shell_status_label),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
              imageVector = Icons.Rounded.AutoMode,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(items = modules, key = { it }) { moduleName ->
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              tonalElevation = 1.dp,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = moduleName,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }

        Button(
          onClick = onContinueClick,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .testTag("boot_continue_button"),
        ) {
          Text(
            text = stringResource(R.string.boot_shell_cta_continue),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

private val defaultBootModules =
  listOf(
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:billing",
    ":core:server",
    ":core:service",
    ":feature:onboarding",
    ":feature:paywall",
    ":feature:home",
    ":feature:editor",
    ":feature:logs",
    ":feature:studio",
    ":feature:overlay",
  )

@Preview(showBackground = true)
@Composable
private fun Phase1BootContentPreview() {
  TapFlowTheme(darkTheme = true, dynamicColor = false) {
    Phase1BootContent(
      modules = defaultBootModules,
      onContinueClick = {},
    )
  }
}
