package app.stemsep.dsp

import java.io.Closeable
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Writes a 16-bit stereo WAV to a non-seekable stream.
 *
 * The data size is known up front, so the header does not need to be patched.
 */
class WavStreamWriter(
    private val output: OutputStream,
    private val sampleRate: Int = 44_100,
    private val channels: Int = 2,
    private val bitsPerSample: Int = 16,
    frameCount: Long,
) : FloatStereoSink, Closeable {
    private val dataBytes = frameCount * channels * (bitsPerSample / 8)
    private var closed = false

    init {
        require(sampleRate > 0)
        require(channels == 2) { "only stereo WAV is supported" }
        require(bitsPerSample == 16) { "only 16-bit WAV is supported" }
        require(frameCount >= 0)
        writeHeader()
    }

    override fun writeFrames(frames: FloatArray, frameCount: Int) {
        check(!closed) { "WAV stream writer is closed" }
        require(frameCount >= 0)
        require(frames.size >= frameCount * channels) { "frame buffer is too small" }
        val bytes = ByteArray(frameCount * channels * 2)
        var offset = 0
        for (index in 0 until frameCount * channels) {
            val sample = (frames[index].coerceIn(-1f, 1f) * 32767f).roundToInt()
            bytes[offset++] = (sample and 0xff).toByte()
            bytes[offset++] = ((sample shr 8) and 0xff).toByte()
        }
        output.write(bytes)
    }

    override fun flush() {
        if (!closed) output.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        output.flush()
        output.close()
    }

    private fun writeHeader() {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLe32(36L + dataBytes)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeLe32(16)
        writeLe16(1)
        writeLe16(channels.toLong())
        writeLe32(sampleRate.toLong())
        writeLe32(byteRate.toLong())
        writeLe16(blockAlign.toLong())
        writeLe16(bitsPerSample.toLong())
        output.write("data".toByteArray(Charsets.US_ASCII))
        writeLe32(dataBytes)
    }

    private fun writeLe16(value: Long) {
        output.write((value and 0xff).toInt())
        output.write(((value shr 8) and 0xff).toInt())
    }

    private fun writeLe32(value: Long) {
        output.write((value and 0xff).toInt())
        output.write(((value shr 8) and 0xff).toInt())
        output.write(((value shr 16) and 0xff).toInt())
        output.write(((value shr 24) and 0xff).toInt())
    }
}
