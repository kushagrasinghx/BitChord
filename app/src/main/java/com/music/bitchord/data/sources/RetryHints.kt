package com.music.bitchord.data.sources

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * "Not yet — ask again in N seconds", collected from the sources while one
 * lookup runs.
 *
 * A debrid-backed addon asked for an album its service has not cached yet has
 * two ways to answer. It can hold `/stream` open while the download runs, which
 * the listener hears as a wait before every track of that album. Or it can
 * answer at once with no URL, which lets playback start on YouTube straight
 * away. The second is the better answer, but on its own it is also a final
 * one: the second look reads it as "nothing better exists", and the track stays
 * on YouTube for the rest of its play even though the FLAC is ready a few
 * minutes later.
 *
 * So an addon can say when asking again is worth it: `retryAfter`, in seconds,
 * on an answer with no `url` (see
 * [AddonStream.retryAfterSec][com.music.bitchord.data.sources.addon.AddonStream.retryAfterSec]).
 * [AddonSource] reports it here, and
 * [QualityUpgrade.lookAgain][com.music.bitchord.playback.QualityUpgrade.lookAgain]
 * — the one caller that can afford to wait, because sound is already coming
 * out of the speaker — puts one of these around its search, and asks again
 * when the addon said to.
 *
 * A coroutine context element rather than a return value, because the answer
 * travels through [SourceResolver.bestAcross], which returns a stream or
 * nothing and has no reason to carry one source's timetable. Its racing
 * `async` children inherit the element, so a report lands with whoever asked,
 * and a lookup nobody installed one around reports to nobody.
 */
class RetryHints : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<RetryHints>

    /** One source's "not yet". */
    data class Hint(val afterSec: Int, val source: String, val reason: String?)

    private var soonest: Hint? = null

    /** Records a hint, keeping the soonest across every source that sent one. */
    fun note(afterSec: Int, source: String, reason: String?) {
        synchronized(this) {
            val current = soonest
            if (current == null || afterSec < current.afterSec) {
                soonest = Hint(afterSec, source, reason)
            }
        }
    }

    /** The soonest hint any source gave, or null when none asked to be asked again. */
    val soonestHint: Hint? get() = synchronized(this) { soonest }
}
