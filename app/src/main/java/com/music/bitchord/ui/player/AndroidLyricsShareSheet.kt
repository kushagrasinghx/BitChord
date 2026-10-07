package com.music.bitchord.ui.player

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.replay.ShareAction
import com.music.bitchord.ui.replay.cacheForSharing
import com.music.bitchord.ui.replay.saveToGallery
import com.music.bitchord.ui.replay.sendIntent
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * The picked lines, drawn, with the two things you can do to the picture.
 *
 * Built on [PlayerDrawer] rather than on a `ModalBottomSheet` for the same reason
 * as every other sheet in this package: it is inside the player, which is itself
 * already a modal sheet, and nesting the two leaves the inner one with nothing
 * to be modal over. It also means this drawer gets the player's own frosted
 * material, the drag-to-dismiss gesture and the scrim fade for free.
 *
 * The render happens on a background dispatcher and the preview is a plain
 * `Image` of the result — no second layout pass, no offscreen Compose, and the
 * identical pixels that the Save and Share buttons then write to disk.
 */
@Composable
internal fun AndroidLyricsShareSheet(
    hazeState: HazeState,
    request: LyricsShareRequest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var image by remember(request) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(request) { mutableStateOf(false) }
    var saved by remember(request) { mutableStateOf(false) }
    // Same picture, cut to the 9:16 frame a story wants instead of to its lines.
    var story by remember(request) { mutableStateOf(false) }

    LaunchedEffect(request, story) {
        image = null
        failed = false
        saved = false
        image = runCatching { renderLyricsShareCard(context, request, story) }
            .onFailure { failed = true }
            .getOrNull()
    }

    PlayerDrawer(
        hazeState = hazeState,
        title = stringResource(R.string.lyrics_share_title),
        onDismiss = onDismiss,
        modifier = modifier,
        titleGap = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.lyrics_share_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            )

            val bitmap = image
            // The card is only as tall as its lines, so the frame follows it:
            // a strip comes out wide, a full verse narrow, both about the same
            // height on screen rather than a fixed box with bands to spare.
            val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: (9f / 16f)
            val preview = Modifier
                .fillMaxWidth((0.72f * ratio).coerceIn(0.36f, 0.92f))
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(18.dp))
            if (bitmap == null) {
                Box(
                    modifier = preview
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.your_lyrics_card),
                    modifier = preview,
                )
            }

            ShareAction(
                label = stringResource(if (story) R.string.lyrics_share_story_revert else R.string.lyrics_share_story),
                icon = if (story) Icons.Rounded.Undo else Icons.Rounded.PhoneIphone,
                accent = false,
                enabled = image != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.play(Haptic.Tap)
                    story = !story
                },
            )

            if (failed) {
                Text(
                    text = stringResource(R.string.couldnt_draw_picture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShareAction(
                    label = stringResource(R.string.save),
                    icon = Icons.Rounded.Download,
                    accent = false,
                    enabled = image != null,
                    modifier = Modifier.weight(1f),
                    // Once the picture is in the gallery the button says so
                    // itself — a check and "Saved!" — so the line under the
                    // card does not have to.
                    saved = saved,
                    savedLabel = stringResource(R.string.saved_bang),
                    onClick = {
                        val drawn = image
                        if (drawn != null) {
                            haptics.play(Haptic.Tap)
                            scope.launch {
                                saved = saveToGallery(
                                    context,
                                    drawn,
                                    request.song.title,
                                    LYRICS_FILE_PREFIX,
                                )
                            }
                        }
                    },
                )
                ShareAction(
                    label = stringResource(R.string.share),
                    icon = Icons.Rounded.IosShare,
                    accent = true,
                    enabled = image != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val drawn = image
                        if (drawn != null) {
                            haptics.play(Haptic.Tap)
                            scope.launch {
                                val uri = cacheForSharing(context, drawn, LYRICS_FILE_NAME)
                                if (uri == null) {
                                    failed = true
                                    saved = false
                                } else {
                                    context.startActivity(
                                        Intent.createChooser(
                                            sendIntent(uri),
                                            context.getString(R.string.share_your_lyrics),
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Two names, deliberately not the Replay's.
 *
 * The cache name so a replay share and a lyrics share cannot overwrite each
 * other while the receiving app is still reading the first, and the gallery
 * prefix so a saved card in the pictures folder says which feature produced it.
 */
private const val LYRICS_FILE_NAME = "lyrics.png"
private const val LYRICS_FILE_PREFIX = "bitchord-lyrics"
