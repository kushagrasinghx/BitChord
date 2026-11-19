package com.music.bitchord.ui.player

import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.LinearGradient
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.playback.AutoMixArtworkTuning as Tuning
import kotlin.math.roundToInt
import kotlin.random.Random

// UI thread only. A driver/shader failure must not be retried on every transition.
private var revealFailed = false
private var cachedBlur: BlurSteps? = null

@Composable
internal fun androidAutoMixIncomingReveal(
    eased: () -> Float,
    reducedMotion: () -> Boolean,
    generation: Long,
    blurEnabled: Boolean,
): Modifier {
    val plain = Modifier.graphicsLayer { alpha = eased() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || revealFailed || reducedMotion()) return plain

    val blur = if (blurEnabled) {
        val density = LocalDensity.current.density
        remember(density) {
            cachedBlur?.takeIf { it.density == density } ?: BlurSteps(density).also { cachedBlur = it }
        }
    } else null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        if (blur == null) return plain
        return Modifier.graphicsLayer {
            val e = eased()
            alpha = e
            renderEffect = if (reducedMotion() || revealFailed) null else blur.at(e)
        }
    }
    val mask = remember(generation) { createMask(generation) } ?: return plain
    val reveal = mask.modifier(eased, reducedMotion)
    if (blur == null) return reveal
    return reveal.graphicsLayer {
        renderEffect = if (reducedMotion() || revealFailed) null else blur.at(eased())
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class BlurSteps(val density: Float) {
    // Immutable effects are safe to share. Only instantiate the radii actually drawn.
    private val effects = arrayOfNulls<RenderEffect>(Tuning.BLUR_RADIUS_STEPS + 1)

    fun at(eased: Float): RenderEffect? {
        if (eased <= 0f || eased >= Tuning.BLUR_END_PROGRESS) return null
        val remaining = 1f - eased / Tuning.BLUR_END_PROGRESS
        val step = (remaining * remaining * Tuning.BLUR_RADIUS_STEPS).roundToInt()
        if (step == 0) return null
        return effects[step] ?: runCatching {
            val radius = Tuning.BLUR_START_DP * density * step / Tuning.BLUR_RADIUS_STEPS
            PlatformRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect().also { effects[step] = it }
        }.onFailure { revealFailed = true }.getOrNull()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createMask(generation: Long): IncomingMask? = runCatching { IncomingMask(generation) }
    .onFailure { revealFailed = true }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class IncomingMask(generation: Long) {
    private val mask = RuntimeShader(MOTTLED_MASK).apply { setPattern(generation) }
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

private val ORGANIC_MASK_FIELD = """
    uniform float2 bounds;
    uniform float progress;
    uniform float2 seedOffset;

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

    float revealCoverage(float2 xy) {
        // Keep the pattern broad over the whole artwork, even on tall or wide covers.
        // Translate the smooth field once per generation instead of warping its lattice.
        float2 point = xy / bounds * ${Tuning.NOISE_SCALE} + seedOffset;
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
        float field = clamp((noise / total - ${Tuning.NOISE_LOW}) / (${Tuning.NOISE_HIGH} - ${Tuning.NOISE_LOW}), 0.0, 1.0);
        return smoothstep(threshold - softness, threshold + softness, field);
    }
""".trimIndent()

private val MOTTLED_MASK = """
    $ORGANIC_MASK_FIELD
    half4 main(float2 xy) {
        return half4(0.0, 0.0, 0.0, revealCoverage(xy));
    }
""".trimIndent()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setPattern(generation: Long) {
    val random = Random(generation)
    setFloatUniform("seedOffset", random.nextFloat() * Tuning.NOISE_SEED_SPAN, random.nextFloat() * Tuning.NOISE_SEED_SPAN)
}

/** The TextureView's RenderNode supplies the live texture as a GPU input. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LiveAutoMixMask {
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform shader bottomFade;
        uniform float2 pictureOrigin;
        uniform float4 internalEdges;
        $ORGANIC_MASK_FIELD
        half4 main(float2 xy) {
            float2 p = xy - pictureOrigin;
            // Only letterbox edges inside this node are feathered. Outer clipping
            // and the normal bottom gradient belong to the artwork presentation.
            float width = max(1.0, min(bounds.x, bounds.y) * 0.065);
            float4 distance = float4(p, bounds - p);
            float4 feather = mix(float4(1.0), smoothstep(0.0, width, distance), internalEdges);
            float edge = feather.x * feather.y * feather.z * feather.w;
            // Restore the ordinary clip continuously before removing this effect.
            float strength = 1.0 - smoothstep(0.65, 1.0, progress);
            half coverage = half(revealCoverage(p) * mix(1.0, edge, strength));
            return content.eval(xy) * (bottomFade.eval(xy).a * coverage);
        }
    """.trimIndent())
    private var patternGeneration: Long? = null
    private val opaque = LinearGradient(0f, 0f, 0f, 1f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP)
    private var appliedBottomFade: Shader? = null
    private var appliedViewport = IntSize.Zero
    private var appliedPicture = Rect.Zero

    fun effect(viewport: IntSize, picture: Rect, progress: Float, generation: Long, bottomFade: Shader?): PlatformRenderEffect {
        if (patternGeneration != generation) {
            shader.setPattern(generation)
            patternGeneration = generation
        }
        val gradient = bottomFade ?: opaque
        if (appliedBottomFade !== gradient) {
            shader.setInputShader("bottomFade", gradient)
            appliedBottomFade = gradient
        }
        if (appliedViewport != viewport || appliedPicture != picture) {
            shader.setFloatUniform("bounds", picture.width, picture.height)
            shader.setFloatUniform("pictureOrigin", picture.left, picture.top)
            shader.setFloatUniform(
                "internalEdges",
                if (picture.left >= 1f) 1f else 0f,
                if (picture.top >= 1f) 1f else 0f,
                if (viewport.width - picture.right >= 1f) 1f else 0f,
                // For a top-aligned hero keep its explicit bottom gradient; its
                // translucent ramp already ends at the contained picture edge.
                if (viewport.height - picture.bottom >= 1f && picture.top >= 1f) 1f else 0f,
            )
            appliedViewport = viewport
            appliedPicture = picture
        }
        shader.setFloatUniform("progress", progress)
        return PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
