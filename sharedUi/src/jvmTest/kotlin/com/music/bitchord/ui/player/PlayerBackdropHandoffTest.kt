package com.music.bitchord.ui.player

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerBackdropHandoffTest {
    @Test
    fun warmSelectionStartsWithIncomingBackdrop() = runBlocking {
        val frames = Frames(Recomposer(coroutineContext))
        try {
            assertEquals("blue", frames.render("first", "blue"))
            assertEquals("red", frames.render("second", "red"))
            assertEquals("green", frames.render("third", "green"))
        } finally {
            frames.close()
        }
    }

    @Test
    fun coldSelectionClearsPreviousBackdrop() = runBlocking {
        val frames = Frames(Recomposer(coroutineContext))
        try {
            assertEquals("blue", frames.render("first", "blue"))
            assertEquals(null, frames.render("queue jump", null))
        } finally {
            frames.close()
        }
    }

    @Test
    fun activeMixKeepsItsOutgoingAnimationStateThroughMetadataSwap() = runBlocking {
        val frames = Frames(Recomposer(coroutineContext))
        try {
            assertEquals("blue", frames.render("first", "blue"))
            assertEquals("blue", frames.render("first", "blue", mixing = true))
            assertEquals("blue", frames.render("second", "red", mixing = true))
            assertEquals("red", frames.render("second", "red"))
        } finally {
            frames.close()
        }
    }

    @Test
    fun sameArtworkOnAnotherQueueEntryUsesItsStillRatherThanThePreviousCanvasFrame() = runBlocking {
        val frames = Frames(Recomposer(coroutineContext))
        try {
            assertEquals("canvas", frames.render("entry 1", "canvas", artwork = "shared cover"))
            assertEquals("still", frames.render("entry 2", "still", artwork = "shared cover"))
        } finally {
            frames.close()
        }
    }

    @Test
    fun changesWithinOneSelectionKeepTheAnimationState() = runBlocking {
        val frames = Frames(Recomposer(coroutineContext))
        try {
            assertEquals("still", frames.render("first", "still"))
            assertEquals("still", frames.render("first", "canvas frame"))
            assertEquals("new source", frames.render("first", "new source", artwork = "new URL"))
        } finally {
            frames.close()
        }
    }

    private class Frames(private val recomposer: Recomposer) {
        private val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)

        // Observe the initial drawable, before any effect or animation can advance.
        fun render(selection: String, target: String?, mixing: Boolean = false, artwork: String = selection): String? {
            var presented: String? = null
            composition.setContent {
                key(rememberBackdropSelectionKey(selection to artwork, mixing = mixing)) {
                    val initialDrawable = remember { target }
                    SideEffect { presented = initialDrawable }
                }
            }
            return presented
        }

        fun close() {
            composition.dispose()
            recomposer.cancel()
        }
    }
}
