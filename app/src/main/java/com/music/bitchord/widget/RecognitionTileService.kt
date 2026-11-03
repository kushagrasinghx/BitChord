package com.music.bitchord.widget

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.music.bitchord.R

/** Quick Settings entry point for the same one-tap recognition flow as the widget. */
class RecognitionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, RecognitionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(RecognitionActivity.EXTRA_SHOW_INTERFACE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        qsTile?.apply {
            icon = Icon.createWithResource(this@RecognitionTileService, R.drawable.ic_qs_recognition)
            label = getString(R.string.quick_tile_recognition_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
