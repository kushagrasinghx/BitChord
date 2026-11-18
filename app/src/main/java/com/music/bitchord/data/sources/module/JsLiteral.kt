package com.music.bitchord.data.sources.module

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Turns a Kotlin string into a JavaScript string literal.
 *
 * Modules are called by building JavaScript source — `__spine_mod['searchTracks']("\"$query\"", 50, …)`
 * — and every one of those values used to be interpolated raw. A search for
 * `"` or `\");globalThis.pwned=1;//` therefore did not search for anything: it
 * closed the literal and the rest of the text was evaluated as code, inside a
 * VM that has `__spine.fetch` bound to a live HTTP client. Track ids,
 * preferred quality and every module setting key and value travel the same way,
 * and the settings are typed by the user.
 *
 * A JSON string literal is a JavaScript string literal — same quoting, same
 * escapes — so the app's own serialiser does the quoting instead of a
 * hand-written `\"$value\"`. Two characters need care afterwards: U+2028 and
 * U+2029 are legal inside JSON strings but are line terminators to an engine
 * parsing source, so they are emitted as escapes. Everything else in the
 * encoded form is already safe.
 */
internal object JsLiteral {

    private val json = Json

    fun encode(value: String): String = json
        .encodeToString(String.serializer(), value)
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
}

/**
 * The `{settings:{…}}` context argument module calls take, with every key and
 * value quoted by [JsLiteral].
 *
 * A settings key or value is typed by the user, so it is untrusted input being
 * placed inside source text; a `"` in either used to close the literal and put
 * the rest of the value into the evaluated program.
 */
internal fun settingsContextArg(settings: Map<String, String>): String =
    settings.entries.joinToString(
        separator = ",",
        prefix = "{\"settings\":{",
        postfix = "}}",
    ) { (key, value) -> "${JsLiteral.encode(key)}:{\"value\":${JsLiteral.encode(value)}}" }
