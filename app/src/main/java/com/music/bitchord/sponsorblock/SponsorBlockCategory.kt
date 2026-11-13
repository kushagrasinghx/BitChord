package com.music.bitchord.sponsorblock

/** SponsorBlock segment categories that are skipped, leaving only the music. */
enum class SponsorBlockCategory(val key: String) {
    SPONSOR("sponsor"),
    SELF_PROMO("selfpromo"),
    INTERACTION("interaction"),
    INTRO("intro"),
    OUTRO("outro"),
    MUSIC_OFFTOPIC("music_offtopic"),
    PREVIEW("preview"),
    FILLER("filler");

    companion object {
        fun fromKey(key: String): SponsorBlockCategory? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}
