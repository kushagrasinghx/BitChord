package com.music.bitchord.playback.dsd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DsdContainer { DSF, DFF }

/**
 * One DSD stream as its container describes it, and the PCM it decodes to.
 *
 * Every rate decimates to the same 176.4 kHz: high enough that the low-pass
 * can sit well above the audible band, low enough for any AudioTrack. DSF
 * stores each channel in its own 4096-byte block and interleaves the blocks;
 * DFF interleaves byte by byte — [position] is what hides the difference.
 */
data class DsdFormat(
    val container: DsdContainer,
    val bitRate: Int,
    val channels: Int,
    val sampleCount: Long,
    val dataOffset: Long,
    val dataBytes: Long,
    val leastSignificantBitFirst: Boolean = false,
    val blockBytes: Int = 4096,
) {
    init {
        require(supportsBitRate(bitRate)) { "Use DSD64 through DSD1024." }
        require(channels in 1..2) { "Use mono or stereo DSD." }
        require(sampleCount in 1..bitRate.toLong() * 86_400) { "Invalid DSD duration." }
        require(dataOffset >= 0 && dataBytes > 0 && dataOffset <= Long.MAX_VALUE - dataBytes) { "Invalid DSD data range." }
        require(blockBytes == 4096) { "Unsupported DSF block size." }
    }

    val pcmRate: Int get() = PCM_RATE
    val decimation: Int get() = bitRate / pcmRate
    val pcmFrames: Long get() = (sampleCount + decimation - 1) / decimation
    val durationUs: Long get() = sampleCount * 1_000_000L / bitRate
    val bytesPerChannel: Long get() = (sampleCount + 7) / 8
    val filterTaps: Int get() = tapsFor(bitRate)

    fun seekFrame(timeUs: Long): Long = if (timeUs > 0 && timeUs >= durationUs) pcmFrames
        else (timeUs.coerceAtLeast(0) * pcmRate / 1_000_000L).coerceAtMost(pcmFrames)

    /**
     * The first byte to feed the filter so that [frame] comes out right: half
     * the filter's length before it, since the taps reach that far back.
     */
    fun prerollByte(frame: Long): Long {
        require(frame in 0..pcmFrames)
        val first = ((frame * decimation - filterTaps / 2).coerceAtLeast(0) / 8).coerceAtMost(bytesPerChannel)
        return if (container == DsdContainer.DSF) first / blockBytes * blockBytes else first
    }

    fun position(bytePerChannel: Long): Long {
        require(bytePerChannel in 0..bytesPerChannel)
        return dataOffset + if (container == DsdContainer.DSF) bytePerChannel / blockBytes * blockBytes * channels
            else bytePerChannel * channels
    }

    companion object {
        /** What every DSD rate is decimated to. */
        const val PCM_RATE = 176_400

        /** Filter length for [bitRate]: 512 taps at DSD64, scaling with the rate. */
        fun tapsFor(bitRate: Int): Int = 512 * (bitRate / 2_822_400)

        val supportedBitRates: List<Int> = listOf(2_822_400, 5_644_800, 11_289_600, 22_579_200, 45_158_400)
        fun supportsBitRate(rate: Int): Boolean = rate in supportedBitRates
    }
}

object DsdHeaders {
    data class DffProperties(val rate: Int, val channels: Int, val dst: Boolean)

    fun dsf(format: ByteArray, dataOffset: Long, dataBytes: Long): DsdFormat {
        require(format.size >= 40) { "Incomplete DSF format." }
        val b = ByteBuffer.wrap(format).order(ByteOrder.LITTLE_ENDIAN)
        require(b.int == 1 && b.int == 0) { "Unsupported DSF format." }
        val layout = b.int
        val channels = b.int
        require(layout == channels && channels in 1..2) { "Use mono or stereo DSF." }
        val rate = b.int
        val bits = b.int
        require(bits == 1 || bits == 8) { "Invalid DSF bit order." }
        val samples = b.long
        val block = b.int
        require(b.int == 0) { "Invalid DSF reserved field." }
        val result = DsdFormat(DsdContainer.DSF, rate, channels, samples, dataOffset, dataBytes, bits == 1, block)
        val expected = ((result.bytesPerChannel + block - 1) / block) * block * channels
        require(dataBytes == expected) { "DSF sample count does not match its blocks." }
        return result
    }

    fun dffSoundProperties(payload: ByteArray): DffProperties {
        val b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        require(b.remaining() >= 4 && id(b) == "SND ") { "Invalid DFF sound properties." }
        var rate = 0
        var channels = 0
        var compression: String? = null
        var chunks = 0
        while (b.hasRemaining()) {
            require(++chunks <= 128 && b.remaining() >= 12) { "Invalid DFF property chunks." }
            val id = id(b)
            val size = b.long
            require(size >= 0 && size <= b.remaining() && size + (size and 1) <= b.remaining()) { "Truncated DFF property." }
            val end = b.position() + size.toInt()
            when (id) {
                "FS  " -> { require(rate == 0 && size == 4L); rate = b.int }
                "CHNL" -> {
                    require(channels == 0 && size >= 2)
                    channels = b.short.toInt() and 0xffff
                    require(channels in 1..2 && size == 2L + 4L * channels) { "Use mono or stereo DFF." }
                    val first = id(b)
                    if (channels == 2) require(first == "SLFT" && id(b) == "SRGT") { "Unsupported DFF channel layout." }
                }
                "CMPR" -> {
                    require(compression == null && size >= 5)
                    compression = id(b)
                    val nameBytes = b.get().toInt() and 255
                    val padding = size - 5 - nameBytes
                    require(padding == 0L || padding == 1L && size % 2 == 0L && payload[end - 1] == 0.toByte()) { "Invalid DFF compression name." }
                    require(compression == "DSD " || compression == "DST ") { "Unsupported DFF compression." }
                }
            }
            b.position(end + (size and 1).toInt())
        }
        require(DsdFormat.supportsBitRate(rate) && channels in 1..2 && compression != null) { "Incomplete or unsupported DFF properties." }
        require(compression != "DST " || rate == 2_822_400) { "DST currently requires DSD64." }
        return DffProperties(rate, channels, compression == "DST ")
    }

    fun id(buffer: ByteBuffer): String {
        require(buffer.remaining() >= 4)
        return CharArray(4) { (buffer.get().toInt() and 255).toChar() }.concatToString()
    }
}

/**
 * The decimation low-pass: a Blackman-windowed sinc with its corner at 48 kHz,
 * normalised to unity gain. Everything above it is DSD's noise-shaped hiss,
 * which climbs steeply past the audible band and would otherwise alias down
 * into it at 176.4 kHz.
 */
object DsdFilter {
    /** Where the low-pass sits; the pipeline dialog reports it. */
    const val CUTOFF_HZ = 48_000

    private val kernels = ConcurrentHashMap<Int, DoubleArray>()

    fun coefficients(format: DsdFormat): DoubleArray = kernels.computeIfAbsent(format.bitRate) { bitRate ->
        val taps = format.filterTaps
        DoubleArray(taps) { i ->
            val x = i - (taps - 1) / 2.0
            val cutoff = CUTOFF_HZ.toDouble() / bitRate
            val window = .42 - .5 * cos(2 * PI * i / (taps - 1)) + .08 * cos(4 * PI * i / (taps - 1))
            sin(2 * PI * cutoff * x) / (PI * x) * window
        }.also { values -> val sum = values.sum(); for (i in values.indices) values[i] /= sum }
    }
}
