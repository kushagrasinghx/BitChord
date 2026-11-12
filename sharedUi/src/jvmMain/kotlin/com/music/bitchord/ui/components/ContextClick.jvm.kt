package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.contextClick(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) {
        this
    } else {
        pointerInput(onClick) {
            awaitEachGesture {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onClick()
                }
            }
        }
    }
