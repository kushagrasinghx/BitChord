/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.looper

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R

val LooperJost =
    FontFamily(
        Font(R.font.looper_jost_regular, FontWeight.Normal),
        Font(R.font.looper_jost_medium, FontWeight.Medium),
        Font(R.font.looper_jost_semibold, FontWeight.SemiBold),
        Font(R.font.looper_jost_bold, FontWeight.Bold),
    )

val LooperTypography =
    Typography(
        titleLarge =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 0.3.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                letterSpacing = 0.2.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                fontFeatureSettings = "tnum",
            ),
        labelSmall =
            TextStyle(
                fontFamily = LooperJost,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            ),
    )
