package com.music.bitchord.data.webdav

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * WebDAV library as [Song] rows, shaped like the on-device library so the
 * same Songs / Artists / Albums view can draw it.
 *
 * Stateless apart from [AppSettings]: every load re-lists the server. The
 * album of a track is its parent folder, which groups a "Music/Artist/Album"
 * remote layout back into releases without any tags.
 */
object WebDavRepository {

    fun isConfigured(): Boolean =
        WebDavConfig.isConfigured(AppSettings.webdavUrl.value)

    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val url = AppSettings.webdavUrl.value
        if (!WebDavConfig.isConfigured(url)) return@withContext emptyList()
        val listing = WebDavClient.listLibrary(
            baseUrl = url,
            username = AppSettings.webdavUsername.value,
            password = AppSettings.webdavPassword.value,
        ).getOrNull() ?: return@withContext emptyList()
        val artByDir = listing.images.groupBy { dirKey(it.url) }
        listing.audio.map { entry ->
            entry.toSong(artworkFor(entry.url, artByDir[dirKey(entry.url)].orEmpty()))
        }
    }

    suspend fun testConnection(
        url: String,
        username: String,
        password: String,
    ): Result<Unit> = WebDavClient.testConnection(url, username, password)

    fun WebDavClient.Entry.toSong(artworkUrl: String? = null): Song {
        val album = parentFolderName(url)
        return WebDavConfig.songFor(url, albumName = album).copy(
            // Prefer the server's display name over the URL-decoded guess when
            // it carries one.
            title = displayName.substringBeforeLast('.').takeIf { it.isNotBlank() }
                ?.let { splitTitle(it).second } ?: WebDavConfig.songFor(url).title,
            artist = splitTitle(displayName.substringBeforeLast('.')).first
                ?: WebDavConfig.songFor(url, album).artist,
            thumbnailUrl = artworkUrl,
        )
    }

    /**
     * The cover for a track: a picture filed beside it. Embedded pictures
     * inside the audio itself are not read — that would be a ranged fetch per
     * track at list time. See [RemoteArtwork][com.music.bitchord.data.remote.RemoteArtwork].
     */
    fun artworkFor(fileUrl: String, siblings: List<WebDavClient.Entry>): String? =
        com.music.bitchord.data.remote.RemoteArtwork.pick(siblings.map { it.url })

    private fun dirKey(fileUrl: String): String = runCatching {
        val uri = java.net.URI(fileUrl)
        "${uri.host.orEmpty()}${uri.path.orEmpty().trimEnd('/').substringBeforeLast('/', "")}"
    }.getOrDefault(fileUrl.substringBeforeLast('/'))

    private fun splitTitle(base: String): Pair<String?, String?> {
        return if (" - " in base) {
            val parts = base.split(" - ", limit = 2)
            parts[0].trim().takeIf { it.isNotBlank() } to parts[1].trim().takeIf { it.isNotBlank() }
        } else {
            null to base.trim().takeIf { it.isNotBlank() }
        }
    }

    internal fun parentFolderName(fileUrl: String): String? = runCatching {
        val path = java.net.URI(fileUrl).path.orEmpty().trimEnd('/')
        val parent = path.substringBeforeLast('/', "").substringAfterLast('/').trim()
        URLDecoder.decode(parent, "UTF-8").takeIf { it.isNotBlank() }
    }.getOrNull()
}
