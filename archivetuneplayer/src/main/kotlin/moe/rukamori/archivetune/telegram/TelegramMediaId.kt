/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Media id codec for Telegram channel tracks. A Telegram track is addressed by
 * its chat + message (stable across sessions) plus a server-stable unique file
 * id ("<docId>:<dcId>").
 *
 * v2 format:  telegram://track/v2/<chatId>/<messageId>/<uniqueFileId>
 * v1 format:  telegram://track/<chatId>/<messageId>[/<tdlibFileId>[/<uniqueId>]]
 *
 * v1 ids (written by the original TDLib build) are still decoded: chat +
 * message survive engine swaps, while the TDLib-local file ids (only valid
 * inside a TDLib database) are ignored and re-resolved from the message when
 * the track is played.
 *
 * Kept free of Android imports so it can be covered by plain JVM unit tests.
 */

package moe.rukamori.archivetune.telegram

const val TELEGRAM_MEDIA_ID_SCHEME = "telegram"

private const val PREFIX = "$TELEGRAM_MEDIA_ID_SCHEME://track/"
private const val V2_PREFIX = "${PREFIX}v2/"

data class TelegramMediaId(
    val chatId: Long,
    val messageId: Long,
    val fileUniqueId: String = "",
) {
    fun encode(): String =
        buildString {
            append(V2_PREFIX)
            append(chatId)
            append('/')
            append(messageId)
            if (fileUniqueId.isNotEmpty()) {
                append('/')
                append(fileUniqueId)
            }
        }

    companion object {
        fun decode(mediaId: String): TelegramMediaId? {
            when {
                mediaId.startsWith(V2_PREFIX) -> {
                    val segments = mediaId.removePrefix(V2_PREFIX).split('/')
                    if (segments.size < 2) return null
                    val chatId = segments[0].toLongOrNull() ?: return null
                    val messageId = segments[1].toLongOrNull() ?: return null
                    if (chatId == 0L || messageId == 0L) return null
                    return TelegramMediaId(
                        chatId = chatId,
                        messageId = messageId,
                        fileUniqueId = segments.getOrNull(2).orEmpty(),
                    )
                }

                mediaId.startsWith(PREFIX) -> {
                    val segments = mediaId.removePrefix(PREFIX).split('/')
                    if (segments.size < 2) return null
                    val chatId = segments[0].toLongOrNull() ?: return null
                    val messageId = segments[1].toLongOrNull() ?: return null
                    if (chatId == 0L || messageId == 0L) return null
                    return TelegramMediaId(
                        chatId = chatId,
                        messageId = messageId,
                        fileUniqueId = segments.lastOrNull { it.contains(':') }.orEmpty(),
                    )
                }
            }
            return null
        }
    }
}

fun String.isTelegramMediaId(): Boolean = startsWith(PREFIX) && TelegramMediaId.decode(this) != null
