package com.music.bitchord.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.BottomFadeScrim
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.TopFadeBlur
import com.music.bitchord.ui.components.TopBarBlur
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/*
 * Design previews for the two bars and the surfaces behind them.
 *
 * In `src/debug` rather than `src/main` on purpose: the sample data and the
 * scaffolding below are worth nothing to a shipped APK, and the `ui-tooling`
 * renderer that draws them is itself a `debugImplementation`. Android Studio
 * picks these up on any *Debug variant, which is the one being worked in.
 *
 * WHAT THESE DO AND DO NOT SHOW
 *
 * [BottomFadeScrim] is a plain shader over a rect, so what the preview draws is
 * exactly what the device draws — the gradient can be judged here.
 *
 * [TopFadeBlur] and [TopBarBlur] cannot. Haze blurs by way of RenderEffect
 * against a real window, and the preview renderer has none, so the effect comes
 * out as a flat pane or as nothing at all. These previews are the place to
 * settle the bar's layout, type and colour; the blur itself has to be read on a
 * device — `./gradlew :app:installDevDebug`.
 */

/** Stand-in feed rows, so the bars have something to sit over. */
@Composable
private fun MockFeed(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(12) { i ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        // Alternating tones, so the ramp has both a light and a
                        // dark edge to be judged against rather than one flat
                        // field that hides where it starts.
                        .background(if (i % 2 == 0) Color(0xFF3A3A3C) else Color(0xFF8E8E93)),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Track title $i", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Artist name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A track with no artwork URL, deliberately.
 *
 * Coil cannot reach the network from the preview renderer, so a real URL would
 * draw the same empty box this does — only after a failed load rather than
 * instead of one. Nulling it hands the box straight to the placeholder tint
 * [MiniPlayer] already paints under its artwork, which is what the device shows
 * for the beat before the real art arrives anyway.
 */
private val PreviewSong = Song(
    videoId = "preview",
    title = "Rains Again",
    artist = "Solji",
    thumbnailUrl = null,
)

private val PreviewTabs = listOf(
    BottomTab("Play", BitChordIcons.Play),
    BottomTab("Explore", BitChordIcons.Explore),
    BottomTab("Library", BitChordIcons.Library),
    BottomTab("Search", BitChordIcons.Search),
)

/**
 * The whole chrome stack in the order [com.music.bitchord.MainActivity] draws
 * it: feed, bounded top blur, bar, bottom scrim, pill.
 */
@Composable
private fun ChromeStack(scrolled: Boolean, withMiniPlayer: Boolean) {
    val hazeState = remember { HazeState() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MockFeed(Modifier.hazeSource(hazeState))

        TopBarBlur(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        FrostedTopBar(
            title = "Listen Now",
            scrolled = scrolled,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomFadeScrim(
            withMiniPlayer = withMiniPlayer,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // The same column MainActivity stacks these in, down to the 8dp gap —
        // the point of this preview is the spacing between the two bars, so it
        // has to be the spacing the app actually uses.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (withMiniPlayer) {
                MiniPlayer(
                    song = PreviewSong,
                    isPlaying = true,
                    isLoading = false,
                    hazeState = hazeState,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onExpand = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FloatingBottomBar(
                tabs = PreviewTabs,
                selectedIndex = 0,
                onTabSelected = {},
                hazeState = hazeState,
            )
        }
    }
}

@Preview(name = "Chrome · dark", device = "id:pixel_8", showBackground = true)
@Composable
private fun ChromeDarkPreview() {
    BitChordTheme(darkTheme = true) { ChromeStack(scrolled = true, withMiniPlayer = false) }
}

@Preview(name = "Chrome · light", device = "id:pixel_8", showBackground = true)
@Composable
private fun ChromeLightPreview() {
    BitChordTheme(darkTheme = false) { ChromeStack(scrolled = true, withMiniPlayer = false) }
}

/** The taller scrim, which the mini player's arrival grows it into. */
@Preview(name = "Chrome · mini player", device = "id:pixel_8", showBackground = true)
@Composable
private fun ChromeMiniPlayerPreview() {
    BitChordTheme(darkTheme = true) { ChromeStack(scrolled = true, withMiniPlayer = true) }
}

/**
 * The mini player on its own, at the three states the transport slot takes.
 *
 * This is the preview for the pill's geometry: the corner is half the height,
 * so it moves whenever the row's padding or the artwork's size does, and the
 * thing to look at is whether the artwork and the skip glyph still clear the
 * curve at either end. The glass does not render here — what is being judged
 * is the shape and the spacing inside it.
 */
@Preview(name = "Mini player · dark", widthDp = 400, heightDp = 260)
@Preview(
    name = "Mini player · light",
    widthDp = 400,
    heightDp = 260,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun MiniPlayerPreview() {
    BitChordTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val haze = remember { HazeState() }
            MiniPlayer(
                song = PreviewSong,
                isPlaying = true,
                isLoading = false,
                hazeState = haze,
                onPlayPause = {}, onNext = {}, onPrevious = {}, onExpand = {},
                modifier = Modifier.fillMaxWidth(),
            )
            MiniPlayer(
                song = PreviewSong.copy(title = "A considerably longer track title that has to truncate"),
                isPlaying = false,
                isLoading = false,
                hazeState = haze,
                onPlayPause = {}, onNext = {}, onPrevious = {}, onExpand = {},
                modifier = Modifier.fillMaxWidth(),
            )
            MiniPlayer(
                song = PreviewSong,
                isPlaying = false,
                isLoading = true,
                hazeState = haze,
                onPlayPause = {}, onNext = {}, onPrevious = {}, onExpand = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The scrim alone, over a flat field.
 *
 * The stack above shows it in place; this shows the ramp itself, which is what
 * banding would be visible in. Judge it here before judging it there.
 */
@Preview(name = "Scrim ramp · dark", widthDp = 300, heightDp = 260)
@Preview(
    name = "Scrim ramp · light",
    widthDp = 300,
    heightDp = 260,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun ScrimRampPreview() {
    BitChordTheme {
        Box(Modifier.fillMaxSize()) {
            // A mid grey under it: the ramp reads as a ramp against this,
            // whereas over the theme's own background it is invisible by
            // construction — it is fading *to* that colour.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF7A7A7E)),
            )
            BottomFadeScrim(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The bar's two backdrops side by side.
 *
 * The regular blurred state and Reduce dynamic blur share this exact footprint
 * and hairline. Haze does not render in Preview, so these samples concentrate
 * on the bar's own paint and layout.
 */
@Preview(name = "Top bar · backdrops", device = "id:pixel_8", showBackground = true)
@Composable
private fun TopBarBackdropPreview() {
    BitChordTheme(darkTheme = true) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.height(160.dp)) {
                FrostedTopBar(title = "At rest", scrolled = false)
            }
            Box(Modifier.height(160.dp)) {
                FrostedTopBar(title = "Scrolled", scrolled = true)
            }
            Box(Modifier.height(160.dp)) {
                FrostedTopBar(title = "Pushed page", scrolled = true, onBack = {})
            }
        }
    }
}
