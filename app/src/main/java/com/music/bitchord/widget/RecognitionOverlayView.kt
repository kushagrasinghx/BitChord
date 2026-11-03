package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.music.bitchord.R
import kotlin.math.min

/**
 * The visible, Google-style recognition surface opened from Quick Settings.
 * The widget still uses the transparent activity window; this view is only
 * installed when the tile asks for a user-facing listen screen.
 */
class RecognitionOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private var waveBitmap: Bitmap? = null
    private var searching = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(72, 255, 255, 255)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(22f)
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = sp(13f)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = sp(15f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = sp(12f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 255, 220)
    }

    init {
        isFocusable = true
        contentDescription = context.getString(R.string.quick_tile_recognition_label)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setWaveBitmap(bitmap: Bitmap) {
        waveBitmap = bitmap
        invalidate()
    }

    fun setSearching(value: Boolean) {
        if (searching == value) return
        searching = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        canvas.drawColor(Color.argb(192, 0, 0, 0))

        val cardWidth = min(width - dp(32f), dp(380f))
        val cardHeight = min(height - dp(48f), dp(390f)).coerceAtLeast(dp(280f))
        val card = RectF(
            (width - cardWidth) / 2f,
            (height - cardHeight) / 2f,
            (width + cardWidth) / 2f,
            (height + cardHeight) / 2f,
        )
        val radius = dp(30f)

        fillPaint.shader = LinearGradient(
            card.left,
            card.top,
            card.right,
            card.bottom,
            Color.rgb(38, 47, 58),
            Color.rgb(10, 13, 17),
            Shader.TileMode.CLAMP,
        )
        fillPaint.setShadowLayer(dp(28f), 0f, dp(12f), Color.argb(150, 0, 0, 0))
        canvas.drawRoundRect(card, radius, radius, fillPaint)
        fillPaint.shader = null
        fillPaint.clearShadowLayer()
        canvas.drawRoundRect(card, radius, radius, borderPaint)

        val centerX = width / 2f
        canvas.drawCircle(centerX, card.top + dp(56f), dp(4f), accentPaint)
        canvas.drawText(
            context.getString(R.string.quick_tile_recognition_label),
            centerX,
            card.top + dp(39f),
            headerPaint,
        )
        canvas.drawText(
            if (searching) context.getString(R.string.recognition_searching)
            else context.getString(R.string.recognition_listening),
            centerX,
            card.top + dp(94f),
            titlePaint,
        )

        val waveWidth = min(dp(180f), card.width() * 0.72f)
        val waveHeight = waveWidth * 54f / 88f
        val waveCenterY = card.top + card.height() * 0.51f
        glowPaint.shader = RadialGradient(
            centerX,
            waveCenterY,
            dp(100f),
            Color.argb(55, 210, 255, 220),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, waveCenterY, dp(92f), glowPaint)
        glowPaint.shader = null

        val wave = RectF(
            centerX - waveWidth / 2f,
            waveCenterY - waveHeight / 2f,
            centerX + waveWidth / 2f,
            waveCenterY + waveHeight / 2f,
        )
        val bitmap = waveBitmap
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, wave, wavePaint)
        } else {
            drawStaticWave(canvas, wave)
        }

        if (!searching) {
            canvas.drawText(
                context.getString(R.string.recognition_hint),
                centerX,
                card.top + card.height() * 0.76f,
                subtitlePaint,
            )
        }
        canvas.drawText(
            context.getString(R.string.recognition_cancel),
            centerX,
            card.bottom - dp(28f),
            footerPaint,
        )
    }

    private fun drawStaticWave(canvas: Canvas, bounds: RectF) {
        val gap = bounds.width() * 0.10f
        val barWidth = (bounds.width() - gap * 4f) / 5f
        val heights = floatArrayOf(0.38f, 0.52f, 0.70f, 0.48f, 0.62f)
        heights.forEachIndexed { index, fraction ->
            val left = bounds.left + index * (barWidth + gap)
            val barHeight = bounds.height() * fraction
            val top = bounds.centerY() - barHeight / 2f
            canvas.drawRoundRect(
                RectF(left, top, left + barWidth, top + barHeight),
                barWidth / 2f,
                barWidth / 2f,
                wavePaint.apply { color = Color.argb(220, 255, 255, 255) },
            )
        }
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * scaledDensity
}
