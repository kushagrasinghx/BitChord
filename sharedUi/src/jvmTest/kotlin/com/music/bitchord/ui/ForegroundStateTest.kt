package com.music.bitchord.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStateTest {
    @Test fun focusedWindowRunsVisibleUiWork() {
        assertTrue(Lifecycle.State.RESUMED.isForeground())
    }

    @Test fun visibleUnfocusedWindowRunsVisibleUiWork() {
        assertTrue(Lifecycle.State.STARTED.isForeground())
    }

    @Test fun stoppedWindowSuspendsVisibleUiWork() {
        assertFalse(Lifecycle.State.CREATED.isForeground())
    }

    @Test fun uninitializedOrDestroyedWindowDoesNotRunVisibleUiWork() {
        assertFalse(Lifecycle.State.INITIALIZED.isForeground())
        assertFalse(Lifecycle.State.DESTROYED.isForeground())
    }

    @Test fun focusLossDoesNotSuspendWorkButStoppingDoes() {
        // Focused -> visible/unfocused -> stopped -> visible/unfocused -> focused.
        val states = listOf(
            Lifecycle.State.RESUMED,
            Lifecycle.State.STARTED,
            Lifecycle.State.CREATED,
            Lifecycle.State.STARTED,
            Lifecycle.State.RESUMED,
        )
        val expected = listOf(true, true, false, true, true)
        states.zip(expected).forEach { (state, foreground) ->
            assertEquals("UI work at $state", foreground, state.isForeground())
        }
    }
}
