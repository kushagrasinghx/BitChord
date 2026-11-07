package com.music.bitchord.data.shazam

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What the recognition sheet is doing, from the microphone to the answer. */
sealed interface RecognitionSessionState {
    data object Listening : RecognitionSessionState
    data object Searching : RecognitionSessionState
    data class Found(val track: ShazamTrack) : RecognitionSessionState
    data object NoMatch : RecognitionSessionState
    data object Failed : RecognitionSessionState
}

/**
 * One microphone session: record 16 kHz mono PCM, animate from its level, and
 * search progressively at 2, 4, 8 and 12 seconds.
 *
 * The short clip goes first so a clear match returns quickly. The recorder keeps
 * running while that lookup is in flight, which is why the next attempt can
 * simply take the longer prefix of the same audio instead of asking the user to
 * start over. A later attempt cancels the previous lookup; only the newest one
 * may change the state. The audio stays in memory: the fingerprint is the only
 * thing sent to the provider, and no recording is written to disk.
 */
class RecognitionSession(
    private val scope: CoroutineScope,
    private val provider: ShazamProvider = ShazamClient,
) {

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _state = MutableStateFlow<RecognitionSessionState>(RecognitionSessionState.Listening)
    val state: StateFlow<RecognitionSessionState> = _state.asStateFlow()

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var capture: Capture? = null

    @Volatile
    private var readJob: Job? = null

    @Volatile
    private var searchJob: Job? = null

    /** Mutable state for one microphone take; a retry gets a fresh instance. */
    private class Capture {
        val samples = ShortArray(MAX_SAMPLES)
        var sampleCount = 0
        var nextSearchSample = FIRST_SEARCH_SAMPLES
        var stage = 0
        var captureDone = false
        var searchGeneration = 0
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        // A second start while a take is still running would fight the first
        // for the microphone. Retries only arrive after the state has settled.
        if (capture != null) return
        stop()
        val audioRecord = createRecorder() ?: run {
            _state.value = RecognitionSessionState.Failed
            return
        }
        val take = Capture()
        recorder = audioRecord
        capture = take
        _level.value = 0f
        _state.value = RecognitionSessionState.Listening
        readJob = scope.launch(Dispatchers.IO) {
            readSamples(audioRecord, take)
        }
    }

    /** Stops the microphone and any lookup. */
    fun stop() {
        capture = null
        searchJob?.cancel()
        searchJob = null
        readJob?.cancel()
        readJob = null
        releaseRecorder(recorder)
        recorder = null
        _level.value = 0f
    }

    private fun readSamples(audioRecord: AudioRecord, take: Capture) {
        val buffer = ShortArray(READ_WINDOW_SAMPLES)
        var readError = false
        try {
            audioRecord.startRecording()
            while (scope.isActive && !take.captureDone) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read < 0) {
                    readError = true
                    break
                }
                if (read == 0) continue
                updateLevel(buffer, read)
                val writable = min(read, MAX_SAMPLES - take.sampleCount)
                if (writable > 0) {
                    buffer.copyInto(take.samples, take.sampleCount, 0, writable)
                    take.sampleCount += writable
                }
                maybeSearch(take)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            readError = true
        } finally {
            releaseRecorder(audioRecord)
            _level.value = 0f
            // A recorder can be taken by a call or another app mid-listen. Only
            // report a failure while this take is still the one the UI shows.
            if (readError && capture === take && !take.captureDone && _state.value == RecognitionSessionState.Listening) {
                capture = null
                _state.value = RecognitionSessionState.Failed
            }
        }
    }

    private fun maybeSearch(take: Capture) {
        while (!take.captureDone && take.sampleCount >= take.nextSearchSample) {
            val length = take.nextSearchSample
            val finalAttempt = take.stage == LAST_STAGE
            take.stage += 1
            take.nextSearchSample = when (take.stage) {
                1 -> SECOND_SEARCH_SAMPLES
                2 -> THIRD_SEARCH_SAMPLES
                3 -> FINAL_SEARCH_SAMPLES
                else -> Int.MAX_VALUE
            }
            if (finalAttempt) {
                // The last fingerprint is the whole 12 seconds; nothing after
                // this point can improve it, so release the microphone while
                // the request is in flight.
                take.captureDone = true
                releaseRecorder(recorder)
                if (capture === take) _state.value = RecognitionSessionState.Searching
            }
            launchSearch(take, length)
        }
    }

    private fun launchSearch(take: Capture, length: Int) {
        searchJob?.cancel()
        val generation = ++take.searchGeneration
        val clip = take.samples.copyOfRange(0, length)
        searchJob = scope.launch(Dispatchers.Default) {
            // Fingerprinting is CPU-bound; the provider then does the network
            // request on its own dispatcher.
            val signature = ShazamSignatureGenerator.generate(clip)
            val result = provider.recognize(signature)
            if (capture !== take || take.searchGeneration != generation) return@launch
            when (result) {
                is ShazamLookupResult.Found -> finishFound(take, result.track)
                ShazamLookupResult.NoMatch -> if (take.captureDone) {
                    finish(take, RecognitionSessionState.NoMatch)
                }
                ShazamLookupResult.Failed -> if (take.captureDone) {
                    finish(take, RecognitionSessionState.Failed)
                }
            }
        }
    }

    private fun finishFound(take: Capture, track: ShazamTrack) {
        if (capture !== take) return
        take.captureDone = true
        releaseRecorder(recorder)
        finish(take, RecognitionSessionState.Found(track))
    }

    private fun finish(take: Capture, state: RecognitionSessionState) {
        if (capture !== take) return
        capture = null
        searchJob = null
        _level.value = 0f
        _state.value = state
    }

    private fun updateLevel(samples: ShortArray, read: Int) {
        var sum = 0.0
        for (index in 0 until read) {
            val sample = samples[index] / 32768.0
            sum += sample * sample
        }
        val rms = sqrt(sum / read).toFloat()
        val next = sqrt((rms * LEVEL_GAIN).coerceIn(0f, 1f))
        val previous = _level.value
        // Jump towards a new sound, fall away gently, so the rings answer a
        // beat instead of flickering at the read rate.
        val weight = if (next > previous) ATTACK else RELEASE
        _level.value = previous + (next - previous) * weight
    }

    private fun createRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            ShazamSignatureGenerator.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferSize = max(minBuffer, READ_WINDOW_SAMPLES * 2)
        val audioRecord = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ShazamSignatureGenerator.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        }.getOrNull() ?: return null
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }
        return audioRecord
    }

    private fun releaseRecorder(audioRecord: AudioRecord?) {
        if (audioRecord == null) return
        if (recorder === audioRecord) recorder = null
        runCatching { audioRecord.stop() }
        runCatching { audioRecord.release() }
    }

    private companion object {
        /** ~100 ms at 16 kHz: one animation frame's worth of samples. */
        const val READ_WINDOW_SAMPLES = 1_600
        const val LEVEL_GAIN = 8f
        const val ATTACK = 0.6f
        const val RELEASE = 0.12f

        const val FIRST_SEARCH_SAMPLES = ShazamSignatureGenerator.SAMPLE_RATE_HZ * 2
        const val SECOND_SEARCH_SAMPLES = ShazamSignatureGenerator.SAMPLE_RATE_HZ * 4
        const val THIRD_SEARCH_SAMPLES = ShazamSignatureGenerator.SAMPLE_RATE_HZ * 8
        const val FINAL_SEARCH_SAMPLES = ShazamSignatureGenerator.SAMPLE_RATE_HZ * 12
        const val MAX_SAMPLES = FINAL_SEARCH_SAMPLES
        const val LAST_STAGE = 3
    }
}
