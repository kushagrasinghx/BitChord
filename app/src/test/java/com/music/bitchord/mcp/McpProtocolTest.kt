package com.music.bitchord.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class McpProtocolTest {
    private fun dispatch(body: String) = runBlocking {
        McpProtocol.dispatch(body, { JsonArray(emptyList()) }) { _, _ -> JsonObject(emptyMap()) }
    }
    private fun code(body: String) = dispatch(body)!!["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
    @Test fun malformedJson() { assertEquals(-32700, code("{")) }
    @Test fun batchesRejected() { assertEquals(-32600, code("[]")) }
    @Test fun invalidVersion() { assertEquals(-32600, code("""{"jsonrpc":"1.0","method":"ping","id":1}""")) }
    @Test fun unknownMethod() { assertEquals(-32601, code("""{"jsonrpc":"2.0","method":"missing","id":1}""")) }
    @Test fun invalidArguments() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{"name":"play","arguments":[]}}""")) }
    @Test fun notificationHasNoReplyOrSideEffect() = runBlocking {
        var called = false
        val result = McpProtocol.dispatch("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"play"}}""",
            { JsonArray(emptyList()) }) { _, _ -> called = true; JsonNull }
        assertNull(result); assertFalse(called)
    }
    @Test fun pingPreservesId() {
        assertEquals(JsonPrimitive("abc"), dispatch("""{"jsonrpc":"2.0","method":"ping","id":"abc"}""")!!["id"])
    }
    @Test fun initializeNegotiatesSupportedVersion() {
        for (version in McpProtocol.versions) {
            val response = dispatch("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"$version","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")!!
            assertEquals(version, response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        }
    }
    @Test fun unsupportedInitializeVersionOffersCurrent() {
        val response = dispatch("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"unknown","capabilities":{},"clientInfo":{}}}""")!!
        assertEquals("2025-06-18", response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    }
}
