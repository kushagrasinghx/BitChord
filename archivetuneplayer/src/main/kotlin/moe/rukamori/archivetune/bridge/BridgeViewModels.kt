/*
 * BRIDGE — the view models the copied player constructs through
 * hiltViewModel(), built by hand instead of Hilt. The constructors are the
 * copied ones; every argument is a bridge instance or a copied class
 * constructed over bridge state.
 */

package moe.rukamori.archivetune.bridge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.equalizer.ApplyEqualizerPresetUseCase
import moe.rukamori.archivetune.equalizer.EqualizerRepository
import moe.rukamori.archivetune.equalizer.ManageEqualizerProfilesUseCase
import moe.rukamori.archivetune.equalizer.ObserveEqualizerUseCase
import moe.rukamori.archivetune.equalizer.UpdateEqualizerUseCase
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.playlist.CreatePlaylistUseCase
import moe.rukamori.archivetune.playlist.GetCreatePlaylistOptionsUseCase
import moe.rukamori.archivetune.repository.PlaylistCreationRepository
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.ui.utils.TrackInfoViewModel
import moe.rukamori.archivetune.viewmodels.CreatePlaylistViewModel
import moe.rukamori.archivetune.viewmodels.EqualizerViewModel
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import tf.monochrome.android.audio.dsp.ChannelDetectorProcessor
import tf.monochrome.android.audio.pipeline.AudioPipelineMonitor
import tf.monochrome.android.audio.pipeline.OutputDeviceProbe
import com.lastwave.app.playback.UsbDacMonitor
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object BridgeViewModels {
    private val creators = ConcurrentHashMap<String, () -> ViewModel>()

    fun <VM : ViewModel> register(
        clazz: Class<VM>,
        creator: () -> VM,
    ) {
        creators[clazz.name] = creator
    }

    fun asFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(
                modelClass: Class<VM>,
            ): VM {
                val creator = creators[modelClass.name]
                if (creator == null) {
                    val message =
                        "No bridge view-model registered for ${modelClass.name} — " +
                            "see BridgeViewModels.init()"
                    // Logged before the throw so the crash report's buffered
                    // log names the class even if the trace is truncated.
                    Timber.tag("ArchiveTuneBridge").e(
                        "%s (registered: %s)",
                        message,
                        creators.keys.joinToString(),
                    )
                    error(message)
                }
                @Suppress("UNCHECKED_CAST")
                return creator() as VM
            }
        }

    /** Seed the registry with the player's view models. Idempotent. */
    fun init(context: Context) {
        val app = context.applicationContext

        if (!creators.containsKey(LyricsMenuViewModel::class.java.name)) {
            val database = MusicDatabase()
            register(LyricsMenuViewModel::class.java) {
                LyricsMenuViewModel(
                    context = app,
                    lyricsHelper = LyricsHelper(),
                    database = database,
                    networkConnectivity = NetworkConnectivityObserver(app),
                )
            }
        }

        if (!creators.containsKey(CreatePlaylistViewModel::class.java.name)) {
            val repository = PlaylistCreationRepository(app, MusicDatabase())
            register(CreatePlaylistViewModel::class.java) {
                CreatePlaylistViewModel(
                    getOptions = GetCreatePlaylistOptionsUseCase(repository),
                    createPlaylist = CreatePlaylistUseCase(repository),
                )
            }
        }

        if (!creators.containsKey(EqualizerViewModel::class.java.name)) {
            val repository =
                EqualizerRepository(app, moe.rukamori.archivetune.playback.EqualizerPlaybackController())
            register(EqualizerViewModel::class.java) {
                EqualizerViewModel(
                    observeEqualizer = ObserveEqualizerUseCase(repository),
                    updateEqualizer = UpdateEqualizerUseCase(repository),
                    applyPreset = ApplyEqualizerPresetUseCase(repository),
                    manageProfiles = ManageEqualizerProfilesUseCase(repository),
                )
            }
        }

        if (!creators.containsKey(TrackInfoViewModel::class.java.name)) {
            register(TrackInfoViewModel::class.java) {
                TrackInfoViewModel(
                    monitor = AudioPipelineMonitor(),
                    channelDetector = ChannelDetectorProcessor(),
                    outputProbe = OutputDeviceProbe(),
                    usbDacMonitor = UsbDacMonitor(),
                )
            }
        }
    }
}
