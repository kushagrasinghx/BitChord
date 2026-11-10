/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import com.kyant.backdrop.Backdrop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

val LocalPlainHeaderPill = compositionLocalOf { false }

@Composable
fun FrostedHeaderPill(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    plain: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    if (plain) {
        CompositionLocalProvider(LocalPlainHeaderPill provides true) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                Row(
                    modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content()
                }
            }
        }
    } else if (backdrop != null) {
        Row(
            modifier =
                modifier
                    .clip(pillShape)
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = pillShape,
                        interactive = false,
                        blurRadius = LiquidGlassPillBlurRadius,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    } else {
        val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
        Surface(
            modifier = modifier.clip(pillShape),
            shape = pillShape,
            color = baseColor.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.72f)),
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}
