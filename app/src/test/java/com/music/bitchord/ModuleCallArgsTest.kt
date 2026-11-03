package com.music.bitchord

import com.music.bitchord.data.sources.module.ModuleManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arguments [ModuleManager] passes to a module's exports are spliced into
 * JavaScript source, so whatever a title or a setting holds has to come out
 * as the same string on the other side, not as code.
 */
class ModuleCallArgsTest {

    private val manager = ModuleManager()

    @Test
    fun `a title with quotes and backslashes stays one string`() {
        val title = """12" Mix \ "); fetch('//x'); ("""
        val literal = manager.jsString(title)
        assertEquals(title, Json.decodeFromString<String>(literal))
    }

    @Test
    fun `a setting value with a quote stays one value`() {
        val context = manager.contextArg(mapOf("apiKey" to "ab\"cd", "region" to "us"))
        val settings = Json.parseToJsonElement(context).jsonObject.getValue("settings").jsonObject
        assertEquals("ab\"cd", settings.getValue("apiKey").jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("us", settings.getValue("region").jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun `no settings is still the shape a module reads`() {
        assertEquals("""{"settings":{}}""", manager.contextArg(emptyMap()))
    }
}
