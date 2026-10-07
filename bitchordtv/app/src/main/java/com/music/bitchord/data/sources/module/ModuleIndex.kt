package com.music.bitchord.data.sources.module

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parses a raw module-index JSON body into a flat list of [SpineModule]s.
 *
 * Index servers organise modules under arbitrary "category:<key>" top-level
 * keys (e.g. "category:music", "category:debrid_modules",
 * "category:ricky_modules"). A fixed-field data class would silently drop
 * every module under an unrecognised key; scanning for any key that starts
 * with "category:" picks them all up — the same approach Convx takes.
 */
object ModuleIndex {

    private val excludedCategories = setOf("category:artworks", "category:testing")
    private val listSerializer = ListSerializer(SpineModule.serializer())

    fun parseModules(json: Json, body: String): List<SpineModule> {
        val obj = json.decodeFromString(JsonObject.serializer(), body)
        return obj.entries
            .filter { it.key.startsWith("category:") && it.key !in excludedCategories }
            .flatMap { (_, value) ->
                runCatching { json.decodeFromJsonElement(listSerializer, value) }
                    .getOrElse { emptyList() }
            }
            .distinctBy { it.id }
    }
}
