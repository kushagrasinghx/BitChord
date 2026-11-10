/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R

val SimpMusicPoppins: FontFamily = FontFamily(Font(R.font.simpmusic_poppins))

val SimpMusicTypography: Typography =
    Typography(

        titleSmall =
            TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SimpMusicPoppins,
            ),
        titleMedium =
            TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SimpMusicPoppins,
            ),
        titleLarge =
            TextStyle(
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SimpMusicPoppins,
            ),
        bodySmall =
            TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SimpMusicPoppins,
            ),
        bodyMedium =
            TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SimpMusicPoppins,
            ),
        bodyLarge =
            TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SimpMusicPoppins,
            ),
        displayLarge =
            TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SimpMusicPoppins,
            ),
        headlineMedium =
            TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SimpMusicPoppins,
            ),
        headlineLarge =
            TextStyle(
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SimpMusicPoppins,
            ),
        labelMedium =
            TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SimpMusicPoppins,
            ),
        labelSmall =
            TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SimpMusicPoppins,
            ),
    )
