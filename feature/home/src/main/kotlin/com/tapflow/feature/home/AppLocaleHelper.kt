package com.tapflow.feature.home

import java.util.Locale

internal object AppLocaleHelper {
  fun isLocaleAlreadyActive(currentLanguageTags: String, targetLanguageTag: String): Boolean {
    val current = currentLanguageTags.trim()
    val target = targetLanguageTag.trim()
    if (current.isEmpty()) {
      return target.isEmpty() || target.equals(AppLocaleOption.ENGLISH.languageTag, ignoreCase = true)
    }
    val primaryTag = current.substringBefore(',').trim()
    if (primaryTag.equals(target, ignoreCase = true)) {
      return true
    }
    val currentLocale = Locale.forLanguageTag(primaryTag)
    val targetLocale = Locale.forLanguageTag(target)
    if (!currentLocale.language.equals(targetLocale.language, ignoreCase = true)) {
      return false
    }
    if (targetLocale.country.isEmpty()) {
      return true
    }
    if (currentLocale.country.isNotEmpty()) {
      return currentLocale.country.equals(targetLocale.country, ignoreCase = true)
    }
    if (targetLocale.language.equals("zh", ignoreCase = true)) {
      val script = currentLocale.script
      if (script.equals("Hant", ignoreCase = true)) {
        return targetLocale.country.equals("TW", ignoreCase = true)
      }
      if (script.equals("Hans", ignoreCase = true)) {
        return targetLocale.country.equals("CN", ignoreCase = true)
      }
    }
    return false
  }

  fun isLocaleOptionSelected(currentLanguageTags: String, option: AppLocaleOption): Boolean =
    isLocaleAlreadyActive(currentLanguageTags, option.languageTag)
}
