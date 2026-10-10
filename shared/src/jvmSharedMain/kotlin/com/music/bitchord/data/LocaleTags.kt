package com.music.bitchord.data

import java.util.Locale

/**
 * Single place that knows zh-Hant / zh-Hans.
 *
 * Internally the app stores and compares full script tags ("zh-Hant",
 * "zh-Hans"). On the wire each service gets the dialect it understands:
 * YouTube / Google Translate want region tags ("zh-TW" / "zh-CN").
 */
object LocaleTags {
    const val ZH_HANT = "zh-Hant"
    const val ZH_HANS = "zh-Hans"
    const val ZH_TW_WIRE = "zh-TW"
    const val ZH_CN_WIRE = "zh-CN"

    /**
     * Normalize any incoming tag ("zh_TW", "zh-HK", "zh", "zh-Hant-TW", ...)
     * to "zh-Hant" / "zh-Hans" / base language. Bare "zh" stays "zh-Hans"
     * because every existing zh catalogue is Simplified.
     */
    fun normalizeAppTag(raw: String?): String {
        val tag = raw?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() } ?: return "en"
        val lower = tag.lowercase(Locale.ROOT)
        if (lower.startsWith("zh")) {
            // Explicit script wins.
            if ("hant" in lower) return ZH_HANT
            if ("hans" in lower) return ZH_HANS
            // Region implies script.
            if ("tw" in lower || "hk" in lower || "mo" in lower) return ZH_HANT
            if ("cn" in lower || "sg" in lower || "my" in lower) return ZH_HANS
            // Bare "zh" (or "zh-Latn" etc.): existing catalogues are Simplified.
            return ZH_HANS
        }
        // Other languages: base language only ("en-US" -> "en"), with legacy fixes.
        val base = Locale.forLanguageTag(tag).language.lowercase(Locale.ROOT).ifBlank { lower.substringBefore('-') }
        return when (base) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> base.ifBlank { "en" }
        }
    }

    /** Full tag for the current device/app locale, script-preserving. */
    fun systemTag(): String = normalizeAppTag(Locale.getDefault().toLanguageTag())

    /** What YouTube Music wants as `hl` / Innertube context hl. */
    fun ytHl(appTag: String): String {
        val norm = normalizeAppTag(appTag)
        return when (norm) {
            ZH_HANT -> ZH_TW_WIRE
            ZH_HANS -> ZH_CN_WIRE
            else -> norm.substringBefore('-')
        }
    }

    /** Geo for Innertube context gl + InnerTubeX locale. */
    fun ytGl(appTag: String): String {
        return when (normalizeAppTag(appTag)) {
            ZH_HANT -> "TW"
            ZH_HANS -> "CN"
            else -> "US"
        }
    }

    fun acceptLanguage(appTag: String): String {
        return when (normalizeAppTag(appTag)) {
            ZH_HANT -> "zh-TW,zh-Hant;q=0.9,zh;q=0.8,en-US;q=0.7,en;q=0.6"
            ZH_HANS -> "zh-CN,zh-Hans;q=0.9,zh;q=0.8,en-US;q=0.7,en;q=0.6"
            "en" -> "en-US,en;q=0.9"
            else -> {
                val base = normalizeAppTag(appTag).substringBefore('-')
                "$base,en-US;q=0.8,en;q=0.7"
            }
        }
    }

    /**
     * What the lyrics endpoint wants as `tl`. Google understands zh-TW /
     * zh-CN as scripts; sending bare "zh" always returns Simplified, and
     * sending "zh-Hant" is not reliably honoured — so map script to region.
     * An explicit translator choice in zh-TW/zh-CN form passes through.
     */
    fun translationWireTarget(tag: String): String {
        val t = tag.trim().replace('_', '-')
        if (t.equals("zh-TW", ignoreCase = true) || t.equals("zh-Hant", ignoreCase = true)) return ZH_TW_WIRE
        if (t.equals("zh-CN", ignoreCase = true) || t.equals("zh-Hans", ignoreCase = true)) return ZH_CN_WIRE
        val norm = normalizeAppTag(t)
        return when (norm) {
            ZH_HANT -> ZH_TW_WIRE
            ZH_HANS -> ZH_CN_WIRE
            else -> t.ifBlank { "en" }
        }
    }

    /** Script-aware equality: zh-Hant != zh-Hans != zh, unlike base-language compare. */
    fun sameLanguageScriptAware(first: String, second: String): Boolean {
        fun canon(tag: String): String {
            val t = tag.trim().replace('_', '-')
            val lower = t.lowercase(Locale.ROOT)
            if (lower.startsWith("zh")) {
                if ("tw" in lower || "hant" in lower || "hk" in lower || "mo" in lower) return "zh-hant"
                if ("cn" in lower || "hans" in lower || "sg" in lower) return "zh-hans"
                return "zh"
            }
            return Locale.forLanguageTag(t).language.lowercase(Locale.ROOT).ifBlank { lower.substringBefore('-') }
        }
        return canon(first) == canon(second)
    }

    fun isChineseTag(tag: String): Boolean =
        tag.trim().replace('_', '-').lowercase(Locale.ROOT).startsWith("zh")
}
