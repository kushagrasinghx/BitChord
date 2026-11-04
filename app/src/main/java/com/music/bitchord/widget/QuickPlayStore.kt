package com.music.bitchord.widget

import android.content.Context
import com.music.bitchord.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * A lightweight track descriptor for the Quick Play widget.
 */
data class QuickPickTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

/**
 * Persisted cache for the Quick Play home-screen widget.
 *
 * Stores up to 8 recommended / recent discovery tracks so the widget can
 * display covers and respond to clicks instantly without waiting on network
 * or service binding.
 */
object QuickPlayStore {

    private const val PREFS_NAME = "bitchord_quick_play"
    private const val KEY_TRACKS = "tracks"

    fun save(context: Context, tracks: List<QuickPickTrack>) {
        if (tracks.isEmpty()) return
        val array = JSONArray()
        for (track in tracks.take(MAX_ITEMS)) {
            val obj = JSONObject().apply {
                put("id", track.videoId)
                put("title", track.title)
                put("artist", track.artist)
                put("thumb", track.thumbnailUrl ?: "")
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACKS, array.toString())
            .apply()
    }

    fun saveSongs(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        val tracks = songs.map {
            QuickPickTrack(
                videoId = it.videoId,
                title = it.title,
                artist = it.artist,
                thumbnailUrl = it.thumbnailUrl,
            )
        }
        save(context, tracks)
    }

    fun load(context: Context): List<QuickPickTrack> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<QuickPickTrack>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                if (id.isNotBlank()) {
                    list.add(
                        QuickPickTrack(
                            videoId = id,
                            title = obj.optString("title", ""),
                            artist = obj.optString("artist", ""),
                            thumbnailUrl = obj.optString("thumb", "").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    const val MAX_ITEMS = 8
}
