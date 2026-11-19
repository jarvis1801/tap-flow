package com.tapflow.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalOffer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapflow.core.billing.DiscountReason
import com.tapflow.core.billing.EntitlementRepository
import com.tapflow.core.billing.PaywallPricingState
import com.tapflow.core.billing.ProductOfferInfo
import com.tapflow.core.model.common.FeatureFlags
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.TrialStatus
import kotlinx.coroutines.launch

private val PAYWALL_COMPARISON_ROWS =
  listOf(
    PaywallComparisonRow(
      id = "core_automation",
      feature = "All Actions, Unlimited Scripts & Flows",
      freeTier = "Forever Free ✓",
      proTier = "Forever Free ✓",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "external_broadcast",
      feature = "MacroDroid & Tasker Automation",
      freeTier = "Not Included",
      proTier = "Broadcast Triggers & Callbacks [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "shizuku",
      feature = "Seamless (Shizuku) Suite",
      freeTier = "Standard Accessibility",
      proTier = "Shell Input, Force-Stop & Auto Media [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "vision",
      feature = "Advanced Image Vision & Inspector",
      freeTier = "Single-Point Color & App Match",
      proTier = "ImageMatch, Multi-Point/Region, Crop & Mode 4 [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "pipeline_automation",
      feature = "Pipeline Looping & Crash Recovery",
      freeTier = "Run Pipeline Once",
      proTier = "Multi-Repeat / Infinite Loop & Skip Recovery [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "logs",
      feature = "Full Execution Logs & Export",
      freeTier = "Basic Run Status",
      proTier = "Detailed Error Traces, Snapshots & Export [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "amoled",
      feature = "AMOLED Pure Black Theme",
      freeTier = "System / Light / Dark",
      proTier = "True #000000 Black [Trial + Lifetime]",
      lifetimeOnly = false,
    ),
    PaywallComparisonRow(
      id = "console",
      feature = "Desktop Console Bridge",
      freeTier = "Not Included",
      proTier = "Ktor Desktop Studio ADB Bridge [Lifetime Only]",
      lifetimeOnly = true,
    ),
    PaywallComparisonRow(
      id = "backup",
      feature = ".tapflow Data Backup & Sharing",
      freeTier = "Not Included",
      proTier = "Export / Import [Lifetime Only]",
      lifetimeOnly = true,
    ),
  )

private val VISIBLE_PAYWALL_COMPARISON_ROWS =
  PAYWALL_COMPARISON_ROWS.filter { row ->
    FeatureFlags.ENABLE_DESKTOP_CONSOLE || row.id != "console"
  }

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
        is PaywallUiEffect.LaunchPlayBillingFlow -> {
          val activity = context.findActivity()
          if (activity != null) {
            entitlementRepository.launchLifetimePurchase(activity, effect.targetSku)
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

        if (!state.isProOwned) {
          item(key = "pricing_offer_card") {
            PaywallPricingOfferCard(pricingState = state.pricingState)
          }

          item(key = "promo_redeem_card") {
            PaywallPromoCodeCard(
              state = state,
              onIntent = onIntent,
            )
          }
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
          items = VISIBLE_PAYWALL_COMPARISON_ROWS,
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
private fun PaywallPricingOfferCard(pricingState: PaywallPricingState) {
  val regularPrice = pricingState.regularProduct?.formattedPrice
  val promoPrice = pricingState.promoProduct?.formattedPrice

  if (pricingState.showPromoPricing && promoPrice != null) {
    val badgeTitle =
      if (pricingState.discountReason == DiscountReason.PROMO_CODE_ACTIVE) {
        "Promo Active"
      } else {
        "Special Offer"
      }
    val windowLabel =
      when (pricingState.discountReason) {
        DiscountReason.PROMO_CODE_ACTIVE ->
          "Promo Code Active: ${pricingState.promoRemainingDays}d ${pricingState.promoRemainingHours}h left"
        DiscountReason.TRIAL_NOT_STARTED,
        DiscountReason.TRIAL_ACTIVE -> "7-Day Trial Offer"
        DiscountReason.NONE -> ""
      }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
      modifier = Modifier.fillMaxWidth().testTag("paywall_promo_pricing_card"),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.LocalOffer,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = badgeTitle,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            if (windowLabel.isNotBlank()) {
              Text(
                text = windowLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("paywall_promo_window_label"),
              )
            }
          }

          pricingState.discountPercentOff?.let { pctOff ->
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
              modifier = Modifier.testTag("paywall_discount_percent_badge"),
            ) {
              Text(
                text = "$pctOff% OFF",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (!regularPrice.isNullOrBlank()) {
            Text(
              text = regularPrice,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
              textDecoration = TextDecoration.LineThrough,
              modifier = Modifier.padding(bottom = 3.dp).testTag("paywall_regular_price_strikethrough"),
            )
          }
          Text(
            text = promoPrice,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("paywall_promo_price"),
          )
          Text(
            text = "One-time lifetime unlock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }
      }
    }
  } else {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().testTag("paywall_regular_pricing_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Lifetime Pro License",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "One-time purchase • Standard price",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = regularPrice ?: "Loading…",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("paywall_regular_price"),
        )
      }
    }
  }
}

@Composable
private fun PaywallPromoCodeCard(
  state: PaywallUiState,
  onIntent: (PaywallUiIntent) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onIntent(PaywallUiIntent.TogglePromoSection) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("promo_code_toggle_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.LocalOffer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "Have a Promo / Redeem Code?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Icon(
          imageVector =
            if (state.isPromoSectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription =
            if (state.isPromoSectionExpanded) "Collapse promo code section" else "Expand promo code section",
        )
      }

      AnimatedVisibility(visible = state.isPromoSectionExpanded) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = state.promoCodeInput,
              onValueChange = { onIntent(PaywallUiIntent.UpdatePromoCodeInput(it)) },
              label = { Text("Enter promo code") },
              placeholder = { Text("e.g. LAUNCH50") },
              singleLine = true,
              enabled = !state.isRedeemingCode,
              isError = !state.promoErrorMessage.isNullOrBlank(),
              keyboardOptions =
                KeyboardOptions(
                  capitalization = KeyboardCapitalization.Characters,
                  imeAction = ImeAction.Done,
                ),
              keyboardActions =
                KeyboardActions(
                  onDone = { onIntent(PaywallUiIntent.ApplyPromoCodeClicked) },
                ),
              modifier = Modifier.weight(1f).testTag("promo_code_input"),
            )

            FilledTonalButton(
              onClick = { onIntent(PaywallUiIntent.ApplyPromoCodeClicked) },
              enabled = !state.isRedeemingCode && state.promoCodeInput.isNotBlank(),
              modifier = Modifier.heightIn(min = 52.dp).testTag("apply_promo_code_button"),
            ) {
              if (state.isRedeemingCode) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                )
              } else {
                Text("Apply Code")
              }
            }
          }

          if (!state.promoErrorMessage.isNullOrBlank()) {
            Text(
              text = state.promoErrorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("promo_code_error"),
            )
          }

          val confirmationBanner =
            state.promoSuccessMessage
              ?: if (state.pricingState.discountReason == DiscountReason.PROMO_CODE_ACTIVE) {
                val codeLabel = state.pricingState.activePromoCode ?: "Promo"
                "Code $codeLabel applied! Special price unlocked (${state.pricingState.promoRemainingDays}d ${state.pricingState.promoRemainingHours}h left)"
              } else {
                null
              }

          if (!confirmationBanner.isNullOrBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.fillMaxWidth().testTag("promo_code_success"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = confirmationBanner,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
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
              text =
                if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                  "All automation, vision matching, Shizuku, and Desktop Studio features are permanently unlocked."
                } else {
                  "All automation, vision matching, Shizuku, and .tapflow Data Backup features are permanently unlocked."
                },
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
              text =
                if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                  "Upgrade to Lifetime Pro to unlock Desktop Console & Data Backup"
                } else {
                  "Upgrade to Lifetime Pro to unlock .tapflow Data Backup & keep all Pro features forever"
                },
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
                  if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                    "All Actions, Unlimited Scripts & Flows — Forever Free. Start a 7-day trial (no card needed) for MacroDroid/Tasker broadcasts, Seamless Shizuku, Image Vision, Pipeline Looping & Crash Recovery, Full Logs, and AMOLED black theme. Desktop Console & .tapflow Backup unlock with Lifetime Pro."
                  } else {
                    "All Actions, Unlimited Scripts & Flows — Forever Free. Start a 7-day trial (no card needed) for MacroDroid/Tasker broadcasts, Seamless Shizuku, Image Vision, Pipeline Looping & Crash Recovery, Full Logs, and AMOLED black theme. .tapflow Data Backup & Sharing unlocks with Lifetime Pro."
                  }
                TrialStatus.EXPIRED ->
                  if (FeatureFlags.ENABLE_DESKTOP_CONSOLE) {
                    "All Actions, Unlimited Scripts & Flows remain free forever. Unlock Lifetime Pro with a single one-time purchase for unattended Pipeline looping, Shizuku, Image Vision, External Triggers, Desktop Console & Backup."
                  } else {
                    "All Actions, Unlimited Scripts & Flows remain free forever. Unlock Lifetime Pro with a single one-time purchase for unattended Pipeline looping, Shizuku, Image Vision, External Triggers, and .tapflow Data Backup & Sharing."
                  }
                TrialStatus.EXPIRED_TAMPERED ->
                  "Local trial verification failed due to device clock rollback or integrity mismatch. All Actions & Unlimited Scripts remain free; unlock Lifetime Pro via Google Play for Pro automation."
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
        val priceSuffix = state.effectiveFormattedPrice?.let { " • $it" }.orEmpty()
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
private fun PaywallFreePromoPreview() {
  MaterialTheme {
    PaywallContent(
      state =
        PaywallUiState(
          entitlementState = EntitlementState.Free(TrialStatus.NOT_STARTED, 7),
          pricingState =
            PaywallPricingState(
              regularProduct = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME, "$14.99", 14_990_000L, "USD"),
              promoProduct = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, "$7.49", 7_490_000L, "USD"),
              isDiscountEligible = true,
              discountReason = DiscountReason.TRIAL_NOT_STARTED,
              activeSku = EntitlementValidator.SKU_PRO_LIFETIME_PROMO,
              discountPercentOff = 50,
            ),
          formattedPrice = "$7.49",
          sourceFeature = "Image Template Matching",
        ),
      snackbarHostState = SnackbarHostState(),
      onIntent = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PaywallExpiredRegularWithPromoCodePreview() {
  MaterialTheme {
    PaywallContent(
      state =
        PaywallUiState(
          entitlementState = EntitlementState.Free(TrialStatus.EXPIRED, 0),
          pricingState =
            PaywallPricingState(
              regularProduct = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME, "$14.99", 14_990_000L, "USD"),
              promoProduct = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, "$7.49", 7_490_000L, "USD"),
              isDiscountEligible = false,
              discountReason = DiscountReason.NONE,
              activeSku = EntitlementValidator.SKU_PRO_LIFETIME,
            ),
          formattedPrice = "$14.99",
          isPromoSectionExpanded = true,
          promoCodeInput = "LAUNCH50",
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
