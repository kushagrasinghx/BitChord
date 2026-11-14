package com.music.bitchord.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Static hardware render check. No Activity, gestures, or frame timing collection. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
@RequiresApi(28)
class GlassRimRenderTest {
    @Test
    fun retainedPaintsMatchFreshGeometryThroughFades() {
        val shape = ContinuousCornerShape(50)
        val size = Size(560f, 112f)
        val density = Density(2f)
        fun geometry() = GlassRimGeometry(shape, size, LayoutDirection.Ltr, density)
        val retained = geometry()
        val width = size.width.toInt() + 2 * PADDING
        val height = size.height.toInt() + 2 * PADDING
        fun render(geometry: GlassRimGeometry, strength: Float) = renderHardware(width, height) { canvas ->
            drawBackground(canvas, width, height)
            canvas.translate(PADDING.toFloat(), PADDING.toFloat())
            canvas.clipPath(geometry.body.asAndroidPath())
            geometry.drawLights(canvas, strength)
        }
        // Repeat each value to exercise the no-change fast path as well as
        // fading out completely and restoring the original full-strength paint.
        for (strength in floatArrayOf(1f, .5f, .1f, 0f, 1f)) {
            repeat(2) { repetition ->
                val reference = render(geometry(), strength)
                val candidate = render(retained, strength)
                try {
                    val count = width * height
                    val expected = IntArray(count)
                    val actual = IntArray(count)
                    reference.getPixels(expected, 0, width, 0, 0, width, height)
                    candidate.getPixels(actual, 0, width, 0, 0, width, height)
                    val mismatch = expected.indices.firstOrNull { expected[it] != actual[it] }
                    assertTrue(
                        "Retained paint changed at strength=$strength repetition=$repetition pixel=$mismatch",
                        mismatch == null,
                    )
                } finally {
                    reference.recycle()
                    candidate.recycle()
                }
            }
        }
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val colors = intArrayOf(
            Color.BLACK, Color.rgb(245, 230, 210), Color.rgb(180, 8, 24),
            Color.rgb(8, 140, 210), Color.rgb(12, 170, 24), Color.WHITE,
        )
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun renderHardware(width: Int, height: Int, drawBlock: (Canvas) -> Unit): Bitmap {
        // This is the same public Picture-based hardware snapshot mechanism
        // used by Compose's GraphicsLayer.toImageBitmap on Android 9+.
        val picture = object : Picture() {
            override fun getWidth() = width
            override fun getHeight() = height
            override fun requiresHardwareAcceleration() = true
            override fun beginRecording(width: Int, height: Int) = Canvas()
            override fun endRecording() = Unit
            override fun draw(canvas: Canvas) {
                check(canvas.isHardwareAccelerated) { "Visual comparisons require the actual hardware rendering path" }
                drawBlock(canvas)
            }
        }
        val hardware = Bitmap.createBitmap(picture)
        return try {
            checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false))
        } finally {
            hardware.recycle()
        }
    }

    private companion object {
        const val PADDING = 8
    }
}
