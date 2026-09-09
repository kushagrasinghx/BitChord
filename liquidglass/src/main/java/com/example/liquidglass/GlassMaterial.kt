/** Offline player glass material presets. */
package com.example.liquidglass

enum class GlassMaterial(
    val blurScale: Float,
    val dimAmount: Float,
    val adaptiveTint: Boolean,
    val baseTint: Int,
    val specBoost: Float,
) {
    REGULAR(1.0f, 0.0f, true, 0x24FFFFFF, 1.0f),
    CLEAR(0.35f, 0.16f, false, 0x14FFFFFF, 1.2f),
}
