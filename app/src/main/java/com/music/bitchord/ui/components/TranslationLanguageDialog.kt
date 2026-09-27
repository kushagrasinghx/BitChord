package com.music.bitchord.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.TRANSLATION_LANGUAGES
import com.music.bitchord.data.lyrics.translationLanguageName
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.Locale

/** How much of the screen the list may take before it scrolls inside the card. */
private val LIST_MAX_HEIGHT = 340.dp

/**
 * Which language the lyrics translate button translates into.
 *
 * The same frosted alert as [AppLanguageDialog], with two differences that a
 * hundred and thirty rows force. It scrolls, because the app's alerts are laid
 * out as a plain Column and this list is longer than any phone; and it has a
 * filter field, because scrolling to Vietnamese is not a reasonable way to pick
 * Vietnamese. Selecting a row applies it and closes, same as the app-language
 * picker — one active language, nothing to confirm.
 *
 * The first row is the default and is deliberately not a language: blank means
 * "follow the app", which is a different answer from naming today's app
 * language and having it stick when the app changes.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TranslationLanguageDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val selected by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)
    val appLocale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    var query by remember { mutableStateOf("") }

    // Names are resolved once per locale rather than per row per frame: the
    // list is long, the platform lookup is not free, and the filter below reads
    // every one of them on every keystroke.
    val named = remember(appLocale) {
        TRANSLATION_LANGUAGES.map { it to translationLanguageName(it.code, appLocale) }
    }
    val shown = remember(named, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            named
        } else {
            // Matched against the English name as well as the localised one, so
            // someone reading the app in Hindi can still type "Japanese".
            named.filter { (language, name) ->
                name.contains(needle, ignoreCase = true) ||
                    language.fallbackName.contains(needle, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.translation_language),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.translation_language_description),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            AlertRule()
            SearchField(query = query, onQueryChange = { query = it })
            AlertRule()

            LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
                // Only with an empty field: it is the default rather than a
                // language, so it has no name to match and filtering it in on a
                // near-miss would be noise.
                if (query.isBlank()) {
                    item {
                        LanguageChoiceRow(
                            label = stringResource(R.string.translation_language_app_default),
                            detail = stringResource(R.string.translation_language_subtitle),
                            selected = selected.isBlank(),
                            onClick = {
                                AppSettings.setTranslationLanguage("")
                                onDismiss()
                            },
                        )
                    }
                }
                items(shown, key = { it.first.code }) { (language, name) ->
                    AlertRule()
                    LanguageChoiceRow(
                        label = name,
                        // The English name under the localised one, the way a
                        // source's detail sits under its label. Reading the app
                        // in German, "Japanisch" is the row you want but
                        // "Japanese" is what you were going to type.
                        detail = language.fallbackName.takeIf { !it.equals(name, ignoreCase = true) }
                            ?: language.code,
                        selected = selected.equals(language.code, ignoreCase = true),
                        onClick = {
                            AppSettings.setTranslationLanguage(language.code)
                            onDismiss()
                        },
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_languages_match),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                        )
                    }
                }
            }

            // Pinned under the list rather than scrolling away with it: on a
            // list this long the way out has to stay where it was put.
            AlertRule()
            AlertAction(
                label = stringResource(R.string.done),
                emphasised = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_languages),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Label over detail, checkmark on the right — a source row from [LyricsSourcesDialog], without the handle. */
@Composable
private fun LanguageChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
