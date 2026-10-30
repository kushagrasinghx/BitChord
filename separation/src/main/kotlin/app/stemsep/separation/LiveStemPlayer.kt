package app.stemsep.separation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.stemsep.dsp.StemMixer
import app.stemsep.dsp.StemMode
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

class LiveStemPlayer(
    private val vocalsFile: File,
    private val instrumentalFile: File,
    val totalFrames: Long,
    val sampleRate: Int = 44_100,
    private val onPosition: (Long) -> Unit = {},
) : Closeable {
    private val vocals = RandomAccessFile(vocalsFile, "r")
    private val instrumental = RandomAccessFile(instrumentalFile, "r")
    private val lock = Object()
    private val chunkFrames = 2048
    private val vocalBuffer = FloatArray(chunkFrames * 2)
    private val instrumentalBuffer = FloatArray(chunkFrames * 2)
    private val mixBuffer = FloatArray(chunkFrames * 2)
    private val bytes = ByteArray(chunkFrames * 8)

    @Volatile
    private var mode: StemMode = StemMode.VOCALS

    @Volatile
    private var vocalLevel: Float = 1f

    private var playing = false
    private var released = false
    private var positionFrames = 0L
    private var generation = 0L
    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null

    fun setMode(mode: StemMode) {
        this.mode = mode
    }

    fun setVocalLevel(level: Float) {
        vocalLevel = level.coerceIn(0f, 1f)
    }

    fun availableFrames(): Long = minOf(
        vocalsFile.length() / 8,
        instrumentalFile.length() / 8,
        totalFrames,
    ).coerceAtLeast(0)

    fun currentFrame(): Long = synchronized(lock) { positionFrames }

    fun isPlaying(): Boolean = synchronized(lock) { playing }

    fun play() {
        synchronized(lock) {
            if (released) return
            if (positionFrames >= totalFrames) positionFrames = 0
            if (audioTrack == null) audioTrack = createAudioTrack()
            playing = true
            val currentThread = thread
            if (currentThread == null || !currentThread.isAlive) {
                thread = Thread(::runPlayback, "stemsep-live-player").apply {
                    isDaemon = true
                    start()
                }
            }
            audioTrack?.play()
            lock.notifyAll()
        }
    }

    fun pause() {
        synchronized(lock) {
            if (released) return
            playing = false
            runCatching { audioTrack?.pause() }
            lock.notifyAll()
        }
    }

    fun seekTo(frame: Long) {
        val target = frame.coerceIn(0L, totalFrames)
        synchronized(lock) {
            if (released) return
            positionFrames = target
            generation++
            runCatching {
                audioTrack?.pause()
                audioTrack?.flush()
                if (playing) audioTrack?.play()
            }
            lock.notifyAll()
        }
        onPosition(target)
    }

    override fun close() {
        synchronized(lock) {
            if (released) return
            released = true
            playing = false
            runCatching { audioTrack?.pause() }
            lock.notifyAll()
        }
        runCatching { thread?.join(2_000) }
        synchronized(lock) {
            runCatching {
                audioTrack?.flush()
                audioTrack?.release()
            }
            audioTrack = null
            thread = null
        }
        runCatching { vocals.close() }
        runCatching { instrumental.close() }
    }

    private fun runPlayback() {
        try {
            while (true) {
                val start: Long
                val writeGeneration: Long
                synchronized(lock) {
                    while (!playing && !released) lock.wait()
                    if (released) return
                    start = positionFrames
                    writeGeneration = generation
                }

                if (start >= totalFrames) {
                    stopAtEnd(totalFrames)
                    continue
                }

                val available = availableFrames()
                if (start >= available) {
                    if (available >= totalFrames) {
                        stopAtEnd(totalFrames)
                    } else {
                        synchronized(lock) {
                            if (!released && playing) lock.wait(50)
                        }
                    }
                    continue
                }

                val requested = minOf(
                    chunkFrames.toLong(),
                    available - start,
                    totalFrames - start,
                ).toInt()
                val vocalRead = readFrames(vocals, start, requested, vocalBuffer)
                val instrumentalRead = readFrames(instrumental, start, requested, instrumentalBuffer)
                val frames = minOf(vocalRead, instrumentalRead)
                if (frames <= 0) {
                    synchronized(lock) {
                        if (!released && playing) lock.wait(50)
                    }
                    continue
                }

                val gains = StemMixer.gains(mode, vocalLevel)
                for (frame in 0 until frames) {
                    val base = frame * 2
                    mixBuffer[base] =
                        vocalBuffer[base] * gains.vocal +
                        instrumentalBuffer[base] * gains.instrumental
                    mixBuffer[base + 1] =
                        vocalBuffer[base + 1] * gains.vocal +
                        instrumentalBuffer[base + 1] * gains.instrumental
                }

                val track = synchronized(lock) { audioTrack } ?: return
                val written = track.write(mixBuffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    synchronized(lock) { playing = false }
                    continue
                }

                val framesWritten = written / 2
                synchronized(lock) {
                    if (writeGeneration == generation) {
                        positionFrames = (start + framesWritten).coerceAtMost(totalFrames)
                    }
                }
                onPosition(currentFrame())
                if (framesWritten < frames) {
                    synchronized(lock) {
                        if (!released && playing) lock.wait(20)
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Throwable) {
            synchronized(lock) { playing = false }
        }
    }

    private fun stopAtEnd(position: Long) {
        synchronized(lock) {
            playing = false
            positionFrames = position
            runCatching { audioTrack?.pause() }
        }
        onPosition(position)
    }

    private fun readFrames(
        file: RandomAccessFile,
        startFrame: Long,
        frameCount: Int,
        out: FloatArray,
    ): Int {
        out.fill(0f, 0, frameCount * 2)
        return runCatching {
            val available = file.length() / 8
            if (startFrame >= available) return 0
            val count = minOf(frameCount.toLong(), available - startFrame).toInt()
            val byteCount = count * 8
            file.seek(startFrame * 8)
            file.readFully(bytes, 0, byteCount)
            var source = 0
            var destination = 0
            repeat(count * 2) {
                val bits = (bytes[source].toInt() and 0xff) or
                    ((bytes[source + 1].toInt() and 0xff) shl 8) or
                    ((bytes[source + 2].toInt() and 0xff) shl 16) or
                    (bytes[source + 3].toInt() shl 24)
                out[destination++] = Float.fromBits(bits)
                source += 4
            }
            count
        }.getOrDefault(0)
    }

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferSize = max(minBuffer, 4096 * 2 * 4)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("Could not initialize the live audio output")
        }
        return track
    }
}
