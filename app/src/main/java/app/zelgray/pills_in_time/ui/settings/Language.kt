package app.zelgray.pills_in_time.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.zelgray.pills_in_time.R

/** tag == null means "follow the system language" (clears the per-app override). */
enum class AppLanguage(val tag: String?, val labelRes: Int) {
    SYSTEM_DEFAULT(null, R.string.language_system_default),
    ENGLISH("en", R.string.language_english),
    UKRAINIAN("uk", R.string.language_ukrainian),
    RUSSIAN("ru", R.string.language_russian),
    CZECH("cs", R.string.language_czech),
}

/**
 * Thin wrapper over AppCompatDelegate's per-app language API. Persistence is
 * automatic (system LocaleManager on API 33+, AppCompat's own store on older
 * API levels via the AppLocalesMetadataHolderService declared in the
 * manifest) — nothing here needs to touch DataStore.
 */
object LanguageManager {

    fun current(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM_DEFAULT
    }

    fun set(language: AppLanguage) {
        val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
