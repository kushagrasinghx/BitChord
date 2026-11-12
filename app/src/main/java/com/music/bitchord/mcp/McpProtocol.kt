package com.music.bitchord.mcp

import kotlinx.serialization.json.*

/** Stateless JSON-RPC dispatch, independent of Android and HTTP. */
internal object McpProtocol {
    val versions = setOf("2025-11-25", "2025-06-18", "2025-03-26")
    fun error(id: JsonElement = JsonNull, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id)
        putJsonObject("error") { put("code", code); put("message", message) }
    }
    suspend fun dispatch(body: String, list: () -> JsonArray,
                         call: suspend (String, JsonObject) -> JsonElement): JsonObject? {
        val element = try { Json.parseToJsonElement(body) } catch (_: Exception) {
            return error(code = -32700, message = "Parse error")
        }
        val obj = element as? JsonObject ?: return error(code = -32600, message = "Invalid Request")
        val id = obj["id"] ?: JsonNull
        val method = (obj["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (obj["jsonrpc"] != JsonPrimitive("2.0") || method == null ||
            (id != JsonNull && (id !is JsonPrimitive || (!id.isString && id.longOrNull == null && id.doubleOrNull == null))) ||
            (obj.containsKey("params") && obj["params"] !is JsonObject))
            return error(code = -32600, message = "Invalid Request")
        // Notifications must never execute tools: no unobservable side effects.
        if (!obj.containsKey("id")) return null
        val params = obj["params"] as? JsonObject ?: JsonObject(emptyMap())
        val result: JsonElement = when (method) {
            "initialize" -> {
                val requested = (params["protocolVersion"] as? JsonPrimitive)?.content
                if (requested == null || params["capabilities"] !is JsonObject || params["clientInfo"] !is JsonObject)
                    return error(id, -32602, "Invalid params")
                buildJsonObject {
                    put("protocolVersion", requested.takeIf { it in versions } ?: "2025-06-18")
                    putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                    putJsonObject("serverInfo") { put("name", "BitChord"); put("version", "1.0") }
                }
            }
            "ping" -> JsonObject(emptyMap())
            "tools/list" -> buildJsonObject { put("tools", list()) }
            "tools/call" -> {
                val name = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return error(id, -32602, "Invalid params")
                val args = params["arguments"] ?: JsonObject(emptyMap())
                if (args !is JsonObject) return error(id, -32602, "Invalid params")
                call(name, args)
            }
            else -> return error(id, -32601, "Method not found")
        }
        return buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
    }
}
