package com.music.bitchord.sync

/** V1 syncable BitChord preferences and the SharedPreferences type each uses. */
object SyncPreferencePolicy {
    private val preferenceTypes = mapOf(
        "audio_quality_wifi" to STRING,
        "audio_quality_cellular" to STRING,
        "upgrade_length_slack_seconds" to INT,
        "audio_quality_download" to STRING,
        "crossfade_seconds" to INT,
        "smart_fade_enabled" to BOOLEAN,
        "sleep_timer_fade_out" to BOOLEAN,
        "automix_performance_mode" to STRING,
        "skip_silence" to BOOLEAN,
        "loudness_normalization" to BOOLEAN,
        "direct_source_stream" to BOOLEAN,
        "youtube_premium_audio" to BOOLEAN,
        "equalizer_enabled" to BOOLEAN,
        "equalizer_mode" to STRING,
        "equalizer_tone_x" to INT,
        "equalizer_tone_y" to INT,
        "equalizer_focused" to BOOLEAN,
        "equalizer_balance" to FLOAT,
        "equalizer_bands" to STRING,
        "playback_speed" to FLOAT,
        "theme_mode" to STRING,
        "app_font" to STRING,
        "autoplay" to BOOLEAN,
        "shuffle_enabled" to BOOLEAN,
        "repeat_mode" to INT,
        "show_nerd_stats" to BOOLEAN,
        "show_listen_together_button" to BOOLEAN,
        "reduce_animation" to BOOLEAN,
        "hide_volume_bar" to BOOLEAN,
        "hide_status_bar_in_player" to BOOLEAN,
        "hide_song_status" to BOOLEAN,
        "hide_lyrics_preview" to BOOLEAN,
        "lyrics_card_logo" to BOOLEAN,
        "show_queue_history" to BOOLEAN,
        "swipe_to_play_next" to BOOLEAN,
        "dont_repeat_suggestions" to BOOLEAN,
        "prefer_music_only" to BOOLEAN,
        "hide_recents" to BOOLEAN,
        "hide_home_videos" to BOOLEAN,
        "hide_top_picks" to BOOLEAN,
        "hide_home_moods" to BOOLEAN,
        "square_video_thumbnails" to BOOLEAN,
        "smart_version_alignment" to BOOLEAN,
        "reduce_dynamic_blur" to BOOLEAN,
        "disable_blend_effects" to BOOLEAN,
        "liquid_glass" to BOOLEAN,
        "glass_performance" to BOOLEAN,
        "progressive_blur" to BOOLEAN,
        "lyrics_blur" to BOOLEAN,
        "lyrics_offset_ms" to INT,
        "translation_language" to STRING,
        "animated_canvas" to BOOLEAN,
        "canvas_over_cellular" to BOOLEAN,
        "disable_spotify_canvas" to BOOLEAN,
        "spotify_canvas_auto_hide" to BOOLEAN,
        "prioritize_spotify_canvas" to BOOLEAN,
        "full_bleed_artwork" to BOOLEAN,
        "full_bleed_detail_artwork" to BOOLEAN,
        "show_total_duration" to BOOLEAN,
        "player_theme" to STRING,
        "last_player_screen" to STRING,
        "synced_lyrics" to BOOLEAN,
        "lyrics_sources" to STRING,
        "lyrics_source_order" to STRING,
        "prioritize_syllable_sync" to BOOLEAN,
        "replay_genres" to BOOLEAN,
        "filter_non_music_audio" to BOOLEAN,
        "library_sort" to STRING,
        "pinned_playlists" to STRING,
    )

    /** A read-only view used by client settings UI and tests. */
    val allowedKeys: Set<String> get() = preferenceTypes.keys

    /** Export only known portable settings, preserving their exact stored type. */
    fun export(stored: Map<String, Any?>): Map<String, SyncValue> =
        stored.entries.mapNotNull { (key, raw) ->
            val expectedType = preferenceTypes[key] ?: return@mapNotNull null
            SyncValue.fromPreference(raw)
                ?.takeIf { it.type == expectedType }
                ?.let { key to it }
        }.toMap()

    /**
     * Apply a merged profile snapshot to local preferences. Tombstones remove
     * only allowlisted preference keys; unknown and device-local state survives.
     */
    fun applySnapshot(
        local: Map<String, Any?>,
        incoming: Map<String, SyncField>,
    ): Map<String, Any?> {
        val values = incoming.mapNotNull { (key, field) ->
            field.value?.let { key to it }
        }.toMap()
        val merged = merge(local, values)
        val removals = incoming.filter { (key, field) ->
            key in preferenceTypes && field.value == null
        }.keys
        return merged - removals
    }

    /** Compute a minimal write/remove set so the Android adapter never clears the file. */
    fun diff(
        local: Map<String, Any?>,
        incoming: Map<String, SyncField>,
    ): SyncPreferenceDelta {
        val after = applySnapshot(local, incoming)
        val upserts = after.entries.mapNotNull { (key, value) ->
            if (key in preferenceTypes && value != null && local[key] != value) key to value else null
        }.toMap()
        val removals = local.keys.filter { it in preferenceTypes && it !in after }.toSet()
        return SyncPreferenceDelta(upserts, removals)
    }

    /**
     * Merge a partial remote settings payload without removing unknown, secret,
     * device-local, or unmentioned local preferences. Values with an unexpected
     * type are ignored instead of being written into SharedPreferences.
     */
    fun merge(
        local: Map<String, Any?>,
        incoming: Map<String, SyncValue>,
    ): Map<String, Any?> {
        val accepted = incoming.entries.mapNotNull { (key, value) ->
            if (preferenceTypes[key] != value.type) return@mapNotNull null
            runCatching { key to value.toPreference() }.getOrNull()
        }.toMap()
        return local + accepted
    }

    private const val BOOLEAN = "bool"
    private const val INT = "int"
    private const val FLOAT = "float"
    private const val STRING = "string"
}

/** The exact SharedPreferences changes needed for a merged snapshot. */
data class SyncPreferenceDelta(
    val upserts: Map<String, Any>,
    val removals: Set<String>,
)
