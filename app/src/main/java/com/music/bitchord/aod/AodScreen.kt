package com.music.bitchord.aod

import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery5Bar
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.settings.AppSettings
import androidx.media3.common.MediaMetadata
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The always-on screen's face, ported from ArchiveTune's AodPlayerScreen in
 * this app's own idiom: near-black rather than themed — a screen that is on
 * all night doesn't get to choose colours — with white chrome over it, exactly
 * as the player's own controls are drawn. Every element answers one of the
 * AOD settings page's switches; the master switch off leaves the system's own
 * screen saver up (see [AodDreamService]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AodScreen(
    metadata: MediaMetadata?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    pixelShift: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val enabled by AppSettings.aodEnabled.collectAsStateWithLifecycle()
    val showThumbnail by AppSettings.aodShowThumbnail.collectAsStateWithLifecycle()
    val showArtist by AppSettings.aodShowArtist.collectAsStateWithLifecycle()
    val showAlbum by AppSettings.aodShowAlbum.collectAsStateWithLifecycle()
    val showProgress by AppSettings.aodShowProgress.collectAsStateWithLifecycle()
    val showTimeLabels by AppSettings.aodShowTimeLabels.collectAsStateWithLifecycle()
    val showControls by AppSettings.aodShowControls.collectAsStateWithLifecycle()
    val showClock by AppSettings.aodShowClock.collectAsStateWithLifecycle()
    val showBattery by AppSettings.aodShowBattery.collectAsStateWithLifecycle()
    val touchLock by AppSettings.aodTouchLock.collectAsStateWithLifecycle()

    // The clock and battery, ticking along while the screen stands.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    var battery by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return@LaunchedEffect
        battery = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Touch lock: a full-screen tap-catcher, so a pocket's taps land
            // nowhere. The power key still ends the dream; that is the system's.
            .then(
                if (touchLock && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                } else {
                    Modifier
                },
            ),
    ) {
        if (!enabled) return@Box

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                // The pixel shift, as ArchiveTune has it: a hair one way, then
                // the other, so no one row of the panel holds the artwork's
                // edge all night.
                .graphicsLayer {
                    translationX = (if (pixelShift >= 1f) 1.5f else -1.5f) * density
                    translationY = (if (pixelShift >= 1f) -1.5f else 1.5f) * density
                },
        ) {
            if (showClock || showBattery) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 28.dp),
                ) {
                    if (showClock) {
                        Text(
                            text = DateTimeFormatter.ofPattern("H:mm")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochMilli(now)),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (showBattery && battery >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Battery5Bar,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "$battery%",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                    }
                }
            }

            if (showThumbnail) {
                val art = metadata?.artworkUri
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                ) {
                    if (art != null) {
                        AsyncImage(
                            // A String, as every other image request in the app
                            // is made — coil3 takes it on every platform.
                            model = art.toString(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            Text(
                text = metadata?.title?.toString().orEmpty().ifBlank { "—" },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            if (showArtist) {
                Text(
                    text = metadata?.artist?.toString().orEmpty(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
            if (showAlbum) {
                Text(
                    text = metadata?.albumTitle?.toString().orEmpty(),
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 3.dp),
                )
            }

            if (showProgress) {
                Spacer(Modifier.height(26.dp))
                val fraction = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val animated by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(900),
                    label = "aodProgress",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animated)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
                }
                if (showTimeLabels) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            text = formatAodTime(positionMs),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "-" + formatAodTime((durationMs - positionMs).coerceAtLeast(0)),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (showControls) {
                Spacer(Modifier.height(30.dp))
                // The app's own transport glyphs, as the mini player draws
                // them — the AOD is the same player, dozing.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(SharedRes.drawable.ic_player_previous),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPrevious() },
                    )
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(
                                if (playing) SharedRes.drawable.ic_player_pause else SharedRes.drawable.ic_player_play
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Image(
                        painter = painterResource(SharedRes.drawable.ic_player_next),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onNext() },
                    )
                }
            }
        }
    }
}

private fun formatAodTime(ms: Long): String {
    val totalSeconds = (ms / 1000).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
