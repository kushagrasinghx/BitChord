package com.music.bitchord.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a sync uploads, downloads and drops — and that doing it twice does nothing. */
class SyncPlannerTest {

    @Test
    fun push_uploadsChangedAndMissingMonths_deletesPrunedOnes() {
        val plan = SyncPlanner.push(
            local = mapOf("2026-10" to "h10", "2026-11" to "h11-new", "2026-09" to "h09"),
            synced = mapOf("2026-10" to "h10", "2026-11" to "h11-old", "2026-09" to "h09"),
            remote = setOf("2026-10", "2026-11", "2023-01"),
        )
        // 11 changed; 09 was uploaded once but is gone from Drive.
        assertEquals(listOf("2026-09", "2026-11"), plan.upload)
        assertEquals(listOf("2023-01"), plan.delete)
    }

    @Test
    fun push_afterAnUpload_hasNothingToDo() {
        val local = mapOf("2026-10" to "h10", "2026-11" to "h11")
        val plan = SyncPlanner.push(local = local, synced = local, remote = local.keys)
        assertTrue(plan.upload.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun pull_fetchesOnlyListedChangedMonths_andDropsUnlistedOnes() {
        val plan = SyncPlanner.pull(
            // 2026-12 is in Drive but not yet in the manifest: an upload in progress.
            listed = setOf("2026-10", "2026-11"),
            remoteMd5 = mapOf("2026-10" to "m10", "2026-11" to "m11-new", "2026-12" to "m12"),
            held = mapOf("2026-10" to "m10", "2026-11" to "m11-old", "2025-01" to "m01"),
        )
        assertEquals(listOf("2026-11"), plan.download)
        assertEquals(listOf("2025-01"), plan.drop)
    }

    @Test
    fun pull_isIdempotent() {
        val remote = mapOf("2026-10" to "m10", "2026-11" to "m11")
        val first = SyncPlanner.pull(listed = remote.keys, remoteMd5 = remote, held = emptyMap())
        assertEquals(listOf("2026-10", "2026-11"), first.download)

        // Once held, the same listing asks for nothing — a re-sync can't add twice.
        val again = SyncPlanner.pull(listed = remote.keys, remoteMd5 = remote, held = remote)
        assertTrue(again.download.isEmpty())
        assertTrue(again.drop.isEmpty())
    }

    @Test
    fun supersededBy_onlyWhenAnotherInstallWroteTheManifestSince() {
        val me = DeviceIdentity(installId = "me", deviceId = "dev", adoptedManifestAt = "t1")
        fun manifest(owner: String) = DeviceManifest(deviceId = "dev", ownerInstall = owner)

        // Mine.
        assertFalse(SyncPlanner.supersededBy(me, manifest("me"), "t9"))
        // Just adopted: the manifest still names the old owner, unchanged since.
        assertFalse(SyncPlanner.supersededBy(me, manifest("old"), "t1"))
        // Nothing in Drive yet.
        assertFalse(SyncPlanner.supersededBy(me, null, null))
        // Another install has written it since.
        assertTrue(SyncPlanner.supersededBy(me, manifest("other"), "t2"))
        // A device this install created: any other owner means a restore elsewhere.
        assertTrue(SyncPlanner.supersededBy(me.copy(adoptedManifestAt = null), manifest("other"), "t2"))
    }
}
