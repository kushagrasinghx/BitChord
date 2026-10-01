package com.music.bitchord.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.data.spotify.SpotifyTrack

private const val LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val LOGIN_LAYOUT_FIX = """
    (function () {
      var id = 'bitchord-login-layout-fix';
      if (document.getElementById(id)) return;
      var st = document.createElement('style');
      st.id = id;
      st.textContent =
        'html, body { height: auto !important; min-height: 100% !important; overflow: visible !important; }' +
        'body > div { height: auto !important; min-height: 100% !important; }' +
        'main { position: static !important; height: auto !important; min-height: 100dvh !important;' +
        ' max-height: none !important; overflow: visible !important; }';
      (document.head || document.documentElement).appendChild(st);
    })();
"""

@Composable
fun SpotifyLibraryScreen(
    onPlay: (SpotifyTrack) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val cookie by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var opened by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showLogin) { showLogin = false }
    BackHandler(enabled = opened != null && !showLogin) { opened = null }

    LaunchedEffect(cookie) {
        if (cookie.isBlank()) {
            playlists = emptyList()
            opened = null
            tracks = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { playlists = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(opened?.id) {
        val playlist = opened ?: return@LaunchedEffect
        loading = true
        error = null
        tracks = emptyList()
        runCatching { SpotifyLibrary.tracks(playlist.id) }
            .onSuccess { tracks = it }
            .onFailure { error = it.message }
        loading = false
    }

    if (showLogin) {
        SpotifyLogin(
            modifier = modifier.padding(contentPadding),
            onConnected = { token ->
                AppSettings.setSpotifySpdcToken(token)
                showLogin = false
            },
        )
        return
    }

    val playlist = opened
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Text(
                text = playlist?.name ?: stringResource(R.string.spotify),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            )
        }
        if (cookie.isBlank()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.spotify_connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showLogin = true }) {
                        Text(stringResource(R.string.spotify_sign_in))
                    }
                }
            }
        } else {
            item {
                TextButton(
                    onClick = { AppSettings.setSpotifySpdcToken("") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.spotify_disconnect))
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (!loading && error == null && playlist == null && playlists.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.spotify_empty_playlists),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (playlist == null) {
                items(playlists, key = { it.id }) { item ->
                    LibraryRow(
                        title = item.name,
                        subtitle = item.owner.orEmpty(),
                        imageUrl = item.imageUrl,
                        onClick = { opened = item },
                    )
                }
            } else {
                if (!loading && error == null && tracks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.spotify_empty_tracks),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    LibraryRow(
                        title = track.title,
                        subtitle = track.artist,
                        imageUrl = track.imageUrl,
                        onClick = { onPlay(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyLogin(
    onConnected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sent by remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = LOGIN_USER_AGENT
                if (Build.VERSION.SDK_INT >= 33) {
                    settings.isAlgorithmicDarkeningAllowed = false
                } else if (Build.VERSION.SDK_INT >= 29) {
                    @Suppress("DEPRECATION")
                    settings.forceDark = WebSettings.FORCE_DARK_OFF
                }
                setBackgroundColor(0xFF121212.toInt())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(LOGIN_LAYOUT_FIX, null)
                        if (sent || url?.startsWith("https://open.spotify.com") != true) return
                        val raw = CookieManager.getInstance().getCookie("https://open.spotify.com") ?: return
                        val token = raw.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("sp_dc=") }
                            ?.substringAfter("=")
                            ?.takeIf { it.isNotBlank() }
                            ?: return
                        sent = true
                        onConnected(token)
                    }
                }
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )
}
