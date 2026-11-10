/*
 * BRIDGE (replaces locals ArchiveTune declared in its MainActivity).
 *
 * Same names, same packages, same shapes as the originals so the copied
 * sources read them unchanged. The hosting player (ArchiveTunePlayerHost)
 * provides each of them before the stack composes.
 */

package moe.rukamori.archivetune

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalStableSystemBarsTopPadding = staticCompositionLocalOf<Dp> { 0.dp }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
