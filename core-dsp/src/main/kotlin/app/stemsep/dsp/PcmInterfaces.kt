package app.stemsep.dsp

/** Random-access, interleaved stereo float PCM. Out-of-range frames are zero. */
interface FloatStereoSource {
    val frameCount: Long

    /**
     * Reads [frameCount] frames starting at [startFrame] into [out].
     * Implementations must zero the destination first and may return fewer frames.
     */
    fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int
}

/** Sequential interleaved stereo float PCM sink. */
interface FloatStereoSink {
    fun writeFrames(frames: FloatArray, frameCount: Int)
    fun flush()
}

fun interface MdxInference {
    fun run(inputTensor: FloatArray, outputTensor: FloatArray)
}
