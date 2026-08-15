package com.music.bitchord.playback

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device has Dolby Atmos, and whether it is switched on right now.
 *
 * Spatial audio hangs off both: the app's own effect is only offered on a
 * device that ships Atmos, and only runs while the system's Atmos switch is on
 * — the same contract Apple Music keeps, where the row is dead on hardware that
 * cannot do it and follows the system switch on hardware that can.
 *
 * Nothing here can turn Atmos *on*. No public API exposes the OEM's switch to a
 * third-party app; [settingsIntent] is the closest thing, a jump to the panel
 * that owns it. What this can do is notice the moment it goes off — via the
 * platform spatializer's own callback, the vendor setting, or an output route
 * change — and take spatial audio down with it.
 */
object DolbyAtmos {

    /**
     * The device can do Dolby Atmos. Near-fixed, but not quite: an HDMI or cast
     * output that decodes Atmos can arrive after launch.
     */
    private val _supported = MutableStateFlow(false)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Atmos is switched on and applies to whatever audio is routed to now. */
    private val _enabledOnDevice = MutableStateFlow(false)
    val enabledOnDevice: StateFlow<Boolean> = _enabledOnDevice.asStateFlow()

    /** What the effect should actually do: the user's switch, gated on Atmos. */
    val spatialAudioActive: Boolean
        get() = _supported.value && _enabledOnDevice.value && AppSettings.spatialAudio.value

    private var audioManager: AudioManager? = null
    private var spatializer: Spatializer? = null

    /** The vendor setting Atmos lives in on this device, if it has one. */
    private var vendorToggle: VendorToggle? = null

    fun init(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AudioManager::class.java) ?: return
        audioManager = manager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            spatializer = runCatching { manager.spatializer }.getOrNull()
            watchSpatializer(app)
        }
        // Only worth probing on a device that has Atmos to switch — elsewhere
        // a key of the same name would be some other vendor's business.
        if (hasAtmosHardware()) {
            vendorToggle = findVendorToggle(app)
            watchVendorToggle(app)
        }
        // Atmos is a property of the route as much as the device: plugging in
        // headphones or handing audio to a Bluetooth sink can switch it on and
        // off without anyone touching a setting.
        runCatching {
            manager.registerAudioDeviceCallback(
                object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refresh()
                    override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refresh()
                },
                null,
            )
        }
        refresh()
    }

    /**
     * Re-read the device's state and, if Atmos has gone away, take the user's
     * spatial audio switch down with it — an effect that survived its
     * precondition would be lying about what it is.
     *
     * Cheap enough to call on every resume, which is how a trip to the system
     * panel and back gets noticed on devices whose Atmos switch isn't watchable.
     */
    fun refresh() {
        _supported.value = hasAtmosHardware()
        _enabledOnDevice.value = _supported.value && isAtmosOn()
        if (!_enabledOnDevice.value && AppSettings.spatialAudio.value) {
            AppSettings.setSpatialAudio(false)
        }
    }

    /**
     * Where the user turns Atmos on. Devices that ship it as an app get that
     * app; everything else gets the system sound panel, which is where both
     * stock Android's spatial audio switch and most OEM Atmos switches live.
     */
    fun settingsIntent(context: Context): Intent? {
        val packages = context.packageManager
        DOLBY_PACKAGES.forEach { name ->
            packages.getLaunchIntentForPackage(name)?.let { return it }
        }
        val sound = Intent(Settings.ACTION_SOUND_SETTINGS)
        return if (sound.resolveActivity(packages) != null) sound else null
    }

    /**
     * Two ways a device says it has Atmos: it publishes a Dolby audio effect
     * (how phones ship it), or an output claims a Dolby encoding (how a TV or
     * an HDMI/passthrough sink does).
     */
    private fun hasAtmosHardware(): Boolean = hasDolbyEffect() || hasAtmosOutput()

    /** The effect list is fixed at boot, so this is asked once and remembered. */
    private val dolbyEffect: Boolean by lazy {
        runCatching {
            AudioEffect.queryEffects()?.any { descriptor ->
                val identity = "${descriptor.implementor} ${descriptor.name}".lowercase()
                DOLBY_MARKERS.any { it in identity }
            } == true
        }.getOrDefault(false)
    }

    private fun hasDolbyEffect(): Boolean = dolbyEffect

    private fun hasAtmosOutput(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
        return devices.any { device ->
            device.encodings.any {
                it == AudioFormat.ENCODING_E_AC3_JOC || it == AudioFormat.ENCODING_AC4
            }
        }
    }

    /**
     * In order of how much the answer can be trusted: the OEM's own setting,
     * then the platform spatializer. A device that offers neither has Atmos
     * with no readable switch, so the user's choice stands rather than being
     * silently overruled by a state we can't see.
     */
    private fun isAtmosOn(): Boolean {
        vendorToggle?.let { return it.isOn() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            spatializer?.let { return it.isEnabled && it.isAvailable }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun watchSpatializer(context: Context) {
        val current = spatializer ?: return
        runCatching {
            current.addOnSpatializerStateChangedListener(
                context.mainExecutor,
                object : Spatializer.OnSpatializerStateChangedListener {
                    override fun onSpatializerEnabledChanged(spatializer: Spatializer, enabled: Boolean) = refresh()
                    override fun onSpatializerAvailableChanged(spatializer: Spatializer, available: Boolean) = refresh()
                },
            )
        }
    }

    /**
     * OEMs keep the Atmos switch in their own Settings row rather than anywhere
     * standard, so the key is found by probing rather than assumed. A miss
     * costs a handful of lookups at startup and leaves [isAtmosOn] on its other
     * sources.
     */
    private fun findVendorToggle(context: Context): VendorToggle? {
        val resolver = context.contentResolver
        VENDOR_KEYS.forEach { key ->
            val candidates = listOf(
                VendorToggle(Settings.Global.getUriFor(key)) {
                    Settings.Global.getInt(resolver, key, ABSENT)
                },
                VendorToggle(Settings.System.getUriFor(key)) {
                    Settings.System.getInt(resolver, key, ABSENT)
                },
                VendorToggle(Settings.Secure.getUriFor(key)) {
                    Settings.Secure.getInt(resolver, key, ABSENT)
                },
            )
            candidates.forEach { candidate ->
                if (candidate.value() != ABSENT) return candidate
            }
        }
        return null
    }

    private fun watchVendorToggle(context: Context) {
        val toggle = vendorToggle ?: return
        runCatching {
            context.contentResolver.registerContentObserver(
                toggle.uri,
                false,
                object : ContentObserver(Handler(context.mainLooper)) {
                    override fun onChange(selfChange: Boolean) = refresh()
                },
            )
        }
    }

    /** A vendor's Atmos switch: where it lives, and how to read it. */
    private class VendorToggle(val uri: Uri, private val read: () -> Int) {
        fun value(): Int = runCatching(read).getOrDefault(ABSENT)
        fun isOn(): Boolean = value().let { it != ABSENT && it != 0 }
    }

    /** Distinguishes "the key is missing" from any value it could legitimately hold. */
    private const val ABSENT = Int.MIN_VALUE

    private val DOLBY_MARKERS = listOf("dolby", "atmos", "dax")

    /** Devices that ship Atmos as a separate app rather than a Settings row. */
    private val DOLBY_PACKAGES = listOf(
        "com.dolby.dax2appui",
        "com.dolby.daxappui",
        "com.dolby.dolby234",
        "com.atc.daxappUI",
    )

    private val VENDOR_KEYS = listOf(
        "dolby_atmos",
        "dolby_atmos_enable",
        "dolby_dap_enable",
        "sound_effect_dolby",
        "dolby_effect",
    )
}
