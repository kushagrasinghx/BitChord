package com.music.bitchord.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import androidx.annotation.RequiresApi
import com.music.bitchord.data.model.Song
import java.io.File
import java.io.OutputStream

/**
 * Where a downloaded track goes, and how it gets there.
 *
 * The destination is the device's own Music folder, in a `BitChord`
 * subfolder — somewhere the file manager lists, other players can open, and a
 * user can back up or delete without going through this app. That choice is
 * what makes this class necessary at all: an app-private directory would be
 * four lines of [File], but a shared one crosses the scoped-storage line and
 * the two sides of that line have nothing in common.
 *
 * It goes through the audio collection rather than Downloads specifically
 * because the files are `.webm` — a container extension Android's own mime
 * table ties to video regardless of what MIME type this class declares for
 * it — and a Gallery app crawling Downloads for video-looking files does not
 * care what column says otherwise. The audio collection is not on that path.
 *
 *  - **API 29+** goes through [MediaStore]. There is no filesystem path to
 *    write to; the store mints a row, hands back a content uri, and the file
 *    exists at a location it chooses. `IS_PENDING` keeps the row invisible to
 *    everything else until the bytes are all there, so a cancelled download is
 *    never a half-file somebody can find and play.
 *  - **API 26–28** is a real path and a runtime permission. The file is written
 *    beside its final name with a `.part` suffix and renamed on completion,
 *    which is the same guarantee `IS_PENDING` gives for free above, and the
 *    media scanner is told afterwards or the file stays invisible to everything
 *    that reads the index rather than the disk.
 *
 * Neither side writes tags — this class only ever copies the bytes googlevideo
 * sends. [MediaTagger] rewrites the finished file afterwards to add them; the
 * filename below is what every downloaded track carries regardless of
 * whether that rewrite finds a layout it recognises.
 */
object DownloadStore {

    private const val TAG = "BitChord"

    /** The subfolder of Music that everything lands in. */
    const val FOLDER = "BitChord"

    private val relativePath = "${Environment.DIRECTORY_MUSIC}/$FOLDER"

    /**
     * Whether saving needs `WRITE_EXTERNAL_STORAGE` asked for at runtime.
     *
     * Only below API 29. From there on the app writes through the media store,
     * which grants access to rows it created and needs no permission for them —
     * and the permission it would ask for isn't grantable anyway.
     *
     * Every version check in this file is written out inline rather than
     * routed through this, deliberately: lint reads an inline `SDK_INT`
     * comparison as a guard around the API-29 calls beside it and does not
     * read a boolean property the same way, so hiding the check behind a name
     * costs a `NewApi` error on the release build.
     */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    // ---- Naming -------------------------------------------------------------

    /**
     * What the file is called: `Artist - Title.ext`.
     *
     * Artist first because a Music folder is sorted by name and nothing
     * else — no tags to group by — so leading with the artist is the only thing
     * that puts an album back together in the listing.
     */
    fun fileNameFor(song: Song, extension: String): String {
        val artist = sanitise(song.artist)
        val title = sanitise(song.title)
        val stem = when {
            artist.isEmpty() -> title
            title.isEmpty() -> artist
            else -> "$artist - $title"
        }.ifEmpty { song.videoId }
        return "${stem.take(MAX_STEM_CHARS).trimEnd()}.$extension"
    }

    /**
     * Everything a FAT32 volume, the media store or a shell would each object
     * to for its own reasons, plus the whitespace that survives them.
     */
    private fun sanitise(raw: String): String = raw
        .replace(ILLEGAL, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .trim('.')

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    private val WHITESPACE = Regex("""\s+""")

    /** Long enough for anything real, short of the 255-byte filename ceiling. */
    private const val MAX_STEM_CHARS = 120

    // ---- Lookup -------------------------------------------------------------

    /**
     * The uri of a file already saved under this name, or null.
     *
     * Worth asking before every download because the media store does not
     * refuse a duplicate — it silently renames it to `… (1)`, and a user who
     * taps download twice gets two copies rather than being told they already
     * have one.
     */
    fun existing(context: Context, name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreEntry(context, name)
        } else {
            legacyFile(name).takeIf { it.exists() }?.let(Uri::fromFile)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreEntry(context: Context, name: String): Uri? = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "%$FOLDER%"),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendPath(cursor.getLong(0).toString())
                .build()
        }
    }.onFailure { Log.w(TAG, "media store lookup failed for $name: ${it.message}") }.getOrNull()

    /**
     * Whether [uri] still names a file that is there.
     *
     * The record of what has been downloaded is kept by this app, but the files
     * are not this app's to keep: they sit in a folder built for the user to
     * manage, and one deleted from a file manager leaves the record behind
     * claiming a download that no longer exists. Cheap to ask, and the answer
     * is what stops the menu offering to delete nothing.
     */
    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") return uri.path?.let { File(it).exists() } == true
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
    }.getOrDefault(false)

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() } == true
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "could not delete $uri: ${it.message}") }.getOrDefault(false)

    // ---- Writing ------------------------------------------------------------

    /**
     * A destination that exists but is not yet a file anyone else can see.
     *
     * Every path out of here is either [commit] or [abort]; there is no third
     * option, because the thing being protected against is a partial file
     * surviving a failure and looking like a whole one.
     */
    class Pending internal constructor(
        private val context: Context,
        val uri: Uri,
        val name: String,
        /** Set on the legacy path only: the `.part` file being written. */
        private val part: File?,
        /** Set on the legacy path only: what [part] is renamed to. */
        private val target: File?,
    ) {
        fun openStream(): OutputStream =
            part?.outputStream()
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Could not open $name for writing")

        /** @return the uri the finished file can be reached at. */
        fun commit(): Uri {
            if (part != null && target != null) {
                if (!part.renameTo(target)) error("Could not finish writing $name")
                // Nothing indexes a file that simply appeared; without this it
                // is on disk and invisible to every app that lists media.
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    null,
                    null,
                )
                return Uri.fromFile(target)
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        }

        fun abort() {
            part?.delete()
            if (part == null) runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    /**
     * Reserve [name] and return somewhere to write it.
     *
     * @throws IllegalStateException if the folder or the store row can't be
     *   made — a failure worth surfacing, since every one of them means the
     *   download cannot start rather than that it might not finish.
     */
    fun begin(context: Context, name: String, mimeType: String): Pending {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create $name in Music")
            return Pending(context, uri, name, part = null, target = null)
        }

        val target = legacyFile(name)
        val folder = target.parentFile ?: error("No Music folder on this device")
        if (!folder.exists() && !folder.mkdirs()) error("Could not create ${folder.path}")
        val part = File(folder, "$name.part")
        part.delete()
        return Pending(context, Uri.fromFile(target), name, part = part, target = target)
    }

    @Suppress("DEPRECATION")
    private fun legacyFile(name: String) = File(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER),
        name,
    )
}
