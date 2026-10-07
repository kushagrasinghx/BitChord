package com.music.bitchord.data.settings

/**
 * Stable persisted ordering for a Library "Show all" grid — playlists or
 * albums. A card there only ever carries a title, so unlike `LocalMusicSort`
 * there is nothing date-based to offer.
 */
enum class LibrarySort {
    /** Whatever order the shelf itself arrived in — YouTube Music's own. */
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
}

/** Display mode for music lists: compact rows or grid cards. */
enum class LibraryViewType {
    LIST,
    GRID,
}
