package com.music.bitchord.ui

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.LiquidGlassView
import com.example.liquidglass.LiquidGlass
import com.music.bitchord.ui.theme.LiquidGlassPreferences

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val enabled = LiquidGlassPreferences.enabled(context).value
    val strength = LiquidGlassPreferences.strength(context).value

    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LiquidGlassView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = false
                LiquidGlass.configure(this, true, strength)
            }
        },
        update = { view ->
            LiquidGlass.configure(view, enabled, strength)
        },
    )
    Box(modifier = modifier, content = content)
}
