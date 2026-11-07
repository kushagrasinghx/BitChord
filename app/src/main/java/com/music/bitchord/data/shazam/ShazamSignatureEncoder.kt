package com.music.bitchord.data.shazam

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32

/**
 * Writes a [ShazamSignature] in the binary packet Shazam's discovery endpoint
 * accepts, and wraps it in the `data:` URI the request body carries.
 *
 * The layout is the one SongRec and shazamio-core produce. Every integer is
 * little-endian, including the length and CRC fix-ups at the end:
 *
 * - a 48-byte header: magic, CRC placeholder, payload size, second magic,
 *   sample-rate id, source length, and the fixed field the reference writes
 * - `0x40000000` and the payload size again
 * - one block per non-empty frequency band, in band order: band id, byte
 *   length, the delta-encoded peaks, then zero padding to a four-byte boundary
 *
 * A peak is a pass-number delta (`0xff` plus an absolute `u32` when the delta
 * does not fit in a byte), a `u16` magnitude and a `u16` corrected frequency
 * bin. The CRC32 covers everything from byte 8 to the end, which is why it is
 * filled in only after all the blocks are written.
 *
 * The encoder is pure Kotlin so the run-length rules can be tested on the JVM.
 */
object ShazamSignatureEncoder {

    const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"

    private const val HEADER_SIZE = 48
    private const val MAGIC_1 = 0xcafe2580.toInt()
    private const val MAGIC_2 = 0x94119c00.toInt()
    private const val FIXED_VALUE = (15 shl 19) + 0x40000
    private const val BAND_BLOCK_MAGIC = 0x60030040
    private const val LONG_DELTA_MARKER = 0xff

    /** Encodes only the binary packet, without the data-URI wrapper. */
    fun encode(signature: ShazamSignature): ByteArray {
        val output = ByteArrayOutputStream()
        writeU32(output, MAGIC_1)
        writeU32(output, 0) // CRC32, filled in below once the bytes exist.
        writeU32(output, 0) // Payload size, filled in below.
        writeU32(output, MAGIC_2)
        repeat(3) { writeU32(output, 0) }
        writeU32(output, sampleRateId(signature.sampleRateHz) shl 27)
        repeat(2) { writeU32(output, 0) }
        writeU32(
            output,
            signature.sampleCount + (signature.sampleRateHz * SAMPLE_RATE_FRACTION).toInt(),
        )
        writeU32(output, FIXED_VALUE)
        writeU32(output, 0x40000000)
        writeU32(output, 0) // Payload size repeated after the fixed chunk.

        for (band in ShazamFrequencyBand.entries) {
            val peaks = signature.peaksByBand[band].orEmpty()
            if (peaks.isEmpty()) continue
            val encodedPeaks = encodePeaks(peaks)
            writeU32(output, BAND_BLOCK_MAGIC + band.index)
            writeU32(output, encodedPeaks.size)
            output.write(encodedPeaks)
            while (output.size() % 4 != 0) output.write(0)
        }

        val bytes = output.toByteArray()
        check(bytes.size >= HEADER_SIZE)
        val payloadSize = bytes.size - HEADER_SIZE
        writeU32At(bytes, 8, payloadSize)
        writeU32At(bytes, HEADER_SIZE + 4, payloadSize)
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }
        writeU32At(bytes, 4, crc.value.toInt())
        return bytes
    }

    /** The `signature.uri` value: the packet above, standard base64, no wrapping. */
    fun encodeToUri(signature: ShazamSignature): String =
        DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encode(signature))

    private fun encodePeaks(peaks: List<ShazamPeak>): ByteArray {
        val output = ByteArrayOutputStream()
        var previousPass = 0
        for (peak in peaks.sortedBy { it.fftPassNumber }) {
            require(peak.fftPassNumber >= previousPass) {
                "Shazam peaks must be in pass-number order"
            }
            var delta = peak.fftPassNumber - previousPass
            if (delta >= 255) {
                output.write(LONG_DELTA_MARKER)
                writeU32(output, peak.fftPassNumber)
                previousPass = peak.fftPassNumber
                delta = 0
            }
            output.write(delta)
            writeU16(output, peak.magnitude)
            writeU16(output, peak.frequencyBin)
            previousPass = peak.fftPassNumber
        }
        return output.toByteArray()
    }

    private fun sampleRateId(sampleRateHz: Int): Int = when (sampleRateHz) {
        8_000 -> 1
        11_025 -> 2
        16_000 -> 3
        32_000 -> 4
        44_100 -> 5
        48_000 -> 6
        else -> error("Unsupported Shazam sample rate: $sampleRateHz")
    }

    private fun writeU16(output: ByteArrayOutputStream, value: Int) {
        require(value in 0..0xffff) { "Value $value does not fit in u16" }
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeU32(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    private fun writeU32At(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    /** 0.24 seconds, as written by the reference encoder into the length field. */
    private const val SAMPLE_RATE_FRACTION = 0.24f
}
