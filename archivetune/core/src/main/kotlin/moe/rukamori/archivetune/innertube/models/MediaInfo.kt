/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models

data class MediaInfo(
    val videoId: String,
    val title: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val authorThumbnail: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val subscribers: String? = null,
    val viewCount: Int? = null,
    val like: Int? = null,
    val dislike: Int? = null,
    /**
     * YouTube's own song credits (the "Song credits" dialog behind the
     * watch page's music attribution card): Song / Artist / Album / Writers /
     * Licensed to YouTube by / Produced by / ... as label-value rows.
     */
    val credits: List<CreditsRow>? = null,
)

data class CreditsRow(
    val label: String,
    val value: String,
)
