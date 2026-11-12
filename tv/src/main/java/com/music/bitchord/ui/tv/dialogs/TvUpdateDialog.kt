package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.AppUpdateChecker.DownloadState
import com.music.bitchord.ui.tv.auth.TvQrCodeView
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvDialogButton
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Outlives the alert on purpose: "Hide" closes it, and the download has to keep
 * going behind it. [AppUpdateChecker] holds the one download there can be.
 */
private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * A new BitChord TV release is out: download its APK and hand it to the system
 * installer without leaving the app, as the phone's update sheet does.
 *
 * Closing the alert mid-download only hides it — the download keeps going, and
 * Settings' "Software Update" row reopens the alert onto wherever it got to.
 * A release with no TV APK on it (or a download that can't be installed) has
 * nowhere on a TV to send the user, as most have no browser, so the releases
 * page is offered as a code to scan with a phone instead.
 */
@Composable
fun TvUpdateDialog(update: AppUpdateChecker.UpdateInfo, onDismiss: () -> Unit) {
    val state by AppUpdateChecker.download.collectAsState()
    val notes = remember(update.notes) { update.notes?.let(::plainNotes) }

    TvDialog(
        title = "BitChord ${update.version} Is Available",
        message = when {
            update.apkUrl == null -> "Scan the code with your phone to get this update from GitHub."
            state is DownloadState.Ready -> "Downloaded. Select Install Now to update."
            state is DownloadState.Failed -> (state as DownloadState.Failed).message
            else -> null
        },
        width = 512.dp,
        onDismissRequest = onDismiss,
    ) {
        if (update.apkUrl == null) {
            TvQrCodeView(content = update.releaseUrl, size = 147.dp)
            Spacer(modifier = Modifier.height(21.dp))
            TvDialogButton(text = "OK", onClick = onDismiss, initialFocus = true)
        } else {
            TvUpdateControls(state, notes, onDismiss)
        }
    }
}

@Composable
private fun TvUpdateControls(state: DownloadState, notes: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    if (!notes.isNullOrBlank() && state is DownloadState.Idle) {
        Text(
            text = notes,
            style = TvType.Callout,
            color = TvGlass.TextSecondary,
            textAlign = TextAlign.Start,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 21.dp),
        )
    }

    // Keyed on the state's kind, so each new set of buttons comes in with
    // focus on its first one rather than leaving focus on a button that
    // has just gone away.
    key(state::class) {
        when (val s = state) {
            DownloadState.Idle -> {
                TvDialogButton(
                    text = "Download and Install",
                    onClick = { downloadScope.launch { AppUpdateChecker.downloadApk(context) } },
                    initialFocus = true,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TvDialogButton(text = "Not Now", onClick = onDismiss)
            }

            is DownloadState.Downloading -> {
                TvDownloadProgress(fraction = s.fraction)
                Spacer(modifier = Modifier.height(21.dp))
                TvDialogButton(text = "Hide", onClick = onDismiss, initialFocus = true)
                Spacer(modifier = Modifier.height(6.dp))
                TvDialogButton(
                    text = "Cancel Download",
                    destructive = true,
                    onClick = AppUpdateChecker::cancelDownload,
                )
            }

            is DownloadState.Ready -> {
                TvDialogButton(
                    text = "Install Now",
                    onClick = { AppUpdateChecker.installApk(context, s.file) },
                    initialFocus = true,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TvDialogButton(text = "Not Now", onClick = onDismiss)
            }

            is DownloadState.Failed -> {
                TvDialogButton(
                    text = "Try Again",
                    onClick = { downloadScope.launch { AppUpdateChecker.downloadApk(context) } },
                    initialFocus = true,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TvDialogButton(
                    text = "Not Now",
                    onClick = {
                        AppUpdateChecker.resetDownload()
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** A thin rounded bar and the percentage under it. The fill is drawn, not laid out. */
@Composable
private fun TvDownloadProgress(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .drawBehind {
                drawRect(TvGlass.FillStrong)
                drawRoundRect(
                    color = TvGlass.TextPrimary,
                    size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Downloading… ${(fraction * 100).toInt()}%",
        style = TvType.Caption,
        color = TvGlass.TextSecondary,
    )
}

/**
 * The release body is GitHub Markdown; an alert shows it as plain lines —
 * headings and emphasis markers dropped, list items as bullets, links as their
 * text.
 */
private fun plainNotes(markdown: String): String =
    markdown.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("<!--") && !it.startsWith("![") }
        .map { line ->
            line.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                .replace(Regex("""^#+\s*"""), "")
                .replace(Regex("""^[-*+]\s+"""), "• ")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
        }
        .joinToString("\n")
