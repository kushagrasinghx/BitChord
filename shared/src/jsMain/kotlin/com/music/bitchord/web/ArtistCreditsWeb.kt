@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.CreditJoiner
import com.music.bitchord.data.model.creditSegments
import com.music.bitchord.data.model.isExactArtistMatch as modelIsExactArtistMatch
import com.music.bitchord.data.model.normalizedArtistName as modelNormalizedArtistName

/** ArtistCredits.kt `normalizedArtistName`: case, accents and spacing folded, in every script. */
@JsExport
fun normalizedArtistName(raw: String): String = modelNormalizedArtistName(raw)

/** ArtistCredits.kt `isExactArtistMatch`: a search answer is exactly the artist asked for. */
@JsExport
fun isExactArtistMatch(answer: String, wanted: String): Boolean = modelIsExactArtistMatch(answer, wanted)

/** ArtistCredits.kt `CreditSegment`, with its credit as an index into the names given; -1 for plain text. */
@JsExport
class CreditStretch(val text: String, val credit: Int)

/** ArtistCredits.kt `creditSegments`: [text] cut where each of [names] begins. */
@JsExport
fun creditStretches(text: String, names: Array<String>): Array<CreditStretch> {
    val credits = names.map { ArtistCredit(it) }
    return creditSegments(text, credits)
        .map { segment -> CreditStretch(segment.text, credits.indexOfFirst { it === segment.credit }) }
        .toTypedArray()
}

/** ArtistCredits.kt `CreditJoiner`, as a pattern: the marks a byline joins its credits with. */
@JsExport
fun creditJoinerPattern(): String = CreditJoiner.pattern
