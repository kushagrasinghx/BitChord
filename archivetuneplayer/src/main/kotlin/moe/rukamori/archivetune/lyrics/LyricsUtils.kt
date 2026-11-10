/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.icu.text.Transliterator
import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import moe.rukamori.archivetune.db.entities.LyricsEntity
import java.lang.Character.UnicodeScript

data class LyricsRomanizationPreferences(
    val romanizeJapanese: Boolean,
    val romanizeKorean: Boolean,
    val romanizeChinese: Boolean,
    val romanizeHindi: Boolean,
    val romanizeOther: Boolean,

    val aiHandled: Boolean = false,
) {
    val isEnabled: Boolean
        get() =
            !aiHandled &&
                (romanizeJapanese || romanizeKorean || romanizeChinese || romanizeHindi || romanizeOther)

    val showsRomanization: Boolean
        get() = aiHandled || isEnabled
}

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val ENHANCED_LRC_WORD_TOKEN_REGEX = Regex("""(<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>)([^<]*)""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}\](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val YRC_WORD_TOKEN_REGEX = Regex("""(.*?)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)""")
    private val QrcTranslationLineRegex = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val QrcWordTimingDetectRegex = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")

    private val SyncedLinePrefixRegex = Regex("""^(\s*(?:\[[^\]]+])+)(\s*)(.*?)(\s*)$""")
    private val TTML_SPAN_REGEX =
        Regex(
            pattern = """<span\b[^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val TTML_BEGIN_ATTRIBUTE_REGEX = Regex("""\bbegin\s*=""", RegexOption.IGNORE_CASE)
    private val TTML_END_ATTRIBUTE_REGEX = Regex("""\b(?:end|dur)\s*=""", RegexOption.IGNORE_CASE)
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    private const val NBSP = '\u00A0'
    private const val ENHANCED_LRC_LAST_WORD_DEFAULT_DURATION_MS = 600L
    private const val MIN_WORD_DURATION_MS = 40L
    private const val SINGLE_WORD_LINE_MAX_SWEEP_MS = 3_000L
    private const val ENHANCED_LRC_TRAILING_LINE_DURATION_MS = 4_000L
    private const val GENERIC_ROMANIZATION_TRANSFORM = "Any-Latin; Latin-ASCII"
    private val OTHER_ROMANIZATION_EXCLUDED_SCRIPTS =
        setOf(
            UnicodeScript.LATIN,
            UnicodeScript.COMMON,
            UnicodeScript.INHERITED,
            UnicodeScript.HAN,
            UnicodeScript.HIRAGANA,
            UnicodeScript.KATAKANA,
            UnicodeScript.HANGUL,
            UnicodeScript.DEVANAGARI,
        )
    private val genericRomanizationTransliterator =
        ThreadLocal.withInitial {
            Transliterator.getInstance(GENERIC_ROMANIZATION_TRANSFORM)
        }

    private val KANA_ROMAJI_MAP: Map<String, String> =
        mapOf(

            "キャ" to "kya",
            "キュ" to "kyu",
            "キョ" to "kyo",
            "シャ" to "sha",
            "シュ" to "shu",
            "ショ" to "sho",
            "チャ" to "cha",
            "チュ" to "chu",
            "チョ" to "cho",
            "ニャ" to "nya",
            "ニュ" to "nyu",
            "ニョ" to "nyo",
            "ヒャ" to "hya",
            "ヒュ" to "hyu",
            "ヒョ" to "hyo",
            "ミャ" to "mya",
            "ミュ" to "myu",
            "ミョ" to "myo",
            "リャ" to "rya",
            "リュ" to "ryu",
            "リョ" to "ryo",
            "ギャ" to "gya",
            "ギュ" to "gyu",
            "ギョ" to "gyo",
            "ジャ" to "ja",
            "ジュ" to "ju",
            "ジョ" to "jo",
            "ヂャ" to "ja",
            "ヂュ" to "ju",
            "ヂョ" to "jo",
            "ビャ" to "bya",
            "ビュ" to "byu",
            "ビョ" to "byo",
            "ピャ" to "pya",
            "ピュ" to "pyu",
            "ピョ" to "pyo",

            "ア" to "a",
            "イ" to "i",
            "ウ" to "u",
            "エ" to "e",
            "オ" to "o",
            "カ" to "ka",
            "キ" to "ki",
            "ク" to "ku",
            "ケ" to "ke",
            "コ" to "ko",
            "サ" to "sa",
            "シ" to "shi",
            "ス" to "su",
            "セ" to "se",
            "ソ" to "so",
            "タ" to "ta",
            "チ" to "chi",
            "ツ" to "tsu",
            "テ" to "te",
            "ト" to "to",
            "ナ" to "na",
            "ニ" to "ni",
            "ヌ" to "nu",
            "ネ" to "ne",
            "ノ" to "no",
            "ハ" to "ha",
            "ヒ" to "hi",
            "フ" to "fu",
            "ヘ" to "he",
            "ホ" to "ho",
            "マ" to "ma",
            "ミ" to "mi",
            "ム" to "mu",
            "メ" to "me",
            "モ" to "mo",
            "ヤ" to "ya",
            "ユ" to "yu",
            "ヨ" to "yo",
            "ラ" to "ra",
            "リ" to "ri",
            "ル" to "ru",
            "レ" to "re",
            "ロ" to "ro",
            "ワ" to "wa",
            "ヲ" to "o",
            "ン" to "n",

            "ガ" to "ga",
            "ギ" to "gi",
            "グ" to "gu",
            "ゲ" to "ge",
            "ゴ" to "go",
            "ザ" to "za",
            "ジ" to "ji",
            "ズ" to "zu",
            "ゼ" to "ze",
            "ゾ" to "zo",
            "ダ" to "da",
            "ヂ" to "ji",
            "ヅ" to "zu",
            "デ" to "de",
            "ド" to "do",

            "バ" to "ba",
            "ビ" to "bi",
            "ブ" to "bu",
            "ベ" to "be",
            "ボ" to "bo",
            "パ" to "pa",
            "ピ" to "pi",
            "プ" to "pu",
            "ペ" to "pe",
            "ポ" to "po",

            "ー" to "",
        )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> =
        mapOf(
            "cho" to
                mapOf(
                    "ᄀ" to "g",
                    "ᄁ" to "kk",
                    "ᄂ" to "n",
                    "ᄃ" to "d",
                    "ᄄ" to "tt",
                    "ᄅ" to "r",
                    "ᄆ" to "m",
                    "ᄇ" to "b",
                    "ᄈ" to "pp",
                    "ᄉ" to "s",
                    "ᄊ" to "ss",
                    "ᄋ" to "",
                    "ᄌ" to "j",
                    "ᄍ" to "jj",
                    "ᄎ" to "ch",
                    "ᄏ" to "k",
                    "ᄐ" to "t",
                    "ᄑ" to "p",
                    "ᄒ" to "h",
                ),
            "jung" to
                mapOf(
                    "ᅡ" to "a",
                    "ᅢ" to "ae",
                    "ᅣ" to "ya",
                    "ᅤ" to "yae",
                    "ᅥ" to "eo",
                    "ᅦ" to "e",
                    "ᅧ" to "yeo",
                    "ᅨ" to "ye",
                    "ᅩ" to "o",
                    "ᅪ" to "wa",
                    "ᅫ" to "wae",
                    "ᅬ" to "oe",
                    "ᅭ" to "yo",
                    "ᅮ" to "u",
                    "ᅯ" to "wo",
                    "ᅰ" to "we",
                    "ᅱ" to "wi",
                    "ᅲ" to "yu",
                    "ᅳ" to "eu",
                    "ᅴ" to "eui",
                    "ᅵ" to "i",
                ),
            "jong" to
                mapOf(
                    "ᆨ" to "k",
                    "ᆨᄋ" to "g",
                    "ᆨᄂ" to "ngn",
                    "ᆨᄅ" to "ngn",
                    "ᆨᄆ" to "ngm",
                    "ᆨᄒ" to "kh",
                    "ᆩ" to "kk",
                    "ᆩᄋ" to "kg",
                    "ᆩᄂ" to "ngn",
                    "ᆩᄅ" to "ngn",
                    "ᆩᄆ" to "ngm",
                    "ᆩᄒ" to "kh",
                    "ᆪ" to "k",
                    "ᆪᄋ" to "ks",
                    "ᆪᄂ" to "ngn",
                    "ᆪᄅ" to "ngn",
                    "ᆪᄆ" to "ngm",
                    "ᆪᄒ" to "kch",
                    "ᆫ" to "n",
                    "ᆫᄅ" to "ll",
                    "ᆬ" to "n",
                    "ᆬᄋ" to "nj",
                    "ᆬᄂ" to "nn",
                    "ᆬᄅ" to "nn",
                    "ᆬᄆ" to "nm",
                    "ᆬㅎ" to "nch",
                    "ᆭ" to "n",
                    "ᆭᄋ" to "nh",
                    "ᆭᄅ" to "nn",
                    "ᆮ" to "t",
                    "ᆮᄋ" to "d",
                    "ᆮᄂ" to "nn",
                    "ᆮᄅ" to "nn",
                    "ᆮᄆ" to "nm",
                    "ᆮᄒ" to "th",
                    "ᆯ" to "l",
                    "ᆯᄋ" to "r",
                    "ᆯᄂ" to "ll",
                    "ᆯᄅ" to "ll",
                    "ᆰ" to "k",
                    "ᆰᄋ" to "lg",
                    "ᆰᄂ" to "ngn",
                    "ᆰᄅ" to "ngn",
                    "ᆰᄆ" to "ngm",
                    "ᆰᄒ" to "lkh",
                    "ᆱ" to "m",
                    "ᆱᄋ" to "lm",
                    "ᆱᄂ" to "mn",
                    "ᆱᄅ" to "mn",
                    "ᆱᄆ" to "mm",
                    "ᆱᄒ" to "lmh",
                    "ᆲ" to "p",
                    "ᆲᄋ" to "lb",
                    "ᆲᄂ" to "mn",
                    "ᆲᄅ" to "mn",
                    "ᆲᄆ" to "mm",
                    "ᆲᄒ" to "lph",
                    "ᆳ" to "t",
                    "ᆳᄋ" to "ls",
                    "ᆳᄂ" to "nn",
                    "ᆳᄅ" to "nn",
                    "ᆳᄆ" to "nm",
                    "ᆳᄒ" to "lsh",
                    "ᆴ" to "t",
                    "ᆴᄋ" to "lt",
                    "ᆴᄂ" to "nn",
                    "ᆴᄅ" to "nn",
                    "ᆴᄆ" to "nm",
                    "ᆴᄒ" to "lth",
                    "ᆵ" to "p",
                    "ᆵᄋ" to "lp",
                    "ᆵᄂ" to "mn",
                    "ᆵᄅ" to "mn",
                    "ᆵᄆ" to "mm",
                    "ᆵᄒ" to "lph",
                    "ᆶ" to "l",
                    "ᆶᄋ" to "lh",
                    "ᆶᄂ" to "ll",
                    "ᆶᄅ" to "ll",
                    "ᆶᄆ" to "lm",
                    "ᆶᄒ" to "lh",
                    "ᆷ" to "m",
                    "ᆷᄅ" to "mn",
                    "ᆸ" to "p",
                    "ᆸᄋ" to "b",
                    "ᆸᄂ" to "mn",
                    "ᆸᄅ" to "mn",
                    "ᆸᄆ" to "mm",
                    "ᆸᄒ" to "ph",
                    "ᆹ" to "p",
                    "ᆹᄋ" to "ps",
                    "ᆹᄂ" to "mn",
                    "ᆹᄅ" to "mn",
                    "ᆹᄆ" to "mm",
                    "ᆹᄒ" to "psh",
                    "ᆺ" to "t",
                    "ᆺᄋ" to "s",
                    "ᆺᄂ" to "nn",
                    "ᆺᄅ" to "nn",
                    "ᆺᄆ" to "nm",
                    "ᆺᄒ" to "sh",
                    "ᆻ" to "t",
                    "ᆻᄋ" to "ss",
                    "ᆻᄂ" to "tn",
                    "ᆻᄅ" to "tn",
                    "ᆻᄆ" to "nm",
                    "ᆻᄒ" to "th",
                    "ᆼ" to "ng",
                    "ᆽ" to "t",
                    "ᆽᄋ" to "j",
                    "ᆽᄂ" to "nn",
                    "ᆽᄅ" to "nn",
                    "ᆽᄆ" to "nm",
                    "ᆽᄒ" to "ch",
                    "ᆾ" to "t",
                    "ᆾᄋ" to "ch",
                    "ᆾᄂ" to "nn",
                    "ᆾᄅ" to "nn",
                    "ᆾᄆ" to "nm",
                    "ᆾᄒ" to "ch",
                    "ᆿ" to "k",
                    "ᆿᄋ" to "k",
                    "ᆿᄂ" to "ngn",
                    "ᆿᄅ" to "ngn",
                    "ᆿᄆ" to "ngm",
                    "ᆿᄒ" to "kh",
                    "ᇀ" to "t",
                    "ᇀᄋ" to "t",
                    "ᇀᄂ" to "nn",
                    "ᇀᄅ" to "nn",
                    "ᇀᄆ" to "nm",
                    "ᇀᄒ" to "th",
                    "ᇁ" to "p",
                    "ᇁᄋ" to "p",
                    "ᇁᄂ" to "mn",
                    "ᇁᄅ" to "mn",
                    "ᇁᄆ" to "mm",
                    "ᇁᄒ" to "ph",
                    "ᇂ" to "t",
                    "ᇂᄋ" to "h",
                    "ᇂᄂ" to "nn",
                    "ᇂᄅ" to "nn",
                    "ᇂᄆ" to "mm",
                    "ᇂᄒ" to "t",
                    "ᇂᄀ" to "k",
                ),
        )

    fun isTtml(lyrics: String): Boolean {
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun hasTranslation(lyrics: String): Boolean {
        if (lyrics.isBlank()) return false
        if (isTtml(lyrics)) {
            return lyrics.contains("data-archivetune", ignoreCase = true) &&
                lyrics.contains("translation", ignoreCase = true)
        }
        val seenPrefixes = HashSet<String>()
        lyrics.lineSequence().forEach { line ->
            val match = SyncedLinePrefixRegex.matchEntire(line) ?: return@forEach
            val prefix = match.groupValues[1]
            if (!seenPrefixes.add(prefix)) return true
        }
        return false
    }

    fun matchesExcludedLanguage(
        dominantCode: String,
        excludedLanguageCodes: Set<String>,
    ): Boolean {
        if (excludedLanguageCodes.isEmpty()) return false
        val dominant = dominantCode.trim().uppercase()
        if (dominant.isEmpty()) return false
        val normalized = excludedLanguageCodes.mapTo(HashSet()) { it.trim().uppercase() }
        if (dominant in normalized) return true
        return EXCLUSION_ALIASES[dominant]?.any { it in normalized } == true
    }

    private val EXCLUSION_ALIASES: Map<String, List<String>> =
        mapOf("CHINESE" to listOf("CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL"))

    fun shouldAutoTranslate(
        lyrics: String,
        targetLanguage: String,
        excludedLanguageCodes: Set<String>,
    ): Boolean {
        if (lyrics.isBlank()) return false
        val dominant = detectDominantLanguageCode(lyrics)

        if (dominant != null && matchesExcludedLanguage(dominant, excludedLanguageCodes)) {
            return false
        }
        val allowedScripts = allowedScriptsForLanguage(targetLanguage)

        return lyrics.asSequence().any { char ->
            char.isLetter() && UnicodeScript.of(char.code) !in allowedScripts
        }
    }

    fun detectDominantLanguageCode(lyrics: String): String? {
        if (lyrics.isBlank()) return null

        val scriptCounts = HashMap<UnicodeScript, Int>()
        for (char in lyrics) {
            if (!char.isLetter()) continue
            val script = UnicodeScript.of(char.code)
            when (script) {
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED -> Unit
                else -> scriptCounts[script] = (scriptCounts[script] ?: 0) + 1
            }
        }
        if (scriptCounts.isEmpty()) return null
        val dominantScript = scriptCounts.maxByOrNull { it.value }!!.key
        return when (dominantScript) {
            UnicodeScript.HAN -> "CHINESE"
            UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA -> "JAPANESE"
            UnicodeScript.HANGUL -> "KOREAN"
            UnicodeScript.DEVANAGARI -> "HINDI"
            UnicodeScript.ARABIC -> "ARABIC"
            UnicodeScript.CYRILLIC -> "RUSSIAN"
            UnicodeScript.THAI -> "THAI"
            UnicodeScript.HEBREW -> "HEBREW"
            UnicodeScript.GREEK -> "GREEK"
            UnicodeScript.ARMENIAN -> "ARMENIAN"
            UnicodeScript.GEORGIAN -> "GEORGIAN"
            else -> null
        }
    }

    private fun allowedScriptsForLanguage(language: String): Set<UnicodeScript> {
        val normalized = language.trim().lowercase().replace('_', '-').substringBefore('-')
        val code = when (normalized) {
            "english", "en" -> "en"
            "japanese", "ja" -> "ja"
            "korean", "ko" -> "ko"
            "chinese", "zh", "mandarin", "cmn", "cantonese", "yue" -> "zh"
            "hindi", "hi", "sanskrit", "sa", "marathi", "mr", "nepali", "ne" -> "hi"
            "arabic", "ar", "persian", "fa", "urdu", "ur" -> "ar"
            "russian", "ru", "ukrainian", "uk", "belarusian", "be", "bulgarian", "bg" -> "ru"
            "thai", "th" -> "th"
            "hebrew", "he", "yiddish", "yi" -> "he"
            "greek", "el" -> "el"
            "armenian", "hy" -> "hy"
            "georgian", "ka" -> "ka"
            else -> normalized
        }
        return when (code) {
            "ja" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HAN,
                UnicodeScript.HIRAGANA,
                UnicodeScript.KATAKANA,
            )
            "ko" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HANGUL,
            )
            "zh" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HAN,
            )
            "hi" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.DEVANAGARI,
            )
            "ar" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.ARABIC,
            )
            "ru" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.CYRILLIC,
            )
            "th" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.THAI,
            )
            "he" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HEBREW,
            )
            "el" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.GREEK,
            )
            "hy" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.ARMENIAN,
            )
            "ka" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.GEORGIAN,
            )
            else -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
            )
        }
    }

    fun isLineSyncedLrc(lyrics: String): Boolean =
        QRCParser.isQrc(normalizeLyricsText(lyrics)) ||
            lyrics.lineSequence().any { line ->
            val trimmedLine = line.trim()
            LINE_REGEX.matches(trimmedLine) || YRC_LINE_REGEX.matches(trimmedLine)
        }

    fun hasWordSyncedLyrics(lyrics: String): Boolean {
        val normalized = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalized)) return QRCParser.hasWordTimings(normalized)
        if (isTtml(normalized)) {
            return TTML_SPAN_REGEX.findAll(normalized).any { match ->
                TTML_BEGIN_ATTRIBUTE_REGEX.containsMatchIn(match.value) &&
                    TTML_END_ATTRIBUTE_REGEX.containsMatchIn(match.value)
            }
        }

        return normalized.lineSequence().any { line ->
            LINE_REGEX.containsMatchIn(line) &&
                (
                    ENHANCED_LRC_WORD_TIME_REGEX.containsMatchIn(line) ||
                        INLINE_MILLISECONDS_TIME_REGEX.containsMatchIn(line) ||
                        YRC_WORD_TIME_REGEX.containsMatchIn(line)
                )
        }
    }

    fun parseTtml(
        lyrics: String,
        durationSeconds: Int? = null,
    ): List<LyricsEntry> {
        val parsedLines = TTMLParser.parseTTML(normalizeLyricsText(lyrics))
        if (parsedLines.isEmpty()) return emptyList()
        val scale = 1.0

        return parsedLines
            .map { line ->
                val words =
                    line.words
                        .filter { it.text.isNotEmpty() }
                        .map { word ->
                            WordTimestamp(
                                text = word.text,
                                startTime = word.startTime * scale,
                                endTime = word.endTime * scale,
                                isBackground = word.isBackground,
                            )
                        }.takeIf { it.isNotEmpty() }

                LyricsEntry(
                    time = (line.startTime * scale * 1000.0).toLong(),
                    text = line.text,
                    words = words,
                    agent = line.agent,
                    providerRomanizedText = line.providerRomanizedText,
                    providerRomanizedWords = line.providerRomanizedWords,
                    providerRomanizedLanguage = line.providerRomanizedLanguage,
                    providerTranslationText = line.providerTranslationText,

                    durationMs = ((line.endTime - line.startTime) * scale * 1000.0)
                        .toLong()
                        .coerceAtLeast(0L),
                )
            }.sorted()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val normalizedLyrics = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalizedLyrics)) {
            val translationsByStartMs = extractQrcTranslations(normalizedLyrics)
            return QRCParser.parseQrc(normalizedLyrics).map { line ->
                val startMs = (line.startTime * 1000.0).toLong()
                LyricsEntry(
                    time = startMs,
                    text = line.text,
                    words =
                        line.words
                            .map { word ->
                                WordTimestamp(
                                    text = word.text,
                                    startTime = word.startTime,
                                    endTime = word.endTime,
                                )
                            }.takeIf { it.isNotEmpty() },
                    agent = line.agent,
                    providerTranslationText = translationsByStartMs[startMs],
                    durationMs = ((line.endTime - line.startTime) * 1000.0).toLong().coerceAtLeast(0L),
                )
            }
        }

        val lines = normalizedLyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        val merged = mergeLineSyncedTranslations(result).sorted()
        return clampEnhancedLrcLastWordEnds(merged)
    }

    private fun clampEnhancedLrcLastWordEnds(entries: List<LyricsEntry>): List<LyricsEntry> =
        entries.mapIndexed { index, entry ->
            val words = entry.words ?: return@mapIndexed entry
            if (words.isEmpty()) return@mapIndexed entry
            val lastWord = words.last()
            val nextStartMs =
                entries
                    .getOrNull(index + 1)
                    ?.takeIf { it.time > entry.time }
                    ?.time
                    ?: (entry.time + ENHANCED_LRC_TRAILING_LINE_DURATION_MS)
            val originalEndMs = (lastWord.endTime * 1000.0).toLong()
            var lastEndMs = originalEndMs

            if (words.size == 1 && lastEndMs < nextStartMs) {
                lastEndMs =
                    minOf(
                        nextStartMs,
                        (lastWord.startTime * 1000.0).toLong() + SINGLE_WORD_LINE_MAX_SWEEP_MS,
                    )
            }
            if (lastEndMs > nextStartMs) {
                val clamped = lastWord.copy(endTime = nextStartMs / 1000.0)
                entry.copy(words = words.dropLast(1) + clamped)
            } else if (lastEndMs != originalEndMs) {
                val stretched = lastWord.copy(endTime = lastEndMs / 1000.0)
                entry.copy(words = words.dropLast(1) + stretched)
            } else {
                entry
            }
        }

    private fun extractQrcTranslations(lyrics: String): Map<Long, String> {
        val wordTimedStartMs = mutableSetOf<Long>()
        val translationCandidates = mutableListOf<Pair<Long, String>>()
        lyrics.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val match = QrcTranslationLineRegex.matchEntire(trimmed) ?: return@forEach
            val startMs = match.groupValues[1].toLongOrNull() ?: return@forEach
            val content = match.groupValues[3]
            if (QrcWordTimingDetectRegex.containsMatchIn(content)) {
                wordTimedStartMs.add(startMs)
            } else if (content.isNotBlank()) {
                translationCandidates.add(startMs to content.trim())
            }
        }
        val translations = mutableMapOf<Long, String>()
        translationCandidates.forEach { (startMs, text) ->
            if (startMs in wordTimedStartMs && startMs !in translations) {
                translations[startMs] = text
            }
        }
        return translations
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == NBSP }

        val unwrapped = unwrapJsonEnvelope(stripCodeFence(raw))
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }

        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    private fun unwrapJsonEnvelope(value: String): String {
        val first = value.firstOrNull() ?: return value
        if (first != '{' && first != '[') return value
        val inner = LyricsPayload.unwrap(value) ?: return value
        if (inner == value) return value
        return inner
    }

    fun displayLyricsText(lyrics: String): String {
        val raw = normalizeLyricsText(lyrics)
        if (raw.isEmpty() || raw == LyricsEntity.LYRICS_NOT_FOUND) return ""

        val visibleLines =
            when {
                isTtml(raw) -> runCatching { parseTtml(raw).map { it.text } }.getOrElse { emptyList() }
                isLineSyncedLrc(raw) -> runCatching { parseLyrics(raw).map { it.text } }.getOrElse { emptyList() }
                raw.startsWith("<") -> emptyList()
                else -> raw.lines().map(::cleanInlineWordTimingText)
            }

        return visibleLines
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
            }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun hasMeaningfulLyricsContent(lyrics: String): Boolean = displayLyricsText(lyrics).isNotEmpty()

    fun lyricsOrNotFound(lyrics: String): String {
        val normalized = normalizeLyricsText(lyrics)
        return normalized.takeIf(::hasMeaningfulLyricsContent) ?: LyricsEntity.LYRICS_NOT_FOUND
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics

        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics

        val bodyLines =
            lines.drop(1).let { remainingLines ->
                if (remainingLines.lastOrNull()?.trim() == "```") {
                    remainingLines.dropLast(1)
                } else {
                    remainingLines
                }
            }

        return bodyLines.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    fun insertInstrumentalBreaks(
        entries: List<LyricsEntry>,
        songDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()
        insertIntroInstrumentalIfNeeded(entries, result)
        result.addAll(entries)
        insertOutroInstrumentalIfNeeded(entries, songDurationMs, result)
        return result
    }

    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    private fun insertIntroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        result: MutableList<LyricsEntry>,
    ) {
        val firstTimedVocalEntry = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() } ?: return
        val introGapMs = firstTimedVocalEntry.time - INSTRUMENTAL_INTRO_START_MS
        if (introGapMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = INSTRUMENTAL_INTRO_START_MS,
                text = "",
                isInstrumental = true,
                durationMs = introGapMs,
            ),
        )
    }

    private fun insertOutroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        songDurationMs: Long,
        result: MutableList<LyricsEntry>,
    ) {
        if (songDurationMs <= 0L) return
        val lastVocalEntry = entries.lastOrNull { it.text.isNotBlank() } ?: return
        val outroStartMs = lastVocalEntry.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
        val outroDurationMs = songDurationMs - outroStartMs
        if (outroDurationMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = outroStartMs,
                text = "",
                isInstrumental = true,
                durationMs = outroDurationMs,
            ),
        )
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val rawText = matchResult.groupValues[3]
        val text = cleanInlineWordTimingText(rawText)
        val inlineWords =
            extractEnhancedLrcWordTimestamps(rawText)
                ?: extractYrcWordTimestamps(rawText)
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLongOrNull() ?: 0L
                when (milString.length) {
                    1 -> mil *= 100
                    2 -> mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                if (inlineWords != null) {
                    val lineStartSec = time / 1000.0
                    val words =
                        inlineWords.map { word ->
                            if (word.startTime >= lineStartSec - 0.001) {
                                word
                            } else {
                                word.copy(
                                    startTime = lineStartSec,
                                    endTime = maxOf(word.endTime, lineStartSec + 0.05),
                                )
                            }
                        }
                    LyricsEntry(time, text, words = words)
                } else {
                    LyricsEntry(time, text)
                }
            }.toList()
    }

    private fun parseEnhancedLrcStampMs(stamp: String): Long? {
        val body = stamp.removePrefix("<").removeSuffix(">")
        val parts = body.split(':', '.')
        if (parts.size < 2) return null
        val min = parts[0].toLongOrNull() ?: return null
        val sec = parts[1].toLongOrNull() ?: return null
        val mil = parts.getOrNull(2)?.let { fraction ->
            when (fraction.length) {
                1 -> fraction.toLongOrNull()?.times(100)
                2 -> fraction.toLongOrNull()?.times(10)
                else -> {
                    val digits = fraction.take(3)
                    val padded = digits.padEnd(3, '0')
                    padded.toLongOrNull()
                }
            }
        } ?: 0L
        return min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
    }

    private fun extractEnhancedLrcWordTimestamps(rawText: String): List<WordTimestamp>? {
        if (!ENHANCED_LRC_WORD_TIME_REGEX.containsMatchIn(rawText)) return null
        val tokens = ENHANCED_LRC_WORD_TOKEN_REGEX.findAll(rawText).toList()

        if (tokens.isEmpty()) return null

        val words = mutableListOf<WordTimestamp>()
        tokens.forEachIndexed { index, token ->
            val startMs = parseEnhancedLrcStampMs(token.groupValues[1]) ?: return@forEachIndexed
            val normalizedText =
                token.groupValues[2]
                    .replace(WHITESPACE_REGEX, " ")
            val wordText = normalizedText.trim { it.isWhitespace() || it == NBSP }
            if (wordText.isEmpty()) return@forEachIndexed

            val textWithGap = if (normalizedText.endsWith(" ")) "$wordText " else wordText
            val nextStartMs =
                tokens
                    .getOrNull(index + 1)
                    ?.let { parseEnhancedLrcStampMs(it.groupValues[1]) }
            val endMs = nextStartMs ?: (startMs + ENHANCED_LRC_LAST_WORD_DEFAULT_DURATION_MS)
            words.add(
                WordTimestamp(
                    text = textWithGap,
                    startTime = startMs / 1000.0,
                    endTime = maxOf(endMs, startMs + MIN_WORD_DURATION_MS) / 1000.0,
                ),
            )
        }
        return words.takeIf { it.isNotEmpty() }
    }

    private fun extractYrcWordTimestamps(rawText: String): List<WordTimestamp>? {
        if (!YRC_WORD_TIME_REGEX.containsMatchIn(rawText)) return null
        val tokens = YRC_WORD_TOKEN_REGEX.findAll(rawText).toList()
        if (tokens.isEmpty()) return null

        val words = mutableListOf<WordTimestamp>()
        tokens.forEach { token ->
            val wordText =
                token.groupValues[1]
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
                    .let { if (token.groupValues[1].endsWith(" ")) "$it " else it }
            if (wordText.isBlank()) return@forEach
            val startMs = token.groupValues[2].toLongOrNull() ?: return@forEach
            val durationMs = token.groupValues[3].toLongOrNull() ?: return@forEach
            val endMs = startMs + durationMs.coerceAtLeast(0L)
            words.add(
                WordTimestamp(
                    text = wordText,
                    startTime = startMs / 1000.0,
                    endTime = maxOf(endMs, startMs + MIN_WORD_DURATION_MS) / 1000.0,
                ),
            )
        }
        return words.takeIf { it.isNotEmpty() }
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = matchResult.groupValues[1].toLongOrNull() ?: return null
        val text = cleanInlineWordTimingText(matchResult.groupValues[2])
        return listOf(LyricsEntry(time, text))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val mergedByTime = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = mergedByTime[entry.time]
            if (existing == null) {
                mergedByTime[entry.time] = entry
                return@forEach
            }

            val translatedText =
                entry.text
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }

            if (translatedText != null && existing.providerTranslationText == null) {
                mergedByTime[entry.time] = existing.copy(providerTranslationText = translatedText)
            }
        }
        return mergedByTime.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String =
        text
            .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
            .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
            .replace(YRC_WORD_TIME_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim { it.isWhitespace() || it == NBSP }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        val exactTarget = position
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < exactTarget) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val currentIdx = high.coerceIn(0, lines.lastIndex)

        val nextIdx = currentIdx + 1
        if (nextIdx > lines.lastIndex) return currentIdx

        val currentLine = lines[currentIdx]
        val nextLine = lines[nextIdx]

        if (currentLine.durationMs > 0L) {
            val currentLineEndMs = currentLine.time + currentLine.durationMs
            if (position < currentLineEndMs) {
                return currentIdx
            }
        }

        val gapBetweenLineStartsMs = nextLine.time - currentLine.time
        val effectiveLeadMs = if (gapBetweenLineStartsMs > 2_000L) 0L else leadMs

        return if (position + effectiveLeadMs >= nextLine.time) {
            nextIdx
        } else {
            currentIdx
        }
    }

    fun hasTrueWordSync(entry: LyricsEntry): Boolean {
        val raw = entry.words ?: return false
        val words = raw.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return false
        if (words.size == 1) {
            val only = words.first()
            return (only.endTime - only.startTime) > 0.0
        }

        val startTimes = words.map { it.startTime }
        val endTimes = words.map { it.endTime }

        if (startTimes.distinct().size == 1) return false

        if (endTimes.distinct().size == 1) return false

        val durations = words.map { (it.endTime - it.startTime).coerceAtLeast(0.0) }

        if (durations.all { it <= 0.0 }) return false

        val lineStart = startTimes.min()
        val lineEnd = endTimes.max()
        val lineDuration = lineEnd - lineStart

        if (lineDuration > 0.0 && words.size > 2) {
            val tolerance = 0.05
            val isEvenlyDistributed = startTimes.indices.all { i ->
                val expected = lineStart + (lineDuration * i / (words.size - 1))
                kotlin.math.abs(startTimes[i] - expected) < tolerance
            }
            if (isEvenlyDistributed) {
                val positiveDurations = durations.filter { it > 0.0 }
                if (positiveDurations.size >= 3) {
                    val avg = positiveDurations.average()
                    if (avg > 0.0) {
                        val variance = positiveDurations.map { (it - avg) * (it - avg) }.average()
                        val stddev = kotlin.math.sqrt(variance)
                        if (stddev / avg < 0.1) {
                            return false
                        }
                    }
                }
            }
        }

        return true
    }

    private fun hiraganaToKatakana(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                if (ch in '\u3041'..'\u3096') {
                    (ch.code + 0x60).toChar()
                } else {
                    ch
                },
            )
        }
        return sb.toString()
    }

    suspend fun romanizeJapanese(text: String): String =
        withContext(Dispatchers.Default) {
            val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull() ?: return@withContext text
            val tokens = tokenizer.tokenize(text)

            val romanizedTokens =
                tokens.mapIndexed { index, token ->
                    val currentReading =
                        if (token.reading.isNullOrEmpty() || token.reading == "*") {
                            token.surface
                        } else {
                            token.reading
                        }

                    val katakanaReading = hiraganaToKatakana(currentReading)

                    val nextTokenReading =
                        if (index + 1 < tokens.size) {
                            val nextReading =
                                tokens[index + 1].reading?.takeIf { it.isNotEmpty() && it != "*" }
                                    ?: tokens[index + 1].surface
                            hiraganaToKatakana(nextReading)
                        } else {
                            null
                        }
                    katakanaToRomaji(katakanaReading, nextTokenReading)
                }
            romanizedTokens.joinToString(" ")
        }

    fun katakanaToRomaji(
        katakana: String?,
        nextKatakana: String? = null,
    ): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length)
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false

            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed && katakana[i] == 'ッ') {
                val nextCharInSameString = katakana.getOrNull(i + 1)
                val nextCharToDouble = nextCharInSameString ?: nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji =
                        KANA_ROMAJI_MAP[nextCharToDouble.toString()]
                            ?: nextCharToDouble.toString()

                    val firstLetter = nextCharRomaji.firstOrNull()?.lowercase()?.trim()
                    if (firstLetter != null && firstLetter.isNotEmpty()) {
                        romajiBuilder.append(firstLetter)
                    }
                }
                i += 1
                consumed = true
            }

            if (!consumed && katakana[i] == 'ー') {
                val lastChar = romajiBuilder.lastOrNull()
                val extension = when (lastChar) {
                    'a' -> "a"
                    'i' -> "i"
                    'u' -> "u"
                    'e' -> "e"
                    'o' -> "o"
                    else -> ""
                }
                romajiBuilder.append(extension)
                i += 1
                consumed = true
            }

            if (!consumed) {
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String =
        withContext(Dispatchers.Default) {
            val romajaBuilder = StringBuilder()
            var prevFinal: String? = null

            for (i in text.indices) {
                val char = text[i]

                if (char in '\uAC00'..'\uD7A3') {
                    val syllableIndex = char.code - 0xAC00

                    val choIndex = syllableIndex / (21 * 28)
                    val jungIndex = (syllableIndex % (21 * 28)) / 28
                    val jongIndex = syllableIndex % 28

                    val choChar = (0x1100 + choIndex).toChar().toString()
                    val jungChar = (0x1161 + jungIndex).toChar().toString()
                    val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                    if (prevFinal != null) {
                        val contextKey = prevFinal + choChar
                        val jong =
                            HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                                ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                                ?: prevFinal
                        romajaBuilder.append(jong)
                    }

                    val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                    val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                    romajaBuilder.append(cho).append(jung)

                    prevFinal = jongChar
                } else {
                    if (prevFinal != null) {
                        val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                        romajaBuilder.append(jong)
                        prevFinal = null
                    }
                    romajaBuilder.append(char)
                }
            }

            if (prevFinal != null) {
                val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                romajaBuilder.append(jong)
            }

            romajaBuilder.toString()
        }

    private val DEVANAGARI_INDEPENDENT_VOWELS =
        mapOf(
            'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ii", 'उ' to "u", 'ऊ' to "uu",
            'ऋ' to "ri", 'ॠ' to "rii", 'ऌ' to "lri", 'ॡ' to "lrii",
            'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        )

    private val DEVANAGARI_MATRAS =
        mapOf(
            'ा' to "aa", 'ि' to "i", 'ी' to "ii", 'ु' to "u", 'ू' to "uu",
            'ृ' to "ri", 'ॄ' to "rii", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
            'ॅ' to "e", 'ॉ' to "o", 'ॢ' to "lri", 'ॣ' to "lrii",
        )

    private val DEVANAGARI_CONSONANTS =
        mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
            'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
            'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
            'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
            'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
            'ळ' to "l",

        )

    private val DEVANAGARI_OTHER =
        mapOf(
            'ॐ' to "om",
            '।' to ".", '॥' to "..",
            'ऽ' to "'",
            'ं' to "n",
            'ः' to "h",
            'ँ' to "n",
            '्' to "",
        )

    private val DEVANAGARI_NUMERALS =
        mapOf(
            '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
            '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9",
        )

    private val LABIALS = setOf('प', 'फ', 'ब', 'भ', 'म')

    suspend fun romanizeHindi(text: String): String =
        withContext(Dispatchers.Default) {
            val sb = StringBuilder(text.length * 2)
            var i = 0
            val n = text.length
            while (i < n) {
                val ch = text[i]

                if (DEVANAGARI_NUMERALS[ch] != null) {
                    sb.append(DEVANAGARI_NUMERALS[ch])
                    i++
                    continue
                }

                if (ch == 'ं') {
                    val next = text.getOrNull(i + 1)
                    sb.append(if (next != null && next in LABIALS) "m" else "n")
                    i++
                    continue
                }

                if (ch == 'ँ') {
                    sb.append("n")
                    i++
                    continue
                }

                if (ch == 'ः') {
                    sb.append("h")
                    i++
                    continue
                }

                if (ch == '्') {
                    i++
                    continue
                }

                val matra = DEVANAGARI_MATRAS[ch]
                if (matra != null) {
                    sb.append(matra)
                    i++
                    continue
                }

                val independentVowel = DEVANAGARI_INDEPENDENT_VOWELS[ch]
                if (independentVowel != null) {
                    sb.append(independentVowel)
                    i++
                    continue
                }

                val consonant = DEVANAGARI_CONSONANTS[ch]
                if (consonant != null) {
                    val next = text.getOrNull(i + 1)
                    val suppressInherentA =
                        next != null && (
                            next in DEVANAGARI_MATRAS ||
                                next == '्' ||
                                next == 'ं' ||
                                next == 'ः' ||
                                next == 'ँ'
                        )
                    sb.append(consonant)
                    if (!suppressInherentA) {
                        sb.append('a')
                    }
                    i++
                    continue
                }

                val other = DEVANAGARI_OTHER[ch]
                if (other != null) {
                    sb.append(other)
                    i++
                    continue
                }

                sb.append(ch)
                i++
            }
            sb.toString()
        }

    fun isJapanese(text: String): Boolean =
        text.any { char ->
            (char in '\u3040'..'\u309F') ||
                (char in '\u30A0'..'\u30FF') ||

                (char in '\u4E00'..'\u9FFF')
        }

    fun isKorean(text: String): Boolean =
        text.any { char ->
            (char in '\uAC00'..'\uD7A3')
        }

    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false

        val hanCharCount = text.count { hasScript(it, UnicodeScript.HAN) }
        if (hanCharCount == 0) return false

        val japaneseKanaCount = text.count { hasScript(it, UnicodeScript.HIRAGANA) || hasScript(it, UnicodeScript.KATAKANA) }
        val hangulCount = text.count { hasScript(it, UnicodeScript.HANGUL) }

        return japaneseKanaCount == 0 && hangulCount == 0
    }

    fun isHindi(text: String): Boolean = text.any { hasScript(it, UnicodeScript.DEVANAGARI) }

    fun hasOtherRomanizableScript(text: String): Boolean {
        return text.any { char ->
            if (!char.isLetter()) return@any false
            val script = UnicodeScript.of(char.code)
            script !in OTHER_ROMANIZATION_EXCLUDED_SCRIPTS
        }
    }

    fun shouldRomanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || text.isBlank()) return false

        return when {
            preferences.romanizeJapanese && looksJapanese(text) -> true
            preferences.romanizeKorean && isKorean(text) -> true
            preferences.romanizeHindi && isHindi(text) -> true
            preferences.romanizeChinese && isChinese(text) -> true
            preferences.romanizeOther && hasOtherRomanizableScript(text) -> true
            else -> false
        }
    }

    fun hasRomanizableScript(text: String): Boolean {
        if (text.isBlank()) return false
        return looksJapanese(text) ||
            isKorean(text) ||
            isHindi(text) ||
            isChinese(text) ||
            hasOtherRomanizableScript(text)
    }

    fun shouldUseProvidedRomanization(
        originalText: String,
        providerRomanizedText: String?,
        providerRomanizedLanguage: String?,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || originalText.isBlank()) return false
        val normalized =
            providerRomanizedText
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return false
        if (normalized.equals(originalText.trim(), ignoreCase = true)) return false

        val language =
            providerRomanizedLanguage
                ?.substringBefore("-")
                ?.substringBefore("_")
                ?.lowercase()

        return when (language) {
            "ja" -> preferences.romanizeJapanese
            "ko" -> preferences.romanizeKorean
            "zh", "cmn", "yue" -> preferences.romanizeChinese
            "hi", "sa", "mr", "ne" -> preferences.romanizeHindi
            null, "" -> shouldRomanizeLyricsLine(originalText, preferences)
            else -> preferences.romanizeOther || shouldRomanizeLyricsLine(originalText, preferences)
        }
    }

    fun providedRomanizedTextForEntry(
        entry: LyricsEntry,
        preferences: LyricsRomanizationPreferences,
    ): String? =
        entry.providerRomanizedText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf {
                shouldUseProvidedRomanization(
                    originalText = entry.text,
                    providerRomanizedText = it,
                    providerRomanizedLanguage = entry.providerRomanizedLanguage,
                    preferences = preferences,
                )
            }

    fun providedTranslationTextForEntry(entry: LyricsEntry): String? =
        entry.providerTranslationText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(entry.text.trim(), ignoreCase = true) }

    fun providedRomanizedWordsForEntry(
        entry: LyricsEntry,
        expectedWordCount: Int,
        preferences: LyricsRomanizationPreferences,
    ): List<String?>? {
        if (expectedWordCount <= 0) return null
        if (providedRomanizedTextForEntry(entry, preferences) == null) return null

        val words =
            entry.providerRomanizedWords
                ?.map { word -> word.replace(WHITESPACE_REGEX, " ").trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.size == expectedWordCount }
                ?: return null

        return words
    }

    suspend fun romanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (!shouldRomanizeLyricsLine(text, preferences)) return null

        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(text) -> romanizeJapanese(text)
                preferences.romanizeKorean && isKorean(text) -> romanizeKorean(text)
                preferences.romanizeHindi && isHindi(text) -> romanizeHindi(text)
                preferences.romanizeChinese && isChinese(text) -> romanizeWithIcu(text)
                preferences.romanizeOther && hasOtherRomanizableScript(text) -> romanizeWithIcu(text)
                else -> null
            }

        return normalizeRomanizedText(text, romanized)
    }

    suspend fun romanizeLyricsWordWithLineContext(
        word: String,
        lineText: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (word.isBlank()) return null
        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(lineText) -> romanizeJapanese(word)
                preferences.romanizeKorean && isKorean(lineText) -> romanizeKorean(word)
                preferences.romanizeHindi && isHindi(lineText) -> romanizeHindi(word)
                preferences.romanizeChinese && isChinese(lineText) -> romanizeWithIcu(word)
                preferences.romanizeOther && hasOtherRomanizableScript(lineText) -> romanizeWithIcu(word)
                else -> null
            }
        return normalizeRomanizedText(word, romanized)
    }

    suspend fun romanizeWordsForLine(
        words: List<String>,
        lineText: String,
        preferences: LyricsRomanizationPreferences,
    ): List<String?> {
        if (words.isEmpty()) return emptyList()

        if (preferences.romanizeJapanese && looksJapanese(lineText)) {
            return romanizeJapaneseWordsForLine(words, lineText)
        }

        return words.map { word ->
            romanizeLyricsWordWithLineContext(word, lineText, preferences)
        }
    }

    private suspend fun romanizeJapaneseWordsForLine(
        words: List<String>,
        lineText: String,
    ): List<String?> = withContext(Dispatchers.Default) {
        if (words.isEmpty()) return@withContext emptyList()
        val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull()
            ?: return@withContext words.map { null }

        val tokens = tokenizer.tokenize(lineText)
        if (tokens.isEmpty()) return@withContext words.map { null }

        val tokenCount = tokens.size
        val tokenStarts = IntArray(tokenCount)
        val tokenEnds = IntArray(tokenCount)
        val tokenRomaji = ArrayList<String>(tokenCount)
        for (i in 0 until tokenCount) {
            val token = tokens[i]
            val surface = token.surface
            tokenStarts[i] = token.position
            tokenEnds[i] = token.position + surface.length

            val currentReading =
                if (token.reading.isNullOrEmpty() || token.reading == "*") {
                    surface
                } else {
                    token.reading
                }
            val katakanaReading = hiraganaToKatakana(currentReading)
            val nextTokenReading =
                if (i + 1 < tokenCount) {
                    val nr = tokens[i + 1].reading
                    val nextReading =
                        if (nr.isNullOrEmpty() || nr == "*") tokens[i + 1].surface else nr
                    hiraganaToKatakana(nextReading)
                } else {
                    null
                }
            tokenRomaji.add(katakanaToRomaji(katakanaReading, nextTokenReading))
        }

        val result = ArrayList<String?>(words.size)
        var scanOffset = 0
        var tokenIdx = 0
        for (word in words) {
            val wordStart = lineText.indexOf(word, startIndex = scanOffset)
            if (wordStart < 0) {
                result.add(null)
                continue
            }
            val wordEnd = wordStart + word.length

            while (tokenIdx < tokenCount && tokenEnds[tokenIdx] <= wordStart) {
                tokenIdx++
            }

            val romajiBuilder = StringBuilder()
            var tIdx = tokenIdx
            while (tIdx < tokenCount && tokenStarts[tIdx] < wordEnd) {
                if (tokenEnds[tIdx] > wordStart) {
                    romajiBuilder.append(tokenRomaji[tIdx])
                }
                tIdx++
            }

            result.add(romajiBuilder.toString().takeIf { it.isNotEmpty() })
            scanOffset = wordEnd
        }
        result
    }

    private suspend fun romanizeWithIcu(text: String): String =
        withContext(Dispatchers.Default) {
            genericRomanizationTransliterator.get().transliterate(text)
        }

    private fun normalizeRomanizedText(
        original: String,
        romanized: String?,
    ): String? {
        val normalized =
            romanized
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

        return normalized.takeUnless { it.equals(original.trim(), ignoreCase = true) }
    }

    private fun looksJapanese(text: String): Boolean {
        if (
            text.any {
                hasScript(it, UnicodeScript.HIRAGANA) ||
                    hasScript(it, UnicodeScript.KATAKANA) ||
                    it == '々' ||
                    it == '〆' ||
                    it == 'ヶ'
            }
        ) {
            return true
        }

        val hasKanji = text.any { it in '\u4E00'..'\u9FFF' }
        return hasKanji && JapaneseLanguagePackManager.tokenizerOrNull() != null
    }

    private fun hasScript(
        char: Char,
        script: UnicodeScript,
    ): Boolean = char.isLetter() && UnicodeScript.of(char.code) == script
}
