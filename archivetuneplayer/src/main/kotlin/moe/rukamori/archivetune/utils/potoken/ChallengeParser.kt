/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

private val json = Json { ignoreUnknownKeys = true }

fun parseCreateChallenge(rawResponse: String): String {
    val outer = json.parseToJsonElement(rawResponse).jsonArray

    val challenge =
        if (outer.size > 1 && outer[1].jsonPrimitive.isString) {
            val decoded = descramble(outer[1].jsonPrimitive.content)
            json.parseToJsonElement(decoded).jsonArray
        } else {
            outer[0].jsonArray
        }

    val program = challenge[4].jsonPrimitive.content
    val globalName = challenge[5].jsonPrimitive.content

    val interpreterJs =
        challenge[1]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.firstOrNull { it.jsonPrimitive.isString }

    val interpreterUrl =
        challenge[2]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.firstOrNull { it.jsonPrimitive.isString }

    return json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "program" to JsonPrimitive(program),
                "globalName" to JsonPrimitive(globalName),
                "interpreterJavascript" to
                    JsonObject(
                        mapOf(
                            "privateDoNotAccessOrElseSafeScriptWrappedValue" to (interpreterJs ?: JsonNull),
                            "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to (interpreterUrl ?: JsonNull),
                        ),
                    ),
            ),
        ),
    )
}

fun parseIntegrityToken(rawResponse: String): Pair<String, Long> {
    val arr = json.parseToJsonElement(rawResponse).jsonArray
    val tokenU8 = base64ToJsUint8Array(arr[0].jsonPrimitive.content)
    val lifetimeSeconds = arr[1].jsonPrimitive.long
    return tokenU8 to lifetimeSeconds
}

fun stringToJsUint8Array(identifier: String): String {
    val bytes = identifier.toByteArray(Charsets.UTF_8)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

fun commaSeparatedBytesToBase64(commaBytes: String): String =
    commaBytes
        .split(",")
        .map { it.trim().toInt().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace('+', '-')
        .replace('/', '_')

private fun descramble(base64Payload: String): String =
    base64ToByteArray(base64Payload)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

private fun base64ToJsUint8Array(base64: String): String {
    val bytes = base64ToByteArray(base64)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

private fun base64ToByteArray(base64: String): ByteArray {
    val normalised =
        base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
    return (
        normalised.decodeBase64()
            ?: throw PoTokenException("Cannot decode base64: ${base64.take(40)}…")
    ).toByteArray()
}
