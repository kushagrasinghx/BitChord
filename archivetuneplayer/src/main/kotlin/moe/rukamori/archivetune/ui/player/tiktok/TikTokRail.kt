/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.tiktok

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.shareLocalAudio
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val TIKTOK_RED = Color(0xFFFE2C55)

private object TikTokLikeCountCache {
    private const val NO_DATA = -1
    private val cache = ConcurrentHashMap<String, Int>()

    suspend fun likeCountLabelOf(videoId: String): String? {
        if (videoId.isBlank() || videoId.isLocalMediaId()) return null
        cache[videoId]?.let { cached ->
            return cached.takeIf { it > 0 }?.toLabel()
        }
        val result = YouTube.getLikeCount(videoId)
        if (result.isSuccess) {
            cache[videoId] = result.getOrNull() ?: NO_DATA
        }
        return result.getOrNull()?.takeIf { it > 0 }?.toLabel()
    }

    private fun Int.toLabel(): String = formatCompactCount(this.toLong())
}

private object TikTokArtistAvatarCache {
    private val cache = ConcurrentHashMap<String, Optional<String>>()

    fun thumbnailUrlOf(artistId: String): String? = cache[artistId]?.orElse(null)

    fun store(
        artistId: String,
        thumbnailUrl: String?,
    ) {
        cache[artistId] = Optional.ofNullable(thumbnailUrl)
    }
}

private const val TIKTOK_RAIL_FADE_MS = 180

@Composable
internal fun TikTokRail(
    pageMetadata: MediaMetadata,
    isCurrentPage: Boolean,
    playerConnection: PlayerConnection,
    sheetState: BottomSheetState,
    lyricsActive: Boolean,
    onToggleLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenLyricsMenu: () -> Unit,
    navController: NavController,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val database = LocalDatabase.current

    val librarySong by database.song(pageMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)
    val isLocal = librarySong?.song?.isLocal == true

    val likeAction =
        rememberTikTokLikeAction(
            pageMetadata = pageMetadata,
            isCurrentPage = isCurrentPage,
            playerConnection = playerConnection,
        )

    val likeCountLabel by produceState<String?>(initialValue = null, pageMetadata.id) {
        value = null
        value = TikTokLikeCountCache.likeCountLabelOf(pageMetadata.id)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .padding(end = 10.dp)
                .padding(vertical = 6.dp),
    ) {
        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TikTokArtistAvatar(
                    pageMetadata = pageMetadata,
                    sheetState = sheetState,
                    navController = navController,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        val liked = librarySong?.song?.liked == true
        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            TikTokLikeRailButton(
                liked = liked,
                likeCountLabel = likeCountLabel,
                onLike = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    likeAction(false)
                },
            )
        }

        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            TikTokRailButton(
                iconRes = R.drawable.solar_chat_round_linear,
                contentDescription = stringResource(R.string.lyrics),
                tint = if (lyricsActive) TIKTOK_RED else Color.White,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleLyrics()
            }
        }

        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            TikTokRailButton(
                iconRes = R.drawable.solar_bookmark_linear,
                contentDescription = stringResource(R.string.add_to_playlist),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAddToPlaylist()
            }
        }

        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            TikTokRailButton(
                iconRes = R.drawable.solar_share_linear,
                contentDescription = stringResource(R.string.share),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isLocal) {
                    val mimeType = librarySong?.format?.mimeType
                    shareLocalAudio(context, pageMetadata.id, mimeType)
                } else {
                    val url = "https://music.youtube.com/watch?v=${pageMetadata.id}"
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                            putExtra(Intent.EXTRA_TITLE, pageMetadata.title)
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            }
        }

        AnimatedVisibility(
            visible = !lyricsActive,
            enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
            exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
        ) {
            TikTokRailButton(
                iconRes = R.drawable.solar_more_vert_linear,
                contentDescription = stringResource(R.string.more),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (lyricsActive) {
                    onOpenLyricsMenu()
                } else {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = pageMetadata,
                            navController = navController,
                            playerBottomSheetState = sheetState,
                            onShowDetailsDialog = {
                                bottomSheetPageState.show { ShowMediaInfo(pageMetadata.id) }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TikTokArtistAvatar(
    pageMetadata: MediaMetadata,
    sheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val haptics = LocalHapticFeedback.current

    val artist = remember(pageMetadata.id) { pageMetadata.artists.firstOrNull() }
    val artistId = artist?.id

    val artistFlow =
        remember(artistId, database) {
            if (artistId != null) {
                database.artist(artistId)
            } else {
                flowOf<moe.rukamori.archivetune.db.entities.Artist?>(null)
            }
        }
    val libraryArtist by artistFlow.collectAsStateWithLifecycle(initialValue = null)
    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null

    val libraryArtistThumbnail = libraryArtist?.artist?.thumbnailUrl
    val resolvedAvatarUrl by
        produceState<String?>(
            initialValue = artist?.thumbnailUrl?.takeIf(String::isNotBlank),
            artistId,
            libraryArtistThumbnail,
        ) {
            val metadataThumbnail = artist?.thumbnailUrl?.takeIf(String::isNotBlank)
            when {
                metadataThumbnail != null -> value = metadataThumbnail
                !libraryArtistThumbnail.isNullOrBlank() -> value = libraryArtistThumbnail
                artistId != null -> {
                    val cached = TikTokArtistAvatarCache.thumbnailUrlOf(artistId)
                    if (cached != null) {
                        value = cached
                    } else {
                        val fetched =
                            runCatching {
                                YouTube.artist(artistId).getOrNull()?.artist?.thumbnail
                            }.getOrNull()?.takeIf(String::isNotBlank)
                        TikTokArtistAvatarCache.store(artistId, fetched)
                        value = fetched
                    }
                }
            }
        }
    val avatarUrl = resolvedAvatarUrl ?: pageMetadata.thumbnailUrl

    Box(
        modifier =
            modifier
                .size(40.dp)
                .tiktokNoRippleClickable(
                    enabled = artistId != null,
                ) {
                    if (artistId == null) return@tiktokNoRippleClickable
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sheetState.collapseSoft()
                    navController.navigate("artist/$artistId") {
                        launchSingleTop = true
                    }
                },
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = artist?.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.95f), CircleShape)
                    .shadow(elevation = 4.dp, shape = CircleShape, clip = false),
        )

        if (!isSubscribed) {
            val subscribeLabel = stringResource(R.string.subscribe)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(18.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(TIKTOK_RED)
                        .semantics { contentDescription = subscribeLabel }
                        .tiktokNoRippleClickable(enabled = artistId != null) {
                            if (artistId == null) return@tiktokNoRippleClickable
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val existing = libraryArtist?.artist
                            if (existing != null) {
                                database.query { update(existing.toggleLike()) }
                            } else if (artist != null) {
                                database.transaction {
                                    insert(
                                        ArtistEntity(
                                            id = artistId,
                                            name = artist.name,
                                            thumbnailUrl = artist.thumbnailUrl,
                                        ).toggleLike(),
                                    )
                                }
                            }
                        },
            ) {
                Canvas(modifier = Modifier.size(9.dp)) {
                    val stroke = 1.6.dp.toPx()
                    val half = stroke / 2f
                    drawLine(
                        color = Color.White,
                        start = Offset(half, size.height / 2f),
                        end = Offset(size.width - half, size.height / 2f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width / 2f, half),
                        end = Offset(size.width / 2f, size.height - half),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberTikTokLikeAction(
    pageMetadata: MediaMetadata,
    isCurrentPage: Boolean,
    playerConnection: PlayerConnection,
): (Boolean) -> Unit {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()

    val librarySong by database.song(pageMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)
    return remember(database, syncUtils, scope, pageMetadata, isCurrentPage, playerConnection) {
        { likeOnly: Boolean ->
            val row = librarySong?.song
            when {
                likeOnly && row?.liked == true -> Unit

                row != null -> {
                    val s = row.toggleLike()
                    database.query { update(s) }
                    syncUtils.likeSong(s)
                }

                isCurrentPage -> playerConnection.toggleLike()

                else -> {
                    database.transaction { insert(pageMetadata) }
                    scope.launch {
                        val entity = database.song(pageMetadata.id).first()?.song ?: return@launch
                        val s = entity.toggleLike()
                        database.query { update(s) }
                        syncUtils.likeSong(s)
                    }
                }
            }
        }
    }
}

@Composable
private fun TikTokLikeRailButton(
    liked: Boolean,
    likeCountLabel: String?,
    onLike: () -> Unit,
) {
    val likeLabel = stringResource(R.string.action_like)
    val scale = remember { Animatable(1f) }

    var wasLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !wasLiked) {
            scale.snapTo(0.2f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        } else if (!liked && wasLiked) {
            scale.snapTo(0.85f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
        }
        wasLiked = liked
    }
    Box(
        modifier =
            Modifier
                .width(48.dp)
                .tiktokNoRippleClickable(onClick = onLike),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
            ) {
                TikTokRailGlyph(
                    iconRes = if (liked) R.drawable.solar_heart_bold else R.drawable.solar_heart_linear,
                    contentDescription = likeLabel,
                    tint = if (liked) TIKTOK_RED else Color.White,
                    iconSize = 34.dp,
                )
            }

            AnimatedVisibility(
                visible = likeCountLabel != null,
                enter =
                    fadeIn(tween(TIKTOK_RAIL_FADE_MS)) +
                        slideInVertically(tween(TIKTOK_RAIL_FADE_MS)) { it / 2 },
                exit = fadeOut(tween(120)),
                modifier = Modifier.height(18.dp),
            ) {
                TikTokRailCountLabel(label = likeCountLabel.orEmpty())
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun TikTokRailCountLabel(label: String) {
    Box {
        Text(
            text = label,
            color = Color.Black.copy(alpha = 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 1.dp)
                    .blur(2.dp),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun TikTokRailButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    TikTokRailActionButton(onClick = onClick) {
        TikTokRailGlyph(
            iconRes = iconRes,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
private fun TikTokRailGlyph(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    iconSize: Dp = 30.dp,
) {
    Box(modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.35f),
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .blur(2.dp),
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TikTokRailActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .tiktokNoRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
    Spacer(Modifier.height(2.dp))
}
