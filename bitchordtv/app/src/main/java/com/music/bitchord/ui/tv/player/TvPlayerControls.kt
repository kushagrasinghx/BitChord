package com.music.bitchord.ui.tv.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

@Composable
fun TvPlayerControls(
    isPlaying: Boolean,
    isLiked: Boolean,
    isShuffleActive: Boolean,
    repeatMode: Int,
    isLyricsActive: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Centered Transport Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            // 1. Favorite / Like (Pure White)
            TvTransportIconButton(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                isActive = isLiked,
                activeColor = Color.White,
                onClick = onToggleLike,
            )

            // 2. Shuffle
            TvTransportIconButton(
                icon = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                isActive = isShuffleActive,
                activeColor = Color.White,
                onClick = onToggleShuffle,
            )

            // 3. Previous
            TvTransportIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                size = 48.dp,
                iconSize = 26.dp,
                enabled = hasPrevious,
                onClick = onPrevious,
            )

            // 4. Large Circular Play/Pause Anchor
            TvPlayPauseAnchor(
                isPlaying = isPlaying,
                onClick = onPlayPause,
            )

            // 5. Next
            TvTransportIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                size = 48.dp,
                iconSize = 26.dp,
                enabled = hasNext,
                onClick = onNext,
            )

            // 6. Queue
            TvTransportIconButton(
                icon = Icons.Default.QueueMusic,
                contentDescription = "Queue",
                onClick = onToggleQueue,
            )
        }

        // 7. Right-aligned Show/Hide Lyrics Pill Button (Pure White Focus)
        TvLyricsPillButton(
            isLyricsActive = isLyricsActive,
            onClick = onToggleLyrics,
        )
    }
}

@Composable
private fun TvPlayPauseAnchor(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(64.dp)
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.1f,
                focusedBorderColor = Color.White,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color(0xFFF2F2F7)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.Black,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun TvTransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    isActive: Boolean = false,
    activeColor: Color = Color.White,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.28f)
            isActive -> Color.White.copy(alpha = 0.20f)
            else -> Color.Transparent
        },
        label = "transportBtnBg",
    )

    Box(
        modifier = modifier
            .size(size)
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.1f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Transparent,
                onClick = if (enabled) onClick else null,
            )
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> Color.White.copy(alpha = 0.35f)
                isFocused -> Color.White
                isActive -> activeColor
                else -> Color.White.copy(alpha = 0.85f)
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TvLyricsPillButton(
    isLyricsActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isLyricsActive -> Color.White.copy(alpha = 0.35f)
            else -> Color(0x33FFFFFF)
        },
        label = "lyricsPillBg",
    )

    Row(
        modifier = modifier
            .tvButtonFocus(
                shape = RoundedCornerShape(20.dp),
                focusedScale = 1.05f,
                focusedBorderColor = Color.White,
                onClick = onClick,
            )
            .background(bgColor)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FormatQuote,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isLyricsActive) "Hide lyrics" else "Show lyrics",
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            fontSize = 14.sp,
        )
    }
}
