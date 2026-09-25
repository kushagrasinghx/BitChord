package com.music.bitchord.data.webdav

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebDAV library as [Song] rows, shaped like the on-device library so the
 * same Songs / Artists / Albums view can draw it.
 *
 * Stateless apart from [AppSettings]: every load re-lists the server. Tracks
 * are credited from their place below the configured URL, which groups an
 * "Artist/Album" remote layout back into releases without any tags.
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

    fun WebDavClient.Entry.toSong(artworkUrl: String? = null): Song =
        WebDavConfig.songFor(url, baseUrl = AppSettings.webdavUrl.value, displayName = displayName)
            .copy(thumbnailUrl = artworkUrl)

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
}
