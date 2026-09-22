package com.music.bitchord.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.ArtworkBackdrop
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.SystemBarIcons
import com.music.bitchord.ui.theme.rememberArtworkPalette
import java.text.DateFormat
import java.util.Date

class AlarmRingingActivity : AppCompatActivity() {
    private var id = ""
    private var token = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        id = intent.getStringExtra(ID).orEmpty()
        token = intent.getStringExtra(TOKEN).orEmpty()
        AlarmStore.init(this)
        setContent {
            BitChordTheme(darkTheme = true) {
                SystemBarIcons(dark = false)
                val state by AlarmStore.alarms.collectAsState()
                val alarm = state.alarms.firstOrNull { it.id == id }
                if (alarm == null || state.activeSession?.token != token) {
                    LaunchedEffect(Unit) { finishAndRemoveTask() }
                } else {
                    Ringing(
                        alarm = alarm,
                        snooze = { send(AlarmReceiver.snoozePendingIntent(this, id, token)) },
                        stop = { send(AlarmReceiver.stopPendingIntent(this, id, token)) },
                    )
                }
            }
        }
    }

    private fun send(pendingIntent: PendingIntent) {
        runCatching { pendingIntent.send() }
        finishAndRemoveTask()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return super.dispatchKeyEvent(event)
        }
        val alarm = AlarmStore.current(this).alarms.firstOrNull { it.id == id }
            ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (volumeButtonCommand(alarm.volumeButtonAction, event.repeatCount == 0)) {
                AlarmVolumeButtonCommand.SNOOZE ->
                    send(AlarmReceiver.snoozePendingIntent(this, id, token))
                AlarmVolumeButtonCommand.STOP ->
                    send(AlarmReceiver.stopPendingIntent(this, id, token))
                AlarmVolumeButtonCommand.ADJUST_ALARM_VOLUME -> getSystemService(AudioManager::class.java)
                    .adjustStreamVolume(
                        AudioManager.STREAM_ALARM,
                        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            AudioManager.ADJUST_RAISE
                        } else {
                            AudioManager.ADJUST_LOWER
                        },
                        AudioManager.FLAG_SHOW_UI,
                    )
                else -> Unit
            }
        }
        return true
    }

    companion object {
        private const val ID = "alarm_id"
        private const val TOKEN = "token"

        fun intent(context: Context, id: String, token: String) = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, AlarmRingingActivity::class.java)
                .putExtra(ID, id)
                .putExtra(TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

@Composable
private fun Ringing(alarm: AlarmConfig, snooze: () -> Unit, stop: () -> Unit) {
    val artworkUrl = alarm.song?.artworkUrl.artworkAt(PLAYER_ART_PX)
    val palette = rememberArtworkPalette(
        imageUrl = artworkUrl,
        dark = true,
        artPx = PLAYER_ART_PX,
    )
    val haptics = rememberHaptics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        ArtworkBackdrop(
            palette = palette,
            imageUrl = artworkUrl,
            modifier = Modifier.matchParentSize(),
            washFraction = 0.64f,
            artPx = PLAYER_ART_PX,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.58f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Black.copy(alpha = 0.48f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 30.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    fontWeight = FontWeight.W800,
                ),
                color = palette.onBackground,
                maxLines = 1,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                AlarmRingingArtwork(
                    artworkUrl = artworkUrl,
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 350.dp)
                        .aspectRatio(1f),
                )
            }

            Text(
                text = alarm.song?.title.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = alarm.song?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(26.dp))

            AlarmActionButton(
                label = stringResource(R.string.alarm_snooze_duration, alarm.snoozeMinutes),
                height = 72.dp,
                container = palette.elevated,
                content = palette.onBackground,
                border = palette.divider,
                onClick = {
                    haptics.play(Haptic.Tap)
                    snooze()
                },
            )
            Spacer(Modifier.height(12.dp))
            AlarmActionButton(
                label = stringResource(R.string.alarm_stop),
                height = 64.dp,
                container = palette.onBackground.copy(alpha = 0.10f),
                content = palette.onBackground,
                border = palette.onBackground.copy(alpha = 0.28f),
                onClick = {
                    haptics.play(Haptic.Select)
                    stop()
                },
            )
        }
    }
}

@Composable
private fun AlarmRingingArtwork(
    artworkUrl: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    if (artworkUrl != null) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .thumbnailBorder(shape),
        )
    } else {
        Box(
            modifier = modifier
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(palette.elevated)
                .thumbnailBorder(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun AlarmActionButton(
    label: String,
    height: androidx.compose.ui.unit.Dp,
    container: Color,
    content: Color,
    border: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
