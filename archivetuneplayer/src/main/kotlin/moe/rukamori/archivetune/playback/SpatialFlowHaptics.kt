@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SpatialFlowHapticEngine(context: Context) {
    private val context: Context = context.applicationContext

    private var attachedViewRef: WeakReference<View>? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var isHapticsEnabled: Boolean = false
        private set

    @Volatile
    private var vibrationStrengthMultiplier: Float = 0.8f

    @Volatile
    var bassBoostMultiplier: Float = 1.0f

    @Volatile
    var loudnessMultiplier: Float = 1.0f

    @Volatile
    var eqBassMultiplier: Float = 1.0f

    @Volatile
    var eqMidMultiplier: Float = 1.0f

    @Volatile
    var playbackSpeedFactor: Float = 1.0f

    private var prefs: SharedPreferences? = null
    private var cachedVibrator: Vibrator? = null

    private val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    private val isSOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private var hasAmplitudeControl = false

    @SuppressLint("ObsoleteSdkInt")
    private var hasViewHaptics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    private var deviceMode: HapticMode = HapticMode.VIBRATION_ONLY

    private enum class HapticMode {
        VIBRATION_ONLY,
        HAPTIC_ONLY,
        DUAL_MODE,
    }

    private val subBassHistory = FloatArray(4)
    private val bassHistory = FloatArray(5)
    private val midHistory = FloatArray(6)
    private var historyIndex = 0

    private var lastMidEnergy = 0f
    private var peakBassLevel = 0.5f
    private var peakMidLevel = 0.3f

    private var lastKickTime: Long = 0
    private var lastSnareTime: Long = 0

    private val beatIntervals = FloatArray(6) { 500f }
    private var beatIntervalIndex = 0
    private var estimatedBPM = 120f

    private var currentBassIntensity = 0f
    private var targetBassIntensity = 0f

    private var globalEnergyAvg = 0.15f

    private var currentMotorState = MotorState.IDLE

    private enum class MotorState {
        IDLE,
        CONTINUOUS,
        TRANSIENT,
        DECAY,
    }

    private var lastContinuousAmplitude = 0

    private var deviceProfile = DeviceHapticProfile()
    private val binderController = BinderLoadController()
    private val normalizer = AdaptiveAmplitudeNormalizer()
    private val oemMapper = OEMHapticMapper()
    private val modeSelector = ContinuousModeSelector()

    private var lastTransientTime: Long = 0
    private var lastTriggeredKickTime: Long = 0

    enum class HapticType {
        KICK,
        SNARE,
    }

    private var cachedKickEffect: VibrationEffect? = null
    private var cachedSnareEffect: VibrationEffect? = null
    private var cachedHiHatEffect: VibrationEffect? = null

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_HAPTICS_ENABLED) {
                val enabled = sharedPreferences.getBoolean(KEY_HAPTICS_ENABLED, false)
                setHapticsEnabled(enabled)
            } else if (key == KEY_VIBRATION_STRENGTH) {
                val strength = sharedPreferences.getFloat(KEY_VIBRATION_STRENGTH, 80f)
                vibrationStrengthMultiplier = strength / 100f
                updateCachedEffects()
                Log.d(TAG, "Strength updated: $vibrationStrengthMultiplier")
            }
        }

    init {
        initEngine()
        loadSettingsFromPrefs()
        registerPrefsListener()
    }

    private fun registerPrefsListener() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun unregisterPrefsListener() {
        prefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun initEngine() {
        hasAmplitudeControl = detectAmplitudeControl()
        detectDeviceCapabilities()

        val profiler = DeviceHapticProfiler()
        deviceProfile = profiler.buildProfile()
        DeviceCalibrationRunner().runCalibration()

        updateCachedEffects()

        Log.d(
            TAG,
            "Haptic Mode: $deviceMode, Amplitude Control: $hasAmplitudeControl, " +
                "Motor: ${if (deviceProfile.isLikelyERM) "ERM" else "LRA"}, " +
                "UpdateInterval: ${deviceProfile.binderSafeUpdateInterval}",
        )
    }

    private fun updateCachedEffects() {
        if (!isOreoOrLater) return
        val multiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (multiplier <= 0f) return

        try {
            val kickBaseAmp = 255
            val kickPeak = normalizer.normalize(kickBaseAmp, deviceProfile, multiplier)
            var kickTimings = longArrayOf(0, 40, 100, 50)
            kickTimings = normalizer.getAdaptedEnvelope(kickTimings, deviceProfile)
            val kickAmps = intArrayOf(0, (kickPeak * 0.9f).toInt(), kickPeak, 0)
            cachedKickEffect =
                if (deviceProfile.supportsWaveform) {
                    VibrationEffect.createWaveform(kickTimings, kickAmps, -1)
                } else {
                    VibrationEffect.createOneShot(kickTimings[1] + kickTimings[2], kickPeak)
                }

            val snareBaseAmp = 255
            val snareAmp = normalizer.normalize(snareBaseAmp, deviceProfile, multiplier)
            val snareDur = if (deviceProfile.isLikelyERM) 60L else 45L
            cachedSnareEffect = VibrationEffect.createOneShot(snareDur, snareAmp)

            val hihatBaseAmp = 255
            val hihatAmp = normalizer.normalize(hihatBaseAmp, deviceProfile, multiplier)
            val hihatDur = if (deviceProfile.isLikelyERM) 40L else 30L
            cachedHiHatEffect = VibrationEffect.createOneShot(hihatDur, hihatAmp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache effects: ${e.message}")
        }
    }

    fun loadSettingsFromPrefs() {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        isHapticsEnabled = prefs?.getBoolean(KEY_HAPTICS_ENABLED, false) ?: false
        vibrationStrengthMultiplier = (prefs?.getFloat(KEY_VIBRATION_STRENGTH, 80f) ?: 80f) / 100f
        updateCachedEffects()
        Log.d(TAG, "Settings loaded - Enabled: $isHapticsEnabled, Strength: $vibrationStrengthMultiplier")
    }

    fun setHapticEnabledPersistent(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HAPTICS_ENABLED, enabled)?.apply()
        setHapticsEnabled(enabled)
    }

    private fun detectDeviceCapabilities() {
        val vib = getVibrator()
        val hasVibrator = vib?.hasVibrator() == true

        deviceMode =
            when {
                hasVibrator && hasViewHaptics -> HapticMode.DUAL_MODE
                hasVibrator -> HapticMode.VIBRATION_ONLY
                hasViewHaptics -> HapticMode.HAPTIC_ONLY
                else -> HapticMode.VIBRATION_ONLY
            }
    }

    fun attachView(view: View?) {
        this.attachedViewRef = view?.let { WeakReference(it) }
        if (view != null && deviceMode != HapticMode.VIBRATION_ONLY) {
            view.isHapticFeedbackEnabled = true
            view.isClickable = true
        }
        Log.d(TAG, "View attached for haptics")
    }

    fun detachView() {
        attachedViewRef?.clear()
        attachedViewRef = null
    }

    fun setHapticsEnabled(enabled: Boolean) {
        val wasEnabled = this.isHapticsEnabled
        this.isHapticsEnabled = enabled

        if (!enabled && wasEnabled) {
            stopAllHaptics()
        }

        Log.d(TAG, "Haptics ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    private fun getVibrator(): Vibrator? {
        if (cachedVibrator != null) return cachedVibrator

        cachedVibrator =
            if (isSOrLater) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        return cachedVibrator
    }

    private fun detectAmplitudeControl(): Boolean {
        val vibrator = getVibrator() ?: return false
        return if (isOreoOrLater) vibrator.hasAmplitudeControl() else false
    }

    fun release() {
        stopAllHaptics()
        unregisterPrefsListener()
        scope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun processPcmHaptics(
        subBass: Float,
        bass: Float,
        mid: Float,
        high: Float,
    ) {
        if (!isHapticsEnabled) return

        scope.launch {
            val frameEnergy = (subBass + bass + mid + high) / 4f
            if (frameEnergy > 0.01f) {
                globalEnergyAvg = globalEnergyAvg * (1 - GAIN_ADJUST_SPEED) + frameEnergy * GAIN_ADJUST_SPEED
            }

            val safeAvg = max(0.08f, globalEnergyAvg)
            var dynamicGain = 0.40f / safeAvg
            dynamicGain = max(1.5f, min(6.0f, dynamicGain))

            val curSubBass = subBass * bassBoostMultiplier * eqBassMultiplier * loudnessMultiplier * dynamicGain
            val curBass = bass * (bassBoostMultiplier * 0.8f) * dynamicGain
            val curMid = mid * eqMidMultiplier * loudnessMultiplier * dynamicGain
            val vocalEnergy = (curBass * 0.4f + curMid * 0.6f)

            updateHistory(subBassHistory, curSubBass)
            updateHistory(bassHistory, curBass)
            updateHistory(midHistory, curMid)
            historyIndex++

            val avgSubBass = fastAverage(subBassHistory)
            val avgMid = fastAverage(midHistory)

            peakBassLevel = max(curSubBass, peakBassLevel * 0.985f)
            peakMidLevel = max(curMid, peakMidLevel * 0.985f)

            val now = System.currentTimeMillis()
            val adjustedKickInterval = (MIN_KICK_INTERVAL / playbackSpeedFactor).toLong()
            val adjustedSnareInterval = (MIN_SNARE_INTERVAL / playbackSpeedFactor).toLong()

            val dynamicPeak = max(peakBassLevel, 0.1f)
            val combinedHapticSignal =
                if (curSubBass > dynamicPeak * 0.4f) {
                    curSubBass + (high * 0.15f)
                } else {
                    curSubBass + (vocalEnergy * 0.45f) + (high * 0.10f)
                }

            var normalizedSignal = min(1f, combinedHapticSignal / dynamicPeak)

            if (normalizedSignal < 0.55f) {
                normalizedSignal = 0f
                if (currentMotorState == MotorState.CONTINUOUS) {
                    targetBassIntensity = 0f
                }
            }

            targetBassIntensity = normalizedSignal.pow(3.0f).coerceIn(0f, 1f)

            if (targetBassIntensity > currentBassIntensity) {
                currentBassIntensity = currentBassIntensity * (1 - ENVELOPE_ATTACK) + targetBassIntensity * ENVELOPE_ATTACK
            } else if (targetBassIntensity == 0f && currentBassIntensity > 0f) {
                currentBassIntensity *= 0.92f
                if (currentBassIntensity < 0.01f) currentBassIntensity = 0f
            } else {
                currentBassIntensity = currentBassIntensity * (1 - ENVELOPE_DECAY) + targetBassIntensity * ENVELOPE_DECAY
            }

            if (binderController.canUpdate(deviceProfile, now)) {
                updateContinuousBassHaptic(currentBassIntensity)
            }

            val subBassRise = curSubBass - avgSubBass
            val isKick =
                curSubBass > avgSubBass * 1.15f &&
                    subBassRise > 0.015f &&
                    (now - lastKickTime) > adjustedKickInterval

            if (isKick) {
                updateBPM(now)
                lastKickTime = now
                val kickIntensity = computeIntensity(curSubBass, avgSubBass, peakBassLevel)
                triggerTransientHaptic(max(0.9f, kickIntensity * 1.5f), HapticType.KICK)
            }

            val midRise = curMid - lastMidEnergy
            val isSnare =
                curMid > avgMid * 1.3f &&
                    midRise > (avgMid * 0.2f) &&
                    midRise > 0.03f &&
                    curSubBass < avgSubBass * 1.6f &&
                    (now - lastSnareTime) > adjustedSnareInterval

            if (isSnare) {
                lastSnareTime = now
                val snareIntensity = computeIntensity(curMid, avgMid, peakMidLevel)
                triggerTransientHaptic(min(1.0f, snareIntensity * 2.0f), HapticType.SNARE)
            }

            lastMidEnergy = curMid
        }
    }

    private fun updateHistory(
        history: FloatArray,
        value: Float,
    ) {
        history[historyIndex % history.size] = value
    }

    private fun fastAverage(array: FloatArray): Float = array.sum() / array.size

    private fun computeIntensity(
        energy: Float,
        avg: Float,
        peak: Float,
    ): Float {
        if (peak <= avg) return 0f
        val rawIntensity = (energy - avg) / (peak - avg + 0.01f)
        return min(1f, rawIntensity.toDouble().pow(0.75).toFloat() * 1.15f)
    }

    private fun updateBPM(now: Long) {
        if (lastKickTime > 0) {
            val interval = now - lastKickTime
            if (interval in 251..1499) {
                beatIntervals[beatIntervalIndex] = interval.toFloat()
                beatIntervalIndex = (beatIntervalIndex + 1) % beatIntervals.size
                estimatedBPM = 60000f / fastAverage(beatIntervals)
            }
        }
    }

    private fun updateContinuousBassHaptic(intensity: Float) {
        if (!isHapticsEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastTransientTime < TRANSIENT_LOCKOUT_MS) return

        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val userMultiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (userMultiplier <= 0f) return

        val clampedIntensity = (intensity * 3.0f).coerceIn(0f, 1f)

        if (isOreoOrLater && hasAmplitudeControl) {
            val targetAmplitudeBase = (clampedIntensity * CONTINUOUS_MAX_AMP).toInt()
            val targetAmplitude = normalizer.normalize(targetAmplitudeBase, deviceProfile, userMultiplier)

            if (targetAmplitude < deviceProfile.minEffectiveAmplitude) {
                if (currentMotorState == MotorState.CONTINUOUS) {
                    currentMotorState = MotorState.DECAY
                }
                currentMotorState = MotorState.IDLE
                lastContinuousAmplitude = 0
                return
            }

            if (currentMotorState == MotorState.CONTINUOUS &&
                abs(targetAmplitude - lastContinuousAmplitude) < DELTA_AMP_THRESHOLD
            ) {
                return
            }

            modeSelector.executeContinuous(vibrator, targetAmplitude, deviceProfile)
            currentMotorState = MotorState.CONTINUOUS
            lastContinuousAmplitude = targetAmplitude
        } else {
            if (clampedIntensity > 0.2f && currentMotorState != MotorState.CONTINUOUS) {
                try {
                    if (isOreoOrLater) {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(80)
                    }
                    currentMotorState = MotorState.CONTINUOUS
                } catch (_: Exception) {
                }
            } else if (clampedIntensity == 0f) {
                currentMotorState = MotorState.IDLE
            }
        }
    }

    private fun stopContinuousHaptic() {
        if (currentMotorState == MotorState.IDLE) return
        val vibrator = getVibrator() ?: return
        try {
            vibrator.cancel()
            currentMotorState = MotorState.IDLE
            lastContinuousAmplitude = 0
        } catch (e: Exception) {
            Log.w(TAG, "Stop continuous error: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun triggerTransientHaptic(
        intensity: Float,
        type: HapticType,
    ) {
        val now = System.currentTimeMillis()
        if (type == HapticType.KICK && now - lastTriggeredKickTime < 110) return
        if (type == HapticType.KICK) lastTriggeredKickTime = now

        lastTransientTime = now
        currentMotorState = MotorState.TRANSIENT

        if (!isHapticsEnabled) return

        val userMultiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (userMultiplier <= 0f) return

        when (deviceMode) {
            HapticMode.DUAL_MODE -> {
                performVibrationTransient(type, intensity, userMultiplier)
                attachedViewRef?.get()?.let { v ->
                    if (v.isAttachedToWindow) {
                        v.post { performAndroidHaptic(v, type) }
                    }
                }
            }

            HapticMode.HAPTIC_ONLY -> {
                attachedViewRef?.get()?.let { v ->
                    if (v.isAttachedToWindow) {
                        v.post { performAndroidHaptic(v, type) }
                    }
                }
            }

            HapticMode.VIBRATION_ONLY -> performVibrationTransient(type, intensity, userMultiplier)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun performAndroidHaptic(
        view: View,
        type: HapticType,
    ) {
        if (!view.isHapticFeedbackEnabled) view.isHapticFeedbackEnabled = true
        try {
            val constant =
                when (type) {
                    HapticType.KICK -> oemMapper.getKickConstant()
                    HapticType.SNARE -> oemMapper.getSnareConstant()
                }
            view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        } catch (_: Exception) {
        }
    }

    private fun performVibrationTransient(
        type: HapticType,
        intensity: Float,
        multiplier: Float,
    ) {
        binderController.markTransient(System.currentTimeMillis())
        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        if (isOreoOrLater && hasAmplitudeControl) {
            try {
                val effect =
                    when (type) {
                        HapticType.KICK ->
                            cachedKickEffect
                                ?: run {
                                    val targetBaseAmp = min(255f, 180f + (75f * intensity)).toInt()
                                    val peakAmp = normalizer.normalize(targetBaseAmp, deviceProfile, multiplier)
                                    var timings = longArrayOf(0, 25, 60, 30)
                                    timings = normalizer.getAdaptedEnvelope(timings, deviceProfile)
                                    val amps = intArrayOf(0, (peakAmp * 0.6f).toInt(), peakAmp, 0)
                                    if (deviceProfile.supportsWaveform) {
                                        VibrationEffect.createWaveform(timings, amps, -1)
                                    } else {
                                        VibrationEffect.createOneShot(timings[1] + timings[2], peakAmp)
                                    }
                                }

                        HapticType.SNARE ->
                            cachedSnareEffect
                                ?: run {
                                    val targetBaseAmp = min(200f, 140f + (60f * intensity)).toInt()
                                    val targetAmp = normalizer.normalize(targetBaseAmp, deviceProfile, multiplier)
                                    val duration = if (deviceProfile.isLikelyERM) 40L else 25L
                                    VibrationEffect.createOneShot(duration, targetAmp)
                                }
                    }
                vibrator.vibrate(effect)
            } catch (e: Exception) {
                Log.w(TAG, "Vibration transient error: ${e.message}", e)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (type == HapticType.KICK) 45L else 20L)
        }
    }

    private fun stopAllHaptics() {
        stopContinuousHaptic()
        currentBassIntensity = 0f
        targetBassIntensity = 0f
    }

    class DeviceHapticProfile {
        var supportsWaveform = true
        var amplitudeLinear = true
        var minEffectiveAmplitude = 35
        var maxStableAmplitude = 255
        var prefersOneShotContinuous = false
        var binderSafeUpdateInterval = 90L
        var isLikelyERM = false
    }

    private class DeviceHapticProfiler {
        fun buildProfile(): DeviceHapticProfile {
            val p = DeviceHapticProfile()
            val mfg = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()

            when {
                mfg.contains("motorola") || mfg.contains("infinix") || mfg.contains("tecno") || mfg.contains("nokia") -> {
                    p.minEffectiveAmplitude = 60
                    p.isLikelyERM = true
                    p.amplitudeLinear = false
                    p.prefersOneShotContinuous = true
                    p.binderSafeUpdateInterval = 120L
                }

                mfg.contains("samsung") -> {
                    p.minEffectiveAmplitude = 40
                    p.binderSafeUpdateInterval = 100L
                    if (model.contains("sm-a") || model.contains("sm-m")) {
                        p.isLikelyERM = true
                        p.prefersOneShotContinuous = true
                    } else {
                        p.isLikelyERM = false
                    }
                }

                mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") -> {
                    p.minEffectiveAmplitude = 70
                    p.binderSafeUpdateInterval = 110L
                    p.prefersOneShotContinuous = false
                }

                mfg.contains("oneplus") || mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("vivo") -> {
                    p.minEffectiveAmplitude = 25
                    p.binderSafeUpdateInterval = 80L
                    p.isLikelyERM = false
                }

                mfg.contains("google") || mfg.contains("pixel") -> {
                    p.minEffectiveAmplitude = 30
                    p.binderSafeUpdateInterval = 85L
                    p.isLikelyERM = false
                }

                mfg.contains("sony") || mfg.contains("asus") -> {
                    p.minEffectiveAmplitude = 35
                    p.binderSafeUpdateInterval = 90L
                }
            }
            return p
        }
    }

    private class DeviceCalibrationRunner {
        fun runCalibration() {
        }
    }

    private class BinderLoadController {
        private var lastUpdate: Long = 0

        fun canUpdate(
            p: DeviceHapticProfile,
            now: Long,
        ): Boolean {
            if (now - lastUpdate > p.binderSafeUpdateInterval) {
                lastUpdate = now
                return true
            }
            return false
        }

        fun markTransient(now: Long) {
            lastUpdate = now
        }
    }

    private class AdaptiveAmplitudeNormalizer {
        fun normalize(
            baseAmp: Int,
            p: DeviceHapticProfile,
            mult: Float,
        ): Int {
            var amp = (baseAmp * mult).toInt()
            amp = max(p.minEffectiveAmplitude, min(p.maxStableAmplitude, amp))
            if (!p.amplitudeLinear) {
                amp = (amp * 1.15f).toInt().coerceAtMost(255)
            }
            return amp
        }

        fun getAdaptedEnvelope(
            timings: LongArray,
            p: DeviceHapticProfile,
        ): LongArray {
            if (p.isLikelyERM) {
                return longArrayOf(timings[0], timings[1] * 2, timings[2] * 2, timings[3] * 2)
            }
            return timings
        }
    }

    private class OEMHapticMapper {
        fun getKickConstant(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }

        @SuppressLint("ObsoleteSdkInt")
        fun getSnareConstant(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                HapticFeedbackConstants.CONTEXT_CLICK
            } else {
                HapticFeedbackConstants.KEYBOARD_PRESS
            }
    }

    private class ContinuousModeSelector {
        fun executeContinuous(
            vib: Vibrator,
            amp: Int,
            p: DeviceHapticProfile,
        ) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (p.prefersOneShotContinuous) {
                        vib.vibrate(VibrationEffect.createOneShot(p.binderSafeUpdateInterval + 50, amp))
                    } else {
                        vib.vibrate(VibrationEffect.createOneShot(100, amp))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Continuous execution failed: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "SpatialFlowHaptics"
        private const val PREFS_NAME = "AppSettings"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_VIBRATION_STRENGTH = "vibration_strength"

        private const val MIN_KICK_INTERVAL = 200L
        private const val MIN_SNARE_INTERVAL = 150L
        private const val GAIN_ADJUST_SPEED = 0.005f
        private const val ENVELOPE_ATTACK = 0.35f
        private const val ENVELOPE_DECAY = 0.15f
        private const val CONTINUOUS_MAX_AMP = 130
        private const val DELTA_AMP_THRESHOLD = 15
        private const val TRANSIENT_LOCKOUT_MS = 120L
    }
}

object MusicHapticsSettings {
    private const val PREFS_NAME = "AppSettings"
    private const val KEY_ENABLED = "haptics_enabled"
    private const val KEY_STRENGTH = "vibration_strength"

    fun isEnabled(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun strengthPercent(context: Context): Int =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_STRENGTH, 80f)
            .toInt()
            .coerceIn(0, 100)

    fun setStrengthPercent(
        context: Context,
        percent: Int,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_STRENGTH, percent.coerceIn(0, 100).toFloat())
            .apply()
    }
}
