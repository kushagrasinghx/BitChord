package com.music.bitchord.wear

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.music.bitchord.wear.data.SelfUpdate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.music.bitchord.wear.ui.Ambient
import com.music.bitchord.wear.ui.LocalAmbient
import com.music.bitchord.wear.data.PhoneLink
import com.music.bitchord.wear.ui.BitChordWearTheme
import com.music.bitchord.wear.ui.WearApp

class WearViewModel(app: Application) : AndroidViewModel(app) {
    /** Outlives a rotation-less recreate (theme, locale) so the last state stays drawn. */
    val link = PhoneLink(app, viewModelScope)

    override fun onCleared() = link.stop()
}

class WearActivity : ComponentActivity() {

    private val model: WearViewModel by viewModels()

    private val ambient = mutableStateOf(Ambient())

    /**
     * Always-on. The activity stays up when the screen dims instead of handing
     * back to the watch face, and draws the ambient player until it wakes.
     * Listening carries on through it, so a track change still shows.
     */
    private val ambientObserver by lazy {
        AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    ambient.value = Ambient(
                        active = true,
                        burnInProtection = ambientDetails.burnInProtectionRequired,
                        lowBit = ambientDetails.deviceHasLowBitAmbient,
                    )
                }

                override fun onUpdateAmbient() {
                    ambient.value = ambient.value.copy(tick = ambient.value.tick + 1)
                }

                override fun onExitAmbient() {
                    ambient.value = Ambient()
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        // For the watch-face icon while the phone plays. Asked once; a "no"
        // only costs that icon — the app itself works the same.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        // Listening follows the screen: on while the app is visible, off the
        // moment the watch face comes back. Nothing runs in the background.
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = model.link.start()
                override fun onStop(owner: LifecycleOwner) = model.link.stop()
            },
        )
        lifecycleScope.launch {
            // Whenever the app is on screen, an install waiting on the wearer's
            // yes is shown — opened by the phone after an update, or by hand.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                SelfUpdate.pendingConfirm.filterNotNull().collect { confirm ->
                    SelfUpdate.pendingConfirm.value = null
                    runCatching { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        setContent {
            BitChordWearTheme {
                CompositionLocalProvider(LocalAmbient provides ambient.value) {
                    WearApp(model.link)
                }
            }
        }
    }
}
