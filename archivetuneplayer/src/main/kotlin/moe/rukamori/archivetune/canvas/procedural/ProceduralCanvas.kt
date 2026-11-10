/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from Sekai Tune (github.com/rgsekai/sekai-tune) canvas module — GPLv3.
 * Kawarp AGSL fluid UV-warp shader, Square Tunnel mirrored-corridor shader
 * (technique provenance: Inigo Quilez MIT 2013 ShaderToy "Square Tunnel" /
 * Ms2SWW, adapted for album art by Screenbox "Music Tunnel" — Dani John /
 * rocksdanister MIT 2023), and the shared procedural entry points with a
 * Ken Burns fallback for pre-AGSL devices.
 */

package moe.rukamori.archivetune.canvas.procedural

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.CanvasAudioReactiveKey
import moe.rukamori.archivetune.constants.ProceduralArtworkEnabledKey
import moe.rukamori.archivetune.constants.ProceduralArtworkStyleKey
import moe.rukamori.archivetune.constants.ProceduralCanvasStyle
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

const val KAWARP_AUDIO_REACTIVE_INTENSITY: Float = 1.80f

const val KAWARP_AGSL_SHADER: String = """
    uniform shader image;
    uniform float2 resolution;
    uniform float time;
    uniform float bassEnergy;

    half4 main(float2 fragCoord) {
        // Isotropic coordinates normalized by min(width, height).
        // This ensures identical wave frequency and physical pixel displacement
        // across any aspect ratio (1:1, 9:16 portrait, 16:9 landscape) without distortion.
        float minDim = min(resolution.x, resolution.y);
        float2 normCoord = fragCoord / minDim;

        float t = time * 0.4;
        float audioBoost = 1.0 + bassEnergy * 1.80;

        // Multi-frequency harmonic wave displacement in isotropic space modulated by audioBoost
        float warpX = (sin(normCoord.y * 6.2831853 + t * 1.2) * 0.015
                    + sin(normCoord.y * 12.5663706 - t * 0.9) * 0.007
                    + cos(normCoord.x * 4.7123889 + t * 0.7) * 0.005) * audioBoost;

        float warpY = (cos(normCoord.x * 6.2831853 + t * 1.1) * 0.015
                    + cos(normCoord.x * 12.5663706 - t * 0.8) * 0.007
                    + sin(normCoord.y * 4.7123889 - t * 0.6) * 0.005) * audioBoost;

        // Convert displacement to physical layer pixel space
        float2 dispPx = float2(warpX, warpY) * minDim;

        // Center-anchored breathing zoom around the actual visual center (resolution * 0.5)
        float2 center = resolution * 0.5;
        float zoom = 1.03 + sin(t * 0.5) * 0.015;
        float2 sampleCoord = center + ((fragCoord - center) / zoom) + dispPx;

        // Clamp sample coordinate safely to [0.0, resolution] bounds
        sampleCoord = clamp(sampleCoord, float2(0.0), resolution);

        return image.eval(sampleCoord);
    }
"""

const val TUNNEL_AUDIO_REACTIVE_INTENSITY: Float = 1.40f

const val SQUARE_TUNNEL_AGSL_SHADER: String = """
    uniform shader image;
    uniform float2 resolution;
    uniform float time;
    uniform float3 centerColor;
    uniform float bassEnergy;

    half4 main(float2 fragCoord) {
        // Isotropic coordinates centered at (0, 0), normalized by min(width, height)
        float minDim = min(resolution.x, resolution.y);
        float2 p = (fragCoord - resolution * 0.5) / minDim;

        // Polar angle in [-PI, PI]
        float theta = atan(p.y, p.x);

        // 8th-order Minkowski p-norm produces flat square tunnel walls with soft corners
        float r = pow(pow(abs(p.x), 8.0) + pow(abs(p.y), 8.0), 0.125);

        // Depth UV motion (continuous forward flight loop modulated subtly by bassEnergy)
        float speed = 0.4 * (1.0 + bassEnergy * 1.40);
        float u = (0.3 / max(r, 0.0001)) + (0.2 * time * speed);

        // Angle UV mapped across full circular revolution
        float v = 0.5 + (theta / 3.14159265359);

        // GL_MIRRORED_REPEAT emulation
        float2 uv = float2(u, v);
        float2 mirroredCoord = 1.0 - abs(fract(uv * 0.5) * 2.0 - 1.0);

        // Sample album art bitmap in layer pixel coordinates
        half4 texColor = image.eval(mirroredCoord * resolution);

        // Radial center vignette fade towards album art dominant/muted color
        float vignette = smoothstep(0.04, 0.40, r);
        half3 finalRgb = mix(half3(centerColor), texColor.rgb, half(vignette));

        return half4(finalRgb, 1.0);
    }
"""

fun isProceduralShaderSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return false
    }
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice != true
}

fun isProceduralShaderSupported(sdkInt: Int, isLowRamDevice: Boolean): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU && !isLowRamDevice
}

@Composable
private fun rememberShaderTime(isPlaying: Boolean): Float {
    val time by produceState(initialValue = 0f, key1 = isPlaying) {
        if (!isPlaying) return@produceState
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    val deltaSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    value += deltaSeconds
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }
    return time
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ProceduralShaderCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
) {
    val time = rememberShaderTime(isPlaying)

    val bassEnergy by rememberBassEnergy(
        audioSessionId = audioSessionId,
        isPlaying = isPlaying,
        enabled = audioReactive,
    )

    val runtimeShader = remember { RuntimeShader(KAWARP_AGSL_SHADER) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val currentWidth = this.size.width
                val currentHeight = this.size.height
                if (currentWidth > 0f && currentHeight > 0f) {
                    runtimeShader.setFloatUniform("resolution", currentWidth, currentHeight)
                    runtimeShader.setFloatUniform("time", time)
                    runtimeShader.setFloatUniform("bassEnergy", bassEnergy)
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(runtimeShader, "image")
                        .asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun SquareTunnelShaderCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
) {
    val time = rememberShaderTime(isPlaying)

    val centerColor = remember(bitmap) {
        extractTunnelCenterColor(bitmap)
    }

    val bassEnergy by rememberBassEnergy(
        audioSessionId = audioSessionId,
        isPlaying = isPlaying,
        enabled = audioReactive,
    )

    val runtimeShader = remember { RuntimeShader(SQUARE_TUNNEL_AGSL_SHADER) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val currentWidth = this.size.width
                val currentHeight = this.size.height
                if (currentWidth > 0f && currentHeight > 0f) {
                    runtimeShader.setFloatUniform("resolution", currentWidth, currentHeight)
                    runtimeShader.setFloatUniform("time", time)
                    runtimeShader.setFloatUniform("centerColor", centerColor[0], centerColor[1], centerColor[2])
                    runtimeShader.setFloatUniform("bassEnergy", bassEnergy)
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(runtimeShader, "image")
                        .asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

fun extractTunnelCenterColor(bitmap: Bitmap): FloatArray {
    return try {
        val palette = androidx.palette.graphics.Palette.from(bitmap)
            .maximumColorCount(16)
            .resizeBitmapArea(4000)
            .generate()

        val swatch = palette.darkMutedSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.vibrantSwatch

        if (swatch != null) {
            val red = android.graphics.Color.red(swatch.rgb) / 255f
            val green = android.graphics.Color.green(swatch.rgb) / 255f
            val blue = android.graphics.Color.blue(swatch.rgb) / 255f
            floatArrayOf(red * 0.6f, green * 0.6f, blue * 0.6f)
        } else {
            floatArrayOf(0.08f, 0.08f, 0.08f)
        }
    } catch (_: Exception) {
        floatArrayOf(0.08f, 0.08f, 0.08f)
    }
}

@Composable
fun ProceduralCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
    style: ProceduralCanvasStyle = ProceduralCanvasStyle.KAWARP,
) {
    val context = LocalContext.current
    val supported = remember(context) { isProceduralShaderSupported(context) }

    if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when (style) {
            ProceduralCanvasStyle.SQUARE_TUNNEL ->
                SquareTunnelShaderCanvas(
                    bitmap = bitmap,
                    modifier = modifier,
                    isPlaying = isPlaying,
                    audioSessionId = audioSessionId,
                    audioReactive = audioReactive,
                )

            ProceduralCanvasStyle.KAWARP ->
                ProceduralShaderCanvas(
                    bitmap = bitmap,
                    modifier = modifier,
                    isPlaying = isPlaying,
                    audioSessionId = audioSessionId,
                    audioReactive = audioReactive,
                )
        }
    } else {
        KenBurnsCanvas(
            bitmap = bitmap,
            modifier = modifier,
            isPlaying = isPlaying,
        )
    }
}

@Composable
fun ProceduralArtworkFallback(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled by rememberPreference(ProceduralArtworkEnabledKey, defaultValue = false)
    if (!enabled || artworkUrl.isNullOrBlank()) return

    val style by rememberEnumPreference(ProceduralArtworkStyleKey, defaultValue = ProceduralCanvasStyle.KAWARP)
    val audioReactive by rememberPreference(CanvasAudioReactiveKey, defaultValue = false)

    val bitmap by produceState<Bitmap?>(null, artworkUrl) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(768, 768)
                            .allowHardware(false)
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) result.image.toBitmap() else null
                } catch (_: Exception) {
                    null
                }
            }
    }

    val loaded = bitmap != null
    val reveal by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "proceduralArtworkReveal",
    )
    if (!loaded || reveal <= 0.01f) return

    Box(modifier = modifier.graphicsLayer { alpha = reveal }) {
        ProceduralCanvas(
            bitmap = bitmap!!,
            modifier = Modifier.fillMaxSize(),
            isPlaying = isPlaying,
            audioReactive = audioReactive,
            style = style,
        )
    }
}
