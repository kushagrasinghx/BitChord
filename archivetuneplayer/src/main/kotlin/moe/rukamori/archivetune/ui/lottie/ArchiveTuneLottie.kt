/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.lottie

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import moe.rukamori.archivetune.R
import androidx.compose.runtime.getValue

@Composable
fun rememberArchiveTuneLottieComposition(
    @RawRes rawRes: Int,
): LottieComposition? {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(rawRes),
    )
    return composition
}

@Composable
private fun archiveTuneTintColorProperties(tintColor: Color): LottieDynamicProperties =
    rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            LottieProperty.COLOR,
            tintColor.toArgb(),
            "**",
            "Color",
            "**",
        ),
        rememberLottieDynamicProperty(
            LottieProperty.STROKE_COLOR,
            tintColor.toArgb(),
            "**",
            "Color",
            "**",
        ),
    )

private typealias LottieDynamicProperties = com.airbnb.lottie.compose.LottieDynamicProperties

@Composable
fun ArchiveTuneLottieAnimation(
    @RawRes rawRes: Int,
    trigger: Any?,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(rawRes),
    )
    val parsed = composition ?: return

    val dynamicProperties = if (tintColor != null) archiveTuneTintColorProperties(tintColor) else null

    key(trigger) {
        val progress by animateLottieCompositionAsState(
            composition = parsed,
            iterations = 1,
            restartOnPlay = true,
            speed = 1f,
            isPlaying = trigger != null,
        )
        LottieAnimation(
            composition = parsed,
            progress = { progress },
            modifier = modifier,
            contentScale = contentScale,
            dynamicProperties = dynamicProperties,
        )
    }
}

@Composable
fun ArchiveTuneLottieLoop(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
    isPlaying: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(rawRes),
    )
    val parsed = composition ?: return

    val dynamicProperties = if (tintColor != null) archiveTuneTintColorProperties(tintColor) else null

    val progress by animateLottieCompositionAsState(
        composition = parsed,
        iterations = LottieConstants.IterateForever,
        isPlaying = isPlaying,
        speed = 1f,
    )

    LottieAnimation(
        composition = parsed,
        progress = { progress },
        modifier = modifier,
        contentScale = contentScale,
        dynamicProperties = dynamicProperties,
    )
}

object ArchiveTuneLottie {
    const val LikeRes: Int = R.raw.lottie_like
    const val DownloadCompleteRes: Int = R.raw.lottie_download_complete
    const val EmptyStateRes: Int = R.raw.lottie_empty_state
}
