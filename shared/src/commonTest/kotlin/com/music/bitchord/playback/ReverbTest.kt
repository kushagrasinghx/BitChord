package com.music.bitchord.playback

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReverbTest {

    private val rate = 48_000

    private fun processor(channels: Int = 2, enabled: Boolean = true, settings: ReverbSettings = ReverbSettings.DEFAULT) =
        ReverbProcessor().apply {
            configure(rate, channels)
            setTarget(enabled, settings)
        }

    private fun noise(frames: Int, channels: Int = 2, seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(frames * channels) { random.nextFloat() * 0.5f - 0.25f }
    }

    private fun rms(samples: FloatArray, from: Int = 0): Double {
        var sum = 0.0
        for (index in from until samples.size) sum += samples[index].toDouble() * samples[index]
        return sqrt(sum / (samples.size - from))
    }

    /** Runs [samples] through in blocks the size a sink hands over. */
    private fun ReverbProcessor.run(samples: FloatArray, channels: Int = 2, blockFrames: Int = 512) {
        val block = FloatArray(blockFrames * channels)
        var at = 0
        while (at < samples.size) {
            val count = minOf(block.size, samples.size - at)
            samples.copyInto(block, 0, at, at + count)
            process(block, count)
            block.copyInto(samples, at, 0, count)
            at += count
        }
    }

    // ---- settings ----------------------------------------------------------

    @Test
    fun `settings survive a round trip through storage`() {
        val settings = ReverbSettings(decayMs = 3_100, preDelayMs = 45, dampingHz = 7_250, mix = 0.6f, width = 0.25f)
        assertEquals(settings, ReverbSettings.decode(settings.encode()))
    }

    @Test
    fun `nothing stored or garbage stored reads as the defaults`() {
        assertEquals(ReverbSettings.DEFAULT, ReverbSettings.decode(null))
        assertEquals(ReverbSettings.DEFAULT, ReverbSettings.decode(""))
        assertEquals(ReverbSettings.DEFAULT, ReverbSettings.decode("x,y,z,NaN,?"))
    }

    @Test
    fun `stored values are clamped and a short record keeps what it has`() {
        val decoded = ReverbSettings.decode("99999,-5,10,7")
        assertEquals(ReverbSettings.DECAY_RANGE_MS.last, decoded.decayMs)
        assertEquals(0, decoded.preDelayMs)
        assertEquals(ReverbSettings.DAMPING_RANGE_HZ.first, decoded.dampingHz)
        assertEquals(1f, decoded.mix)
        assertEquals(ReverbSettings.DEFAULT.width, decoded.width)
    }

    @Test
    fun `picking a preset changes the room but not the mix`() {
        val custom = ReverbSettings(decayMs = 999, mix = 0.8f, width = 0.4f)
        assertNull(custom.preset)
        val cathedral = custom.withPreset(ReverbPreset.CATHEDRAL)
        assertEquals(ReverbPreset.CATHEDRAL, cathedral.preset)
        assertEquals(0.8f, cathedral.mix)
        assertEquals(0.4f, cathedral.width)
        assertEquals(ReverbPreset.HALL, ReverbSettings.DEFAULT.preset)
    }

    // ---- processing --------------------------------------------------------

    @Test
    fun `switched off it leaves every sample untouched`() {
        val input = noise(4_096)
        val output = input.copyOf()
        processor(enabled = false).run(output)
        assertContentEquals(input, output)
    }

    @Test
    fun `an impulse comes back as a tail after the pre-delay`() {
        val settings = ReverbSettings(preDelayMs = 50, mix = 1f)
        val frames = rate // one second
        val samples = FloatArray(frames * 2).also { it[0] = 1f; it[1] = 1f }
        val reverb = processor(settings = settings)
        // Let the wet level glide all the way in first, so it is the tail being measured.
        reverb.run(FloatArray(rate / 2 * 2))
        reverb.reset()
        reverb.run(samples)

        val preDelayFrames = 50 * rate / 1_000
        val early = (2 until preDelayFrames * 2).maxOf { abs(samples[it]) }
        val late = (preDelayFrames * 2 until samples.size).maxOf { abs(samples[it]) }
        assertTrue(early < 1e-6f, "nothing before the pre-delay, got $early")
        assertTrue(late > 1e-3f, "a tail after it, got $late")
        // Still ringing half a second later: this is a hall, not an echo.
        assertTrue(rms(samples, from = samples.size / 2) > 1e-5)
    }

    @Test
    fun `rooms of very different size sit at about the same level`() {
        fun wetLevel(preset: ReverbPreset): Double {
            val samples = noise(rate * 2)
            processor(settings = ReverbSettings(mix = 1f).withPreset(preset)).run(samples)
            return rms(samples, from = samples.size / 2)
        }
        val input = rms(noise(rate * 2))
        val room = wetLevel(ReverbPreset.ROOM)
        val cathedral = wetLevel(ReverbPreset.CATHEDRAL)
        assertTrue(room / cathedral in 0.8..1.25, "room $room vs cathedral $cathedral")
        assertTrue(room / input in 0.5..1.5, "room $room vs input $input")
    }

    @Test
    fun `switching off mid-song fades out without a click and then passes audio through`() {
        val reverb = processor()
        val samples = noise(rate)
        reverb.run(samples)
        assertTrue(reverb.isActive)

        reverb.setTarget(false, ReverbSettings.DEFAULT)
        // A constant signal, so anything else in the output can only be the reverb's doing.
        val steady = FloatArray(rate / 2 * 2) { 0.2f }
        reverb.run(steady)
        assertFalse(reverb.isActive, "settled off within half a second")

        // Faded rather than cut: the first frames still carry the dry level the
        // mix had and some of the tail, a hard switch would have put out 0.2 at once.
        assertTrue(abs(steady[0] - 0.2f) > 0.01f, "first sample ${steady[0]}")
        assertEquals(0.2f, steady.last())
        // And from then on, bit-exact.
        val after = FloatArray(1_024) { 0.3f }
        reverb.run(after)
        assertTrue(after.all { it == 0.3f })
    }

    @Test
    fun `mono and surround layouts are processed without disturbing the extra channels' balance`() {
        val mono = FloatArray(4_096) { if (it == 0) 1f else 0f }
        processor(channels = 1).run(mono, channels = 1)
        assertTrue(mono.drop(1).any { it != 0f }, "mono gets a tail")

        val channels = 6
        val surround = FloatArray(rate / 4 * channels) { 0.5f }
        processor(channels = channels, settings = ReverbSettings(mix = 0.5f)).run(surround, channels = channels)
        val lastFrame = surround.size - channels
        // The rear and centre channels carry no tail: just the dry level, gliding to 1 - mix.
        for (channel in 2 until channels) {
            assertEquals(surround[lastFrame + 2], surround[lastFrame + channel])
        }
        assertTrue(abs(surround[lastFrame + 2] - 0.25f) < 0.01f)
    }

    @Test
    fun `no sample rate means no processing rather than a crash`() {
        val reverb = ReverbProcessor().apply {
            configure(0, 2)
            setTarget(true, ReverbSettings.DEFAULT)
        }
        val samples = noise(256)
        val copy = samples.copyOf()
        reverb.process(samples, samples.size)
        assertContentEquals(copy, samples)
    }
}
