package com.music.bitchord.ui

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.LiquidGlass
import com.example.liquidglass.LiquidGlassView
import com.music.bitchord.ui.theme.LiquidGlassPreferences

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 28f,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val enabled = LiquidGlassPreferences.enabled(context).value
    val strength = LiquidGlassPreferences.strength(context).value

    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val radiusPx = remember(cornerRadiusDp, context) {
        cornerRadiusDp * context.resources.displayMetrics.density
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                clipChildren = false
                val glass = LiquidGlassView(ctx).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    cornerRadius = radiusPx
                    LiquidGlass.configure(this, true, strength)
                }
                addView(
                    glass,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
                tag = glass
            }
        },
        update = { host ->
            (host.tag as? LiquidGlassView)?.let { glass ->
                glass.cornerRadius = radiusPx
                LiquidGlass.configure(glass, enabled, strength)
            }
        },
    )

    Box(modifier = modifier, content = content)
}
