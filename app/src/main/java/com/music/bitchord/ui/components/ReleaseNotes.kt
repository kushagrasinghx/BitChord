package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown

/** Renders a release's markdown notes, themed by Material 3, images via Coil. */
@Composable
fun ReleaseNotes(notes: String?, modifier: Modifier = Modifier) {
    if (notes.isNullOrBlank()) return
    Markdown(
        content = notes,
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier,
    )
}
