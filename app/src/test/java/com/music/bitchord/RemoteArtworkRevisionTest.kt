package com.music.bitchord

import com.music.bitchord.data.settings.RemoteArtworkRevision
import com.music.bitchord.data.settings.remoteArtworkSourceFingerprint
import com.music.bitchord.data.settings.restoredRemoteArtworkScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteArtworkRevisionTest {
    @Test fun restartDoesNotReuseScopeAfterOnlyCredentialsReachDisk() {
        val previous = remoteArtworkSourceFingerprint(listOf("same endpoint", "previous credential state"))
        val current = remoteArtworkSourceFingerprint(listOf("same endpoint", "new credential state"))
        val record = "41:$previous"
        assertEquals(41L, restoredRemoteArtworkScope(record, previous) { 90L })
        assertEquals(90L, restoredRemoteArtworkScope(record, current) { 90L })
    }

    @Test fun keepsPersistedScopeOnStartupAndDoesNotInvalidateUnchangedEffectiveSettings() {
        val revision = RemoteArtworkRevision<String>()
        val persisted = mutableListOf<Long>()
        val published = mutableListOf<Pair<Long, String>>()
        fun update(source: String) = revision.update({ 41L }, { scope, _ -> persisted.add(scope) }, { source }) { scope, value, _ ->
            published += scope to value
        }
        update("original source")
        update("original source")
        update("changed source")
        assertEquals(listOf(41L, 42L), persisted)
        assertEquals(listOf(41L to "original source", 42L to "changed source"), published)
    }

    @Test fun concurrentRestoreAndCredentialEditPublishDifferentScopesInMutationOrder() {
        val revision = RemoteArtworkRevision<String>()
        val restoreEntered = CountDownLatch(1)
        val releaseRestore = CountDownLatch(1)
        val startedEdit = CountDownLatch(1)
        val publications = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, String>>()
        val pool = Executors.newFixedThreadPool(2)
        fun update(edit: () -> String) = revision.update({ 7L }, { _, _ -> }, edit) { scope, source, _ ->
            publications.add(scope to source)
        }
        update { "original source" }
        try {
            val restore = pool.submit {
                update {
                    restoreEntered.countDown()
                    assertTrue(releaseRestore.await(5, SECONDS))
                    "restored source"
                }
            }
            assertTrue(restoreEntered.await(5, SECONDS))
            val edit = pool.submit {
                startedEdit.countDown()
                update { "edited credentials" }
            }
            assertTrue(startedEdit.await(5, SECONDS))
            // A concurrent edit either waits for restore or exposes the old duplicated revision.
            try { edit.get(200, java.util.concurrent.TimeUnit.MILLISECONDS) }
            catch (_: java.util.concurrent.TimeoutException) { /* Correctly held until restore commits. */ }
            releaseRestore.countDown()
            restore.get(5, SECONDS)
            edit.get(5, SECONDS)
            assertEquals(listOf(7L to "original source", 8L to "restored source", 9L to "edited credentials"), publications.toList())
        } finally {
            releaseRestore.countDown()
            pool.shutdownNow()
        }
    }
}
