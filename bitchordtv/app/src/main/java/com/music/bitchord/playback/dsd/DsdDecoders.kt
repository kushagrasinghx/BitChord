package com.music.bitchord.playback.dsd

/**
 * Reads a DSD stream's bytes in the container's own layout and hands them on
 * interleaved and most-significant-bit first, whichever container and bit
 * order they came from. One [consume] per [readSize] bytes of file.
 */
class DsdRawSource(val format: DsdFormat) {
    private val normalized = ByteArray(8192)
    var bytePosition: Long = 0; private set
    val ended: Boolean get() = bytePosition == format.bytesPerChannel
    val filePosition: Long get() = format.position(bytePosition)
    val readSize: Int get() = if (ended) 0 else if (format.container == DsdContainer.DSF)
        format.blockBytes * format.channels
        else minOf(8192L / format.channels, format.bytesPerChannel - bytePosition).toInt() * format.channels

    fun seek(firstByte: Long) {
        require(firstByte in 0..format.bytesPerChannel)
        require(format.container != DsdContainer.DSF || firstByte == format.bytesPerChannel || firstByte % format.blockBytes == 0L)
        bytePosition = firstByte
    }

    fun consume(containerBytes: ByteArray, size: Int): DsdRawBlock {
        require(!ended && size == readSize && containerBytes.size >= size)
        val count = minOf((size / format.channels).toLong(), format.bytesPerChannel - bytePosition).toInt()
        val samples = minOf(count * 8L, format.sampleCount - bytePosition * 8)
        for (frame in 0 until count) for (channel in 0 until format.channels) {
            val index = if (format.container == DsdContainer.DSF) channel * format.blockBytes + frame else frame * format.channels + channel
            val value = containerBytes[index].toInt() and 255
            normalized[frame * format.channels + channel] =
                (if (format.leastSignificantBitFirst) Integer.reverse(value) ushr 24 else value).toByte()
        }
        return DsdRawBlock(normalized, count * format.channels, bytePosition * 8, samples).also { bytePosition += count }
    }
}

/** Interleaved, MSB-first bytes; only valid until the next read from the same source. */
class DsdRawBlock(val bytes: ByteArray, val size: Int, val firstSample: Long, val sampleCount: Long)

/** Native DSD -> 176.4 kHz float PCM. See dsd_decimator.cpp. */
class DsdBlockDecoder(val format: DsdFormat) : AutoCloseable {
    private var handle = create(format.channels, format.decimation, format.sampleCount, DsdFilter.coefficients(format))
    var outputFrame: Long = 0; private set
    val ended: Boolean get() = outputFrame == format.pcmFrames
    private var nextSample = 0L

    init { check(handle != 0L) { "DSD decoder could not start." } }

    fun reset(firstByte: Long, firstFrame: Long) {
        check(handle != 0L)
        require(firstFrame in 0..format.pcmFrames && firstByte in 0..format.prerollByte(firstFrame))
        reset(handle, firstByte, firstFrame)
        outputFrame = firstFrame
        nextSample = firstByte * 8
    }

    /** Decodes one block, or flushes the filter's tail when [block] is null. Returns frames written. */
    fun decode(block: DsdRawBlock?, output: FloatArray): Int {
        check(handle != 0L)
        if (ended) return 0
        if (block != null) {
            require(block.firstSample == nextSample && block.size in 1..8192 && block.size <= block.bytes.size)
            require(block.size % format.channels == 0 && block.sampleCount in 1..block.size / format.channels * 8L)
            require(block.sampleCount == minOf(block.size / format.channels * 8L, format.sampleCount - nextSample))
        } else {
            require(nextSample >= format.sampleCount)
        }
        val frames = decode(handle, block?.bytes, block?.size ?: 0, output)
        check(frames >= 0) { "Invalid DSD decoder block." }
        outputFrame += frames
        nextSample += block?.sampleCount ?: 0
        return frames
    }

    override fun close() { if (handle != 0L) { destroy(handle); handle = 0 } }

    private external fun create(channels: Int, decimation: Int, samples: Long, coefficients: DoubleArray): Long
    private external fun reset(handle: Long, byte: Long, frame: Long)
    private external fun decode(handle: Long, bytes: ByteArray?, size: Int, output: FloatArray): Int
    private external fun destroy(handle: Long)

    companion object { init { System.loadLibrary("bitchord_dsd") } }
}

/** Native DST (the lossless compression in SACD-ripped DFF) -> raw DSD, one 1/75 s frame at a time. */
class DstDecoder(val channels: Int) : AutoCloseable {
    private var handle = create(channels)

    init {
        require(channels in 1..2)
        check(handle != 0L) { "DST decoder could not start." }
    }

    val frameBytes: Int get() = 4704 * channels

    fun decode(input: ByteArray, output: ByteArray) {
        check(handle != 0L)
        require(input.size in 2..1_048_576 && output.size == frameBytes)
        val result = decode(handle, input, output)
        if (result == -2) throw UnsupportedOperationException("This DST segmentation mode is not supported.")
        require(result == frameBytes) { "Invalid DST frame." }
    }

    override fun close() { if (handle != 0L) { destroy(handle); handle = 0 } }

    private external fun create(channels: Int): Long
    private external fun decode(handle: Long, input: ByteArray, output: ByteArray): Int
    private external fun destroy(handle: Long)

    companion object { init { System.loadLibrary("bitchord_dst") } }
}

data class DstFrame(val offset: Long, val size: Int, val crc: Int? = null)

object DstFrameCrc {
    fun calculate(bytes: ByteArray): Int {
        var crc = 0
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and 255) shl 24)
            repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x80000011.toInt() else crc shl 1 }
        }
        return crc
    }
}
