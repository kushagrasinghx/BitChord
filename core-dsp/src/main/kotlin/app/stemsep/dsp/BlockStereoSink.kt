package app.stemsep.dsp

/** Coalesces many tiny frame writes into larger sink writes. */
class BlockStereoSink(
    private val target: FloatStereoSink,
    capacityFrames: Int = 4096,
) : FloatStereoSink {
    private val buffer = FloatArray(capacityFrames.coerceAtLeast(1) * 2)
    private var bufferedFrames = 0

    override fun writeFrames(frames: FloatArray, frameCount: Int) {
        require(frameCount >= 0)
        var sourceOffset = 0
        var remaining = frameCount
        while (remaining > 0) {
            val capacity = buffer.size / 2
            val copy = minOf(remaining, capacity - bufferedFrames)
            frames.copyInto(
                destination = buffer,
                destinationOffset = bufferedFrames * 2,
                startIndex = sourceOffset * 2,
                endIndex = (sourceOffset + copy) * 2,
            )
            bufferedFrames += copy
            sourceOffset += copy
            remaining -= copy
            if (bufferedFrames == capacity) flushBuffer()
        }
    }

    override fun flush() {
        flushBuffer()
        target.flush()
    }

    private fun flushBuffer() {
        if (bufferedFrames > 0) {
            target.writeFrames(buffer, bufferedFrames)
            bufferedFrames = 0
        }
    }
}
