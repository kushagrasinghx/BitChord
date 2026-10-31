package com.music.bitchord.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings

/**
 * The settings rows that may be taken off the Settings screen.
 *
 * [key] is what gets stored, in `AppSettings.hiddenSettings`; it is the name of
 * the row's string resource because that name is already stable, unique and
 * says what the row is. [title] and [group] are repeated here rather than read
 * off the screen so that this page can list an option that is currently hidden
 * — the one thing the Settings screen cannot do.
 *
 * Only switches are listed. A row that opens a page or a dialog is a door, and
 * a door taken off the wall leaves whatever was behind it unreachable; a switch
 * left off this list still does what it was last told.
 *
 * The order is the order the rows have in Settings, group by group, so the two
 * screens read as the same list with some of it turned off.
 */
internal object HideableSettings {
    data class Item(val key: String, @StringRes val title: Int, @StringRes val group: Int)

    val items: List<Item> = listOf(
        Item("dolby_atmos", R.string.dolby_atmos, R.string.audio_quality),
        Item("download_wifi_only", R.string.download_wifi_only, R.string.downloads),
        Item("export_compatible_downloads", R.string.export_compatible_downloads, R.string.downloads),
        Item("prefer_music_only", R.string.prefer_music_only, R.string.playback),
        Item("prefer_usb_dac", R.string.prefer_usb_dac, R.string.playback),
        Item("loudness_normalization", R.string.loudness_normalization, R.string.playback),
        Item("loudness_off_on_speaker", R.string.loudness_off_on_speaker, R.string.playback),
        Item("automix", R.string.automix, R.string.playback),
        Item("skip_silence", R.string.skip_silence, R.string.playback),
        Item("spatial_audio", R.string.spatial_audio, R.string.playback),
        Item("reduce_animation", R.string.reduce_animation, R.string.appearance),
        Item("reduce_dynamic_blur", R.string.reduce_dynamic_blur, R.string.appearance),
        Item("liquid_glass", R.string.liquid_glass, R.string.appearance),
        Item("full_screen_cover_art", R.string.full_screen_cover_art, R.string.appearance),
        Item("legacy_mesh_gradient", R.string.legacy_mesh_gradient, R.string.appearance),
        Item("animated_cover_art", R.string.animated_cover_art, R.string.appearance),
        Item("animated_cover_cellular", R.string.animated_cover_cellular, R.string.appearance),
        Item("artist_signatures", R.string.artist_signatures, R.string.appearance),
        Item("synced_lyrics", R.string.synced_lyrics, R.string.appearance),
        Item("blur_unfocused_lyrics", R.string.blur_unfocused_lyrics, R.string.appearance),
        Item("high_performance_mode", R.string.high_performance_mode, R.string.performance),
        Item("filter_non_music_audio", R.string.filter_non_music_audio, R.string.local_music),
        Item("show_cache_folder", R.string.show_cache_folder, R.string.storage),
        Item("work_out_genres", R.string.work_out_genres, R.string.your_data),
        Item("play_next_on_swipe", R.string.play_next_on_swipe, R.string.miscellaneous),
        Item("dont_repeat_songs", R.string.dont_repeat_songs, R.string.miscellaneous),
        Item("stop_music_on_close", R.string.stop_music_on_close, R.string.miscellaneous),
        Item("hide_volume_bar", R.string.hide_volume_bar, R.string.miscellaneous),
        Item("hide_status_bar_in_player", R.string.hide_status_bar_in_player, R.string.miscellaneous),
        Item("show_queue_history", R.string.show_queue_history, R.string.miscellaneous),
        Item("hide_song_status", R.string.hide_song_status, R.string.miscellaneous),
        Item("smart_version_alignment", R.string.smart_version_alignment, R.string.advanced_options),
        Item("show_nerd_stats", R.string.show_nerd_stats, R.string.advanced_options),
    )
}

/**
 * The page that decides which of [HideableSettings] Settings gets to show.
 *
 * Its switches are not the settings themselves but the rows they live in, so
 * every one of them is on until it is turned off — and turning one off moves
 * nothing here, since a hidden row has to stay listable for the sake of the day
 * it is wanted back.
 */
@Composable
internal fun HiddenSettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val hidden by AppSettings.hiddenSettings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.hide_options),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        HideableSettings.items
            .groupBy { it.group }
            .toList()
            .forEachIndexed { groupIndex, (group, groupItems) ->
                SettingsGroup(
                    // Said once, at the top of the list rather than under every
                    // card: what it explains is the page, not one group of it.
                    header = stringResource(group),
                    footer = if (groupIndex == 0) stringResource(R.string.hide_options_footer) else null,
                ) {
                    groupItems.forEachIndexed { index, item ->
                        if (index > 0) RowDivider()
                        val visible = item.key !in hidden
                        SettingsRow(
                            title = stringResource(item.title),
                            trailing = {
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { AppSettings.setSettingHidden(item.key, !it) },
                                    colors = sleeveSwitchColors(),
                                )
                            },
                            onClick = { AppSettings.setSettingHidden(item.key, visible) },
                        )
                    }
                }
            }
    }
}
