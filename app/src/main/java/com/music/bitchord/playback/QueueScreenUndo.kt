package com.music.bitchord.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ACTION_QUEUE_REMOVE_WITH_UNDO = "com.music.bitchord.action.QUEUE_REMOVE_WITH_UNDO"
const val ACTION_QUEUE_REMOVE_ENTRIES_WITH_UNDO = "com.music.bitchord.action.QUEUE_REMOVE_ENTRIES_WITH_UNDO"
const val ACTION_QUEUE_CLEAR_WITH_UNDO = "com.music.bitchord.action.QUEUE_CLEAR_WITH_UNDO"
const val ACTION_QUEUE_UNDO = "com.music.bitchord.action.QUEUE_UNDO"
const val EXTRA_QUEUE_UNDO_TOKEN = "bitchord.queueUndo.token"
const val EXTRA_QUEUE_BULK_MEDIA_IDS = "bitchord.queueBulk.mediaIds"
const val EXTRA_QUEUE_BULK_ENTRY_IDS = "bitchord.queueBulk.entryIds"
const val EXTRA_QUEUE_BULK_ENTRY_ID_PRESENT = "bitchord.queueBulk.entryIdPresent"
const val EXTRA_QUEUE_BULK_OCCURRENCES = "bitchord.queueBulk.occurrences"

/** Process-local, latest-only UI feedback; never persisted or sent to a party. */
data class QueueUndoNotice(
    val token: String,
    val message: String,
    val removedCount: Int? = null,
    val skippedCount: Int = 0,
)

object QueueScreenUndo {
    private val mutableNotice = MutableStateFlow<QueueUndoNotice?>(null)
    val notice: StateFlow<QueueUndoNotice?> = mutableNotice.asStateFlow()

    internal fun publish(notice: QueueUndoNotice) {
        mutableNotice.value = notice
    }

    internal fun clear() {
        mutableNotice.value = null
    }
}
