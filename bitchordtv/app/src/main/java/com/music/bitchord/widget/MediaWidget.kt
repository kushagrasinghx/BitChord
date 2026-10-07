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
import android.view.View
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
 * The home-screen widget: the cover of what's playing, with a transport across
 * the foot of it.
 *
 * Two providers, [MediaWidgetSquare] and [MediaWidgetWide], so the picker offers
 * a square one and a full-width one — but they are the same widget, and both can
 * be resized across the whole range. What they differ in is the size they arrive
 * at. Which of the two layouts a given instance draws is decided from its
 * *measured* width ([WIDE_LAYOUT_MIN_DP]), not from which provider it came from,
 * so a square dragged out to four cells sets its title beside the buttons and
 * picks up the artist, and a wide one squeezed back to two stacks the title over
 * them instead — rather than either being stuck with a layout its size doesn't
 * suit.
 *
 * Everything visual except the transport itself is one bitmap, drawn by
 * [MediaWidgetArt] — see there for why the blur has to work that way.
 */
abstract class MediaWidget : AppWidgetProvider() {

    /**
     * The width to assume when the host hasn't said. Launchers are supposed to
     * put a size in the options bundle before the first update and most do, but
     * not all, and a widget that guessed wrong renders its artwork at the wrong
     * aspect ratio until something moves it. This provider's own target size is
     * the best available guess.
     */
    protected abstract val fallbackWidthDp: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    /** Fires on every resize, which is a re-render: the bitmap is size-specific. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        renderAsync(context, intArrayOf(id))
    }

    /** A widget that has been dragged off the home screen has no last cover. */
    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) lastArt.remove(id)
    }

    override fun onDisabled(context: Context) {
        MediaWidgetArt.clear()
        lastArt.clear()
    }

    /**
     * Renders off the broadcast thread, holding the broadcast open while it runs.
     *
     * [goAsync] is what makes that legal — artwork may have to come off disk or
     * out of the network, and returning from `onUpdate` first would let the
     * process be killed mid-render. The deadline is well inside the window a
     * broadcast gets; past it the widget keeps whatever it last drew, which is
     * a better outcome than an ANR.
     */
    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        val fallback = fallbackWidthDp
        scope.launch {
            try {
                render(app, ids, fallback, SystemClock.elapsedRealtime() + BROADCAST_BUDGET_MS)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    companion object {

        /**
         * The measured width at which the track is worth setting beside the
         * transport rather than above it.
         *
         * The wide layout spends 158dp on chrome — 14dp of leading padding, three
         * 44dp buttons, 4dp trailing, 8dp between text and buttons — so below
         * roughly 230dp the title is a stub, and the compact layout, which gives
         * that width back to the title by putting it on its own line, reads
         * better.
         *
         * Set at the midpoint between a three-cell span (180dp) and a four-cell
         * one (250dp) rather than at either end. Cells are not really 70dp on
         * every launcher, and the midpoint is the value that tolerates the most
         * variance in both directions before a four-cell widget falls to compact
         * or a three-cell one is promoted to a layout it has no room for.
         */
        const val WIDE_LAYOUT_MIN_DP = 215

        /**
         * Redraws every placed widget of any kind, the 4×1 included.
         *
         * Called by
         * [PlaybackService][com.music.bitchord.playback.PlaybackService] whenever
         * what the widget shows has changed — which is on every play, pause and
         * track change, so it does nothing on the calling thread beyond handing
         * off. Even asking the system which widgets exist is a binder round trip,
         * and that thread is the one ExoPlayer runs on.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            MediaWidgetPill.refresh(app)
            scope.launch {
                val manager = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return@launch
                for ((provider, fallbackWidthDp) in PROVIDERS) {
                    val ids = runCatching {
                        manager.getAppWidgetIds(ComponentName(app, provider))
                    }.getOrNull()
                    if (ids == null || ids.isEmpty()) continue
                    // No deadline: nothing is being held open here. This is the
                    // hot path — it runs on every track change, play and pause —
                    // so it is also the one where starving the second widget of
                    // its render would be noticed most.
                    render(app, ids, fallbackWidthDp, deadline = null)
                }
            }
        }

        /**
         * Redraws [ids], each within its own [RENDER_TIMEOUT_MS].
         *
         * Per widget rather than across all of them, because these renders are
         * mostly network waits and one slow cover used to spend the whole budget
         * — leaving every widget after it in the list unrendered. [deadline], for
         * the [goAsync] path only, caps the total: the broadcast has a window it
         * must finish inside, and a per-widget timeout can outrun it.
         */
        private suspend fun render(
            context: Context,
            ids: IntArray,
            fallbackWidthDp: Int,
            deadline: Long?,
        ) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val snapshot = MediaWidgetSnapshot.load(context)
            // Keyed on the artwork rather than the track: two tracks off one
            // album are one picture, so moving through an album redraws nothing.
            val key = snapshot.artworkUrl ?: KEY_NO_ARTWORK
            for (id in ids) {
                val budget = when (deadline) {
                    null -> RENDER_TIMEOUT_MS
                    else -> minOf(RENDER_TIMEOUT_MS, deadline - SystemClock.elapsedRealtime())
                }
                // Out of time. The widgets left keep what they are already
                // showing, which is the same thing they would keep if their
                // render had timed out.
                if (budget <= 0L) return
                withTimeoutOrNull(budget) { render(context, manager, id, snapshot, key, fallbackWidthDp) }
            }
        }

        private suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            snapshot: MediaWidgetSnapshot,
            key: String,
            fallbackWidthDp: Int,
        ) {
            val size = measure(context, manager, id, fallbackWidthDp)
            val cached = MediaWidgetArt.peek(key, size.widthPx, size.heightPx, size.bandPx)
            if (cached == null) {
                // The picture still has to be drawn, and that can mean a network
                // fetch. Push the transport now so a tap is answered in a frame;
                // the artwork catches up below.
                //
                // With the cover this widget last drew, not with nothing. A
                // RemoteViews replaces the whole tree, so leaving widget_art
                // unset does not leave it alone — it clears it, and the layout's
                // placeholder background shows through. That is only correct if
                // the render below then succeeds; when it fails, or times out, or
                // never gets a turn, the wipe is all that happens and the widget
                // sits on a gradient until something else refreshes it. Holding
                // the old cover for the fraction of a second the new one takes is
                // the smaller wrong: it is briefly the wrong picture rather than
                // durably no picture.
                manager.push(id, views(context, snapshot, size, lastArt[id]))
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
            manager.push(id, views(context, snapshot, size, art))
        }

        // ---- views ----

        private fun views(
            context: Context,
            snapshot: MediaWidgetSnapshot,
            size: WidgetSize,
            art: Bitmap?,
        ): RemoteViews {
            val layout =
                if (size.wide) R.layout.widget_media_wide else R.layout.widget_media_compact
            val views = RemoteViews(context.packageName, layout)
            art?.let { views.setImageViewBitmap(R.id.widget_art, it) }

            // Both layouts carry the title; only the wide one has room for the
            // artist beneath it.
            views.setTextViewText(
                R.id.widget_title,
                if (snapshot.hasTrack) {
                    snapshot.title
                } else {
                    context.getString(R.string.widget_nothing_played)
                },
            )
            if (size.wide) {
                views.setTextViewText(R.id.widget_artist, snapshot.artist)
                views.setViewVisibility(
                    R.id.widget_artist,
                    if (snapshot.artist.isBlank()) View.GONE else View.VISIBLE,
                )
            }

            views.setImageViewResource(
                R.id.widget_toggle,
                if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(
                    if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play,
                ),
            )

            // On the root, not on widget_art: the root is the last view offered a
            // touch, so this catches every pixel that isn't a button — including
            // the empty space either side of them in the band, which is over the
            // artwork and should behave like it.
            val open = openPlayer(context)
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (snapshot.hasTrack) {
                views.setImageAlpha(R.id.widget_toggle, ALPHA_ENABLED)
                // Dimmed at the ends of the queue, matching what the media
                // notification does with the same commands unavailable.
                views.setImageAlpha(
                    R.id.widget_previous,
                    if (snapshot.hasPrevious) ALPHA_ENABLED else ALPHA_DISABLED,
                )
                views.setImageAlpha(
                    R.id.widget_next,
                    if (snapshot.hasNext) ALPHA_ENABLED else ALPHA_DISABLED,
                )
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
                // Nothing has ever played, so there is nothing to control. Dim the
                // transport and send every button to the app: a control that opens
                // the library is more use than one that does nothing at all.
                for (button in TRANSPORT) {
                    views.setImageAlpha(button, ALPHA_DISABLED)
                    views.setOnClickPendingIntent(button, open)
                }
            }
            return views
        }

        /**
         * `setImageAlpha` has no dedicated `RemoteViews` helper, but `ImageView`
         * marks it remotable, so it can be reached by name.
         */
        private fun RemoteViews.setImageAlpha(viewId: Int, alpha: Int) =
            setInt(viewId, "setImageAlpha", alpha)

        /**
         * Opens the app on the full player.
         *
         * No action and no launcher category on purpose. `MainActivity` is
         * `singleTask`, and an `ACTION_MAIN`/`CATEGORY_LAUNCHER` intent aimed at
         * the root of an existing task can be treated as a launcher tap and
         * satisfied by bringing the task forward — without `onNewIntent`, which
         * is the half of the relay that runs whenever the app was already alive.
         * An explicit component with no action always delivers.
         */
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

        /**
         * A host that has gone away, or one that rejects the bitmap, is not worth
         * taking the process down for — and this runs from a broadcast, where an
         * escaping exception is a crash.
         */
        private fun AppWidgetManager.push(id: Int, views: RemoteViews) {
            runCatching { updateAppWidget(id, views) }
        }

        // ---- measuring ----

        private class WidgetSize(
            val wide: Boolean,
            val widthPx: Int,
            val heightPx: Int,
            val bandPx: Int,
            val cornerRadiusPx: Float,
        )

        /**
         * The size to draw this instance at, in bitmap pixels.
         *
         * The options bundle reports a range, not a size: the smallest the host
         * will make the widget in one orientation and the largest in the other.
         * Portrait wants min-width and max-height, landscape the reverse. Read
         * for the orientation in force now, so what is drawn matches what is on
         * screen — a widget rendered for the wrong orientation is stretched by
         * `fitXY`, and a vertical stretch is the one that shows, because it slides
         * the baked blur out of line with the band drawn over it.
         */
        private fun measure(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            fallbackWidthDp: Int,
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
            )?.takeIf { it > 0 } ?: fallbackWidthDp
            val heightDp = options?.getInt(
                if (landscape) {
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                } else {
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                },
            )?.takeIf { it > 0 } ?: FALLBACK_HEIGHT_DP

            val wide = widthDp >= WIDE_LAYOUT_MIN_DP
            val resources = context.resources
            val density = resources.displayMetrics.density
            val rawWidth = widthDp * density
            val rawHeight = heightDp * density
            // Every widget update parcels this bitmap to the host, which keeps it
            // for as long as the widget is on screen. Bounded so a tablet-sized
            // instance can't hand over something absurd; at phone sizes it never
            // binds.
            val longest = maxOf(rawWidth, rawHeight)
            val scale = if (longest > MAX_BITMAP_PX) MAX_BITMAP_PX / longest else 1f
            val band = resources.getDimensionPixelSize(
                if (wide) R.dimen.widget_band_wide else R.dimen.widget_band_compact,
            )
            return WidgetSize(
                wide = wide,
                widthPx = (rawWidth * scale).roundToInt().coerceAtLeast(1),
                heightPx = (rawHeight * scale).roundToInt().coerceAtLeast(1),
                // Scaled by the same factor as the bitmap, or the blurred region
                // would stop lining up with the band laid over it.
                bandPx = (band * scale).roundToInt().coerceAtLeast(1),
                cornerRadiusPx = resources.getDimension(R.dimen.widget_corner_radius) * scale,
            )
        }

        // ---- constants ----

        private val PROVIDERS = listOf(
            MediaWidgetSquare::class.java to SQUARE_WIDTH_DP,
            MediaWidgetWide::class.java to WIDE_WIDTH_DP,
        )

        private val TRANSPORT =
            intArrayOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)

        /**
         * Renders run on a process-wide scope rather than one tied to a receiver:
         * a provider instance lives only for the duration of one `onReceive`, and
         * [refresh] is not called from a receiver at all.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** How long any one widget's artwork may take before it is given up on. */
        private const val RENDER_TIMEOUT_MS = 8_000L

        /**
         * How long all of a broadcast's widgets together may take. Comfortably
         * inside the window a broadcast is allowed to stay open, which
         * [RENDER_TIMEOUT_MS] on its own no longer promises now that it is spent
         * per widget rather than across them.
         */
        private const val BROADCAST_BUDGET_MS = 8_000L

        /**
         * The cover each widget is currently showing.
         *
         * Kept so a render that fails has something truthful to fall back on —
         * see the push in [render]. Held strongly, which is the same thing the
         * launcher is already doing with these bitmaps for as long as the widget
         * is on screen, and dropped when the widget is.
         */
        private val lastArt = ConcurrentHashMap<Int, Bitmap>()

        private const val MAX_BITMAP_PX = 1_200f

        private const val ALPHA_ENABLED = 255
        private const val ALPHA_DISABLED = 90

        /** What the placeholder composite is remembered under. */
        private const val KEY_NO_ARTWORK = "no-artwork"

        private const val REQUEST_OPEN_PLAYER = 0
    }
}

/** Grid spans, by the launcher's own arithmetic: a span of n cells is 70n − 30 dp. */
private const val SQUARE_WIDTH_DP = 110

private const val WIDE_WIDTH_DP = 250

private const val FALLBACK_HEIGHT_DP = 110

/** The 2×2 entry in the picker. See `res/xml/widget_media_square.xml`. */
class MediaWidgetSquare : MediaWidget() {
    override val fallbackWidthDp = SQUARE_WIDTH_DP
}

/** The 4×2 entry in the picker. See `res/xml/widget_media_wide.xml`. */
class MediaWidgetWide : MediaWidget() {
    override val fallbackWidthDp = WIDE_WIDTH_DP
}
