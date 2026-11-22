package com.music.bitchord.data.lyrics

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.music.bitchord.data.DebugLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads and writes the `.lrc` file that sits beside a local track.
 *
 * A sidecar is the one place synchronized lyrics can live without touching the
 * audio bytes: every player that reads LRC finds it by the audio file's own
 * name, and the audio file stays exactly as it was. That matters for files the
 * user copied in themselves, which this app has no right to rewrite.
 *
 * The destination follows the track, not a setting:
 *
 *  - a `file://` track gets `<name>.lrc` in the same directory;
 *  - a MediaStore track gets a text row in the same `RELATIVE_PATH`;
 *  - a document-provider track (the custom download folder) gets a sibling
 *    document through the same tree permission.
 *
 * **Nothing is ever overwritten.** An existing sidecar may have been put there
 * by the user, by another player, or by an earlier run with different sources,
 * and silently replacing it would be the one destructive thing this feature
 * could do. A file that is already there is reported as
 * [State.ALREADY_PRESENT] and left alone.
 */
object LyricsSidecar {

    private const val TAG = "BitChord"
    private const val MIME = "text/plain"
    private const val MAX_BYTES = 256 * 1024

    enum class State {
        WRITTEN,
        ALREADY_PRESENT,
        NO_SYNCED_LYRICS,
        UNSUPPORTED,
        PERMISSION_DENIED,
        FAILED,
    }

    data class Result(val state: State, val uri: Uri? = null, val message: String? = null)

    /**
     * The sidecar beside [uriString], or null when there is none worth reading.
     *
     * Runs on IO: every branch touches either the disk or a content provider.
     */
    suspend fun read(context: Context, uriString: String): String? = withContext(Dispatchers.IO) {
        runCatching { readNow(context, Uri.parse(uriString)) }
            .onFailure { Log.d(TAG, "could not read lyrics sidecar for $uriString: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Writes [enhanced] when it has word timings, else [plain], beside the
     * track. Both are already LRC text; this method only chooses which form is
     * worth keeping and refuses anything that is not actually timed.
     */
    suspend fun write(
        context: Context,
        uriString: String,
        plain: String,
        enhanced: String?,
    ): Result = withContext(Dispatchers.IO) {
        val text = enhanced?.takeIf { it.isNotBlank() && hasTimestamp(it) }
            ?: plain.takeIf { it.isNotBlank() && hasTimestamp(it) }
            ?: return@withContext Result(State.NO_SYNCED_LYRICS)
        if (text.length > MAX_BYTES) return@withContext Result(State.FAILED, message = "lyrics are too large")

        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext Result(State.UNSUPPORTED)
        runCatching {
            when {
                uri.scheme == "file" -> writeFile(uri, text)
                isMediaStore(uri) -> writeMediaStore(context, uri, text)
                DocumentsContract.isDocumentUri(context, uri) -> writeDocument(context, uri, text)
                else -> writeDocumentFile(context, uri, text)
            }
        }.getOrElse { failure ->
            val denied = failure is SecurityException
            Log.w(TAG, "could not write lyrics beside $uriString: ${failure.message}")
            Result(
                if (denied) State.PERMISSION_DENIED else State.FAILED,
                message = failure.message,
            )
        }
    }

    /**
     * The name a sidecar takes from its audio file: `Song.mp3` → `Song.lrc`.
     *
     * Kept pure so the pairing can be tested without a device, and so the
     * reader and the writer cannot disagree about which file they mean.
     */
    internal fun lrcNameFor(displayName: String): String {
        val name = displayName.substringAfterLast('/').substringAfterLast('\\')
        val stem = name.substringBeforeLast('.', name).trim().ifBlank { "lyrics" }
        return "$stem.lrc"
    }

    /** Whether [text] carries at least one `[mm:ss]` or `<mm:ss>` stamp. */
    internal fun hasTimestamp(text: String): Boolean = TIMESTAMP.containsMatchIn(text)

    private val TIMESTAMP = Regex("""[\[<](?:\d{1,2}:)?\d{1,3}:\d{2}(?:[.:]\d{1,3})?[\]>]""")

    // ---- Reading ------------------------------------------------------------

    private fun readNow(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            val audio = uri.path?.let(::File) ?: return null
            return readFile(File(audio.parentFile, lrcNameFor(audio.name)))
        }
        if (isMediaStore(uri)) {
            readMediaStore(context, uri)?.let { return it }
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            readDocument(context, uri)?.let { return it }
        }
        return readDocumentFile(context, uri)
    }

    private fun readFile(file: File): String? {
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun readMediaStore(context: Context, audio: Uri): String? {
        val row = mediaRow(context, audio) ?: return null
        val name = lrcNameFor(row.displayName)
        // API 28 and below still has a real path; reading it directly is both
        // cheaper and works for files the store has not indexed as documents.
        row.dataPath?.let { path ->
            val file = File(File(path).parentFile, name)
            readFile(file)?.let { return it }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val relative = row.relativePath ?: return null
        val sidecar = findMediaStoreFile(context, name, relative) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(sidecar)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()?.takeIf { it.length <= MAX_BYTES }
    }

    private fun readDocument(context: Context, audio: Uri): String? {
        val name = documentName(context, audio)?.let(::lrcNameFor) ?: return null
        // The common case: a document under a tree, where the sibling's id is
        // the parent id plus the name.
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(audio) }.getOrNull()
        val documentId = runCatching { DocumentsContract.getDocumentId(audio) }.getOrNull()
        if (treeId != null && documentId != null) {
            val parentId = documentId.substringBeforeLast('/', "")
            if (parentId.isNotEmpty()) {
                val sibling = DocumentsContract.buildDocumentUriUsingTree(audio, "$parentId/$name")
                if (documentExists(context, sibling)) return readUri(context, sibling)
            }
        }
        return readDocumentFile(context, audio)
    }

    private fun readDocumentFile(context: Context, audio: Uri): String? {
        val audioDoc = runCatching { DocumentFile.fromSingleUri(context, audio) }.getOrNull()
            ?: return null
        val parent = runCatching { audioDoc.parentFile }.getOrNull() ?: return null
        val name = audioDoc.name?.let(::lrcNameFor) ?: return null
        val sidecar = runCatching { parent.findFile(name) }.getOrNull() ?: return null
        if (!sidecar.isFile) return null
        return readUri(context, sidecar.uri)
    }

    private fun readUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }.getOrNull()?.takeIf { it.length in 1..MAX_BYTES }

    // ---- Writing: a real file ----------------------------------------------

    private fun writeFile(audio: Uri, text: String): Result {
        val file = audio.path?.let(::File) ?: return Result(State.UNSUPPORTED)
        val target = File(file.parentFile, lrcNameFor(file.name))
        if (target.exists()) return Result(State.ALREADY_PRESENT, Uri.fromFile(target))
        val parent = target.parentFile ?: return Result(State.UNSUPPORTED)
        if (!parent.exists() || !parent.canWrite()) return Result(State.PERMISSION_DENIED)
        val part = File(parent, ".${target.name}.${System.nanoTime()}.part")
        return try {
            part.writeText(text, Charsets.UTF_8)
            // Another process may have written the same sidecar while this
            // one was being prepared; the existing file wins.
            if (target.exists()) {
                part.delete()
                return Result(State.ALREADY_PRESENT, Uri.fromFile(target))
            }
            if (!part.renameTo(target)) error("could not rename ${part.name}")
            Result(State.WRITTEN, Uri.fromFile(target))
        } catch (failure: Throwable) {
            part.delete()
            throw failure
        }
    }

    // ---- Writing: MediaStore ------------------------------------------------

    private fun writeMediaStore(context: Context, audio: Uri, text: String): Result {
        val row = mediaRow(context, audio) ?: return Result(State.UNSUPPORTED)
        val name = lrcNameFor(row.displayName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Only reachable on API 28 and below, where a real path is the
            // only writable destination and the caller holds the permission.
            val path = row.dataPath ?: return Result(State.UNSUPPORTED)
            return writeFile(Uri.fromFile(File(path)), text)
        }
        val relative = row.relativePath ?: return Result(State.UNSUPPORTED)
        findMediaStoreFile(context, name, relative)?.let {
            return Result(State.ALREADY_PRESENT, it)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val sidecar = context.contentResolver.insert(collection, values)
            ?: return Result(State.FAILED, message = "could not create $name")
        return try {
            context.contentResolver.openOutputStream(sidecar, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("could not open $name for writing")
            context.contentResolver.update(
                sidecar,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            Result(State.WRITTEN, sidecar)
        } catch (failure: Throwable) {
            runCatching { context.contentResolver.delete(sidecar, null, null) }
            throw failure
        }
    }

    private class MediaRow(
        val displayName: String,
        val relativePath: String?,
        val dataPath: String?,
    )

    private fun mediaRow(context: Context, uri: Uri): MediaRow? = runCatching {
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            @Suppress("DEPRECATION")
            add(MediaStore.MediaColumns.DATA)
        }.toTypedArray()
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun text(column: String): String? {
                val index = cursor.getColumnIndex(column)
                return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            }
            MediaRow(
                displayName = text(MediaStore.MediaColumns.DISPLAY_NAME) ?: uri.lastPathSegment ?: return@use null,
                relativePath = text(MediaStore.MediaColumns.RELATIVE_PATH),
                dataPath = text(MediaStore.MediaColumns.DATA),
            )
        }
    }.getOrNull()

    private fun findMediaStoreFile(context: Context, name: String, relativePath: String): Uri? = runCatching {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, relativePath),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
    }.getOrNull()

    // ---- Writing: document providers ---------------------------------------

    private fun writeDocument(context: Context, audio: Uri, text: String): Result {
        val name = documentName(context, audio)?.let(::lrcNameFor)
            ?: return Result(State.UNSUPPORTED)
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(audio) }.getOrNull()
        val documentId = runCatching { DocumentsContract.getDocumentId(audio) }.getOrNull()
        if (treeId != null && documentId != null) {
            val parentId = documentId.substringBeforeLast('/', "")
            if (parentId.isNotEmpty()) {
                val existing = DocumentsContract.buildDocumentUriUsingTree(audio, "$parentId/$name")
                if (documentExists(context, existing)) return Result(State.ALREADY_PRESENT, existing)
                val parent = DocumentsContract.buildDocumentUriUsingTree(audio, parentId)
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    parent,
                    MIME,
                    name,
                ) ?: return Result(State.FAILED, message = "could not create $name")
                return finishDocument(context, created, name, text)
            }
        }
        return writeDocumentFile(context, audio, text)
    }

    private fun writeDocumentFile(context: Context, audio: Uri, text: String): Result {
        val audioDoc = runCatching { DocumentFile.fromSingleUri(context, audio) }.getOrNull()
            ?: return Result(State.UNSUPPORTED)
        val parent = runCatching { audioDoc.parentFile }.getOrNull()
            ?: return Result(State.UNSUPPORTED)
        val name = lrcNameFor(audioDoc.name ?: return Result(State.UNSUPPORTED))
        runCatching { parent.findFile(name) }.getOrNull()?.let {
            return Result(State.ALREADY_PRESENT, it.uri)
        }
        val created = runCatching { parent.createFile(MIME, name) }.getOrNull()
            ?: return Result(State.FAILED, message = "could not create $name")
        return finishDocument(context, created.uri, name, text)
    }

    private fun finishDocument(context: Context, created: Uri, name: String, text: String): Result {
        // A provider that raced us, or one that renames duplicates, must not
        // be allowed to leave a second file under a name we did not ask for.
        val actualName = runCatching {
            DocumentFile.fromSingleUri(context, created)?.name
        }.getOrNull()
        if (actualName != null && actualName != name) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
            return Result(State.ALREADY_PRESENT, created)
        }
        return try {
            context.contentResolver.openOutputStream(created, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("could not open $name for writing")
            Result(State.WRITTEN, created)
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
            throw failure
        }
    }

    private fun documentExists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    private fun documentName(context: Context, uri: Uri): String? {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun isMediaStore(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY ||
            uri.path?.contains("/audio/media/") == true
}
