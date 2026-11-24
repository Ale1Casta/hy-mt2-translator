package com.example.hy_mt2translator

import android.content.Context

/** Come si sceglie il tema: segue il telefono (Auto) oppure è fisso, notte o giorno. */
enum class ThemeMode { Auto, Night, Day }

/**
 * Piccolo archivio delle scelte dell'utente, salvato nelle preferenze dell'app
 * (un file privato che resta anche quando l'app viene chiusa).
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hy_mt2_settings", Context.MODE_PRIVATE)

    /** Nome inglese dell'ultima lingua di destinazione scelta. */
    var targetLanguageName: String
        get() = prefs.getString(KEY_TARGET_LANGUAGE, DEFAULT_LANGUAGE.nameEn) ?: DEFAULT_LANGUAGE.nameEn
        set(value) {
            prefs.edit().putString(KEY_TARGET_LANGUAGE, value).apply()
        }

    /** Id dell'ultimo tono scelto. */
    var toneId: String
        get() = prefs.getString(KEY_TONE, DEFAULT_TONE.id) ?: DEFAULT_TONE.id
        set(value) {
            prefs.edit().putString(KEY_TONE, value).apply()
        }

    /** Tema scelto con il pulsante giorno/notte; Auto = segue il telefono. */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.Auto
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    private companion object {
        const val KEY_TARGET_LANGUAGE = "target_language"
        const val KEY_TONE = "tone"
        const val KEY_THEME = "theme"
    }
}