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

import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption

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

private val CobaltDarkColorScheme =
  TapFlowDarkColorScheme.copy(
    primary = CobaltAccent,
    onPrimary = Color(0xFF001D47),
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = CobaltContainerLight,
    secondary = CyanPulse,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanContainerLight,
  )

private val CobaltLightColorScheme =
  TapFlowLightColorScheme.copy(
    primary = CobaltDeep,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = Color(0xFF001A41),
    secondary = CyanDeep,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF001F24),
  )

private val AmberDarkColorScheme =
  TapFlowDarkColorScheme.copy(
    primary = AmberPro,
    onPrimary = Color(0xFF3E2900),
    primaryContainer = AmberProDark,
    onPrimaryContainer = AmberProLight,
    secondary = CyanPulse,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanContainerLight,
  )

private val AmberLightColorScheme =
  TapFlowLightColorScheme.copy(
    primary = AmberProDark,
    onPrimary = Color.White,
    primaryContainer = AmberProLight,
    onPrimaryContainer = Color(0xFF281800),
    secondary = CyanDeep,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF001F24),
  )

@Composable
fun TapFlowTheme(
  darkMode: DarkModeOption = DarkModeOption.SYSTEM,
  themePalette: ThemePaletteOption = ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  darkTheme: Boolean =
    when (darkMode) {
      DarkModeOption.SYSTEM -> isSystemInDarkTheme()
      DarkModeOption.LIGHT -> false
      DarkModeOption.DARK -> true
    },
  dynamicColor: Boolean = themePalette == ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      themePalette == ThemePaletteOption.COBALT_TACTICAL ->
        if (darkTheme) CobaltDarkColorScheme else CobaltLightColorScheme
      themePalette == ThemePaletteOption.AMBER_INDUSTRIAL ->
        if (darkTheme) AmberDarkColorScheme else AmberLightColorScheme
      else -> if (darkTheme) TapFlowDarkColorScheme else TapFlowLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = TapFlowTypography,
    content = content,
  )
}
