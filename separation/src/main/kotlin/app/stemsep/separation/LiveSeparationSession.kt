package app.stemsep.separation

import android.util.Log
import app.stemsep.dsp.FloatPcmFileSource
import app.stemsep.dsp.FloatPcmFileWriter
import app.stemsep.dsp.MdxStreamingSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Live vocal-removal session over an already-decoded track.
 *
 * StemSep's SeparationPipeline processed a whole file from frame 0 and
 * committed the stems to permanent storage. BitChord runs the same chunk loop
 * from the current playback position, keeps the stems temporary and publishes
 * [LiveStemPreview] after every flushed chunk so playback can read the
 * growing files immediately.
 */
class LiveSeparationSession {

    data class Stats(
        val fraction: Float,
        val chunks: Int,
        val averageMillis: Long,
        val lastChunkMillis: Long,
        val workerThreads: Int,
        val activeBackend: ComputeBackend,
        val fallbackReason: String?,
    )

    suspend fun run(
        model: ModelSpec,
        modelFile: File,
        decoded: DecodedAudio,
        startFrame: Long,
        vocalsFile: File,
        instrumentalFile: File,
        cpuThreads: Int,
        playbackLeadSeconds: () -> Float? = { null },
        onStats: (Stats) -> Unit = {},
        onPreview: (LiveStemPreview) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        if (decoded.frameCount <= 0) throw IOException("Decoded audio is empty")
        require(startFrame in 0 until decoded.frameCount) { "startFrame past the end" }

        val stemFrames = decoded.frameCount - startFrame
        vocalsFile.parentFile?.mkdirs()
        val autoThreads = cpuThreads <= 0
        val threadLimit = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val autoController = if (autoThreads) AutoThreadController(threadLimit) else null
        val initialThreads = autoController?.threads ?: cpuThreads.coerceIn(1, threadLimit)

        publish(
            onPreview, vocalsFile, instrumentalFile,
            availableFrames = 0L, totalFrames = stemFrames, sampleRate = decoded.sampleRate,
        )

        FloatPcmFileSource(decoded.file, decoded.frameCount).use { source ->
            FloatPcmFileWriter(vocalsFile).use { vocals ->
                FloatPcmFileWriter(instrumentalFile).use { instrumental ->
                    OnnxMdxEngine(
                        modelFile = modelFile,
                        threads = initialThreads,
                        requestedBackend = ComputeBackend.CPU,
                    ).use { engine ->
                        var lastChunkEndNanos = System.nanoTime()
                        var lastWritten = 0L
                        val separator = MdxStreamingSeparator(
                            config = model.config,
                            inference = engine.inference(model.config),
                        )
                        separator.separate(
                            source = source,
                            vocalsSink = vocals,
                            instrumentalSink = instrumental,
                            isCancelled = isCancelled,
                            startFrame = startFrame,
                            onProgress = { fraction ->
                                val now = System.nanoTime()
                                val written = (fraction * stemFrames).toLong()
                                val chunkMillis = (now - lastChunkEndNanos) / 1_000_000
                                lastChunkEndNanos = now
                                lastWritten = written

                                publish(
                                    onPreview, vocalsFile, instrumentalFile,
                                    availableFrames = minOf(
                                        vocals.frameCount,
                                        instrumental.frameCount,
                                    ),
                                    totalFrames = stemFrames,
                                    sampleRate = decoded.sampleRate,
                                )
                                if (autoController != null) {
                                    val desired = autoController.observe(playbackLeadSeconds())
                                    if (desired != engine.effectiveThreads) {
                                        engine.setThreads(desired)
                                    }
                                }
                                val stats = engine.stats()
                                onStats(
                                    Stats(
                                        fraction = fraction,
                                        chunks = stats.chunks,
                                        averageMillis = stats.averageMillis,
                                        lastChunkMillis = chunkMillis,
                                        workerThreads = engine.effectiveThreads,
                                        activeBackend = engine.activeBackend,
                                        fallbackReason = engine.fallbackReason,
                                    ),
                                )
                            },
                        )
                        Log.d(
                            TAG,
                            "Live separation finished: start=$startFrame stemFrames=$stemFrames " +
                                "written=$lastWritten threads=${engine.effectiveThreads}",
                        )
                    }
                }
            }
        }
    }

    private fun publish(
        onPreview: (LiveStemPreview) -> Unit,
        vocalsFile: File,
        instrumentalFile: File,
        availableFrames: Long,
        totalFrames: Long,
        sampleRate: Int,
    ) {
        onPreview(
            LiveStemPreview(
                vocalsFile = vocalsFile,
                instrumentalFile = instrumentalFile,
                availableFrames = availableFrames,
                totalFrames = totalFrames,
                sampleRate = sampleRate,
            ),
        )
    }

    private companion object {
        const val TAG = "LiveSeparationSession"
    }
}
