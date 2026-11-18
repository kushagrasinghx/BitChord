package com.music.bitchord.mcp

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.DownloadState
import com.music.bitchord.playback.SleepTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Permission checks belong to the MCP dispatcher, before this entry point. */
object McpUtilities {
    internal val fields = mapOf(
        "recommendations" to setOf("video_id", "limit"),
        "listening_summary" to setOf("period", "limit"),
        "sleep_timer_get" to emptySet(),
        "sleep_timer_set" to setOf("minutes", "after_track"),
        "sleep_timer_cancel" to emptySet(),
        "downloads_list" to setOf("limit"),
        "download_status" to setOf("video_id"),
        "download_enqueue" to setOf("video_id"),
        "download_cancel" to setOf("video_id"),
    )

    internal fun validate(name: String, args: JsonObject) {
        val allowed = fields[name] ?: throw IllegalArgumentException("Unknown utility tool")
        require(args.keys.all { it in allowed }) { "Unexpected argument" }
        args.forEach { (key, value) ->
            val p = value as? JsonPrimitive ?: throw IllegalArgumentException("Invalid $key type")
            when (key) {
                "video_id" -> require(p.isString && Regex("[A-Za-z0-9_-]{11}").matches(p.content)) { "Expected an 11-character catalogue video ID" }
                "period" -> require(p.isString && p.content in periods) { "period must be this_week, this_month, this_year or all_time" }
                "after_track" -> require(!p.isString && p.booleanOrNull != null) { "after_track must be boolean" }
                "limit", "minutes" -> {
                    require(!p.isString && Regex("[1-9][0-9]*").matches(p.content)) { "$key must be a positive integer" }
                    require(p.intOrNull in 1..(if (key == "limit") 50 else 720)) { "$key outside allowed range" }
                }
            }
        }
        if (name.startsWith("download_")) require("video_id" in args) { "video_id is required" }
        if (name == "listening_summary") require("period" in args) { "period is required" }
        if (name == "sleep_timer_set") {
            val after = args["after_track"]?.jsonPrimitive?.booleanOrNull == true
            require(after.xor("minutes" in args)) { "Supply minutes or after_track=true, exclusively" }
        }
    }

    private val periods = mapOf(
        "this_week" to ReplayPeriod.THIS_WEEK, "this_month" to ReplayPeriod.THIS_MONTH,
        "this_year" to ReplayPeriod.THIS_YEAR, "all_time" to ReplayPeriod.ALL_TIME,
    )

    suspend fun call(context: Context, name: String, args: JsonObject): JsonObject {
        if (name !in fields) return error("unknown_tool", "Unknown utility tool")
        try { validate(name, args) }
        catch (e: IllegalArgumentException) { return error("invalid_arguments", e.message ?: "Invalid arguments") }
        return try {
            withContext(Dispatchers.IO) {
                val limit = args["limit"]?.jsonPrimitive?.int ?: 20
                val id = args["video_id"]?.jsonPrimitive?.content
                when (name) {
                    "recommendations" -> {
                        val songs = (if (id == null) YtMusicRepository.quickPicks() else YtMusicRepository.radio(id)).getOrThrow()
                        buildJsonObject { put("songs", JsonArray(songs.distinctBy { it.videoId }.take(limit).map(::song))) }
                    }
                    "listening_summary" -> {
                        val period = args.getValue("period").jsonPrimitive.content
                        val summary = ListeningStats.summary(periods.getValue(period))
                        buildJsonObject {
                            put("period", period); put("total_ms", summary.totalMs); put("total_plays", summary.totalPlays)
                            put("distinct_songs", summary.distinctSongs); put("distinct_artists", summary.distinctArtists)
                            put("songs", JsonArray(summary.songs.take(limit).map { ranked -> buildJsonObject {
                                put("song", song(ranked.song)); put("played_ms", ranked.ms); put("plays", ranked.plays)
                            } }))
                        }
                    }
                    "sleep_timer_get", "sleep_timer_set", "sleep_timer_cancel" -> withContext(Dispatchers.Main.immediate) {
                        when (name) {
                            "sleep_timer_set" -> if (args["after_track"]?.jsonPrimitive?.booleanOrNull == true) SleepTimer.startAfterTrack()
                                else SleepTimer.start(args.getValue("minutes").jsonPrimitive.int)
                            "sleep_timer_cancel" -> SleepTimer.cancel()
                        }
                        timer()
                    }
                    "downloads_list" -> {
                        val ids = (Downloads.active.value.keys + Downloads.saved.value.keys).asSequence()
                            .filter { Regex("[A-Za-z0-9_-]{11}").matches(it) }.take(limit).toList()
                        buildJsonObject { put("downloads", JsonArray(ids.map { status(context, it) })) }
                    }
                    "download_status" -> status(context, id!!)
                    "download_enqueue" -> {
                        val videoId = id!!
                        if (Downloads.savedUri(context, videoId) == null) {
                            val track = YtMusicRepository.trackLinks(videoId).getOrThrow()
                            check(track.videoId == videoId) { "Catalogue identity mismatch" }
                            Downloads.enqueue(context, track)
                        }
                        status(context, videoId)
                    }
                    "download_cancel" -> {
                        Downloads.cancel(id!!)
                        buildJsonObject { put("video_id", id); put("cancel_requested", true); put("download", status(context, id)) }
                    }
                    else -> error("unknown_tool", "Unknown utility tool")
                }
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { error("operation_failed", "Utility operation could not be completed") }
    }

    private fun song(value: Song) = buildJsonObject {
        put("video_id", value.videoId); put("title", value.title.take(500)); put("artist", value.artist.take(500))
        value.durationText?.let { put("duration", it.take(50)) }
    }

    private fun timer() = buildJsonObject {
        put("running", SleepTimer.isRunning); put("after_track", SleepTimer.afterTrack.value)
        put("minutes", SleepTimer.minutes.value?.let(::JsonPrimitive) ?: JsonNull)
        put("remaining_ms", SleepTimer.remainingMs()?.let(::JsonPrimitive) ?: JsonNull)
    }

    private suspend fun status(context: Context, id: String): JsonObject {
        val completed = Downloads.savedUri(context, id) != null
        val active = Downloads.active.value[id]
        return buildJsonObject {
            put("video_id", id)
            put("status", when {
                completed -> "completed"
                active is DownloadState.Queued -> "queued"
                active is DownloadState.Running -> "running"
                active is DownloadState.Failed -> "failed"
                else -> "not_downloaded"
            })
            if (!completed && active is DownloadState.Running && active.fraction.isFinite())
                put("progress", active.fraction.coerceIn(0f, 1f))
        }
    }

    private fun error(code: String, message: String) = buildJsonObject {
        put("error", buildJsonObject { put("code", code); put("message", message) })
    }
}
