package com.music.bitchord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.PlayerDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The Quick Play home-screen widget.
 *
 * Provides a YouTube Music-style home screen interface:
 *  - Top section: Current or last-played track with artwork, title, artist,
 *    and instant background playback controls (like/favorite, play/pause toggle, next).
 *  - Bottom section: A 2×4 grid of Quick Picks discovery and recent tracks.
 *    Tapping any item immediately launches playback of that song.
 */
class QuickPlayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        renderAsync(context, intArrayOf(id))
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                render(app, ids)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    companion object {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val SLOT_IDS = intArrayOf(
            R.id.quick_pick_0,
            R.id.quick_pick_1,
            R.id.quick_pick_2,
            R.id.quick_pick_3,
            R.id.quick_pick_4,
            R.id.quick_pick_5,
            R.id.quick_pick_6,
            R.id.quick_pick_7,
        )

        private val ART_IDS = intArrayOf(
            R.id.quick_pick_0_art,
            R.id.quick_pick_1_art,
            R.id.quick_pick_2_art,
            R.id.quick_pick_3_art,
            R.id.quick_pick_4_art,
            R.id.quick_pick_5_art,
            R.id.quick_pick_6_art,
            R.id.quick_pick_7_art,
        )

        private val TITLE_IDS = intArrayOf(
            R.id.quick_pick_0_title,
            R.id.quick_pick_1_title,
            R.id.quick_pick_2_title,
            R.id.quick_pick_3_title,
            R.id.quick_pick_4_title,
            R.id.quick_pick_5_title,
            R.id.quick_pick_6_title,
            R.id.quick_pick_7_title,
        )

        private const val REQUEST_OPEN_PLAYER = 400
        private const val REQUEST_QUICK_PLAY_BASE = 500

        /**
         * Redraws every placed Quick Play widget. Called when playback state,
         * favorites, or recommended tracks change.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return@launch
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(app, QuickPlayWidget::class.java))
                }.getOrNull()
                if (ids == null || ids.isEmpty()) return@launch
                render(app, ids)
            }
        }

        private suspend fun render(context: Context, ids: IntArray) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val snapshot = MediaWidgetSnapshot.load(context)
            var quickPicks = QuickPlayStore.load(context)

            // If store is empty, attempt a fast lazy load from online or offline sources
            if (quickPicks.isEmpty()) {
                val fetched = runCatching {
                    withTimeoutOrNull(2500L) {
                        YtMusicRepository.quickPicks().getOrNull()
                    } ?: withTimeoutOrNull(1500L) {
                        YtMusicRepository.recents().getOrNull()
                    }
                }.getOrNull()

                val fallbackSongs = fetched ?: OfflinePlaylists.all.value.firstOrNull { it.isLiked }?.songs
                if (!fallbackSongs.isNullOrEmpty()) {
                    QuickPlayStore.saveSongs(context, fallbackSongs)
                    quickPicks = QuickPlayStore.load(context)
                }
            }

            val density = context.resources.displayMetrics.density
            val topArtPx = (52 * density).roundToInt()
            val topCornerRadiusPx = 12 * density
            val itemArtPx = (58 * density).roundToInt()
            val itemCornerRadiusPx = 12 * density

            for (id in ids) {
                val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val heightDp = options?.getInt(
                    if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                )?.takeIf { it > 0 } ?: 180

                val views = RemoteViews(context.packageName, R.layout.widget_quick_play)

                // 1. Setup Top Section
                if (snapshot.hasTrack) {
                    views.setTextViewText(R.id.widget_title, snapshot.title)
                    views.setTextViewText(R.id.widget_artist, snapshot.artist)
                    views.setImageViewResource(
                        R.id.widget_toggle,
                        if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    )
                    views.setContentDescription(
                        R.id.widget_toggle,
                        context.getString(if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play),
                    )
                    views.setImageViewResource(
                        R.id.widget_like,
                        if (snapshot.isLiked) R.drawable.ic_widget_star_filled else R.drawable.ic_widget_star,
                    )

                    val topArt = snapshot.artworkUrl?.let {
                        MediaWidgetArt.squircle(context, it, topArtPx, topCornerRadiusPx, snapshot.mediaId ?: it)
                    }
                    if (topArt != null) {
                        views.setImageViewBitmap(R.id.widget_art, topArt)
                    } else {
                        views.setImageViewResource(R.id.widget_art, R.drawable.widget_item_placeholder)
                    }
                } else {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_quick_play_label))
                    views.setImageViewResource(R.id.widget_toggle, R.drawable.ic_widget_play)
                    views.setImageViewResource(R.id.widget_like, R.drawable.ic_widget_star)
                    views.setImageViewResource(R.id.widget_art, R.drawable.widget_item_placeholder)
                }

                // Top section click actions
                val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_PLAYER,
                    openPlayerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_top_section, pendingOpen)
                views.setOnClickPendingIntent(R.id.widget_art, pendingOpen)

                views.setOnClickPendingIntent(
                    R.id.widget_like,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_LIKE),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_toggle,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_TOGGLE),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_NEXT),
                )

                // 2. Setup Bottom Grid (Quick Picks)
                val showRow2 = heightDp >= 150
                views.setViewVisibility(R.id.widget_row_2, if (showRow2) View.VISIBLE else View.GONE)
                val maxVisibleItems = if (showRow2) 8 else 4

                for (i in 0 until 8) {
                    val slotId = SLOT_IDS[i]
                    val artId = ART_IDS[i]
                    val titleId = TITLE_IDS[i]

                    if (i < maxVisibleItems && i < quickPicks.size) {
                        val track = quickPicks[i]
                        views.setViewVisibility(slotId, View.VISIBLE)
                        views.setTextViewText(titleId, track.title)
                        views.setContentDescription(artId, "${track.title} • ${track.artist}")

                        val artBitmap = track.thumbnailUrl?.let {
                            MediaWidgetArt.squircle(context, it, itemArtPx, itemCornerRadiusPx, track.videoId)
                        }
                        if (artBitmap != null) {
                            views.setImageViewBitmap(artId, artBitmap)
                        } else {
                            views.setImageViewResource(artId, R.drawable.widget_item_placeholder)
                        }

                        val playIntent = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true)
                            putExtra(MusicLink.EXTRA_QUICK_PLAY_ID, track.videoId)
                            putExtra(MusicLink.EXTRA_QUICK_PLAY_TITLE, track.title)
                            putExtra(MusicLink.EXTRA_QUICK_PLAY_ARTIST, track.artist)
                            putExtra(MusicLink.EXTRA_QUICK_PLAY_THUMBNAIL, track.thumbnailUrl)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingPlay = PendingIntent.getActivity(
                            context,
                            REQUEST_QUICK_PLAY_BASE + i,
                            playIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        views.setOnClickPendingIntent(slotId, pendingPlay)
                    } else {
                        views.setViewVisibility(slotId, View.INVISIBLE)
                    }
                }

                runCatching { manager.updateAppWidget(id, views) }
            }
        }
    }
}
