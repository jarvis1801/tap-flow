package com.tapflow.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.TrialStatus
import kotlinx.coroutines.launch

private val PAYWALL_COMPARISON_ROWS =
  listOf(
    PaywallComparisonRow("scripts", "Scripts & Actions", "3 Scripts / 15 Actions", "Unlimited [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("pipelines", "Pipelines", "1 Pipeline", "Unlimited + Skip Recovery [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("vision", "Vision Matching", "Single-Point Color", "ImageMatch + Multi-Point [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("inspect", "Inspect Studio", "Pixel View Only", "Full Crop + Mode 4 Live [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("logic", "Logic & Variables", "Fixed Delay & Count Loop", "Nested If + Loops + Vars [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("human", "Anti-Ban Humanization", "Linear Swipes", "Jitter + Bezier + Deviation [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("shizuku", "Shizuku Seamless", "Standard Accessibility", "Shell Input + Force-Stop [7-Day Trial + Lifetime]"),
    PaywallComparisonRow("logs", "Execution Logs", "Latest Summary", "Full History + Export [7-Day Trial + Lifetime]"),
    PaywallComparisonRow(
      id = "console",
      feature = "Desktop Console Bridge",
      freeTier = "Not Included",
      proTier = "Ktor WS Bridge [Lifetime Only]",
      lifetimeOnly = true,
    ),
    PaywallComparisonRow(
      id = "backup",
      feature = "Data Backup (.tapflow Export/Import)",
      freeTier = "Not Included",
      proTier = "Export / Import [Lifetime Only]",
      lifetimeOnly = true,
    ),
    PaywallComparisonRow(
      id = "amoled",
      feature = "AMOLED Pure Black Theme",
      freeTier = "System / Light / Dark",
      proTier = "True #000000 Black [7-Day Trial + Lifetime]",
      lifetimeOnly = false,
    ),
  )

@Composable
fun PaywallScreen(
  sourceFeature: String?,
  entitlementRepository: EntitlementRepository,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PaywallViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(sourceFeature) {
    viewModel.onIntent(PaywallUiIntent.SetSourceFeature(sourceFeature))
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        PaywallUiEffect.LaunchPlayBillingFlow -> {
          val activity = context.findActivity()
          if (activity != null) {
            entitlementRepository.launchLifetimePurchase(activity)
          } else {
            launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar("Unable to launch Google Play Billing from this context.")
            }
          }
        }
        is PaywallUiEffect.ShowSnackbar -> {
          launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(effect.message)
          }
        }
        PaywallUiEffect.NavigateBack -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          onNavigateBack()
        }
      }
    }
  }

  PaywallContent(
    state = uiState,
    snackbarHostState = snackbarHostState,
    onIntent = viewModel::onIntent,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallContent(
  state: PaywallUiState,
  snackbarHostState: SnackbarHostState,
  onIntent: (PaywallUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text("TapFlow Pro") },
        navigationIcon = {
          IconButton(
            onClick = { onIntent(PaywallUiIntent.NavigateBackClicked) },
            modifier = Modifier.testTag("paywall_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
      )
    },
    bottomBar = {
      PaywallActionFooter(
        state = state,
        onIntent = onIntent,
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!state.sourceFeature.isNullOrBlank() && !state.isProOwned) {
          item(key = "source_feature_banner") {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.secondaryContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.LockOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = "Pro feature requested: ${state.sourceFeature}",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        }

        if (state.isPendingPurchase) {
          item(key = "pending_banner") {
            PendingPurchaseBanner()
          }
        }

        item(key = "hero_status_card") {
          PaywallHeroCard(state = state)
        }

        if (!state.statusMessage.isNullOrBlank()) {
          item(key = "status_message") {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }

        item(key = "comparison_header") {
          ComparisonTableHeader()
        }

        items(
          items = PAYWALL_COMPARISON_ROWS,
          key = { it.id },
        ) { row ->
          ComparisonRowItem(row = row)
        }

        item(key = "offline_guarantee") {
          Text(
            text =
              "One-time lifetime unlock. No recurring subscription, no remote account required, 100% offline execution.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun PendingPurchaseBanner() {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
    modifier = Modifier.fillMaxWidth().testTag("paywall_pending_banner"),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.HourglassTop,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Payment Pending",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
          text = "Your Google Play payment is awaiting settlement. TapFlow Pro will unlock automatically once confirmed.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }
    }
  }
}

@Composable
private fun PaywallHeroCard(state: PaywallUiState) {
  when (val entitlement = state.entitlementState) {
    is EntitlementState.ProLifetime -> {
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "TapFlow Lifetime Pro Active",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = "All automation, vision matching, Shizuku, and Desktop Studio features are permanently unlocked.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
            )
          }
        }
      }
    }
    is EntitlementState.ProTrial -> {
      val totalHours = 7 * 24f
      val leftHours = (entitlement.remainingDays * 24 + entitlement.remainingHours).coerceAtLeast(1)
      val progress = (leftHours / totalHours).coerceIn(0.05f, 1f)
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = "7-Day Trial Active (${entitlement.remainingDays}d ${entitlement.remainingHours}h left)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
            modifier = Modifier.fillMaxWidth().height(6.dp),
          )
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().testTag("paywall_trial_lifetime_callout"),
          ) {
            Text(
              text = "Upgrade to Lifetime Pro to unlock Desktop Console & Data Backup",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }
    }
    EntitlementState.PendingPurchase -> {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Lifetime Unlock Processing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Once Google Play confirms settlement, tap Restore Purchase if it does not update automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    is EntitlementState.Free -> {
      val isExpiredOrTampered =
        entitlement.trialStatus == TrialStatus.EXPIRED ||
          entitlement.trialStatus == TrialStatus.EXPIRED_TAMPERED
      val cardContentColor =
        if (isExpiredOrTampered) {
          MaterialTheme.colorScheme.onErrorContainer
        } else {
          MaterialTheme.colorScheme.onSurface
        }
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (isExpiredOrTampered) {
                MaterialTheme.colorScheme.errorContainer
              } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
              },
            contentColor = cardContentColor,
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector =
                if (isExpiredOrTampered) Icons.Filled.WarningAmber else Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint =
                if (isExpiredOrTampered) {
                  MaterialTheme.colorScheme.onErrorContainer
                } else {
                  MaterialTheme.colorScheme.primary
                },
            )
            Text(
              text =
                when (entitlement.trialStatus) {
                  TrialStatus.NOT_STARTED -> "Try TapFlow Pro Free for 7 Days"
                  TrialStatus.EXPIRED -> "Your 7-Day Trial Has Ended"
                  TrialStatus.EXPIRED_TAMPERED -> "Trial Ended (Clock / Integrity Check)"
                  TrialStatus.ACTIVE -> "Trial Active"
                },
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = cardContentColor,
            )
          }
          Text(
            text =
              when (entitlement.trialStatus) {
                TrialStatus.NOT_STARTED ->
                  "Unlock unlimited scripts, template matching, nested logic, Bezier humanization, Shizuku, and AMOLED black theme for 7 days with no credit card required. Desktop Console & Data Backup unlock with Lifetime Pro."
                TrialStatus.EXPIRED ->
                  "Basic scripts remain free forever. Unlock Lifetime Pro with a single one-time purchase to continue using Pro automation tools."
                TrialStatus.EXPIRED_TAMPERED ->
                  "Local trial verification failed due to device clock rollback or integrity mismatch. Unlock Lifetime Pro via Google Play to continue."
                TrialStatus.ACTIVE -> ""
              },
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (isExpiredOrTampered) {
                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.88f)
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    }
  }
}

@Composable
private fun ComparisonTableHeader() {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Capability",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1.2f),
      )
      Text(
        text = "Free",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(0.9f),
      )
      Text(
        text = "Pro",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ComparisonRowItem(row: PaywallComparisonRow) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = row.feature,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1.2f).padding(end = 6.dp),
      )
      Text(
        text = row.freeTier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(0.9f).padding(end = 6.dp),
      )
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector =
            if (row.lifetimeOnly) Icons.Filled.WorkspacePremium else Icons.Filled.CheckCircle,
          contentDescription = null,
          tint =
            if (row.lifetimeOnly) {
              MaterialTheme.colorScheme.tertiary
            } else {
              MaterialTheme.colorScheme.primary
            },
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = row.proTier,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color =
            if (row.lifetimeOnly) {
              MaterialTheme.colorScheme.tertiary
            } else {
              MaterialTheme.colorScheme.onSurface
            },
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
  }
}

@Composable
private fun PaywallActionFooter(
  state: PaywallUiState,
  onIntent: (PaywallUiIntent) -> Unit,
) {
  Surface(
    tonalElevation = 3.dp,
    shadowElevation = 8.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.canStartTrial) {
        FilledTonalButton(
          onClick = { onIntent(PaywallUiIntent.ActivateTrialClicked) },
          enabled = !state.isProcessingAction,
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("start_trial_button"),
        ) {
          Text("Start 7-Day Free Trial (No Card Needed)")
        }
      }

      if (!state.isProOwned) {
        val priceSuffix = state.formattedPrice?.let { " • $it" }.orEmpty()
        Button(
          onClick = { onIntent(PaywallUiIntent.PurchaseLifetimeClicked) },
          enabled = !state.isProcessingAction && !state.isPendingPurchase,
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("unlock_lifetime_pro_button"),
        ) {
          if (state.isProcessingAction) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text("Unlock Lifetime Pro$priceSuffix")
        }
      }

      OutlinedButton(
        onClick = { onIntent(PaywallUiIntent.RestorePurchaseClicked) },
        enabled = !state.isProcessingAction,
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("restore_purchase_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Restore,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Restore Purchase")
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PaywallFreePreview() {
  MaterialTheme {
    PaywallContent(
      state =
        PaywallUiState(
          entitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
          formattedPrice = "$14.99",
          sourceFeature = "Image Template Matching",
        ),
      snackbarHostState = SnackbarHostState(),
      onIntent = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PaywallTrialPreview() {
  MaterialTheme {
    PaywallContent(
      state =
        PaywallUiState(
          entitlementState = EntitlementState.ProTrial(0L, remainingDays = 5, remainingHours = 14),
          formattedPrice = "$14.99",
        ),
      snackbarHostState = SnackbarHostState(),
      onIntent = {},
    )
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
