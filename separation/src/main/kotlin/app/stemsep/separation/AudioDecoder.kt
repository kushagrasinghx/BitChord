package app.stemsep.separation

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import app.stemsep.dsp.FloatPcmFileWriter
import app.stemsep.dsp.StereoResampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

data class DecodedAudio(
    val file: File,
    val frameCount: Long,
    val sampleRate: Int = 44_100,
)

/**
 * Decodes a local audio file (already fetched, e.g. a resolved stream body)
 * into interleaved float32 stereo at 44.1 kHz. StemSep's URI-based variant
 * used SAF; BitChord downloads the playing track's stream itself, so the
 * decoder only ever sees a file.
 */
class AudioDecoder {
    suspend fun decode(
        file: File,
        output: File,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): DecodedAudio = withContext(Dispatchers.IO) {
        output.parentFile?.mkdirs()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: FloatPcmFileWriter? = null
        var resampler: StereoResampler? = null
        var succeeded = false

        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.stringOrNull(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    trackIndex = index
                    inputFormat = format
                    break
                }
            }
            require(trackIndex >= 0) { "The selected file has no audio track" }
            extractor.selectTrack(trackIndex)
            val format = inputFormat!!
            val mime = format.stringOrNull(MediaFormat.KEY_MIME)
                ?: error("The audio track has no MIME type")
            val durationUs = format.longOr(MediaFormat.KEY_DURATION, 0L)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmWriter = FloatPcmFileWriter(output)
            writer = pcmWriter
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sourceRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var sourceChannels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2)

            fun updateFormat(outputFormat: MediaFormat) {
                sourceRate = outputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, sourceRate)
                sourceChannels = outputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, sourceChannels)
                require(sourceRate > 0) { "Invalid decoder sample rate" }
                require(sourceChannels > 0) { "Invalid decoder channel count" }
                val existing = resampler
                if (existing == null) {
                    resampler = StereoResampler(sourceRate, 44_100)
                } else if (existing.inputRate != sourceRate) {
                    error("The decoder changed sample rate mid-stream")
                }
            }

            updateFormat(format)

            while (!outputDone) {
                if (isCancelled()) throw InterruptedException("Audio decoding cancelled")

                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: error("Decoder returned a null input buffer")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        updateFormat(decoder.outputFormat)
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                ?: error("Decoder returned a null output buffer")
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            if (info.size > 0) {
                                val chunk = toStereoFloat(
                                    outputBuffer,
                                    decoder.outputFormat,
                                    sourceChannels,
                                )
                                val activeResampler = resampler
                                    ?: error("Resampler was not initialized")
                                activeResampler.process(chunk.samples, chunk.frames) { frames, count ->
                                    pcmWriter.writeFrames(frames, count)
                                }
                            }
                            decoder.releaseOutputBuffer(outputIndex, false)
                            if (durationUs > 0) {
                                onProgress(
                                    (info.presentationTimeUs.toDouble() / durationUs)
                                        .toFloat()
                                        .coerceIn(0f, 1f),
                                )
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            resampler?.finish { frames, count -> pcmWriter.writeFrames(frames, count) }
            pcmWriter.flush()
            val frames = pcmWriter.frameCount
            pcmWriter.close()
            writer = null
            succeeded = true
            onProgress(1f)
            DecodedAudio(output, frames, 44_100)
        } finally {
            writer?.close()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            if (!succeeded) output.delete()
        }
    }

    private fun toStereoFloat(
        buffer: java.nio.ByteBuffer,
        format: MediaFormat,
        channels: Int,
    ): StereoChunk {
        val encoding = format.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                val values = FloatArray(floatBuffer.remaining())
                floatBuffer.get(values)
                interleaveStereo(values, channels)
            }

            AudioFormat.ENCODING_PCM_16BIT -> {
                val shortBuffer = buffer.asShortBuffer()
                val values = ShortArray(shortBuffer.remaining())
                shortBuffer.get(values)
                val floats = FloatArray(values.size) { values[it] / 32768f }
                interleaveStereo(floats, channels)
            }

            else -> error("Unsupported decoder PCM encoding $encoding")
        }
    }

    private fun interleaveStereo(samples: FloatArray, channels: Int): StereoChunk {
        require(channels > 0)
        val frames = samples.size / channels
        val output = FloatArray(frames * 2)
        if (channels == 1) {
            for (frame in 0 until frames) {
                val value = samples[frame]
                output[frame * 2] = value
                output[frame * 2 + 1] = value
            }
        } else {
            for (frame in 0 until frames) {
                val base = frame * channels
                output[frame * 2] = samples[base]
                output[frame * 2 + 1] = samples[base + 1]
            }
        }
        return StereoChunk(output, frames)
    }

    private data class StereoChunk(val samples: FloatArray, val frames: Int)

    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) getString(key) else null

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun MediaFormat.longOr(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback
}
