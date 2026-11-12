package com.music.bitchord.playback.dsd

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The stock extractors plus [DsdExtractor] ahead of them. Media3 has no DSD
 * support of any kind, so without this a `.dsf` or `.dff` fails to sniff.
 */
@UnstableApi
class DsdExtractorsFactory(private val delegate: ExtractorsFactory = DefaultExtractorsFactory()) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = arrayOf(DsdExtractor(), *delegate.createExtractors())
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        arrayOf(DsdExtractor(), *delegate.createExtractors(uri, responseHeaders))
}

/**
 * DSF and DFF (plain or DST-compressed) as float PCM at 176.4 kHz.
 *
 * The decoding happens here rather than in a renderer: the track goes out as
 * `audio/raw`, so the ordinary audio renderer and every sink and DSP stage
 * after it treat a DSD file like any other hi-res PCM track. Nothing on the
 * phone ever sees DSD itself.
 */
@UnstableApi
class DsdExtractor : Extractor {
    private enum class State { HEADER, DFF_CHUNKS, DST_CHUNKS, DSF_METADATA, AUDIO }

    private var state = State.HEADER
    private lateinit var output: ExtractorOutput
    private var track: TrackOutput? = null
    private var format: DsdFormat? = null
    private var decoder: DsdBlockDecoder? = null
    private var rawSource: DsdRawSource? = null
    private var fileEnd = 0L
    private var scanPosition = 0L
    private var metadataOffset = 0L
    private var chunks = 0
    private var versionSeen = false
    private var properties: DsdHeaders.DffProperties? = null
    private val dstFrames = ArrayList<DstFrame>()
    private var dstDecoder: DstDecoder? = null
    private var dstScan = 0L
    private var dstEnd = 0L
    private var dstCount = 0
    private var dstFrame = 0
    private var dstByte = 0
    private var dstLoaded = false
    private var dstDecoded = ByteArray(0)
    private var declaredDstIndex: List<Pair<Long, Int>>? = null
    private var dstIndexSeen = false
    private var dffDataOffset = -1L
    private var dffDataBytes = 0L
    private val metadata = ArrayList<Metadata.Entry>()
    private val cache = ByteArray(8192)
    private val pcm = FloatArray(4160)
    private val pcmBytes = ByteArray(16640)
    private val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
    private val packet = ParsableByteArray(pcmBytes)

    override fun sniff(input: ExtractorInput): Boolean {
        val bytes = ByteArray(4)
        return try {
            input.peekFully(bytes, 0, 4, true) &&
                (bytes.contentEquals("DSD ".toByteArray()) || bytes.contentEquals("FRM8".toByteArray()))
        } catch (_: EOFException) {
            false
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) { this.output = output }

    override fun release() {
        decoder?.close(); decoder = null
        dstDecoder?.close(); dstDecoder = null
    }

    override fun seek(position: Long, timeUs: Long) {
        val f = format ?: return
        val frame = f.seekFrame(timeUs)
        val requestedByte = f.prerollByte(frame)
        // DST frames can only be decoded whole, so start at the one holding the preroll.
        val sourceByte = if (dstFrames.isNotEmpty()) requestedByte / DST_FRAME_BYTES * DST_FRAME_BYTES else requestedByte
        decoder?.reset(sourceByte, frame)
        if (dstFrames.isNotEmpty()) {
            dstFrame = (sourceByte / DST_FRAME_BYTES).toInt(); dstByte = 0; dstLoaded = false
            dstDecoder?.close(); dstDecoder = DstDecoder(f.channels)
        } else {
            rawSource?.seek(sourceByte)
        }
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = try {
        when (state) {
            State.HEADER -> readHeader(input)
            State.DFF_CHUNKS -> readDffChunk(input, seekPosition)
            State.DST_CHUNKS -> readDstChunk(input, seekPosition)
            State.DSF_METADATA -> readDsfMetadata(input, seekPosition)
            State.AUDIO -> readAudio(input, seekPosition)
        }
    } catch (failure: IllegalArgumentException) {
        throw ParserException.createForMalformedContainer(failure.message ?: "Invalid DSD file.", failure)
    } catch (failure: EOFException) {
        throw ParserException.createForMalformedContainer("Truncated DSD file.", failure)
    } catch (failure: UnsupportedOperationException) {
        throw ParserException.createForUnsupportedContainerFeature(failure.message ?: "Unsupported DSD format.")
    }

    private fun readHeader(input: ExtractorInput): Int {
        require(input.position == 0L) { "Missing DSD header." }
        val first = bytes(input, 12)
        val id = String(first, 0, 4, Charsets.US_ASCII)
        if (id == "DSD ") {
            require(long(first, 4, true) == 28L) { "Invalid DSF header." }
            val header = bytes(input, 16)
            fileEnd = long(header, 0, true)
            metadataOffset = long(header, 8, true)
            checkLength(input)
            val fmt = bytes(input, 12)
            require(String(fmt, 0, 4, Charsets.US_ASCII) == "fmt ") { "Missing DSF format." }
            val size = long(fmt, 4, true)
            require(size in 52..4096) { "Invalid DSF format size." }
            val body = bytes(input, size.toInt() - 12)
            val data = bytes(input, 12)
            require(String(data, 0, 4, Charsets.US_ASCII) == "data") { "Missing DSF data." }
            val dataSize = long(data, 4, true)
            require(dataSize >= 12)
            val end = boundedEnd(input.position, dataSize - 12, fileEnd)
            require(if (metadataOffset == 0L) end == fileEnd else metadataOffset == end && metadataOffset < fileEnd) {
                "Invalid DSF metadata offset."
            }
            format = DsdHeaders.dsf(body, input.position, dataSize - 12)
            if (metadataOffset > 0) state = State.DSF_METADATA else startAudio()
        } else {
            require(id == "FRM8") { "Unsupported DSD container." }
            val size = long(first, 4, false)
            require(size >= 4 && size <= Long.MAX_VALUE - 12)
            fileEnd = size + 12
            checkLength(input)
            require(String(bytes(input, 4), Charsets.US_ASCII) == "DSD ") { "Unsupported DFF form." }
            scanPosition = input.position
            state = State.DFF_CHUNKS
        }
        return Extractor.RESULT_CONTINUE
    }

    private fun readDffChunk(input: ExtractorInput, seek: PositionHolder): Int {
        if (scanPosition == fileEnd) {
            val p = requireNotNull(properties) { "Missing DFF properties." }
            require(versionSeen && dffDataOffset >= 0 && dffDataBytes > 0) { "Missing DFF data." }
            if (p.dst) {
                require(dstCount > 0 && dstFrames.size == dstCount) { "DST frame count does not match its header." }
                require(dstFrames.none { it.crc != null } || dstFrames.all { it.crc != null }) { "Incomplete DST CRC list." }
                declaredDstIndex?.let { entries ->
                    require(entries.size == dstFrames.size && entries.indices.all { entries[it] == dstFrames[it].let { f -> f.offset to f.size } }) {
                        "DST index does not match its frames."
                    }
                }
            } else {
                require(dffDataBytes % p.channels == 0L && dffDataBytes / p.channels <= Long.MAX_VALUE / 8) { "Misaligned DFF data." }
            }
            val samples = if (p.dst) dstCount * DST_FRAME_SAMPLES else dffDataBytes / p.channels * 8
            format = DsdFormat(DsdContainer.DFF, p.rate, p.channels, samples, dffDataOffset, dffDataBytes)
            startAudio()
            return Extractor.RESULT_CONTINUE
        }
        if (input.position != scanPosition) return reposition(seek, scanPosition)
        require(++chunks <= 4096 && fileEnd - scanPosition >= 12) { "Invalid DFF chunk count or size." }
        val header = bytes(input, 12)
        val id = String(header, 0, 4, Charsets.US_ASCII)
        val size = long(header, 4, false)
        val end = boundedEnd(input.position, size, fileEnd)
        scanPosition = boundedEnd(end, size and 1, fileEnd)
        require(versionSeen || id == "FVER") { "DFF must begin with its version." }
        when (id) {
            "FVER" -> {
                require(!versionSeen && size == 4L) { "Invalid DFF version chunk." }
                val version = ByteBuffer.wrap(bytes(input, 4)).int
                require(version ushr 24 == 1) { "Unsupported DFF version." }
                versionSeen = true
            }
            "PROP" -> {
                require(properties == null && dffDataOffset < 0 && size in 4..65_536) { "Invalid DFF properties." }
                properties = DsdHeaders.dffSoundProperties(bytes(input, size.toInt()))
            }
            "DSD " -> {
                require(properties != null && properties?.dst == false && dffDataOffset < 0) { "Invalid or duplicate DFF sound data." }
                dffDataOffset = input.position; dffDataBytes = size
            }
            "DST " -> {
                require(properties?.dst == true && dffDataOffset < 0 && size >= 18) { "Invalid DST sound data." }
                dffDataOffset = input.position; dffDataBytes = size
                dstScan = input.position; dstEnd = end; state = State.DST_CHUNKS
            }
            "DSTI" -> {
                require(properties?.dst == true && !dstIndexSeen && size in 12..12_000_000 && size % 12 == 0L) { "Invalid DST index." }
                dstIndexSeen = true
                val index = ByteBuffer.wrap(bytes(input, size.toInt()))
                declaredDstIndex = List(size.toInt() / 12) {
                    val offset = index.long
                    val length = index.int
                    require(offset >= dffDataOffset && length in 2..1_048_576 && offset <= fileEnd - length) { "Invalid DST index entry." }
                    offset to length
                }
            }
            "DIIN" -> if (size <= MAX_METADATA_BYTES) readDffText(bytes(input, size.toInt()))
            "ID3 " -> if (size <= MAX_METADATA_BYTES) addId3(bytes(input, size.toInt()))
        }
        return Extractor.RESULT_CONTINUE
    }

    private fun readDstChunk(input: ExtractorInput, seek: PositionHolder): Int {
        if (dstScan == dstEnd) { state = State.DFF_CHUNKS; return Extractor.RESULT_CONTINUE }
        if (input.position != dstScan) return reposition(seek, dstScan)
        require(dstEnd - dstScan >= 12) { "Truncated DST chunk." }
        val header = bytes(input, 12)
        val id = String(header, 0, 4, Charsets.US_ASCII)
        val size = long(header, 4, false)
        val end = boundedEnd(input.position, size, dstEnd)
        dstScan = boundedEnd(end, size and 1, dstEnd)
        when (id) {
            "FRTE" -> {
                require(dstCount == 0 && input.position == dffDataOffset + 12 && size == 6L) { "Invalid DST frame header." }
                val info = ByteBuffer.wrap(bytes(input, 6))
                dstCount = info.int
                require(dstCount in 1..1_000_000 && info.short.toInt() == 75) { "Unsupported DST frame count or rate." }
            }
            "DSTF" -> {
                require(dstCount > 0 && dstFrames.size < dstCount && size in 2..1_048_576) { "Invalid DST frame." }
                dstFrames += DstFrame(input.position, size.toInt())
                input.skipFully(size.toInt() + (size and 1).toInt())
            }
            "DSTC" -> {
                require(size == 4L && dstFrames.isNotEmpty() && dstFrames.last().crc == null) { "Invalid DST CRC." }
                val previous = dstFrames.last()
                require(input.position - 12 == previous.offset + previous.size + (previous.size and 1)) { "DST CRC must follow its frame." }
                dstFrames[dstFrames.lastIndex] = previous.copy(crc = ByteBuffer.wrap(bytes(input, 4)).int)
            }
            else -> throw IllegalArgumentException("Unsupported DST subchunk.")
        }
        return Extractor.RESULT_CONTINUE
    }

    private fun readDsfMetadata(input: ExtractorInput, seek: PositionHolder): Int {
        if (input.position != metadataOffset) return reposition(seek, metadataOffset)
        if (fileEnd - metadataOffset >= 10) {
            val header = bytes(input, 10)
            if (String(header, 0, 3, Charsets.US_ASCII) == "ID3" && (6..9).all { header[it].toInt() and 128 == 0 }) {
                var length = 0
                for (i in 6..9) length = (length shl 7) or (header[i].toInt() and 127)
                if (length <= MAX_METADATA_BYTES - 10 && length <= fileEnd - input.position) {
                    val tag = ByteArray(length + 10)
                    header.copyInto(tag)
                    input.readFully(tag, 10, length)
                    addId3(tag)
                }
            }
        }
        startAudio()
        return Extractor.RESULT_CONTINUE
    }

    private fun addId3(bytes: ByteArray) {
        val tag = Id3Decoder().decode(bytes, bytes.size) ?: return
        for (i in 0 until tag.length()) if (metadata.size < 256) metadata.add(tag[i])
    }

    private fun readDffText(bytes: ByteArray) {
        val b = ByteBuffer.wrap(bytes)
        var count = 0
        while (b.hasRemaining()) {
            require(++count <= 1024 && b.remaining() >= 12) { "Invalid DFF metadata." }
            val id = DsdHeaders.id(b)
            val size = b.long
            require(size >= 0 && size <= b.remaining() && size + (size and 1) <= b.remaining()) { "Truncated DFF metadata." }
            val end = b.position() + size.toInt()
            if ((id == "DIAR" || id == "DITI") && size >= 4) {
                val length = b.int
                require(length >= 0 && length <= size - 4) { "Invalid DFF text size." }
                if (length <= 4096 && metadata.size < 256) {
                    val text = ByteArray(length).also(b::get).toString(Charsets.US_ASCII).trimEnd('\u0000')
                    metadata.add(TextInformationFrame(if (id == "DIAR") "TPE1" else "TIT2", null, listOf(text)))
                }
            }
            b.position(end + (size and 1).toInt())
        }
    }

    private fun startAudio() {
        val f = checkNotNull(format)
        decoder?.close()
        decoder = DsdBlockDecoder(f)
        rawSource = DsdRawSource(f)
        dstFrame = 0; dstByte = 0; dstLoaded = false
        if (dstFrames.isNotEmpty()) {
            dstDecoder = DstDecoder(f.channels)
            dstDecoded = ByteArray(DST_FRAME_BYTES * f.channels)
        }
        track = output.track(0, C.TRACK_TYPE_AUDIO).also { track ->
            track.format(
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setContainerMimeType(if (f.container == DsdContainer.DSF) "audio/x-dsf" else "audio/x-dff")
                    .setCodecs("dsd${f.bitRate / 44_100}")
                    .setSampleRate(f.pcmRate)
                    .setChannelCount(f.channels)
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                    .setAverageBitrate(f.bitRate * f.channels)
                    .setMaxInputSize(pcmBytes.size)
                    .setMetadata(Metadata(metadata))
                    .build(),
            )
        }
        output.seekMap(object : SeekMap {
            override fun isSeekable() = true
            override fun getDurationUs() = f.durationUs
            override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
                val frame = f.seekFrame(timeUs)
                val first = f.prerollByte(frame)
                val position = if (dstFrames.isNotEmpty()) {
                    dstFrames[(first / DST_FRAME_BYTES).toInt().coerceAtMost(dstFrames.lastIndex)].offset
                } else {
                    f.position(first)
                }
                return SeekMap.SeekPoints(SeekPoint(frame * 1_000_000L / f.pcmRate, position))
            }
        })
        output.endTracks()
        state = State.AUDIO
    }

    private fun readAudio(input: ExtractorInput, seek: PositionHolder): Int {
        if (dstFrames.isNotEmpty()) return readDstAudio(input, seek)
        val d = checkNotNull(decoder)
        if (d.ended) return Extractor.RESULT_END_OF_INPUT
        val raw = checkNotNull(rawSource)
        val firstFrame = d.outputFrame
        val block = if (raw.ended) {
            null
        } else {
            if (input.position != raw.filePosition) return reposition(seek, raw.filePosition)
            val size = raw.readSize
            input.readFully(cache, 0, size)
            raw.consume(cache, size)
        }
        writePcm(d.decode(block, pcm), firstFrame)
        return Extractor.RESULT_CONTINUE
    }

    private fun readDstAudio(input: ExtractorInput, seek: PositionHolder): Int {
        val f = checkNotNull(format)
        val d = checkNotNull(decoder)
        if (d.ended) return Extractor.RESULT_END_OF_INPUT
        var block: DsdRawBlock? = null
        if (dstFrame < dstFrames.size) {
            val entry = dstFrames[dstFrame]
            if (!dstLoaded) {
                if (input.position != entry.offset) return reposition(seek, entry.offset)
                checkNotNull(dstDecoder).decode(bytes(input, entry.size), dstDecoded)
                require(entry.crc == null || entry.crc == DstFrameCrc.calculate(dstDecoded)) { "DST frame checksum failed." }
                dstLoaded = true; dstByte = 0
            }
            val count = minOf(8192 - 8192 % f.channels, dstDecoded.size - dstByte)
            dstDecoded.copyInto(cache, 0, dstByte, dstByte + count)
            block = DsdRawBlock(cache, count, dstFrame * DST_FRAME_SAMPLES + dstByte / f.channels * 8L, count / f.channels * 8L)
            dstByte += count
            if (dstByte == dstDecoded.size) { dstLoaded = false; dstFrame++ }
        }
        val frame = d.outputFrame
        writePcm(d.decode(block, pcm), frame)
        return Extractor.RESULT_CONTINUE
    }

    private fun writePcm(frames: Int, firstFrame: Long) {
        if (frames <= 0) return
        val f = checkNotNull(format)
        pcmBuffer.clear()
        repeat(frames * f.channels) { pcmBuffer.putFloat(pcm[it]) }
        val size = frames * f.channels * 4
        packet.reset(pcmBytes, size)
        val output = checkNotNull(track)
        output.sampleData(packet, size)
        output.sampleMetadata(firstFrame * 1_000_000L / f.pcmRate, C.BUFFER_FLAG_KEY_FRAME, size, 0, null)
    }

    private fun checkLength(input: ExtractorInput) {
        require(fileEnd >= 16 && (input.length == C.LENGTH_UNSET.toLong() || input.length == fileEnd)) {
            "DSD container length does not match the file."
        }
    }

    private fun bytes(input: ExtractorInput, count: Int): ByteArray = ByteArray(count).also { input.readFully(it, 0, count) }

    private fun long(bytes: ByteArray, offset: Int, little: Boolean): Long =
        ByteBuffer.wrap(bytes).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).getLong(offset)

    private fun boundedEnd(position: Long, size: Long, limit: Long): Long {
        require(position in 0..limit && size >= 0 && size <= limit - position) { "DSD chunk exceeds the file." }
        return position + size
    }

    private fun reposition(holder: PositionHolder, position: Long): Int {
        holder.position = position
        return Extractor.RESULT_SEEK
    }

    private companion object {
        const val MAX_METADATA_BYTES = 4 * 1024 * 1024
        /** One DST frame: 1/75 s of DSD64 per channel, in bytes and in 1-bit samples. */
        const val DST_FRAME_BYTES = 4704
        const val DST_FRAME_SAMPLES = 37_632L
    }
}
