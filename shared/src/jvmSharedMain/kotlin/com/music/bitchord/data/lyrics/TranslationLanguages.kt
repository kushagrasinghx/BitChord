package com.music.bitchord.data.lyrics

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
data class TranslationLanguage(val code: String, val fallbackName: String)

/**
 * Every language Google Translate offers, in the order its own picker lists
 * them: alphabetical by English name, which is not the order they end up in
 * once localised, but is the order anyone who has used Translate expects.
 */
val TRANSLATION_LANGUAGES = listOf(
    TranslationLanguage("af", "Afrikaans"),
    TranslationLanguage("sq", "Albanian"),
    TranslationLanguage("am", "Amharic"),
    TranslationLanguage("ar", "Arabic"),
    TranslationLanguage("hy", "Armenian"),
    TranslationLanguage("as", "Assamese"),
    TranslationLanguage("ay", "Aymara"),
    TranslationLanguage("az", "Azerbaijani"),
    TranslationLanguage("bm", "Bambara"),
    TranslationLanguage("eu", "Basque"),
    TranslationLanguage("be", "Belarusian"),
    TranslationLanguage("bn", "Bengali"),
    TranslationLanguage("bho", "Bhojpuri"),
    TranslationLanguage("bs", "Bosnian"),
    TranslationLanguage("bg", "Bulgarian"),
    TranslationLanguage("ca", "Catalan"),
    TranslationLanguage("ceb", "Cebuano"),
    TranslationLanguage("ny", "Chichewa"),
    TranslationLanguage("zh-CN", "Chinese (Simplified)"),
    TranslationLanguage("zh-TW", "Chinese (Traditional)"),
    TranslationLanguage("co", "Corsican"),
    TranslationLanguage("hr", "Croatian"),
    TranslationLanguage("cs", "Czech"),
    TranslationLanguage("da", "Danish"),
    TranslationLanguage("dv", "Dhivehi"),
    TranslationLanguage("doi", "Dogri"),
    TranslationLanguage("nl", "Dutch"),
    TranslationLanguage("en", "English"),
    TranslationLanguage("eo", "Esperanto"),
    TranslationLanguage("et", "Estonian"),
    TranslationLanguage("ee", "Ewe"),
    TranslationLanguage("tl", "Filipino"),
    TranslationLanguage("fi", "Finnish"),
    TranslationLanguage("fr", "French"),
    TranslationLanguage("fy", "Frisian"),
    TranslationLanguage("gl", "Galician"),
    TranslationLanguage("ka", "Georgian"),
    TranslationLanguage("de", "German"),
    TranslationLanguage("el", "Greek"),
    TranslationLanguage("gn", "Guarani"),
    TranslationLanguage("gu", "Gujarati"),
    TranslationLanguage("ht", "Haitian Creole"),
    TranslationLanguage("ha", "Hausa"),
    TranslationLanguage("haw", "Hawaiian"),
    TranslationLanguage("iw", "Hebrew"),
    TranslationLanguage("hi", "Hindi"),
    TranslationLanguage("hmn", "Hmong"),
    TranslationLanguage("hu", "Hungarian"),
    TranslationLanguage("is", "Icelandic"),
    TranslationLanguage("ig", "Igbo"),
    TranslationLanguage("ilo", "Ilocano"),
    TranslationLanguage("id", "Indonesian"),
    TranslationLanguage("ga", "Irish"),
    TranslationLanguage("it", "Italian"),
    TranslationLanguage("ja", "Japanese"),
    TranslationLanguage("jw", "Javanese"),
    TranslationLanguage("kn", "Kannada"),
    TranslationLanguage("kk", "Kazakh"),
    TranslationLanguage("km", "Khmer"),
    TranslationLanguage("rw", "Kinyarwanda"),
    TranslationLanguage("gom", "Konkani"),
    TranslationLanguage("ko", "Korean"),
    TranslationLanguage("kri", "Krio"),
    TranslationLanguage("ku", "Kurdish (Kurmanji)"),
    TranslationLanguage("ckb", "Kurdish (Sorani)"),
    TranslationLanguage("ky", "Kyrgyz"),
    TranslationLanguage("lo", "Lao"),
    TranslationLanguage("la", "Latin"),
    TranslationLanguage("lv", "Latvian"),
    TranslationLanguage("ln", "Lingala"),
    TranslationLanguage("lt", "Lithuanian"),
    TranslationLanguage("lg", "Luganda"),
    TranslationLanguage("lb", "Luxembourgish"),
    TranslationLanguage("mk", "Macedonian"),
    TranslationLanguage("mai", "Maithili"),
    TranslationLanguage("mg", "Malagasy"),
    TranslationLanguage("ms", "Malay"),
    TranslationLanguage("ml", "Malayalam"),
    TranslationLanguage("mt", "Maltese"),
    TranslationLanguage("mi", "Maori"),
    TranslationLanguage("mr", "Marathi"),
    TranslationLanguage("mni-Mtei", "Meiteilon (Manipuri)"),
    TranslationLanguage("lus", "Mizo"),
    TranslationLanguage("mn", "Mongolian"),
    TranslationLanguage("my", "Myanmar (Burmese)"),
    TranslationLanguage("ne", "Nepali"),
    TranslationLanguage("no", "Norwegian"),
    TranslationLanguage("or", "Odia (Oriya)"),
    TranslationLanguage("om", "Oromo"),
    TranslationLanguage("ps", "Pashto"),
    TranslationLanguage("fa", "Persian"),
    TranslationLanguage("pl", "Polish"),
    TranslationLanguage("pt", "Portuguese"),
    TranslationLanguage("pa", "Punjabi"),
    TranslationLanguage("qu", "Quechua"),
    TranslationLanguage("ro", "Romanian"),
    TranslationLanguage("ru", "Russian"),
    TranslationLanguage("sm", "Samoan"),
    TranslationLanguage("sa", "Sanskrit"),
    TranslationLanguage("gd", "Scots Gaelic"),
    TranslationLanguage("nso", "Sepedi"),
    TranslationLanguage("sr", "Serbian"),
    TranslationLanguage("st", "Sesotho"),
    TranslationLanguage("sn", "Shona"),
    TranslationLanguage("sd", "Sindhi"),
    TranslationLanguage("si", "Sinhala"),
    TranslationLanguage("sk", "Slovak"),
    TranslationLanguage("sl", "Slovenian"),
    TranslationLanguage("so", "Somali"),
    TranslationLanguage("es", "Spanish"),
    TranslationLanguage("su", "Sundanese"),
    TranslationLanguage("sw", "Swahili"),
    TranslationLanguage("sv", "Swedish"),
    TranslationLanguage("tg", "Tajik"),
    TranslationLanguage("ta", "Tamil"),
    TranslationLanguage("tt", "Tatar"),
    TranslationLanguage("te", "Telugu"),
    TranslationLanguage("th", "Thai"),
    TranslationLanguage("ti", "Tigrinya"),
    TranslationLanguage("ts", "Tsonga"),
    TranslationLanguage("tr", "Turkish"),
    TranslationLanguage("tk", "Turkmen"),
    TranslationLanguage("ak", "Twi"),
    TranslationLanguage("uk", "Ukrainian"),
    TranslationLanguage("ur", "Urdu"),
    TranslationLanguage("ug", "Uyghur"),
    TranslationLanguage("uz", "Uzbek"),
    TranslationLanguage("vi", "Vietnamese"),
    TranslationLanguage("cy", "Welsh"),
    TranslationLanguage("xh", "Xhosa"),
    TranslationLanguage("yi", "Yiddish"),
    TranslationLanguage("yo", "Yoruba"),
    TranslationLanguage("zu", "Zulu"),
)

private val byCode = TRANSLATION_LANGUAGES.associateBy { it.code.lowercase(Locale.ROOT) }

/**
 * What to call [code] on screen, written in [inLocale].
 *
 * Asks the platform first so the name arrives in the reader's own language —
 * "Japanese" to an English reader, "japonés" to a Spanish one — and falls back
 * to the English name from the table for the codes ICU does not carry. A code
 * that is not in the table at all comes back as itself rather than blank, which
 * is wrong but legible; an empty label in a picker is neither.
 */
fun translationLanguageName(code: String, inLocale: Locale): String {
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
