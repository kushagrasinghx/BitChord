package com.music.bitchord.wear

/**
 * What Settings → "BitChord for Wear OS" draws, kept apart from
 * [WatchInstaller] so the TV build — which replaces that file with a stub —
 * still has the types.
 */
data class WatchEntry(
    /** The Wearable node id: what every message to this watch is addressed to. */
    val nodeId: String,
    /** What the watch calls itself — "Galaxy Watch4 Classic (WZTM)". */
    val name: String,
    val status: WatchAppStatus,
    /** The watch app's versionName, once it has said. */
    val versionName: String? = null,
)

enum class WatchAppStatus {
    /** Asked; no answer yet. */
    CHECKING,

    /** This build of BitChord (dev or prod) isn't on the watch. */
    NOT_INSTALLED,

    /** The watch has exactly the build this phone carries. */
    UP_TO_DATE,

    /** The watch has a different build from the one this phone carries. */
    UPDATE_AVAILABLE,

    /** Installed, but it didn't answer — an old build that predates the question, or asleep. */
    UNKNOWN,
}

/** Sending this phone's watch build to a watch that already has BitChord. */
sealed interface WatchUpdateState {
    data object Idle : WatchUpdateState
    data class Sending(val nodeId: String) : WatchUpdateState

    /** On the watch now; the installer may want a tap there. */
    data class Sent(val nodeId: String) : WatchUpdateState
    data class Failed(val nodeId: String, val reason: String) : WatchUpdateState
}

/**
 * The first install, which only ADB can do without the Play Store: this phone
 * is the ADB client, over the watch's Wireless debugging, on the same Wi-Fi.
 */
sealed interface WatchPairingState {
    data object Idle : WatchPairingState

    /** Looking on the Wi-Fi for a watch showing its pairing code. */
    data object Searching : WatchPairingState

    /** A watch's pairing service is up; waiting for the six digits. */
    data class Found(val host: String) : WatchPairingState
    data object Pairing : WatchPairingState

    /** Paired and connected; the APK is going over. */
    data object Installing : WatchPairingState
    data object Installed : WatchPairingState
    data class Failed(val reason: String) : WatchPairingState
}
