/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PlayerSliderColors {
    @Composable
    fun getSliderColors(
        activeColor: Color,
        inactiveAlpha: Float = 0.25f,
    ): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = activeColor,
            inactiveTrackColor = activeColor.copy(alpha = inactiveAlpha),
        )

    @Composable
    fun standardSliderColors(buttonColor: Color): SliderColors =
        getSliderColors(
            activeColor = buttonColor,
            inactiveAlpha = Config.INACTIVE_TRACK_ALPHA,
        )

    @Composable
    fun wavySliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor,
            activeTickColor = buttonColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = buttonColor.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
            inactiveTickColor = buttonColor.copy(alpha = Config.INACTIVE_TICK_ALPHA),
        )

    @Composable
    fun thickSliderColors(buttonColor: Color): SliderColors =
        getSliderColors(
            activeColor = buttonColor,
            inactiveAlpha = Config.THICK_INACTIVE_TRACK_ALPHA,
        )

    @Composable
    fun circularSliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor,
            activeTickColor = buttonColor,
            thumbColor = buttonColor,
            inactiveTrackColor = buttonColor.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
        )

    @Composable
    fun simpleSliderColors(buttonColor: Color): SliderColors =
        SliderDefaults.colors(
            activeTrackColor = buttonColor.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            activeTickColor = buttonColor.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            thumbColor = Color.Transparent,
            inactiveTrackColor = buttonColor.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA),
            inactiveTickColor = buttonColor.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA),
        )

    object Config {
        const val INACTIVE_TRACK_ALPHA = 0.22f

        const val THICK_INACTIVE_TRACK_ALPHA = 0.28f

        const val SIMPLE_ACTIVE_TRACK_ALPHA = 0.85f

        const val SIMPLE_INACTIVE_TRACK_ALPHA = 0.15f

        const val INACTIVE_TICK_ALPHA = 0.25f

        val DEFAULT_ACTIVE_COLOR = Color(0xFF1976D2)

        val DEFAULT_INACTIVE_COLOR = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA)
    }
}
