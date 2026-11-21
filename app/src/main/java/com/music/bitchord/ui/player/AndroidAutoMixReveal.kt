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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.music.bitchord.playback.AutoMixArtworkTuning as Tuning
import kotlin.math.roundToInt
import kotlin.random.Random

// UI thread only. A driver/shader failure must not be retried on every transition.
private var revealFailed = false
private var cachedBlur: BlurSteps? = null

/** Keep the outgoing video on its fallback when the incoming still cannot use AGSL. */
internal fun stillRevealMaskAvailable(): Boolean = !revealFailed

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
    private var appliedWidth = 0f
    private var appliedHeight = 0f
    private var appliedProgress = -1f

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
                // Each uniform write discards Android's native shader instance.
                // This mask belongs to one layer and can reuse unchanged inputs.
                if (width != appliedWidth || height != appliedHeight) {
                    mask.setFloatUniform("bounds", width, height)
                    appliedWidth = width
                    appliedHeight = height
                }
                if (e != appliedProgress) {
                    mask.setFloatUniform("progress", e)
                    appliedProgress = e
                }
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
        // Adjacent cells must agree on their shared corners. On Adreno the
        // amplified sine hash produced discontinuities at those lattice lines.
        // Mix the small integer-valued cell coordinates in a bounded range.
        float n = mod(cell.x * 127.0 + cell.y * 197.0, 251.0);
        n = mod(n * (n + 73.0) + cell.x * 97.0 + cell.y * 53.0, 251.0);
        n = mod(n * (n + 37.0) + cell.x * 31.0 + cell.y * 109.0, 251.0);
        return n / 251.0;
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
        $ORGANIC_MASK_FIELD
        half4 main(float2 xy) {
            return content.eval(xy) * (bottomFade.eval(xy).a * half(revealCoverage(xy)));
        }
    """.trimIndent())
    private var patternGeneration: Long? = null
    private val opaque = LinearGradient(0f, 0f, 0f, 1f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP)
    private var appliedBottomFade: Shader? = null
    private var appliedWidth = 0f
    private var appliedHeight = 0f

    fun effect(width: Float, height: Float, progress: Float, generation: Long, bottomFade: Shader?): PlatformRenderEffect {
        if (patternGeneration != generation) {
            shader.setPattern(generation)
            patternGeneration = generation
        }
        val gradient = bottomFade ?: opaque
        if (appliedBottomFade !== gradient) {
            shader.setInputShader("bottomFade", gradient)
            appliedBottomFade = gradient
        }
        if (appliedWidth != width || appliedHeight != height) {
            shader.setFloatUniform("bounds", width, height)
            appliedWidth = width
            appliedHeight = height
        }
        shader.setFloatUniform("progress", progress)
        return PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

/** Retires the outgoing video through the same field as the incoming still. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OutgoingStillRevealMask {
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform shader bottomFade;
        uniform shader stillFade;
        $ORGANIC_MASK_FIELD
        half4 main(float2 xy) {
            float feather = stillFade.eval(xy).a;
            float remaining = 1.0;
            // Source-over already removes the outgoing video behind the opaque still.
            // Correct only the excess elsewhere: (1 - reveal * feather) * remaining = 1 - reveal.
            if (feather < 1.0) {
                float reveal = revealCoverage(xy);
                remaining = (1.0 - reveal) / (1.0 - reveal * feather);
            }
            return content.eval(xy) * half(bottomFade.eval(xy).a * remaining);
        }
    """.trimIndent())
    private val opaque = LinearGradient(0f, 0f, 0f, 1f, Color.BLACK, Color.BLACK, Shader.TileMode.CLAMP)
    private var appliedGeneration: Long? = null
    private var appliedBottom: Shader? = null
    private var appliedStill: Shader? = null

    fun effect(
        width: Float,
        height: Float,
        progress: Float,
        generation: Long,
        bottom: Shader?,
        still: Shader,
    ): PlatformRenderEffect {
        if (appliedGeneration != generation) {
            shader.setPattern(generation)
            appliedGeneration = generation
        }
        val fade = bottom ?: opaque
        if (appliedBottom !== fade) {
            shader.setInputShader("bottomFade", fade)
            appliedBottom = fade
        }
        if (appliedStill !== still) {
            shader.setInputShader("stillFade", still)
            shader.setFloatUniform("bounds", width, height)
            appliedStill = still
        }
        shader.setFloatUniform("progress", progress)
        return PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
