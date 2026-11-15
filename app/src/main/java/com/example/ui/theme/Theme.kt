package com.jarvis.tapflow.ui.theme

import androidx.compose.runtime.Composable
import com.tapflow.core.datastore.DarkModeOption
import com.tapflow.core.datastore.ThemePaletteOption
import com.tapflow.core.service.theme.TapFlowTheme as SharedTapFlowTheme
import com.tapflow.core.service.theme.resolveIsDarkTheme

@Composable
fun TapFlowTheme(
  darkMode: DarkModeOption = DarkModeOption.SYSTEM,
  themePalette: ThemePaletteOption = ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  isPro: Boolean = false,
  darkTheme: Boolean = resolveIsDarkTheme(darkMode, isPro),
  dynamicColor: Boolean = themePalette == ThemePaletteOption.DYNAMIC_MATERIAL_YOU,
  content: @Composable () -> Unit,
) {
  SharedTapFlowTheme(
    darkMode = darkMode,
    themePalette = themePalette,
    isPro = isPro,
    darkTheme = darkTheme,
    dynamicColor = dynamicColor,
    typography = TapFlowTypography,
    content = content,
  )
}
