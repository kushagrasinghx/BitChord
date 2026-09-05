package com.music.bitchord.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.lyrics.LyricsArtifact
import java.io.File

/**
 * Atomic storage for the lyrics file that sits beside a downloaded track.
 *
 * Stored in the app's external music directory to reliably support all
 * Android versions (API 26–36) without running afoul of MediaStore Scoped
 * Storage restrictions on non-audio MIME types in shared Music collections.
 *
 * The sidecar is deliberately independent of the audio file: an unavailable
 * lyrics provider must never make a successfully downloaded track partial.
 */
object LyricsSidecarStore {

    private const val TAG = "BitChord"

    /** The visible filename derived from the already chosen audio filename. */
    fun fileNameFor(audioName: String, artifact: LyricsArtifact): String {
        val stem = audioName.substringBeforeLast('.', audioName)
        return "$stem.${artifact.format.extension}"
    }

    /** The directory where lyrics sidecars are stored. */
    fun storageDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val folder = File(base, DownloadStore.FOLDER)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun existing(context: Context, name: String): Uri? {
        val appFile = File(storageDir(context), name)
        if (appFile.exists()) return Uri.fromFile(appFile)

        val legacy = legacyFile(name)
        if (legacy.exists()) return Uri.fromFile(legacy)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(name),
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        .buildUpon()
                        .appendPath(cursor.getLong(0).toString())
                        .build()
                }
            }.onFailure {
                Log.w(TAG, "sidecar lookup failed for $name: ${it.message}")
            }.getOrNull()
        }
        return null
    }

    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).exists() } == true
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
        }
    }.getOrDefault(false)

    fun read(context: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).readText()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
        }
    }.onFailure { Log.w(TAG, "could not read lyrics sidecar $uri: ${it.message}") }.getOrNull()

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() } == true
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "could not delete lyrics sidecar $uri: ${it.message}") }.getOrDefault(false)

    /** Writes a complete sidecar atomically or removes its destination on failure. */
    fun write(context: Context, name: String, artifact: LyricsArtifact): Uri {
        val folder = storageDir(context)
        val target = File(folder, name)
        val part = File(folder, "$name.part")
        part.delete()
        return try {
            part.outputStream().bufferedWriter().use { it.write(artifact.content) }
            if (!part.renameTo(target)) error("Could not finish lyrics sidecar $name")
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            Uri.fromFile(target)
        } catch (failure: Throwable) {
            part.delete()
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyFile(name: String) = File(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), DownloadStore.FOLDER),
        name,
    )
}
