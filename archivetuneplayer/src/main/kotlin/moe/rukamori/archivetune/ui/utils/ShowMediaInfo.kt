/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.LiveAudioChainPill
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.extractTtmlWriters
import moe.rukamori.archivetune.utils.AudioOutputStats
import moe.rukamori.archivetune.utils.AudioOutputStatsProvider
import android.text.format.Formatter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.snapshotFlow
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.audiosource.CurrentStreamInfo
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.isLossless
import moe.rukamori.archivetune.playback.dsp.AudioEngineRouterProcessor
import moe.rukamori.archivetune.playback.dsp.BitPerfectRuntime
import moe.rukamori.archivetune.playback.dsp.EngineRuntime
import androidx.media3.common.C
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import tf.monochrome.android.audio.dsp.ChannelDetectorProcessor
import tf.monochrome.android.audio.pipeline.AudioPipelineMonitor
import tf.monochrome.android.audio.pipeline.OutputDeviceProbe
import com.lastwave.app.playback.UsbDacMonitor

private enum class MediaInfoTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    Information(R.string.information, R.drawable.solar_info),
    Details(R.string.details, R.drawable.solar_ruler),
    Numbers(R.string.numbers, R.drawable.solar_hertz),
}

private fun creditIconFor(label: String): Int =
    when {
        label.equals("Song", ignoreCase = true) -> R.drawable.solar_music_note
        label.contains("artist", true) || label.contains("perform", true) -> R.drawable.solar_users
        label.contains("album", true) -> R.drawable.solar_album_linear
        label.contains("writ", true) || label.contains("compos", true) ||
            label.contains("lyricis", true) -> R.drawable.edit
        label.contains("licen", true) -> R.drawable.solar_bookmark_linear
        else -> R.drawable.solar_text
    }

private fun extractTrailingWrittenBy(lyrics: String?): String {
    if (lyrics.isNullOrBlank()) return ""
    val pattern =
        Regex(
            pattern =
                """^\s*(?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\]\s*)?\[?\s*""" +
                    """(?:(?:written|song)\s+by|writers?|songwriters?|lyricists?|composers?)""" +
                    """\s*\]?\s*:?\s*(.+?)\s*$""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    return lyrics
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
        .takeLast(8)
        .mapNotNull { line ->
            pattern.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
        }
        .distinct()
        .joinToString(", ")
}

private data class MediaInfoQuickFact(
    val iconRes: Int,
    val text: String,
)

private data class MediaInfoDetail(
    val iconRes: Int,
    val label: String,
    val value: String,
    val multiline: Boolean = false,
)

private data class MediaInfoMetric(
    val iconRes: Int,
    val labelRes: Int,
    val value: String,
)

private val ExpressiveSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current
    val song by database.song(videoId).collectAsStateWithLifecycle(initialValue = null)
    val currentFormat by database.format(videoId).collectAsStateWithLifecycle(initialValue = null)
    val info = rememberMediaInfo(videoId)
    val lyricsEntity by database.lyrics(videoId).collectAsStateWithLifecycle(initialValue = null)
    var selectedTab by rememberSaveable(videoId) { mutableStateOf(MediaInfoTab.Information) }
    var outputStats by remember(videoId) { mutableStateOf<AudioOutputStats?>(null) }

    val trackInfoViewModel: TrackInfoViewModel = hiltViewModel()
    val facts by trackInfoViewModel.facts.collectAsStateWithLifecycle()
    val service = playerConnection?.service
    val liveMediaMetadata by (service?.currentMediaMetadata ?: remember { MutableStateFlow<MediaMetadata?>(null) })
        .collectAsStateWithLifecycle()
    val isLiveTrack = liveMediaMetadata?.id == videoId
    val streamInfo by (service?.currentStreamInfo ?: remember { MutableStateFlow<CurrentStreamInfo?>(null) })
        .collectAsStateWithLifecycle()
    val normalizeFactor by (service?.liveNormalizeFactor ?: remember { MutableStateFlow(1f) })
        .collectAsStateWithLifecycle()
    val sourcesRevision by (service?.resolvedSourcesRevision ?: remember { MutableStateFlow(0L) })
        .collectAsStateWithLifecycle()
    val availableSources: List<AudioSourceType> =
        remember(videoId, sourcesRevision, isLiveTrack) {
            if (isLiveTrack) {
                runCatching { service?.availableSourcesForSong(videoId) ?: emptyList() }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

    val unknownText = stringResource(R.string.unknown)
    val pleaseWaitText = stringResource(R.string.please_wait)
    val copyText = stringResource(R.string.copy)
    val shareText = stringResource(R.string.share)
    val closeText = stringResource(R.string.close)
    val songTitleLabel = stringResource(R.string.song_title)
    val songArtistsLabel = stringResource(R.string.song_artists)
    val mediaIdLabel = stringResource(R.string.media_id)
    val mimeTypeLabel = stringResource(R.string.mime_type)
    val codecsLabel = stringResource(R.string.codecs)
    val bitrateLabel = stringResource(R.string.bitrate)
    val sampleRateLabel = stringResource(R.string.sample_rate)
    val loudnessLabel = stringResource(R.string.loudness)
    val volumeLabel = stringResource(R.string.volume)
    val fileSizeLabel = stringResource(R.string.file_size)
    val descriptionLabel = stringResource(R.string.description)
    val durationLabel = stringResource(R.string.ti_duration)
    val bitDepthLabel = stringResource(R.string.ti_bit_depth)
    val channelsLabelRes = stringResource(R.string.ti_channels)
    val qualityLabel = stringResource(R.string.ti_quality)
    val workedProviderLabel = stringResource(R.string.ti_worked_provider)
    val engineLabel = stringResource(R.string.ti_engine)
    val normalizationLabel = stringResource(R.string.ti_normalization)
    val streamDeliveryLabel = stringResource(R.string.ti_stream_delivery)
    val protocolLabel = stringResource(R.string.ti_protocol)
    val playbackSectionTitle = stringResource(R.string.ti_section_playback)
    val providersSectionTitle = stringResource(R.string.ti_section_providers)
    val dacSectionTitle = stringResource(R.string.ti_section_dac)
    val dacDeviceLabel = stringResource(R.string.ti_dac_device)
    val signalPathLabel = stringResource(R.string.ti_signal_path)
    val hardwareClockLabel = stringResource(R.string.ti_hardware_clock)
    val usbIdLabel = stringResource(R.string.ti_usb_id)
    val pipelineStateLabel = stringResource(R.string.ti_pipeline_state)
    val decoderLabel = stringResource(R.string.ti_decoder)
    val workedLabel = stringResource(R.string.ti_worked)
    val standbyLabel = stringResource(R.string.ti_standby)

    val mediaUrl = remember(videoId) { "https://music.youtube.com/watch?v=$videoId" }

    LaunchedEffect(videoId) {
        outputStats = AudioOutputStatsProvider.resolve(context)
    }

    val isLocal = videoId.isLocalMediaId() || videoId.isTelegramMediaId()
    val liveStreamInfo = streamInfo?.takeIf { it.mediaId == videoId }
    val codec = currentFormat.codecName()
    val bitrateKbps = currentFormat?.bitrate?.takeIf { it > 0 }?.let { it / 1000 }
    val liveSampleRateHz =
        liveStreamInfo?.sampleRate?.takeIf { it > 0 }
            ?: currentFormat?.sampleRate?.takeIf { it > 0 }
    val liveBitDepth =
        liveStreamInfo?.bitDepth?.takeIf { it > 0 }
            ?: facts?.decodedBits?.takeIf { it > 0 }
    val liveChannels = facts?.chainChannels ?: facts?.decodedChannels

    val heroTitle = song?.title ?: info?.title ?: videoId
    val heroSubtitle =
        song
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: info?.author
            ?: unknownText
    val artworkModel = song?.thumbnailUrl ?: info?.authorThumbnail
    val playbackVolume = playerConnection?.let { "${(it.player.volume * 100).toInt()}%" }

    val overviewDetails =
        buildList {
            add(
                MediaInfoDetail(
                    iconRes = R.drawable.solar_music_note,
                    label = songTitleLabel,
                    value = song?.title ?: info?.title ?: unknownText,
                ),
            )
            add(
                MediaInfoDetail(
                    iconRes = R.drawable.solar_users,
                    label = songArtistsLabel,
                    value =
                        song
                            ?.artists
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString { it.name }
                            ?: info?.author
                            ?: unknownText,
                ),
            )

            song?.song?.duration?.takeIf { it > 0 }?.let { seconds ->
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.timer,
                        label = durationLabel,
                        value = formatDuration(seconds * 1000L, 0L),
                    ),
                )
            }
            add(
                MediaInfoDetail(
                    iconRes = R.drawable.solar_hash,
                    label = mediaIdLabel,
                    value = videoId,
                ),
            )
        }

    val overviewTitleValue = song?.title ?: info?.title
    val overviewArtistsValue =
        song?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name } ?: info?.author
    val youtubeCredits =
        info?.credits.orEmpty().mapNotNull { row ->
            val value = row.value.trim()
            if (value.isBlank()) return@mapNotNull null
            val duplicatesOverview =
                (row.label.equals("Song", ignoreCase = true) && value == overviewTitleValue) ||
                    (row.label.equals("Artist", ignoreCase = true) && value == overviewArtistsValue)
            if (duplicatesOverview) return@mapNotNull null
            MediaInfoDetail(
                iconRes = creditIconFor(row.label),
                label = row.label,
                value = value,
                multiline = true,
            )
        }
    val hasWriterRowFromYouTube =
        youtubeCredits.any { detail ->
            detail.label.contains(Regex("writ|compos|lyricis", RegexOption.IGNORE_CASE))
        }
    val writtenByLabel = stringResource(R.string.credits_written_by)
    val lyricsWriters =
        if (hasWriterRowFromYouTube) {
            ""
        } else {
            extractTtmlWriters(lyricsEntity?.lyrics)
                .ifBlank { extractTrailingWrittenBy(lyricsEntity?.lyrics) }
        }
    val creditDetails =
        buildList {
            addAll(youtubeCredits)
            if (lyricsWriters.isNotBlank()) {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.edit,
                        label = writtenByLabel,
                        value = lyricsWriters,
                        multiline = true,
                    ),
                )
            }
        }

    val technicalDetails =
        buildList {
            currentFormat?.itag?.takeIf { it > 0 }?.toString()?.let {
                add(MediaInfoDetail(iconRes = R.drawable.solar_ruler, label = "Itag", value = it))
            }
            currentFormat
                ?.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    add(MediaInfoDetail(iconRes = R.drawable.solar_database, label = mimeTypeLabel, value = it))
                }
            currentFormat
                ?.codecs
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    add(MediaInfoDetail(iconRes = R.drawable.solar_code, label = codecsLabel, value = it))
                }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let {
                    add(MediaInfoDetail(iconRes = R.drawable.solar_speed, label = bitrateLabel, value = "${it / 1000} Kbps"))
                }
            currentFormat
                ?.sampleRate
                ?.takeIf { it > 0 }
                ?.let {
                    add(MediaInfoDetail(iconRes = R.drawable.solar_hertz, label = sampleRateLabel, value = "$it Hz"))
                }
            currentFormat?.loudnessDb?.let {
                add(MediaInfoDetail(iconRes = R.drawable.solar_volume, label = loudnessLabel, value = "$it dB"))
            }
            playbackVolume?.let {
                add(MediaInfoDetail(iconRes = R.drawable.solar_headphones, label = volumeLabel, value = it))
            }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoDetail(
                            iconRes = R.drawable.solar_database,
                            label = fileSizeLabel,
                            value = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }

            liveBitDepth?.let {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.solar_ruler,
                        label = bitDepthLabel,
                        value = "$it-bit",
                    ),
                )
            }
            liveChannels?.let {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.solar_headphones,
                        label = channelsLabelRes,
                        value = channelsLabel(it, unknownText),
                    ),
                )
            }
            qualityTierLabel(codec, liveBitDepth, liveSampleRateHz, bitrateKbps, null)?.let {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.solar_wave,
                        label = qualityLabel,
                        value = it,
                    ),
                )
            }
        }

    val quickFacts =
        buildList {
            currentFormat
                ?.mimeType
                ?.substringBefore(';')
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.solar_wave, text = it)) }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.solar_speed, text = "${it / 1000} Kbps")) }
            currentFormat
                ?.sampleRate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.solar_hertz, text = "${it / 1000.0} kHz")) }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoQuickFact(
                            iconRes = R.drawable.solar_database,
                            text = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }
            info
                ?.subscribers
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.solar_users, text = it)) }
        }

    val metrics =
        if (info != null) {
            listOf(
                MediaInfoMetric(R.drawable.solar_users, R.string.subscribers, info?.subscribers ?: unknownText),
                MediaInfoMetric(R.drawable.solar_eye, R.string.views, info?.viewCount?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.drawable.solar_heart, R.string.likes, info?.like?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.drawable.solar_dislike, R.string.dislikes, info?.dislike?.let(::numberFormatter) ?: unknownText),
            )
        } else {
            emptyList()
        }

    var entered by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        entered = false
        kotlinx.coroutines.delay(80)
        entered = true
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(contentType = "Hero") {
            MediaInfoExpressiveEntrance(entered = entered, index = 0) {
                MediaInfoExpressiveHero(
                    title = heroTitle,
                    subtitle = heroSubtitle,
                    artworkModel = artworkModel,
                    isLoading = info == null,
                    loadingText = pleaseWaitText,
                    closeText = closeText,
                    onCopy = { copyToClipboard(context, videoId) },
                    onShare = { shareMediaLink(context, mediaUrl) },
                    copyText = copyText,
                    shareText = shareText,
                    onClose = bottomSheetPageState::dismiss,
                )
            }
        }

        if (quickFacts.isNotEmpty()) {
            item(contentType = "QuickFacts") {
                MediaInfoExpressiveEntrance(entered = entered, index = 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        quickFacts.forEach { fact ->
                            MediaInfoQuickPill(
                                iconRes = fact.iconRes,
                                text = fact.text,
                                onClick = { copyToClipboard(context, fact.text) },
                            )
                        }
                    }
                }
            }
        }

        item(contentType = "Tabs") {
            MediaInfoExpressiveEntrance(entered = entered, index = 2) {
                MediaInfoExpressiveTabs(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        }

        item(contentType = "SelectedContent") {
            MediaInfoExpressiveEntrance(entered = entered, index = 3) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(220),
                            initialOffsetY = { it / 6 },
                        ) + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(160),
                                targetOffsetY = { -it / 8 },
                            ) + fadeOut(tween(160)))
                    },
                    label = "mediaInfoTab",
                ) { tab ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when (tab) {
                            MediaInfoTab.Information -> {
                                MediaInfoExpressiveCard {
                                    overviewDetails.forEachIndexed { index, item ->
                                        MediaInfoExpressiveRow(
                                            iconRes = item.iconRes,
                                            label = item.label,
                                            value = item.value,
                                            showDivider = index != overviewDetails.lastIndex,
                                            onClick = { copyToClipboard(context, item.value) },
                                        )
                                    }
                                }

                                if (creditDetails.isNotEmpty()) {
                                    MediaInfoExpressiveCard {
                                        creditDetails.forEachIndexed { index, item ->
                                            MediaInfoExpressiveRow(
                                                iconRes = item.iconRes,
                                                label = item.label,
                                                value = item.value,
                                                showDivider = index != creditDetails.lastIndex,
                                                onClick = { copyToClipboard(context, item.value) },
                                            )
                                        }
                                    }
                                }

                                if (info == null) {
                                    MediaInfoExpressivePending(
                                        iconRes = R.drawable.solar_text,
                                        title = descriptionLabel,
                                        message = pleaseWaitText,
                                    )
                                } else {
                                    MediaInfoNarrativeCard(
                                        iconRes = R.drawable.solar_text,
                                        title = descriptionLabel,
                                        body = info?.description?.takeIf { it.isNotBlank() } ?: unknownText,
                                        onCopy = {
                                            info
                                                ?.description
                                                ?.takeIf { value -> value.isNotBlank() }
                                                ?.let { copyToClipboard(context, it) }
                                        },
                                    )
                                }
                            }

                            MediaInfoTab.Details -> {
                                if (isLiveTrack) {
                                    LiveAudioChainPill(compact = true)
                                }

                                if (technicalDetails.isEmpty()) {
                                    MediaInfoExpressivePending(
                                        iconRes = R.drawable.solar_ruler,
                                        title = stringResource(R.string.details),
                                        message = pleaseWaitText,
                                    )
                                } else {
                                    MediaInfoExpressiveCard {
                                        technicalDetails.forEachIndexed { index, item ->
                                            MediaInfoExpressiveRow(
                                                iconRes = item.iconRes,
                                                label = item.label,
                                                value = item.value,
                                                showDivider = index != technicalDetails.lastIndex,
                                                onClick = { copyToClipboard(context, item.value) },
                                            )
                                        }
                                    }
                                }

                                if (isLiveTrack) {
                                    MediaInfoSectionCard(
                                        title = playbackSectionTitle,
                                        rows =
                                            buildList {
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.graphic_eq,
                                                        label = engineLabel,
                                                        value = facts?.engineName ?: "None",
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.bolt,
                                                        label = workedProviderLabel,
                                                        value =
                                                            liveStreamInfo?.label
                                                                ?: if (isLocal) "Local Library" else unknownText,
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_server_linear,
                                                        label = streamDeliveryLabel,
                                                        value =
                                                            liveStreamInfo?.label
                                                                ?: if (isLocal) "On-device file" else unknownText,
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_code,
                                                        label = protocolLabel,
                                                        value =
                                                            liveStreamInfo?.protocol
                                                                ?: if (isLocal) "Local File" else unknownText,
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_volume,
                                                        label = normalizationLabel,
                                                        value =
                                                            if (normalizeFactor != 1f) {
                                                                "×${"%.3f".format(Locale.ROOT, normalizeFactor)}"
                                                            } else {
                                                                "Off (unity)"
                                                            },
                                                    ),
                                                )
                                            },
                                    )

                                    if (!isLocal) {
                                        MediaInfoProvidersCard(
                                            title = providersSectionTitle,
                                            providers =
                                                availableSources.map { source ->
                                                    val worked = liveStreamInfo?.source == source
                                                    Triple(
                                                        providerDisplayName(source),
                                                        providerNote(source),
                                                        worked,
                                                    )
                                                },
                                            workedLabel = workedLabel,
                                            standbyLabel = standbyLabel,
                                        )
                                    }

                                    val factsHere = facts
                                    MediaInfoSectionCard(
                                        title = dacSectionTitle,
                                        rows =
                                            buildList {
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_headphones,
                                                        label = dacDeviceLabel,
                                                        value =
                                                            factsHere?.routedName
                                                                ?: "Android Audio (Built-in Output)",
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_aspect_ratio_linear,
                                                        label = signalPathLabel,
                                                        value =
                                                            when {
                                                                factsHere?.usbExclusive == true -> "USB Exclusive (Direct DAC)"
                                                                EngineRuntime.tryptifyUsbPinActive -> "USB Framework (Pinned Route)"
                                                                else -> "Default Android System Mixer"
                                                            },
                                                    ),
                                                )
                                                val hwClock =
                                                    factsHere?.tryptifyUsbRateHz?.takeIf { it > 0 }
                                                        ?: factsHere?.halSampleRateHz
                                                        ?: liveSampleRateHz
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.timer,
                                                        label = hardwareClockLabel,
                                                        value = hwClock?.let { formatHz(it) } ?: unknownText,
                                                    ),
                                                )
                                                if (factsHere != null &&
                                                    factsHere.usbVendorId >= 0 &&
                                                    factsHere.usbProductId >= 0
                                                ) {
                                                    add(
                                                        MediaInfoDetail(
                                                            iconRes = R.drawable.solar_hash,
                                                            label = usbIdLabel,
                                                            value = "0x%04X:0x%04X".format(
                                                                Locale.ROOT,
                                                                factsHere.usbVendorId,
                                                                factsHere.usbProductId,
                                                            ),
                                                        ),
                                                    )
                                                }
                                                val pipelineStateValue = run {
                                                    val bp = BitPerfectRuntime.status
                                                    val inForm = when {
                                                        bp.sourceSampleRate > 0 && bp.sourceIsLossy -> "Lossy Source"
                                                        bp.sourceEncoding == C.ENCODING_PCM_FLOAT -> "Float PCM"
                                                        bp.sourceBitDepth > 0 -> "${bp.sourceBitDepth}-bit Integer PCM"
                                                        factsHere?.decodedFloat == true -> "Float PCM"
                                                        else -> "${factsHere?.decodedBits ?: 16}-bit Integer PCM"
                                                    }
                                                    val outForm = when {
                                                        EngineRuntime.usbExclusiveActive -> {
                                                            val wireBits =
                                                                EngineRuntime.lastwaveUsbBitsPerSample.takeIf { it > 0 }
                                                                    ?: EngineRuntime.tryptifyUsbStream?.bitsPerSample?.takeIf { it > 0 }
                                                                    ?: bp.outputBitDepth
                                                            if (bp.outputEncoding == C.ENCODING_PCM_FLOAT) {
                                                                "Float · ${wireBits}-bit USB out"
                                                            } else {
                                                                "$wireBits-bit USB out"
                                                            }
                                                        }
                                                        bp.mixerBitPerfectActive -> {
                                                            "${bp.outputBitDepth}-bit bit-perfect mixer"
                                                        }
                                                        bp.outputEncoding == C.ENCODING_PCM_FLOAT -> {
                                                            "Float · ${bp.outputBitDepth}-bit out"
                                                        }
                                                        else -> "${bp.outputBitDepth}-bit out"
                                                    }
                                                    "$inForm → $outForm"
                                                }
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_wave,
                                                        label = pipelineStateLabel,
                                                        value = pipelineStateValue,
                                                    ),
                                                )
                                                add(
                                                    MediaInfoDetail(
                                                        iconRes = R.drawable.solar_code,
                                                        label = decoderLabel,
                                                        value = factsHere?.decoderName ?: "—",
                                                    ),
                                                )
                                            },
                                    )
                                }

                                MediaInfoOutputCard(
                                    outputStats = outputStats,
                                    sourceSampleRate = currentFormat?.sampleRate,
                                )

                                AcousticAuditSection(
                                    videoId = videoId,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }

                            MediaInfoTab.Numbers -> {
                                if (metrics.isEmpty()) {
                                    MediaInfoExpressivePending(
                                        iconRes = R.drawable.solar_hertz,
                                        title = stringResource(R.string.numbers),
                                        message = pleaseWaitText,
                                    )
                                } else {
                                    metrics.chunked(2).forEach { rowMetrics ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            rowMetrics.forEach { metric ->
                                                MediaInfoExpressiveMetric(
                                                    iconRes = metric.iconRes,
                                                    labelRes = metric.labelRes,
                                                    value = metric.value,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (rowMetrics.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoExpressiveEntrance(
    entered: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.01f,
        ),
        label = "entrance_$index",
    )
    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    val p = progress.coerceIn(0f, 1f)
                    alpha = p
                    scaleX = 0.92f + 0.08f * p
                    scaleY = 0.92f + 0.08f * p
                    translationY = (1f - p) * 18f
                },
    ) {
        content()
    }
}

@Composable
private fun MediaInfoExpressiveHero(
    title: String,
    subtitle: String,
    artworkModel: String?,
    isLoading: Boolean,
    loadingText: String,
    closeText: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    copyText: String,
    shareText: String,
    onClose: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.solar_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.media_info_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = loadingText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.solar_close_circle_linear),
                    contentDescription = closeText,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = onCopy,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = copyText)
            }

            OutlinedButton(
                onClick = onShare,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_share_linear),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = shareText)
            }
        }
    }
}

@Composable
private fun MediaInfoQuickPill(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaInfoExpressiveTabs(
    selectedTab: MediaInfoTab,
    onSelect: (MediaInfoTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
        ) {
            MediaInfoTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val selectionScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = ExpressiveSpring,
                    label = "tabScale_${tab.name}",
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    modifier =
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = selectionScale
                                scaleY = selectionScale
                            },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clickable { onSelect(tab) }
                                .padding(vertical = 10.dp),
                    ) {
                        AnimatedVisibility(visible = selected) {
                            Row {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoExpressiveCard(
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun MediaInfoExpressiveRow(
    iconRes: Int,
    label: String,
    value: String,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

        }
        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 64.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .alpha(0.5f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun MediaInfoNarrativeCard(
    iconRes: Int,
    title: String,
    body: String,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.solar_copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaInfoOutputCard(
    outputStats: AudioOutputStats?,
    sourceSampleRate: Int?,
) {
    val stats = outputStats ?: return
    if (!stats.hasData) return

    val unknownText = stringResource(R.string.unknown)
    val conversionNote =
        stats.conversionDescription(sourceSampleRate)
            ?: stringResource(R.string.output_no_conversion)

    val details =
        buildList {
            add(
                MediaInfoDetail(
                    iconRes = R.drawable.solar_headphones,
                    label = stringResource(R.string.output_device),
                    value = "${stats.deviceLabel} · ${stats.deviceTypeLabel}",
                ),
            )
            if (stats.deviceSampleRates.isNotEmpty()) {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.solar_hertz,
                        label = stringResource(R.string.output_device_rates),
                        value = stats.deviceSampleRates.joinToString(" / ") { "$it Hz" },
                    ),
                )
            }
            if (stats.mixSampleRate > 0) {
                add(
                    MediaInfoDetail(
                        iconRes = R.drawable.solar_wave,
                        label = stringResource(R.string.output_mix_rate),
                        value = "${stats.mixSampleRate} Hz",
                    ),
                )
            }
            add(
                MediaInfoDetail(
                    iconRes = R.drawable.solar_speed,
                    label = stringResource(R.string.output_conversion),
                    value = conversionNote,
                    multiline = true,
                ),
            )
        }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            details.forEachIndexed { index, item ->
                MediaInfoExpressiveRow(
                    iconRes = item.iconRes,
                    label = item.label,
                    value = item.value,
                    showDivider = index != details.lastIndex,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun MediaInfoExpressiveMetric(
    iconRes: Int,
    labelRes: Int,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaInfoExpressivePending(
    iconRes: Int,
    title: String,
    message: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    value: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", value))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun shareMediaLink(
    context: Context,
    mediaUrl: String,
) {
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mediaUrl)
        }
    context.startActivity(Intent.createChooser(shareIntent, null))
}

@HiltViewModel
class TrackInfoViewModel @Inject constructor(
    monitor: AudioPipelineMonitor,
    channelDetector: ChannelDetectorProcessor,
    outputProbe: OutputDeviceProbe,
    usbDacMonitor: UsbDacMonitor,
) : ViewModel() {
    data class Polled(
        val halSampleRateHz: Int?,
        val usbExclusive: Boolean,
        val usbVendorId: Int,
        val usbProductId: Int,
        val tryptifyUsbRateHz: Int,
        val tryptifyUsbBits: Int,
    )

    private val polled = flow {
        while (true) {
            val runtime = EngineRuntime
            emit(
                Polled(
                    halSampleRateHz = outputProbe.halSampleRateHz(),
                    usbExclusive = runtime.usbExclusiveActive,
                    usbVendorId = usbDacMonitor.state.value.dac?.vendorId ?: -1,
                    usbProductId = usbDacMonitor.state.value.dac?.productId ?: -1,
                    tryptifyUsbRateHz = runtime.tryptifyUsbStream?.sampleRateHz ?: 0,
                    tryptifyUsbBits = runtime.tryptifyUsbStream?.bitsPerSample ?: 0,
                ),
            )
            delay(POLL_INTERVAL_MS)
        }
    }

    private val engineFacts = snapshotFlow {
        val active = EngineRuntime.activeEngineState
        val wanted = EngineRuntime.wantedEngineState
        val engineName =
            when {
                active == AudioEngineRouterProcessor.Engine.TRYPTIFY -> "Tryptify"
                active == AudioEngineRouterProcessor.Engine.LASTWAVE -> "LastWave"
                wanted == AudioEngineRouterProcessor.Engine.TRYPTIFY &&
                    EngineRuntime.tryptifyAvailableState -> "Tryptify"
                wanted == AudioEngineRouterProcessor.Engine.LASTWAVE &&
                    EngineRuntime.lastwaveAvailableState -> "LastWave"
                else -> "None"
            }
        engineName to EngineRuntime.outputFloatState
    }

    private data class RuntimeFacts(
        val halSampleRateHz: Int?,
        val engineName: String,
        val outputFloat: Boolean,
        val usbExclusive: Boolean,
        val usbVendorId: Int,
        val usbProductId: Int,
        val tryptifyUsbRateHz: Int,
        val tryptifyUsbBits: Int,
    )

    private val runtimeFacts: Flow<RuntimeFacts> =
        combine(polled, engineFacts) { poll, (engineName, outputFloat) ->
            RuntimeFacts(
                halSampleRateHz = poll.halSampleRateHz,
                engineName = engineName,
                outputFloat = outputFloat,
                usbExclusive = poll.usbExclusive,
                usbVendorId = poll.usbVendorId,
                usbProductId = poll.usbProductId,
                tryptifyUsbRateHz = poll.tryptifyUsbRateHz,
                tryptifyUsbBits = poll.tryptifyUsbBits,
            )
        }

    data class PipelineFacts(
        val decodedBits: Int?,
        val decodedFloat: Boolean,
        val decodedRateHz: Int?,
        val decodedChannels: Int?,
        val decoderName: String?,
        val chainChannels: Int?,
        val chainLayoutName: String?,
        val routedName: String?,
        val halSampleRateHz: Int?,
        val engineName: String,
        val outputFloat: Boolean,
        val usbExclusive: Boolean,
        val usbVendorId: Int,
        val usbProductId: Int,
        val tryptifyUsbRateHz: Int,
        val tryptifyUsbBits: Int,
    )

    val facts: StateFlow<PipelineFacts?> = combine(
        monitor.stream,
        monitor.decoderName,
        channelDetector.state,
        outputProbe.routed,
        runtimeFacts,
    ) { stream, decoder, chain, routed, runtime ->
        PipelineFacts(
            decodedBits = stream?.pcmBits,
            decodedFloat = stream?.pcmIsFloat == true,
            decodedRateHz = stream?.sampleRate,
            decodedChannels = stream?.channelCount,
            decoderName = decoder,
            chainChannels = chain?.channelCount,
            chainLayoutName = chain?.layoutName,
            routedName = routed?.name,
            halSampleRateHz = runtime.halSampleRateHz,
            engineName = runtime.engineName,
            outputFloat = runtime.outputFloat,
            usbExclusive = runtime.usbExclusive,
            usbVendorId = runtime.usbVendorId,
            usbProductId = runtime.usbProductId,
            tryptifyUsbRateHz = runtime.tryptifyUsbRateHz,
            tryptifyUsbBits = runtime.tryptifyUsbBits,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(POLL_INTERVAL_MS), null)

    private companion object {
        const val POLL_INTERVAL_MS = 1000L
    }
}

internal fun formatHz(hz: Int): String =
    when {
        hz >= 1_000_000 -> "${"%.1f".format(Locale.ROOT, hz / 1_000_000.0)} MHz"
        hz >= 1000 -> {
            val value = hz / 1000.0
            if (value == value.toInt().toDouble()) {
                "${value.toInt()} kHz"
            } else {
                "${"%.1f".format(Locale.ROOT, value)} kHz"
            }
        }
        else -> "$hz Hz"
    }

internal fun channelsLabel(channels: Int?, unknown: String): String =
    when (channels) {
        null -> unknown
        1 -> "Mono (1.0 channel)"
        2 -> "Stereo (2.0 channels • Left / Right)"
        else -> "$channels channels"
    }

internal fun qualityTierLabel(
    codec: String?,
    bitDepth: Int?,
    sampleRateHz: Int?,
    bitrateKbps: Int?,
    unknown: String?,
): String? =
    when {
        codec.equals("flac", true) || codec.equals("alac", true) ->
            when {
                (bitDepth ?: 16) > 16 || (sampleRateHz ?: 44100) > 48000 -> "Hi-Res Lossless Audio"
                else -> "CD Quality Audio ($bitDepth-bit / ${sampleRateHz?.let { formatHz(it) } ?: "44.1 kHz"} Lossless)"
            }
        (bitrateKbps ?: 0) >= 320 -> "High Quality Audio (320 kbps+)"
        (bitrateKbps ?: 0) > 0 -> "Standard Quality Audio"
        else -> unknown
    }

internal fun providerDisplayName(source: AudioSourceType): String =
    when (source) {
        AudioSourceType.TIDAL -> "Tidal (HiFi FLAC)"
        AudioSourceType.QOBUZ -> "Qobuz (Studio FLAC)"
        AudioSourceType.QOBUZ_BACKUP -> "Qobuz Backup (Mirror)"
        AudioSourceType.DEEZER -> "Deezer (Lossless FLAC)"
        AudioSourceType.APPLE -> "Apple Music (Catalogue)"
        AudioSourceType.JIOSAAVN -> "JioSaavn (AAC)"
        AudioSourceType.SOUNDCLOUD -> "SoundCloud"
        AudioSourceType.SPOTIFY -> "Spotify Direct (AAC)"
        AudioSourceType.YOUTUBE -> "YouTube Music (Standard)"
    }

internal fun providerNote(source: AudioSourceType): String =
    when (source) {
        AudioSourceType.TIDAL -> "Direct API • SourcePool"
        AudioSourceType.QOBUZ -> "SourcePool • Akamai CDN"
        AudioSourceType.QOBUZ_BACKUP -> "Secondary mirror • endpoint chain"
        AudioSourceType.DEEZER -> "Lossless resolver"
        AudioSourceType.APPLE -> "Metadata & lyrics tier"
        AudioSourceType.JIOSAAVN -> "AAC resolver"
        AudioSourceType.SOUNDCLOUD -> "Public API resolver"
        AudioSourceType.SPOTIFY -> "Web-player pipeline (experimental)"
        AudioSourceType.YOUTUBE -> "Ultimate zero-skip fallback"
    }

internal fun formatDuration(durationMs: Long, playbackMs: Long): String {
    val total = if (durationMs > 0) durationMs else playbackMs
    if (total <= 0) return "0 min 00s"
    val minutes = total / 60000L
    val seconds = (total % 60000L) / 1000L
    return "$minutes min ${"%02d".format(Locale.ROOT, seconds)}s"
}

private fun FormatEntity?.codecName(): String? =
    this?.codecLabel()?.takeIf { it.isNotBlank() && it != "—" }
        ?: this?.codecs?.takeIf { it.isNotBlank() }?.substringBefore('.')
        ?: this?.mimeType?.substringAfter("audio/", "")?.substringBefore(';')?.takeIf { it.isNotBlank() && it != "mpeg" }

@Composable
private fun MediaInfoSectionCard(
    title: String,
    rows: List<MediaInfoDetail>,
) {
    if (rows.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            ) {
                Text(
                    text = title.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            rows.forEachIndexed { index, item ->
                MediaInfoExpressiveRow(
                    iconRes = item.iconRes,
                    label = item.label,
                    value = item.value,
                    showDivider = index != rows.lastIndex,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun MediaInfoProviderRow(
    name: String,
    note: String,
    status: String,
    worked: Boolean,
    showDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color =
                    if (worked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(if (worked) R.drawable.solar_check_circle_linear else R.drawable.solar_more_circle_linear),
                        contentDescription = null,
                        tint =
                            if (worked) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (worked) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color =
                    if (worked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (worked) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 64.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .alpha(0.5f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun MediaInfoProvidersCard(
    title: String,
    providers: List<Triple<String, String, Boolean>>,
    workedLabel: String,
    standbyLabel: String,
) {
    if (providers.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )
            providers.forEachIndexed { index, (name, note, worked) ->
                MediaInfoProviderRow(
                    name = name,
                    note = note,
                    status = if (worked) workedLabel else standbyLabel,
                    worked = worked,
                    showDivider = index != providers.lastIndex,
                )
            }
        }
    }
}
