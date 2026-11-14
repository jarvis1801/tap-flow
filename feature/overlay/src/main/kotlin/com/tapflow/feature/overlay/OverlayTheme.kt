package com.tapflow.feature.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object OverlayTokens {
  val CyanPulse = Color(0xFF22D3EE)
  val CyanContainer = Color(0xFF004F58)
  val CyanOnContainer = Color(0xFFA5F3FC)
  val CobaltAccent = Color(0xFF60A5FA)
  val CobaltContainer = Color(0xFF172554)
  val AmberAlert = Color(0xFFFBBF24)
  val AmberContainer = Color(0xFF451A03)
  val RecordCrimson = Color(0xFFF43F5E)
  val RecordContainer = Color(0xFF4C0519)
  val EmeraldActive = Color(0xFF34D399)
  val DockSurface = Color(0xFF0B1220)
  val DockSurfaceElevated = Color(0xFF131D31)
  val DockOutline = Color(0xFF26344D)
  val OnDock = Color(0xFFF1F5F9)
  val OnDockMuted = Color(0xFF94A3B8)
  val ScrimOverlay = Color(0x18020617)
}

private val OverlayDarkScheme =
  darkColorScheme(
    primary = OverlayTokens.CyanPulse,
    onPrimary = Color(0xFF00363D),
    primaryContainer = OverlayTokens.CyanContainer,
    onPrimaryContainer = OverlayTokens.CyanOnContainer,
    secondary = OverlayTokens.CobaltAccent,
    onSecondary = Color(0xFF002557),
    secondaryContainer = OverlayTokens.CobaltContainer,
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = OverlayTokens.AmberAlert,
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = OverlayTokens.AmberContainer,
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = OverlayTokens.RecordCrimson,
    onError = Color.White,
    errorContainer = OverlayTokens.RecordContainer,
    onErrorContainer = Color(0xFFFFE4E6),
    background = OverlayTokens.DockSurface,
    onBackground = OverlayTokens.OnDock,
    surface = OverlayTokens.DockSurface,
    onSurface = OverlayTokens.OnDock,
    surfaceContainer = OverlayTokens.DockSurface,
    surfaceContainerHigh = OverlayTokens.DockSurfaceElevated,
    onSurfaceVariant = OverlayTokens.OnDockMuted,
    outline = OverlayTokens.DockOutline,
  )

private val OverlayTypography =
  Typography(
    titleSmall =
      TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
      ),
    labelLarge =
      TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
      ),
    labelMedium =
      TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
      ),
    labelSmall =
      TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
      ),
  )

@Composable
internal fun TapFlowOverlayTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = OverlayDarkScheme,
    typography = OverlayTypography,
    content = content,
  )
}
