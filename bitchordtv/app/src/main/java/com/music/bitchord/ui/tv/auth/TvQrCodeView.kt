package com.music.bitchord.ui.tv.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TvQrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    quietZone: Int = 3,
) {
    val matrix = remember(content) {
        try {
            QrCodeGenerator.encode(content)
        } catch (e: Exception) {
            emptyArray()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix.isNotEmpty()) {
            Canvas(modifier = Modifier.size(size - 24.dp)) {
                val matrixSize = matrix.size
                val cellSize = this.size.width / (matrixSize + quietZone * 2)

                for (r in 0 until matrixSize) {
                    for (c in 0 until matrixSize) {
                        if (matrix[r][c]) {
                            val x = (c + quietZone) * cellSize
                            val y = (r + quietZone) * cellSize
                            drawRect(
                                color = Color(0xFF08080B),
                                topLeft = Offset(x, y),
                                size = Size(cellSize + 0.5f, cellSize + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
