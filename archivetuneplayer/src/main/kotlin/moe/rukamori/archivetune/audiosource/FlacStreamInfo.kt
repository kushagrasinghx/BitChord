/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

object FlacStreamInfo {
    const val REQUIRED_BYTES: Int = 42

    private const val MAGIC_LENGTH = 4
    private const val BLOCK_HEADER_LENGTH = 4
    private const val STREAMINFO_LENGTH = 34
    private const val BLOCK_TYPE_STREAMINFO = 0

    private const val PACKED_FIELD_OFFSET = 10

    data class Info(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,

        val totalSamples: Long?,
    ) {
        val durationMs: Long?
            get() {
                val samples = totalSamples ?: return null
                if (samples <= 0L || sampleRate <= 0) return null
                return samples * 1000L / sampleRate
            }

        val pcmBitrate: Int
            get() = sampleRate * bitDepth * channels
    }

    fun parse(bytes: ByteArray): Info? {
        if (bytes.size < MAGIC_LENGTH + BLOCK_HEADER_LENGTH + STREAMINFO_LENGTH) return null
        if (bytes[0] != 'f'.code.toByte() ||
            bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'a'.code.toByte() ||
            bytes[3] != 'C'.code.toByte()
        ) {
            return null
        }

        val blockType = bytes[MAGIC_LENGTH].toInt() and 0x7F
        if (blockType != BLOCK_TYPE_STREAMINFO) return null
        val blockLength = readUnsigned(bytes, MAGIC_LENGTH + 1, 3).toInt()
        if (blockLength != STREAMINFO_LENGTH) return null

        val packedOffset = MAGIC_LENGTH + BLOCK_HEADER_LENGTH + PACKED_FIELD_OFFSET
        val packed = readUnsigned(bytes, packedOffset, 8)

        val sampleRate = ((packed ushr 44) and 0xFFFFF).toInt()
        val channels = (((packed ushr 41) and 0x7).toInt()) + 1
        val bitDepth = (((packed ushr 36) and 0x1F).toInt()) + 1
        val totalSamples = packed and 0xF_FFFF_FFFFL

        if (sampleRate <= 0) return null

        return Info(
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            totalSamples = totalSamples.takeIf { it > 0L },
        )
    }

    private fun readUnsigned(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Long {
        var value = 0L
        for (index in 0 until length) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return value
    }
}
