package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable

/**
 * What to load for a release card's backdrop: the lower part of the cover at
 * [url], blurred the way Apple Music blurs it under a release, as an image
 * model for `AsyncImage`.
 *
 * The blur is done once, on a bitmap a few dozen pixels across, when the
 * image loads — never per frame — and the image cache keeps the result, so a
 * card scrolling past costs one draw of a finished picture. Where the
 * platform has no image transformations the cover comes back as it is.
 */
@Composable
expect fun rememberBlurredBackdrop(url: String?): Any?
