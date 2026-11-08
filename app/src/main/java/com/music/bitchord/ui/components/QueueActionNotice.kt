package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Text is formatted at the Save call site, after each edit has been acknowledged. */
data class PlaylistSaveNotice(
    val added: String? = null,
    val removed: String? = null,
    val error: String? = null,
)

/** A unique queue result, so repeating the same action restarts its lifetime. */
data class QueueActionNotice(
    val id: Int,
    val message: String,
    val playlistSave: PlaylistSaveNotice? = null,
)

/** Brief queue confirmation shown immediately above the mini player. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun QueueActionNoticeHost(
    notice: QueueActionNotice?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn() + slideInVertically { height -> height / 2 }) togetherWith
                    (fadeOut() + slideOutVertically { height -> height / 2 })
            }
        },
        contentKey = { it?.id },
        label = "queueActionNotice",
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        if (current != null) {
            val playlistSave = current.playlistSave
            val shape = if (playlistSave != null) MaterialTheme.shapes.extraLarge else RoundedCornerShape(5.dp)
            val surface = MaterialTheme.colorScheme.surface
            Surface(
                color = if (playlistSave != null) Color.Transparent else Color(0xFF282828),
                contentColor = if (playlistSave != null) MaterialTheme.colorScheme.onSurface else Color.White,
                shape = shape,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .then(if (playlistSave == null) Modifier else Modifier
                        .clip(shape)
                        .then(if (reduceDynamicBlur) Modifier.background(surface) else Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(surface),
                        ))
                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape))
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (playlistSave != null) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        playlistSave.added?.let { PlaylistNoticeRow(it, added = true) }
                        playlistSave.removed?.let { PlaylistNoticeRow(it, added = false) }
                        playlistSave.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistNoticeRow(message: String, added: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (added) SfIcons.AddToPlaylist else SfIcons.RemoveFromPlaylist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
