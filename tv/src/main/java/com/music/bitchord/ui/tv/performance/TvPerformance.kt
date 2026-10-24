package com.music.bitchord.ui.tv.performance

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.music.bitchord.data.settings.TvSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Installed by TvStartup; shared phone and desktop settings are never overwritten. */
object TvPerformance {
    private val _resources = MutableStateFlow(TvDeviceResources())
    val resources = _resources.asStateFlow()

    fun init(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        _resources.value = TvDeviceResources(
            lowRam = manager.isLowRamDevice,
            totalMemoryBytes = memory.totalMem,
            memoryClassMb = manager.memoryClass,
        )
    }
}

private val LocalTvPerformanceProfile = staticCompositionLocalOf { TvPerformanceProfile.STANDARD }

/** Collect once at the root, rather than opening two collectors for every visible artwork. */
@Composable
fun TvPerformanceProvider(content: @Composable () -> Unit) {
    val mode by TvSettings.tvPerformanceMode.collectAsState()
    val resources by TvPerformance.resources.collectAsState()
    CompositionLocalProvider(
        LocalTvPerformanceProfile provides resolveTvPerformanceProfile(mode, resources),
        content = content,
    )
}

@Composable
fun rememberTvPerformanceProfile(): TvPerformanceProfile = LocalTvPerformanceProfile.current
