package com.music.bitchord.desktop

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

/** A cached, theme-independent invite QR for a nearby phone to scan. */
@Composable
internal fun DesktopQrCode(content: String, size: Dp = 104.dp) {
    val density = LocalDensity.current
    val quietZone = 9.dp
    val sidePx = with(density) { (size - quietZone * 2).roundToPx() }
    val image by produceState<ImageBitmap?>(null, content, sidePx) {
        value = withContext(Dispatchers.Default) { renderDesktopQr(content, sidePx, density) }
    }
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(quietZone),
    ) {
        image?.let { Image(it, contentDescription = "Party invite QR code", modifier = Modifier.fillMaxSize()) }
    }
}

private fun renderDesktopQr(content: String, sidePx: Int, density: Density): ImageBitmap? {
    if (content.isBlank() || sidePx <= 0) return null
    val matrix = runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            256,
            256,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }.getOrNull() ?: return null
    val image = ImageBitmap(sidePx, sidePx)
    val side = sidePx.toFloat()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(side, side)) {
        val cell = side / matrix.width
        val dot = cell * 0.86f
        val inset = (cell - dot) / 2f
        val radius = CornerRadius(dot * 0.3f, dot * 0.3f)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(x * cell + inset, y * cell + inset),
                    size = Size(dot, dot),
                    cornerRadius = radius,
                )
            }
        }
    }
    return image
}
