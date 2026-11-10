/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    val playbackUrl: String?,
    val replayGainTrackDb: Double? = null,
    val replayGainAlbumDb: Double? = null,
)

fun FormatEntity.containerLabel(): String = mimeType.substringAfter("/").substringBefore(";").uppercase()

fun FormatEntity.fileExtension(): String {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.lowercase()
    val rawMime = mimeType.substringAfter("/").substringBefore(";").lowercase()
    return when {
        rawCodec.contains("flac") -> "flac"
        rawCodec.contains("alac") -> "m4a"
        rawCodec.contains("opus") || rawMime.contains("opus") -> "opus"
        rawCodec.contains("aac") || rawCodec.contains("mp4a") || rawMime.contains("mp4") -> "m4a"
        rawCodec.contains("vorbis") -> "ogg"
        rawCodec.contains("mp3") || rawMime.contains("mpeg") -> "mp3"
        rawMime.contains("wav") || rawCodec.contains("pcm") -> "wav"
        else -> "bin"
    }
}

fun FormatEntity.exportMimeType(): String {
    val ext = fileExtension()
    return when (ext) {
        "flac" -> "audio/flac"
        "opus" -> "audio/opus"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}

fun FormatEntity.isLossless(): Boolean {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    return rawCodec.contains("FLAC") || rawCodec.contains("ALAC")
}

fun FormatEntity.codecLabel(): String {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    val rawMime = mimeType.substringAfter("/").substringBefore(";").uppercase()

    return when {
        rawCodec.contains("FLAC") || rawCodec.contains("ALAC") -> "Lossless"
        rawCodec.contains("OPUS") -> "OPUS"
        rawCodec.contains("AAC") || rawCodec.contains("MP4A") -> "AAC"
        rawCodec.contains("VORBIS") -> "VORBIS"
        rawMime.contains("OPUS") -> "OPUS"
        rawMime.contains("AAC") || rawMime.contains("MP4A") -> "AAC"
        rawMime.contains("VORBIS") -> "VORBIS"
        rawMime.isNotBlank() -> rawMime
        else -> rawCodec
    }
}

fun FormatEntity.formattedBitrate(): String? = bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }

fun FormatEntity.formattedSampleRate(): String? =
    sampleRate?.takeIf { it > 0 }?.let {
        "${(it / 100.0).roundToInt() / 10.0} kHz"
    }

fun FormatEntity.formattedFileSize(): String =
    contentLength.takeIf { it > 0 }?.let {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = it.toDouble()
        var unitIndex = 0

        while (size >= 1024.0 && unitIndex < units.lastIndex) {
            size /= 1024.0
            unitIndex++
        }

        var rounded =
            if (size >= 99.95) {
                size.roundToInt().toDouble()
            } else {
                (size * 10.0).roundToInt() / 10.0
            }

        if (rounded >= 1023.95 && unitIndex < units.lastIndex) {
            rounded = 1.0
            unitIndex++
        }

        if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()} ${units[unitIndex]}"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", rounded, units[unitIndex])
        }
    } ?: ""

fun detectAudioExtensionFromSpans(
    spans: java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan>,
): String {
    val firstSpan = spans.firstOrNull() ?: return "mp3"
    val file = firstSpan.file ?: return "mp3"
    if (!file.exists() || file.length() < 12) return "mp3"
    val header = ByteArray(12)
    file.inputStream().use { if (it.read(header) < 4) return "mp3" }
    return when {
        header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() && header[3] == 0x43.toByte() -> "flac"

        header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() && header[3] == 0x53.toByte() -> "opus"

        header.size >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() && header[7] == 0x70.toByte() -> "m4a"

        header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() -> "wav"

        header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "webm"

        header[0] == 0x49.toByte() && header[1] == 0x44.toByte() &&
            header[2] == 0x33.toByte() -> "mp3"
        (header[0].toInt() and 0xFF) == 0xFF &&
            (header[1].toInt() and 0xE0) == 0xE0 -> "mp3"
        else -> "mp3"
    }
}

fun extensionToMimeType(ext: String): String = when (ext) {
    "flac" -> "audio/flac"
    "opus" -> "audio/opus"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "mp3" -> "audio/mpeg"
    "webm" -> "audio/webm"
    else -> "application/octet-stream"
}

enum class RatePriority {
    BITRATE_FIRST,
    SAMPLE_RATE_FIRST,
}

fun FormatEntity.autoRateDisplay(priority: RatePriority = RatePriority.BITRATE_FIRST): String {
    val hasBitrate = bitrate > 0
    val hasSampleRate = sampleRate != null && sampleRate > 0

    return when (priority) {
        RatePriority.BITRATE_FIRST -> {
            when {
                hasBitrate -> formattedBitrate() ?: ""
                hasSampleRate -> formattedSampleRate() ?: ""
                else -> "Unknown"
            }
        }

        RatePriority.SAMPLE_RATE_FIRST -> {
            when {
                hasSampleRate -> formattedSampleRate() ?: ""
                hasBitrate -> formattedBitrate() ?: ""
                else -> "Unknown"
            }
        }
    }
}
