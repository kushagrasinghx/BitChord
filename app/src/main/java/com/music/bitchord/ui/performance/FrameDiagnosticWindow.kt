package com.music.bitchord.ui.performance

import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.bitchord.BuildConfig

@Composable
internal fun FrameDiagnosticWindow(window: Window?, view: View, name: String, resetRefresh: (() -> Unit)? = null) {
    if (!BuildConfig.PERFORMANCE_DIAGNOSTICS || window == null) return
    val currentReset = rememberUpdatedState(resetRefresh)
    DisposableEffect(window, view, name) {
        val unregister = FrameDiagnostics.registerWindow(window, view, name,
            if (resetRefresh != null) ({ currentReset.value?.invoke(); Unit }) else null)
        onDispose { unregister() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, name) {
        val observer = LifecycleEventObserver { _, event -> FrameDiagnostics.mark("$name:$event") }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
