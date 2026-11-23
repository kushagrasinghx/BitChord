package com.music.bitchord.mcp

import kotlinx.serialization.json.*

/** Stateless JSON-RPC dispatch, independent of Android and HTTP. */
internal object McpProtocol {
    // Newest first: negotiation falls back to versions.first().
    val versions = setOf("2025-11-25", "2025-06-18", "2025-03-26")
    private val newest = "2025-11-25"

    fun error(id: JsonElement = JsonNull, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id)
        putJsonObject("error") { put("code", code); put("message", message) }
    }

    private fun JsonObject.invalidRequest() = error(code = -32600, message = "Invalid Request")
    private fun JsonObject.invalidParams(id: JsonElement) = error(id, -32602, "Invalid params")

    /** JSON-RPC 2.0 id: string, number (finite, not fractional-broken) or null. Never bool/array/object. */
    private fun JsonElement.isValidId(): Boolean = when (this) {
        is JsonNull -> true
        is JsonPrimitive -> isString || (longOrNull != null || doubleOrNull != null)
        else -> false
    }

    /** A params member, when present, must be an object; absent means empty. */
    private fun JsonObject.params(): JsonObject? =
        if (containsKey("params")) this["params"] as? JsonObject else JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    suspend fun dispatch(body: String, list: () -> JsonArray,
                         call: suspend (String, JsonObject) -> JsonElement): JsonObject? {
        val element = try { Json.parseToJsonElement(body) } catch (_: Exception) {
            return error(code = -32700, message = "Parse error")
        }
        // Batches are not supported: an array body is an invalid request.
        val obj = element as? JsonObject ?: return error(code = -32600, message = "Invalid Request")

        val id = obj["id"] ?: JsonNull
        if (!id.isValidId()) return obj.invalidRequest()
        if (obj["jsonrpc"] != JsonPrimitive("2.0")) return obj.invalidRequest()
        val method = obj.string("method") ?: return obj.invalidRequest()
        val params = obj.params() ?: return obj.invalidRequest()

        // Notifications must never execute tools: no unobservable side effects.
        if (!obj.containsKey("id")) return null

        val result: JsonElement = when (method) {
            "initialize" -> {
                // protocolVersion must be a string; capabilities and clientInfo must be objects.
                val requested = params.string("protocolVersion") ?: return obj.invalidParams(id)
                if (params["capabilities"] !is JsonObject) return obj.invalidParams(id)
                val clientInfo = params["clientInfo"] as? JsonObject ?: return obj.invalidParams(id)
                if (clientInfo.string("name") == null) return obj.invalidParams(id)
                buildJsonObject {
                    // Echo known versions, otherwise fall back to the newest supported one.
                    put("protocolVersion", if (requested in versions) requested else newest)
                    putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                    putJsonObject("serverInfo") { put("name", "BitChord"); put("version", "1.0") }
                }
            }
            "ping" -> JsonObject(emptyMap())
            "tools/list" -> buildJsonObject { put("tools", list()) }
            "tools/call" -> {
                val name = params.string("name") ?: return obj.invalidParams(id)
                val args = params["arguments"] ?: JsonObject(emptyMap())
                if (args !is JsonObject) return obj.invalidParams(id)
                call(name, args)
            }
            else -> return error(id, -32601, "Method not found")
        }
        return buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
    }
}
