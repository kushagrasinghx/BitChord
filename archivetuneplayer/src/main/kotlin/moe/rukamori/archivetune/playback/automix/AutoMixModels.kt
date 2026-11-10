/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.automix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

const val AUTO_MIX_CURVE_STEP_MS = 250L

@Serializable
data class AutoMixAnalysis(
    val durationMs: Long,

    val bpm: Double,
    val beatIntervalMs: Double,

    val beatConfidence: Double,

    val downbeatPhaseMs: Double,
    val audibleStartMs: Long,
    val introEndMs: Long,
    val outroStartMs: Long,
    val contentEndMs: Long,

    val finalFadeOnsetMs: Long = 0L,
    val mixInCandidatesMs: List<Long>,
    val mixOutCandidatesMs: List<Long>,

    val energyCurve: List<Float>,

    val vocalActivity: List<Float>,
) {
    val isUsable: Boolean
        get() = durationMs > 0L && contentEndMs > 0L && energyCurve.isNotEmpty()

    fun energyAt(ms: Long): Float = curveValueAt(energyCurve, ms)

    fun vocalAt(ms: Long): Float = curveValueAt(vocalActivity, ms)

    private fun curveValueAt(curve: List<Float>, ms: Long): Float {
        if (curve.isEmpty()) return 0f
        val index = (ms / AUTO_MIX_CURVE_STEP_MS).toInt()
        return curve.getOrElse(index) { if (index < 0) curve.first() else curve.last() }
    }

    companion object {
        fun unusable(durationMs: Long) =
            AutoMixAnalysis(
                durationMs = durationMs,
                bpm = 0.0,
                beatIntervalMs = 0.0,
                beatConfidence = 0.0,
                downbeatPhaseMs = 0.0,
                audibleStartMs = 0L,
                introEndMs = 0L,
                outroStartMs = durationMs,
                contentEndMs = durationMs,
                finalFadeOnsetMs = durationMs,
                mixInCandidatesMs = emptyList(),
                mixOutCandidatesMs = emptyList(),
                energyCurve = emptyList(),
                vocalActivity = emptyList(),
            )
    }
}

enum class AutoMixAnalysisState {
    WAITING,
    ANALYSING,
    ANALYSED,
    FAILED,
}

data class AutoMixAnalysisStates(
    val current: AutoMixAnalysisState = AutoMixAnalysisState.WAITING,
    val next: AutoMixAnalysisState = AutoMixAnalysisState.WAITING,
)

data class AutoMixTransitionWindow(
    val startFraction: Float,
    val endFraction: Float,
)

object AutoMixUiState {
    val enabled = MutableStateFlow(false)
    val analysis = MutableStateFlow(AutoMixAnalysisStates())
    val transitionWindow = MutableStateFlow<AutoMixTransitionWindow?>(null)
    val mixing = MutableStateFlow(false)
}

enum class AutoMixStyle {
    GAPLESS,

    EQUAL_POWER,

    DJ_BLEND,

    DJ_FILTER,
}

data class AutoMixTrackInfo(
    val id: String,
    val durationMs: Long,
    val title: String,
    val artist: String,
    val album: String,
)

data class AutoMixPlan(
    val blocked: Boolean,
    val reason: String,
    val style: AutoMixStyle,

    val transitionStartMs: Long,
    val transitionEndMs: Long,
    val fadeMs: Long,

    val incomingCueMs: Long,

    val incomingPlaybackRate: Double,
    val bassSwap: Boolean,

    val bassSwapFraction: Double,

    val filterSweep: Double,

    val vocalOverlap: Double,
    val markerVisible: Boolean,
) {
    companion object {
        fun fallback(
            fadeMs: Long,
            anchorMs: Long,
            reason: String,
        ): AutoMixPlan =
            AutoMixPlan(
                blocked = false,
                reason = reason,
                style = AutoMixStyle.EQUAL_POWER,
                transitionStartMs = anchorMs,
                transitionEndMs = anchorMs + fadeMs,
                fadeMs = fadeMs,
                incomingCueMs = 0L,
                incomingPlaybackRate = 1.0,
                bassSwap = false,
                bassSwapFraction = 0.5,
                filterSweep = 0.0,
                vocalOverlap = 0.0,
                markerVisible = false,
            )
    }
}

enum class AutoMixPerformanceMode {
    EFFICIENT,
    BALANCED,
    PERFORMANCE,
}
