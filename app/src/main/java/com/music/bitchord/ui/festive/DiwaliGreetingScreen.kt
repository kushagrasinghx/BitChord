package com.music.bitchord.ui.festive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.animation.core.tween
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R

/**
 * One-time Diwali greeting: the illustration full-bleed, with the wish set at
 * the foot of it in the welcome screen's own type — [displayLarge] heading,
 * [bodyLarge] subtitle, full-width capsule — all in white.
 *
 * The foot of the painting is the brightest part of it (the rangoli and its
 * diyas), so a deep scrim rises from the bottom for the white type to sit on.
 */
@Composable
fun DiwaliGreetingScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2A1430))
            // Swallows every touch so nothing reaches the app underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Decoded off the main thread: a ~1600px JPEG decoded inline cost the
        // opening fade its first frames, every time the logo reopens this.
        val context = LocalContext.current
        val art by produceState<ImageBitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(context.resources, R.drawable.diwali_greeting)?.asImageBitmap()
            }
        }
        val artAlpha by animateFloatAsState(if (art != null) 1f else 0f, tween(350), label = "diwaliArt")
        art?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artAlpha },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.12f to Color.Transparent,
                        0.48f to Color.Transparent,
                        0.72f to Color(0xFF1A0A1E).copy(alpha = 0.72f),
                        1f to Color(0xFF12061A).copy(alpha = 0.96f),
                    ),
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                // Capped first, then filled: the other way round the fill's
                // full width wins and a tablet or landscape phone gets a
                // screen-wide button.
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    text = stringResource(R.string.diwali_title),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-1.2).sp,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.diwali_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(28.dp))
                WhitePill(
                    label = stringResource(R.string.diwali_cta),
                    onClick = onDismiss,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The welcome screen's capsule, in frosted white with white type. */
@Composable
private fun WhitePill(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "diwaliPill")
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
