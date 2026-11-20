package com.music.bitchord.ui.replay.year

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.replay.ShareAction
import com.music.bitchord.ui.replay.cacheForSharing
import com.music.bitchord.ui.replay.saveToGallery
import com.music.bitchord.ui.replay.sendIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sharing a card of the year story: the card itself, as it stood when it had
 * finished playing, not one of the ordinary Replay's posters.
 *
 * The card is laid out at full story size and drawn into a layer that the
 * preview shows scaled down, and once its artwork has had a moment to arrive
 * that layer is read back as the picture. So what is sent is what was seen,
 * at the phone's own resolution, with nothing drawn twice by hand.
 */
@Composable
fun ReplayYearShareSheet(year: ReplayYear, slide: YearSlide, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(year, slide) {
        // The covers on the card come from the image cache, but a frame or
        // two late; read back too soon and they are blank tiles.
        delay(SETTLE_MS)
        picture = runCatching {
            layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.share_my_replay),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.replay_share_card_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(
            Modifier
                .fillMaxWidth(0.42f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(CARD_WIDTH / CARD_HEIGHT)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val captured = picture
            if (captured != null) {
                Image(
                    bitmap = captured.asImageBitmap(),
                    contentDescription = stringResource(R.string.your_replay),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The card at full size, shrunk to the preview; the layer it is
                // recorded into sits inside the shrinking, so it keeps every pixel.
                val scale = maxWidth / CARD_WIDTH.dp
                YearCardStill(
                    year = year,
                    slide = slide,
                    modifier = Modifier
                        .requiredSize(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .clipToBounds(),
                )
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Spacer(Modifier.height(22.dp))

        val ready = picture != null
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction(
                label = if (saved) stringResource(R.string.saved) else stringResource(R.string.save),
                icon = Icons.Rounded.Download,
                accent = false,
                enabled = ready && !saved,
                modifier = Modifier.weight(1f),
            ) {
                val image = picture ?: return@ShareAction
                scope.launch { saved = saveToGallery(context, image, "${year.year}-${slide.name.lowercase()}") }
            }
            ShareAction(
                label = stringResource(R.string.share),
                icon = Icons.Rounded.IosShare,
                accent = true,
                enabled = ready,
                modifier = Modifier.weight(1f),
            ) {
                val image = picture ?: return@ShareAction
                scope.launch {
                    val uri = cacheForSharing(context, image, fileName = "replay-year.png") ?: return@launch
                    context.startActivity(
                        Intent.createChooser(sendIntent(uri), context.getString(R.string.share_your_replay)),
                    )
                    onDismiss()
                }
            }
        }
    }
}

/** The card's size as the picture is drawn, in dp: a phone's width, at the story's 9:16. */
private const val CARD_WIDTH = 405f
private const val CARD_HEIGHT = 720f

private const val SETTLE_MS = 1_200L
