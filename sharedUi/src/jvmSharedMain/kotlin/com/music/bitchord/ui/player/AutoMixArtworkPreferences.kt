package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState

/** Saved choices plus in-memory fences for mixes interrupted by a preference change. */
data class AutoMixArtworkPreferences(
    // Both off until the listener turns them on: AutoMix audio does not depend on them.
    val artTransition: Boolean = false,
    val miniPlayerTransition: Boolean = false,
    val skipArtworkThrough: Long = 0L,
    val skipMiniThrough: Long = 0L,
) {
    internal fun gate(automix: Boolean, mini: Boolean = false) = AutoMixArtworkGate(
        enabled = automix && (if (mini) miniPlayerTransition else artTransition),
        skipThrough = if (mini) skipMiniThrough else skipArtworkThrough,
    )

    fun allows(automix: Boolean, generation: Long, mini: Boolean = false): Boolean =
        gate(automix, mini).allows(generation)
}

internal data class AutoMixArtworkGate(
    val enabled: Boolean = true,
    val skipThrough: Long = 0L,
    val remoteArtworkScope: Long? = null,
) {
    fun allows(generation: Long) = enabled && generation > skipThrough
    fun allows(visual: AutoMixVisualState) = visual.admitsArtwork && allows(visual.generation)

    fun holdsArtworkResources(visual: AutoMixVisualState): Boolean =
        allows(visual) && matchesScope(visual.remoteArtworkScope) &&
            (visual.mixActive || visual.phase == AutoMixVisualPhase.ARMED)
    fun matchesScope(scope: Long?) = scope == null || scope == remoteArtworkScope
}

@Composable
internal fun autoMixArtworkGate(mini: Boolean = false): AutoMixArtworkGate {
    val automix by PlayerSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val preferences by PlayerSettings.autoMixArtwork.collectAsStateWithLifecycle()
    val remoteScope = PlayerPlatform.host.remoteArtworkScope?.collectAsStateWithLifecycle()?.value
    return preferences.gate(automix, mini).copy(remoteArtworkScope = remoteScope)
}
