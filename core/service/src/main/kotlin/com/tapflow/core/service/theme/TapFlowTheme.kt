package com.tapflow.core.service.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption

internal val CyanPulse = Color(0xFF00E5FF)
internal val CyanDeep = Color(0xFF006874)
internal val CyanContainerDark = Color(0xFF004F58)
internal val CyanContainerLight = Color(0xFF97F0FF)

internal val CobaltAccent = Color(0xFF448AFF)
internal val CobaltDeep = Color(0xFF1565C0)
internal val CobaltContainerDark = Color(0xFF0D3B75)
internal val CobaltContainerLight = Color(0xFFD6E4FF)

internal val AmberPro = Color(0xFFFFB300)
internal val AmberProDark = Color(0xFF7A5200)
internal val AmberProLight = Color(0xFFFFE082)

internal val NavySurfaceLowestDark = Color(0xFF060B12)
internal val NavySurfaceDark = Color(0xFF0A111A)
internal val NavySurfaceContainerLowDark = Color(0xFF0E1722)
internal val NavySurfaceContainerDark = Color(0xFF111B27)
internal val NavySurfaceContainerHighDark = Color(0xFF182433)
internal val NavySurfaceContainerHighestDark = Color(0xFF213042)
internal val SlateOnSurfaceDark = Color(0xFFE2EDF7)
internal val SlateOnSurfaceVariantDark = Color(0xFF9FB3C8)
internal val OutlineDark = Color(0xFF394E66)
internal val OutlineVariantDark = Color(0xFF263649)

internal val SlateSurfaceLight = Color(0xFFF4F8FB)
internal val SlateSurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val SlateSurfaceContainerLowLight = Color(0xFFF9FCFE)
internal val SlateSurfaceContainerLight = Color(0xFFF0F5F9)
internal val SlateSurfaceContainerHighLight = Color(0xFFE7EFF6)
internal val SlateSurfaceContainerHighestLight = Color(0xFFDDE7F0)
internal val NavyOnSurfaceLight = Color(0xFF0D1B2A)
internal val NavyOnSurfaceVariantLight = Color(0xFF486581)
internal val OutlineLight = Color(0xFF8DA4B8)
internal val OutlineVariantLight = Color(0xFFCBD8E4)

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
    surfaceContainerLowest = NavySurfaceLowestDark,
    surfaceContainerLow = NavySurfaceContainerLowDark,
    surfaceContainer = NavySurfaceContainerDark,
    surfaceContainerHigh = NavySurfaceContainerHighDark,
    surfaceContainerHighest = NavySurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
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
    surfaceContainerLowest = SlateSurfaceContainerLowestLight,
    surfaceContainerLow = SlateSurfaceContainerLowLight,
    surfaceContainer = SlateSurfaceContainerLight,
    surfaceContainerHigh = SlateSurfaceContainerHighLight,
    surfaceContainerHighest = SlateSurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
  )

private val CobaltDarkColorScheme =
  TapFlowDarkColorScheme.copy(
    primary = CobaltAccent,
    onPrimary = Color(0xFF001D47),
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = CobaltContainerLight,
    secondary = CyanPulse,
    onSecondary = Color(0xFF00363D),
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
    onSecondary = Color.White,
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
    onSecondary = Color(0xFF00363D),
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
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF001F24),
  )

private val AmoledTrueBlackColorScheme =
  TapFlowDarkColorScheme.copy(
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF000000),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC4CED9),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF525E6B),
    outlineVariant = Color(0xFF2F3742),
  )

@Composable
fun resolveIsDarkTheme(
  darkMode: DarkModeOption,
  themePalette: ThemePaletteOption = ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  isPro: Boolean = false,
): Boolean {
  if (themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK && isPro) {
    return true
  }
  return when (darkMode) {
    DarkModeOption.SYSTEM -> isSystemInDarkTheme()
    DarkModeOption.LIGHT -> false
    DarkModeOption.DARK -> true
  }
}

@Composable
fun TapFlowTheme(
  darkMode: DarkModeOption = DarkModeOption.SYSTEM,
  themePalette: ThemePaletteOption = ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  isPro: Boolean = false,
  darkTheme: Boolean = resolveIsDarkTheme(darkMode, themePalette, isPro),
  dynamicColor: Boolean =
    (themePalette == ThemePaletteOption.DYNAMIC_MATERIAL_YOU) ||
      (themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK && !isPro),
  typography: Typography = MaterialTheme.typography,
  content: @Composable () -> Unit,
) {
  val effectivePalette =
    if (themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK && !isPro) {
      ThemePaletteOption.DYNAMIC_MATERIAL_YOU
    } else {
      themePalette
    }
  val isAmoledActive = effectivePalette == ThemePaletteOption.AMOLED_TRUE_BLACK
  val effectiveDarkTheme = if (isAmoledActive) true else darkTheme
  val effectiveDynamicColor =
    effectivePalette == ThemePaletteOption.DYNAMIC_MATERIAL_YOU &&
      (dynamicColor || themePalette == ThemePaletteOption.AMOLED_TRUE_BLACK)

  val colorScheme =
    when {
      isAmoledActive -> AmoledTrueBlackColorScheme
      effectiveDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      effectivePalette == ThemePaletteOption.COBALT_TACTICAL ->
        if (effectiveDarkTheme) CobaltDarkColorScheme else CobaltLightColorScheme
      effectivePalette == ThemePaletteOption.AMBER_INDUSTRIAL ->
        if (effectiveDarkTheme) AmberDarkColorScheme else AmberLightColorScheme
      else -> if (effectiveDarkTheme) TapFlowDarkColorScheme else TapFlowLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = typography,
    content = content,
  )
}
