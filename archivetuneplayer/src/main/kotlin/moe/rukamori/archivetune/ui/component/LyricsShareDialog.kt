/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.ComposeToImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

fun shareLyricsAsText(
    context: Context,
    payload: LyricsSharePayload,
    songId: String?,
) {
    val songLink = songId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" }
    val shareBody =
        buildString {
            append("\"")
            append(payload.lyricsText)
            append("\"\n\n")
            append(payload.songTitle)
            append(" - ")
            append(payload.artists)
            if (songLink != null) {
                append('\n')
                append(songLink)
            }
        }

    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_lyrics),
        ),
    )
}

@Composable
fun LyricsShareImageDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompactLayout =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    var isSharing by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(LyricsShareImageOptions()) }
    var areAdvancedOptionsVisible by remember { mutableStateOf(false) }

    var customTextColor by remember { mutableStateOf<Color?>(null) }

    val handleShare: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(context, R.string.lyrics_share_export_not_supported, Toast.LENGTH_SHORT).show()
        } else {
            isSharing = true
            scope.launch {
                try {
                    val image =
                        if (options.vinylMode) {
                            ComposeToImage.createVinylImage(
                                context = context,
                                coverArtUrl = mediaMetadata?.thumbnailUrl,
                                songTitle = payload.songTitle,
                                artistName = payload.artists,
                                width = options.aspectRatio.exportWidth,
                                height = options.aspectRatio.exportHeight,
                            )
                        } else {
                            val (displayText, romanisedLineIndices) =
                                payload.shareDisplayText(
                                    showTranslation = options.showTranslation,
                                    showRomanisation = options.showRomanisation,
                                )
                            ComposeToImage.createLyricsImage(
                                context = context,
                                coverArtUrl = mediaMetadata?.thumbnailUrl,
                                songTitle = payload.songTitle,
                                artistName = payload.artists,
                                lyrics = displayText,
                                width = options.aspectRatio.exportWidth,
                                height = options.aspectRatio.exportHeight,
                                textColor = customTextColor?.toArgb(),
                                shareOptions = options,
                                romanisedLineIndices = romanisedLineIndices,
                            )
                        }
                    val fileName = "lyrics_${System.currentTimeMillis()}"
                    val uri = ComposeToImage.saveBitmapAsFile(context, image, fileName)
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.share_lyrics),
                        ),
                    )
                    onDismissRequest()
                } catch (e: Exception) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.lyrics_share_export_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT,
                        ).show()
                } finally {
                    isSharing = false
                }
            }
        }
    }

    LyricsShareStudioDialog(
        mediaMetadata = mediaMetadata,
        payload = payload,
        options = options,
        onOptionsChange = { options = it },
        customTextColor = customTextColor,
        onCustomTextColorChange = { customTextColor = it },
        areAdvancedOptionsVisible = !isCompactLayout || areAdvancedOptionsVisible,
        onShowAdvancedOptions = { areAdvancedOptionsVisible = true },
        isSharing = isSharing,
        isCompactLayout = isCompactLayout,
        onShare = handleShare,
        onDismissRequest = onDismissRequest,
    )

    if (isSharing) {
        LyricsShareLoadingDialog()
    }
}

@Composable
private fun LyricsShareStudioDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    customTextColor: Color?,
    onCustomTextColorChange: (Color?) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!isSharing) onDismissRequest()
        },
        properties =
            DialogProperties(
                dismissOnBackPress = !isSharing,
                dismissOnClickOutside = !isSharing,
                usePlatformDefaultWidth = false,
            ),
    ) {
        KeepStatusBarHiddenInDialog()
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val outerPadding = if (isCompactLayout) 12.dp else 24.dp
            val maxDialogHeight = (maxHeight - outerPadding * 2).coerceAtLeast(1.dp)
            val maxDialogWidth = if (isCompactLayout) 560.dp else 980.dp

            Surface(
                modifier =
                    Modifier
                        .padding(outerPadding)
                        .fillMaxWidth()
                        .widthIn(max = maxDialogWidth)
                        .heightIn(max = maxDialogHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
            ) {
                LyricsShareStudioScaffold(
                    mediaMetadata = mediaMetadata,
                    payload = payload,
                    options = options,
                    onOptionsChange = onOptionsChange,
                    customTextColor = customTextColor,
                    onCustomTextColorChange = onCustomTextColorChange,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    isSharing = isSharing,
                    isCompactLayout = isCompactLayout,
                    onShare = onShare,
                    onDismiss = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareLoadingDialog() {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Text(
                    text = stringResource(R.string.generating_image),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.please_wait),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareStudioScaffold(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    customTextColor: Color?,
    onCustomTextColorChange: (Color?) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    val horizontalPadding = if (isCompactLayout) 12.dp else 16.dp
    val verticalPadding = if (isCompactLayout) 12.dp else 16.dp
    val sectionSpacing = if (isCompactLayout) 10.dp else 12.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            if (isCompactLayout) {
                LyricsShareHeader(
                    payload = payload,
                    options = options,
                    modifier = Modifier.fillMaxWidth(),
                )
                PreviewContainer(
                    payload = payload,
                    mediaMetadata = mediaMetadata,
                    options = options,
                    customTextColor = customTextColor,
                    isCompactLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ControlsSection(
                    payload = payload,
                    options = options,
                    onOptionsChange = onOptionsChange,
                    customTextColor = customTextColor,
                    onCustomTextColorChange = onCustomTextColorChange,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PreviewContainer(
                        payload = payload,
                        mediaMetadata = mediaMetadata,
                        options = options,
                        customTextColor = customTextColor,
                        isCompactLayout = false,
                        modifier = Modifier.weight(1.1f),
                    )
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LyricsShareHeader(
                            payload = payload,
                            options = options,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ControlsSection(
                            payload = payload,
                            options = options,
                            onOptionsChange = onOptionsChange,
                            customTextColor = customTextColor,
                            onCustomTextColorChange = onCustomTextColorChange,
                            areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                            onShowAdvancedOptions = onShowAdvancedOptions,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionsSection(
            isSharing = isSharing,
            isCompactLayout = isCompactLayout,
            onShare = onShare,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun LyricsShareHeader(
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    modifier: Modifier = Modifier,
) {
    val lyricSnippet =
        remember(payload.lyricsText) {
            payload.lyricsText
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.share_lyrics),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            LyricsShareInfoPill(
                text =
                    stringResource(
                        R.string.lyrics_share_resolution_value,
                        options.aspectRatio.exportWidth,
                        options.aspectRatio.exportHeight,
                    ),
                emphasized = true,
            )
        }
        Text(
            text = payload.songTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = payload.artists,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lyricSnippet.isNotBlank() && lyricSnippet != payload.songTitle) {
            Text(
                text = lyricSnippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewContainer(
    payload: LyricsSharePayload,
    mediaMetadata: MediaMetadata?,
    options: LyricsShareImageOptions,
    customTextColor: Color?,
    isCompactLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewWidthFraction =
        when (options.aspectRatio) {
            LyricsShareAspectRatio.Square -> if (isCompactLayout) 0.78f else 0.82f
            LyricsShareAspectRatio.Portrait -> if (isCompactLayout) 0.58f else 0.62f
            LyricsShareAspectRatio.Story -> if (isCompactLayout) 0.40f else 0.42f
        }
    val previewMaxWidth = if (isCompactLayout) 260.dp else 380.dp

    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(payload, options, customTextColor, mediaMetadata?.thumbnailUrl) {
        if (options.vinylMode) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@LaunchedEffect

        delay(200)
        val exportW = options.aspectRatio.exportWidth
        val exportH = options.aspectRatio.exportHeight
        val scale = minOf(1f, 900f / maxOf(exportW, exportH))
        val (displayText, romanisedLineIndices) =
            payload.shareDisplayText(
                showTranslation = options.showTranslation,
                showRomanisation = options.showRomanisation,
            )
        previewBitmap =
            runCatching {
                ComposeToImage.createLyricsImage(
                    context = context,
                    coverArtUrl = mediaMetadata?.thumbnailUrl,
                    songTitle = payload.songTitle,
                    artistName = payload.artists,
                    lyrics = displayText,
                    width = (exportW * scale).toInt().coerceAtLeast(320),
                    height = (exportH * scale).toInt().coerceAtLeast(320),
                    textColor = customTextColor?.toArgb(),
                    shareOptions = options,
                    romanisedLineIndices = romanisedLineIndices,
                )
            }.getOrNull()
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(previewWidthFraction)
                            .widthIn(max = previewMaxWidth)
                            .aspectRatio(options.aspectRatio.previewAspectRatio)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.large,
                            ).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (options.vinylMode) {
                        VinylImageCard(
                            songTitle = payload.songTitle,
                            artistName = payload.artists,
                            coverArtUrl = mediaMetadata?.thumbnailUrl,
                        )
                    } else {
                        val bitmap = previewBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LoadingIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsSection(
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    customTextColor: Color?,
    onCustomTextColorChange: (Color?) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_layout)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsShareAspectRatio.entries.forEach { aspectRatio ->
                        LyricsAspectRatioOption(
                            aspectRatio = aspectRatio,
                            selected = options.aspectRatio == aspectRatio,
                            onClick = { onOptionsChange(options.copy(aspectRatio = aspectRatio)) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_style)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsShareStyle.entries.forEach { style ->
                        LyricsStyleOption(
                            style = style,
                            selected = options.style == style && !options.vinylMode,
                            onClick = { onOptionsChange(options.copy(style = style, vinylMode = false)) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { onOptionsChange(options.copy(vinylMode = !options.vinylMode)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyrics_share_vinyl_mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.lyrics_share_vinyl_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = options.vinylMode,
                    onCheckedChange = { onOptionsChange(options.copy(vinylMode = it)) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (payload.hasTranslation || payload.hasRomanisation) {
                LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_lyrics_content)) {
                    if (payload.hasTranslation) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .clickable {
                                        onOptionsChange(options.copy(showTranslation = !options.showTranslation))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.lyrics_share_translation),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_share_translation_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = options.showTranslation,
                                onCheckedChange = {
                                    onOptionsChange(options.copy(showTranslation = it))
                                },
                            )
                        }
                    }
                    if (payload.hasRomanisation) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .clickable {
                                        onOptionsChange(options.copy(showRomanisation = !options.showRomanisation))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.lyrics_share_romanisation),
                                    style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_share_romanisation_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = options.showRomanisation,
                                onCheckedChange = {
                                    onOptionsChange(options.copy(showRomanisation = it))
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_text_color)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsTextColorSwatch(
                        color = Color(0xFFF7F0EB),
                        label = stringResource(R.string.lyrics_share_text_color_default),
                        selected = customTextColor == null,
                        onClick = { onCustomTextColorChange(null) },
                    )
                    LyricsTextColorPreset.entries.forEach { preset ->
                        LyricsTextColorSwatch(
                            color = preset.color,
                            label = stringResource(preset.labelRes),
                            selected = customTextColor == preset.color,
                            onClick = { onCustomTextColorChange(preset.color) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (areAdvancedOptionsVisible) {
                LyricsShareControlGroup(title = stringResource(R.string.more_options)) {
                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_background_blur),
                        valueLabel = stringResource(R.string.lyrics_share_background_blur_value, options.sanitizedBlurRadius.toInt()),
                        value = options.blurRadius,
                        onValueChange = { onOptionsChange(options.copy(blurRadius = it)) },
                        valueRange = 0f..48f,
                    )

                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_glass_dim),
                        valueLabel = stringResource(R.string.lyrics_share_glass_dim_value, (options.sanitizedDimAmount * 100).toInt()),
                        value = options.dimAmount,
                        onValueChange = { onOptionsChange(options.copy(dimAmount = it)) },
                        valueRange = 0f..1f,
                    )

                    if (options.style == LyricsShareStyle.LIQUID_GLASS) {
                        LyricsShareSlider(
                            title = stringResource(R.string.lyrics_share_glass_liquidy),
                            valueLabel = stringResource(R.string.lyrics_share_glass_liquidy_value, (options.sanitizedLiquidyAmount * 100).toInt()),
                            value = options.liquidyAmount,
                            onValueChange = { onOptionsChange(options.copy(liquidyAmount = it)) },
                            valueRange = 0f..1f,
                        )

                        LyricsShareSlider(
                            title = stringResource(R.string.lyrics_share_glass_refraction),
                            valueLabel = stringResource(R.string.lyrics_share_glass_refraction_value, (options.sanitizedRefractionAmount * 100).toInt()),
                            value = options.refractionAmount,
                            onValueChange = { onOptionsChange(options.copy(refractionAmount = it)) },
                            valueRange = 0f..1f,
                        )

                        LyricsShareSlider(
                            title = stringResource(R.string.lyrics_share_glass_opacity),
                            valueLabel = stringResource(R.string.lyrics_share_glass_opacity_value, (options.sanitizedGlassOpacity * 100).toInt()),
                            value = options.glassOpacity,
                            onValueChange = { onOptionsChange(options.copy(glassOpacity = it)) },
                            valueRange = 0f..1f,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .clickable { onOptionsChange(options.copy(showArtwork = !options.showArtwork)) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lyrics_share_show_cover),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.lyrics_share_show_cover_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = options.showArtwork,
                            onCheckedChange = { onOptionsChange(options.copy(showArtwork = it)) },
                        )
                    }
                }
            } else {
                TextButton(
                    onClick = onShowAdvancedOptions,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.more_options),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareControlGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun LyricsShareInfoPill(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsAspectRatioOption(
    aspectRatio: LyricsShareAspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContent",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 84.dp)
                .heightIn(min = 40.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(aspectRatio.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsStyleOption(
    style: LyricsShareStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleContent",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 84.dp)
                .heightIn(min = 40.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(style.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsShareSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionsSection(
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionModifier = Modifier.height(48.dp)
    val contentPadding =
        Modifier.padding(
            horizontal = if (isCompactLayout) 12.dp else 16.dp,
            vertical = 10.dp,
        )

    if (isCompactLayout) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1.2f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class LyricsTextColorPreset(
    val color: Color,
    val labelRes: Int,
) {
    WHITE(Color(0xFFFFFFFF), R.string.lyrics_share_text_color_white),
    BLACK(Color(0xFF000000), R.string.lyrics_share_text_color_black),
    CREAM(Color(0xFFF5E9D5), R.string.lyrics_share_text_color_cream),
    CORAL(Color(0xFFFF6B6B), R.string.lyrics_share_text_color_coral),
    AMBER(Color(0xFFFFB454), R.string.lyrics_share_text_color_amber),
    LEMON(Color(0xFFFFE66D), R.string.lyrics_share_text_color_lemon),
    MINT(Color(0xFF6BCB77), R.string.lyrics_share_text_color_mint),
    TEAL(Color(0xFF4DCCC0), R.string.lyrics_share_text_color_teal),
    SKY(Color(0xFF54A0FF), R.string.lyrics_share_text_color_sky),
    INDIGO(Color(0xFF5F6CAF), R.string.lyrics_share_text_color_indigo),
    VIOLET(Color(0xFFB06AB3), R.string.lyrics_share_text_color_violet),
    ROSE(Color(0xFFFF7BB0), R.string.lyrics_share_text_color_rose),
}

@Composable
private fun LyricsTextColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val borderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsTextSwatchBorder",
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsTextSwatchScale",
    )

    Box(
        modifier =
            modifier
                .size(32.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = label,
                tint =
                    if (color.luminance() > 0.5f) {
                        Color.Black
                    } else {
                        Color.White
                    },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
