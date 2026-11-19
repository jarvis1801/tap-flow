package com.tapflow.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

object TapFlowLegalLinks {
  const val CONTACT_EMAIL = "ccplayconsole@gmail.com"
  const val GOOGLE_PRIVACY_URL = "https://policies.google.com/privacy"
  const val FIREBASE_PRIVACY_URL = "https://firebase.google.com/support/privacy"
  const val GOOGLE_PLAY_REFUND_URL = "https://support.google.com/googleplay/answer/2479637"

  // Paste hosted GitHub Pages legal doc URLs here after publishing.
  // Keep null in v1.0 so "View Online" stays hidden and never shows a dead 404 link.
  val PRIVACY_POLICY_ONLINE_URL: String? = null
  val TERMS_ONLINE_URL: String? = null
}

enum class LegalDocumentType {
  PRIVACY_POLICY,
  TERMS_AND_CONDITIONS,
}

@Immutable
data class LegalLinkAction(
  val id: String,
  val label: String,
  val url: String,
  val isEmail: Boolean = false,
)

@Immutable
data class LegalBulletPoint(
  val heading: String? = null,
  val body: String,
)

@Immutable
data class LegalSubsection(
  val id: String,
  val title: String,
  val bullets: List<LegalBulletPoint>,
)

@Immutable
data class LegalSectionCardModel(
  val id: String,
  val title: String,
  val icon: ImageVector,
  val intro: String? = null,
  val bullets: List<LegalBulletPoint> = emptyList(),
  val subsections: List<LegalSubsection> = emptyList(),
  val paragraphs: List<String> = emptyList(),
  val links: List<LegalLinkAction> = emptyList(),
)

@Immutable
data class LegalDocumentModel(
  val type: LegalDocumentType,
  val badge: String,
  val title: String,
  val effectiveMeta: String,
  val highlightTitle: String,
  val highlightBody: String,
  val isWarningHighlight: Boolean,
  val sections: List<LegalSectionCardModel>,
  val footerCopyright: String,
  val closeLabel: String,
  val viewOnlineLabel: String,
)

@Composable
fun LegalDocumentDialog(
  documentType: LegalDocumentType,
  onDismiss: () -> Unit,
  onlineUrlOverride: String? = null,
) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val linkFailedMessage = stringResource(R.string.legal_open_link_failed)

  val document =
    when (documentType) {
      LegalDocumentType.PRIVACY_POLICY -> buildPrivacyPolicyDocument()
      LegalDocumentType.TERMS_AND_CONDITIONS -> buildTermsDocument()
    }

  val resolvedOnlineUrl =
    onlineUrlOverride
      ?: when (documentType) {
        LegalDocumentType.PRIVACY_POLICY -> TapFlowLegalLinks.PRIVACY_POLICY_ONLINE_URL
        LegalDocumentType.TERMS_AND_CONDITIONS -> TapFlowLegalLinks.TERMS_ONLINE_URL
      }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    LegalDocumentReaderContent(
      document = document,
      onlineUrl = resolvedOnlineUrl,
      snackbarHostState = snackbarHostState,
      onClose = onDismiss,
      onOpenLink = { action ->
        if (action.isEmail) {
          val mailIntent =
            Intent(Intent.ACTION_SENDTO).apply {
              data = Uri.parse(action.url)
            }
          runCatching { context.startActivity(mailIntent) }
            .onFailure {
              runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("TapFlow Email", TapFlowLegalLinks.CONTACT_EMAIL))
              }
              scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(linkFailedMessage)
              }
            }
        } else {
          runCatching { uriHandler.openUri(action.url) }
            .onFailure {
              scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(linkFailedMessage)
              }
            }
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LegalDocumentReaderContent(
  document: LegalDocumentModel,
  onlineUrl: String?,
  onClose: () -> Unit,
  onOpenLink: (LegalLinkAction) -> Unit,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
    modifier =
      modifier
        .fillMaxSize()
        .testTag(
          if (document.type == LegalDocumentType.PRIVACY_POLICY) {
            "legal_privacy_reader"
          } else {
            "legal_terms_reader"
          },
        ),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
          ),
        navigationIcon = {
          IconButton(
            onClick = onClose,
            modifier = Modifier.testTag("legal_reader_close_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = document.closeLabel,
            )
          }
        },
        title = {
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = document.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = document.badge,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        actions = {
          if (!onlineUrl.isNullOrBlank()) {
            TextButton(
              onClick = {
                onOpenLink(
                  LegalLinkAction(
                    id = "view_online",
                    label = document.viewOnlineLabel,
                    url = onlineUrl,
                    isEmail = false,
                  ),
                )
              },
              modifier = Modifier.heightIn(min = 48.dp).testTag("legal_view_online_button"),
            ) {
              Text(document.viewOnlineLabel)
            }
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item(key = "legal_header_card") {
        LegalHeaderCard(
          document = document,
          modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
        )
      }

      items(
        items = document.sections,
        key = { it.id },
      ) { section ->
        LegalSectionCard(
          section = section,
          onOpenLink = onOpenLink,
          modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
        )
      }

      item(key = "legal_footer_item") {
        Column(
          modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = document.footerCopyright,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          FilledTonalButton(
            onClick = onClose,
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("legal_footer_close_button"),
          ) {
            Text(document.closeLabel)
          }
        }
      }
    }
  }
}

@Composable
private fun LegalHeaderCard(
  document: LegalDocumentModel,
  modifier: Modifier = Modifier,
) {
  val bannerContainerColor =
    if (document.isWarningHighlight) {
      MaterialTheme.colorScheme.tertiaryContainer
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val bannerContentColor =
    if (document.isWarningHighlight) {
      MaterialTheme.colorScheme.onTertiaryContainer
    } else {
      MaterialTheme.colorScheme.onPrimaryContainer
    }
  val bannerIcon =
    if (document.isWarningHighlight) {
      Icons.Filled.WarningAmber
    } else {
      Icons.Filled.VerifiedUser
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Text(
          text = document.badge,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }

      Text(
        text = document.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Text(
        text = document.effectiveMeta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = bannerContainerColor,
        contentColor = bannerContentColor,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = bannerIcon,
            contentDescription = null,
            tint = bannerContentColor,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = document.highlightTitle,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = bannerContentColor,
            )
            Text(
              text = document.highlightBody,
              style = MaterialTheme.typography.bodySmall,
              color = bannerContentColor.copy(alpha = 0.92f),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegalSectionCard(
  section: LegalSectionCardModel,
  onOpenLink: (LegalLinkAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = section.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Text(
          text = section.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
      }

      section.intro?.let { intro ->
        Text(
          text = intro,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      section.paragraphs.forEach { paragraph ->
        Text(
          text = paragraph,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (section.bullets.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          section.bullets.forEach { bullet ->
            LegalBulletRow(bullet = bullet)
          }
        }
      }

      if (section.subsections.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          section.subsections.forEachIndexed { index, sub ->
            if (index > 0) {
              HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
              )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = sub.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              sub.bullets.forEach { bullet ->
                LegalBulletRow(bullet = bullet)
              }
            }
          }
        }
      }

      if (section.links.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          section.links.forEach { linkAction ->
            FilledTonalButton(
              onClick = { onOpenLink(linkAction) },
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
              modifier =
                Modifier
                  .heightIn(min = 48.dp)
                  .testTag("legal_link_${linkAction.id}"),
            ) {
              Icon(
                imageVector =
                  if (linkAction.isEmail) {
                    Icons.Filled.Email
                  } else {
                    Icons.AutoMirrored.Filled.OpenInNew
                  },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(linkAction.label)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegalBulletRow(
  bullet: LegalBulletPoint,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier =
        Modifier
          .padding(top = 8.dp)
          .size(6.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
    )
    Text(
      text =
        buildAnnotatedString {
          if (!bullet.heading.isNullOrBlank()) {
            withStyle(
              SpanStyle(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              ),
            ) {
              append(bullet.heading)
              append(" ")
            }
          }
          withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append(bullet.body)
          }
        },
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun buildPrivacyPolicyDocument(): LegalDocumentModel {
  return LegalDocumentModel(
    type = LegalDocumentType.PRIVACY_POLICY,
    badge = stringResource(R.string.legal_badge),
    title = stringResource(R.string.legal_privacy_title),
    effectiveMeta = stringResource(R.string.legal_effective_meta),
    highlightTitle = stringResource(R.string.privacy_summary_title),
    highlightBody = stringResource(R.string.privacy_summary_body),
    isWarningHighlight = false,
    footerCopyright = stringResource(R.string.legal_footer_copyright),
    closeLabel = stringResource(R.string.legal_close),
    viewOnlineLabel = stringResource(R.string.legal_view_online),
    sections =
      listOf(
        LegalSectionCardModel(
          id = "privacy_local_storage",
          title = stringResource(R.string.privacy_s1_title),
          icon = Icons.Filled.Storage,
          intro = stringResource(R.string.privacy_s1_intro),
          bullets =
            listOf(
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s1_b1_head),
                body = stringResource(R.string.privacy_s1_b1_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s1_b2_head),
                body = stringResource(R.string.privacy_s1_b2_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s1_b3_head),
                body = stringResource(R.string.privacy_s1_b3_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s1_b4_head),
                body = stringResource(R.string.privacy_s1_b4_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s1_b5_head),
                body = stringResource(R.string.privacy_s1_b5_body),
              ),
            ),
        ),
        LegalSectionCardModel(
          id = "privacy_permissions",
          title = stringResource(R.string.privacy_s2_title),
          icon = Icons.Filled.AdminPanelSettings,
          intro = stringResource(R.string.privacy_s2_intro),
          subsections =
            listOf(
              LegalSubsection(
                id = "a11y",
                title = stringResource(R.string.privacy_s2_sub_a11y_title),
                bullets =
                  listOf(
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_a11y_b1_head),
                      body = stringResource(R.string.privacy_s2_sub_a11y_b1_body),
                    ),
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_a11y_b2_head),
                      body = stringResource(R.string.privacy_s2_sub_a11y_b2_body),
                    ),
                  ),
              ),
              LegalSubsection(
                id = "projection",
                title = stringResource(R.string.privacy_s2_sub_proj_title),
                bullets =
                  listOf(
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_proj_b1_head),
                      body = stringResource(R.string.privacy_s2_sub_proj_b1_body),
                    ),
                  ),
              ),
              LegalSubsection(
                id = "overlay",
                title = stringResource(R.string.privacy_s2_sub_overlay_title),
                bullets =
                  listOf(
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_overlay_b1_head),
                      body = stringResource(R.string.privacy_s2_sub_overlay_b1_body),
                    ),
                  ),
              ),
              LegalSubsection(
                id = "apps_visibility",
                title = stringResource(R.string.privacy_s2_sub_apps_title),
                bullets =
                  listOf(
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_apps_b1_head),
                      body = stringResource(R.string.privacy_s2_sub_apps_b1_body),
                    ),
                  ),
              ),
              LegalSubsection(
                id = "local_integrations",
                title = stringResource(R.string.privacy_s2_sub_integrations_title),
                bullets =
                  listOf(
                    LegalBulletPoint(
                      heading = stringResource(R.string.privacy_s2_sub_integrations_b1_head),
                      body = stringResource(R.string.privacy_s2_sub_integrations_b1_body),
                    ),
                  ),
              ),
            ),
        ),
        LegalSectionCardModel(
          id = "privacy_network",
          title = stringResource(R.string.privacy_s3_title),
          icon = Icons.Filled.CloudOff,
          intro = stringResource(R.string.privacy_s3_intro),
          bullets =
            listOf(
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s3_b1_head),
                body = stringResource(R.string.privacy_s3_b1_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.privacy_s3_b2_head),
                body = stringResource(R.string.privacy_s3_b2_body),
              ),
            ),
          links =
            listOf(
              LegalLinkAction(
                id = "google_privacy",
                label = stringResource(R.string.privacy_link_google),
                url = TapFlowLegalLinks.GOOGLE_PRIVACY_URL,
              ),
              LegalLinkAction(
                id = "firebase_privacy",
                label = stringResource(R.string.privacy_link_firebase),
                url = TapFlowLegalLinks.FIREBASE_PRIVACY_URL,
              ),
            ),
        ),
        LegalSectionCardModel(
          id = "privacy_retention",
          title = stringResource(R.string.privacy_s4_title),
          icon = Icons.Filled.DeleteSweep,
          bullets =
            listOf(
              LegalBulletPoint(body = stringResource(R.string.privacy_s4_b1)),
              LegalBulletPoint(body = stringResource(R.string.privacy_s4_b2)),
              LegalBulletPoint(body = stringResource(R.string.privacy_s4_b3)),
            ),
        ),
        LegalSectionCardModel(
          id = "privacy_children",
          title = stringResource(R.string.privacy_s5_title),
          icon = Icons.Filled.ChildCare,
          paragraphs = listOf(stringResource(R.string.privacy_s5_body)),
        ),
        LegalSectionCardModel(
          id = "privacy_changes",
          title = stringResource(R.string.privacy_s6_title),
          icon = Icons.Filled.Update,
          paragraphs = listOf(stringResource(R.string.privacy_s6_body)),
        ),
        LegalSectionCardModel(
          id = "privacy_contact",
          title = stringResource(R.string.privacy_s7_title),
          icon = Icons.Filled.Email,
          paragraphs = listOf(stringResource(R.string.privacy_s7_body)),
          links =
            listOf(
              LegalLinkAction(
                id = "privacy_contact_email",
                label = TapFlowLegalLinks.CONTACT_EMAIL,
                url = "mailto:${TapFlowLegalLinks.CONTACT_EMAIL}",
                isEmail = true,
              ),
            ),
        ),
      ),
  )
}

@Composable
private fun buildTermsDocument(): LegalDocumentModel {
  return LegalDocumentModel(
    type = LegalDocumentType.TERMS_AND_CONDITIONS,
    badge = stringResource(R.string.legal_badge),
    title = stringResource(R.string.legal_terms_title),
    effectiveMeta = stringResource(R.string.legal_effective_meta),
    highlightTitle = stringResource(R.string.terms_warning_title),
    highlightBody = stringResource(R.string.terms_warning_body),
    isWarningHighlight = true,
    footerCopyright = stringResource(R.string.legal_footer_copyright),
    closeLabel = stringResource(R.string.legal_close),
    viewOnlineLabel = stringResource(R.string.legal_view_online),
    sections =
      listOf(
        LegalSectionCardModel(
          id = "terms_license",
          title = stringResource(R.string.terms_s1_title),
          icon = Icons.Filled.Policy,
          intro = stringResource(R.string.terms_s1_intro),
          bullets =
            listOf(
              LegalBulletPoint(body = stringResource(R.string.terms_s1_b1)),
              LegalBulletPoint(body = stringResource(R.string.terms_s1_b2)),
            ),
        ),
        LegalSectionCardModel(
          id = "terms_acceptable_use",
          title = stringResource(R.string.terms_s2_title),
          icon = Icons.Filled.Security,
          intro = stringResource(R.string.terms_s2_intro),
          bullets =
            listOf(
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s2_b1_head),
                body = stringResource(R.string.terms_s2_b1_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s2_b2_head),
                body = stringResource(R.string.terms_s2_b2_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s2_b3_head),
                body = stringResource(R.string.terms_s2_b3_body),
              ),
            ),
        ),
        LegalSectionCardModel(
          id = "terms_integrations",
          title = stringResource(R.string.terms_s3_title),
          icon = Icons.Filled.Extension,
          paragraphs = listOf(stringResource(R.string.terms_s3_b1)),
        ),
        LegalSectionCardModel(
          id = "terms_billing",
          title = stringResource(R.string.terms_s4_title),
          icon = Icons.Filled.Payments,
          bullets =
            listOf(
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s4_b1_head),
                body = stringResource(R.string.terms_s4_b1_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s4_b2_head),
                body = stringResource(R.string.terms_s4_b2_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s4_b3_head),
                body = stringResource(R.string.terms_s4_b3_body),
              ),
              LegalBulletPoint(
                heading = stringResource(R.string.terms_s4_b4_head),
                body = stringResource(R.string.terms_s4_b4_body),
              ),
            ),
          links =
            listOf(
              LegalLinkAction(
                id = "google_play_refunds",
                label = stringResource(R.string.terms_link_refund),
                url = TapFlowLegalLinks.GOOGLE_PLAY_REFUND_URL,
              ),
            ),
        ),
        LegalSectionCardModel(
          id = "terms_warranty",
          title = stringResource(R.string.terms_s5_title),
          icon = Icons.Filled.Info,
          paragraphs = listOf(stringResource(R.string.terms_s5_body)),
        ),
        LegalSectionCardModel(
          id = "terms_liability",
          title = stringResource(R.string.terms_s6_title),
          icon = Icons.Filled.Shield,
          paragraphs = listOf(stringResource(R.string.terms_s6_body)),
        ),
        LegalSectionCardModel(
          id = "terms_governing_law",
          title = stringResource(R.string.terms_s7_title),
          icon = Icons.Filled.Gavel,
          paragraphs = listOf(stringResource(R.string.terms_s7_body)),
        ),
        LegalSectionCardModel(
          id = "terms_contact",
          title = stringResource(R.string.terms_s8_title),
          icon = Icons.Filled.Description,
          paragraphs = listOf(stringResource(R.string.terms_s8_body)),
          links =
            listOf(
              LegalLinkAction(
                id = "terms_contact_email",
                label = TapFlowLegalLinks.CONTACT_EMAIL,
                url = "mailto:${TapFlowLegalLinks.CONTACT_EMAIL}",
                isEmail = true,
              ),
            ),
        ),
      ),
  )
}

@Preview(showBackground = true)
@Composable
private fun PrivacyPolicyReaderLightPreview() {
  MaterialTheme {
    LegalDocumentReaderContent(
      document = buildPrivacyPolicyDocument(),
      onlineUrl = null,
      onClose = {},
      onOpenLink = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TermsReaderAmoledPreview() {
  MaterialTheme(
    colorScheme =
      androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF97F0FF),
        secondaryContainer = Color(0xFF0D3B75),
        onSecondaryContainer = Color(0xFFD6E4FF),
        tertiaryContainer = Color(0xFF7A5200),
        onTertiaryContainer = Color(0xFFFFE082),
        background = Color(0xFF000000),
        onBackground = Color.White,
        surface = Color(0xFF000000),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFC4CED9),
        surfaceContainerLow = Color(0xFF0A0A0A),
        outlineVariant = Color(0xFF2F3742),
      ),
  ) {
    LegalDocumentReaderContent(
      document = buildTermsDocument(),
      onlineUrl = "https://example.com/terms",
      onClose = {},
      onOpenLink = {},
    )
  }
}
