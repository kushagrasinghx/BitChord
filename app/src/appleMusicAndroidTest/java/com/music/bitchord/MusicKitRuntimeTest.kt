package com.music.bitchord

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.apple.android.music.playback.model.PlaybackShuffleMode
import com.apple.android.music.playback.player.MediaPlayerContextFactory
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.TokenProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native linking/controller creation only. This test never supplies credentials or starts DRM audio. */
@RunWith(AndroidJUnit4::class)
class MusicKitRuntimeTest {
    @Test
    fun officialSdkLoadsAndCreatesAnEmptyController() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(BuildConfig.APPLE_MUSIC_SDK_PRESENT)
        assertNotNull(AuthenticationFactory.createAuthenticationManager(context))
        System.loadLibrary("c++_shared")
        System.loadLibrary("appleMusicSDK")
        val emptyTokens = object : TokenProvider {
            override fun getDeveloperToken(): String? = null
            override fun getUserToken(): String? = null
            override fun getAppleMusicStoreFrontId(): String? = null
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MediaPlayerControllerFactory.createLocalController(
                context, Handler(Looper.getMainLooper()), emptyTokens,
            )
            try {
                player.playbackStoreConfiguration.notifyTokenChanged(context, emptyTokens)
                player.repeatMode = PlaybackRepeatMode.REPEAT_MODE_OFF
                player.shuffleMode = PlaybackShuffleMode.SHUFFLE_MODE_OFF
            } finally {
                player.release()
                MediaPlayerContextFactory.createPlayerContext(context).clearData()
            }
        }
    }
}
