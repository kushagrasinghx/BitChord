package com.music.bitchord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.playback.PlayerDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The home-screen Synced Lyrics widget: displays the active synchronized lyric line
 * with high readability over the current track's blurred cover art, paired with
 * playback transport controls.
 */
class LyricsWidget : AppWidgetProvider() {

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

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) lastArt.remove(id)
    }

    override fun onDisabled(context: Context) {
        lastArt.clear()
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                render(app, ids, SystemClock.elapsedRealtime() + BROADCAST_BUDGET_MS)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    companion object {

        private const val BROADCAST_BUDGET_MS = 9_000L
        private const val RENDER_TIMEOUT_MS = 6_000L
        private const val FALLBACK_WIDTH_DP = 250
        private const val FALLBACK_HEIGHT_DP = 110
        private const val MAX_BITMAP_PX = 1_200f
        private const val REQUEST_OPEN_PLAYER = 15

        private val lastArt = ConcurrentHashMap<Int, Bitmap>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return@launch
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(app, LyricsWidget::class.java))
                }.getOrNull()
                if (ids == null || ids.isEmpty()) return@launch
                render(app, ids, deadline = null)
            }
        }

        private suspend fun render(
            context: Context,
            ids: IntArray,
            deadline: Long?,
        ) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val snapshot = MediaWidgetSnapshot.load(context)
            val key = snapshot.artworkUrl ?: "no-artwork"
            for (id in ids) {
                val budget = when (deadline) {
                    null -> RENDER_TIMEOUT_MS
                    else -> minOf(RENDER_TIMEOUT_MS, deadline - SystemClock.elapsedRealtime())
                }
                if (budget <= 0L) return
                withTimeoutOrNull(budget) {
                    renderInstance(context, manager, id, snapshot, key)
                }
            }
        }

        private suspend fun renderInstance(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            snapshot: MediaWidgetSnapshot,
            key: String,
        ) {
            val size = measure(context, manager, id)
            val cached = MediaWidgetArt.peek(key, size.widthPx, size.heightPx, size.bandPx)
            val currentArt = cached ?: lastArt[id]
            if (currentArt != null) {
                manager.push(id, buildViews(context, snapshot, currentArt))
            }

            val art = cached ?: MediaWidgetArt.render(
                context = context,
                artworkUrl = snapshot.artworkUrl,
                widthPx = size.widthPx,
                heightPx = size.heightPx,
                bandPx = size.bandPx,
                key = key,
                cornerRadiusPx = size.cornerRadiusPx,
            )
            lastArt[id] = art
            manager.push(id, buildViews(context, snapshot, art))
        }

        private fun buildViews(
            context: Context,
            snapshot: MediaWidgetSnapshot,
            art: Bitmap?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
            art?.let { views.setImageViewBitmap(R.id.widget_art, it) }

            if (snapshot.hasTrack) {
                views.setTextViewText(R.id.widget_title, snapshot.title)
                views.setTextViewText(R.id.widget_artist, snapshot.artist)

                val lyricText = snapshot.currentLyric?.takeIf { it.isNotBlank() }
                    ?: if (snapshot.isPlaying) {
                        context.getString(R.string.widget_no_lyrics)
                    } else {
                        snapshot.artist
                    }
                views.setTextViewText(R.id.widget_lyric_line, lyricText)
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_nothing_played))
                views.setTextViewText(R.id.widget_artist, "")
                views.setTextViewText(R.id.widget_lyric_line, context.getString(R.string.widget_no_lyrics))
            }

            views.setImageViewResource(
                R.id.widget_toggle,
                if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play),
            )

            val open = openPlayer(context)
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (snapshot.hasTrack) {
                views.setImageAlpha(R.id.widget_toggle, 255)
                views.setImageAlpha(R.id.widget_previous, if (snapshot.hasPrevious) 255 else 90)
                views.setImageAlpha(R.id.widget_next, if (snapshot.hasNext) 255 else 90)

                views.setOnClickPendingIntent(
                    R.id.widget_toggle,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_TOGGLE),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_previous,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_PREVIOUS),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_NEXT),
                )
            } else {
                views.setImageAlpha(R.id.widget_toggle, 90)
                views.setImageAlpha(R.id.widget_previous, 90)
                views.setImageAlpha(R.id.widget_next, 90)
                views.setOnClickPendingIntent(R.id.widget_toggle, open)
                views.setOnClickPendingIntent(R.id.widget_previous, open)
                views.setOnClickPendingIntent(R.id.widget_next, open)
            }
            return views
        }

        private fun RemoteViews.setImageAlpha(viewId: Int, alpha: Int) =
            setInt(viewId, "setImageAlpha", alpha)

        private fun openPlayer(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true)
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_PLAYER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun AppWidgetManager.push(id: Int, views: RemoteViews) {
            runCatching { updateAppWidget(id, views) }
        }

        private class WidgetSize(
            val widthPx: Int,
            val heightPx: Int,
            val bandPx: Int,
            val cornerRadiusPx: Float,
        )

        private fun measure(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
        ): WidgetSize {
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = options?.getInt(
                if (landscape) {
                    AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                } else {
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                },
            )?.takeIf { it > 0 } ?: FALLBACK_WIDTH_DP
            val heightDp = options?.getInt(
                if (landscape) {
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                } else {
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                },
            )?.takeIf { it > 0 } ?: FALLBACK_HEIGHT_DP

            val resources = context.resources
            val density = resources.displayMetrics.density
            val rawWidth = widthDp * density
            val rawHeight = heightDp * density
            val longest = maxOf(rawWidth, rawHeight)
            val scale = if (longest > MAX_BITMAP_PX) MAX_BITMAP_PX / longest else 1f
            val band = resources.getDimensionPixelSize(R.dimen.widget_band_wide)
            return WidgetSize(
                widthPx = (rawWidth * scale).roundToInt().coerceAtLeast(1),
                heightPx = (rawHeight * scale).roundToInt().coerceAtLeast(1),
                bandPx = (band * scale).roundToInt().coerceAtLeast(1),
                cornerRadiusPx = resources.getDimension(R.dimen.widget_corner_radius) * scale,
            )
        }
    }
}
