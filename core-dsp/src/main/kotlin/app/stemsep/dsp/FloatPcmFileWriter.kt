package app.stemsep.dsp

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/** Writes raw interleaved stereo float32 little-endian PCM. */
class FloatPcmFileWriter(file: File) : FloatStereoSink, Closeable {
    private val output = BufferedOutputStream(FileOutputStream(file), 256 * 1024)
    private val bytes = ByteArray(64 * 1024)
    private var byteOffset = 0
    private var closed = false

    var frameCount: Long = 0
        private set

    override fun writeFrames(frames: FloatArray, frameCount: Int) {
        check(!closed) { "PCM writer is closed" }
        require(frameCount >= 0)
        require(frames.size >= frameCount * 2) { "frame buffer is too small" }

        for (index in 0 until frameCount * 2) {
            if (byteOffset + 4 > bytes.size) flushBytes()
            val bits = java.lang.Float.floatToRawIntBits(frames[index])
            bytes[byteOffset++] = bits.toByte()
            bytes[byteOffset++] = (bits ushr 8).toByte()
            bytes[byteOffset++] = (bits ushr 16).toByte()
            bytes[byteOffset++] = (bits ushr 24).toByte()
        }
        this.frameCount += frameCount
    }

    override fun flush() {
        if (closed) return
        flushBytes()
        output.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        flushBytes()
        output.flush()
        output.close()
    }

    private fun flushBytes() {
        if (byteOffset > 0) {
            output.write(bytes, 0, byteOffset)
            byteOffset = 0
        }
    }
}
