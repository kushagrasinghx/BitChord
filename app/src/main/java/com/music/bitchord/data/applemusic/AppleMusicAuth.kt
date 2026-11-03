package com.music.bitchord.data.applemusic

import android.content.Context
import androidx.activity.result.ActivityResultCaller
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings


/** Activity-scoped controller factory; call create once, before the Activity/Fragment is started. */
object AppleMusicAuth {
    private val session by lazy {
        AppleMusicAuthSessionStore(
            readToken = AppleMusicTokens::userToken,
            save = { token, storefront, playbackStorefront ->
                AppleMusicTokens.saveUserToken(token, storefront, playbackStorefront)
                AppSettings.appleMusicStorefront.value = storefront
            },
            clear = AppSettings::disconnectAppleMusic,
        )
    }

    /**
     * Caller must be a lifecycle-aware ActivityResultRegistryOwner (e.g. ComponentActivity).
     * Launch connect in a lifecycle-owned coroutine. Observe state for UI status; a
     * Connected state must also refresh any AppleMusicLibrarySession used for browsing.
     * Call controller.disconnect(), not a direct token clear, to cancel pending sign-in.
     */
    fun create(context: Context, caller: ActivityResultCaller): AppleMusicAuthController {
        val storefront = AppleMusicAuthStorefrontClient(appleMusicTokenClient(Http.client))
        return AppleMusicAuthController(
            adapter = createAppleMusicAuthAdapter(context, caller),
            developerToken = AppleMusicTokens::developerToken,
            resolveStorefront = storefront::resolve,
            session = session,
            configurationValid = { validAppleMusicAuthBackend(BuildConfig.APPLE_MUSIC_TOKEN_URL) },
        )
    }
}
