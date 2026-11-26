package com.music.bitchord.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Media3's navigation helper treats repeat-one as repeat-off; natural playback does not. */
internal fun Player.expectedArtworkIndex(arming: Boolean): Int? {
    if ((!playWhenReady && !arming) || playbackState == Player.STATE_IDLE ||
        playbackState == Player.STATE_ENDED || repeatMode == Player.REPEAT_MODE_ONE
    ) return null
    return nextMediaItemIndex.takeIf { it in 0 until mediaItemCount && it != currentMediaItemIndex }
}

/** One speculative worker per service, confined to the player's application looper. */
internal class NextArtworkPrefetch<T : Any>(
    scope: CoroutineScope,
    prepare: suspend (T, isCurrent: () -> Boolean) -> Unit,
) {
    private class Selection<T>(val target: T)

    private val selection = MutableStateFlow<Selection<T>?>(null)

    init {
        scope.launch {
            selection.collectLatest { selected ->
                if (selected == null || selected !== selection.value) return@collectLatest
                val worker = currentCoroutineContext()
                // collectLatest cancels and joins the previous worker before entering here.
                prepare(selected.target) { selected === selection.value && worker.isActive }
            }
        }
    }

    fun replace(target: T?) {
        if (selection.value?.target != target) selection.value = target?.let(::Selection)
    }
}
