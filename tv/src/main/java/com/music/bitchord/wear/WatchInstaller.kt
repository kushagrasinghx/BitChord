package com.music.bitchord.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The TV app's stand-in for the phone's watch installer
 * (app/src/main/java/.../wear/WatchInstaller.kt). A TV has no watch to install
 * on, so none of it — the ADB client least of all — is built in.
 */
object WatchInstaller {
    val watches: StateFlow<List<WatchEntry>?> = MutableStateFlow(emptyList())
    val update: StateFlow<WatchUpdateState> = MutableStateFlow(WatchUpdateState.Idle)
    val pairing: StateFlow<WatchPairingState> = MutableStateFlow(WatchPairingState.Idle)

    @Suppress("UNUSED_PARAMETER")
    fun available(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun refresh(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun sendUpdate(context: Context, nodeId: String) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun startPairing(context: Context) = Unit

    fun stopPairing() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun pairAndInstall(context: Context, nodeId: String, code: String) = Unit
}
