/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models.response

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.NavigationEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistPanelRenderer
import moe.rukamori.archivetune.innertube.models.Runs
import moe.rukamori.archivetune.innertube.models.Tabs
import moe.rukamori.archivetune.innertube.models.YouTubeDataPage

@Serializable
data class NextResponse(
    val contents: Contents,
    val continuationContents: ContinuationContents?,
    val currentVideoEndpoint: NavigationEndpoint?,
    val engagementPanels: List<EngagementPanel>? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer?,
        val twoColumnWatchNextResults: YouTubeDataPage.Contents.TwoColumnWatchNextResults?,
    ) {
        @Serializable
        data class SingleColumnMusicWatchNextResultsRenderer(
            val tabbedRenderer: TabbedRenderer?,
        ) {
            @Serializable
            data class TabbedRenderer(
                val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer?,
            ) {
                @Serializable
                data class WatchNextTabbedResultsRenderer(
                    val tabs: List<Tabs.Tab>,
                )
            }
        }
    }

    @Serializable
    data class ContinuationContents(
        val playlistPanelContinuation: PlaylistPanelRenderer,
    )

    /**
     * Watch-page engagement panels. The "structured description" panel is
     * where the music attribution cards live (videoAttributeViewModel): each
     * card's overflow menu carries the full "Song credits" dialog with the
     * Song / Artist / Album / Writers / Licensed to YouTube by / ...
     * label-value blocks that YouTube itself shows in its track information.
     */
    @Serializable
    data class EngagementPanel(
        val engagementPanelSectionListRenderer: EngagementPanelSectionListRenderer? = null,
    ) {
        @Serializable
        data class EngagementPanelSectionListRenderer(
            val panelIdentifier: String? = null,
            val content: Content? = null,
        ) {
            @Serializable
            data class Content(
                val structuredDescriptionContentRenderer: StructuredDescriptionContentRenderer? = null,
            )
        }
    }

    @Serializable
    data class StructuredDescriptionContentRenderer(
        val items: List<StructuredDescriptionItem>? = null,
    ) {
        @Serializable
        data class StructuredDescriptionItem(
            val horizontalCardListRenderer: HorizontalCardListRenderer? = null,
        )
    }

    @Serializable
    data class HorizontalCardListRenderer(
        val cards: List<HorizontalCard>? = null,
    ) {
        @Serializable
        data class HorizontalCard(
            val videoAttributeViewModel: VideoAttributeViewModel? = null,
        )
    }

    @Serializable
    data class VideoAttributeViewModel(
        val title: String? = null,
        val subtitle: String? = null,
        val secondarySubtitle: SecondarySubtitle? = null,
        val overflowMenuOnTap: OverflowMenuOnTap? = null,
    ) {
        @Serializable
        data class SecondarySubtitle(
            val content: String? = null,
        )

        @Serializable
        data class OverflowMenuOnTap(
            val innertubeCommand: InnertubeCommand? = null,
        ) {
            @Serializable
            data class InnertubeCommand(
                val confirmDialogEndpoint: ConfirmDialogEndpoint? = null,
            ) {
                @Serializable
                data class ConfirmDialogEndpoint(
                    val content: Content? = null,
                ) {
                    @Serializable
                    data class Content(
                        val confirmDialogRenderer: ConfirmDialogRenderer? = null,
                    )
                }
            }
        }

        @Serializable
        data class ConfirmDialogRenderer(
            val dialogMessages: List<Runs>? = null,
        )
    }
}
