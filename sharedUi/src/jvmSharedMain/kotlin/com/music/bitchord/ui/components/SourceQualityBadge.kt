package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a source row says it can be streamed at, in the player badge's terms.
 *
 * Read off [Song.sourceQuality] — the tier the addon *offered* in its search
 * answer, not anything measured — so it is a label for picking between rows,
 * and the player's own badge is still the one that confirms what played.
 * `LOW` and unlabelled rows get none, as under the seek bar.
 */
enum class SourceQuality { DOLBY_ATMOS, HI_RES_LOSSLESS, LOSSLESS, HIGH_QUALITY }

fun Song.sourceQualityBadge(): SourceQuality? = when (sourceQuality?.uppercase()) {
    "DOLBY" -> SourceQuality.DOLBY_ATMOS
    "HI-RES", "HIRES" -> SourceQuality.HI_RES_LOSSLESS
    // The desktop's local files carry their container here — FLAC, WAV.
    "LOSSLESS", "FLAC", "ALAC", "WAV" ->
        if (sourceHiRes) SourceQuality.HI_RES_LOSSLESS else SourceQuality.LOSSLESS
    "HIGH", "320" -> SourceQuality.HIGH_QUALITY
    else -> null
}

/**
 * The player's quality glyph and label, at row size: the same lossless wave,
 * Dolby double-D and headphones the badge under the seek bar draws.
 */
@Composable
fun SourceQualityBadge(
    quality: SourceQuality,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (quality) {
            // The two drawables at their own size, as in the player: the wave
            // is wide and the double-D nearly square.
            SourceQuality.DOLBY_ATMOS -> Icon(painterResource(Res.drawable.ic_dolby_atmos), null, tint = color)
            SourceQuality.HI_RES_LOSSLESS, SourceQuality.LOSSLESS ->
                Icon(painterResource(Res.drawable.ic_lossless), null, tint = color)
            SourceQuality.HIGH_QUALITY -> Icon(
                rememberVectorPainter(Icons.Rounded.Headphones),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = when (quality) {
                SourceQuality.DOLBY_ATMOS -> "Dolby Atmos"
                SourceQuality.HI_RES_LOSSLESS -> stringResource(Res.string.hi_res_lossless)
                SourceQuality.LOSSLESS -> stringResource(Res.string.lossless)
                SourceQuality.HIGH_QUALITY -> stringResource(Res.string.high_quality)
            },
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
