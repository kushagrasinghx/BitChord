package com.music.bitchord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.music.bitchord.R

/**
 * The 3×2 home-screen widget that starts song recognition on tap:
 *
 * - sleeping: the sheet's five-bar waveform at rest — the state between takes
 *   and the face the picker previews;
 * - listening: the bars breathe and rise with the room, frame bitmaps
 *   rendered by [RecognitionActivity] and handed here through [showWave].
 *   RemoteViews has no frame of its own to animate in — the animation is a
 *   stream of measured pictures, which makes the widget react to the
 *   sound instead of merely pulsing.
 */
open class RecognitionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context, layoutFor(javaClass))
        for (id in ids) {
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    companion object {

        /**
         * Puts every recognition widget back to sleep. The take's ending calls
         * this last — including the endings where the activity is about to
         * finish — so neither size stands at a frozen frame. A full apply also
         * restores the tap's PendingIntent and description after frame updates.
         */
        fun showSleep(context: Context) {
            val appContext = context.applicationContext
            val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
            for (widget in WIDGETS) {
                runCatching {
                    manager.updateAppWidget(
                        ComponentName(appContext, widget.provider),
                        buildViews(appContext, widget.layout),
                    )
                }
            }
        }

        /**
         * One listening frame for the recognition widget on the
         * screen. The frames go out as *partial* updates — only the ImageView
         * is replaced, leaving each sleep face's RemoteViews tree standing —
         * because a full re-apply at the taking cadence makes the launcher
         * re-render the whole widget instead of one bar picture.
         */
        fun showWave(context: Context, bitmap: Bitmap) {
            val appContext = context.applicationContext
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                for (widget in WIDGETS) {
                    val ids = manager.getAppWidgetIds(ComponentName(appContext, widget.provider))
                    if (ids.isEmpty()) continue
                    val views = RemoteViews(appContext.packageName, widget.layout).apply {
                        setImageViewBitmap(R.id.widget_wave, bitmap)
                        setContentDescription(
                            R.id.widget_wave,
                            appContext.getString(R.string.widget_recognition_listening),
                        )
                    }
                    manager.partiallyUpdateAppWidget(ids, views)
                }
            }
        }

        /**
         * The tap's payload: everything the widget wants from the system is
         * one launch of [RecognitionActivity]. Code 0 and an intent with no
         * extras keeps the widget's PendingIntent stable across launcher
         * re-applies.
         */
        private fun tapIntent(context: Context): Intent =
            Intent(context, RecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        /**
         * A recognition widget at rest: the sleeping face and the tap's
         * PendingIntent.
         * The ImageView reset is recorded as its own action on purpose: when
         * the launcher re-applies this view set over a host whose ImageView
         * still carries a listening frame's bitmap (the same-layout update
         * path doesn't re-inflate the layout, it only re-applies recorded
         * actions), without the reset the stale frame would keep standing on
         * the bars — a stop that never looked like one.
         */
        private fun buildViews(context: Context, layout: Int): RemoteViews {
            val pending = PendingIntent.getActivity(
                context,
                0,
                tapIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, layout).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
                setImageViewResource(R.id.widget_wave, R.drawable.ic_widget_wave_sleep)
            }
        }

        private data class WidgetSpec(
            val provider: Class<out AppWidgetProvider>,
            val layout: Int,
        )

        private val WIDGETS = listOf(
            WidgetSpec(RecognitionWidgetWide::class.java, R.layout.widget_recognition_wide),
        )

        private fun layoutFor(provider: Class<*>): Int =
            WIDGETS.firstOrNull { it.provider == provider }?.layout
                ?: R.layout.widget_recognition_wide
    }
}
