/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class AiRateLimitException(
    message: String,
) : AiServiceException(message)

object AiRateLimiter {
    private const val HourMs = 60L * 60_000L
    private const val TAG = "AiRateLimiter"

    enum class Feature(
        val label: String,
        val minIntervalMs: Long,
        val maxPerHour: Int,
        val smoothingWaitMs: Long,
    ) {
        LYRICS_TRANSLATION(label = "lyrics translation", minIntervalMs = 50L, maxPerHour = 600, smoothingWaitMs = 150L),

        LYRICS_ROMANIZATION(
            label = "lyrics romanisation",
            minIntervalMs = 50L,
            maxPerHour = 600,
            smoothingWaitMs = 150L,
        ),

        AI_MIX(label = "AI Mix", minIntervalMs = 10L * 60_000L, maxPerHour = 6, smoothingWaitMs = 0L),
    }

    private val history = HashMap<Feature, ArrayDeque<Long>>()

    suspend fun <T> withLimit(
        feature: Feature,
        block: suspend () -> T,
    ): T {
        while (true) {
            val wait = reserveOrWait(feature)
            if (wait <= 0L) break
            delay(wait)
        }

        val reservedAt = markReserved(feature)
        return try {
            val result = block()

            result
        } catch (e: CancellationException) {
            refund(feature, reservedAt)
            throw e
        } catch (e: Throwable) {
            refund(feature, reservedAt)
            Log.w(TAG, "${feature.label} call failed; refunded rate-limit slot: ${e.message}")
            throw e
        }
    }

    @Synchronized
    private fun reserveOrWait(feature: Feature): Long {
        val now = SystemClock.elapsedRealtime()
        val calls = history.getOrPut(feature) { ArrayDeque() }
        while (calls.isNotEmpty() && now - calls.first() > HourMs) {
            calls.removeFirst()
        }
        if (calls.size >= feature.maxPerHour) {
            val retryInMin = (HourMs - (now - calls.first())) / 60_000L + 1
            throw AiRateLimitException(
                "Hourly AI budget for ${feature.label} reached - try again in ~$retryInMin min",
            )
        }
        val last = calls.lastOrNull()
        if (last != null) {
            val deficit = feature.minIntervalMs - (now - last)
            if (deficit > 0L) {
                if (deficit > feature.smoothingWaitMs) {
                    val retryInMin = deficit / 60_000L + 1
                    throw AiRateLimitException(
                        "${feature.label.replaceFirstChar { it.uppercase() }} ran recently - try again in ~$retryInMin min to save tokens",
                    )
                }
                return deficit
            }
        }
        return 0L
    }

    @Synchronized
    private fun markReserved(feature: Feature): Long {
        val now = SystemClock.elapsedRealtime()
        val calls = history.getOrPut(feature) { ArrayDeque() }
        calls.addLast(now)
        return now
    }

    @Synchronized
    private fun refund(feature: Feature, reservedAt: Long) {
        val calls = history[feature] ?: return

        if (calls.isNotEmpty() && calls.last() == reservedAt) {
            calls.removeLast()
        }
    }
}
