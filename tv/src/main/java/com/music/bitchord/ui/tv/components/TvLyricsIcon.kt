package com.music.bitchord.ui.tv.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

/**
 * 3-bar curved wave lyrics icon matching the user's custom design.
 */
@Composable
fun TvLyricsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.height * 0.16f
        val cap = StrokeCap.Round

        // 1. Top curved wave bar (narrowest)
        val p1 = Path().apply {
            val y = size.height * 0.20f
            val w = size.width * 0.64f
            val startX = (size.width - w) / 2f
            val endX = startX + w
            moveTo(startX, y)
            quadraticTo(size.width / 2f, y + size.height * 0.15f, endX, y)
        }
        drawPath(p1, color = tint, style = Stroke(width = strokeWidth, cap = cap))

        // 2. Middle curved wave bar (medium)
        val p2 = Path().apply {
            val y = size.height * 0.50f
            val w = size.width * 0.84f
            val startX = (size.width - w) / 2f
            val endX = startX + w
            moveTo(startX, y)
            quadraticTo(size.width / 2f, y + size.height * 0.16f, endX, y)
        }
        drawPath(p2, color = tint, style = Stroke(width = strokeWidth, cap = cap))

        // 3. Bottom curved wave bar (widest)
        val p3 = Path().apply {
            val y = size.height * 0.80f
            val w = size.width * 0.98f
            val startX = (size.width - w) / 2f
            val endX = startX + w
            moveTo(startX, y)
            quadraticTo(size.width / 2f, y + size.height * 0.17f, endX, y)
        }
        drawPath(p3, color = tint, style = Stroke(width = strokeWidth, cap = cap))
    }
}

/**
 * Clean BitChord Brand Badge displayed on the bottom-left below album cover.
 * Pure transparent background (no gray box), displaying Logo + "BitChord".
 */
@Composable
fun TvLyricsBadge(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "BitChord Logo",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "BitChord",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            color = Color.White,
            letterSpacing = (-0.2).sp,
        )
    }
}
