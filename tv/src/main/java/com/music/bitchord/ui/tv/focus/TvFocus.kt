package com.music.bitchord.ui.tv.focus

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.appleSpring

/**
 * Standard TV card focus modifier with hardware-accelerated draw-layer scale and Apple smooth spring physics.
 * Includes active compression response upon D-pad click (0.96x press feedback).
 */
fun Modifier.tvCardFocus(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.05f,
    borderWidth: Dp = 2.dp,
    focusedBorderColor: Color = Color.Unspecified,
    unfocusedBorderColor: Color = Color.Transparent,
    elevation: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val palette = com.music.bitchord.ui.tv.theme.LocalTvColors.current
    val effectiveBorderColor = if (focusedBorderColor != Color.Unspecified) focusedBorderColor else palette.borderFocusedWhite
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.96f
        isFocused -> focusedScale
        else -> 1.0f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(if (isPressed) AppleSpringPreset.Snappy else AppleSpringPreset.Smooth),
        label = "tvCardFocusScale",
    )

    val effectiveBorderWidth = if (isFocused) borderWidth.coerceAtLeast(3.5.dp) else borderWidth

    this
        .focusable(interactionSource = interactionSource)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .zIndex(if (isFocused) 10f else 1f)
        .then(
            if (isFocused) {
                Modifier.shadow(elevation, shape, clip = false)
            } else Modifier
        )
        .clip(shape)
        .border(
            border = BorderStroke(
                width = effectiveBorderWidth,
                color = if (isFocused) effectiveBorderColor else unfocusedBorderColor,
            ),
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else Modifier
        )
}

/**
 * Standard TV button focus modifier with hardware-accelerated graphicsLayer scale and click feedback.
 */
fun Modifier.tvButtonFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.03f,
    focusedBorderColor: Color = Color.Unspecified,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val palette = com.music.bitchord.ui.tv.theme.LocalTvColors.current
    val effectiveBorderColor = if (focusedBorderColor != Color.Unspecified) focusedBorderColor else palette.borderFocusedWhite
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.95f
        isFocused -> focusedScale
        else -> 1.0f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "tvBtnFocusScale",
    )

    val effectiveBorderWidth = if (isFocused) borderWidth.coerceAtLeast(3.dp) else borderWidth

    this
        .focusable(interactionSource = interactionSource)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .zIndex(if (isFocused) 10f else 1f)
        .clip(shape)
        .border(
            border = BorderStroke(
                width = effectiveBorderWidth,
                color = if (isFocused) effectiveBorderColor else unfocusedBorderColor,
            ),
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
            } else Modifier
        )
}

/**
 * Intercepts TV remote key events (such as media keys, D-pad center, Left/Right seeking, Back).
 */
fun Modifier.onTvKeyEvent(
    onSelect: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onPlayPause: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

    when (event.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            onSelect?.invoke() ?: false
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            onLeft?.invoke() ?: false
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            onRight?.invoke() ?: false
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            onUp?.invoke() ?: false
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            onDown?.invoke() ?: false
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> {
            onPlayPause?.invoke() ?: false
        }
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE -> {
            onBack?.invoke() ?: false
        }
        else -> false
    }
}
