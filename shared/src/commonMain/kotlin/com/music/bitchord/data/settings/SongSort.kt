package com.music.bitchord.data.settings

/**
 * Ordering for the track list on an album or playlist page — the same idea as
 * the Downloads folder's sort, with a date option for the one thing a
 * catalogue row can still be dated by: the position it sits at. A playlist's
 * running order is the order songs were added in — YouTube Music appends each
 * addition at the foot — so read backwards it *is* a date order, newest first.
 * DetailScreen.kt holds the sort itself. Persisted app-wide rather than per
 * page: one choice, kept until the user makes another.
 */
enum class SongSort {
    DEFAULT,

    /**
     * By the file's own track-number tag — only offered on a page whose rows
     * carry one, which in practice means an album off the device's files.
     */
    TRACK_NUMBER,
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED_ASC,
    DATE_ADDED_DESC,
}
