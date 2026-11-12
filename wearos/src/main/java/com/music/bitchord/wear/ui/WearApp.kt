package com.music.bitchord.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.music.bitchord.wear.R
import com.music.bitchord.wear.data.Link
import com.music.bitchord.wear.data.PhoneLink

private const val PLAYER = "player"
private const val QUEUE = "queue"
private const val MORE = "more"
private const val OUTPUT = "output"

/** Player at the root; Up Next and More swipe away back to it, as every watch app does. */
@Composable
fun WearApp(link: PhoneLink) {
    val nav = rememberSwipeDismissableNavController()
    val ambient = LocalAmbient.current
    val np by link.nowPlaying.collectAsStateWithLifecycle()
    val showAmbient = ambient.active && np != null
    // No pill behind the clock: the mesh runs right up to the bezel.
    AppScaffold(timeText = { TimeText(backgroundColor = Color.Transparent) }) {
        // Kept composed under the ambient player so waking up lands exactly
        // where it was. Alpha 0 is skipped at draw time, so it costs nothing
        // while the always-on screen is up.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (showAmbient) 0f else 1f }) {
        SwipeDismissableNavHost(navController = nav, startDestination = PLAYER) {
            composable(PLAYER) {
                PlayerRoute(
                    link = link,
                    openQueue = { nav.navigate(QUEUE) },
                    openMore = { nav.navigate(MORE) },
                )
            }
            composable(QUEUE) {
                val queue by link.queue.collectAsStateWithLifecycle()
                QueueScreen(
                    queue = queue,
                    playingId = np?.mediaId,
                    onRequest = link::requestQueue,
                    onPlay = { index ->
                        link.skipTo(index)
                        nav.popBackStack()
                    },
                )
            }
            composable(MORE) {
                MoreScreen(np = np, link = link, onOpenOutput = { nav.navigate(OUTPUT) })
            }
            composable(OUTPUT) {
                // Straight back to the player once a choice is made.
                OutputScreen(link = link, onDone = { nav.popBackStack(PLAYER, inclusive = false) })
            }
        }
        }
        np?.takeIf { showAmbient }?.let { AmbientPlayer(it, ambient) }
    }
}

@Composable
private fun PlayerRoute(link: PhoneLink, openQueue: () -> Unit, openMore: () -> Unit) {
    val state by link.link.collectAsStateWithLifecycle()
    val heard by link.heard.collectAsStateWithLifecycle()
    val np by link.nowPlaying.collectAsStateWithLifecycle()
    val artwork by link.artwork.collectAsStateWithLifecycle()
    val volume by link.volume.collectAsStateWithLifecycle()
    val actions = remember(link) {
        PlayerActions(
            togglePlay = link::togglePlay,
            next = link::next,
            previous = link::previous,
            toggleLike = link::toggleLike,
            volume = link::adjustVolume,
            seekTo = link::seekTo,
            openQueue = openQueue,
            openMore = openMore,
        )
    }

    ScreenScaffold {
        val playing = np
        when {
            // The last state the phone sent is still the best thing to show
            // while it is being asked again; only "no phone at all" replaces it.
            state == Link.NoPhone -> NoPhone(onRetry = link::retry)
            playing != null -> NowPlayingScreen(np = playing, artwork = artwork, volume = volume, actions = actions)
            !heard -> Connecting()
            else -> NothingPlaying(onResume = link::togglePlay)
        }
    }
}

@Composable
private fun Connecting() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.connecting), style = WearType.subtitle, color = Secondary)
        }
    }
}

@Composable
private fun NoPhone(onRetry: () -> Unit) {
    Message(
        title = stringResource(R.string.no_phone_title),
        body = stringResource(R.string.no_phone_body),
        action = stringResource(R.string.retry),
        onAction = onRetry,
        mesh = false,
    )
}

@Composable
private fun NothingPlaying(onResume: () -> Unit) {
    Message(
        title = stringResource(R.string.nothing_playing),
        body = stringResource(R.string.nothing_playing_body),
        action = stringResource(R.string.resume),
        onAction = onResume,
        mesh = true,
    )
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit, mesh: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (mesh) MeshBackground(emptyList())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            if (!mesh) {
                Icon(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(title, style = WearType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(body, style = WearType.rowSub, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Pill(text = action, onClick = onAction)
        }
    }
}

/** The phone app's primary button: white, black label, fully rounded. */
@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .graphicsLayer {
                scaleX = if (pressed) 0.95f else 1f
                scaleY = if (pressed) 0.95f else 1f
            }
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
    ) {
        Text(text, style = WearType.button, color = Color.Black)
    }
}
