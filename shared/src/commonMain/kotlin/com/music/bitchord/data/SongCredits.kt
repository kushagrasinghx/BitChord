package com.music.bitchord.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SongCredit(val role: String, val names: List<String>) {
    val text: String get() = names.joinToString(", ")
}

/**
 * Reading YouTube Music's song credits, apart from fetching them
 * ([SongInfoRepository]), so the web player reads them with the same code.
 */
object SongCredits {
    /**
     * Finds the credits browse endpoint in a `next` response. Prefers an endpoint
     * whose page type marks it as track credits, falling back to any menu
     * navigation whose text suggests credits.
     */
    fun findCreditsEndpoint(root: JsonElement, videoId: String): JsonObject? {
        fun JsonObject.pageType(): String =
            this["browseEndpointContextSupportedConfigs"]
                ?.let { it as? JsonObject }?.get("browseEndpointContextMusicConfig")
                ?.let { it as? JsonObject }?.get("pageType")
                ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty()

        fun isCreditsBrowse(browse: JsonObject): Boolean {
            val id = (browse["browseId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return "CREDIT" in browse.pageType() || id.startsWith("MPTC")
        }

        val rows = objectsNamed(root, "playlistPanelVideoRenderer")
            .filter { (it["videoId"] as? JsonPrimitive)?.contentOrNull == videoId }
            .toList()
            .ifEmpty { objectsNamed(root, "playlistPanelVideoRenderer").toList() }
        val menuScopes = (rows + listOfNotNull(root as? JsonObject))
        for (scope in menuScopes) {
            val items = objectsNamed(scope, "menuNavigationItemRenderer").toList()
            val endpoint = items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (isCreditsBrowse(browse)) browse else null
            } ?: items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (nav.runsText().contains("credit", ignoreCase = true)) browse else null
            }
            if (endpoint != null) return endpoint
        }
        return objectsNamed(root, "browseEndpoint").firstOrNull(::isCreditsBrowse)
    }

    fun parseCreditsPage(root: JsonElement): List<SongCredit> {
        val credits = mutableListOf<SongCredit>()
        // dismissableDialogContentSectionRenderer: the song credits browse page (MPTC*)
        for (section in objectsNamed(root, "dismissableDialogContentSectionRenderer")) {
            val role = section["title"]?.runsText()?.trim().orEmpty()
            val names = splitNames(listOf(section["subtitle"]?.runsText().orEmpty()))
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // musicResponsiveListItemRenderer rows: flex column 0 = role, 1+ = names
        for (item in objectsNamed(root, "musicResponsiveListItemRenderer")) {
            val flex = ((item["flexColumns"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val role = flex.getOrNull(0)?.rendererText()?.trim().orEmpty()
            val names = if (flex.size > 1) {
                flex.drop(1).flatMap { splitNames(listOf(it.rendererText())) }
            } else {
                splitNames(listOf(item["subtitle"]?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // Fallback: shelf-based layouts
        for (shelf in objectsNamed(root, "musicShelfRenderer") + objectsNamed(root, "musicCarouselShelfRenderer")) {
            val header = shelf["header"] as? JsonObject
            val role = header?.runsText()?.trim().orEmpty()
            val contents = ((shelf["contents"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val names = contents.flatMap { c ->
                splitNames(listOf((c as? JsonObject)?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
    }

    private fun JsonElement.rendererText(): String =
        objectsNamed(this, "text").firstNotNullOfOrNull { it.runsText().takeIf(String::isNotBlank) }
            ?: runsText()

    private fun JsonElement.runsText(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString("") { it.runsText() }
        is JsonObject -> (this["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (this["runs"] as? JsonArray)?.joinToString("") { run ->
                ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            ?: this["text"]?.runsText()
            ?: ""
    }

    private fun splitNames(strings: List<String>): List<String> {
        val joined = strings.joinToString("")
        if (joined.isBlank()) return emptyList()
        return joined.split(Regex("\\r?\\n+|\\s*[•·]\\s*|\\s*,\\s+(?=[\\p{Lu}\"'])"))
            .map { it.trim().trimEnd(',', '•', '·') }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(joined.trim()) }
    }

    private fun objectsNamed(root: JsonElement, name: String): Sequence<JsonObject> = sequence {
        when (root) {
            is JsonObject -> for ((key, value) in root) {
                if (key == name && value is JsonObject) yield(value)
                yieldAll(objectsNamed(value, name))
            }
            is JsonArray -> for (value in root) yieldAll(objectsNamed(value, name))
            else -> Unit
        }
    }
}
