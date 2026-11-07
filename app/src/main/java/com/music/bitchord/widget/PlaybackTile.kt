package com.music.bitchord.widget

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile: plays or pauses whatever BitChord last had queued,
 * streamed or local, without opening the app.
 *
 * A tap is the widget's own play button — [MediaWidgetActions.ACTION_TOGGLE] —
 * so it brings up the restored queue exactly the way the widget does; see
 * [MediaWidgetActions] for why binding, not starting, the service is what makes
 * that work from outside the app. The tile draws the widget's snapshot too,
 * and follows it while the panel is open: the service saves a new one on every
 * play, pause and track change.
 */
class PlaybackTile : TileService() {

    private val redraw = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> draw() }

    override fun onStartListening() {
        MediaWidgetSnapshot.prefs(this).registerOnSharedPreferenceChangeListener(redraw)
        draw()
    }

    override fun onStopListening() {
        MediaWidgetSnapshot.prefs(this).unregisterOnSharedPreferenceChangeListener(redraw)
    }

    override fun onClick() {
        sendBroadcast(
            Intent(this, MediaWidgetActions::class.java).setAction(MediaWidgetActions.ACTION_TOGGLE),
        )
    }

    private fun draw() {
        val tile = qsTile ?: return
        val snapshot = MediaWidgetSnapshot.load(this)
        tile.state = if (snapshot.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = snapshot.title.takeIf { snapshot.hasTrack }
        }
        tile.updateTile()
    }
}
