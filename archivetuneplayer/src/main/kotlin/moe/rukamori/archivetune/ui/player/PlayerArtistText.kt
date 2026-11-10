/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ClickableArtists(
    artists: List<MediaMetadata.Artist>,
    onArtistClick: (artistId: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
    artistThreshold: Int = 24,
    fadeWidth: Dp = 24.dp,
) {
    val annotatedString =
        remember(artists) {
            buildAnnotatedString {
                artists.forEachIndexed { index, artist ->
                    pushStringAnnotation(tag = "artist_${artist.id.orEmpty()}", annotation = artist.id.orEmpty())
                    append(artist.name)
                    pop()
                    if (index != artists.lastIndex) append(", ")
                }
            }
        }

    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val layoutResult = layoutState.value
    val viewportWidth = remember { mutableStateOf(0) }
    val shouldFade =
        viewportWidth.value > 0 &&
            (layoutResult?.size?.width ?: 0) > viewportWidth.value

    Box(
        modifier =
            (if (shouldFade) modifier.viewportEdgeFade(fadeWidth) else modifier)
                .clipToBounds()
                .onSizeChanged { viewportWidth.value = it.width },
    ) {
        Text(
            text = annotatedString,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutState.value = it },
            modifier =
                Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                    .pointerInput(annotatedString) {
                    detectTapGestures(
                        onTap = { offset ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val position = layout.getOffsetForPosition(offset)
                            annotatedString
                                .getStringAnnotations(position, position)
                                .firstOrNull()
                                ?.let { onArtistClick(it.item) }
                        },
                        onLongPress = onLongClick?.let { handler -> { handler() } },
                    )
                },
        )
    }
}
