package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TapFlowDarkColorScheme =
  darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF00363D),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanContainerLight,
    secondary = CobaltAccent,
    onSecondary = Color(0xFF002557),
    secondaryContainer = CobaltContainerDark,
    onSecondaryContainer = CobaltContainerLight,
    tertiary = AmberPro,
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = AmberProDark,
    onTertiaryContainer = AmberProLight,
    background = NavySurfaceDark,
    onBackground = SlateOnSurfaceDark,
    surface = NavySurfaceDark,
    onSurface = SlateOnSurfaceDark,
    surfaceVariant = NavySurfaceContainerHighDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark,
    surfaceContainer = NavySurfaceContainerDark,
    surfaceContainerHigh = NavySurfaceContainerHighDark,
    outline = OutlineDark,
  )

private val TapFlowLightColorScheme =
  lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = Color(0xFF001F24),
    secondary = CobaltDeep,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainerLight,
    onSecondaryContainer = Color(0xFF001A41),
    tertiary = AmberProDark,
    onTertiary = Color.White,
    tertiaryContainer = AmberProLight,
    onTertiaryContainer = Color(0xFF281800),
    background = SlateSurfaceLight,
    onBackground = NavyOnSurfaceLight,
    surface = SlateSurfaceLight,
    onSurface = NavyOnSurfaceLight,
    surfaceVariant = SlateSurfaceContainerHighLight,
    onSurfaceVariant = NavyOnSurfaceVariantLight,
    surfaceContainer = SlateSurfaceContainerLight,
    surfaceContainerHigh = SlateSurfaceContainerHighLight,
    outline = OutlineLight,
  )

@Composable
fun TapFlowTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> TapFlowDarkColorScheme
      else -> TapFlowLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = TapFlowTypography,
    content = content,
  )
}
