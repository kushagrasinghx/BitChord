package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * The Karaoke drawer: the feature's master switch, the vocal level it sings
 * against, and — when the audio on its way out cannot be processed — the
 * reason and the way back.
 *
 * The button that opens this ([PlayerActionRow]'s Karaoke glyph) also turns
 * the feature on at the level the listener last chose, which is 0% — maximum
 * reduction — the first time, so the press is immediately audible. Closing
 * the drawer never switches anything off; the master row here is the only
 * way to do that, and it keeps the level for the next time.
 *
 * A route that bypasses the vocal stage (bit-perfect, a Cast speaker, the
 * watch, a mono track, an undecoded stream) never looks like it is working:
 * the master row says "paused", the status row says why, and where a way
 * back exists it is right there — the drawer does not just explain, it
 * recovers. Enabling on such a route is allowed rather than blocked, and
 * processing resumes by itself when the route allows it again, so the
 * preference survives the detour.
 */
@Composable
internal fun KaraokeSheet(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    /**
     * Recovery for a remote speaker: take the music back on this phone, by
     * way of the output drawer that knows how. The caller closes this sheet
     * first, so the two drawers never stack.
     */
    onOpenOutputs: () -> Unit,
    /**
     * The sing-along continuation: called once the row below has turned the
     * synced-lyrics preference on by its own explicit tap, this opens the
     * lyrics surface behind the drawer — the caller's own [openLyrics], so
     * the sing-along opens the same panel the Lyrics glyph does, never a
     * second one, and dismissing the drawer lands in it.
     */
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled by PlayerSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val level by PlayerSettings.karaokeVocalLevel.collectAsStateWithLifecycle()
    val availability by PlayerSettings.karaokeAvailability.collectAsStateWithLifecycle()
    val syncedLyrics by PlayerSettings.syncedLyrics.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val processing = availability == KaraokeAvailability.AVAILABLE

    PlayerDrawer(
        hazeState = hazeState,
        title = stringResource(Res.string.karaoke),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MasterRow(
                enabled = enabled,
                processing = processing,
                onToggle = {
                    haptics.play(if (enabled) Haptic.ToggleOff else Haptic.ToggleOn)
                    PlayerSettings.setKaraokeEnabled(!enabled)
                },
            )

            VocalsCard(
                level = level,
                // The slider writes the preference, and the preference is always
                // valid to hold — it is applied the moment a compatible route has
                // it, and the status row below is what says when it is not being
                // applied right now.
                onLevelChange = { PlayerSettings.setKaraokeVocalLevel(coerceKaraokeVocalLevel(it)) },
            )

            // "Original" is the 100% end of the slider: back to the mix
            // exactly as it was recorded. Centered by the drawer, like the
            // offset sheet's Reset.
            Text(
                text = stringResource(Res.string.karaoke_reset_vocals),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = if (level < 1f) 0.8f else 0.35f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = level < 1f) {
                        haptics.play(Haptic.Tap)
                        PlayerSettings.setKaraokeVocalLevel(1f)
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )

            // Sing-along offered, never assumed: with Karaoke on and the
            // synced-lyrics preference off, the one tap that turns the
            // preference on sits right here — and only the tap performs it,
            // because the preference governs third-party network lookups
            // that the listener's settings, not this feature, decide about.
            // The tap then opens the words behind this drawer, so
            // dismissing it lands in the sing-along; the vocal reduction
            // itself runs on either way, with the words or without them.
            if (offersSyncedLyricsEnable(enabled, syncedLyrics)) {
                SingAlongRow(
                    onEnable = {
                        haptics.play(Haptic.ToggleOn)
                        PlayerSettings.setSyncedLyrics(true)
                        onOpenLyrics()
                    },
                )
            }

            Text(
                text = stringResource(Res.string.karaoke_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            )

            if (!processing) {
                AvailabilityRow(
                    availability = availability,
                    onUseNormalOutput = {
                        // An explicit tap, never a silent rewrite: this is the
                        // one action that touches the bit-perfect setting.
                        haptics.play(Haptic.Select)
                        PlayerPlatform.host.useNormalOutput()
                    },
                    onOpenOutputs = {
                        haptics.play(Haptic.Select)
                        onOpenOutputs()
                    },
                )
            }
        }
    }
}

/**
 * The master switch. Its trailing button is the drawer's one disable action,
 * labelled in full so it reads whole on its own — screen reader or eye.
 */
@Composable
private fun MasterRow(
    enabled: Boolean,
    processing: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BitChordIcons.KaraokeMic,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.karaoke),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (enabled) 1f else 0.85f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // "On — paused", not "On", whenever a route is holding the
                // processing up: nothing in this drawer is allowed to look
                // like it is working when it is not.
                text = stringResource(
                    when {
                        !enabled -> Res.string.karaoke_off
                        !processing -> Res.string.karaoke_on_paused
                        else -> Res.string.karaoke_on
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = if (enabled && processing) 0.7f else 0.55f),
            )
        }
        Text(
            text = stringResource(if (enabled) Res.string.karaoke_disable else Res.string.karaoke_enable),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggle() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/**
 * Sing-along, offered rather than assumed. Drawn only while Karaoke is on
 * with the synced-lyrics preference off, and the preference is turned on by
 * the trailing tap alone — nothing anywhere in the feature flips it for the
 * listener. The tap then opens the words behind the drawer, so dismissing
 * the drawer lands in the sing-along.
 */
@Composable
private fun SingAlongRow(
    onEnable: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BitChordIcons.LyricsQuote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.karaoke_sing_along),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.karaoke_sing_along_off_detail),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Text(
            text = stringResource(Res.string.karaoke_enable_synced_lyrics),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onEnable() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/**
 * The vocal level, 0% (maximum reduction) to 100% (the original mix), on the
 * player's own slider. Every drag writes the setting immediately — the DSP
 * glides to each new target rather than stepping, so the bar is as continuous
 * under a finger as the audio is.
 */
@Composable
private fun VocalsCard(
    level: Float,
    onLevelChange: (Float) -> Unit,
) {
    val percent = karaokeVocalPercent(level)
    // Resolved here rather than in the semantics lambda below, which is not
    // a composable scope.
    val sliderDescription = stringResource(Res.string.karaoke_vocals_slider, percent)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.karaoke_vocals),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = stringResource(Res.string.karaoke_vocals_percent, percent),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        ThinSlider(
            value = coerceKaraokeVocalLevel(level),
            onValueChange = onLevelChange,
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                // ThinSlider draws its own gesture, not Compose's slider
                // semantics, so the percentage it carries is spoken here.
                .semantics {
                    contentDescription = sliderDescription
                },
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.karaoke_vocals_reduced),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                text = stringResource(Res.string.karaoke_vocals_original),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/**
 * Why processing cannot run right now — and the way back where one exists:
 * bit-perfect offers to stand down for normal processed output, a remote
 * speaker offers the output drawer to take the music back. The track's own
 * shape (mono, undecoded) has no action to offer, only the explanation.
 */
@Composable
private fun AvailabilityRow(
    availability: KaraokeAvailability,
    onUseNormalOutput: () -> Unit,
    onOpenOutputs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (availability) {
                        KaraokeAvailability.NEEDS_STEREO -> Res.string.karaoke_unavailable_stereo
                        KaraokeAvailability.NEEDS_LINEAR_PCM -> Res.string.karaoke_unavailable_pcm
                        KaraokeAvailability.BIT_PERFECT_ACTIVE -> Res.string.karaoke_unavailable_bit_perfect
                        KaraokeAvailability.REMOTE_OUTPUT_ACTIVE -> Res.string.karaoke_unavailable_remote
                        KaraokeAvailability.AVAILABLE -> Res.string.karaoke_on
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    when (availability) {
                        KaraokeAvailability.NEEDS_STEREO -> Res.string.karaoke_unavailable_stereo_detail
                        KaraokeAvailability.NEEDS_LINEAR_PCM -> Res.string.karaoke_unavailable_pcm_detail
                        KaraokeAvailability.BIT_PERFECT_ACTIVE -> Res.string.karaoke_unavailable_bit_perfect_detail
                        KaraokeAvailability.REMOTE_OUTPUT_ACTIVE -> Res.string.karaoke_unavailable_remote_detail
                        KaraokeAvailability.AVAILABLE -> Res.string.karaoke_description
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            when (availability) {
                KaraokeAvailability.BIT_PERFECT_ACTIVE -> RecoveryButton(
                    label = stringResource(Res.string.karaoke_use_normal_output),
                    onClick = onUseNormalOutput,
                )
                KaraokeAvailability.REMOTE_OUTPUT_ACTIVE -> RecoveryButton(
                    label = stringResource(Res.string.karaoke_switch_output),
                    onClick = onOpenOutputs,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun RecoveryButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

/**
 * The slider's 0..1 level as the whole percentage the drawer shows and
 * speaks. 0 is maximum reduction and 100 is the original mix.
 */
internal fun karaokeVocalPercent(level: Float): Int =
    (coerceKaraokeVocalLevel(level) * 100f).roundToInt()

/**
 * Clamps [level] to the range the setting accepts — 0 (maximum reduction)
 * to 1 (original mix) — with anything unreadable read as the default 0
 * rather than passed on, so a slider event can never store a NaN or an
 * out-of-range level.
 */
internal fun coerceKaraokeVocalLevel(level: Float): Float =
    if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
