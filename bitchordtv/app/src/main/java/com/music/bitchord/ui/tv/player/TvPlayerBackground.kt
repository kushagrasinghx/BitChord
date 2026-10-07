package com.music.bitchord.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.player.ArtworkMeshBackdrop
import com.music.bitchord.ui.player.rememberArtworkMesh
import com.music.bitchord.ui.tv.components.TvMeshBackground

/**
 * Ultra-performance cinematic Apple Music TV background.
 * Randomizes and scrambles album cover pixels into a dynamic colorful mesh,
 * blurred by 80% with an Apple TV dark cinema scrim.
 */
@Composable
fun TvPlayerBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    isLyricsMode: Boolean = false,
) {
    val mesh = rememberArtworkMesh(imageUrl = artworkUrl)

    Box(modifier = modifier.fillMaxSize()) {
        if (mesh != null) {
            ArtworkMeshBackdrop(
                mesh = mesh,
                blurRadius = 42.dp,
                seam = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TvMeshBackground(
                artworkUrl = artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 80% blur cinema scrim: deep gradient overlay keeping contrast optimal for TV
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = if (isLyricsMode) 0.65f else 0.40f),
                        0.5f to Color.Black.copy(alpha = if (isLyricsMode) 0.75f else 0.50f),
                        1.0f to Color.Black.copy(alpha = if (isLyricsMode) 0.85f else 0.65f),
                    )
                ),
        )
    }
}

