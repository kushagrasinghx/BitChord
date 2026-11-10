/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.bitchord

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
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.utils.PreferenceStore
import java.util.concurrent.atomic.AtomicBoolean

enum class Haptic {
    Tick,

    Tap,

    Select,

    ToggleOn,

    ToggleOff,

    SkipNext,

    SkipPrevious,

    Resume,

    Pause,

    Expand,
}

class Haptics internal constructor(context: Context) {
    private val app = context.applicationContext

    fun play(haptic: Haptic) {
        HapticDevice.of(app)?.play(haptic)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}

private class Beat(val kind: Kind, val scale: Float, val gapMs: Long) {
    enum class Kind(

        val pulseMs: Long,

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

private class HapticDevice private constructor(
    private val vibrator: Vibrator,
    private val canCompose: Boolean,
    private val canScaleAmplitude: Boolean,
    private val systemHapticsEnabled: () -> Boolean,
) {
    private val compiled = HashMap<Haptic, VibrationEffect>()

    fun play(haptic: Haptic) {
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
        canCompose && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Primitives.compose(beats)
        canScaleAmplitude -> waveform(beats, coarse = false)
        else -> waveform(beats.outerTwo(), coarse = true)
    }

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
            VibrationEffect.createWaveform(timings, -1)
        } else {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        }
    }

    private fun coarsePulseMs(beat: Beat): Long =
        (beat.kind.pulseMs * 2.5f * (0.6f + 0.4f * beat.scale)).toLong().coerceIn(18, 40)

    companion object {
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

            val canCompose = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Primitives.supportedBy(vibrator)

            return HapticDevice(
                vibrator = vibrator,
                canCompose = canCompose,
                canScaleAmplitude = vibrator.hasAmplitudeControl(),
                systemHapticsEnabled = systemHapticsWatcher(app),
            )
        }

        @Suppress("DEPRECATION")
        private fun systemHapticsWatcher(app: Context): () -> Boolean {
            val resolver = app.contentResolver
            val uri = Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED)
            fun read() = Settings.System.getInt(
                resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0

            val enabled = AtomicBoolean(read())
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    enabled.set(read())
                }
            }
            runCatching { resolver.registerContentObserver(uri, false, observer) }
            return { enabled.get() && (PreferenceStore.get(EnableHapticFeedbackKey) ?: true) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private object Primitives {
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

        Beat.Kind.LowTick -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        } else {
            VibrationEffect.Composition.PRIMITIVE_TICK
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object TouchVibration {
    private val attributes: VibrationAttributes = VibrationAttributes.Builder()
        .setUsage(VibrationAttributes.USAGE_TOUCH)
        .build()

    fun send(vibrator: Vibrator, effect: VibrationEffect) {
        vibrator.vibrate(effect, attributes)
    }
}
