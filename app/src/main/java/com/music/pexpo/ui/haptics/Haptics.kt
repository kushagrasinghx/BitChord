package com.music.pexpo.ui.haptics

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What a touch *meant*, not what it should feel like — the shape of the buzz is
 * this file's business, so a screen never has to know what the motor under it
 * can do.
 *
 * Everything here is deliberately brief. The longest pattern is a three-beat
 * one under 65ms; a haptic that outlasts the finger stops reading as a response
 * to the tap and starts reading as the phone ringing.
 */
enum class Haptic {
    /**
     * The lightest single beat, for something that repeats while a finger is
     * still down — a drag crossing a tab boundary, say. Anything firmer becomes
     * a rattle once it fires ten times in a row.
     */
    Tick,

    /** A plain button press with no state behind it: More, Download, Menu. */
    Tap,

    /** A discrete choice landing: a tab, a filter pill, the end of a scrub. */
    Select,

    /** Switching something on — a light lead-in *rising* into a firm beat. */
    ToggleOn,

    /** Switching it back off — the same pair mirrored, so it falls away. */
    ToggleOff,

    /** Forward through the queue: an accelerating triplet. */
    SkipNext,

    /** Backward: [SkipNext] reversed, which is what makes the pair legible. */
    SkipPrevious,

    /** Playback starting — swells into the beat that lands. */
    Resume,

    /** Playback stopping — lands first, then releases. */
    Pause,

    /** Something growing to fill the screen, e.g. the mini player opening. */
    Expand,
}

/**
 * A handle on the device's motor, obtained with [rememberHaptics].
 *
 * Cheap to hold and cheap to call: the capability probe and the compiled
 * [VibrationEffect]s live in [HapticDevice], one set for the whole process.
 */
class Haptics internal constructor(context: Context) {
    private val app = context.applicationContext

    fun play(haptic: Haptic) {
        HapticDevice.of(app)?.play(haptic)
    }
}

/** `val haptics = rememberHaptics()`, then `haptics.play(Haptic.Select)`. */
@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}

// ── The rhythms ───────────────────────────────────────────────────────────────

/**
 * One beat of a pattern: which of the three short primitives to strike, how
 * hard relative to that primitive's nominal strength, and how long to wait
 * after the previous beat before striking it.
 *
 * Only the three genuinely *short* primitives are used. The platform also
 * offers rises, falls, thuds and a spin, and all of them run 80–500ms — long
 * enough that a two-beat pattern built from them would still be vibrating well
 * after the screen had finished responding.
 */
private class Beat(val kind: Kind, val scale: Float, val gapMs: Long) {
    enum class Kind(
        /** Roughly how long the primitive itself lasts, for the waveform tiers. */
        val pulseMs: Long,
        /** Its nominal amplitude, before [Beat.scale]. */
        val amplitude: Int,
    ) {
        Tick(8, 110),
        LowTick(10, 95),
        Click(14, 210),
    }
}

private fun rhythmOf(haptic: Haptic): List<Beat> = when (haptic) {
    Haptic.Tick -> listOf(Beat(Beat.Kind.Tick, 0.35f, 0))
    Haptic.Tap -> listOf(Beat(Beat.Kind.Click, 0.5f, 0))
    Haptic.Select -> listOf(
        Beat(Beat.Kind.Tick, 0.4f, 0),
        Beat(Beat.Kind.Click, 0.75f, 18),
    )
    Haptic.ToggleOn -> listOf(
        Beat(Beat.Kind.Tick, 0.4f, 0),
        Beat(Beat.Kind.Click, 0.9f, 14),
    )
    Haptic.ToggleOff -> listOf(
        Beat(Beat.Kind.Click, 0.75f, 0),
        Beat(Beat.Kind.Tick, 0.3f, 14),
    )
    Haptic.SkipNext -> listOf(
        Beat(Beat.Kind.Tick, 0.4f, 0),
        Beat(Beat.Kind.Tick, 0.55f, 16),
        Beat(Beat.Kind.Click, 0.7f, 16),
    )
    Haptic.SkipPrevious -> listOf(
        Beat(Beat.Kind.Click, 0.7f, 0),
        Beat(Beat.Kind.Tick, 0.55f, 16),
        Beat(Beat.Kind.Tick, 0.4f, 16),
    )
    Haptic.Resume -> listOf(
        Beat(Beat.Kind.LowTick, 0.5f, 0),
        Beat(Beat.Kind.Click, 0.85f, 22),
    )
    Haptic.Pause -> listOf(
        Beat(Beat.Kind.Click, 0.85f, 0),
        Beat(Beat.Kind.LowTick, 0.4f, 22),
    )
    Haptic.Expand -> listOf(
        Beat(Beat.Kind.Tick, 0.3f, 0),
        Beat(Beat.Kind.Tick, 0.45f, 12),
        Beat(Beat.Kind.Click, 0.6f, 12),
    )
}

// ── The motor ─────────────────────────────────────────────────────────────────

/**
 * Resolves what this particular phone can do once, then renders every [Haptic]
 * into the best [VibrationEffect] available to it:
 *
 *  1. **Composition** (API 30+, primitives supported). Real rhythmic haptics —
 *     the beats are handed to the vibrator as primitives and it reproduces
 *     their character, not just their timing. This is the Pixel / recent
 *     Samsung path and what the patterns above were written for.
 *  2. **Waveform with amplitudes** (API 26+, `hasAmplitudeControl`). The same
 *     rhythm as on-pulses of varying strength. Cruder, still clearly a pattern
 *     rather than a buzz.
 *  3. **Plain waveform**. An on/off pattern on a motor with one volume, so only
 *     the timing survives — and the pulses have to be longer to be felt at all,
 *     which is why this tier drops a three-beat pattern to its two outer beats
 *     rather than letting the total run past ~80ms.
 *
 * Effects are immutable, so each one is compiled on first use and kept.
 */
private class HapticDevice private constructor(
    private val vibrator: Vibrator,
    private val canCompose: Boolean,
    private val canScaleAmplitude: Boolean,
    private val systemHapticsEnabled: () -> Boolean,
) {
    private val compiled = HashMap<Haptic, VibrationEffect>()

    fun play(haptic: Haptic) {
        // The system-wide touch-feedback switch is the user's answer to this
        // whole feature, and going through Vibrator rather than the View means
        // nothing else is checking it for us.
        if (!systemHapticsEnabled()) return

        val effect = synchronized(compiled) {
            compiled.getOrPut(haptic) { compile(rhythmOf(haptic)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TouchVibration.send(vibrator, effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, LEGACY_ATTRIBUTES)
        }
    }

    private fun compile(beats: List<Beat>): VibrationEffect = when {
        // [canCompose] already implies API 30 — see the probe. The version check
        // is repeated because it's the only form lint can follow, and a
        // suppression here would hide a real mistake later.
        canCompose && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Primitives.compose(beats)
        canScaleAmplitude -> waveform(beats, coarse = false)
        else -> waveform(beats.outerTwo(), coarse = true)
    }

    /**
     * Timings and amplitudes alternate gap / pulse, so a beat contributes two
     * entries and a leading gap of 0 is harmless.
     */
    private fun waveform(beats: List<Beat>, coarse: Boolean): VibrationEffect {
        val timings = LongArray(beats.size * 2)
        val amplitudes = IntArray(beats.size * 2)
        beats.forEachIndexed { i, beat ->
            timings[i * 2] = if (coarse) beat.gapMs.coerceAtLeast(if (i == 0) 0 else 20) else beat.gapMs
            timings[i * 2 + 1] = if (coarse) coarsePulseMs(beat) else beat.kind.pulseMs
            amplitudes[i * 2] = 0
            amplitudes[i * 2 + 1] = (beat.kind.amplitude * beat.scale).toInt().coerceIn(1, 255)
        }
        return if (coarse) {
            // One volume only: the amplitude array would be a lie, and the
            // two-argument form already means "off for this long, on for that".
            VibrationEffect.createWaveform(timings, -1)
        } else {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        }
    }

    /**
     * An ERM motor needs ~20ms just to spin up, so the primitive-length pulses
     * above would land as nothing. Stretched to something felt, floored at 18ms.
     */
    private fun coarsePulseMs(beat: Beat): Long =
        (beat.kind.pulseMs * 2.5f * (0.6f + 0.4f * beat.scale)).toLong().coerceIn(18, 40)

    companion object {
        /** First and last beat, which for a mirrored pair keeps the mirror. */
        private fun List<Beat>.outerTwo(): List<Beat> =
            if (size > 2) listOf(first(), last()) else this

        private val LEGACY_ATTRIBUTES by lazy {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        @Volatile
        private var instance: HapticDevice? = null

        @Volatile
        private var probed = false

        /**
         * Null on a phone with no vibrator at all, which is a legitimate answer
         * and not worth re-checking on every tap.
         */
        fun of(app: Context): HapticDevice? {
            instance?.let { return it }
            synchronized(this) {
                if (probed) return instance
                probed = true
                instance = probe(app)
                return instance
            }
        }

        private fun probe(app: Context): HapticDevice? {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                app.getSystemService(Vibrator::class.java)
            }
            if (vibrator == null || !vibrator.hasVibrator()) return null

            // Claiming API 30 isn't enough — plenty of phones on 30+ have an
            // ERM motor that supports no primitives, and asking for a
            // composition there produces silence rather than a fallback.
            val canCompose = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Primitives.supportedBy(vibrator)

            return HapticDevice(
                vibrator = vibrator,
                canCompose = canCompose,
                canScaleAmplitude = vibrator.hasAmplitudeControl(),
                systemHapticsEnabled = systemHapticsWatcher(app),
            )
        }

        /**
         * Reads Settings.System once and then only when it changes, rather than
         * making a binder call on every tap.
         *
         * The key is marked deprecated and has no public replacement: it is
         * still what the Settings app writes for "Touch feedback" and still the
         * only readable answer. From API 33 the USAGE_TOUCH attribute means the
         * platform applies the same preference itself, so there this check is
         * belt-and-braces rather than the only thing honouring it.
         */
        @Suppress("DEPRECATION")
        private fun systemHapticsWatcher(app: Context): () -> Boolean {
            val resolver = app.contentResolver
            val uri = Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED)
            fun read() = Settings.System.getInt(
                resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0

            // Atomic because the observer fires on a binder thread and the read
            // happens on whichever thread just handled a tap.
            val enabled = AtomicBoolean(read())
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    enabled.set(read())
                }
            }
            runCatching { resolver.registerContentObserver(uri, false, observer) }
            return { enabled.get() }
        }
    }
}

/**
 * Everything that touches [VibrationEffect.Composition], kept in a class of its
 * own so that class — which does not exist below API 30 — is only ever *loaded*
 * on a device that has it. Gating the call sites would very likely be enough on
 * its own; keeping the references out of [HapticDevice] entirely means it can't
 * come down to how eagerly a particular runtime resolves them.
 */@RequiresApi(Build.VERSION_CODES.R)
private object Primitives {
    /**
     * Only the two primitives that exist on API 30 are checked, because they're
     * the only two ever asked for there — see [primitive].
     */
    fun supportedBy(vibrator: Vibrator): Boolean = vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    )

    fun compose(beats: List<Beat>): VibrationEffect {
        var composition = VibrationEffect.startComposition()
        beats.forEach { beat ->
            composition = composition.addPrimitive(
                beat.kind.primitive(),
                beat.scale,
                beat.gapMs.toInt(),
            )
        }
        return composition.compose()
    }

    private fun Beat.Kind.primitive(): Int = when (this) {
        Beat.Kind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
        Beat.Kind.Click -> VibrationEffect.Composition.PRIMITIVE_CLICK
        // LOW_TICK only became public API in 31; below that a plain tick is the
        // nearest thing, and the pattern still reads correctly without it.
        Beat.Kind.LowTick -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        } else {
            VibrationEffect.Composition.PRIMITIVE_TICK
        }
    }
}

/**
 * Tells the platform this buzz is touch feedback, which is what lets the system
 * scale or mute it alongside every other tap in the OS.
 *
 * Held by an object for the same reason as [Primitives] — [VibrationAttributes]
 * arrived in API 30, and the two-argument `vibrate` in 33 — so neither type is
 * named anywhere that loads on an older phone. A Kotlin `object` initialises on
 * first access, which makes this the cache as well.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object TouchVibration {
    private val attributes: VibrationAttributes = VibrationAttributes.Builder()
        .setUsage(VibrationAttributes.USAGE_TOUCH)
        .build()

    fun send(vibrator: Vibrator, effect: VibrationEffect) {
        vibrator.vibrate(effect, attributes)
    }
}
