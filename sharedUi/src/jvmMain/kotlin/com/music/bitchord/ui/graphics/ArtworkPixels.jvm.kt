package com.music.bitchord.ui.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Skia bitmaps are always CPU-readable.
actual fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = this

actual fun Image.toImageBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()

actual fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
    // BGRA_8888 little-endian is ARGB as an int, byte for byte.
    val bytes = ByteBuffer.allocate(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    bytes.asIntBuffer().put(pixels)
    bitmap.installPixels(bytes.array())
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

actual fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> =
    PaletteQuantizer.swatches(image, maxColors, clearFilters)
