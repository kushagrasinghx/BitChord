package com.music.bitchord.ui.player

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.music.bitchord.playback.AutoMixArtworkTuning as Tuning
import kotlin.math.roundToInt

// UI thread only. A driver/shader failure must not be retried on every transition.
private var revealFailed = false

@Composable
internal fun androidAutoMixIncomingReveal(eased: () -> Float, reducedMotion: () -> Boolean): Modifier {
    val plain = Modifier.graphicsLayer { alpha = eased() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || revealFailed || reducedMotion()) return plain

    val density = LocalDensity.current.density
    val blur = remember(density) { createBlurSteps(density) } ?: return plain
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return Modifier.graphicsLayer {
            val e = eased()
            alpha = e
            renderEffect = if (reducedMotion() || revealFailed) null else blur.at(e)
        }
    }
    val mask = remember { createMask() } ?: return plain
    return mask.modifier(eased, reducedMotion).graphicsLayer {
        renderEffect = if (reducedMotion() || revealFailed) null else blur.at(eased())
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun createBlurSteps(density: Float): Array<RenderEffect?>? = runCatching {
    // Cache native effects up front: changing the layer radius never allocates a new effect.
    Array<RenderEffect?>(Tuning.BLUR_RADIUS_STEPS + 1) { step ->
        if (step == 0) null else {
            val radius = Tuning.BLUR_START_DP * density * step / Tuning.BLUR_RADIUS_STEPS
            PlatformRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
        }
    }
}.onFailure { revealFailed = true }.getOrNull()

private fun Array<RenderEffect?>.at(eased: Float): RenderEffect? {
    if (eased <= 0f || eased >= Tuning.BLUR_END_PROGRESS) return null
    val remaining = 1f - eased / Tuning.BLUR_END_PROGRESS
    return this[(remaining * remaining * Tuning.BLUR_RADIUS_STEPS).roundToInt()]
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createMask(): IncomingMask? = runCatching { IncomingMask() }
    .onFailure { revealFailed = true }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class IncomingMask {
    private val mask = RuntimeShader(MOTTLED_MASK)
    private val paint = Paint().apply {
        shader = mask
        blendMode = BlendMode.DST_IN
    }

    fun modifier(eased: () -> Float, reducedMotion: () -> Boolean): Modifier = Modifier.drawWithContent {
        val e = eased()
        if (e <= 0f) return@drawWithContent
        if (e >= 1f) {
            drawContent()
            return@drawWithContent
        }
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@drawIntoCanvas
            val useMask = !revealFailed && !reducedMotion() && canvas.isHardwareAccelerated && runCatching {
                mask.setFloatUniform("bounds", width, height)
                mask.setFloatUniform("progress", e)
                // The sleeve/landscape container is square; the opened hero is tall.
                val hero = ((height / width - 1f) / (Tuning.HERO_MASK_FULL_ASPECT - 1f)).coerceIn(0f, 1f)
                mask.setFloatUniform("strength", Tuning.SLEEVE_MASK_STRENGTH +
                    (Tuning.HERO_MASK_STRENGTH - Tuning.SLEEVE_MASK_STRENGTH) * hero)
                true
            }.getOrElse {
                revealFailed = true
                false
            }
            val layer = if (useMask) canvas.saveLayer(0f, 0f, width, height, null)
            else canvas.saveLayerAlpha(0f, 0f, width, height, (e * 255f).roundToInt())
            try {
                drawContent()
                if (useMask) canvas.drawRect(0f, 0f, width, height, paint)
            } finally {
                canvas.restoreToCount(layer)
            }
        }
    }
}

private val MOTTLED_MASK = """
    uniform float2 bounds;
    uniform float progress;
    uniform float strength;

    float hashCell(float2 cell) {
        return fract(sin(dot(cell, float2(127.1, 311.7))) * 43758.5453);
    }

    float valueNoise(float2 point) {
        float2 cell = floor(point);
        float2 f = fract(point);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(hashCell(cell), hashCell(cell + float2(1.0, 0.0)), f.x),
                   mix(hashCell(cell + float2(0.0, 1.0)), hashCell(cell + float2(1.0)), f.x), f.y);
    }

    half4 main(float2 xy) {
        float2 uv = xy / bounds;
        float2 point = uv * ${Tuning.NOISE_SCALE} + float2(0.3, -0.7) * (progress * ${Tuning.NOISE_DRIFT});
        float noise = 0.0;
        float weight = 0.5;
        float total = 0.0;
        for (int i = 0; i < ${Tuning.NOISE_OCTAVES}; ++i) {
            noise += valueNoise(point) * weight;
            total += weight;
            point = point * 2.0 + float2(3.9, 5.1);
            weight *= 0.5;
        }
        float softness = ${Tuning.NOISE_SOFTNESS};
        float threshold = mix(1.0 + softness, -softness, progress);
        float mottled = smoothstep(threshold - softness, threshold + softness, noise / total);
        return half4(0.0, 0.0, 0.0, mix(progress, mottled, strength));
    }
""".trimIndent()
