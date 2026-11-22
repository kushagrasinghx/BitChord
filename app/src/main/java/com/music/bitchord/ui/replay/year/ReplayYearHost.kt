package com.music.bitchord.ui.replay.year

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The year story and its share sheet, as the app mounts them over everything.
 *
 * A function of its own rather than more of the app's body, which is already
 * as large as one JVM method is allowed to be. Back unwinds them in the order
 * they opened: the share sheet first, then the story.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayYearHost(year: ReplayYear?, open: Boolean, onClose: () -> Unit) {
    var sharing by rememberSaveable { mutableStateOf<YearSlide?>(null) }
    year ?: return

    BackHandler(enabled = open && sharing == null, onBack = onClose)
    BackHandler(enabled = sharing != null) { sharing = null }

    if (open) {
        ReplayYearStories(
            year = year,
            onClose = onClose,
            onShare = { slide -> sharing = slide },
            paused = sharing != null,
        )
    }
    // Its own sheet: a card of the year story is shared as that card, as it
    // stood when it finished, not as one of the ordinary Replay's posters.
    sharing?.let { slide ->
        ModalBottomSheet(
            onDismissRequest = { sharing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            ReplayYearShareSheet(year = year, slide = slide, onDismiss = { sharing = null })
        }
    }
}
