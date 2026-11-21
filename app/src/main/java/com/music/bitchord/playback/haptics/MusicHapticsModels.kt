package com.music.bitchord.playback.haptics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tactile response profiles shaping how the music haptics engine balances
 * bass, drums, vocals, and high-frequency textures.
 */
enum class MusicHapticsProfile {
    /** Full musical picture: kicks, snares, claps, vocal phrasing, and bass grooves. */
    BALANCED,

    /** Heavy focus on sub-drops, kick drums, and 808 basslines. */
    BASS_BEAT,

    /** Tight, ultra-snappy kicks, snares, claps, and hi-hats with zero rumble. */
    CRISP_PERCUSSION,

    /** Expressive full-spectrum feel combining every drum hit with rich vocal & bass swells. */
    IMMERSIVE,
}

/**
 * Immutable snapshot of all user-configurable Music Haptics settings pushed from
 * [com.music.bitchord.data.settings.AppSettings] to the DSP processor and motor engine.
 */
data class MusicHapticsConfig(
    val enabled: Boolean = false,
    val profile: MusicHapticsProfile = MusicHapticsProfile.BALANCED,
    /** Master vibration strength in [0.15f, 1.0f]. */
    val intensity: Float = 0.78f,
    /** Beat & onset sensitivity in [0.0f, 1.0f]; higher catches subtler accents. */
    val sensitivity: Float = 0.58f,
    /**
     * Manual timing offset in milliseconds, in [-150, +250].
     * Positive values delay haptics further (for Bluetooth); negative pulls earlier.
     */
    val syncOffsetMs: Int = 0,
    /** Independent mix weight for kicks, 808 drops, and bass grooves in [0f, 1f]. */
    val bassWeight: Float = 1.0f,
    /** Independent mix weight for sung vocal syllables and chorus swells in [0f, 1f]. */
    val vocalWeight: Float = 0.75f,
    /** Independent mix weight for snares, claps, hi-hats, and crashes in [0f, 1f]. */
    val percussionWeight: Float = 0.80f,
    /** Automatically pause haptics while playing over the phone's built-in speaker. */
    val headphonesOnly: Boolean = false,
    /** Automatically pause haptics while the phone screen is turned off. */
    val pauseWhenScreenOff: Boolean = false,
)

/**
 * Nine distinct musical articulations detected by [MusicHapticsProcessor] and
 * rendered with unique physical motor textures by [MusicHapticsEngine].
 */
enum class HapticEventKind {
    /** Massive 808 / bass drop or downbeat slam (20–85 Hz surge + punch). */
    SUB_DROP,

    /** Tight, focused kick drum impact (45–110 Hz). */
    KICK_PUNCH,

    /** Warm sustained 808 glide or rolling bassline groove (30–130 Hz). */
    BASS_GROOVE,

    /** Punchy snare drum / rim crack (220–650 Hz body + high snap). */
    SNARE_CRACK,

    /** Crisp twin-impulse handclap / pop snap (650–2800 Hz sharp burst). */
    CLAP_SNAP,

    /** Soft, expressive curved pulse synced to sung vocal / lyric syllables (650–3200 Hz). */
    VOCAL_SYLLABLE,

    /** Gentle breathing shimmer during held chorus vocals and melodic pads. */
    VOCAL_SWELL,

    /** Feather-light micro-tick on closed/open hi-hats and shakers (4.5–11 kHz). */
    HIHAT_SHIMMER,

    /** Rising-then-fading tactile sweep on cymbal crashes and song risers. */
    CRASH_BUILD,
}

/**
 * A single beat/vocal-synchronized tactile event scheduled for playback on the vibrator motor.
 */
data class HapticBeatEvent(
    val kind: HapticEventKind,
    val strength: Float,
    val subBassEnergy: Float,
    val durationMs: Int,
    val delayMs: Long,
)

/**
 * Hardware capability tier detected from Android's [android.os.Vibrator].
 */
enum class HapticMotorTier {
    UNAVAILABLE,
    HD_PRIMITIVES,
    AMPLITUDE_ENVELOPE,
    BASIC_PULSE,
}

/**
 * Real-time 5-band telemetry snapshot exposed to [com.music.bitchord.ui.screens.MusicHapticsScreen].
 */
data class MusicHapticsSnapshot(
    val active: Boolean = false,
    val motorTier: HapticMotorTier = HapticMotorTier.AMPLITUDE_ENVELOPE,
    val subLevel: Float = 0f,
    val kickLevel: Float = 0f,
    val snareLevel: Float = 0f,
    val vocalLevel: Float = 0f,
    val airLevel: Float = 0f,
    val estimatedBpm: Int = 0,
    val lastEventKind: HapticEventKind? = null,
    val beatCounter: Long = 0L,
    val currentLeadMs: Int = 0,
)

/**
 * Process-wide telemetry bus bridging [MusicHapticsProcessor] / [MusicHapticsEngine]
 * with Compose UI without allocating when the UI is not observing.
 */
object MusicHapticsTelemetry {
    private val _state = MutableStateFlow(MusicHapticsSnapshot())
    val state: StateFlow<MusicHapticsSnapshot> = _state.asStateFlow()

    @Volatile
    var uiObserving: Boolean = false

    fun updateMotorTier(tier: HapticMotorTier) {
        _state.value = _state.value.copy(motorTier = tier)
    }

    fun updateActive(active: Boolean) {
        val current = _state.value
        if (current.active != active) {
            _state.value = if (active) {
                current.copy(active = true)
            } else {
                current.copy(
                    active = false,
                    subLevel = 0f,
                    kickLevel = 0f,
                    snareLevel = 0f,
                    vocalLevel = 0f,
                    airLevel = 0f,
                    estimatedBpm = 0,
                    lastEventKind = null,
                )
            }
        }
    }

    fun publishFrame(
        sub: Float,
        kick: Float,
        snare: Float,
        vocal: Float,
        air: Float,
        bpm: Int,
        eventKind: HapticEventKind?,
        leadMs: Int,
    ) {
        if (!uiObserving) return
        val current = _state.value
        val isPercussiveBeat = eventKind == HapticEventKind.SUB_DROP ||
            eventKind == HapticEventKind.KICK_PUNCH ||
            eventKind == HapticEventKind.SNARE_CRACK ||
            eventKind == HapticEventKind.CLAP_SNAP
        _state.value = current.copy(
            active = true,
            subLevel = sub.coerceIn(0f, 1f),
            kickLevel = kick.coerceIn(0f, 1f),
            snareLevel = snare.coerceIn(0f, 1f),
            vocalLevel = vocal.coerceIn(0f, 1f),
            airLevel = air.coerceIn(0f, 1f),
            estimatedBpm = bpm,
            lastEventKind = eventKind ?: current.lastEventKind,
            beatCounter = if (isPercussiveBeat) current.beatCounter + 1L else current.beatCounter,
            currentLeadMs = leadMs,
        )
    }
}
