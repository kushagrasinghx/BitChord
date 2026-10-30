package app.stemsep.dsp

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Writes 16-bit little-endian stereo PCM WAV. */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 44_100,
    private val channels: Int = 2,
    private val bitsPerSample: Int = 16,
) : FloatStereoSink, Closeable {
    private val output = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    private var dataBytes = 0L
    private var closed = false

    init {
        require(channels == 2) { "only stereo WAV is supported" }
        require(bitsPerSample == 16) { "only 16-bit WAV is supported" }
        writeHeader(output, sampleRate, channels, bitsPerSample, 0)
    }

    override fun writeFrames(frames: FloatArray, frameCount: Int) {
        check(!closed) { "WAV writer is closed" }
        require(frameCount >= 0)
        require(frames.size >= frameCount * channels) { "frame buffer is too small" }
        if (frameCount == 0) return

        val bytes = ByteArray(frameCount * channels * 2)
        var offset = 0
        for (index in 0 until frameCount * channels) {
            val sample = (frames[index].coerceIn(-1f, 1f) * 32767f).roundToInt()
            bytes[offset++] = (sample and 0xff).toByte()
            bytes[offset++] = ((sample shr 8) and 0xff).toByte()
        }
        output.write(bytes)
        dataBytes += bytes.size
    }

    override fun flush() {
        if (!closed) output.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        output.flush()
        output.close()
        patchHeader(file, dataBytes)
    }

    private fun writeHeader(
        output: BufferedOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataBytes: Long,
    ) {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        output.write(byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        ))
        output.writeLe32(36L + dataBytes)
        output.write(byteArrayOf(
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
        ))
        output.write(byteArrayOf(
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
        ))
        output.writeLe32(16)
        output.writeLe16(1)
        output.writeLe16(channels.toLong())
        output.writeLe32(sampleRate.toLong())
        output.writeLe32(byteRate.toLong())
        output.writeLe16(blockAlign.toLong())
        output.writeLe16(bitsPerSample.toLong())
        output.write(byteArrayOf(
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
        ))
        output.writeLe32(dataBytes)
    }

    private fun patchHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.writeLe32(36L + dataBytes)
            raf.seek(40)
            raf.writeLe32(dataBytes)
        }
    }
}

/** Reads the 16/24/32-bit PCM or 32-bit float WAV files produced by the app. */
class WavReader(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    val sampleRate: Int
    val channels: Int
    val bitsPerSample: Int
    val audioFormat: Int
    val dataOffset: Long
    val frameCount: Long

    init {
        val riff = ByteArray(12)
        raf.readFully(riff)
        require(String(riff, 0, 4, Charsets.US_ASCII) == "RIFF") { "not a RIFF file" }
        require(String(riff, 8, 4, Charsets.US_ASCII) == "WAVE") { "not a WAVE file" }

        var format = 0
        var channelCount = 0
        var rate = 0
        var bits = 0
        var offset = -1L
        var dataSize = 0L
        val header = ByteArray(8)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(header)
            val id = String(header, 0, 4, Charsets.US_ASCII)
            val size = readLe32(header, 4)
            val payloadStart = raf.filePointer
            when (id) {
                "fmt " -> {
                    require(size >= 16) { "invalid fmt chunk" }
                    val fmt = ByteArray(size.toInt())
                    raf.readFully(fmt)
                    format = readLe16(fmt, 0)
                    channelCount = readLe16(fmt, 2)
                    rate = readLe32(fmt, 4).toInt()
                    bits = readLe16(fmt, 14)
                }

                "data" -> {
                    offset = payloadStart
                    dataSize = if (size == 0xffffffffL) {
                        raf.length() - payloadStart
                    } else {
                        size
                    }
                }
            }
            raf.seek(payloadStart + size + (size and 1L))
        }

        require(format == 1 || format == 3) { "unsupported WAV format $format" }
        require(format != 3 || bits == 32) { "float WAV must be 32-bit" }
        require(channelCount > 0) { "invalid channel count" }
        require(rate > 0) { "invalid sample rate" }
        require(bits == 16 || bits == 24 || bits == 32) { "unsupported bit depth $bits" }
        require(offset >= 0) { "missing data chunk" }

        audioFormat = format
        channels = channelCount
        sampleRate = rate
        bitsPerSample = bits
        dataOffset = offset
        val bytesPerFrame = channels * (bits / 8)
        frameCount = dataSize / bytesPerFrame
    }

    fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int {
        require(frameCount >= 0)
        require(out.size >= frameCount * channels) { "output buffer is too small" }
        out.fill(0f, 0, frameCount * channels)
        if (frameCount == 0) return 0

        val first = startFrame.coerceAtLeast(0)
        val prefix = (first - startFrame).toInt()
        if (prefix >= frameCount || first >= this.frameCount) return 0
        val count = minOf(frameCount.toLong() - prefix, this.frameCount - first).toInt()
        if (count <= 0) return 0

        val bytesPerSample = bitsPerSample / 8
        val bytes = ByteArray(count * channels * bytesPerSample)
        raf.seek(dataOffset + first * channels * bytesPerSample)
        raf.readFully(bytes)

        var source = 0
        var destination = prefix * channels
        repeat(count * channels) {
            val value = when {
                audioFormat == 3 && bitsPerSample == 32 -> Float.fromBits(readLe32(bytes, source).toInt())
                bitsPerSample == 16 -> readLe16Signed(bytes, source) / 32768f
                bitsPerSample == 24 -> readLe24Signed(bytes, source) / 8_388_608f
                else -> readLe32(bytes, source).toInt() / 2_147_483_648f
            }
            out[destination++] = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
            source += bytesPerSample
        }
        return count
    }

    fun readSamples(startFrame: Long, frameCount: Int, out: IntArray): Int {
        require(frameCount >= 0)
        require(out.size >= frameCount * channels) { "output buffer is too small" }
        out.fill(0, 0, frameCount * channels)
        if (frameCount == 0) return 0

        val first = startFrame.coerceAtLeast(0)
        val prefix = (first - startFrame).toInt()
        if (prefix >= frameCount || first >= this.frameCount) return 0
        val count = minOf(frameCount.toLong() - prefix, this.frameCount - first).toInt()
        if (count <= 0) return 0

        val bytesPerSample = bitsPerSample / 8
        val bytes = ByteArray(count * channels * bytesPerSample)
        raf.seek(dataOffset + first * channels * bytesPerSample)
        raf.readFully(bytes)

        var source = 0
        var destination = prefix * channels
        repeat(count * channels) {
            val value = when {
                audioFormat == 3 && bitsPerSample == 32 -> {
                    (Float.fromBits(readLe32(bytes, source).toInt()).coerceIn(-1f, 1f) * 32767f)
                        .roundToInt()
                }

                bitsPerSample == 16 -> readLe16Signed(bytes, source).toInt()
                bitsPerSample == 24 -> readLe24Signed(bytes, source) shr 8
                else -> readLe32(bytes, source).toInt() shr 16
            }
            out[destination++] = value.coerceIn(-32768, 32767)
            source += bytesPerSample
        }
        return count
    }

    override fun close() {
        raf.close()
    }
}

private fun BufferedOutputStream.writeLe16(value: Long) {
    write((value and 0xff).toInt())
    write(((value shr 8) and 0xff).toInt())
}

private fun BufferedOutputStream.writeLe32(value: Long) {
    write((value and 0xff).toInt())
    write(((value shr 8) and 0xff).toInt())
    write(((value shr 16) and 0xff).toInt())
    write(((value shr 24) and 0xff).toInt())
}

private fun RandomAccessFile.writeLe32(value: Long) {
    write((value and 0xff).toInt())
    write(((value shr 8) and 0xff).toInt())
    write(((value shr 16) and 0xff).toInt())
    write(((value shr 24) and 0xff).toInt())
}

private fun readLe16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun readLe16Signed(bytes: ByteArray, offset: Int): Short =
    readLe16(bytes, offset).toShort()

private fun readLe24Signed(bytes: ByteArray, offset: Int): Int {
    val value = (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16)
    return if (value and 0x800000 != 0) value or -0x1000000 else value
}

private fun readLe32(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)
