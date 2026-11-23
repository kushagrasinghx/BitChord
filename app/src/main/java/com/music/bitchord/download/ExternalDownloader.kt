package com.music.bitchord.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ExternalDownloaderApp

object ExternalDownloader {

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun resolveTargetPackage(context: Context, app: ExternalDownloaderApp, customPackage: String): String? {
        return when (app) {
            ExternalDownloaderApp.YTDLNIS -> "com.deniscerri.ytdl"
            ExternalDownloaderApp.SEAL -> "com.junkfood.seal"
            ExternalDownloaderApp.SEAL_PLUS -> {
                val candidates = listOf("com.junkfood.sealplus", "com.seal.plus")
                candidates.firstOrNull { isAppInstalled(context, it) } ?: "com.junkfood.sealplus"
            }
            ExternalDownloaderApp.OTHER -> customPackage.trim().takeIf { it.isNotBlank() }
        }
    }

    fun buildMediaUrl(song: Song?, playlistId: String? = null): String {
        return if (!playlistId.isNullOrBlank()) {
            val cleanId = playlistId.removePrefix("VL")
            "https://music.youtube.com/playlist?list=$cleanId"
        } else if (song != null && song.videoId.isNotBlank()) {
            "https://music.youtube.com/watch?v=${song.videoId}"
        } else {
            ""
        }
    }

    fun launch(
        context: Context,
        url: String,
        app: ExternalDownloaderApp = AppSettings.externalDownloaderApp.value,
        customPackage: String = AppSettings.externalDownloaderPackage.value,
    ): Boolean {
        if (url.isBlank()) return false

        val targetPackage = resolveTargetPackage(context, app, customPackage)
        val appName = app.displayName(context)

        if (targetPackage.isNullOrBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.external_downloader_no_package_specified),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        if (!isAppInstalled(context, targetPackage)) {
            Toast.makeText(
                context,
                context.getString(R.string.external_downloader_not_installed, appName),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(sendIntent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.external_downloader_cannot_launch, appName),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }
}
