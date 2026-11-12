package com.music.bitchord.mcp

import kotlinx.serialization.json.*

/** Validate every supplied field before network, disk access or player binding. */
internal fun validateToolArguments(args: JsonObject, fields: Map<String, String>, required: List<String>) {
    require(args.keys.all { it in fields }) { "Unexpected argument" }
    required.forEach { require(it in args) { "Missing argument: $it" } }
    args.forEach { (key, value) ->
        val p = value as? JsonPrimitive ?: throw IllegalArgumentException("$key has invalid type")
        when (fields[key]) {
            "string" -> {
                require(p.isString) { "$key must be a string" }
                val max = when (key) { "query" -> 300; "video_id" -> 11; else -> 200 }
                require(p.content.trim().length in 1..max) { "Invalid $key length" }
                if (key == "video_id") require(Regex("[A-Za-z0-9_-]{11}").matches(p.content.trim())) {
                    "Expected an 11-character YouTube video ID"
                }
            }
            "integer" -> {
                require(!p.isString && Regex("-?(0|[1-9][0-9]*)").matches(p.content)) { "$key must be an integer" }
                val n = p.longOrNull ?: throw IllegalArgumentException("$key outside integer range")
                when (key) {
                    "limit" -> require(n in 1..200) { "limit must be 1..200" }
                    "position_ms" -> require(n >= 0) { "Position must be nonnegative" }
                    else -> require(n in 0..Int.MAX_VALUE.toLong()) { "$key outside allowed range" }
                }
            }
        }
    }
}
