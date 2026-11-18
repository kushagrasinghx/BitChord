package com.music.bitchord.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncOperationMutexTest {
    @Test
    fun sameProfileOperationsSerializeAcrossCoordinatorInstances() = runBlocking {
        val order = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            SyncOperationMutex.withProfileLock("profile-1") {
                order += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-end"
            }
        }
        firstEntered.await()
        val second = async {
            SyncOperationMutex.withProfileLock("profile-1") {
                order += "second"
            }
        }

        yield()
        assertEquals(listOf("first-start"), order)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), order)
    }
}
