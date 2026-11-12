package com.music.bitchord.ui.tv.display

import android.os.Build
import android.view.Display
import kotlin.math.abs

object TvDisplayCapabilitiesReader {

    private const val TOLERANCE_HZ = 0.6f

    fun readCapabilities(display: Display?): TvDisplayCapabilities {
        if (display == null) {
            return TvDisplayCapabilities()
        }

        val currentMode = display.mode
        val currentWidth = currentMode.physicalWidth
        val currentHeight = currentMode.physicalHeight
        val currentRefreshRate = currentMode.refreshRate

        val rawModes = display.supportedModes ?: emptyArray()
        val supportedModes = rawModes.map { mode ->
            val altRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mode.alternativeRefreshRates
            } else {
                floatArrayOf()
            }
            TvDisplayMode(
                id = mode.modeId,
                physicalWidth = mode.physicalWidth,
                physicalHeight = mode.physicalHeight,
                refreshRate = mode.refreshRate,
                alternativeRefreshRates = altRates,
            )
        }

        // Filter for candidates matching identical physical resolution (do not downscale 4K to 1080p if avoidable)
        val sameResModes = supportedModes.filter {
            it.physicalWidth == currentWidth && it.physicalHeight == currentHeight
        }

        // Find closest 60 Hz candidate (59.94 or 60.0 Hz)
        val mode60 = sameResModes
            .filter { abs(it.refreshRate - 60.0f) <= TOLERANCE_HZ || abs(it.refreshRate - 59.94f) <= TOLERANCE_HZ }
            .minByOrNull { abs(it.refreshRate - 60.0f) }
            ?: supportedModes
                .filter { abs(it.refreshRate - 60.0f) <= TOLERANCE_HZ || abs(it.refreshRate - 59.94f) <= TOLERANCE_HZ }
                .maxByOrNull { it.physicalWidth * it.physicalHeight }

        // Find closest 120 Hz candidate (119.88 or 120.0 Hz or >= 119Hz)
        // 1. Search in matching resolution
        // 2. Search in all supported modes (prioritizing highest resolution)
        // 3. Search in modes that report alternativeRefreshRates >= 119Hz
        val mode120 = sameResModes
            .filter { it.refreshRate >= 119.0f || abs(it.refreshRate - 120.0f) <= TOLERANCE_HZ || abs(it.refreshRate - 119.88f) <= TOLERANCE_HZ }
            .minByOrNull { abs(it.refreshRate - 120.0f) }
            ?: supportedModes
                .filter { it.refreshRate >= 119.0f }
                .maxByOrNull { it.physicalWidth * it.physicalHeight }
            ?: supportedModes.firstOrNull { mode ->
                mode.alternativeRefreshRates.any { it >= 119.0f }
            }

        val canSwitchSeamlessly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentMode.alternativeRefreshRates.any { it >= 119.0f } ||
                (mode120 != null && currentMode.physicalWidth == mode120.physicalWidth && currentMode.physicalHeight == mode120.physicalHeight)
        } else {
            false
        }

        val hasAdaptiveSupport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ supports adaptive refresh rate queries
            true
        } else {
            null
        }

        return TvDisplayCapabilities(
            displayId = display.displayId,
            currentModeId = currentMode.modeId,
            currentPhysicalWidth = currentWidth,
            currentPhysicalHeight = currentHeight,
            actualRefreshRateHz = currentRefreshRate,
            supportedModes = supportedModes,
            compatible60ModeId = mode60?.id,
            compatible120ModeId = mode120?.id,
            canSwitchTo120Seamlessly = canSwitchSeamlessly,
            hasAdaptiveRefreshRateSupport = hasAdaptiveSupport,
        )
    }
}
