/*
 * The refraction below is the backdrop library's rounded-rectangle lens with
 * dispersion (Kyant0/backdrop, Apache-2.0 — see the vendored Shaders.kt),
 * copied unchanged; what follows it is BitChord's glass fill and rim.
 */
package com.music.bitchord.ui.components

import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import com.music.bitchord.ui.components.backdrop.BackdropEffectScope
import com.music.bitchord.ui.components.backdrop.asAndroidRuntimeShader
import com.music.bitchord.ui.components.backdrop.cachedEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.music.bitchord.ui.components.backdrop.internal.chain

/**
 * The glass in one pass: the lens, the glass's own fill, the glow and the rim,
 * and the shape's anti-aliased edge, all in the shader that already refracts.
 *
 * Drawn the usual way, after the lens came the fill as a path, the four lights
 * as blurred draws in colour dodge and plus — each dodge a read of whatever was
 * already drawn there — and two clips to the shape's path, each a mask every
 * draw inside went through. Measured, those were most of what a glass surface
 * still cost. Here they are arithmetic on the colour the lens already has in
 * hand, read against [GlassRimMasks.packed]: the very rim, glow and edge the
 * path drawing rasterises, baked once and stretched along the pill exactly as
 * its outline is. The surface then needs no clip to its shape at all.
 *
 * At full resolution, unlike the third the lens alone ran at: a third of a 0.8dp
 * rim is not a rim. The lens measured as next to nothing at a third, and its
 * input is the group's already-blurred strip, so the full pass is still cheap.
 *
 * The outline, outside the shape, stays where it was — [glassRim] with no lights.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun BackdropEffectScope.glassSurface(
    refractionHeight: Float,
    refractionAmount: Float,
    masks: GlassRimMasks,
    light: Boolean,
) {
    val shape = shape as? CornerBasedShape ?: return
    val size = size
    if (size.width <= 0f || size.height <= 0f) return
    val radii = cornerRadii(shape, size, layoutDirection)
    val input = renderEffect
    val padding = padding
    val layout = masks.layoutFor(size)
    val key = GlassSurfaceKey(
        input, size.width, size.height, padding, radii[0], radii[1], radii[2], radii[3],
        refractionHeight, refractionAmount, masks, layout, light,
    )
    renderEffect = cachedEffect(key) {
        // The surface's one compiled shader, its uniforms set anew: constructed
        // from source instead, every size a resizing bar went through compiled
        // the whole program again on this thread.
        val shader = obtainRuntimeShader("GlassSurface", GlassSurfaceShader).asAndroidRuntimeShader().apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("offset", -padding, -padding)
            setFloatUniform("cornerRadii", radii)
            setFloatUniform("refractionHeight", refractionHeight)
            setFloatUniform("refractionAmount", -refractionAmount)
            setFloatUniform("depthEffect", 1f)
            setFloatUniform("chromaticAberration", 1f)
            setFloatUniform("maskSlice", layout.cap, layout.capOnSurface, layout.bakedWidth, layout.margin)
            setFloatUniform("maskScale", layout.scale)
            setFloatUniform("maskSquash", if (layout.squash) 1f else 0f)
            setFloatUniform("gains", GLOW_GAIN, GLOW_LIGHT, RIM_GAIN, RIM_LIGHT)
            setFloatUniform("fill", if (light) 1f else 0f, DARK_LIFT, LIGHT_VEIL_ALPHA, 0f)
            setFloatUniform("veil", LIGHT_VEIL, LIGHT_VEIL, LIGHT_VEIL)
            setInputShader(
                "masks",
                BitmapShader(masks.packed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
        }
        input.chain(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect())
    }
}

/**
 * Whether glass surfaces under a strip draw their rim, fill and edge in the
 * lens shader — [glassSurface] — rather than as separate draws. A frame
 * recording can switch it either way, to price one against the other.
 */
internal val shaderGlassForDiagnostics = mutableStateOf(SHADER_GLASS_DEFAULT)

/**
 * On: judged against the drawn rim on a phone and kept (2026-10-28) — the
 * same picture, a frame recording 1.6ms faster at the median and with half
 * the frames over budget. The drawn rim stays for a recording to compare.
 */
internal const val SHADER_GLASS_DEFAULT = true

private data class GlassSurfaceKey(
    val input: androidx.compose.ui.graphics.RenderEffect?,
    val width: Float,
    val height: Float,
    val padding: Float,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val refractionHeight: Float,
    val refractionAmount: Float,
    val masks: GlassRimMasks,
    val layout: GlassRimMasks.Layout,
    val light: Boolean,
)

/** The lens's corner radii, as the backdrop library's lens reads them off a [CornerBasedShape]. */
private fun BackdropEffectScope.cornerRadii(shape: CornerBasedShape, size: Size, direction: LayoutDirection): FloatArray {
    val maxRadius = size.minDimension / 2f
    val ltr = direction == LayoutDirection.Ltr
    val topLeft = if (ltr) shape.topStart.toPx(size, this) else shape.topEnd.toPx(size, this)
    val topRight = if (ltr) shape.topEnd.toPx(size, this) else shape.topStart.toPx(size, this)
    val bottomRight = if (ltr) shape.bottomEnd.toPx(size, this) else shape.bottomStart.toPx(size, this)
    val bottomLeft = if (ltr) shape.bottomStart.toPx(size, this) else shape.bottomEnd.toPx(size, this)
    return floatArrayOf(
        topLeft.coerceAtMost(maxRadius),
        topRight.coerceAtMost(maxRadius),
        bottomRight.coerceAtMost(maxRadius),
        bottomLeft.coerceAtMost(maxRadius),
    )
}

// The drawn glass's values, restated for the shader — see LiquidGlass.kt's
// fill and GlassRim.kt's lights; they are the same numbers.
private const val DARK_LIFT = 0.08f
private const val LIGHT_VEIL = 0xFA / 255f
private const val LIGHT_VEIL_ALPHA = 0.4f
private const val GLOW_GAIN = 1.6f
private const val GLOW_LIGHT = 0.07f
private const val RIM_GAIN = 2.4f
private const val RIM_LIGHT = 0.07f

private const val GlassSurfaceShader = """
uniform shader content;
uniform shader masks;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

// x: cap in the bake, y: that cap on this surface, z: the baked shape's width, w: the bake's margin.
uniform float4 maskSlice;
// This surface's height over the baked one.
uniform float maskScale;
// 1 where the surface is narrower than its two caps and they are squeezed to fit.
uniform float maskSquash;
// x: glow gain, y: glow light, z: rim gain, w: rim light.
uniform float4 gains;
// x: 1 for the light theme's veil, y: the dark theme's lift, z: the veil's alpha.
uniform float4 fill;
uniform float3 veil;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 refracted(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}

// Where on the bake this point of the surface reads: the end caps as baked,
// scaled with the height, and the straight run between stretched.
float2 bakeCoord(float2 p) {
    float cap = maskSlice.x;
    float capOnSurface = maskSlice.y;
    float bakedWidth = maskSlice.z;
    float margin = maskSlice.w;
    float k = maskScale;
    float w = size.x;
    float sx;
    if (maskSquash > 0.5) {
        float halfWidth = w * 0.5;
        sx = p.x < halfWidth ? p.x / halfWidth * cap : bakedWidth - (w - p.x) / halfWidth * cap;
    } else if (p.x < capOnSurface) {
        sx = p.x / k;
    } else if (p.x > w - capOnSurface) {
        sx = bakedWidth - (w - p.x) / k;
    } else {
        sx = cap + (p.x - capOnSurface) * ((bakedWidth - 2.0 * cap) / max(w - 2.0 * capOnSurface, 1.0));
    }
    return float2(sx, p.y / k) + float2(margin);
}

half4 main(float2 coord) {
    float3 c = float3(refracted(coord).rgb);
    half4 m = masks.eval(bakeCoord(coord + offset));
    float rim = float(m.r);
    float glow = float(m.g);
    float body = float(m.b);

    // The glass's fill: the dark theme's faint added white, or the light
    // theme's milky veil.
    if (fill.x > 0.5) {
        c = mix(c, veil, fill.z);
    } else {
        c = min(c + fill.y, 1.0);
    }
    // The glow, then the rim: each a colour dodge — a gain on every channel —
    // and then a little added white, in that order, as they are drawn.
    c = mix(c, min(c * gains.x, 1.0), glow);
    c = min(c + gains.y * glow, 1.0);
    c = mix(c, min(c * gains.z, 1.0), rim);
    c = min(c + gains.w * rim, 1.0);
    return half4(half3(c * body), half(body));
}
"""
