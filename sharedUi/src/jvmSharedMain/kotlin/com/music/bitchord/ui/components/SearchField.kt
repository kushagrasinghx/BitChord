package com.music.bitchord.ui.components

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * True where a search field sits on a surface its host draws — the top bar's
 * pill — so the field draws no fill of its own and takes that surface's height
 * rather than its own 46dp.
 */
val LocalSearchFieldOnSurface = staticCompositionLocalOf { false }

/**
 * The pill-shaped search field used at the top of the search page — a
 * magnifier that doubles as the submit button, a hint that gives way to typed
 * text, and a clear button once there is something to clear. Reused wherever
 * else the app needs the same "search this" affordance, such as filtering the
 * settings list.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    /** What the empty field says it is for — the one part that changes per screen. */
    placeholder: String = stringResource(Res.string.search_hint),
    /**
     * Starts the platform's voice input, or null when this device has none.
     * Only surfaces that can act on a spoken query pass it, which keeps the
     * microphone off the settings filter and the desktop's sidebar field.
     */
    onVoiceSearch: (() -> Unit)? = null,
    pulseTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Both ways of saying "search this" do the same two things, so they're
    // written once here rather than twice.
    val submit = {
        onSubmit()
        focusManager.clearFocus()
    }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger == 0) return@LaunchedEffect
        pulseScale.animateTo(1.02f, animationSpec = tween(120, easing = FastOutSlowInEasing))
        pulseScale.animateTo(1f, animationSpec = tween(130, easing = FastOutSlowInEasing))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .then(
                if (LocalSearchFieldOnSurface.current) {
                    Modifier.fillMaxHeight()
                } else {
                    // Fixed height prevents the row from growing when text is entered
                    Modifier
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
                },
            )
            // Asymmetric: the magnifier is a button now and wants a real touch
            // target, so it's given the room by pulling the field's own start
            // padding in rather than by pushing the glyph and the text along.
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The search button. It reads as one — a magnifier at the head of a
        // text field is the search affordance on every platform — and now that
        // pressing it is the only thing that runs a search, leaving it
        // decorative would mean the keyboard's own key was the single way in.
        Icon(
            Icons.Rounded.Search,
            contentDescription = stringResource(Res.string.search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = query.isNotBlank(), onClick = submit)
                .padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // A narrow field (the phone's, beside the account photo)
                    // must cut the hint short, not wrap it out of the pill.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        // Emptying the field is also how the recent searches are got back to,
        // so it needs to be one tap rather than a held backspace.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        onQueryChange("")
                        // Clearing is an edit, not a dismissal: leave the user
                        // ready to immediately type their next search.
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Voice input is the last thing in the pill, after the clear button.
        // At the trailing edge it stays under the thumb while typing adds the
        // clear button to its left, rather than sliding away from it.
        if (onVoiceSearch != null) {
            Spacer(Modifier.width(4.dp))
            val voiceDescription = stringResource(Res.string.search_voice)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onVoiceSearch)
                    .semantics { contentDescription = voiceDescription },
                contentAlignment = Alignment.Center,
            ) {
                VoiceSearchBars(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Four rounded bars rather than a microphone: the same mark the recognition
 * sheet listens with, so the search field's voice action reads as "listen to
 * this song" instead of Android's text dictation.
 */
@Composable
private fun VoiceSearchBars(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val heights = floatArrayOf(0.46f, 0.78f, 1f, 0.62f)
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * (heights.size - 1)) / heights.size
        val radius = CornerRadius(barWidth / 2f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = tint,
                topLeft = Offset(
                    x = index * (barWidth + gap),
                    y = (size.height - barHeight) / 2f,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
