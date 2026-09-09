package com.example.liquidglass

import android.content.Context
import android.graphics.Color
import android.view.View

object LiquidGlass {
    fun configure(
        view: LiquidGlassView,
        enabled: Boolean,
        strength: Float,
    ) {
        val s = strength.coerceIn(0f, 1f)
        view.visibility = View.VISIBLE
        view.alpha = 1f
        if (!enabled || s <= 0f) {
            view.enableBackdropBlur = false
            view.enableChromaticAberration = false
            view.enableChromaticDispersion = false
            view.enableEdgeHighlight = false
            view.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        view.enableBackdropBlur = true
        view.enableChromaticAberration = s > 0.08f
        view.enableChromaticDispersion = false
        view.enableEdgeHighlight = true
        view.blurAmount = 0.04f + (0.105f * s)
        view.saturation = 115f + (45f * s)
        view.displacementScale = 30f + (40f * s)
        view.aberrationIntensity = 0.65f + (1.35f * s)
        view.edgeHighlightOpacity = 45f + (55f * s)
        view.edgeHighlightBorderWidth = 1.0f + (0.75f * s)
        view.globalDownsampleFactor = 0.5f + (0.5f * s)
    }
}

fun Context.createLiquidGlassView(): LiquidGlassView = LiquidGlassView(this)
