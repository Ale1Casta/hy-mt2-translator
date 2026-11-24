package com.example.hy_mt2translator

/**
 * Una lingua di destinazione.
 * - nameIt: il nome mostrato all'utente
 * - nameEn: il nome usato nel prompt (il README di Hy-MT2 vuole i nomi in inglese)
 * - flag: la bandiera mostrata accanto al nome (puramente decorativa: una lingua non coincide
 *   con un paese, per esempio lo spagnolo o l'arabo si parlano in molti)
 */
data class Language(val nameIt: String, val nameEn: String, val flag: String = "🌐")

/** Lingue della tabella ufficiale di Hy-MT2, in ordine alfabetico italiano. */
val LANGUAGES = listOf(
    Language("Arabo", "Arabic", "🇸🇦"),
    Language("Bengalese", "Bengali", "🇧🇩"),
    Language("Birmano", "Burmese", "🇲🇲"),
    Language("Cantonese", "Cantonese", "🇭🇰"),
    Language("Ceco", "Czech", "🇨🇿"),
    Language("Cinese (semplificato)", "Chinese", "🇨🇳"),
    Language("Cinese (tradizionale)", "Traditional Chinese", "🇹🇼"),
    Language("Coreano", "Korean", "🇰🇷"),
    Language("Ebraico", "Hebrew", "🇮🇱"),
    Language("Filippino", "Filipino", "🇵🇭"),
    Language("Francese", "French", "🇫🇷"),
    Language("Giapponese", "Japanese", "🇯🇵"),
    Language("Gujarati", "Gujarati", "🇮🇳"),
    Language("Hindi", "Hindi", "🇮🇳"),
    Language("Indonesiano", "Indonesian", "🇮🇩"),
    Language("Inglese", "English", "🇬🇧"),
    Language("Italiano", "Italian", "🇮🇹"),
    Language("Kazako", "Kazakh", "🇰🇿"),
    Language("Khmer", "Khmer", "🇰🇭"),
    Language("Malese", "Malay", "🇲🇾"),
    Language("Marathi", "Marathi", "🇮🇳"),
    Language("Mongolo", "Mongolian", "🇲🇳"),
    Language("Olandese", "Dutch", "🇳🇱"),
    Language("Persiano", "Persian", "🇮🇷"),
    Language("Polacco", "Polish", "🇵🇱"),
    Language("Portoghese", "Portuguese", "🇵🇹"),
    Language("Russo", "Russian", "🇷🇺"),
    Language("Spagnolo", "Spanish", "🇪🇸"),
    Language("Tailandese", "Thai", "🇹🇭"),
    Language("Tamil", "Tamil", "🇮🇳"),
    Language("Tedesco", "German", "🇩🇪"),
    Language("Telugu", "Telugu", "🇮🇳"),
    Language("Tibetano", "Tibetan", "🌐"),
    Language("Turco", "Turkish", "🇹🇷"),
    Language("Ucraino", "Ukrainian", "🇺🇦"),
    Language("Uiguro", "Uyghur", "🌐"),
    Language("Urdu", "Urdu", "🇵🇰"),
    Language("Vietnamita", "Vietnamese", "🇻🇳"),
)

/** Lingua scelta all'avvio. */
val DEFAULT_LANGUAGE = LANGUAGES.first { it.nameEn == "English" }

/** Ritrova una lingua dal nome inglese (serve per salvare e ripristinare la scelta). */
fun languageFromEnglishName(nameEn: String): Language =
    LANGUAGES.firstOrNull { it.nameEn == nameEn } ?: DEFAULT_LANGUAGE