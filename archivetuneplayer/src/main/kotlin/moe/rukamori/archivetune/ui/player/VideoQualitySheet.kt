/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.VideoAspectRatio
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.KeepStatusBarHiddenInDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private data class SheetMetrics(
    val horizontalPadding: Dp,
    val rowVerticalPadding: Dp,
    val titleBottomPadding: Dp,
    val dividerVerticalPadding: Dp,
    val listMaxHeight: Dp,
    val showDescriptions: Boolean,

    val pillSpacing: Dp,

    val pillInnerPadding: Dp,

    val pillMinHeight: Dp,
)

@Composable
private fun rememberSheetMetrics(): SheetMetrics {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return remember(landscape) {
        if (landscape) {
            SheetMetrics(
                horizontalPadding = 20.dp,
                rowVerticalPadding = 6.dp,
                titleBottomPadding = 4.dp,
                dividerVerticalPadding = 2.dp,
                listMaxHeight = 168.dp,
                showDescriptions = false,
                pillSpacing = 6.dp,
                pillInnerPadding = 18.dp,
                pillMinHeight = 44.dp,
            )
        } else {
            SheetMetrics(
                horizontalPadding = 24.dp,
                rowVerticalPadding = 12.dp,
                titleBottomPadding = 12.dp,
                dividerVerticalPadding = 8.dp,
                listMaxHeight = 320.dp,
                showDescriptions = true,
                pillSpacing = 10.dp,
                pillInnerPadding = 22.dp,
                pillMinHeight = 60.dp,
            )
        }
    }
}

@Composable
internal fun VideoQualitySheet(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    selectedHeight: Int?,
    onPreferredHeightChange: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        KeepStatusBarHiddenInDialog()
        VideoQualitySheetContent(
            preferredHeight = preferredHeight,
            availableHeights = availableHeights,
            selectedHeight = selectedHeight,
            onSelect = { choice ->
                onPreferredHeightChange(choice)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun VideoQualitySheetContent(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    selectedHeight: Int?,
    onSelect: (Int?) -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(VideoQualityPreference.isExactHeight(preferredHeight)) }
    val metrics = rememberSheetMetrics()

    AnimatedContent(
        targetState = advancedOpen,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (
                slideInHorizontally(tween(220)) { width -> direction * width / 3 } +
                    fadeIn(tween(220))
            ) togetherWith (
                slideOutHorizontally(tween(220)) { width -> -direction * width / 3 } +
                    fadeOut(tween(160))
            )
        },
        label = "video-quality-sheet-page",
    ) { showAdvanced ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.pillSpacing),
        ) {
            if (showAdvanced) {
                AdvancedQualityPage(
                    preferredHeight = preferredHeight,
                    availableHeights = availableHeights,
                    metrics = metrics,
                    onBack = { advancedOpen = false },
                    onSelect = onSelect,
                )
            } else {
                MainQualityPage(
                    preferredHeight = preferredHeight,
                    selectedHeight = selectedHeight,
                    metrics = metrics,
                    onSelect = onSelect,
                    onOpenAdvanced = { advancedOpen = true },
                )
            }
        }
    }
}

@Composable
private fun MainQualityPage(
    preferredHeight: Int?,
    selectedHeight: Int?,
    metrics: SheetMetrics,
    onSelect: (Int?) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val playingLabel = selectedHeight?.let { stringResource(R.string.video_quality_current, formatHeightLabel(it)) }

    fun subtitleFor(
        active: Boolean,
        description: String,
    ) = when {
        active && playingLabel != null -> playingLabel
        metrics.showDescriptions -> description
        else -> null
    }

    SheetTitle(stringResource(R.string.video_quality), metrics)

    QualityRow(
        title = stringResource(R.string.video_quality_mode_auto),
        subtitle = subtitleFor(preferredHeight == null, stringResource(R.string.video_quality_mode_auto_desc)),
        selected = preferredHeight == null,
        metrics = metrics,
        onClick = { onSelect(null) },
    )
    QualityRow(
        title = stringResource(R.string.video_quality_mode_data_saver),
        subtitle =
            subtitleFor(
                preferredHeight == VideoQualityPreference.DATA_SAVER,
                stringResource(R.string.video_quality_mode_data_saver_desc),
            ),
        selected = preferredHeight == VideoQualityPreference.DATA_SAVER,
        metrics = metrics,
        onClick = { onSelect(VideoQualityPreference.DATA_SAVER) },
    )
    QualityRow(
        title = stringResource(R.string.video_quality_mode_high),
        subtitle =
            subtitleFor(
                preferredHeight == VideoQualityPreference.HIGH_QUALITY,
                stringResource(R.string.video_quality_mode_high_desc),
            ),
        selected = preferredHeight == VideoQualityPreference.HIGH_QUALITY,
        metrics = metrics,
        onClick = { onSelect(VideoQualityPreference.HIGH_QUALITY) },
    )

    Spacer(modifier = Modifier.height(metrics.dividerVerticalPadding))

    val exactHeight = preferredHeight?.takeIf { VideoQualityPreference.isExactHeight(it) }
    QualityRow(
        title = stringResource(R.string.video_quality_advanced),

        subtitle =
            when {
                exactHeight != null -> formatHeightLabel(exactHeight)
                metrics.showDescriptions -> stringResource(R.string.video_quality_advanced_desc)
                else -> null
            },
        selected = exactHeight != null,
        metrics = metrics,
        onClick = onOpenAdvanced,

        trailingIcon = R.drawable.navigate_next,
    )
}

@Composable
private fun AdvancedQualityPage(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    metrics: SheetMetrics,
    onBack: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.video_quality_back),
            )
        }
        Text(
            text = stringResource(R.string.video_quality_advanced_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (availableHeights.isEmpty()) {
        Text(
            text = stringResource(R.string.video_quality_unavailable_on_device),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.rowVerticalPadding,
                ),
        )
    } else {
        Column(
            modifier =
                Modifier
                    .heightIn(max = metrics.listMaxHeight)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.pillSpacing),
        ) {
            availableHeights.sortedDescending().forEach { height ->
                QualityRow(
                    title = formatHeightLabel(height),
                    subtitle = null,
                    selected = preferredHeight == height,
                    metrics = metrics,
                    onClick = { onSelect(height) },
                )
            }
        }
    }
}

@Composable
internal fun VideoAspectRatioSheet(
    aspectRatio: VideoAspectRatio,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val metrics = rememberSheetMetrics()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        KeepStatusBarHiddenInDialog()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.pillSpacing),
        ) {
            SheetTitle(stringResource(R.string.video_aspect_ratio), metrics)
            VideoAspectRatio.entries.forEach { ratio ->
                QualityRow(
                    title = stringResource(ratio.labelRes),
                    subtitle = null,
                    selected = ratio == aspectRatio,
                    metrics = metrics,
                    onClick = {
                        onAspectRatioChange(ratio)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

private val VideoAspectRatio.labelRes: Int
    get() =
        when (this) {
            VideoAspectRatio.FIT -> R.string.video_aspect_fit
            VideoAspectRatio.CROP -> R.string.video_aspect_crop
            VideoAspectRatio.STRETCH -> R.string.video_aspect_stretch
            VideoAspectRatio.FILL -> R.string.video_aspect_fill
        }

@Composable
private fun SheetTitle(
    text: String,
    metrics: SheetMetrics,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier.padding(

                start = metrics.horizontalPadding + 8.dp,
                end = metrics.horizontalPadding + 8.dp,
                top = 4.dp,

                bottom = (metrics.titleBottomPadding - metrics.pillSpacing).coerceAtLeast(0.dp),
            ),
    )
}

@Composable
private fun QualityRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    metrics: SheetMetrics,
    onClick: () -> Unit,
    @DrawableRes trailingIcon: Int? = null,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val subtitleColor =
        if (selected) {
            contentColor.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.horizontalPadding)
                .heightIn(min = metrics.pillMinHeight)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(containerColor)
                .then(

                    if (trailingIcon != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                    },
                ).padding(
                    horizontal = metrics.pillInnerPadding,
                    vertical = metrics.rowVerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        when {
            trailingIcon != null ->
                Icon(
                    painter = painterResource(trailingIcon),
                    contentDescription = null,
                    tint = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

            selected ->
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
        }
    }
}

@Composable
internal fun videoQualityPillLabel(
    preferredHeight: Int?,
    selectedHeight: Int?,
): String =
    when {
        VideoQualityPreference.isExactHeight(preferredHeight) -> "${preferredHeight}p"
        selectedHeight != null -> "${selectedHeight}p"
        preferredHeight == VideoQualityPreference.DATA_SAVER ->
            stringResource(R.string.video_quality_mode_data_saver)

        preferredHeight == VideoQualityPreference.HIGH_QUALITY ->
            stringResource(R.string.video_quality_mode_high)

        else -> stringResource(R.string.video_quality_mode_auto)
    }
