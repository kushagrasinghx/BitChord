package com.music.bitchord.widget

import android.content.Intent
import android.graphics.drawable.Icon
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
        startActivityAndCollapse(
            Intent(this, RecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(RecognitionActivity.EXTRA_SHOW_INTERFACE, true)
            },
        )
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
