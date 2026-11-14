package com.tapflow.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.PanToolAlt
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapflow.core.model.action.ScriptAction

@Immutable
internal data class CategoryToneColors(
  val container: Color,
  val content: Color,
)

@Composable
internal fun categoryToneColors(category: ActionCategoryGroup): CategoryToneColors =
  when (category) {
    ActionCategoryGroup.TOUCH_GESTURES ->
      CategoryToneColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.primary,
      )
    ActionCategoryGroup.FLOW_CONTROL ->
      CategoryToneColors(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.tertiary,
      )
    ActionCategoryGroup.SYSTEM_HARDWARE ->
      CategoryToneColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.secondary,
      )
    ActionCategoryGroup.VARIABLE_OPS ->
      CategoryToneColors(
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }

internal fun ScriptAction.actionIcon(): ImageVector =
  when (this) {
    is ScriptAction.Tap -> Icons.Rounded.TouchApp
    is ScriptAction.DoubleTap -> Icons.Rounded.AdsClick
    is ScriptAction.LongPress -> Icons.Rounded.PanToolAlt
    is ScriptAction.MultiTouch -> Icons.Rounded.Pinch
    is ScriptAction.SwipeDrag -> Icons.Rounded.Swipe
    is ScriptAction.InputText -> Icons.Rounded.Keyboard
    is ScriptAction.Delay -> Icons.Rounded.HourglassBottom
    is ScriptAction.IfBlock -> Icons.AutoMirrored.Rounded.AltRoute
    is ScriptAction.LoopBlock -> Icons.Rounded.Repeat
    is ScriptAction.Break -> Icons.AutoMirrored.Rounded.ExitToApp
    is ScriptAction.Continue -> Icons.Rounded.SkipNext
    is ScriptAction.EndScript -> Icons.Rounded.OutlinedFlag
    is ScriptAction.KeyEvent -> Icons.Rounded.Smartphone
    is ScriptAction.LaunchApp -> Icons.AutoMirrored.Rounded.OpenInNew
    is ScriptAction.ForceStopApp -> Icons.Rounded.Cancel
    is ScriptAction.ClipboardOp -> Icons.Rounded.ContentPaste
    is ScriptAction.Vibrate -> Icons.Rounded.Vibration
    is ScriptAction.PlayAlertSound -> Icons.Rounded.NotificationsActive
    is ScriptAction.VariableOp -> Icons.Rounded.DataObject
  }

@Composable
internal fun ActionIconBadge(
  icon: ImageVector,
  category: ActionCategoryGroup,
  contentDescription: String? = null,
  boxSize: Dp = 40.dp,
  iconSize: Dp = 20.dp,
  cornerRadius: Dp = 12.dp,
  modifier: Modifier = Modifier,
) {
  val tone = categoryToneColors(category)
  Surface(
    shape = RoundedCornerShape(cornerRadius),
    color = tone.container,
    modifier = modifier.size(boxSize),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tone.content,
        modifier = Modifier.size(iconSize),
      )
    }
  }
}

@Composable
internal fun EditorSectionCard(
  title: String? = null,
  subtitle: String? = null,
  trailing: (@Composable () -> Unit)? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (title != null || trailing != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
              Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (subtitle != null) {
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (trailing != null) {
            trailing()
          }
        }
      }
      content()
    }
  }
}
