package com.music.bitchord.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Measures the production standby queue-transfer helper against real Media3
 * ExoPlayer instances. This is a queue-transfer benchmark, not a full audio
 * crossfade benchmark; it isolates the PBK-07 copy/reuse decision.
 */
@RunWith(AndroidJUnit4::class)
class StandbyQueueTransferBenchmark {

    private val sizes = listOf(100, 500, 754, 1_000, 2_000)
    private val iterations = 7

    @Test
    fun compareFullTransferWithValidatedReuse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (size in sizes) {
            val items = List(size) { MediaItem.Builder().setMediaId("track-$it").setUri("https://example.test/track-$it").build() }
            var out: ExoPlayer? = null
            var into: ExoPlayer? = null
            val copyNs = LongArray(iterations)
            val reuseNs = LongArray(iterations)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                out = ExoPlayer.Builder(context).build()
                into = ExoPlayer.Builder(context).build()
                out!!.setMediaItems(items)
                // Warm-up.
                into!!.clearMediaItems()
                StandbyQueueTransfer.prepare(out!!, into!!, 0, 0L)
                for (i in 0 until iterations) {
                    into!!.clearMediaItems()
                    val t0 = System.nanoTime()
                    val reused = StandbyQueueTransfer.prepare(out!!, into!!, 0, 0L)
                    copyNs[i] = System.nanoTime() - t0
                    assertFalse("first transfer must copy", reused)

                    val t1 = System.nanoTime()
                    val reusedAgain = StandbyQueueTransfer.prepare(out!!, into!!, 0, 0L)
                    reuseNs[i] = System.nanoTime() - t1
                    assertTrue("identical queue must reuse", reusedAgain)
                    assertEquals(size, into!!.mediaItemCount)
                }
            }
            val copySorted = copyNs.sortedArray()
            val reuseSorted = reuseNs.sortedArray()
            Log.i(
                "PBK07Benchmark",
                "size=$size copyMedianUs=${copySorted[iterations / 2] / 1000.0} " +
                    "copyP95Us=${copySorted[((iterations - 1) * 0.95).roundToInt()] / 1000.0} " +
                    "reuseMedianUs=${reuseSorted[iterations / 2] / 1000.0} " +
                    "reuseP95Us=${reuseSorted[((iterations - 1) * 0.95).roundToInt()] / 1000.0}",
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                out?.release()
                into?.release()
            }
        }
    }
}
