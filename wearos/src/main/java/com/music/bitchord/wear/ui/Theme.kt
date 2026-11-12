package com.music.bitchord.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.music.bitchord.wear.R

/**
 * SF Pro Display, as on the phone — subset to Latin for the watch (the phone's
 * files are 2.3 MB a weight; these are ~50 KB). Anything outside the subset, a
 * Hindi or Japanese title, falls back to the system face glyph by glyph.
 */
val SFPro = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
)

/** The phone's AccentRed: Love, and nothing much else. */
val AccentRed = Color(0xFFFA2D48)

/** Secondary text on the mesh and on black: the phone's onSurfaceVariant. */
val Secondary = Color(0xFF8E8E93)

/** Grouped cells on black, iOS's secondarySystemBackground in dark. */
val Cell = Color(0xFF1C1C1E)

object WearType {
    val title = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W600, fontSize = 15.sp, letterSpacing = (-0.2).sp)
    val subtitle = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W400, fontSize = 13.sp, letterSpacing = (-0.1).sp)
    val header = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W700, fontSize = 17.sp, letterSpacing = (-0.3).sp)
    val row = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = (-0.15).sp)
    val rowSub = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W400, fontSize = 12.sp)
    val button = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W600, fontSize = 14.sp)
    val caption = TextStyle(fontFamily = SFPro, fontWeight = FontWeight.W500, fontSize = 11.sp, letterSpacing = 0.2.sp)
}

@Composable
fun BitChordWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Secondary,
            surfaceContainer = Cell,
        ),
        typography = Typography(defaultFontFamily = SFPro),
        content = content,
    )
}
