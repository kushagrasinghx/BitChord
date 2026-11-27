@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.backup.DeviceIdentity
import com.music.bitchord.data.backup.DeviceManifest
import com.music.bitchord.data.backup.SyncPlanner
import com.music.bitchord.data.stats.PrefValue
import kotlinx.serialization.json.Json

/*
 * Google Drive sync, as the Android app does it (ReplaySync.kt): what to upload, download
 * and drop, and the device manifest in exactly the bytes the phone reads and writes, so the
 * web player is one more device in the same Drive. Maps and Longs become arrays and Doubles.
 */

/** ReplaySync's own Json: unknown keys skipped (a newer phone's manifest), defaults written. */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** One month and a hash of it: SHA-256 of its canonical bytes, or Drive's MD5 of the stored file. */
@JsExport
class MonthHash(val month: String, val hash: String?)

private fun Array<MonthHash>.hashes(): Map<String, String> = mapNotNull { m -> m.hash?.let { m.month to it } }.toMap()

/** SyncPlanner.Push. */
@JsExport
class SyncPushPlan(val upload: Array<String>, val delete: Array<String>)

/** SyncPlanner.push: this device's months to upload, and to delete from Drive. */
@JsExport
fun syncPush(local: Array<MonthHash>, synced: Array<MonthHash>, remote: Array<String>): SyncPushPlan =
    SyncPlanner.push(local.hashes(), synced.hashes(), remote.toSet())
        .let { SyncPushPlan(it.upload.toTypedArray(), it.delete.toTypedArray()) }

/** SyncPlanner.Pull. */
@JsExport
class SyncPullPlan(val download: Array<String>, val drop: Array<String>)

/** SyncPlanner.pull: one other device's months to fetch ([remoteMd5] is every file in Drive), and to drop. */
@JsExport
fun syncPull(listed: Array<String>, remoteMd5: Array<MonthHash>, held: Array<MonthHash>): SyncPullPlan =
    SyncPlanner.pull(listed.toSet(), remoteMd5.associate { it.month to it.hash }, held.hashes())
        .let { SyncPullPlan(it.download.toTypedArray(), it.drop.toTypedArray()) }

/** SyncPlanner.supersededBy: another install has restored as this device since this one took it. */
@JsExport
fun syncSupersededBy(installId: String, adoptedManifestAt: String?, manifestJson: String?, manifestAt: String?): Boolean =
    SyncPlanner.supersededBy(
        DeviceIdentity(installId = installId, adoptedManifestAt = adoptedManifestAt),
        manifestJson?.let { runCatching { json.decodeFromString(DeviceManifest.serializer(), it) }.getOrNull() },
        manifestAt,
    )

/** Backup.kt's PrefValue under its preference key. */
@JsExport
class WebPref(val key: String, val type: String, val value: String?, val values: Array<String>)

/** DeviceManifest: one device's record in Drive. */
@JsExport
class WebManifest(
    val schema: Int,
    val deviceId: String,
    val name: String,
    val ownerInstall: String,
    val updatedAt: Double,
    val versionName: String,
    val months: Array<MonthHash>,
    val settings: Array<WebPref>,
)

/** Reads a device manifest (gunzipped), as ReplaySync.readManifest does. Throws on one that isn't. */
@JsExport
fun decodeManifest(text: String): WebManifest {
    val m = json.decodeFromString(DeviceManifest.serializer(), text)
    return WebManifest(
        schema = m.schema,
        deviceId = m.deviceId,
        name = m.name,
        ownerInstall = m.ownerInstall,
        updatedAt = m.updatedAt.toDouble(),
        versionName = m.versionName,
        months = m.months.map { (month, hash) -> MonthHash(month, hash) }.toTypedArray(),
        settings = m.settings.map { (k, v) -> WebPref(k, v.type, v.value, v.values.toTypedArray()) }.toTypedArray(),
    )
}

/** Writes this device's manifest (to be gzipped), as ReplaySync.sync does after its months. */
@JsExport
fun encodeManifest(
    deviceId: String,
    name: String,
    ownerInstall: String,
    updatedAt: Double,
    versionName: String,
    months: Array<MonthHash>,
    settings: Array<WebPref>,
): String = json.encodeToString(
    DeviceManifest.serializer(),
    DeviceManifest(
        deviceId = deviceId,
        name = name,
        ownerInstall = ownerInstall,
        updatedAt = updatedAt.toLong(),
        versionName = versionName,
        months = months.hashes(),
        settings = settings.associate { it.key to PrefValue(it.type, it.value, it.values.toList()) },
    ),
)
