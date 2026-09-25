package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.RecommendationFeedbackNotice

/** Short-lived native-feedback result, optionally exposing YouTube's own inverse action. */
@Composable
fun RecommendationFeedbackNoticeHost(
    notice: RecommendationFeedbackNotice?,
    onAction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn() + slideInVertically { it / 2 }) togetherWith
                (fadeOut() + slideOutVertically { it / 2 })
        },
        contentKey = { it?.id },
        label = "recommendationFeedbackNotice",
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        if (current != null) {
            Surface(
                color = Color(0xFF282828),
                contentColor = Color.White,
                shape = RoundedCornerShape(5.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    current.actionLabel?.let { label ->
                        TextButton(onClick = { onAction(current.id) }) { Text(label) }
                    }
                }
            }
        }
    }
}
