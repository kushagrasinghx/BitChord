/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from Sekai Tune (github.com/rgsekai/sekai-tune) canvas module — GPLv3.
 * Bass-energy extraction from the active audio session via Android's
 * Visualizer FFT capture, feeding the procedural shaders' audio-reactive
 * uniforms (experimental feature — requires RECORD_AUDIO).
 */

package moe.rukamori.archivetune.canvas.procedural

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerConnection
import kotlin.math.hypot

fun extractBassEnergyFromFft(fft: ByteArray, captureSize: Int = 256): Float {
    if (fft.isEmpty()) return 0f
    var bassSum = 0f
    val maxBin = (captureSize / 16).coerceIn(2, 6)
    var count = 0
    for (k in 1..maxBin) {
        val realIdx = 2 * k
        val imagIdx = 2 * k + 1
        if (imagIdx < fft.size) {
            val r = fft[realIdx].toFloat()
            val i = fft[imagIdx].toFloat()
            bassSum += hypot(r, i)
            count++
        }
    }
    if (count == 0) return 0f
    val rawBass = bassSum / count.toFloat()

    return ((rawBass - 4f) / 50f).coerceIn(0f, 1f)
}

fun smoothBassEnergy(current: Float, target: Float): Float {
    return if (target > current) {
        current * 0.4f + target * 0.6f
    } else {
        current * 0.85f + target * 0.15f
    }
}

@Composable
fun rememberBassEnergy(
    audioSessionId: Int = 0,
    isPlaying: Boolean = true,
    enabled: Boolean = false,
): State<Float> {
    val context = LocalContext.current
    val bassEnergyState = remember { mutableFloatStateOf(0f) }
    val playerConnection = LocalPlayerConnection.current

    LaunchedEffect(audioSessionId, enabled, isPlaying, playerConnection) {
        if (!enabled || !isPlaying) {
            bassEnergyState.floatValue = 0f
            return@LaunchedEffect
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            bassEnergyState.floatValue = 0f
            return@LaunchedEffect
        }

        var activeSessionId =
            if (audioSessionId > 0) {
                audioSessionId
            } else {
                playerConnection?.localPlayer?.audioSessionId ?: 0
            }

        var attempts = 0
        while (isActive && activeSessionId <= 0 && attempts < 50) {
            delay(100)
            attempts++
            activeSessionId =
                if (audioSessionId > 0) {
                    audioSessionId
                } else {
                    playerConnection?.localPlayer?.audioSessionId ?: 0
                }
        }

        var visualizer: Visualizer? = null
        var smoothedEnergy = 0f

        try {
            val viz =
                try {
                    Visualizer(activeSessionId)
                } catch (t: Throwable) {
                    if (activeSessionId != 0) {
                        Visualizer(0)
                    } else {
                        throw t
                    }
                }
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = 256.coerceIn(captureRange[0], captureRange[1])
            viz.captureSize = captureSize

            val rate = Visualizer.getMaxCaptureRate().coerceAtMost(30_000)

            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {

                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null || fft.isEmpty()) return
                        val normalized = extractBassEnergyFromFft(fft, captureSize)
                        smoothedEnergy = smoothBassEnergy(smoothedEnergy, normalized)
                        bassEnergyState.floatValue = smoothedEnergy
                    }
                },
                rate,
                false,
                true,
            )

            viz.enabled = true
            visualizer = viz

            try {
                awaitCancellation()
            } finally {
                try {
                    visualizer?.enabled = false
                    visualizer?.release()
                } catch (_: Throwable) {
                }
                visualizer = null
                bassEnergyState.floatValue = 0f
            }
        } catch (_: Throwable) {

            bassEnergyState.floatValue = 0f
        }
    }

    return bassEnergyState
}
