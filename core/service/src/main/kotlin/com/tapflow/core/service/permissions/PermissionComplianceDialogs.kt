package com.tapflow.core.service.permissions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapflow.core.service.accessibility.TapFlowAccessibilityService

fun openAccessibilitySettings(context: Context) {
  val serviceComponent =
    ComponentName(context.packageName, TapFlowAccessibilityService::class.java.name)
  val targeted =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      putExtra(":settings:fragment_args_key", serviceComponent.flattenToString())
      if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  runCatching { context.startActivity(targeted) }
    .onFailure {
      val fallback =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      context.startActivity(fallback)
    }
}

fun openOverlayPermissionSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${context.packageName}"),
    ).apply {
      if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  runCatching { context.startActivity(intent) }
    .onFailure {
      val fallback =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
          if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      context.startActivity(fallback)
    }
}

/**
 * Google Play Prominent Disclosure required before directing users to system Accessibility Settings.
 */
@Composable
fun AccessibilityProminentDisclosureDialog(
  onAcceptAndContinue: () -> Unit,
  onDecline: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDecline,
    modifier = Modifier.testTag("accessibility_prominent_disclosure_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Filled.PrivacyTip,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = "Accessibility Service Disclosure",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "TapFlow uses the AccessibilityService API exclusively to automate gestures on your device when you run a script or pipeline:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          shape = MaterialTheme.shapes.medium,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "• Simulate taps, swipes, long-presses, and multi-touch gestures you configure.",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "• Read the current foreground app package name when evaluating App Match conditions.",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "• Optionally insert text into focused input fields for Input Text actions.",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "• Optionally detect Volume Down presses while running if you enable emergency stop in Settings.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        Text(
          text =
            "100% Offline Privacy: TapFlow never collects, stores remotely, or transmits any screen content, app usage, or personal data.",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onAcceptAndContinue,
        modifier = Modifier.testTag("accessibility_disclosure_accept_button"),
      ) {
        Text("Accept & Continue")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDecline,
        modifier = Modifier.testTag("accessibility_disclosure_decline_button"),
      ) {
        Text("Decline")
      }
    },
  )
}

/**
 * Runtime Pre-Flight Guard shown before launching Overlay/Runner/Recorder/Inspector/Picker when
 * Accessibility Service or Overlay permission is currently disabled.
 */
@Composable
fun RuntimePermissionsDisabledDialog(
  accessibilityConnected: Boolean,
  overlayGranted: Boolean,
  onRequestAccessibility: () -> Unit,
  onRequestOverlay: () -> Unit,
  onContinue: () -> Unit,
  onDismiss: () -> Unit,
) {
  val bothReady = accessibilityConnected && overlayGranted
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("runtime_permissions_disabled_dialog"),
    icon = {
      Icon(
        imageVector = if (bothReady) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
        contentDescription = null,
        tint = if (bothReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = if (bothReady) "Permissions Ready" else "Permissions Disabled",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            if (bothReady) {
              "All required system permissions are active. You can launch now."
            } else {
              "Enable the required permissions below before starting TapFlow's floating overlay or automation engine."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionFixStatusRow(
          icon = Icons.Filled.AccessibilityNew,
          title = "Accessibility Service",
          subtitle =
            if (accessibilityConnected) "Connected & active" else "Required for gesture playback",
          granted = accessibilityConnected,
          buttonTag = "preflight_fix_accessibility_button",
          onFixClick = onRequestAccessibility,
        )

        PermissionFixStatusRow(
          icon = Icons.Filled.Layers,
          title = "Display Over Other Apps",
          subtitle = if (overlayGranted) "Granted" else "Required for floating dock & picker",
          granted = overlayGranted,
          buttonTag = "preflight_fix_overlay_button",
          onFixClick = onRequestOverlay,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onContinue,
        enabled = bothReady,
        modifier = Modifier.testTag("preflight_continue_button"),
      ) {
        Text("Continue")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("preflight_cancel_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun PermissionFixStatusRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  granted: Boolean,
  buttonTag: String,
  onFixClick: () -> Unit,
) {
  Surface(
    color =
      if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
      if (granted) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = "$title granted",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      } else {
        FilledTonalButton(
          onClick = onFixClick,
          modifier = Modifier.testTag(buttonTag),
        ) {
          Text("Grant")
        }
      }
    }
  }
}
