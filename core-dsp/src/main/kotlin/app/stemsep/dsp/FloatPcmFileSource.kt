package app.stemsep.dsp

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Random-access source for a raw interleaved stereo float32 file. */
class FloatPcmFileSource(
    file: File,
    override val frameCount: Long,
) : FloatStereoSource, Closeable {
    private val raf = RandomAccessFile(file, "r")
    private var bytes = ByteArray(0)

    init {
        require(frameCount >= 0) { "negative frame count" }
        require(file.length() >= frameCount * 8) { "raw PCM file is shorter than declared" }
    }

    override fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int {
        require(frameCount >= 0)
        require(out.size >= frameCount * 2) { "output buffer is too small" }
        out.fill(0f, 0, frameCount * 2)
        if (frameCount == 0 || startFrame >= this.frameCount) return 0

        val first = startFrame.coerceAtLeast(0)
        val prefixFrames = (first - startFrame).toInt()
        val count = minOf(frameCount.toLong() - prefixFrames, this.frameCount - first)
            .coerceAtLeast(0)
            .toInt()
        if (count == 0) return 0

        val byteCount = count * 8
        if (bytes.size < byteCount) bytes = ByteArray(byteCount)
        raf.seek(first * 8)
        raf.readFully(bytes, 0, byteCount)

        var destination = prefixFrames * 2
        var source = 0
        repeat(count * 2) {
            val bits = (bytes[source].toInt() and 0xff) or
                ((bytes[source + 1].toInt() and 0xff) shl 8) or
                ((bytes[source + 2].toInt() and 0xff) shl 16) or
                (bytes[source + 3].toInt() shl 24)
            out[destination++] = Float.fromBits(bits)
            source += 4
        }
        return count
    }

    override fun close() {
        raf.close()
    }
}
