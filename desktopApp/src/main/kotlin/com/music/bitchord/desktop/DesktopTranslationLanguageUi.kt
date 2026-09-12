package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Which language the translate button translates into.
 *
 * Borrows the lyrics-sources card — label over detail, a checkmark, pinned actions — with the two
 * things a hundred and thirty rows force: it scrolls, and it has a filter field, because scrolling
 * to Vietnamese is not a way to pick Vietnamese.
 */
@Composable
internal fun DesktopTranslationLanguageDialog(onDismiss: () -> Unit) {
    val stored by DesktopTranslationSetting.language.collectAsState()
    val locale = remember { Locale.forLanguageTag(DesktopStrings.resolvedTag().ifBlank { "en" }) }
    var filter by remember { mutableStateOf("") }

    // Named in the reader's own language, and sorted that way too — the table's own order is
    // alphabetical by English name, which is not alphabetical once localised.
    val named = remember(locale) {
        DESKTOP_TRANSLATION_LANGUAGES
            .map { it.code to desktopTranslationLanguageName(it.code, locale) }
            .sortedBy { it.second.lowercase(locale) }
    }
    val shown = remember(named, filter) {
        val needle = filter.trim().lowercase(locale)
        if (needle.isBlank()) named else named.filter { it.second.lowercase(locale).contains(needle) }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                DesktopStrings["translation_language", "Translation language"],
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                DesktopStrings["d_what_the_translate_button_translates_into", "What the translate button on the lyrics translates into"],
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopSecondary,
            )
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            DesktopSearchField(
                query = filter,
                onQueryChange = { filter = it },
                onSearch = {},
                placeholder = DesktopStrings["d_filter_languages", "Filter languages"],
            )
        }

        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                LanguageRow(
                    name = DesktopStrings["d_follow_the_app_language", "Follow the app language"],
                    detail = desktopTranslationLanguageName(DesktopStrings.resolvedTag(), locale),
                    selected = stored.isBlank(),
                    onClick = { DesktopTranslationSetting.set("") },
                )
            }
            items(shown, key = { it.first }) { (code, name) ->
                LanguageRow(
                    name = name,
                    detail = code,
                    selected = stored.equals(code, ignoreCase = true),
                    onClick = { DesktopTranslationSetting.set(code) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = Color.White) }
        }
    }
}

@Composable
private fun LanguageRow(name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = Color.White,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = DesktopAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
