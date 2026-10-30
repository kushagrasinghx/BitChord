package com.music.bitchord.ui.player

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The live vocal remover's UI contract. The engine itself lives in the app
 * module (`playback/vocalremover/VocalRemover.kt`, built on the ported StemSep
 * `:separation` modules — see docs/VOCAL_REMOVER.md); this is what the player
 * screens need from it, kept here so sharedUi never depends back on the app.
 */
enum class VocalRemoverPhase {
    OFF,
    DOWNLOADING_MODEL,
    FETCHING_STREAM,
    DECODING,
    SEPARATING,
    ACTIVE,
    UNSUPPORTED_SOURCE,
    WAITING_FOR_TRACK,
    ERROR,
}

/**
 * Everything the temporary debug panel shows. TEMPORARY as a unit: removing
 * the panel means deleting this class and its plumbing, nothing else.
 */
data class VocalRemoverDebug(
    val videoId: String = "",
    val sessionUuid: String = "",
    val modelId: String = "",
    val modelSha: String = "",
    val threadsSetting: Int = 0,
    val workerThreads: Int = 0,
    val deviceCores: Int = 0,
    val backend: String = "",
    val fallback: String? = null,
    val streamBytes: Long = 0,
    val paths: String = "",
    val startFrame: Long = 0,
    val pcmFrames: Long = 0,
    val availableFrames: Long = 0,
    val playheadFrame: Long = 0,
    val expectedFrame: Long = 0,
    val leadMs: Long = 0,
    val driftMs: Long = 0,
    val chunkCount: Int = 0,
    val lastChunkMs: Long = 0,
    val avgChunkMs: Long = 0,
    val stallingTicks: Int = 0,
    val restartCount: Int = 0,
    val phaseStartedAt: Long = 0,
    val lastError: String? = null,
)

data class VocalRemoverState(
    val enabled: Boolean = false,
    val phase: VocalRemoverPhase = VocalRemoverPhase.OFF,
    val fraction: Float = 0f,
    val error: String? = null,
    val vocalLevel: Float = 0f,
    val totalStemMs: Long = 0,
    val availableStemMs: Long = 0,
    val playheadMs: Long = 0,
    val debug: VocalRemoverDebug = VocalRemoverDebug(),
)

/** What the capsule drives: enable/disable, and the vocal mix-back level. */
interface VocalRemoverController {
    val snapshot: StateFlow<VocalRemoverState>

    fun setEnabled(enabled: Boolean)

    /** 0 = vocals fully removed, 1 = vocals mixed back at full level. */
    fun setVocalLevel(level: Float)
}

/**
 * Provided by hosts that run the separation engine (the phone app). Null
 * elsewhere — the desktop build has no engine, and the control hides itself.
 */
val LocalVocalRemover = staticCompositionLocalOf<VocalRemoverController?> { null }
