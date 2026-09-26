package com.music.bitchord.ui.player

import com.music.bitchord.sharedui.resources.*

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song

/**
 * The widest the two columns are allowed to get between them, centred in
 * whatever is left over.
 *
 * Without a cap the lyric column simply takes every pixel past the artwork,
 * and on a wide window that is a column of text with a hand's width of empty
 * backdrop trailing off the right of every line. Lyrics are read down, not
 * across: past a certain measure the extra width is further for the eye to
 * travel back rather than more room for the words.
 */
private val LANDSCAPE_PLAYER_MAX_WIDTH = 1100.dp

/**
 * Below this height the landscape player tightens its right column — smaller
 * transport, shorter gaps — so a phone on its side fits the credits, the
 * scrubber, the transport and the volume bar between its status and
 * navigation bars without any of them falling off the bottom.
 */
private val LANDSCAPE_COMPACT_HEIGHT = 440.dp

/**
 * Side margin of the landscape player's sleeve column and credits on a
 * phone-height window, in place of [PLAYER_GUTTER].
 */
private val LANDSCAPE_GUTTER_COMPACT = 20.dp

/**
 * How the right column hands over between the player, the lyrics and the
 * queue. The left column never moves, so a crossfade is the whole transition —
 * the same page being turned, not a panel being opened.
 */
private const val LANDSCAPE_PANE_FADE_IN_MS = 220
private const val LANDSCAPE_PANE_FADE_IN_DELAY_MS = 90
private const val LANDSCAPE_PANE_FADE_OUT_MS = 140

/** Which of its three things the player's content column is showing. */
internal enum class PlayerPane { Main, Lyrics, Queue }

/** Room above the landscape columns for the sheet's drag handle. */
private val LANDSCAPE_HANDLE_STRIP = 24.dp

/**
 * The player in a landscape window — a tablet, or a phone on its side. See
 * [landscapePlayerAvailable].
 *
 * Two columns of equal width. The left one is the same in every state: the
 * sleeve, and under it the lyrics / output / queue row with the output name —
 * the controls for *which* of the three the right column shows. The right one
 * is that one thing: the credits and transport, the lyric sheet or the queue.
 * Nothing else is drawn twice and nothing moves between columns, so opening
 * the lyrics or the queue is only ever the right column's page being turned.
 *
 * Purely the arrangement. Every slot is built by [NowPlayingScreen], which is
 * where the state behind all of them lives — the same state the portrait
 * player reads, so rotating mid-song carries the open panel, the scrub and the
 * translation mode straight across.
 *
 * Held to [LANDSCAPE_PLAYER_MAX_WIDTH] and centred, inside the safe-drawing
 * insets: a phone on its side has its status bar, navigation bar and camera
 * cutout down the short edges, and the player's content keeps clear of all
 * three while its backdrop still runs underneath them.
 */
@Composable
internal fun LandscapePlayerLayout(
    pane: PlayerPane,
    background: @Composable (Modifier) -> Unit,
    /** The sleeve; handed a modifier that already fixes its square size. */
    artwork: @Composable (Modifier) -> Unit,
    /** The row under the sleeve, emitted into its column. */
    actions: @Composable () -> Unit,
    /** Credits and transport. [compact] is a phone-height window. */
    mainPane: @Composable (compact: Boolean) -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compact = maxHeight < LANDSCAPE_COMPACT_HEIGHT
            val gutter = if (compact) LANDSCAPE_GUTTER_COMPACT else PLAYER_GUTTER

            Row(
                modifier = Modifier
                    .widthIn(max = LANDSCAPE_PLAYER_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(
                        top = LANDSCAPE_HANDLE_STRIP,
                        bottom = if (compact) 8.dp else 20.dp,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The sleeve is square and takes whichever of the two axes
                    // runs out first once the row below has had its height —
                    // the column's width on a big tablet, the height on
                    // anything shorter. Measured here rather than estimated,
                    // so the row under it can never be pushed off the bottom.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        artwork(Modifier.size(minOf(maxWidth, maxHeight)))
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                    actions()
                }

                AnimatedContent(
                    targetState = pane,
                    transitionSpec = {
                        fadeIn(
                            tween(
                                LANDSCAPE_PANE_FADE_IN_MS,
                                delayMillis = LANDSCAPE_PANE_FADE_IN_DELAY_MS,
                            ),
                        ) togetherWith fadeOut(tween(LANDSCAPE_PANE_FADE_OUT_MS))
                    },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "landscapePane",
                ) { shown ->
                    when (shown) {
                        PlayerPane.Main -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = gutter),
                            contentAlignment = Alignment.Center,
                        ) {
                            mainPane(compact)
                        }
                        // The lyric sheet and the queue both reach back across
                        // [PLAYER_GUTTER] on their own (see
                        // [bleedHorizontally]), so that is the padding they
                        // get: their scroll areas then run exactly to the
                        // column's edges.
                        PlayerPane.Lyrics -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = PLAYER_GUTTER),
                        ) {
                            lyricsPane()
                        }
                        PlayerPane.Queue -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = PLAYER_GUTTER),
                        ) {
                            queuePane()
                        }
                    }
                }
            }

            // The sheet closes from a downward drag anywhere nothing else
            // claims, as it does in portrait; this is only the promise.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 8.dp)
                    .width(38.dp)
                    .height(5.dp)
                    .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.70f)),
            )
        }
    }
}

/**
 * The landscape player's sleeve: the still cover, and over it the looping
 * clip when there is one.
 *
 * Draws from the screen's own [request][PlayerArtwork.request] rather than a
 * request of its own, so it shares the portrait sleeve's bitmap, its retries
 * and its [loaded][PlayerArtwork.loaded] — rotating never reloads the cover.
 *
 * The clip keeps playing whichever pane is up. The portrait player stops it
 * because its sleeve shrinks to a thumbnail behind a panel; this one never
 * shrinks.
 */
@Composable
internal fun LandscapeArtwork(
    artRequest: ImageRequest,
    artLoaded: Boolean,
    onArtState: (AsyncImagePainter.State) -> Unit,
    canvas: CanvasArtwork?,
    canvasRendered: Boolean,
    isPlaying: Boolean,
    onCanvasRenderedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only cast once there is a picture: on the flat placeholder
                // tile a shadow reads as a second, darker square.
                .shadow(if (artLoaded || canvasRendered) 14.dp else 0.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artLoaded && !canvasRendered) {
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxSize(0.36f),
                )
            }
            AsyncImage(
                model = artRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = onArtState,
                // Not drawn under a clip that got there first — see the
                // portrait sleeve's note on TextureView stacking order.
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { if (artLoaded || !canvasRendered) drawContent() },
            )
            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = isPlaying,
                    onRenderedChanged = onCanvasRenderedChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        overlay()
    }
}

/**
 * The landscape player's right column in its main state: where the session
 * came from, the credits, the current line, the scrubber, the transport and
 * the volume bar — the portrait half-player, less the row the left column
 * already has.
 *
 * Centred in the column rather than pinned to its foot: there is no artwork
 * above it here to hand the spare height to.
 */
@Composable
internal fun LandscapeMainPane(
    compact: Boolean,
    /** "Playing from …", or null where Settings hides it. */
    caption: String?,
    onOpenCaption: () -> Unit,
    credits: @Composable () -> Unit,
    /** The one-line lyric over the scrubber; null where synced lyrics are off. */
    lyricStrip: (@Composable () -> Unit)?,
    scrubber: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    /** Null where Settings hides the volume bar. */
    volume: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = PLAYER_MAX_WIDTH)
            .fillMaxWidth(),
    ) {
        if (caption != null) {
            PlaybackOriginCaption(
                text = caption,
                onClick = onOpenCaption,
                textAlign = TextAlign.Start,
                contentPadding = PaddingValues(vertical = 2.dp),
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        }
        credits()
        Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
        lyricStrip?.invoke()
        scrubber()
        Spacer(Modifier.height(if (compact) 0.dp else 8.dp))
        transport()
        if (volume != null) {
            Spacer(Modifier.height(if (compact) 0.dp else 12.dp))
            volume()
        }
    }
}

/**
 * Title, artist, like and the three-dot menu, for the landscape player.
 *
 * Marquee rather than a plain ellipsis, matching the portrait player: a tablet
 * is wider, not infinitely wide, and a long title truncated on the one surface
 * with room to scroll it would be the odd one out.
 */
@Composable
internal fun LandscapeCredits(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    /** The menu glyph is briefly the undo arrow after a quality upgrade. */
    showRevertCue: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Crossfade(
            targetState = song.title to song.artist,
            animationSpec = tween(durationMillis = 300),
            modifier = Modifier.weight(1f),
            label = "landscapeCredits",
        ) {
            Column {
                var titleOverflowing by remember { mutableStateOf(false) }
                MarqueeText(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    onOverflowChange = { titleOverflowing = it },
                    leading = if (song.isExplicit == true) {
                        { ExplicitBadge(Color.White) }
                    } else {
                        null
                    },
                    modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                )
                Spacer(Modifier.height(2.dp))
                MarqueeText(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W500),
                    color = Color.White.copy(alpha = 0.55f),
                    // A title that's also scrolling gets to go first — starting
                    // together reads as clutter, so the artist waits a beat.
                    startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                    modifier = Modifier.opensPage(song.artistId, onOpenArtist),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Same gate as the portrait player's: no account to like against for a
        // guest, and no YouTube identity to rate a local file or a finished
        // download against either.
        if (signedIn && song.localUri == null) {
            val liked = likeStatus == LikeStatus.LIKE
            CircleGlyph(
                icon = if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                contentDescription = stringResource(
                    if (liked) Res.string.remove_from_liked else Res.string.like,
                ),
                onClick = onToggleLike,
                active = liked,
                haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
            )
            Spacer(Modifier.width(8.dp))
        }
        CircleGlyph(
            icon = if (showRevertCue) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(Res.string.more),
            onClick = onOpenMenu,
        )
    }
}

/**
 * The landscape player's right column with the lyrics open: the sheet, and
 * under it the romanize / source / translate bar.
 *
 * The bar is where the portrait player's status line and its two floating
 * toggles went. There the line sits over the scrubber and the toggles float
 * over the foot of the words; here there is no scrubber beside the lyrics, and
 * the bar gives all three a place of their own that costs the sheet no lines.
 */
@Composable
internal fun LandscapeLyricsPane(
    hasLyrics: Boolean,
    /** Shown in place of the sheet while there are no lines to draw. */
    placeholder: String,
    status: String,
    onChangeProvider: () -> Unit,
    romanizationToggle: @Composable () -> Unit,
    translationToggle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (hasLyrics) {
                panel(Modifier.fillMaxSize())
            } else {
                // Held at a fixed line rather than through
                // [LyricsUnavailableLine] or [LyricsLoadingLine]: those fade
                // out after a few seconds, which here would leave the whole
                // column blank for as long as the track keeps playing.
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fixed slots either side whether or not the toggles are drawn, so
            // the status line stays centred as lyrics arrive.
            Box(Modifier.size(34.dp)) { if (hasLyrics) romanizationToggle() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LyricsStatusWithChange(status = status, onChange = onChangeProvider)
            }
            Box(Modifier.size(34.dp)) { if (hasLyrics) translationToggle() }
        }
    }
}
