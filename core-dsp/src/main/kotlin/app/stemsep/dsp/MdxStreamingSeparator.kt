package app.stemsep.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * Streams UVR MDX-Net chunks with the same centre-trim geometry as UVR.
 *
 * The vocal chunk is weighted with a symmetric Hann window and overlap-added;
 * the instrumental chunk is always `mixture - vocal` at the same global
 * position. Only two full chunks are resident at once.
 *
 * `startFrame` lets a live session begin mid-file: output covers
 * `[startFrame, frameCount)` only. When it lands inside the chunk grid the
 * chunk loop begins one segment earlier so overlap-add keeps the same pair of
 * chunks the whole-file run would have used — clamped output is bit-identical
 * to slicing the full-file output at `startFrame`.
 */
class MdxStreamingSeparator(
    private val config: MdxConfig,
    private val inference: MdxInference,
    private val stft: MdxStft = MdxStft(config),
) {
    fun separate(
        source: FloatStereoSource,
        vocalsSink: FloatStereoSink,
        instrumentalSink: FloatStereoSink,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        startFrame: Long = 0L,
    ) {
        val totalFrames = source.frameCount
        require(totalFrames >= 0) { "negative frame count" }
        require(startFrame in 0..max(0L, totalFrames)) { "startFrame outside the source" }

        val trim = config.edgeTrimSamples
        val chunkSize = config.segmentSamples
        val generated = config.generatedSamples
        require(generated > 0) { "invalid generated chunk length" }

        val remainder = totalFrames % generated
        val trailingPad = generated + trim - remainder
        val paddedFrames = trim + totalFrames + trailingPad
        val stride = config.strideSamples
        val segmentCount = ((paddedFrames + stride - 1) / stride).toInt()
        val desiredStart = trim + startFrame
        val desiredEnd = trim + totalFrames
        val firstSegment = if (startFrame > 0L) {
            // Smallest segment index k-1 such that the pair (k-1, k) is the
            // first pair the whole-file run would use for any frame ≥
            // desiredStart: coverage of iteration k begins at
            // (k-1)*stride + chunkLength.
            max(0L, Math.floorDiv(desiredStart - chunkSize.toLong(), stride.toLong()) + 1L)
                .toInt()
        } else {
            0
        }
        val span = desiredEnd - desiredStart

        val inputTensor = FloatArray(config.tensorElements)
        val outputTensor = FloatArray(config.tensorElements)
        var currentMixture = FloatArray(chunkSize * 2)
        var currentVocal = FloatArray(chunkSize * 2)
        var currentWeights = FloatArray(chunkSize)
        var previousMixture = FloatArray(chunkSize * 2)
        var previousVocal = FloatArray(chunkSize * 2)
        var previousWeights = FloatArray(chunkSize)
        var previousStart = 0L
        var previousActual = 0
        var havePrevious = false
        var writeCursor = desiredStart
        val vocalsBlock = BlockStereoSink(vocalsSink)
        val instrumentalBlock = BlockStereoSink(instrumentalSink)

        if (totalFrames == 0L || span <= 0L) {
            vocalsBlock.flush()
            instrumentalBlock.flush()
            onProgress(1f)
            return
        }

        for (segmentIndex in firstSegment until segmentCount) {
            if (isCancelled()) throw InterruptedException("Separation cancelled")
            if (writeCursor >= desiredEnd) break
            // When starting mid-file the first processed chunk is context for
            // the overlap-add of the following pair; it must not emit output.
            val contextOnly = segmentIndex == firstSegment && firstSegment > 0
            val currentStart = segmentIndex.toLong() * stride
            if (currentStart >= paddedFrames) break
            val currentActual = min(chunkSize.toLong(), paddedFrames - currentStart).toInt()
            val sourceStart = currentStart - trim

            currentMixture.fill(0f)
            source.readFrames(sourceStart, chunkSize, currentMixture)
            stft.forward(currentMixture, inputTensor)
            inference.run(inputTensor, outputTensor)
            stft.inverse(outputTensor, currentVocal)

            val compensation = config.compensation
            for (index in currentVocal.indices) {
                val value = currentVocal[index] * compensation
                if (!value.isFinite()) {
                    throw IllegalStateException("Separated vocal contains NaN or Inf")
                }
                currentVocal[index] = value
            }

            val weights = symmetricHann(currentActual)
            currentWeights.fill(1f)
            weights.copyInto(currentWeights)

            if (!havePrevious && !contextOnly) {
                val firstSafeStart = max(writeCursor, desiredStart)
                val firstSafeEnd = minOf(
                    currentStart + stride,
                    currentStart + currentActual,
                    desiredEnd,
                )
                if (firstSafeEnd > firstSafeStart) {
                    writeSingle(
                        vocalsBlock,
                        instrumentalBlock,
                        firstSafeStart,
                        firstSafeEnd,
                        currentStart,
                        currentMixture,
                        currentVocal,
                    )
                }
                writeCursor = max(writeCursor, firstSafeEnd)
            }

            if (havePrevious) {
                val previousEnd = previousStart + previousActual
                val currentEnd = currentStart + currentActual
                if (currentStart > previousEnd) {
                    throw IllegalStateException("UVR chunks contain an uncovered gap")
                }

                val singleEnd = minOf(currentStart, previousEnd, desiredEnd)
                val singleStart = max(writeCursor, desiredStart)
                if (singleEnd > singleStart) {
                    writeSingle(
                        vocalsBlock,
                        instrumentalBlock,
                        singleStart,
                        singleEnd,
                        previousStart,
                        previousMixture,
                        previousVocal,
                    )
                }
                writeCursor = max(writeCursor, singleEnd)

                val overlapStart = maxOf(currentStart, writeCursor, desiredStart)
                val overlapEnd = minOf(previousEnd, currentEnd, desiredEnd)
                if (overlapEnd > overlapStart) {
                    writeOverlap(
                        vocalsBlock,
                        instrumentalBlock,
                        overlapStart,
                        overlapEnd,
                        previousStart,
                        currentStart,
                        currentMixture,
                        previousVocal,
                        currentVocal,
                        previousWeights,
                        currentWeights,
                    )
                }
                writeCursor = max(writeCursor, overlapEnd)
            }

            previousStart = currentStart
            previousActual = currentActual
            val oldMixture = previousMixture
            previousMixture = currentMixture
            currentMixture = oldMixture
            val oldVocal = previousVocal
            previousVocal = currentVocal
            currentVocal = oldVocal
            val oldWeights = previousWeights
            previousWeights = currentWeights
            currentWeights = oldWeights
            havePrevious = true

            vocalsBlock.flush()
            instrumentalBlock.flush()
            val written = min(span, max(0L, writeCursor - desiredStart))
            onProgress(written.toFloat() / span)
        }

        if (!havePrevious) {
            throw IllegalStateException("UVR produced no chunks")
        }

        val previousEnd = previousStart + previousActual
        val finalStart = max(writeCursor, desiredStart)
        val finalEnd = min(previousEnd, desiredEnd)
        if (finalEnd > finalStart) {
            writeSingle(
                vocalsBlock,
                instrumentalBlock,
                finalStart,
                finalEnd,
                previousStart,
                previousMixture,
                previousVocal,
            )
        }
        writeCursor = max(writeCursor, finalEnd)
        if (writeCursor != desiredEnd) {
            throw IllegalStateException("UVR output coverage does not match source length")
        }
        vocalsBlock.flush()
        instrumentalBlock.flush()
        onProgress(1f)
    }

    private fun writeSingle(
        vocalsSink: FloatStereoSink,
        instrumentalSink: FloatStereoSink,
        globalStart: Long,
        globalEnd: Long,
        chunkStart: Long,
        mixture: FloatArray,
        vocal: FloatArray,
    ) {
        var global = globalStart
        while (global < globalEnd) {
            val local = (global - chunkStart).toInt()
            val base = local * 2
            writeFrame(
                vocalsSink,
                instrumentalSink,
                mixture[base],
                mixture[base + 1],
                vocal[base],
                vocal[base + 1],
            )
            global++
        }
    }

    private fun writeOverlap(
        vocalsSink: FloatStereoSink,
        instrumentalSink: FloatStereoSink,
        globalStart: Long,
        globalEnd: Long,
        previousStart: Long,
        currentStart: Long,
        currentMixture: FloatArray,
        previousVocal: FloatArray,
        currentVocal: FloatArray,
        previousWeights: FloatArray,
        currentWeights: FloatArray,
    ) {
        var global = globalStart
        while (global < globalEnd) {
            val previousLocal = (global - previousStart).toInt()
            val currentLocal = (global - currentStart).toInt()
            val previousWeight = previousWeights[previousLocal]
            val currentWeight = currentWeights[currentLocal]
            val denominator = previousWeight + currentWeight
            val previousBase = previousLocal * 2
            val currentBase = currentLocal * 2

            val vocalLeft: Float
            val vocalRight: Float
            if (denominator > 1e-8f) {
                vocalLeft = (
                    previousVocal[previousBase] * previousWeight +
                        currentVocal[currentBase] * currentWeight
                    ) / denominator
                vocalRight = (
                    previousVocal[previousBase + 1] * previousWeight +
                        currentVocal[currentBase + 1] * currentWeight
                    ) / denominator
            } else {
                vocalLeft = 0.5f * (previousVocal[previousBase] + currentVocal[currentBase])
                vocalRight = 0.5f * (previousVocal[previousBase + 1] + currentVocal[currentBase + 1])
            }

            writeFrame(
                vocalsSink,
                instrumentalSink,
                currentMixture[currentBase],
                currentMixture[currentBase + 1],
                vocalLeft,
                vocalRight,
            )
            global++
        }
    }

    private fun writeFrame(
        vocalsSink: FloatStereoSink,
        instrumentalSink: FloatStereoSink,
        mixLeft: Float,
        mixRight: Float,
        vocalLeft: Float,
        vocalRight: Float,
    ) {
        frameBuffer[0] = vocalLeft
        frameBuffer[1] = vocalRight
        vocalsSink.writeFrames(frameBuffer, 1)

        frameBuffer[0] = mixLeft - vocalLeft
        frameBuffer[1] = mixRight - vocalRight
        instrumentalSink.writeFrames(frameBuffer, 1)
    }

    private val frameBuffer = FloatArray(2)
}
