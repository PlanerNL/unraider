package nl.planernl.unraider.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Taal per app. Op Android 13+ gebruikt dit de systeemfunctie (ook te kiezen in
 * Instellingen → Apps → Unraider → Taal); op oudere versies bewaart AppCompat de keuze.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    DUTCH("nl");

    companion object {
        fun current(): AppLanguage {
            val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return when {
                tags.startsWith("nl") -> DUTCH
                tags.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            )
        }
    }
}
