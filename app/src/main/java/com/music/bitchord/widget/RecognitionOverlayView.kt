package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.music.bitchord.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
    private var topInset = 0f
    private var rightInset = 0f
    private var audioLevel = 0f
    private var colorPhase = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(21f)
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(20f)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(156, 18, 22, 28)
        style = Paint.Style.FILL
    }
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(15f)
    }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(156, 18, 22, 28)
        style = Paint.Style.FILL
    }
    private val closeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val closeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 255, 220)
    }

    init {
        isFocusable = true
        contentDescription = context.getString(R.string.quick_tile_recognition_label)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            topInset = safeInsets.top.toFloat()
            rightInset = safeInsets.right.toFloat()
            invalidate()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setWaveBitmap(bitmap: Bitmap, level: Float = 0f) {
        waveBitmap = bitmap
        audioLevel = level.coerceIn(0f, 1f)
        colorPhase = (colorPhase + 0.08f + audioLevel * 0.16f) % (2f * Math.PI.toFloat())
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

        drawReactiveBackground(canvas, width, height)

        val centerX = width / 2f
        val closeCenterX = width - rightInset - dp(30f)
        val closeCenterY = topInset + dp(30f)
        canvas.drawCircle(closeCenterX, closeCenterY, dp(22f), closePaint)
        canvas.drawCircle(closeCenterX, closeCenterY, dp(22f), closeBorderPaint)
        canvas.drawLine(
            closeCenterX - dp(7.5f),
            closeCenterY - dp(7.5f),
            closeCenterX + dp(7.5f),
            closeCenterY + dp(7.5f),
            closeLinePaint,
        )
        canvas.drawLine(
            closeCenterX + dp(7.5f),
            closeCenterY - dp(7.5f),
            closeCenterX - dp(7.5f),
            closeCenterY + dp(7.5f),
            closeLinePaint,
        )

        val waveWidth = min(dp(180f), width * 0.58f)
        val waveHeight = waveWidth * 54f / 88f
        val waveCenterY = height * 0.42f
        val waveTop = waveCenterY - waveHeight / 2f
        canvas.drawCircle(centerX, waveCenterY, dp(92f), glowPaint.apply {
            shader = RadialGradient(
                centerX,
                waveCenterY,
                dp(100f),
                Color.argb(46, 210, 255, 220),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        })
        glowPaint.shader = null

        canvas.drawCircle(centerX, waveTop - dp(34f), dp(4f), accentPaint)
        canvas.drawText(
            if (searching) context.getString(R.string.recognition_searching)
            else context.getString(R.string.quick_tile_listening),
            centerX,
            waveTop - dp(28f),
            headerPaint,
        )

        val wave = RectF(
            centerX - waveWidth / 2f,
            waveCenterY - waveHeight / 2f,
            centerX + waveWidth / 2f,
            waveCenterY + waveHeight / 2f,
        )
        val bitmap = waveBitmap
        if (bitmap != null) {
            wavePaint.alpha = (105 + audioLevel * 55f).toInt()
            canvas.drawBitmap(bitmap, null, wave, wavePaint)
            wavePaint.alpha = 255
        } else {
            drawStaticWave(canvas, wave)
        }

        canvas.drawText(
            context.getString(R.string.recognition_tile_hint),
            centerX,
            wave.bottom + dp(42f),
            hintPaint,
        )
        val cancelButton = RectF(
            centerX - dp(68f),
            height - dp(112f),
            centerX + dp(68f),
            height - dp(58f),
        )
        canvas.drawRoundRect(cancelButton, dp(27f), dp(27f), buttonPaint)
        canvas.drawRoundRect(cancelButton, dp(27f), dp(27f), buttonBorderPaint)
        canvas.drawText(
            context.getString(R.string.recognition_cancel),
            centerX,
            cancelButton.centerY() + dp(5f),
            cancelPaint,
        )
    }

    private fun drawStaticWave(canvas: Canvas, bounds: RectF) {
        val gap = bounds.width() * 0.10f
        val barWidth = (bounds.width() - gap * 4f) / 5f
        val heights = floatArrayOf(0.38f, 0.52f, 0.70f, 0.48f, 0.62f)
        wavePaint.color = Color.argb((105 + audioLevel * 55f).toInt(), 255, 255, 255)
        heights.forEachIndexed { index, fraction ->
            val left = bounds.left + index * (barWidth + gap)
            val barHeight = bounds.height() * fraction
            val top = bounds.centerY() - barHeight / 2f
            canvas.drawRoundRect(
                RectF(left, top, left + barWidth, top + barHeight),
                barWidth / 2f,
                barWidth / 2f,
                wavePaint,
            )
        }
    }

    private fun drawReactiveBackground(canvas: Canvas, width: Float, height: Float) {
        // Keep the activity transparent so the launcher remains visible. The
        // color field is made from soft orbiting gradients whose hue, speed,
        // size, and intensity all respond to the live microphone level.
        val centerX = width / 2f
        val centerY = height * 0.42f
        val energy = 0.18f + audioLevel * 0.82f
        val orbit = min(width, height) * (0.15f + energy * 0.12f)
        val radius = maxOf(width, height) * (0.30f + energy * 0.10f)
        val alpha = (34f + energy * 82f).toInt()
        val baseHue = (colorPhase * 48f + audioLevel * 110f) % 360f

        repeat(6) { index ->
            val angle = colorPhase * (0.65f + audioLevel * 0.55f) +
                index * (Math.PI.toFloat() / 3f)
            val blobX = centerX + cos(angle) * orbit
            val blobY = centerY + sin(angle) * orbit * 1.16f
            val hue = (baseHue + index * 58f + audioLevel * index * 18f) % 360f
            val color = Color.HSVToColor(
                alpha,
                floatArrayOf(hue, 0.72f + audioLevel * 0.22f, 1f),
            )
            drawColorBlob(canvas, blobX, blobY, radius, color)
        }

        val coreColor = Color.HSVToColor(
            (22f + energy * 46f).toInt(),
            floatArrayOf((baseHue + 150f) % 360f, 0.55f, 1f),
        )
        drawColorBlob(canvas, centerX, centerY, radius * 0.78f, coreColor)
    }

    private fun drawColorBlob(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
    ) {
        backgroundPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            color,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        backgroundPaint.shader = null
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * scaledDensity
}
