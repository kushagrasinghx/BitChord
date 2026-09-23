package com.music.bitchord.playback.spatializer

import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The spatializer's 6 x 2 binaural room impulse responses at one sample rate: how each virtual speaker
 * (L R C LFE Ls Rs) reaches the left and right ear in the listening room.
 *
 * Shipped as `assets/spatializer/speakers_<rate>.bin` for 44.1 and 48 kHz, built from Meta's SS2 head-and-torso
 * HRTFs (Meta Reality Labs Research, CC BY 4.0) and a synthetic room; see `docs/stereo-spatialization.md`. Other
 * rates are resampled from the nearest family on first use.
 */
class SpeakerResponses(val sampleRate: Int, val taps: Int, private val data: FloatArray) {
    init { require(data.size == INPUTS * EARS * taps) }

    fun response(input: Int, ear: Int): FloatArray =
        data.copyOfRange((input * EARS + ear) * taps, (input * EARS + ear + 1) * taps)

    /** Partition spectra for a convolution block size, shared by every spatializer at this rate. */
    class Spectra(val block: Int, val partitions: Int) {
        val re = Array(SpeakerResponses.INPUTS) { Array(SpeakerResponses.EARS) { Array(partitions) { FloatArray(block + 1) } } }
        val im = Array(SpeakerResponses.INPUTS) { Array(SpeakerResponses.EARS) { Array(partitions) { FloatArray(block + 1) } } }
        /** Leading partitions that carry energy, per (input, ear); the rest are skipped. */
        val activePartitions = Array(SpeakerResponses.INPUTS) { IntArray(SpeakerResponses.EARS) }
    }

    private val spectraCache = HashMap<Int, Spectra>()

    @Synchronized
    fun spectra(block: Int): Spectra = spectraCache.getOrPut(block) {
        val parts = (taps + block - 1) / block
        val s = Spectra(block, parts)
        val fft = RealFft(2 * block)
        val frame = FloatArray(2 * block)
        for (c in 0 until INPUTS) for (e in 0 until EARS) {
            val base = (c * EARS + e) * taps
            var last = 0
            for (p in 0 until parts) {
                frame.fill(0f)
                val n = min(block, taps - p * block)
                var nonzero = false
                for (i in 0 until n) {
                    val v = data[base + p * block + i]
                    frame[i] = v
                    if (v != 0f) nonzero = true
                }
                if (nonzero) last = p + 1
                fft.forward(frame, s.re[c][e][p], s.im[c][e][p])
            }
            s.activePartitions[c][e] = last
        }
        s
    }

    /**
     * Kaiser-windowed sinc resampling of every response to [target] Hz (band-limited when going down).
     *
     * The responses keep their frequency response, not their sample values: at twice the rate the same response
     * spans twice the taps, so every tap is scaled by `sampleRate / target`.
     *
     * Polyphase: for the rational ratio `up / down` (in lowest terms) the read position of output tap n,
     * `n * down / up`, has only `up` distinct fractional parts, so the windowed-sinc kernel is computed once per
     * phase and each output tap is a plain dot product. That keeps this cheap enough to run where the first block
     * at a new rate needs it (tens of milliseconds even at 192 kHz, rather than seconds).
     */
    fun resampledTo(target: Int): SpeakerResponses {
        if (target == sampleRate) return this
        val ratio = target.toDouble() / sampleRate
        val outTaps = ceil(taps * ratio).toInt()
        val cutoff = min(1.0, ratio)
        val half = ceil(HALF_WIDTH / cutoff).toInt()
        val width = 2 * half + 2
        val scale = sampleRate.toDouble() / target
        val g = gcd(target, sampleRate)
        val up = target / g
        val down = sampleRate / g
        val i0Beta = besselI0(KAISER_BETA)
        // weight of the input tap at distance x (input samples) from the read position
        fun weight(x: Double): Double {
            val u = x / (half + 1)
            if (abs(u) >= 1.0) return 0.0
            val arg = PI * cutoff * x
            val sinc = if (abs(arg) < 1e-12) 1.0 else sin(arg) / arg
            return scale * cutoff * sinc * besselI0(KAISER_BETA * sqrt(1 - u * u)) / i0Beta
        }
        val out = FloatArray(INPUTS * EARS * outTaps)
        if (up <= MAX_PHASES) {
            // kernel[p][j]: weight of input tap floor(pos) - half + j when frac(pos) = p / up
            val kernel = Array(up) { p -> DoubleArray(width) { j -> weight(p.toDouble() / up + half - j) } }
            for (ch in 0 until INPUTS * EARS) {
                val src = ch * taps
                val dst = ch * outTaps
                for (n in 0 until outTaps) {
                    val pos = n.toLong() * down
                    val k0 = (pos / up).toInt() - half
                    val w = kernel[(pos % up).toInt()]
                    val j0 = maxOf(0, -k0)
                    val j1 = minOf(width, taps - k0)
                    var acc = 0.0
                    for (j in j0 until j1) acc += data[src + k0 + j] * w[j]
                    out[dst + n] = acc.toFloat()
                }
            }
        } else {
            // an unusual ratio with too many phases to tabulate: evaluate the kernel per tap
            for (ch in 0 until INPUTS * EARS) {
                val src = ch * taps
                val dst = ch * outTaps
                for (n in 0 until outTaps) {
                    val center = n / ratio
                    val k0 = floor(center).toInt() - half
                    var acc = 0.0
                    for (k in maxOf(0, k0) until minOf(taps, k0 + width)) acc += data[src + k] * weight(center - k)
                    out[dst + n] = acc.toFloat()
                }
            }
        }
        return SpeakerResponses(target, outTaps, out)
    }

    companion object {
        const val INPUTS = 6
        const val EARS = 2
        private const val MAGIC = 0x50534342 // "BCSP" little-endian
        private const val HALF_WIDTH = 32.0
        private const val KAISER_BETA = 8.6
        private const val MAX_PHASES = 1024

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        /**
         * Parses the asset format, little-endian: `"BCSP"`, u32 version (1), u32 sample rate, u32 inputs (6),
         * u32 ears (2), u32 taps, then float32 `[inputs][ears][taps]`.
         */
        fun read(input: InputStream): SpeakerResponses {
            val bytes = DataInputStream(input).use { it.readBytes() }
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bb.int == MAGIC) { "not a spatializer response file" }
            val version = bb.int
            require(version == 1) { "unsupported spatializer response version $version" }
            val rate = bb.int
            val inputs = bb.int
            val ears = bb.int
            val taps = bb.int
            require(inputs == INPUTS && ears == EARS) { "expected 6 x 2 responses, got $inputs x $ears" }
            val data = FloatArray(inputs * ears * taps)
            bb.asFloatBuffer().get(data)
            return SpeakerResponses(rate, taps, data)
        }

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val q = x * x / 4
            var k = 1
            while (term > 1e-12 * sum) {
                term *= q / (k.toDouble() * k)
                sum += term
                k++
            }
            return sum
        }
    }
}

/**
 * Process-wide cache of [SpeakerResponses] per sample rate, loaded from the app's assets. [init] must run before the
 * first spatialized block (PlaybackService does it in onCreate); until then — and for rates outside
 * [MIN_RATE]..[MAX_RATE] — [forRate] returns null and the spatial processor falls back to stereo widening.
 */
object SpeakerResponseStore {
    private const val TAG = "SpeakerResponseStore"
    const val MIN_RATE = 22050
    const val MAX_RATE = 192000

    @Volatile
    private var context: Context? = null
    private val byRate = HashMap<Int, SpeakerResponses>()
    private val shipped = HashMap<Int, SpeakerResponses>()

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** For tests: supply the shipped responses directly instead of reading them from assets. */
    @Synchronized
    fun install(responses: SpeakerResponses) {
        shipped[responses.sampleRate] = responses
        byRate.clear()
    }

    @Synchronized
    fun forRate(sampleRate: Int): SpeakerResponses? {
        if (sampleRate !in MIN_RATE..MAX_RATE) return null
        byRate[sampleRate]?.let { return it }
        // 44.1 kHz family for multiples of 11025, 48 kHz family otherwise
        val family = if (sampleRate % 11025 == 0) 44100 else 48000
        val base = shippedFor(family) ?: return null
        val responses = try {
            base.resampledTo(sampleRate)
        } catch (t: Throwable) {
            Log.w(TAG, "resampling spatializer responses to $sampleRate Hz failed", t)
            return null
        }
        byRate[sampleRate] = responses
        return responses
    }

    private fun shippedFor(rate: Int): SpeakerResponses? {
        shipped[rate]?.let { return it }
        val ctx = context ?: return null
        return try {
            ctx.assets.open("spatializer/speakers_$rate.bin").use { SpeakerResponses.read(it) }
                .also { shipped[rate] = it }
        } catch (t: Throwable) {
            Log.w(TAG, "spatializer responses for $rate Hz unavailable", t)
            null
        }
    }
}
