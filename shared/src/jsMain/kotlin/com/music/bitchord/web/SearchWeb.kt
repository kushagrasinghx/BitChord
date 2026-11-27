@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.SongCredits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** SongCredits.kt `SongCredit`: a role ("Written by") and who filled it. */
@JsExport
class SongCreditGroup(val role: String, val names: Array<String>)

/** A browse request for a track's credits page, as a `next` response links to it. */
@JsExport
class CreditsBrowse(val browseId: String, val params: String?)

/** SongCredits.kt `parseCreditsPage`: the credits on a raw credits `browse` answer (MPTC…), in YouTube's order. */
@JsExport
fun songCredits(browseResponse: String): Array<SongCreditGroup> {
    val root = runCatching { Json.parseToJsonElement(browseResponse) }.getOrNull() ?: return emptyArray()
    return SongCredits.parseCreditsPage(root).map { SongCreditGroup(it.role, it.names.toTypedArray()) }.toTypedArray()
}

/** SongCredits.kt `findCreditsEndpoint`: where a raw `next` answer for [videoId] says its credits are. */
@JsExport
fun creditsBrowse(nextResponse: String, videoId: String): CreditsBrowse? {
    val root = runCatching { Json.parseToJsonElement(nextResponse) }.getOrNull() ?: return null
    val endpoint = SongCredits.findCreditsEndpoint(root, videoId) ?: return null
    val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull ?: return null
    return CreditsBrowse(browseId, (endpoint["params"] as? JsonPrimitive)?.contentOrNull)
}
