package com.music.bitchord.playback.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.music.bitchord.BuildConfig
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Hardware-adaptive, latency-synchronized music haptics renderer (v3).
 *
 * Synthesizes **9 distinct musical vibration textures** ([HapticEventKind]) tailored
 * to each phone's exact actuator:
 * - **SUB_DROP**: Heavy double-stage thud + decaying sub-tail.
 * - **KICK_PUNCH**: Tight, dry chest-punch attack.
 * - **BASS_GROOVE**: Smooth rolling 808 / bassline wave.
 * - **SNARE_CRACK**: Punchy body + razor-sharp crack snap.
 * - **CLAP_SNAP**: Twin-impulse micro-flam mimicking a real handclap.
 * - **VOCAL_SYLLABLE**: Soft Gaussian bell-curved pulse synced to sung lyrics.
 * - **VOCAL_SWELL**: Ultra-gentle breathing shimmer on held chorus notes.
 * - **HIHAT_SHIMMER**: Feather-light 8–10 ms micro-tick.
 * - **CRASH_BUILD**: Rising-then-fading tactile sweep on crashes and risers.
 */
class MusicHapticsEngine(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val vibrator: Vibrator? =
        if (MusicHapticsCapability.isSupported(appContext)) resolveVibrator(appContext) else null
    private val thread = HandlerThread("BitChord-MusicHaptics", Process.THREAD_PRIORITY_URGENT_AUDIO).apply {
        start()
    }
    private val handler = Handler(thread.looper)

    @Volatile
    private var config: MusicHapticsConfig = MusicHapticsConfig()

    @Volatile
    private var playing: Boolean = false

    @Volatile
    private var released: Boolean = false

    private var lastFireUptimeMs: Long = 0L
    private var motorBusyUntilMs: Long = 0L
    private var activePriority: Int = 0

    private val supportsThud: Boolean
    private val supportsLowTick: Boolean
    private val supportsTick: Boolean
    private val supportsClick: Boolean
    private val supportsQuickRise: Boolean
    private val supportsSlowRise: Boolean
    private val supportsQuickFall: Boolean
    private val supportsSpin: Boolean
    private val supportsAmplitude: Boolean

    val motorTier: HapticMotorTier
    val motorWakeLatencyMs: Long

    private val mediaVibrationAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_MEDIA)
                .build()
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private val legacyAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    init {
        val vib = vibrator
        if (vib == null || !vib.hasVibrator()) {
            supportsThud = false
            supportsLowTick = false
            supportsTick = false
            supportsClick = false
            supportsQuickRise = false
            supportsSlowRise = false
            supportsQuickFall = false
            supportsSpin = false
            supportsAmplitude = false
            motorTier = HapticMotorTier.UNAVAILABLE
            motorWakeLatencyMs = 0L
        } else {
            supportsAmplitude = vib.hasAmplitudeControl()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val res = vib.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                )
                supportsThud = res[0]
                supportsLowTick = res[1]
                supportsTick = res[2]
                supportsClick = res[3]
                supportsQuickRise = res[4]
                supportsSlowRise = res[5]
                supportsQuickFall = res[6]
                supportsSpin = res[7]
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val res = vib.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                )
                supportsThud = false
                supportsLowTick = false
                supportsTick = res[0]
                supportsClick = res[1]
                supportsQuickRise = res[2]
                supportsSlowRise = res[3]
                supportsQuickFall = res[4]
                supportsSpin = false
            } else {
                supportsThud = false
                supportsLowTick = false
                supportsTick = false
                supportsClick = false
                supportsQuickRise = false
                supportsSlowRise = false
                supportsQuickFall = false
                supportsSpin = false
            }

            motorTier = when {
                supportsThud || supportsLowTick || (supportsClick && supportsTick) -> HapticMotorTier.HD_PRIMITIVES
                supportsAmplitude -> HapticMotorTier.AMPLITUDE_ENVELOPE
                else -> HapticMotorTier.BASIC_PULSE
            }
            motorWakeLatencyMs = when (motorTier) {
                HapticMotorTier.HD_PRIMITIVES -> 8L
                HapticMotorTier.AMPLITUDE_ENVELOPE -> 12L
                HapticMotorTier.BASIC_PULSE -> 18L
                HapticMotorTier.UNAVAILABLE -> 0L
            }
        }
        MusicHapticsTelemetry.updateMotorTier(motorTier)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Initialized motorTier=$motorTier amp=$supportsAmplitude wakeMs=$motorWakeLatencyMs " +
                    "thud=$supportsThud lowTick=$supportsLowTick tick=$supportsTick click=$supportsClick " +
                    "qRise=$supportsQuickRise sRise=$supportsSlowRise qFall=$supportsQuickFall spin=$supportsSpin",
            )
        }
    }

    fun updateConfig(newConfig: MusicHapticsConfig) {
        val wasEnabled = config.enabled
        config = newConfig
        if (wasEnabled && !newConfig.enabled) {
            cancelPending()
            MusicHapticsTelemetry.updateActive(false)
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        playing = isPlaying
        if (!isPlaying) {
            cancelPending()
            MusicHapticsTelemetry.updateActive(false)
        }
    }

    fun cancelPending() {
        if (released) return
        handler.removeCallbacksAndMessages(EVENT_TOKEN)
        handler.post {
            motorBusyUntilMs = 0L
            activePriority = 0
            try {
                vibrator?.cancel()
            } catch (_: Throwable) {
            }
        }
    }

    fun schedule(event: HapticBeatEvent) {
        if (released || !playing || motorTier == HapticMotorTier.UNAVAILABLE) return
        val cfg = config
        if (!cfg.enabled) return
        if (cfg.pauseWhenScreenOff && powerManager?.isInteractive == false) return

        val clampedDelay = event.delayMs.coerceIn(0L, MAX_SCHEDULE_DELAY_MS)
        val targetUptime = SystemClock.uptimeMillis() + clampedDelay
        handler.postAtTime(
            {
                if (!released && playing && config.enabled) {
                    if (!config.pauseWhenScreenOff || powerManager?.isInteractive != false) {
                        renderEvent(event, config)
                    }
                }
            },
            EVENT_TOKEN,
            targetUptime,
        )
    }

    /**
     * Plays a rich multi-articulation groove preview showcasing Kick, Hi-Hat, Vocal,
     * Snare, Clap, Crash Build, and Sub Drop with the user's live tuning.
     */
    fun playTestGroove(testConfig: MusicHapticsConfig = config) {
        if (released || motorTier == HapticMotorTier.UNAVAILABLE) return
        handler.removeCallbacksAndMessages(PREVIEW_TOKEN)
        val base = SystemClock.uptimeMillis()
        val steps = listOf(
            0L to HapticBeatEvent(HapticEventKind.KICK_PUNCH, 0.88f, 0.85f, 30, 0L),
            140L to HapticBeatEvent(HapticEventKind.HIHAT_SHIMMER, 0.55f, 0.10f, 10, 0L),
            260L to HapticBeatEvent(HapticEventKind.VOCAL_SYLLABLE, 0.65f, 0.20f, 34, 0L),
            440L to HapticBeatEvent(HapticEventKind.SNARE_CRACK, 0.85f, 0.40f, 24, 0L),
            640L to HapticBeatEvent(HapticEventKind.CLAP_SNAP, 0.78f, 0.25f, 26, 0L),
            840L to HapticBeatEvent(HapticEventKind.CRASH_BUILD, 0.72f, 0.40f, 56, 0L),
            1060L to HapticBeatEvent(HapticEventKind.SUB_DROP, 1.00f, 1.00f, 54, 0L),
            1240L to HapticBeatEvent(HapticEventKind.BASS_GROOVE, 0.65f, 0.90f, 64, 0L),
        )
        for ((offsetMs, evt) in steps) {
            handler.postAtTime(
                {
                    if (!released) {
                        motorBusyUntilMs = 0L
                        activePriority = 0
                        renderEvent(evt, testConfig, isPreview = true)
                    }
                },
                PREVIEW_TOKEN,
                base + offsetMs,
            )
        }
    }

    fun release() {
        if (released) return
        released = true
        playing = false
        handler.removeCallbacksAndMessages(null)
        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
        thread.quitSafely()
        MusicHapticsTelemetry.updateActive(false)
    }

    private fun priorityOf(kind: HapticEventKind): Int = when (kind) {
        HapticEventKind.SUB_DROP, HapticEventKind.KICK_PUNCH -> 3
        HapticEventKind.SNARE_CRACK, HapticEventKind.CLAP_SNAP -> 2
        HapticEventKind.CRASH_BUILD, HapticEventKind.VOCAL_SYLLABLE -> 1
        HapticEventKind.BASS_GROOVE, HapticEventKind.VOCAL_SWELL, HapticEventKind.HIHAT_SHIMMER -> 0
    }

    private fun renderEvent(
        event: HapticBeatEvent,
        cfg: MusicHapticsConfig,
        isPreview: Boolean = false,
    ) {
        val vib = vibrator ?: return
        val now = SystemClock.uptimeMillis()
        val prio = priorityOf(event.kind)

        if (!isPreview) {
            if (now < motorBusyUntilMs && prio <= activePriority) return
            val minGapMs = when (prio) {
                3 -> MIN_GAP_KICK_MS
                2 -> MIN_GAP_SNARE_MS
                1 -> MIN_GAP_VOCAL_MS
                else -> MIN_GAP_TEXTURE_MS
            }
            if (now - lastFireUptimeMs < minGapMs) return
        }

        val rawScale = (event.strength.coerceIn(0.05f, 1.0f).pow(0.70f) * cfg.intensity.coerceIn(0.15f, 1.0f))
            .coerceIn(0.08f, 1.0f)

        val effect = buildEffect(event, rawScale, cfg.profile) ?: return
        vibrateWithAttributes(vib, effect)

        lastFireUptimeMs = now
        activePriority = prio
        val busyMs = when (event.kind) {
            HapticEventKind.SUB_DROP -> 46L
            HapticEventKind.KICK_PUNCH -> 30L
            HapticEventKind.SNARE_CRACK -> 24L
            HapticEventKind.CLAP_SNAP -> 26L
            HapticEventKind.CRASH_BUILD -> 44L
            HapticEventKind.VOCAL_SYLLABLE -> 26L
            HapticEventKind.BASS_GROOVE -> 44L
            HapticEventKind.VOCAL_SWELL -> 40L
            HapticEventKind.HIHAT_SHIMMER -> 12L
        }
        motorBusyUntilMs = now + busyMs
    }

    private fun buildEffect(
        event: HapticBeatEvent,
        scale: Float,
        profile: MusicHapticsProfile,
    ): VibrationEffect? {
        // Continuous/curved vocal & bass textures feel silkier as amplitude waveforms when supported
        if (supportsAmplitude && (
                event.kind == HapticEventKind.VOCAL_SYLLABLE ||
                    event.kind == HapticEventKind.VOCAL_SWELL ||
                    event.kind == HapticEventKind.BASS_GROOVE
                )
        ) {
            return buildAmplitudeEnvelope(event, scale, profile)
        }

        // Tier 1: Rich LRA Primitive Compositions (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && motorTier == HapticMotorTier.HD_PRIMITIVES) {
            val primitiveEffect = buildPrimitiveComposition(event, scale, profile)
            if (primitiveEffect != null) return primitiveEffect
        }

        // Tier 2: Sculpted Multi-Segment Amplitude Envelopes (API 26+)
        if (supportsAmplitude) {
            return buildAmplitudeEnvelope(event, scale, profile)
        }

        // Tier 3: Basic ERM Motor Fallback — only fire tight rhythmic pulses on core beats
        if (event.kind == HapticEventKind.BASS_GROOVE ||
            event.kind == HapticEventKind.VOCAL_SWELL ||
            event.kind == HapticEventKind.HIHAT_SHIMMER ||
            event.kind == HapticEventKind.VOCAL_SYLLABLE
        ) {
            return null
        }
        val durationMs = when (event.kind) {
            HapticEventKind.SUB_DROP -> (22 + (scale * 16f).roundToInt()).toLong()
            HapticEventKind.KICK_PUNCH -> (14 + (scale * 12f).roundToInt()).toLong()
            HapticEventKind.SNARE_CRACK, HapticEventKind.CLAP_SNAP -> (11 + (scale * 9f).roundToInt()).toLong()
            HapticEventKind.CRASH_BUILD -> (16 + (scale * 12f).roundToInt()).toLong()
            else -> 12L
        }
        return VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun buildPrimitiveComposition(
        event: HapticBeatEvent,
        scale: Float,
        profile: MusicHapticsProfile,
    ): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val comp = VibrationEffect.startComposition()
        var added = false

        when (event.kind) {
            HapticEventKind.SUB_DROP -> {
                val s = scale.coerceIn(0.38f, 1.0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsThud) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                    added = true
                    if (supportsQuickFall && profile != MusicHapticsProfile.CRISP_PERCUSSION) {
                        comp.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                            (s * 0.72f).coerceIn(0.18f, 1.0f),
                            14,
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick) {
                        comp.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                            (s * 0.65f).coerceIn(0.16f, 1.0f),
                            16,
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s)
                    added = true
                    if (supportsClick) {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 0.65f).coerceIn(0.16f, 1.0f), 12)
                    }
                } else if (supportsClick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                    added = true
                }
            }

            HapticEventKind.KICK_PUNCH -> {
                val s = scale.coerceIn(0.24f, 1.0f)
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsThud -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, s)
                        added = true
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s)
                        added = true
                    }
                    supportsClick -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                        added = true
                    }
                }
            }

            HapticEventKind.SNARE_CRACK -> {
                val s = (scale * 0.92f).coerceIn(0.20f, 1.0f)
                when {
                    supportsClick -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                        added = true
                        if (supportsTick) {
                            comp.addPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_TICK,
                                (s * 0.55f).coerceIn(0.14f, 0.85f),
                                6,
                            )
                        }
                    }
                    supportsTick -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, s)
                        added = true
                    }
                }
            }

            HapticEventKind.CLAP_SNAP -> {
                // Realistic twin-impulse flam mimicking a layered handclap
                val s = (scale * 0.85f).coerceIn(0.18f, 0.95f)
                if (supportsTick && supportsClick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (s * 0.65f).coerceIn(0.14f, 0.85f))
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s, 10)
                    added = true
                } else if (supportsTick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, s)
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (s * 0.72f).coerceIn(0.12f, 0.85f), 11)
                    added = true
                } else if (supportsClick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                    added = true
                }
            }

            HapticEventKind.HIHAT_SHIMMER -> {
                val s = (scale * 0.50f).coerceIn(0.10f, 0.62f)
                if (supportsTick) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, s)
                    added = true
                }
            }

            HapticEventKind.CRASH_BUILD -> {
                val s = (scale * 0.72f).coerceIn(0.18f, 0.88f)
                when {
                    supportsQuickRise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsSpin -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, s)
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, (s * 0.65f).coerceIn(0.15f, 0.80f), 14)
                        added = true
                    }
                    supportsSlowRise -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, s)
                        added = true
                    }
                    supportsQuickRise -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, s)
                        added = true
                    }
                }
            }

            HapticEventKind.VOCAL_SYLLABLE -> {
                val s = (scale * 0.52f).coerceIn(0.12f, 0.65f)
                when {
                    supportsQuickRise -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, s)
                        added = true
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsLowTick -> {
                        comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s)
                        added = true
                    }
                }
            }

            HapticEventKind.BASS_GROOVE, HapticEventKind.VOCAL_SWELL -> {
                val s = (scale * 0.54f).coerceIn(0.12f, 0.70f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsSpin) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, s)
                    added = true
                } else if (supportsSlowRise) {
                    comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, s)
                    added = true
                }
            }
        }

        return if (added) comp.compose() else null
    }

    private fun buildAmplitudeEnvelope(
        event: HapticBeatEvent,
        scale: Float,
        profile: MusicHapticsProfile,
    ): VibrationEffect {
        val peak = (scale * 255f).roundToInt().coerceIn(18, 255)
        val crisp = if (profile == MusicHapticsProfile.CRISP_PERCUSSION) 0.72f else 1.0f

        return when (event.kind) {
            HapticEventKind.SUB_DROP -> {
                val timings = longArrayOf(
                    8L,
                    (20L * crisp).toLong().coerceAtLeast(10L),
                    (18L * crisp).toLong().coerceAtLeast(10L),
                    14L,
                    10L,
                )
                val amps = intArrayOf(
                    (peak * 0.90f).roundToInt().coerceIn(18, 255),
                    peak,
                    (peak * 0.70f).roundToInt().coerceIn(14, 255),
                    (peak * 0.36f).roundToInt().coerceIn(8, 255),
                    (peak * 0.14f).roundToInt().coerceIn(5, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.KICK_PUNCH -> {
                val tailMs = (8L + (event.subBassEnergy * 10f).roundToInt())
                val timings = longArrayOf(8L, 13L, (tailMs * crisp).toLong().coerceAtLeast(7L))
                val amps = intArrayOf(
                    peak,
                    (peak * 0.62f).roundToInt().coerceIn(14, 255),
                    (peak * 0.20f).roundToInt().coerceIn(6, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.BASS_GROOVE -> {
                val groovePeak = (peak * 0.58f).roundToInt().coerceIn(16, 195)
                val timings = longArrayOf(12L, 20L, 20L, 14L)
                val amps = intArrayOf(
                    (groovePeak * 0.42f).roundToInt().coerceIn(8, 255),
                    groovePeak,
                    (groovePeak * 0.78f).roundToInt().coerceIn(10, 255),
                    (groovePeak * 0.28f).roundToInt().coerceIn(6, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.SNARE_CRACK -> {
                val timings = longArrayOf(7L, 10L, 7L)
                val amps = intArrayOf(
                    (peak * 0.95f).roundToInt().coerceIn(16, 255),
                    (peak * 0.48f).roundToInt().coerceIn(10, 255),
                    (peak * 0.15f).roundToInt().coerceIn(5, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.CLAP_SNAP -> {
                // Twin-impulse handclap flam: short pre-hit -> 6 ms gap -> crisp snap
                val timings = longArrayOf(8L, 6L, 11L, 7L)
                val amps = intArrayOf(
                    (peak * 0.62f).roundToInt().coerceIn(12, 255),
                    0,
                    (peak * 0.92f).roundToInt().coerceIn(16, 255),
                    (peak * 0.24f).roundToInt().coerceIn(6, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.VOCAL_SYLLABLE -> {
                val vocalPeak = (peak * 0.56f).roundToInt().coerceIn(14, 180)
                val timings = longArrayOf(9L, 16L, 10L)
                val amps = intArrayOf(
                    (vocalPeak * 0.45f).roundToInt().coerceIn(8, 255),
                    vocalPeak,
                    (vocalPeak * 0.30f).roundToInt().coerceIn(6, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.VOCAL_SWELL -> {
                val swellPeak = (peak * 0.44f).roundToInt().coerceIn(12, 150)
                val timings = longArrayOf(14L, 22L, 16L)
                val amps = intArrayOf(
                    (swellPeak * 0.42f).roundToInt().coerceIn(6, 255),
                    swellPeak,
                    (swellPeak * 0.35f).roundToInt().coerceIn(6, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }

            HapticEventKind.HIHAT_SHIMMER -> {
                val tickAmp = (peak * 0.48f).roundToInt().coerceIn(12, 160)
                VibrationEffect.createOneShot(9L, tickAmp)
            }

            HapticEventKind.CRASH_BUILD -> {
                val buildPeak = (peak * 0.72f).roundToInt().coerceIn(16, 215)
                val timings = longArrayOf(12L, 14L, 16L, 14L)
                val amps = intArrayOf(
                    (buildPeak * 0.35f).roundToInt().coerceIn(8, 255),
                    (buildPeak * 0.70f).roundToInt().coerceIn(12, 255),
                    buildPeak,
                    (buildPeak * 0.30f).roundToInt().coerceIn(8, 255),
                )
                VibrationEffect.createWaveform(timings, amps, -1)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateWithAttributes(vib: Vibrator, effect: VibrationEffect) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mediaVibrationAttributes != null) {
                vib.vibrate(effect, mediaVibrationAttributes)
            } else {
                vib.vibrate(effect, legacyAudioAttributes)
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "vibrate() failed: ${t.message}")
            }
        }
    }

    companion object {
        private const val TAG = "MusicHapticsEngine"
        private const val MAX_SCHEDULE_DELAY_MS = 900L
        private const val MIN_GAP_KICK_MS = 26L
        private const val MIN_GAP_SNARE_MS = 30L
        private const val MIN_GAP_VOCAL_MS = 38L
        private const val MIN_GAP_TEXTURE_MS = 46L
        private val EVENT_TOKEN = Any()
        private val PREVIEW_TOKEN = Any()

        @Suppress("DEPRECATION")
        private fun resolveVibrator(context: Context): Vibrator? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }
}

/**
 * Lightweight bridge allowing the UI settings screen to trigger a tactile groove
 * preview whether or not [com.music.bitchord.playback.PlaybackService] is running.
 */
object MusicHapticsRuntime {
    @Volatile
    private var attachedEngine: MusicHapticsEngine? = null

    fun attach(engine: MusicHapticsEngine?) {
        attachedEngine = engine
    }

    fun playTestGroove(context: Context, config: MusicHapticsConfig) {
        if (!MusicHapticsCapability.isSupported(context)) return
        val live = attachedEngine
        if (live != null) {
            live.playTestGroove(config)
            return
        }
        val standalone = MusicHapticsEngine(context)
        standalone.playTestGroove(config)
        Handler(Looper.getMainLooper()).postDelayed(
            { standalone.release() },
            1750L,
        )
    }
}
