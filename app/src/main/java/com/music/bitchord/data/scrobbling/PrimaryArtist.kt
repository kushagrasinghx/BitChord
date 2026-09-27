package com.music.bitchord.data.scrobbling

/**
 * Returns the first credited artist for services that do not handle joint
 * artist credits well. The original value is preserved when no separator is
 * present so names such as "AC/DC" remain intact.
 */
internal fun String.primaryArtist(): String =
    split(PRIMARY_ARTIST_SEPARATOR, limit = 2).first().trim().ifBlank { this }

private val PRIMARY_ARTIST_SEPARATOR = Regex("""\s*,\s*|\s+&\s+|\s+＆\s+""")
