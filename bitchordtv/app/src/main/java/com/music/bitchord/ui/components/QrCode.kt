package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A party invite as a square somebody else can point a camera at.
 *
 * Drawn rather than decoded into a bitmap. ZXing hands back a bit matrix and
 * this paints it, which costs nothing extra — the matrix is the expensive part
 * — and buys the two things a stock QR bitmap cannot give: the modules are
 * rounded to match every other shape in the app, and the colours come from the
 * theme instead of being baked black on white.
 *
 * Painted once, off the main thread, into an [ImageBitmap]. Both halves of the
 * work are too heavy to sit in a frame: encoding walks the matrix several times
 * over, and a version-7 code is two thousand rounded rectangles, which is two
 * thousand draw calls on *every* frame of the sheet sliding up if the painting
 * happens in a [androidx.compose.foundation.Canvas]. Once it is a bitmap the
 * sheet's animation is blitting one image, and the encode has already happened
 * on [Dispatchers.Default] while the drawer was opening.
 *
 * [ErrorCorrectionLevel.M] rather than the default L: this gets photographed
 * off a phone screen at an angle, often a cracked one, and M tolerates about
 * 15% of the code being unreadable for a matrix only slightly denser.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    foreground: Color = Color.Black,
    background: Color = Color.White,
    quietZone: Dp = 12.dp,
) {
    val density = LocalDensity.current
    val sidePx = with(density) { (size - quietZone * 2).roundToPx() }

    val image by produceState<ImageBitmap?>(null, content, sidePx, foreground) {
        value = withContext(Dispatchers.Default) {
            renderQr(content, sidePx, foreground, density)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            // Painted even before the code is ready, so the sheet does not
            // reflow around a square appearing a frame or two in.
            .background(background)
            .padding(quietZone),
    ) {
        AnimatedVisibility(visible = image != null, enter = fadeIn()) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Encodes and paints [content], entirely off the main thread.
 *
 * Safe to call from a background dispatcher: [ImageBitmap] here is a software
 * bitmap, and [CanvasDrawScope] holds no state from the composition it came
 * from — only the [Density] it is handed.
 */
private fun renderQr(
    content: String,
    sidePx: Int,
    foreground: Color,
    density: Density,
): ImageBitmap? {
    if (sidePx <= 0 || content.isBlank()) return null
    val matrix = runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            QR_MATRIX_SIZE,
            QR_MATRIX_SIZE,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                // Ours, drawn as padding by the caller, so ZXing does not also
                // reserve a margin inside the matrix and shrink the modules.
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }.getOrNull() ?: return null

    val modules = matrix.width
    if (modules <= 0) return null

    val image = ImageBitmap(sidePx, sidePx)
    val side = sidePx.toFloat()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(side, side)) {
        val cell = side / modules
        // Slightly smaller than the cell so neighbouring modules read as
        // separate dots rather than merging into a solid block, which is what
        // makes a rounded code still scan.
        val dot = cell * 0.86f
        val radius = CornerRadius(dot * 0.3f, dot * 0.3f)
        val inset = (cell - dot) / 2f
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (!matrix.get(x, y)) continue
                drawRoundRect(
                    color = foreground,
                    topLeft = Offset(x * cell + inset, y * cell + inset),
                    size = Size(dot, dot),
                    cornerRadius = radius,
                )
            }
        }
    }
    return image
}

/**
 * The requested matrix size handed to ZXing.
 *
 * Not the drawn size: the encoder rounds up to whatever QR version actually
 * fits the content, so this is only a floor. Asking for something near the
 * final pixel size keeps that rounding from inflating the version.
 */
private const val QR_MATRIX_SIZE = 256
