package com.music.bitchord.data.smb

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.remote.RemoteArtwork
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB library as [Song] rows, shaped like every other library in the app so
 * the same Songs / Artists / Albums view can draw it.
 *
 * Stateless apart from [AppSettings]: every load re-lists the share. The
 * album of a track is its parent folder, which groups a "Music/Artist/Album"
 * share layout back into releases without any tags — and covers are the
 * pictures filed beside the music, matched by directory.
 */
object SmbRepository {

    fun isConfigured(): Boolean =
        SmbConfig.isConfigured(AppSettings.smbHost.value, AppSettings.smbShare.value)

    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val host = SmbAuth.host
        val share = SmbAuth.share
        val listing = SmbClient.listLibrary().getOrThrow()
        val artByDir = listing.images.groupBy { dirKey(it.path) }
        listing.audio.map { entry ->
            toSong(host, share, entry, artByDir[dirKey(entry.path)].orEmpty())
        }
    }

    suspend fun testConnection(
        host: String,
        share: String,
        basePath: String,
        username: String,
        password: String,
    ): Result<Unit> = SmbClient.testConnection(host, share, basePath, username, password)

    private fun toSong(
        host: String,
        share: String,
        entry: SmbClient.Entry,
        siblings: List<SmbClient.Entry>,
    ): Song {
        // Entry paths accumulate from the base folder down, so they are
        // already share-rooted.
        val fullPath = entry.path
        val album = fullPath.substringBeforeLast('/', "").substringAfterLast('/').trim()
            .takeIf { it.isNotBlank() }
        val coverUrls = siblings.map { streamUrl(host, share, it.path) }
        return SmbConfig.songFor(host, share, fullPath, albumName = album).copy(
            thumbnailUrl = RemoteArtwork.pick(coverUrls),
        )
    }

    private fun streamUrl(host: String, share: String, path: String): String =
        "smb://${SmbConfig.normalizeHost(host)}/${share.trim().trim('/')}/${path.trim('/')}"

    private fun dirKey(path: String): String =
        path.trim('/').substringBeforeLast('/', "").lowercase(java.util.Locale.ROOT)
}
