package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable

// No transformations on this target; the release card shows the cover as it is.
@Composable
actual fun rememberBlurredBackdrop(url: String?): Any? = url
