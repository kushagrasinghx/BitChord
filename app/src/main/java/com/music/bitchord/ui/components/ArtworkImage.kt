package com.music.bitchord.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.SingletonImageLoader
import com.music.bitchord.R
import com.music.bitchord.data.model.artworkAt
import java.io.File

/**
 * Loads artwork with graceful fallback: tries the local sidecar file first,
 * then falls back to a remote [fallbackUrl] (e.g. [thumbnailUrl]), and finally
 * shows a default placeholder when both fail.
 *
 * This covers the case where PR #456 saved a sidecar file that is later
 * corrupted, zero-byte, or otherwise undecodable by Coil — the UI no longer
 * goes blank; it reaches for the remote copy on the next network request.
 */
@Composable
fun ArtworkImage(
    localUri: String?,
    fallbackUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
) {
    val resolvedLocal = remember(localUri) {
        localUri?.let { Uri.parse(it)?.path }
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
    }

    val resolvedFallback = remember(fallbackUrl) {
        fallbackUrl?.let { url ->
            // Prefer the sized variant when available; Coil's cache will
            // share entries across sizes, so this is a no-op for cached hits.
            if (url.contains("w") && url.contains("h")) url else url.artworkAt(120) ?: url
        }
    }

    val defaultPlaceholder = painterResource(R.drawable.ic_logo)

    AsyncImage(
        model = when {
            resolvedLocal != null -> Uri.fromFile(resolvedLocal)
            resolvedFallback != null -> resolvedFallback
            else -> null
        },
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder ?: defaultPlaceholder,
        onError = { errorState ->
            val ctx = errorState.result.request.context
            // If we have a local file that failed to load and a remote fallback,
            // re-request with the remote URL so Coil fetches it.
            if (resolvedLocal != null && resolvedFallback != null) {
                SingletonImageLoader.get(ctx).enqueue(
                    coil3.request.ImageRequest.Builder(ctx)
                        .data(resolvedFallback)
                        .build(),
                )
            }
        },
    )
}

/**
 * Lightweight variant for remote-only URLs (no local sidecar). Falls back to
 * [fallbackUrl] when the primary URL fails, and shows a placeholder otherwise.
 */
@Composable
fun RemoteArtworkImage(
    url: String?,
    fallbackUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val sizedUrl = remember(url) { url?.artworkAt(120) ?: url }

    AsyncImage(
        model = sizedUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = painterResource(R.drawable.ic_logo),
        onError = { errorState ->
            val ctx = errorState.result.request.context
            if (fallbackUrl != null) {
                SingletonImageLoader.get(ctx).enqueue(
                    coil3.request.ImageRequest.Builder(ctx)
                        .data(fallbackUrl)
                        .build(),
                )
            }
        },
    )
}
