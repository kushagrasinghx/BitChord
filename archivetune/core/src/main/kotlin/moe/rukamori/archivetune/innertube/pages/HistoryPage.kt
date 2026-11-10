/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.pages

import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems

data class HistoryPage(
    val sections: List<HistorySection>?,
) {
    data class HistorySection(
        val title: String,
        val songs: List<SongItem>,
        /**
         * Continuation token for fetching more songs in this section.
         *
         * YouTube Music paginates each history section (Today, Yesterday,
         * This Week, etc.) separately via `musicShelfContinuation`. The
         * first browse response includes a continuation token at the end
         * of each section's contents; subsequent pages are fetched by
         * calling `innerTube.browse(continuation = token)`.
         *
         * Per user request (2026-08-28): "The local song history got its
         * 200 limit removed but the remote history is still capped to
         * 200. Fix it." Previously this token was discarded — the initial
         * browse response contained up to ~200 songs per section (the
         * upstream page size), and the app never followed the
         * continuation. The token is now surfaced so the YouTube.kt
         * `musicHistory()` sweep can follow every continuation until each
         * section is exhausted (see commit on `YouTube.musicHistory`).
         */
        val continuation: String? = null,
    )

    companion object {
        fun fromSectionListContent(content: SectionListRenderer.Content): List<HistorySection> {
            val directSongs = mutableListOf<SongItem>()
            val sections =
                buildList {
                    content.musicShelfRenderer?.toHistorySection()?.let(::add)
                    content.itemSectionRenderer?.contents.orEmpty().forEach { itemSectionContent ->
                        itemSectionContent.musicShelfRenderer?.toHistorySection()?.let(::add)
                        itemSectionContent.musicResponsiveListItemRenderer
                            ?.let { fromMusicResponsiveListItemRenderer(it) }
                            ?.let(directSongs::add)
                    }
                }

            return if (directSongs.isEmpty()) {
                sections
            } else {
                sections +
                    HistorySection(
                        title =
                            content.musicShelfRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                .orEmpty(),
                        songs = directSongs,
                    )
            }
        }

        fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection =
            renderer.toHistorySection()
                ?: HistorySection(
                    title =
                        renderer.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            .orEmpty(),
                    songs = emptyList(),
                )

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? =
            renderer.toSongItem(albumColumnIndex = 3)
    }
}

private fun MusicShelfRenderer.toHistorySection(): HistoryPage.HistorySection? {
    val songs =
        contents.orEmpty().getItems().mapNotNull {
            it.toSongItem(albumColumnIndex = 3)
        }
    if (songs.isEmpty()) return null
    // Surface the continuation token so YouTube.kt's musicHistory()
    // sweep can follow it for the full per-section history (the
    // upstream page size caps each browse response at ~200 songs).
    val continuation =
        continuations?.getContinuation() ?: contents?.getContinuation()
    return HistoryPage.HistorySection(
        title =
            title
                ?.runs
                ?.firstOrNull()
                ?.text
                .orEmpty(),
        songs = songs,
        continuation = continuation,
    )
}
