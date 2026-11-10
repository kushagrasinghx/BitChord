/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * App-wide back animation style, ported from SukiSU-Ultra's predictive
 * back toggle model (https://github.com/sukisu-ultra/sukisu-ultra).
 * DEFAULT keeps the classic instant back dispatch; PREDICTIVE routes
 * sheet back handling through androidx PredictiveBackHandler so surfaces
 * scale away with the gesture and spring back when it is cancelled.
 */

package moe.rukamori.archivetune.ui.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import moe.rukamori.archivetune.constants.BackAnimationStyle

val LocalBackAnimationStyle = staticCompositionLocalOf { BackAnimationStyle.DEFAULT }

enum class PlayerBackTarget { NONE, SHEET, SUB_PAGE }

@Stable
class PlayerBackController {
    var backTarget by mutableStateOf(PlayerBackTarget.NONE)

    var routeBack: () -> Unit = {}
}

@Stable
class PredictiveSheetBackState {
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)

    var gestureActive by mutableStateOf(false)
}

@Composable
fun rememberPredictiveSheetBackState(): PredictiveSheetBackState {
    val state = remember { PredictiveSheetBackState() }
    LaunchedEffect(state.gestureActive) {
        if (!state.gestureActive && state.progress.value > 0f) {
            state.progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            )
        }
    }
    return state
}

@Composable
fun PredictiveStyleBackHandler(
    backState: PredictiveSheetBackState,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    if (LocalBackAnimationStyle.current == BackAnimationStyle.PREDICTIVE) {
        val onBackUpdated = rememberUpdatedState(onBack)
        PredictiveBackHandler(enabled = enabled) { events ->
            backState.gestureActive = true
            try {
                events.collect { event -> backState.progress.snapTo(event.progress) }
                onBackUpdated.value()
            } finally {
                backState.gestureActive = false
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onBack)
    }
}
