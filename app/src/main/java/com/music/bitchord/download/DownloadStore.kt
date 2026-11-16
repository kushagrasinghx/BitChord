package com.music.bitchord.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import androidx.annotation.RequiresApi
import com.music.bitchord.data.model.Song
import java.io.File
import java.io.OutputStream
import java.util.Locale

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
 * It goes through the audio collection rather than Downloads because that is
 * where audio belongs and where every other player on the device looks. What
 * first ruled Downloads out was narrower and is worth keeping on the record:
 * the files were `.webm` then — a container extension Android's own mime table
 * ties to video regardless of what MIME type this class declares for it — and a
 * Gallery app crawling Downloads for video-looking files does not care what a
 * column says otherwise. Nothing writes `.webm` any more (see [storable] and
 * `StreamResolver.resolveForDownload`), so that particular trap is behind us;
 * the conclusion it led to is still the right one.
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
 * Neither side writes tags — this class only ever copies the bytes the server
 * on the other end sent. [MediaTagger] rewrites the finished file afterwards to
 * add them; the filename below is what every downloaded track carries
 * regardless of whether that rewrite finds a layout it recognises.
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

    private val nameLock = Any()
    private val reservedNames = HashSet<String>()

    /** First `Name (n).ext` not already present in the download folder. */
    fun nextAvailableName(context: Context, name: String): String = nextAvailableName(name) { existing(context, it) != null }

    /**
     * Reserve a destination name and begin writing it, atomically.
     *
     * Two workers can finish downloads with the same artist/title at once; both
     * seeing "no file yet" and choosing the same name would make one overwrite
     * the other. The reservation closes the gap between choosing and writing,
     * and is released by [Pending.commit] or [Pending.abort].
     */
    fun reserveAndBegin(context: Context, baseName: String, mimeType: String): Pending = synchronized(nameLock) {
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val extension = if (dot > 0) baseName.substring(dot) else ""
        var candidate = baseName
        var suffix = 2
        while (candidate in reservedNames || existing(context, candidate) != null) {
            candidate = "$stem (${suffix++})$extension"
        }
        reservedNames += candidate
        try {
            begin(context, candidate, mimeType).also { pending ->
                pending.releaseReservation = { synchronized(nameLock) { reservedNames.remove(candidate) } }
            }
        } catch (t: Throwable) {
            reservedNames.remove(candidate)
            throw t
        }
    }

    /** Distinct from a network failure: a full device must say so. */
    internal fun requireFreeSpace(availableBytes: Long, minimumBytes: Long) {
        if (availableBytes < minimumBytes) {
            throw InsufficientSpaceException(
                "Not enough free storage — ${minimumBytes / (1024 * 1024)} MB is required before downloading",
            )
        }
    }

    /**
     * Extra room a transfer must leave beside the bytes it still has to write.
     *
     * Tagging rewrites the finished file with the cover and lyrics embedded, so
     * the last thing a download does needs somewhere to put a second copy of
     * itself. Eight megabytes covers a full-size cover and a long lyric sheet;
     * anything larger would start refusing downloads that would in fact have
     * finished.
     */
    internal const val TRANSFER_HEADROOM_BYTES = 8L * 1024 * 1024

    /**
     * Whether a transfer with [remainingBytes] still to write fits in
     * [availableBytes].
     *
     * A null or negative remainder means the length is not known — a chunked
     * response, or a manifest being fetched segment by segment — and only the
     * headroom is required. That is deliberately the conservative choice in the
     * other direction: refusing a download because of a guess is worse than
     * letting it run and failing at ENOSPC, which [Downloads] now reports as a
     * storage fault rather than a network one.
     */
    internal fun transferFits(availableBytes: Long, remainingBytes: Long?): Boolean {
        val remaining = (remainingBytes ?: 0L).coerceAtLeast(0L)
        return availableBytes >= remaining + TRANSFER_HEADROOM_BYTES
    }

    /** Free space on the volume downloads are currently being written to. */
    fun availableBytes(context: Context): Long = StatFs(freeSpacePath(context)).availableBytes

    /**
     * Fail a transfer that cannot finish, before it writes the bytes it would
     * have to throw away.
     *
     * The check at [begin] is a snapshot taken before the first byte; a queue of
     * four lossless albums can fill the device long before the last one starts.
     * This is the same question asked again mid-flight, which is the only way a
     * download that is *already* doomed stops early enough to say why.
     */
    fun requireTransferSpace(context: Context, remainingBytes: Long?) {
        val available = availableBytes(context)
        if (transferFits(available, remainingBytes)) return
        val needed = ((remainingBytes ?: 0L).coerceAtLeast(0L) + TRANSFER_HEADROOM_BYTES) / (1024 * 1024)
        throw InsufficientSpaceException(
            "Not enough free storage to finish this download — $needed MB still to write",
        )
    }

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

    /** Name allocation that never overwrites an unrelated recording. */
internal fun nextAvailableName(name: String, taken: (String) -> Boolean): String {
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    var suffix = 2
    while (true) {
        val candidate = "$stem ($suffix)$extension"
        if (!taken(candidate)) return candidate
        suffix++
    }
}

/** Free space required before starting a new download. */
    private const val MIN_FREE_BYTES = 64L * 1024 * 1024

    class InsufficientSpaceException(message: String) : IllegalStateException(message)

    /** What a file of some codec is called and what the store is told it is. */
    class Storable(val extension: String, val mimeType: String)

    /**
     * How to file a track of [codec], or null if this device won't have it.
     *
     * A source that can serve lossless does not thereby serve something Android
     * will keep: the media store's audio collection accepts a closed list of
     * MIME types, and one it doesn't recognise is refused outright at [begin] —
     * which is a download that cannot start rather than one that sounds worse
     * than hoped.
     *
     * Only asked of manifests now, whose segments [ManifestFile] joins into one
     * of these. A plain file is named for what its opening bytes are — see
     * [Downloader.Container] — because a codec label alone can't tell raw ADTS
     * from AAC-in-MP4, and this table used to send every addon `aac` and `mp3`
     * to YouTube for want of an entry.
     *
     * Kept as a table rather than derived from the codec string because two of
     * these are not the identity mapping they look like. WAV's registered type
     * is `audio/x-wav` on Android, and ALAC ships inside an MP4 container, so an
     * ALAC file is an `.m4a` as far as both the store and [Mp4Tagger] are
     * concerned — the tagger works on the box tree and never asks what the
     * samples inside are.
     */
    fun storable(codec: String?): Storable? = when (codec?.lowercase(Locale.ROOT)?.trim()) {
        "flac", "x-flac" -> Storable("flac", "audio/flac")
        "wav", "x-wav", "wave" -> Storable("wav", "audio/x-wav")
        "alac", "m4a", "mp4", "eac3-joc", "ec3-joc", "dolby-atmos" -> Storable("m4a", "audio/mp4")
        else -> null
    }

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
        if (!AppSettings.exportDownloads.value) {
            privateFile(context, name).takeIf { it.exists() }?.let(Uri::fromFile)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            uri.path?.let { path ->
                val file = File(path)
                if (file.name == "playlist.m3u8" && file.parentFile?.parentFile?.name == "offline-hls") {
                    file.parentFile?.deleteRecursively() == true
                } else file.delete()
            } == true
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
        internal var releaseReservation: (() -> Unit)? = null
        private var reservationReleased = false

        private fun releaseOnce() {
            if (reservationReleased) return
            reservationReleased = true
            releaseReservation?.invoke()
            releaseReservation = null
        }
        /** The bytes being built, whether this is a MediaStore row or a legacy `.part` file. */
        val tagUri: Uri get() = part?.let(Uri::fromFile) ?: uri

        fun openStream(): OutputStream =
            part?.outputStream()
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Could not open $name for writing")

        /** @return the uri the finished file can be reached at. */
        fun commit(): Uri {
            try {
                return commitNow()
            } finally {
                releaseOnce()
            }
        }

        private fun commitNow(): Uri {
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
            try {
                part?.delete()
                if (part == null) runCatching { context.contentResolver.delete(uri, null, null) }
            } finally {
                releaseOnce()
            }
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
        requireFreeSpace(StatFs(freeSpacePath(context)).availableBytes, MIN_FREE_BYTES)
        if (!AppSettings.exportDownloads.value) {
            val target = privateFile(context, name)
            val folder = target.parentFile ?: error("No download folder")
            if (!folder.exists() && !folder.mkdirs()) error("Could not create ${folder.path}")
            val part = File(folder, ".$name.part")
            part.delete()
            return Pending(context, Uri.fromFile(target), name, part = part, target = target)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            // A MIME type the audio collection doesn't recognise is not a null
            // return but an IllegalArgumentException thrown from inside the
            // resolver, several frames away from anything that names the
            // download it belongs to. Every type written here came from
            // [storable] or from the stream resolver, so landing in this branch
            // means one of those two is wrong about this device — worth saying
            // in those words the first time it happens again.
            val uri = runCatching {
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrElse { cause ->
                Log.w(TAG, "the media store refused $mimeType for $name: ${cause.message}")
                error("Android won't store ${name.substringAfterLast('.', mimeType)} files in Music")
            } ?: error("Could not create $name in Music")
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

    /**
     * The volume a download is about to be written to.
     *
     * Not always [Context.getFilesDir]. An exported download lands in the shared
     * Music folder, which on many phones is a different volume — and the only
     * one a full card affects — so measuring internal storage there answered the
     * wrong question: a device with no room left in Music passed the check and
     * then failed during the transfer. The copy that never leaves app storage
     * does live under filesDir, and that is what is measured for it.
     */
    /** Where offline HLS and DASH packages live — one directory each. */
    private fun packageRoot(context: Context): File = File(context.filesDir, "offline-hls")

    /**
     * A package directory younger than this is assumed to be a download that is
     * still running.
     *
     * The only way a package directory exists without a record pointing at it is
     * that its download has not finished yet — [Downloads.remember] is what
     * writes the record, and it runs after the bytes. A day is far longer than
     * any download takes and far shorter than a user would notice storage
     * disappearing, so it separates "in flight" from "abandoned" without asking
     * anything that can rage.
     */
    internal const val ORPHAN_MIN_AGE_MS = 24L * 60 * 60 * 1000L

    /**
     * Package directories that no record points at and that are too old to be a
     * download in progress.
     *
     * Both conditions are necessary. Referenced-only would never delete anything,
     * which is the state this finding describes; age-only would delete the
     * package a download is currently filling. Takes the directory listing and
     * the clock rather than reading them so the rule can be tested on a folder
     * it built itself.
     */
    internal fun orphanedPackages(
        root: File,
        referenced: Set<String>,
        nowMillis: Long,
        minAgeMillis: Long = ORPHAN_MIN_AGE_MS,
    ): List<File> {
        val entries = root.listFiles() ?: return emptyList()
        return entries.filter { entry ->
            entry.isDirectory &&
                entry.name !in referenced &&
                nowMillis - entry.lastModified() >= minAgeMillis
        }
    }

    /**
     * Delete [packages] and everything inside them.
     *
     * Separate from the sweep so the rule and the deleting can be tested without
     * a `Context`, and so nothing here needs a logger: this is called with
     * directories a test built itself.
     *
     * @return how many directories were removed.
     */
    internal fun removePackages(packages: List<File>): Int =
        packages.count { it.deleteRecursively() }

    /**
     * Remove the packages [orphanedPackages] names, and everything inside them.
     *
     * Covers the sidecars as well as the audio: a package holds `playlist.m3u8`,
     * its segments, `lyrics.lrc` and `cover.jpg`, so reclaiming the directory is
     * what reclaims the artwork too — including the covers that belong to a
     * package whose record was lost.
     *
     * @return how many directories were removed.
     */
    fun reclaimOrphanedPackages(
        context: Context,
        referenced: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        minAgeMillis: Long = ORPHAN_MIN_AGE_MS,
    ): Int {
        val orphans = orphanedPackages(packageRoot(context), referenced, nowMillis, minAgeMillis)
        val removed = removePackages(orphans)
        if (removed > 0) Log.d(TAG, "reclaimed $removed orphaned offline packages")
        return removed
    }

    private fun freeSpacePath(context: Context): String =
        if (AppSettings.exportDownloads.value) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory().path
        } else {
            context.filesDir.path
        }

    private fun privateFile(context: Context, name: String) =
        File(File(context.filesDir, "downloads"), name)
}
