package com.music.bitchord.data.remote

import java.net.URLDecoder
import java.util.Locale

/**
 * Picks a track's cover from the pictures filed beside it. Shared by every
 * remote file library (WebDAV, SMB): `cover.*` and friends win over a stray
 * `IMG_1234.jpg`, because a folder a download manager drops is full of
 * exactly those; with nothing filed, there is no cover rather than a wrong
 * one.
 *
 * Grouping by directory is the caller's job — only it knows what a
 * "directory" is on its protocol.
 */
object RemoteArtwork {

    fun pick(siblingUrls: List<String>): String? {
        if (siblingUrls.isEmpty()) return null
        return siblingUrls.sortedWith(
            compareBy(
                { url ->
                    preferredStems.indexOfFirst { stem(url).startsWith(it) }
                        .takeIf { it >= 0 } ?: Int.MAX_VALUE
                },
                { stem(it) },
            ),
        ).first()
    }

    private val preferredStems = listOf("cover", "folder", "front", "albumart", "album", "artwork")

    private fun stem(fileUrl: String): String = runCatching {
        URLDecoder.decode(fileUrl.trimEnd('/').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(fileUrl.trimEnd('/').substringAfterLast('/'))
        .substringBeforeLast('.')
        .lowercase(Locale.ROOT)
}
