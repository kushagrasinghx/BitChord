package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.ui.components.ReleaseNotes

/**
 * The changelog, as a full page: pick a release from the version picker, read
 * its patch notes below.
 */
@Composable
fun ChangelogScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var entries by remember { mutableStateOf<List<AppUpdateChecker.ReleaseEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        failed = false
        entries = runCatching { AppUpdateChecker.fetchChangelog() }.getOrNull()
        if (entries == null) failed = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Text(
            text = "Changelog",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        when {
            entries != null -> {
                val list = entries!!
                val current = list.getOrNull(selected) ?: list.firstOrNull()

                // The version picker. A plain row opening a menu rather than an
                // exposed-dropdown widget — it reads calmer against the page's
                // large title, which is what this screen leads with.
                Box(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = current?.let { "v${it.version}" } ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = current?.date ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "Pick a version",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    ) {
                        list.forEachIndexed { index, entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "v${entry.version}" + (entry.date?.let { "  ·  $it" } ?: ""),
                                        color = if (index == selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    selected = index
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }

                if (current != null) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        ReleaseNotes(current.notes, Modifier.fillMaxWidth())
                    }
                } else {
                    Box(Modifier.weight(1f))
                }
            }
            failed -> Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "Couldn't load the changelog.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Tap to retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { attempt++ },
                )
            }
            else -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.width(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
