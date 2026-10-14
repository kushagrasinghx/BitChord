package com.music.bitchord.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.LocalMediaRepository
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
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

    fun resolveSubfolder(song: Song): String? {
        if (!AppSettings.downloadOrganizeFolders.value) return null
        val albumArtist = MediaTagger.resolveAlbumArtistString(song)
        val safeArtist = sanitise(albumArtist).ifEmpty { "Unknown Artist" }
        val safeAlbum = sanitise(song.albumName.orEmpty())
        return if (safeAlbum.isEmpty()) safeArtist else "$safeArtist/$safeAlbum"
    }

    fun relativePathFor(subfolder: String?): String =
        if (subfolder.isNullOrEmpty()) "${Environment.DIRECTORY_MUSIC}/$FOLDER"
        else "${Environment.DIRECTORY_MUSIC}/$FOLDER/$subfolder"

    private fun resolveSafDirectory(
        root: DocumentFile,
        subfolder: String?,
        create: Boolean = false,
    ): DocumentFile? {
        if (subfolder.isNullOrEmpty()) return root
        var current = root
        val segments = subfolder.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val next = current.findFile(segment)
                ?: if (create) current.createDirectory(segment) else null
            if (next == null || !next.isDirectory) return null
            current = next
        }
        return current
    }

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

    /** What a file of some codec is called and what the store is told it is. */
    class Storable(val extension: String, val mimeType: String)

    /**
     * How to file a track of [codec], or null if this device won't have it.
     *
     * A source that can serve lossless does not thereby serve something Android
     * will keep: the media store's audio collection accepts a closed list of
     * MIME types, and one it doesn't recognise is refused outright at [begin] —
     * which is a download that cannot start rather than one that sounds worse
     * than hoped. Anything not answered for here falls the caller back to
     * YouTube's AAC, so an unfamiliar codec costs quality and not the download.
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
    fun existing(context: Context, name: String, subfolder: String? = null): Uri? {
        val inActive = existingInternal(context, name, subfolder)
        if (inActive != null) return inActive
        if (!subfolder.isNullOrEmpty()) {
            val inRoot = existingInternal(context, name, null)
            if (inRoot != null) return inRoot
        }
        return null
    }

    private fun existingInternal(context: Context, name: String, subfolder: String?): Uri? {
        val safUriStr = AppSettings.downloadFolderUri.value
        if (AppSettings.exportDownloads.value && !safUriStr.isNullOrBlank()) {
            val foundUri = runCatching {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(safUriStr))
                if (rootDoc != null) {
                    val targetDir = resolveSafDirectory(rootDoc, subfolder, create = false)
                    targetDir?.findFile(name)?.uri
                } else null
            }.onFailure { Log.w(TAG, "SAF duplicate lookup failed for $name: ${it.message}") }.getOrNull()
            if (foundUri != null) return foundUri
        }

        if (!AppSettings.exportDownloads.value) {
            return privateFile(context, name, subfolder).takeIf { it.exists() }?.let(Uri::fromFile)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return mediaStoreEntry(context, name, subfolder)
        } else {
            return legacyFile(name, subfolder).takeIf { it.exists() }?.let(Uri::fromFile)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreEntry(context: Context, name: String, subfolder: String? = null): Uri? = runCatching {
        val targetPathPattern = if (subfolder.isNullOrEmpty()) {
            "%$FOLDER%"
        } else {
            "%$FOLDER/$subfolder%"
        }
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, targetPathPattern),
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
                } else {
                    val stem = file.nameWithoutExtension
                    val parent = file.parentFile
                    if (parent != null) {
                        File(parent, "$stem.lrc").takeIf { it.exists() }?.delete()
                        File(parent, "$stem.jpg").takeIf { it.exists() }?.delete()
                    }
                    file.delete()
                }
            } == true
        } else if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            val (docName, docId) = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        val id = cursor.getString(1)
                        name to id
                    } else null
                }
            }.getOrNull() ?: (null to null)

            val finalDocName = docName ?: DocumentFile.fromSingleUri(context, uri)?.name
            if (!finalDocName.isNullOrBlank()) {
                val stem = finalDocName.substringBeforeLast('.')
                if (docId != null && uri.authority != null) {
                    val parentId = if (docId.contains('/')) docId.substringBeforeLast('/') else docId.substringBefore(':') + ":"
                    val lrcId = "$parentId/$stem.lrc"
                    val jpgId = "$parentId/$stem.jpg"
                    val lrcUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, lrcId)
                    val jpgUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, jpgId)
                    runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, lrcUri) }
                    runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, jpgUri) }
                }

                val safUriStr = AppSettings.downloadFolderUri.value
                if (!safUriStr.isNullOrBlank()) {
                    val rootPath = LocalMediaRepository.selectedFolderPath(safUriStr)
                    if (!rootPath.isNullOrBlank()) {
                        val rootDir = File(rootPath)
                        if (rootDir.exists()) {
                            rootDir.walkTopDown().maxDepth(4)
                                .filter { it.isFile && (it.name == "$stem.lrc" || it.name == "$stem.jpg") }
                                .forEach { runCatching { it.delete() } }
                        }
                    }
                    runCatching {
                        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(safUriStr))
                        if (rootDoc != null) {
                            fun cleanTree(dir: DocumentFile, depth: Int) {
                                if (depth <= 0) return
                                dir.findFile("$stem.lrc")?.delete()
                                dir.findFile("$stem.jpg")?.delete()
                                dir.listFiles().filter { it.isDirectory }.forEach { cleanTree(it, depth - 1) }
                            }
                            cleanTree(rootDoc, 4)
                        }
                    }
                }
            }
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
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
        private val part: File? = null,
        /** Set on the legacy path only: what [part] is renamed to. */
        private val target: File? = null,
        val safPartDoc: DocumentFile? = null,
        val safTargetDir: DocumentFile? = null,
        val subfolder: String? = null,
    ) {
        /** The bytes being built, whether this is a MediaStore row, a SAF DocumentFile, or a legacy `.part` file. */
        val tagUri: Uri get() = part?.let(Uri::fromFile) ?: uri

        fun openStream(): OutputStream =
            part?.outputStream()
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Could not open $name for writing")

        /** @return the uri the finished file can be reached at. */
        fun commit(): Uri {
            if (safPartDoc != null && safTargetDir != null) {
                val existing = safTargetDir.findFile(name)
                if (existing != null && existing.uri != safPartDoc.uri) {
                    existing.delete()
                }
                if (!safPartDoc.renameTo(name)) {
                    error("Could not finalize SAF part file $name")
                }
                val committedUri = safTargetDir.findFile(name)?.uri ?: safPartDoc.uri

                // Trigger MediaScanner scan if physical path can be determined
                val rootUriStr = AppSettings.downloadFolderUri.value
                if (!rootUriStr.isNullOrBlank()) {
                    val rootPath = LocalMediaRepository.selectedFolderPath(rootUriStr)
                    if (!rootPath.isNullOrBlank()) {
                        val dirPath = if (subfolder.isNullOrEmpty()) rootPath else "$rootPath/$subfolder"
                        val physicalFile = File(dirPath, name)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(physicalFile.absolutePath),
                            null,
                            null,
                        )
                    }
                }
                return committedUri
            }

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
            val filePath = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                    } else null
                }
            }.getOrNull()
            if (!filePath.isNullOrBlank()) {
                MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            }
            return uri
        }

        fun abort() {
            safPartDoc?.delete()
            part?.delete()
            if (part == null && safPartDoc == null) {
                runCatching { context.contentResolver.delete(uri, null, null) }
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
    fun begin(context: Context, name: String, mimeType: String, subfolder: String? = null): Pending {
        // Mode 1: Custom SAF Export Folder (Strictly when exportDownloads is true)
        val safUriStr = AppSettings.downloadFolderUri.value
        if (AppSettings.exportDownloads.value && !safUriStr.isNullOrBlank()) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(safUriStr))
            if (rootDoc != null && rootDoc.canWrite()) {
                val targetDir = resolveSafDirectory(rootDoc, subfolder, create = true)
                    ?: error("Could not resolve or create subfolder $subfolder in SAF")
                val partName = ".$name.part"
                targetDir.findFile(partName)?.delete()
                // Force application/octet-stream so provider does not append unwanted audio extensions
                val partDoc = targetDir.createFile("application/octet-stream", partName)
                    ?: error("Could not create part file in SAF")
                if (partDoc.name != partName) {
                    partDoc.renameTo(partName)
                }
                return Pending(
                    context = context,
                    uri = partDoc.uri,
                    name = name,
                    safPartDoc = partDoc,
                    safTargetDir = targetDir,
                    subfolder = subfolder,
                )
            } else {
                Log.w(TAG, "Configured SAF folder is inaccessible or non-writable ($safUriStr); falling back to default storage")
            }
        }

        // Mode 2: App-Private Storage
        if (!AppSettings.exportDownloads.value) {
            val target = privateFile(context, name, subfolder)
            val folder = target.parentFile ?: error("No download folder")
            if (!folder.exists() && !folder.mkdirs()) error("Could not create ${folder.path}")
            val part = File(folder, ".$name.part")
            part.delete()
            return Pending(context, Uri.fromFile(target), name, part = part, target = target, subfolder = subfolder)
        }

        // Mode 3: Standard MediaStore (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val targetRelativePath = relativePathFor(subfolder)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching {
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrElse { cause ->
                Log.w(TAG, "the media store refused $mimeType for $name in $targetRelativePath: ${cause.message}")
                error("Android won't store ${name.substringAfterLast('.', mimeType)} files in Music")
            } ?: error("Could not create $name in Music")
            return Pending(context, uri, name, part = null, target = null, subfolder = subfolder)
        }

        // Mode 4: Legacy Public Storage (API 26-28)
        val target = legacyFile(name, subfolder)
        val folder = target.parentFile ?: error("No Music folder on this device")
        if (!folder.exists() && !folder.mkdirs()) error("Could not create ${folder.path}")
        val part = File(folder, "$name.part")
        part.delete()
        return Pending(context, Uri.fromFile(target), name, part = part, target = target, subfolder = subfolder)
    }

    /**
     * Writes a companion file (.lrc, cover.jpg, or .nomedia) alongside downloads.
     */
    fun writeCompanion(
        context: Context,
        subfolder: String?,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
    ) {
        runCatching {
            // Mode 1: Custom SAF Export Folder
            val safUriStr = AppSettings.downloadFolderUri.value
            if (AppSettings.exportDownloads.value && !safUriStr.isNullOrBlank()) {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(safUriStr))
                if (rootDoc != null && rootDoc.canWrite()) {
                    val targetDir = resolveSafDirectory(rootDoc, subfolder, create = true) ?: return
                    val existing = targetDir.findFile(fileName)
                    if (existing != null) {
                        if (fileName == ".nomedia" || (fileName == "cover.jpg" && existing.length() == bytes.size.toLong())) {
                            return
                        }
                        existing.delete()
                    }
                    val newFile = targetDir.createFile(mimeType, fileName)
                    if (newFile != null) {
                        // Samsung & AOSP StorageProvider anti-mutation guard
                        if (newFile.name != fileName) {
                            newFile.renameTo(fileName)
                        }
                        context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
                    }
                    return
                } else {
                    Log.w(TAG, "Configured SAF folder is inaccessible or non-writable ($safUriStr); falling back to default storage")
                }
            }

            // Mode 2: App-Private Storage
            if (!AppSettings.exportDownloads.value) {
                val base = File(context.filesDir, "downloads")
                val dir = if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, fileName)
                if (target.exists()) {
                    if (fileName == ".nomedia" || (fileName == "cover.jpg" && target.length() == bytes.size.toLong())) {
                        return
                    }
                }
                target.writeBytes(bytes)
                return
            }

            // Mode 3: Shared Public Storage (Legacy & Direct File System in Music/BitChord)
            val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
            val dir = if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            if (target.exists()) {
                if (fileName == ".nomedia" || (fileName == "cover.jpg" && target.length() == bytes.size.toLong())) {
                    return
                }
            }
            val written = runCatching {
                target.writeBytes(bytes)
                true
            }.getOrDefault(false)

            if (written) {
                // Never scan companion images into MediaStore to prevent gallery pollution
                return
            }

            // Scoped Storage fallback on Android 10+ (API 29+) when direct File write is restricted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relPath = if (subfolder.isNullOrEmpty()) "Music/$FOLDER/" else "Music/$FOLDER/$subfolder/"
                val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                
                // Query for existing companion entry to prevent duplicates (e.g. cover (1).jpg)
                var existingUri: Uri? = null
                var existingSize: Long = -1L
                context.contentResolver.query(
                    filesUri,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(fileName, relPath),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        existingSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                        existingUri = android.content.ContentUris.withAppendedId(filesUri, id)
                    }
                }

                if (existingUri != null) {
                    if (fileName == ".nomedia" || (fileName == "cover.jpg" && existingSize == bytes.size.toLong())) {
                        return
                    }
                    val stream = runCatching { context.contentResolver.openOutputStream(existingUri!!, "wt") }.getOrNull()
                        ?: context.contentResolver.openOutputStream(existingUri!!, "w")
                    stream?.use { it.write(bytes) }
                    return
                }

                val isImage = fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true)
                val safeMime = if (isImage) "application/octet-stream" else mimeType

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, safeMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val itemUri = context.contentResolver.insert(filesUri, contentValues)
                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { it.write(bytes) }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, contentValues, null, null)
                    return
                }
            }
        }.onFailure { Log.w(TAG, "Companion write non-fatal failure for $fileName: ${it.message}") }
    }

    /**
     * Purges unwanted companion image entries from Android's MediaStore that were
     * accidentally scanned into the user's Gallery app, and ensures .nomedia is present.
     */
    fun cleanupGalleryImages(context: Context) {
        runCatching {
            val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
            if (base.exists() && base.isDirectory) {
                val nomedia = File(base, ".nomedia")
                if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
                base.walkTopDown().maxDepth(4).forEach { f ->
                    if (f.isDirectory) {
                        val subNomedia = File(f, ".nomedia")
                        if (!subNomedia.exists()) runCatching { subNomedia.createNewFile() }
                    } else if (f.isFile && (f.name.endsWith(".jpg", ignoreCase = true) || f.name.endsWith(".png", ignoreCase = true))) {
                        // Delete loose track cover images that flooded the folder unless explicitly enabled
                        if (f.name != "cover.jpg" && !AppSettings.downloadSaveTrackCover.value) {
                            runCatching { f.delete() }
                        }
                    }
                }
            }

            // Drop any image entries under Music/BitChord from Android's MediaStore image database
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    context.contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                        arrayOf("%Music/$FOLDER/%"),
                    )
                }
            }
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DATA} LIKE ?",
                    arrayOf("%/Music/$FOLDER/%"),
                )
            }
        }.onFailure { Log.w(TAG, "Failed cleaning gallery images: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    private fun legacyFile(name: String, subfolder: String? = null): File {
        val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
        val dir = if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
        return File(dir, name)
    }

    private fun privateFile(context: Context, name: String, subfolder: String? = null): File {
        val base = File(context.filesDir, "downloads")
        val dir = if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
        return File(dir, name)
    }
}
