package com.music.bitchord.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether a YouTube account is signed in, as one flow for the whole process.
 *
 * [MainViewModel][com.music.bitchord.ui.MainViewModel] owns sign-in and sign-out
 * and writes it; the playback service reads it, so the surfaces it draws — the
 * notification, the home-screen widgets, the watch — offer Like on exactly the
 * tracks the player does. A service started without the app (a widget tap, the
 * watch) seeds it from [AuthStore] the same way the view model does.
 */
object SignedIn {

    @Volatile private var state: MutableStateFlow<Boolean>? = null

    fun state(context: Context): MutableStateFlow<Boolean> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(AuthStore(context.applicationContext).isSignedIn).also { state = it }
        }
}
