package com.music.bitchord.desktop

import java.util.Locale

/**
 * One language the translate button can translate lyrics *into*.
 *
 * [code] is what goes on the wire as the endpoint's `tl` parameter, so it is
 * kept exactly as Google spells it — including the handful that are not bare
 * ISO-639-1: `zh-CN` and `zh-TW` are different scripts of the same language and
 * collapsing either to `zh` silently gives you Simplified, and `mni-Mtei` names
 * the script it is actually written in.
 *
 * [fallbackName] is only reached when the platform has no display name for the
 * code — true for most of the long tail here, which Android's ICU data predates
 * or never carried. Where the platform *does* know the language its own name
 * wins, because that one is localised into whatever the app is set to and this
 * one is not.
 */
internal data class DesktopTranslationLanguage(val code: String, val fallbackName: String)

/**
 * Every language Google Translate offers, in the order its own picker lists
 * them: alphabetical by English name, which is not the order they end up in
 * once localised, but is the order anyone who has used Translate expects.
 */
internal val DESKTOP_TRANSLATION_LANGUAGES = listOf(
    DesktopTranslationLanguage("af", "Afrikaans"),
    DesktopTranslationLanguage("sq", "Albanian"),
    DesktopTranslationLanguage("am", "Amharic"),
    DesktopTranslationLanguage("ar", "Arabic"),
    DesktopTranslationLanguage("hy", "Armenian"),
    DesktopTranslationLanguage("as", "Assamese"),
    DesktopTranslationLanguage("ay", "Aymara"),
    DesktopTranslationLanguage("az", "Azerbaijani"),
    DesktopTranslationLanguage("bm", "Bambara"),
    DesktopTranslationLanguage("eu", "Basque"),
    DesktopTranslationLanguage("be", "Belarusian"),
    DesktopTranslationLanguage("bn", "Bengali"),
    DesktopTranslationLanguage("bho", "Bhojpuri"),
    DesktopTranslationLanguage("bs", "Bosnian"),
    DesktopTranslationLanguage("bg", "Bulgarian"),
    DesktopTranslationLanguage("ca", "Catalan"),
    DesktopTranslationLanguage("ceb", "Cebuano"),
    DesktopTranslationLanguage("ny", "Chichewa"),
    DesktopTranslationLanguage("zh-CN", "Chinese (Simplified)"),
    DesktopTranslationLanguage("zh-TW", "Chinese (Traditional)"),
    DesktopTranslationLanguage("co", "Corsican"),
    DesktopTranslationLanguage("hr", "Croatian"),
    DesktopTranslationLanguage("cs", "Czech"),
    DesktopTranslationLanguage("da", "Danish"),
    DesktopTranslationLanguage("dv", "Dhivehi"),
    DesktopTranslationLanguage("doi", "Dogri"),
    DesktopTranslationLanguage("nl", "Dutch"),
    DesktopTranslationLanguage("en", "English"),
    DesktopTranslationLanguage("eo", "Esperanto"),
    DesktopTranslationLanguage("et", "Estonian"),
    DesktopTranslationLanguage("ee", "Ewe"),
    DesktopTranslationLanguage("tl", "Filipino"),
    DesktopTranslationLanguage("fi", "Finnish"),
    DesktopTranslationLanguage("fr", "French"),
    DesktopTranslationLanguage("fy", "Frisian"),
    DesktopTranslationLanguage("gl", "Galician"),
    DesktopTranslationLanguage("ka", "Georgian"),
    DesktopTranslationLanguage("de", "German"),
    DesktopTranslationLanguage("el", "Greek"),
    DesktopTranslationLanguage("gn", "Guarani"),
    DesktopTranslationLanguage("gu", "Gujarati"),
    DesktopTranslationLanguage("ht", "Haitian Creole"),
    DesktopTranslationLanguage("ha", "Hausa"),
    DesktopTranslationLanguage("haw", "Hawaiian"),
    DesktopTranslationLanguage("iw", "Hebrew"),
    DesktopTranslationLanguage("hi", "Hindi"),
    DesktopTranslationLanguage("hmn", "Hmong"),
    DesktopTranslationLanguage("hu", "Hungarian"),
    DesktopTranslationLanguage("is", "Icelandic"),
    DesktopTranslationLanguage("ig", "Igbo"),
    DesktopTranslationLanguage("ilo", "Ilocano"),
    DesktopTranslationLanguage("id", "Indonesian"),
    DesktopTranslationLanguage("ga", "Irish"),
    DesktopTranslationLanguage("it", "Italian"),
    DesktopTranslationLanguage("ja", "Japanese"),
    DesktopTranslationLanguage("jw", "Javanese"),
    DesktopTranslationLanguage("kn", "Kannada"),
    DesktopTranslationLanguage("kk", "Kazakh"),
    DesktopTranslationLanguage("km", "Khmer"),
    DesktopTranslationLanguage("rw", "Kinyarwanda"),
    DesktopTranslationLanguage("gom", "Konkani"),
    DesktopTranslationLanguage("ko", "Korean"),
    DesktopTranslationLanguage("kri", "Krio"),
    DesktopTranslationLanguage("ku", "Kurdish (Kurmanji)"),
    DesktopTranslationLanguage("ckb", "Kurdish (Sorani)"),
    DesktopTranslationLanguage("ky", "Kyrgyz"),
    DesktopTranslationLanguage("lo", "Lao"),
    DesktopTranslationLanguage("la", "Latin"),
    DesktopTranslationLanguage("lv", "Latvian"),
    DesktopTranslationLanguage("ln", "Lingala"),
    DesktopTranslationLanguage("lt", "Lithuanian"),
    DesktopTranslationLanguage("lg", "Luganda"),
    DesktopTranslationLanguage("lb", "Luxembourgish"),
    DesktopTranslationLanguage("mk", "Macedonian"),
    DesktopTranslationLanguage("mai", "Maithili"),
    DesktopTranslationLanguage("mg", "Malagasy"),
    DesktopTranslationLanguage("ms", "Malay"),
    DesktopTranslationLanguage("ml", "Malayalam"),
    DesktopTranslationLanguage("mt", "Maltese"),
    DesktopTranslationLanguage("mi", "Maori"),
    DesktopTranslationLanguage("mr", "Marathi"),
    DesktopTranslationLanguage("mni-Mtei", "Meiteilon (Manipuri)"),
    DesktopTranslationLanguage("lus", "Mizo"),
    DesktopTranslationLanguage("mn", "Mongolian"),
    DesktopTranslationLanguage("my", "Myanmar (Burmese)"),
    DesktopTranslationLanguage("ne", "Nepali"),
    DesktopTranslationLanguage("no", "Norwegian"),
    DesktopTranslationLanguage("or", "Odia (Oriya)"),
    DesktopTranslationLanguage("om", "Oromo"),
    DesktopTranslationLanguage("ps", "Pashto"),
    DesktopTranslationLanguage("fa", "Persian"),
    DesktopTranslationLanguage("pl", "Polish"),
    DesktopTranslationLanguage("pt", "Portuguese"),
    DesktopTranslationLanguage("pa", "Punjabi"),
    DesktopTranslationLanguage("qu", "Quechua"),
    DesktopTranslationLanguage("ro", "Romanian"),
    DesktopTranslationLanguage("ru", "Russian"),
    DesktopTranslationLanguage("sm", "Samoan"),
    DesktopTranslationLanguage("sa", "Sanskrit"),
    DesktopTranslationLanguage("gd", "Scots Gaelic"),
    DesktopTranslationLanguage("nso", "Sepedi"),
    DesktopTranslationLanguage("sr", "Serbian"),
    DesktopTranslationLanguage("st", "Sesotho"),
    DesktopTranslationLanguage("sn", "Shona"),
    DesktopTranslationLanguage("sd", "Sindhi"),
    DesktopTranslationLanguage("si", "Sinhala"),
    DesktopTranslationLanguage("sk", "Slovak"),
    DesktopTranslationLanguage("sl", "Slovenian"),
    DesktopTranslationLanguage("so", "Somali"),
    DesktopTranslationLanguage("es", "Spanish"),
    DesktopTranslationLanguage("su", "Sundanese"),
    DesktopTranslationLanguage("sw", "Swahili"),
    DesktopTranslationLanguage("sv", "Swedish"),
    DesktopTranslationLanguage("tg", "Tajik"),
    DesktopTranslationLanguage("ta", "Tamil"),
    DesktopTranslationLanguage("tt", "Tatar"),
    DesktopTranslationLanguage("te", "Telugu"),
    DesktopTranslationLanguage("th", "Thai"),
    DesktopTranslationLanguage("ti", "Tigrinya"),
    DesktopTranslationLanguage("ts", "Tsonga"),
    DesktopTranslationLanguage("tr", "Turkish"),
    DesktopTranslationLanguage("tk", "Turkmen"),
    DesktopTranslationLanguage("ak", "Twi"),
    DesktopTranslationLanguage("uk", "Ukrainian"),
    DesktopTranslationLanguage("ur", "Urdu"),
    DesktopTranslationLanguage("ug", "Uyghur"),
    DesktopTranslationLanguage("uz", "Uzbek"),
    DesktopTranslationLanguage("vi", "Vietnamese"),
    DesktopTranslationLanguage("cy", "Welsh"),
    DesktopTranslationLanguage("xh", "Xhosa"),
    DesktopTranslationLanguage("yi", "Yiddish"),
    DesktopTranslationLanguage("yo", "Yoruba"),
    DesktopTranslationLanguage("zu", "Zulu"),
)

private val byCode = DESKTOP_TRANSLATION_LANGUAGES.associateBy { it.code.lowercase(Locale.ROOT) }

/**
 * What to call [code] on screen, written in [inLocale].
 *
 * Asks the platform first so the name arrives in the reader's own language —
 * "Japanese" to an English reader, "japonés" to a Spanish one — and falls back
 * to the English name from the table for the codes ICU does not carry. A code
 * that is not in the table at all comes back as itself rather than blank, which
 * is wrong but legible; an empty label in a picker is neither.
 */
internal fun desktopTranslationLanguageName(code: String, inLocale: Locale): String {
    val entry = byCode[code.lowercase(Locale.ROOT)]
    // A subtag is the whole point of the codes that carry one, and the platform
    // renders it as territory: zh-CN comes back "Chinese (China)", which names
    // the country rather than the script and leaves zh-TW looking like the same
    // language somewhere else. For those the curated name is the accurate one.
    val platform = if ('-' in code) {
        ""
    } else {
        Locale.forLanguageTag(code).getDisplayName(inLocale)
    }
    // getDisplayName echoes the tag back when it knows nothing about it.
    val known = platform.isNotBlank() && !platform.equals(code, ignoreCase = true)
    val name = if (known) platform else entry?.fallbackName ?: code
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(inLocale) else it.toString() }
}
