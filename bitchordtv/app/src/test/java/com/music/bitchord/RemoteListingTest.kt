package com.music.bitchord

import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.remote.RemoteListing
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteListingTest {

    @Test
    fun listsWhatTheServerReturned() {
        assertEquals(UiState.Success(listOf("a", "b")), RemoteListing.state(Result.success(listOf("a", "b")), "empty"))
    }

    @Test
    fun emptyListingReadsAsEmpty() {
        assertEquals(UiState.Error("empty"), RemoteListing.state(Result.success(emptyList<String>()), "empty"))
    }

    @Test
    fun failedListingNamesItsCause() {
        val denied = IllegalStateException("STATUS_ACCESS_DENIED (0xc0000022): Could not connect to \\\\nas\\Music")
        assertEquals(
            UiState.Error("STATUS_ACCESS_DENIED (0xc0000022): Could not connect to \\\\nas\\Music"),
            RemoteListing.state(Result.failure<List<String>>(denied), "empty"),
        )
    }

    @Test
    fun failureWithoutMessageNamesItsType() {
        assertEquals(
            UiState.Error("NullPointerException"),
            RemoteListing.state(Result.failure<List<String>>(NullPointerException()), "empty"),
        )
        assertEquals(
            UiState.Error("IllegalStateException"),
            RemoteListing.state(Result.failure<List<String>>(IllegalStateException("  ")), "empty"),
        )
    }
}
