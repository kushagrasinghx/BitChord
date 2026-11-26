package com.music.bitchord.data.settings

/** Stable persisted ordering for each on-device music library (moved from AppSettings.kt with LocalGroups.kt's sortedForLibrary). */
enum class LocalMusicSort {
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED,
    DATE_MODIFIED,
}
