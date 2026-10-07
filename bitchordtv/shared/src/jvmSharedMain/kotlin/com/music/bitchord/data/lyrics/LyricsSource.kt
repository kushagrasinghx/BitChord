package com.music.bitchord.data.lyrics

/**
 * The databases [LyricsRepository] can ask, in the order it asks them.
 *
 * Exposed in Settings because the trade-offs are real and personal: one of
 * these is geoblocked in some countries, another runs on volunteer mirrors
 * that come and go, and all of them are third-party services being reached on
 * the user's connection. Anyone who would rather not talk to a given one
 * should be able to say so.
 */
enum class LyricsSource(
    val label: String,
    val detail: String,
    /** Whether it can return per-word timings, or only whole lines. */
    val wordSynced: Boolean,
    /**
     * Kept in code but off the list: not shown in Settings or the provider
     * picker, and never asked — even by someone whose saved choices still name
     * it. For a host that has stopped answering, where deleting the provider
     * outright would only mean writing it again if it comes back.
     */
    val hidden: Boolean = false,
) {
    // Declaration order is the default priority — [AppSettings.lyricsSourceOrder]
    // and [AppSettings.lyricsSources] both fall back to [LyricsSource.entries]
    // verbatim, so this list *is* the out-of-the-box experience. Stored by
    // name rather than position, so this can be rearranged without disturbing
    // an order somebody has already chosen for themselves.
    //
    // The three Apple hosts lead, because they carry the same catalogue and
    // that catalogue is the one with the voices in it. [BINI_LYRICS] goes
    // first of the three: it is the only one that will answer to a recording
    // rather than to a name, and it is where the ISRC the others use comes
    // from. [LYRICS_PLUS] has the finest timing of the lot and the least
    // reliable hosting — behind them rather than in front, so a track does not
    // wait on a mirror that is down to be told what three other hosts already
    // had.
    //
    // [LRC_RED] is ahead of all of them: it is where [BINI_LYRICS] gets its
    // documents now, so asking it directly is the same Apple timing without
    // the middleman, and it is the one a dead mirror can't take down with it.
    LRC_RED(
        label = "lrc.red",
        detail = "Apple timings by recording, syllable by syllable",
        wordSynced = true,
    ),
    BINI_LYRICS(
        label = "BiniLyrics",
        detail = "The same Apple timings, matched on the recording itself",
        wordSynced = true,
    ),
    BETTER_LYRICS(
        label = "BetterLyrics",
        detail = "Apple Music timings, word by word",
        wordSynced = true,
    ),
    BETTER_LYRICS_PORTATO(
        label = "BetterLyrics Portato",
        detail = "QQ Music karaoke timings through BetterLyrics",
        wordSynced = true,
    ),
    PAXSENIX(
        label = "PaxSenix",
        detail = "Apple Music timings through the original keyless provider",
        wordSynced = true,
    ),
    PAXSENIX_SPOTIFY(
        label = "PaxSenix: Spotify",
        detail = "Spotify lyrics with PaxSenix fallback; API key required",
        wordSynced = false,
    ),
    PAXSENIX_MUSIXMATCH(
        label = "PaxSenix: Musixmatch",
        detail = "Musixmatch timings with PaxSenix fallback; API key required",
        wordSynced = true,
    ),
    LYRICS_PLUS(
        label = "LyricsPlus",
        detail = "Syllable by syllable, on community mirrors",
        wordSynced = true,
    ),
    SIMP_MUSIC(
        label = "SimpMusic",
        detail = "Matched on the video, so never the wrong edit",
        wordSynced = true,
        hidden = true,
    ),
    UNISON(
        label = "Unison",
        detail = "Contributed by listeners, so it has what nobody licensed",
        wordSynced = true,
    ),
    YOUTUBE_TRANSCRIPT(
        label = "YouTube captions",
        detail = "Timed captions matched to the exact playing video",
        wordSynced = false,
    ),
    YOUTUBE_MUSIC(
        label = "YouTube Music",
        detail = "Plain lyrics from the playing video's Lyrics tab",
        wordSynced = false,
    ),
    MEGALOBIZ(
        label = "Megalobiz",
        detail = "Community-made, whole-line LRC",
        wordSynced = false,
        hidden = true,
    ),
    KUGOU(
        label = "KuGou",
        detail = "Whole lines, strong outside the English catalogue",
        wordSynced = false,
    ),
    LRCLIB(
        label = "LRCLIB",
        detail = "Whole lines only, and always up",
        wordSynced = false,
    ),
    MUSIXMATCH(
        label = "Musixmatch",
        detail = "Whole lines, from the biggest lyrics database there is",
        wordSynced = false,
    ),
    GENIUS(
        label = "Genius",
        detail = "Plain text fallback, massive web catalogue",
        wordSynced = false,
    ),
    ;

    companion object {
        /** Every source a listener can see and choose, in default priority. */
        val offered: List<LyricsSource> = entries.filterNot { it.hidden }

        /**
         * A saved order brought up to date with this build: hidden sources
         * dropped, and any source added since it was saved slotted in right
         * after the one it is declared behind — at the very top if nothing is
         * declared ahead of it. So a new source lands where a fresh install
         * puts it relative to its neighbours, rather than under every
         * line-only fallback where it would never get to answer.
         */
        fun ordered(saved: List<LyricsSource>): List<LyricsSource> {
            val result = saved.filter { it in offered }.distinct().toMutableList()
            offered.forEachIndexed { index, source ->
                if (source in result) return@forEachIndexed
                val before = offered.subList(0, index).lastOrNull { it in result }
                result.add(before?.let { result.indexOf(it) + 1 } ?: 0, source)
            }
            return result
        }
    }
}
