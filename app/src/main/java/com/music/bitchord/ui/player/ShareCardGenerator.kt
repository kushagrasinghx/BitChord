package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ShareCardGenerator {
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920
    private const val ARTWORK_HEIGHT = (CARD_HEIGHT * 0.6f).toInt()
    private const val PADDING = 80f
    private const val CORNER_RADIUS = 60f
    private const val WATERMARK_PADDING = 60f

    fun generateShareCard(
        context: Context,
        artwork: Bitmap?,
        title: String,
        artist: String
    ): File? {
        return try {
            val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw dark frosted glass style background
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#121212")
            }
            canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

            // Draw artwork
            if (artwork != null) {
                val artWidth = CARD_WIDTH.toFloat()
                val artHeight = ARTWORK_HEIGHT.toFloat()

                // Scale artwork to fill width and crop height if necessary
                val scale = Math.max(artWidth / artwork.width, artHeight / artwork.height)
                val scaledWidth = artwork.width * scale
                val scaledHeight = artwork.height * scale

                val left = (artWidth - scaledWidth) / 2f
                val top = 0f

                val margin = PADDING
                val artRect = RectF(margin, margin, artWidth - margin, artHeight - margin)
                val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Use a BitmapShader for rounded corners on the bottom
                val shader = android.graphics.BitmapShader(
                    Bitmap.createScaledBitmap(artwork, scaledWidth.toInt(), scaledHeight.toInt(), true),
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
                )

                // Adjust shader matrix to center
                val matrix = android.graphics.Matrix()
                matrix.postTranslate(left, top)
                shader.setLocalMatrix(matrix)
                artPaint.shader = shader

                // Draw rounded rect, but we only want rounded corners at the bottom
                // We draw a full rect first, then draw a rect over the top half to make it square on top
                canvas.drawRoundRect(artRect, CORNER_RADIUS, CORNER_RADIUS, artPaint)
            }

            // Draw subtle gradient overlay at bottom
            val gradientPaint = Paint().apply {
                shader = LinearGradient(
                    0f, CARD_HEIGHT.toFloat() * 0.7f,
                    0f, CARD_HEIGHT.toFloat(),
                    Color.TRANSPARENT, Color.parseColor("#000000"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, CARD_HEIGHT.toFloat() * 0.7f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), gradientPaint)

            // Draw Text
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 90f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B3B3B3")
                textSize = 60f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            // Draw title with max 2 lines
            val titleWords = title.split(" ")
            var line1 = ""
            var line2 = ""
            var currentWord = 0

            while (currentWord < titleWords.size && titlePaint.measureText(line1 + titleWords[currentWord] + " ") < CARD_WIDTH - PADDING * 2) {
                line1 += titleWords[currentWord] + " "
                currentWord++
            }

            while (currentWord < titleWords.size && titlePaint.measureText(line2 + titleWords[currentWord] + " ") < CARD_WIDTH - PADDING * 2) {
                line2 += titleWords[currentWord] + " "
                currentWord++
            }

            if (currentWord < titleWords.size) {
                line2 = line2.dropLast(3) + "..."
            }

            val titleY = ARTWORK_HEIGHT + PADDING + 60f
            canvas.drawText(line1.trim(), PADDING, titleY, titlePaint)
            var nextY = titleY
            if (line2.isNotBlank()) {
                nextY += titlePaint.fontSpacing
                canvas.drawText(line2.trim(), PADDING, nextY, titlePaint)
            }

            nextY += artistPaint.fontSpacing + 20f

            canvas.drawText(artist, PADDING, nextY, artistPaint)

            // Draw BitChord watermark
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80FFFFFF")
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("BitChord", CARD_WIDTH - WATERMARK_PADDING, CARD_HEIGHT - WATERMARK_PADDING, logoPaint)

            // Save to file
            val sharedDir = File(context.cacheDir, "shared")
            if (!sharedDir.exists()) {
                sharedDir.mkdirs()
            }

            val file = File(sharedDir, "share_card_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            bitmap.recycle()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
