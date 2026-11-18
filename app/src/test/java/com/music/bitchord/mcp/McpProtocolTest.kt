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
    private fun init(version: String = "2025-06-18", id: String = "1", client: String = """{"name":"test","version":"1"}""") =
        dispatch("""{"jsonrpc":"2.0","method":"initialize","id":$id,"params":{"protocolVersion":$version,"capabilities":{},"clientInfo":$client}}""")

    @Test fun malformedJson() { assertEquals(-32700, code("{")) }
    @Test fun batchesRejected() { assertEquals(-32600, code("[]")) }
    @Test fun scalarBodyRejected() { assertEquals(-32600, code("\"text\"")) }
    @Test fun invalidVersion() { assertEquals(-32600, code("""{"jsonrpc":"1.0","method":"ping","id":1}""")) }
    @Test fun missingJsonrpc() { assertEquals(-32600, code("""{"method":"ping","id":1}""")) }
    @Test fun numericJsonrpcRejected() { assertEquals(-32600, code("""{"jsonrpc":2,"method":"ping","id":1}""")) }
    @Test fun nonStringMethodRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":5,"id":1}""")) }
    @Test fun nullMethodRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":null,"id":1}""")) }
    @Test fun arrayParamsRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":"ping","id":1,"params":[]}""")) }
    @Test fun unknownMethod() { assertEquals(-32601, code("""{"jsonrpc":"2.0","method":"missing","id":1}""")) }
    @Test fun invalidArguments() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{"name":"play","arguments":[]}}""")) }
    @Test fun callWithoutName() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{}}""")) }
    @Test fun callWithNumericName() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{"name":7}}""")) }
    @Test fun callWithNullArguments() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{"name":"play","arguments":null}}""")) }

    @Test fun booleanIdRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":"ping","id":true}""")) }
    @Test fun arrayIdRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":"ping","id":[]}""")) }
    @Test fun objectIdRejected() { assertEquals(-32600, code("""{"jsonrpc":"2.0","method":"ping","id":{}}""")) }

    @Test fun notificationHasNoReplyOrSideEffect() = runBlocking {
        var called = false
        val result = McpProtocol.dispatch("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"play"}}""",
            { JsonArray(emptyList()) }) { _, _ -> called = true; JsonNull }
        assertNull(result); assertFalse(called)
    }
    @Test fun invalidNotificationStillSilent() = runBlocking {
        // Notifications are answered only on error, and invalid params is not a transport error.
        var called = false
        val result = McpProtocol.dispatch("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":7}}""",
            { JsonArray(emptyList()) }) { _, _ -> called = true; JsonNull }
        assertNull(result); assertFalse(called)
    }
    @Test fun invalidNotificationBodyStillAnswered() {
        assertNotNull(dispatch("""{"jsonrpc":"2.0","method":"ping","id":false}"""))
    }
    @Test fun unknownMethodNotification() {
        assertNull(dispatch("""{"jsonrpc":"2.0","method":"missing"}"""))
    }

    @Test fun pingPreservesId() {
        assertEquals(JsonPrimitive("abc"), dispatch("""{"jsonrpc":"2.0","method":"ping","id":"abc"}""")!!["id"])
    }
    @Test fun numericIdPreserved() {
        assertEquals(JsonPrimitive(42), dispatch("""{"jsonrpc":"2.0","method":"ping","id":42}""")!!["id"])
    }
    @Test fun explicitNullIdIsAnswered() {
        val response = dispatch("""{"jsonrpc":"2.0","method":"ping","id":null}""")!!
        assertEquals(JsonNull, response["id"])
        assertTrue(response.containsKey("result"))
    }
    @Test fun parseErrorHasNullId() {
        assertEquals(JsonNull, dispatch("{")!!["id"])
    }

    @Test fun deepJsonInArgumentsPassesThrough() = runBlocking {
        var seen: JsonObject? = null
        val deep = """{"a":[[[{"b":[1,2,{"c":null}]}]]],"d":{"e":{}}}"""
        McpProtocol.dispatch("""{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{"name":"play","arguments":$deep}}""",
            { JsonArray(emptyList()) }) { _, args -> seen = args; JsonNull }
        assertEquals(Json.parseToJsonElement(deep), seen)
    }
    @Test fun extraMembersIgnored() {
        assertNotNull(dispatch("""{"jsonrpc":"2.0","method":"ping","id":1,"extra":[1,{"x":null}]}""")!!["result"])
    }

    @Test fun initializeNegotiatesSupportedVersion() {
        for (version in McpProtocol.versions) {
            val response = init(version = "\"$version\"")!!
            assertEquals(version, response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        }
    }
    @Test fun unsupportedInitializeVersionOffersNewest() {
        val response = init(version = "\"unknown\"")!!
        assertEquals("2025-11-25", response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    }
    @Test fun nonStringProtocolVersionRejected() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":20250618,"capabilities":{},"clientInfo":{"name":"t"}}}""")) }
    @Test fun nullProtocolVersionRejected() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":null,"capabilities":{},"clientInfo":{"name":"t"}}}""")) }
    @Test fun missingProtocolVersionRejected() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"capabilities":{},"clientInfo":{"name":"t"}}}""")) }
    @Test fun invalidClientInfoRejected() {
        assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":"x"}}"""))
        assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{}}}"""))
        assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":3}}}"""))
    }
    @Test fun invalidCapabilitiesRejected() { assertEquals(-32602, code("""{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-06-18","capabilities":[],"clientInfo":{"name":"t"}}}""")) }

    @Test fun november2025ClientCanListTools() = runBlocking {
        val response = McpProtocol.dispatch("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
            { JsonArray(emptyList()) }) { _, _ -> JsonNull }
        assertEquals(JsonArray(emptyList()), response!!["result"]!!.jsonObject["tools"])
        assertTrue("2025-11-25" in McpProtocol.versions)
    }
    @Test fun toolsListWorksWithoutParams() {
        assertNotNull(dispatch("""{"jsonrpc":"2.0","method":"tools/list","id":1}""")!!["result"])
    }
}
