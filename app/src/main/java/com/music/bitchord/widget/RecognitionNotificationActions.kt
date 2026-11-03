package com.music.bitchord.widget

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Relays recognition-notification button taps into the Compose app flow. */
object RecognitionNotificationActions {

    sealed interface Action {
        val query: String

        data class PlayNow(override val query: String) : Action
        data class AddToPlaylist(override val query: String) : Action
        data class OpenSearch(override val query: String) : Action
    }

    private val _pending = MutableStateFlow<Action?>(null)

    val pending: StateFlow<Action?> = _pending.asStateFlow()

    fun consume(intent: Intent?): Boolean {
        if (intent == null || intent.getBooleanExtra(EXTRA_CONSUMED, false)) return false
        val query = intent.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
        if (query.isEmpty()) return false
        val action = when (intent.action) {
            ACTION_PLAY_NOW -> Action.PlayNow(query)
            ACTION_ADD_TO_PLAYLIST -> Action.AddToPlaylist(query)
            ACTION_OPEN_SEARCH -> Action.OpenSearch(query)
            else -> return false
        }
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = action
        return true
    }

    fun handled() {
        _pending.value = null
    }

    fun intent(context: Context, action: String, query: String): Intent =
        Intent(context, com.music.bitchord.MainActivity::class.java).apply {
            this.action = action
            putExtra(EXTRA_QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    const val ACTION_PLAY_NOW = "com.music.bitchord.action.RECOGNITION_PLAY_NOW"
    const val ACTION_ADD_TO_PLAYLIST = "com.music.bitchord.action.RECOGNITION_ADD_TO_PLAYLIST"
    const val ACTION_OPEN_SEARCH = "com.music.bitchord.action.RECOGNITION_OPEN_SEARCH"

    private const val EXTRA_QUERY = "com.music.bitchord.extra.RECOGNITION_QUERY"
    private const val EXTRA_CONSUMED = "com.music.bitchord.extra.RECOGNITION_CONSUMED"
}
