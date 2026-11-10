/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import java.io.ByteArrayOutputStream

internal object SpotifyCanvazProtocol {
    private const val WIRE_VARINT = 0
    private const val WIRE_64BIT = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_START_GROUP = 3
    private const val WIRE_END_GROUP = 4
    private const val WIRE_32BIT = 5

    private const val FIELD_ENTITIES = 1

    private const val FIELD_ENTITY_URI = 1

    private const val FIELD_CANVAZ_URL = 2

    private const val FIELD_CANVAZ_ENTITY_URI = 5

    data class CanvazEntry(
        val entityUri: String?,
        val url: String?,
    )

    fun encodeRequest(trackUris: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        for (uri in trackUris) {
            if (uri.isBlank()) continue
            val entity = ByteArrayOutputStream()
            writeStringField(entity, FIELD_ENTITY_URI, uri)
            writeBytesField(out, FIELD_ENTITIES, entity.toByteArray())
        }
        return out.toByteArray()
    }

    fun decodeResponse(bytes: ByteArray): List<CanvazEntry> {
        val reader = Reader(bytes)
        val entries = mutableListOf<CanvazEntry>()
        while (reader.hasRemaining()) {
            val tag = reader.readTag() ?: break
            if (tag.field == FIELD_ENTITIES && tag.wire == WIRE_LENGTH_DELIMITED) {
                val payload = reader.readLengthDelimited() ?: break
                entries += decodeCanvaz(payload)
            } else if (!reader.skip(tag.wire)) {
                break
            }
        }
        return entries
    }

    private fun decodeCanvaz(bytes: ByteArray): CanvazEntry {
        val reader = Reader(bytes)
        var url: String? = null
        var entityUri: String? = null
        while (reader.hasRemaining()) {
            val tag = reader.readTag() ?: break
            if (tag.wire == WIRE_LENGTH_DELIMITED) {
                val payload = reader.readLengthDelimited() ?: break
                when (tag.field) {
                    FIELD_CANVAZ_URL -> url = payload.decodeToString()
                    FIELD_CANVAZ_ENTITY_URI -> entityUri = payload.decodeToString()
                    else -> Unit
                }
            } else if (!reader.skip(tag.wire)) {
                break
            }
        }
        return CanvazEntry(entityUri = entityUri, url = url)
    }

    private fun writeStringField(
        out: ByteArrayOutputStream,
        field: Int,
        value: String,
    ) = writeBytesField(out, field, value.encodeToByteArray())

    private fun writeBytesField(
        out: ByteArrayOutputStream,
        field: Int,
        value: ByteArray,
    ) {
        writeVarint(out, ((field.toLong() shl 3) or WIRE_LENGTH_DELIMITED.toLong()))
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    private fun writeVarint(
        out: ByteArrayOutputStream,
        value: Long,
    ) {
        var remaining = value
        while (true) {
            val chunk = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining == 0L) {
                out.write(chunk)
                return
            }
            out.write(chunk or 0x80)
        }
    }

    private data class Tag(
        val field: Int,
        val wire: Int,
    )

    private class Reader(
        private val bytes: ByteArray,
    ) {
        private var position = 0

        fun hasRemaining(): Boolean = position < bytes.size

        fun readTag(): Tag? {
            val raw = readVarint() ?: return null
            val field = (raw ushr 3).toInt()
            val wire = (raw and 0x07).toInt()
            if (field <= 0) return null
            return Tag(field = field, wire = wire)
        }

        fun readVarint(): Long? {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (position >= bytes.size) return null
                val byte = bytes[position++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        fun readLengthDelimited(): ByteArray? {
            val length = readVarint()?.toInt() ?: return null
            if (length < 0 || position + length > bytes.size) return null
            val slice = bytes.copyOfRange(position, position + length)
            position += length
            return slice
        }

        fun skip(wire: Int): Boolean =
            when (wire) {
                WIRE_VARINT -> readVarint() != null
                WIRE_64BIT -> advance(8)
                WIRE_LENGTH_DELIMITED -> readLengthDelimited() != null
                WIRE_32BIT -> advance(4)

                WIRE_START_GROUP, WIRE_END_GROUP -> false
                else -> false
            }

        private fun advance(count: Int): Boolean {
            if (position + count > bytes.size) return false
            position += count
            return true
        }
    }
}
