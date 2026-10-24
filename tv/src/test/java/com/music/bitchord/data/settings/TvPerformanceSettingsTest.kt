package com.music.bitchord.data.settings

import com.music.bitchord.ui.tv.performance.TvPerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvPerformanceSettingsTest {
    @Test fun existingInstallationKeepsArtworkAndBlurPreferences() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putBoolean("animated_canvas", true).putBoolean("reduce_dynamic_blur", false).apply()
        val before = prefs.all.toMap()
        TvSettings.init(prefs)
        assertEquals(TvPerformanceMode.AUTO, TvSettings.tvPerformanceMode.value)
        assertEquals(before, prefs.all)
        assertFalse(prefs.contains("tv_performance_mode"))
    }

    @Test fun explicitChoiceSurvivesSettingsReload() {
        val prefs = FakeSharedPreferences()
        TvSettings.init(prefs)
        TvSettings.setTvPerformanceMode(TvPerformanceMode.STANDARD)
        assertEquals("STANDARD", prefs.getString("tv_performance_mode", null))
        TvSettings.init(FakeSharedPreferences())
        assertEquals(TvPerformanceMode.AUTO, TvSettings.tvPerformanceMode.value)
        TvSettings.init(prefs)
        assertEquals(TvPerformanceMode.STANDARD, TvSettings.tvPerformanceMode.value)
    }

    @Test fun unrecognizedSavedChoiceFallsBackWithoutDestroyingOtherSettings() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("tv_performance_mode", "FUTURE_MODE").putFloat("tv_ui_scale", 1.2f).apply()
        TvSettings.init(prefs)
        assertEquals(TvPerformanceMode.AUTO, TvSettings.tvPerformanceMode.value)
        assertEquals(1.2f, TvSettings.tvUiScale.value)
    }
}
