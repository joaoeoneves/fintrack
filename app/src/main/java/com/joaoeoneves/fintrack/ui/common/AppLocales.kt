package com.joaoeoneves.fintrack.ui.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.joaoeoneves.fintrack.domain.model.AppLanguage

/** BCP-47 tag list for [AppLanguage]; empty means "follow the system locale". */
internal fun AppLanguage.languageTags(): String =
    when (this) {
        AppLanguage.SYSTEM -> ""
        AppLanguage.ENGLISH -> "en"
        AppLanguage.PORTUGUESE -> "pt-PT"
    }

internal fun needsLocaleUpdate(
    currentTags: String,
    language: AppLanguage,
): Boolean = currentTags != language.languageTags()

/**
 * Applies [language] app-wide via [AppCompatDelegate.setApplicationLocales], only when it differs
 * from the current override (a redundant call would needlessly recreate the activity).
 */
fun applyAppLanguage(language: AppLanguage) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (needsLocaleUpdate(current, language)) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTags()))
    }
}
