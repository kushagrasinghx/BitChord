package com.music.bitchord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Bitmap
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
 * The 4×1 widget: a capsule with the cover in a circle, the track, and Like /
 * previous / play / next / Shuffle.
 *
 * Its own provider rather than a third size of [MediaWidget]: a different
 * shape with a different picture. It reads the same [MediaWidgetSnapshot],
 * shares the same [MediaWidgetActions], and draws two bitmaps through
 * [MediaWidgetArt] — the round cover, and the capsule's body, which is the cover
 * blurred to colour at the capsule's exact size.
 */
class MediaWidgetPill : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    /** A resize changes the capsule's width, and its body is drawn to size. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        renderAsync(context, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) lastBody.remove(id)
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

        /** Redraws every placed 4×1 widget. Called from [MediaWidget.refresh]. */
        internal fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return@launch
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(app, MediaWidgetPill::class.java))
                }.getOrNull()
                if (ids == null || ids.isEmpty()) return@launch
                render(app, ids)
            }
        }

        /**
         * Pushes the controls at once when either picture still has to be drawn,
         * for the same reason [MediaWidget] does: a tap should be answered in a
         * frame, not after a cover fetch. The disc is one size and shared; the
         * body is per instance, because each can be a different width.
         */
        private suspend fun render(context: Context, ids: IntArray) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val snapshot = MediaWidgetSnapshot.load(context)
            val key = snapshot.artworkUrl
            val resources = context.resources
            val discPx = resources.getDimensionPixelSize(R.dimen.widget_pill_art)
            val heightPx = resources.getDimensionPixelSize(R.dimen.widget_pill_height)
            for (id in ids) {
                val widthPx = widthPx(context, manager, id)
                val cachedDisc = key?.let { MediaWidgetArt.peekCircle(it, discPx) }
                val cachedBody = key?.let { MediaWidgetArt.peekPill(it, widthPx, heightPx) }
                if (cachedDisc == null || cachedBody == null) {
                    // With what this widget last drew, not with nothing: a
                    // RemoteViews replaces the whole tree. See MediaWidget.render.
                    manager.push(id, views(context, snapshot, cachedDisc ?: lastDisc, cachedBody ?: lastBody[id]))
                }
                val drawn = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                    val disc = cachedDisc ?: key?.let {
                        MediaWidgetArt.circle(context, snapshot.artworkUrl, discPx, it)
                    }
                    disc to (cachedBody ?: MediaWidgetArt.pill(context, snapshot.artworkUrl, widthPx, heightPx, key))
                } ?: continue
                val (disc, body) = drawn
                // A track with no cover, or one that failed, shows the
                // placeholder disc rather than the previous track's picture.
                lastDisc = disc
                lastBody[id] = body
                manager.push(id, views(context, snapshot, disc, body))
            }
        }

        /**
         * The capsule's width in pixels: the widget's width for the orientation
         * in force, as [MediaWidget.measure] reads it. The capsule is
         * match_parent, so it is exactly this wide and the body bitmap lands
         * 1:1 — its round ends stay round rather than being stretched.
         */
        private fun widthPx(context: Context, manager: AppWidgetManager, id: Int): Int {
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = options?.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            )?.takeIf { it > 0 } ?: FALLBACK_WIDTH_DP
            return (widthDp * context.resources.displayMetrics.density).roundToInt().coerceIn(1, MAX_WIDTH_PX)
        }

        private fun views(
            context: Context,
            snapshot: MediaWidgetSnapshot,
            disc: Bitmap?,
            body: Bitmap?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_media_pill)
            disc?.let { views.setImageViewBitmap(R.id.widget_art, it) }
            body?.let {
                views.setImageViewBitmap(R.id.widget_pill_bg, it)
                // The translucent stand-in capsule would show through the
                // body's own transparency and darken it unevenly.
                views.setInt(R.id.widget_pill_bg, "setBackgroundResource", 0)
            }

            views.setTextViewText(
                R.id.widget_title,
                when {
                    !snapshot.hasTrack -> context.getString(R.string.widget_nothing_played)
                    snapshot.artist.isBlank() -> snapshot.title
                    else -> "${snapshot.title} - ${snapshot.artist}"
                },
            )

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
                if (snapshot.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart,
            )
            views.setContentDescription(
                R.id.widget_like,
                context.getString(if (snapshot.isLiked) R.string.remove_from_liked else R.string.like),
            )
            views.setInt(
                R.id.widget_shuffle,
                "setBackgroundResource",
                if (snapshot.shuffleEnabled) R.drawable.widget_toggle_on else R.drawable.widget_transport_button,
            )
            views.setContentDescription(
                R.id.widget_shuffle,
                context.getString(if (snapshot.shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off),
            )

            val open = openPlayer(context)
            views.setOnClickPendingIntent(R.id.widget_pill, open)

            if (snapshot.hasTrack) {
                views.setImageAlpha(R.id.widget_toggle, ALPHA_ENABLED)
                views.setImageAlpha(R.id.widget_like, ALPHA_ENABLED)
                views.setImageAlpha(R.id.widget_shuffle, ALPHA_ENABLED)
                views.setImageAlpha(
                    R.id.widget_previous,
                    if (snapshot.hasPrevious) ALPHA_ENABLED else ALPHA_DISABLED,
                )
                views.setImageAlpha(
                    R.id.widget_next,
                    if (snapshot.hasNext) ALPHA_ENABLED else ALPHA_DISABLED,
                )
                for ((button, action) in ACTIONS) {
                    views.setOnClickPendingIntent(button, MediaWidgetActions.pendingIntent(context, action))
                }
            } else {
                // Nothing to control yet; every button opens the app instead.
                // See MediaWidget.views.
                for ((button, _) in ACTIONS) {
                    views.setImageAlpha(button, ALPHA_DISABLED)
                    views.setOnClickPendingIntent(button, open)
                }
            }
            return views
        }

        private fun RemoteViews.setImageAlpha(viewId: Int, alpha: Int) =
            setInt(viewId, "setImageAlpha", alpha)

        /** Same intent as [MediaWidget]'s, and why it has no action is explained there. */
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

        private val ACTIONS = listOf(
            R.id.widget_like to MediaWidgetActions.ACTION_LIKE,
            R.id.widget_previous to MediaWidgetActions.ACTION_PREVIOUS,
            R.id.widget_toggle to MediaWidgetActions.ACTION_TOGGLE,
            R.id.widget_next to MediaWidgetActions.ACTION_NEXT,
            R.id.widget_shuffle to MediaWidgetActions.ACTION_SHUFFLE,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** The disc all instances currently show. @see render */
        @Volatile
        private var lastDisc: Bitmap? = null

        /** The body each instance currently shows. @see render */
        private val lastBody = ConcurrentHashMap<Int, Bitmap>()

        /** Four cells by the launcher's 70n − 30 arithmetic, for a host that hasn't said. */
        private const val FALLBACK_WIDTH_DP = 250

        /** Bounds the bitmap parcelled to the host on a tablet-wide instance. */
        private const val MAX_WIDTH_PX = 1_600

        private const val RENDER_TIMEOUT_MS = 8_000L

        private const val ALPHA_ENABLED = 255
        private const val ALPHA_DISABLED = 90

        private const val REQUEST_OPEN_PLAYER = 0
    }
}
