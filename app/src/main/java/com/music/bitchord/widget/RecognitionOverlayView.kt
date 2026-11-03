package com.music.bitchord.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.music.bitchord.R
import kotlin.math.abs
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
    private var searching = false
    private var topInset = 0f
    private var rightInset = 0f
    private var targetAudioLevel = 0f
    private var displayAudioLevel = 0f
    private var colorPhase = 0f
    private var wavePhase = 0f
    private var lastFrameNanos = 0L
    private var frameLoopRunning = false

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = FloatArray(3)
    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            val deltaSeconds = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.008f, 0.05f)
            }
            lastFrameNanos = frameTimeNanos

            // Ease the microphone updates into the render cadence instead of
            // stepping the color field and pills at the recorder's slower cadence.
            val follow = (deltaSeconds * 9f).coerceAtMost(1f)
            displayAudioLevel += (targetAudioLevel - displayAudioLevel) * follow
            val energy = 0.18f + displayAudioLevel * 0.82f
            // Keep this phase continuous. The orbit itself is periodic, but
            // wrapping here made the hue expression jump when its multiplier
            // reached the end of a cycle.
            colorPhase += deltaSeconds * (0.72f + energy * 1.8f)
            wavePhase = (wavePhase + deltaSeconds * WAVE_PHASE_SPEED) % TAU
            postInvalidateOnAnimation()
            choreographer.postFrameCallback(this)
        }
    }
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
    init {
        isFocusable = true
        contentDescription = context.getString(R.string.quick_tile_recognition_label)
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

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setSearching(value: Boolean) {
        if (searching == value) return
        searching = value
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        frameLoopRunning = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        frameLoopRunning = false
        choreographer.removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
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
        drawSmoothWave(canvas, wave)

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

    private fun drawSmoothWave(canvas: Canvas, bounds: RectF) {
        val gap = bounds.width() * 0.10f
        val bars = 5
        val barWidth = (bounds.width() - gap * (bars - 1)) / bars
        val maxHeight = bounds.height() * 0.94f
        wavePaint.color = Color.WHITE
        wavePaint.alpha = ((0.62f + displayAudioLevel * 0.38f) * 255f).toInt()
        repeat(bars) { index ->
            val wave = 0.30f + 0.70f * abs(sin(wavePhase + index * 0.85f))
            val idleHeight = bounds.height() * (0.22f + 0.12f * wave)
            val barHeight = idleHeight + (maxHeight - idleHeight) * displayAudioLevel * wave
            val left = bounds.left + index * (barWidth + gap)
            val top = bounds.centerY() - barHeight / 2f
            canvas.drawRoundRect(
                RectF(left, top, left + barWidth, top + barHeight),
                barWidth / 2f,
                barWidth / 2f,
                wavePaint,
            )
        }
        wavePaint.alpha = 255
    }

    private fun drawReactiveBackground(canvas: Canvas, width: Float, height: Float) {
        // Keep the activity transparent so the launcher remains visible. The
        // color field is made from soft orbiting gradients whose hue, speed,
        // size, and intensity all respond to the live microphone level.
        val centerX = width / 2f
        val centerY = height * 0.42f
        val energy = 0.18f + displayAudioLevel * 0.82f
        val orbit = min(width, height) * (0.15f + energy * 0.12f)
        val radius = maxOf(width, height) * (0.30f + energy * 0.10f)
        val alpha = (34f + energy * 82f).toInt()
        val baseHue = (colorPhase * 48f + displayAudioLevel * 110f) % 360f

        repeat(5) { index ->
            val angle = colorPhase * (0.65f + displayAudioLevel * 0.55f) +
                index * (TAU / 5f)
            val blobX = centerX + cos(angle) * orbit
            val blobY = centerY + sin(angle) * orbit * 1.16f
            val hue = (baseHue + index * 58f + displayAudioLevel * index * 18f) % 360f
            hsv[0] = hue
            hsv[1] = 0.72f + displayAudioLevel * 0.22f
            hsv[2] = 1f
            val color = Color.HSVToColor(alpha, hsv)
            drawColorBlob(canvas, blobX, blobY, radius, color)
        }

        hsv[0] = (baseHue + 150f) % 360f
        hsv[1] = 0.55f
        hsv[2] = 1f
        val coreColor = Color.HSVToColor((22f + energy * 46f).toInt(), hsv)
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

    private companion object {
        const val TAU = 6.2831855f
        const val WAVE_PHASE_SPEED = 4.4879894f
    }
}
