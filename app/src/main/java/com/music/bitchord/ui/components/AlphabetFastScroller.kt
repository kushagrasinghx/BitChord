package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Normalizes a track title into its A–Z index bucket (or `#` for digits and symbols).
 */
internal fun alphabetIndexBucket(title: String): Char {
    val first = title.trimStart().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

/**
 * Builds a map from each index bucket (`A`–`Z`, `#`) to the first matching position
 * in [titles], preserving the list's current sort order.
 */
internal fun buildAlphabetSectionMap(titles: List<String>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    titles.forEachIndexed { index, title ->
        val bucket = alphabetIndexBucket(title)
        if (!map.containsKey(bucket)) {
            map[bucket] = index
        }
    }
    return map
}

private val ALPHABET_ASC: List<Char> = ('A'..'Z').toList() + '#'
private val ALPHABET_DESC: List<Char> = listOf('#') + ('Z' downTo 'A').toList()

/**
 * Right-edge alphabetical fast-scroll index rail for alphabetically sorted playlists.
 *
 * Dragging or tapping along the rail jumps the list to the first track in that letter
 * bucket while showing a floating letter indicator bubble.
 */
@Composable
fun AlphabetFastScroller(
    titles: List<String>,
    descending: Boolean,
    accentColor: Color,
    surfaceColor: Color,
    onJumpToIndex: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = if (descending) ALPHABET_DESC else ALPHABET_ASC
    val sectionMap = remember(titles) { buildAlphabetSectionMap(titles) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var barHeightPx by remember { mutableIntStateOf(1) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    fun jumpForY(y: Float) {
        val fraction = (y / barHeightPx.coerceAtLeast(1)).coerceIn(0f, 0.9999f)
        val letterIndex = (fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[letterIndex]
        if (activeLetter != letter) {
            activeLetter = letter
            val target = sectionMap[letter]
            if (target != null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch { onJumpToIndex(target) }
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = isDragging && activeLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeLetter?.toString() ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .width(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(surfaceColor.copy(alpha = if (isDragging) 0.78f else 0.45f))
                .padding(vertical = 6.dp)
                .onSizeChanged { barHeightPx = it.height }
                .pointerInput(letters, sectionMap) {
                    detectTapGestures { offset ->
                        isDragging = true
                        jumpForY(offset.y)
                        isDragging = false
                        activeLetter = null
                    }
                }
                .pointerInput(letters, sectionMap) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            jumpForY(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            jumpForY(change.position.y)
                        },
                        onDragEnd = {
                            isDragging = false
                            activeLetter = null
                        },
                        onDragCancel = {
                            isDragging = false
                            activeLetter = null
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            letters.forEach { ch ->
                val available = sectionMap.containsKey(ch)
                val selected = activeLetter == ch
                Text(
                    text = ch.toString(),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> accentColor
                        available -> Color.White.copy(alpha = 0.9f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
}
