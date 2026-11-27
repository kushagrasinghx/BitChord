package com.music.bitchord.data.backup

import com.music.bitchord.data.stats.PrefValue
import kotlinx.serialization.Serializable

/**
 * Who this install is, as far as sync is concerned.
 *
 * [deviceId] names the *partition* this phone writes — the only listening it
 * ever uploads — and is null until the listener has chosen to back up as a new
 * device or restore as an existing one. [installId] names this install, and is
 * what a device's manifest records as its owner: when someone restores *as*
 * this device on another phone, the owner changes, and this install finds out
 * on its next sync (see [ReplaySync.sync]).
 */
data class DeviceIdentity(
    val installId: String,
    val deviceId: String? = null,
    val deviceName: String = "",
    /**
     * The manifest's Drive timestamp when this install adopted [deviceId], or
     * null for one it created. A manifest owned by another install and stamped
     * differently means another phone has written it since.
     */
    val adoptedManifestAt: String? = null,
)

/**
 * One device's record in Drive, written *after* its months: a peer reads only
 * the months listed here, so a sync cut off halfway is never half-read.
 */
@Serializable
data class DeviceManifest(
    val schema: Int = SCHEMA,
    val deviceId: String,
    val name: String = "",
    val ownerInstall: String = "",
    val updatedAt: Long = 0L,
    val versionName: String = "",
    /** Month → SHA-256 of its canonical bytes. */
    val months: Map<String, String> = emptyMap(),
    /** That device's settings, restored only by restoring as it. */
    val settings: Map<String, PrefValue> = emptyMap(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

/**
 * What a sync has to do, worked out from what is here and what is in Drive
 * with no I/O of its own, so it can be tested on its own. See [ReplaySync].
 */
object SyncPlanner {

    data class Push(val upload: List<String>, val delete: List<String>)

    /**
     * This device's months to upload — changed since last uploaded, or
     * missing from Drive — and to delete, pruned here but still in Drive.
     */
    fun push(local: Map<String, String>, synced: Map<String, String>, remote: Set<String>): Push = Push(
        upload = local.filter { (month, hash) -> synced[month] != hash || month !in remote }.keys.sorted(),
        delete = (remote - local.keys).sorted(),
    )

    data class Pull(val download: List<String>, val drop: List<String>)

    /**
     * One other device's months to fetch — listed in its manifest, present in
     * Drive, and changed since the copy held — and to drop, held here but no
     * longer listed.
     */
    fun pull(listed: Set<String>, remoteMd5: Map<String, String?>, held: Map<String, String>): Pull {
        val available = listed.filter { it in remoteMd5 }.toSet()
        return Pull(
            download = available.filter { held[it] == null || held[it] != remoteMd5[it] }.sorted(),
            drop = (held.keys - available).sorted(),
        )
    }

    /** Whether another install has restored as this device since this one took it. */
    fun supersededBy(identity: DeviceIdentity, manifest: DeviceManifest?, manifestAt: String?): Boolean =
        manifest != null &&
            manifest.ownerInstall.isNotEmpty() &&
            manifest.ownerInstall != identity.installId &&
            manifestAt != identity.adoptedManifestAt
}
