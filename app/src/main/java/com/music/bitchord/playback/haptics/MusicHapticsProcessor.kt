package com.music.bitchord.playback.haptics

import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-allocation, strictly read-only 5-band Float32 DSP processor (v3) running
 * at 125 Hz (8 ms hop) with sub-block acoustic timestamping and 3-point local
 * peak picking.
 *
 * Classifies live music into 9 distinct tactile articulations ([HapticEventKind])
 * and scales each layer by the user's independent Bass, Vocals, and Percussion
 * mix sliders.
 */
class MusicHapticsProcessor(
    private val engine: MusicHapticsEngine,
    private val leadUsProvider: () -> Long,
    private val isAudible: () -> Boolean,
) : FloatAudioProcessor {

    @Volatile
    var config: MusicHapticsConfig = MusicHapticsConfig()

    private var sampleRate: Int = 48_000
    private var channelCount: Int = 2
    private var hopFrames: Int = 384 // 8 ms at 48 kHz (125 Hz precision)

    // 5-band 2nd-order RBJ biquad filter bank (scalar state -> zero allocations)
    private val subFilter = BiquadFilter()   // 20–85 Hz: 808 drops & sub-bass
    private val kickFilter = BiquadFilter()  // 85–220 Hz: tight kick punch & bassline
    private val snareFilter = BiquadFilter() // 220–650 Hz: snare body & toms
    private val vocalFilter = BiquadFilter() // 650–3200 Hz: lead vocals, lyrics & claps
    private val airFilter = BiquadFilter()   // 4500–11000 Hz: hi-hats, shakers & crashes

    // Hop accumulators
    private var hopCounter: Int = 0
    private var sumSqTotal: Double = 0.0
    private var sumSqSub: Double = 0.0
    private var sumSqKick: Double = 0.0
    private var sumSqSnare: Double = 0.0
    private var sumSqVocal: Double = 0.0
    private var sumSqAir: Double = 0.0

    // Previous hop RMS for spectral flux
    private var prevSubRms: Float = 0f
    private var prevKickRms: Float = 0f
    private var prevSnareRms: Float = 0f
    private var prevVocalRms: Float = 0f
    private var prevAirRms: Float = 0f

    // 3-point local peak picking window (prev -> curr -> next)
    private var subFluxPrev: Float = 0f
    private var subFluxCurr: Float = 0f
    private var subRmsCurr: Float = 0f

    private var kickFluxPrev: Float = 0f
    private var kickFluxCurr: Float = 0f
    private var kickRmsCurr: Float = 0f

    private var snareFluxPrev: Float = 0f
    private var snareFluxCurr: Float = 0f
    private var snareRmsCurr: Float = 0f

    private var vocalFluxPrev: Float = 0f
    private var vocalFluxCurr: Float = 0f
    private var vocalRmsCurr: Float = 0f

    private var airFluxPrev: Float = 0f
    private var airFluxCurr: Float = 0f
    private var airRmsCurr: Float = 0f

    private var totalRmsCurr: Float = 0f

    // Adaptive rolling history (~1.0 s at 125 Hz = 124 frames)
    private val subHistory = FloatArray(HISTORY_SIZE)
    private val vocalHistory = FloatArray(HISTORY_SIZE)
    private val subFluxHistory = FloatArray(HISTORY_SIZE)
    private val kickFluxHistory = FloatArray(HISTORY_SIZE)
    private val snareFluxHistory = FloatArray(HISTORY_SIZE)
    private val vocalFluxHistory = FloatArray(HISTORY_SIZE)
    private val airFluxHistory = FloatArray(HISTORY_SIZE)
    private var historyIndex: Int = 0
    private var historyFilled: Int = 0

    // Slow automatic gain reference so quiet verses & loud drops stay balanced
    private var agcPeakRms: Float = 0.15f

    // Beat timing & live tempo tracking
    private var streamTimeMs: Double = 0.0
    private var lastKickTimeMs: Double = -1000.0
    private var lastSnareTimeMs: Double = -1000.0
    private var lastClapTimeMs: Double = -1000.0
    private var lastVocalTimeMs: Double = -1000.0
    private var lastHihatTimeMs: Double = -1000.0
    private var lastCrashTimeMs: Double = -1000.0
    private var lastSwellTimeMs: Double = -1000.0
    private var lastAnyBeatTimeMs: Double = -1000.0

    private val beatIntervalsMs = FloatArray(BPM_WINDOW)
    private var bpmIndex: Int = 0
    private var bpmCount: Int = 0
    private var estimatedBpm: Int = 0

    // Smoothed 5-band UI visualizer meters
    private var visSub: Float = 0f
    private var visKick: Float = 0f
    private var visSnare: Float = 0f
    private var visVocal: Float = 0f
    private var visAir: Float = 0f

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000)
        this.channelCount = channelCount.coerceAtLeast(1)
        this.hopFrames = ((this.sampleRate * HOP_DURATION_SEC).roundToInt()).coerceIn(96, 1536)

        subFilter.configureLowPass(this.sampleRate, cutoffHz = 85.0, q = 0.7071)
        kickFilter.configureBandPass(this.sampleRate, centerHz = 135.0, q = 0.95)
        snareFilter.configureBandPass(this.sampleRate, centerHz = 360.0, q = 0.85)
        vocalFilter.configureBandPass(this.sampleRate, centerHz = 1350.0, q = 0.60)
        airFilter.configureHighPass(this.sampleRate, cutoffHz = 4500.0, q = 0.7071)
        clearAnalysisState()
    }

    override fun process(block: AudioBlock) {
        val cfg = config
        if (!cfg.enabled || block.frameCount <= 0) return
        if (!isAudible()) return

        val samples = block.samples
        val frames = block.frameCount
        val ch = block.channelCount.coerceAtLeast(1)
        val invCh = 1f / ch.toFloat()

        val baseLeadMs = (leadUsProvider() / 1000L).coerceIn(0L, 650L)
        val msPerFrame = 1000.0 / sampleRate.toDouble()

        var idx = 0
        for (f in 0 until frames) {
            val mono = if (ch == 2) {
                (samples[idx] + samples[idx + 1]) * 0.5f
            } else {
                var acc = 0f
                for (c in 0 until ch) acc += samples[idx + c]
                acc * invCh
            }
            idx += ch

            val sub = subFilter.process(mono)
            val kick = kickFilter.process(mono)
            val snare = snareFilter.process(mono)
            val vocal = vocalFilter.process(mono)
            val air = airFilter.process(mono)

            sumSqTotal += (mono * mono).toDouble()
            sumSqSub += (sub * sub).toDouble()
            sumSqKick += (kick * kick).toDouble()
            sumSqSnare += (snare * snare).toDouble()
            sumSqVocal += (vocal * vocal).toDouble()
            sumSqAir += (air * air).toDouble()

            hopCounter++
            if (hopCounter >= hopFrames) {
                val frameOffsetMs = ((f + 1 - hopFrames).coerceAtLeast(0) * msPerFrame).toLong()
                analyzeHop(cfg, baseLeadMs + frameOffsetMs)
                hopCounter = 0
                sumSqTotal = 0.0
                sumSqSub = 0.0
                sumSqKick = 0.0
                sumSqSnare = 0.0
                sumSqVocal = 0.0
                sumSqAir = 0.0
            }
        }
    }

    override fun flush() {
        clearAnalysisState()
        if (isAudible()) engine.cancelPending()
    }

    override fun reset() {
        clearAnalysisState()
        if (isAudible()) engine.cancelPending()
    }

    private fun clearAnalysisState() {
        subFilter.reset()
        kickFilter.reset()
        snareFilter.reset()
        vocalFilter.reset()
        airFilter.reset()
        hopCounter = 0
        sumSqTotal = 0.0
        sumSqSub = 0.0
        sumSqKick = 0.0
        sumSqSnare = 0.0
        sumSqVocal = 0.0
        sumSqAir = 0.0
        prevSubRms = 0f
        prevKickRms = 0f
        prevSnareRms = 0f
        prevVocalRms = 0f
        prevAirRms = 0f
        subFluxPrev = 0f; subFluxCurr = 0f; subRmsCurr = 0f
        kickFluxPrev = 0f; kickFluxCurr = 0f; kickRmsCurr = 0f
        snareFluxPrev = 0f; snareFluxCurr = 0f; snareRmsCurr = 0f
        vocalFluxPrev = 0f; vocalFluxCurr = 0f; vocalRmsCurr = 0f
        airFluxPrev = 0f; airFluxCurr = 0f; airRmsCurr = 0f
        totalRmsCurr = 0f
        subHistory.fill(0f)
        vocalHistory.fill(0f)
        subFluxHistory.fill(0f)
        kickFluxHistory.fill(0f)
        snareFluxHistory.fill(0f)
        vocalFluxHistory.fill(0f)
        airFluxHistory.fill(0f)
        historyIndex = 0
        historyFilled = 0
        agcPeakRms = 0.15f
        streamTimeMs = 0.0
        lastKickTimeMs = -1000.0
        lastSnareTimeMs = -1000.0
        lastClapTimeMs = -1000.0
        lastVocalTimeMs = -1000.0
        lastHihatTimeMs = -1000.0
        lastCrashTimeMs = -1000.0
        lastSwellTimeMs = -1000.0
        lastAnyBeatTimeMs = -1000.0
        beatIntervalsMs.fill(0f)
        bpmIndex = 0
        bpmCount = 0
        estimatedBpm = 0
        visSub = 0f; visKick = 0f; visSnare = 0f; visVocal = 0f; visAir = 0f
    }

    private fun analyzeHop(cfg: MusicHapticsConfig, acousticLeadMs: Long) {
        val invN = 1.0 / hopFrames.toDouble()
        val hopMs = hopFrames * 1000.0 / sampleRate.toDouble()
        streamTimeMs += hopMs

        val totalNext = sqrt(sumSqTotal * invN).toFloat()
        val subNext = sqrt(sumSqSub * invN).toFloat()
        val kickNext = sqrt(sumSqKick * invN).toFloat()
        val snareNext = sqrt(sumSqSnare * invN).toFloat()
        val vocalNext = sqrt(sumSqVocal * invN).toFloat()
        val airNext = sqrt(sumSqAir * invN).toFloat()

        val subFluxNext = max(0f, subNext - prevSubRms)
        val kickFluxNext = max(0f, kickNext - prevKickRms)
        val snareFluxNext = max(0f, snareNext - prevSnareRms)
        val vocalFluxNext = max(0f, vocalNext - prevVocalRms)
        val airFluxNext = max(0f, airNext - prevAirRms)

        prevSubRms = subNext
        prevKickRms = kickNext
        prevSnareRms = snareNext
        prevVocalRms = vocalNext
        prevAirRms = airNext

        agcPeakRms = if (totalNext > agcPeakRms) {
            agcPeakRms * 0.86f + totalNext * 0.14f
        } else {
            max(0.055f, agcPeakRms * 0.997f)
        }
        val agcGain = (0.28f / agcPeakRms).coerceIn(0.65f, 2.8f)

        val subMean = mean(subHistory, historyFilled)
        val vocalMean = mean(vocalHistory, historyFilled)
        val subFluxMean = mean(subFluxHistory, historyFilled)
        val subFluxStd = stdDev(subFluxHistory, historyFilled, subFluxMean)
        val kickFluxMean = mean(kickFluxHistory, historyFilled)
        val kickFluxStd = stdDev(kickFluxHistory, historyFilled, kickFluxMean)
        val snareFluxMean = mean(snareFluxHistory, historyFilled)
        val snareFluxStd = stdDev(snareFluxHistory, historyFilled, snareFluxMean)
        val vocalFluxMean = mean(vocalFluxHistory, historyFilled)
        val vocalFluxStd = stdDev(vocalFluxHistory, historyFilled, vocalFluxMean)
        val airFluxMean = mean(airFluxHistory, historyFilled)
        val airFluxStd = stdDev(airFluxHistory, historyFilled, airFluxMean)

        val isSubPeak = subFluxCurr > subFluxPrev && subFluxCurr >= subFluxNext
        val isKickPeak = kickFluxCurr > kickFluxPrev && kickFluxCurr >= kickFluxNext
        val isSnarePeak = snareFluxCurr > snareFluxPrev && snareFluxCurr >= snareFluxNext
        val isVocalPeak = vocalFluxCurr > vocalFluxPrev && vocalFluxCurr >= vocalFluxNext
        val isAirPeak = airFluxCurr > airFluxPrev && airFluxCurr >= airFluxNext

        visSub = max(visSub * 0.85f, (subRmsCurr * agcGain * 2.4f).coerceIn(0f, 1f))
        visKick = max(visKick * 0.83f, (kickRmsCurr * agcGain * 2.8f).coerceIn(0f, 1f))
        visSnare = max(visSnare * 0.82f, (snareRmsCurr * agcGain * 3.1f).coerceIn(0f, 1f))
        visVocal = max(visVocal * 0.86f, (vocalRmsCurr * agcGain * 2.8f).coerceIn(0f, 1f))
        visAir = max(visAir * 0.78f, (airRmsCurr * agcGain * 4.5f).coerceIn(0f, 1f))

        var firedKind: HapticEventKind? = null

        if (totalRmsCurr >= SILENCE_RMS_GATE && historyFilled >= MIN_WARMUP_HOPS) {
            val sens = cfg.sensitivity.coerceIn(0f, 1f)
            val sigmaK = 2.00f - sens * 1.18f

            val subThresh = max(MIN_SUB_FLUX, subFluxMean + subFluxStd * sigmaK)
            val kickThresh = max(MIN_KICK_FLUX, kickFluxMean + kickFluxStd * sigmaK)
            val snareThresh = max(MIN_SNARE_FLUX, snareFluxMean + snareFluxStd * (sigmaK * 1.05f))
            val vocalThresh = max(MIN_VOCAL_FLUX, vocalFluxMean + vocalFluxStd * (sigmaK * 1.12f))
            val airThresh = max(MIN_AIR_FLUX, airFluxMean + airFluxStd * (sigmaK * 1.20f))

            val isSubOnset = isSubPeak && subFluxCurr > subThresh && subRmsCurr > max(0.018f, subMean * 0.82f)
            val isKickOnset = isKickPeak && kickFluxCurr > kickThresh && kickRmsCurr > 0.018f
            val isSnareOnset = isSnarePeak && snareFluxCurr > snareThresh && snareRmsCurr > 0.016f
            val isVocalOnset = isVocalPeak && vocalFluxCurr > vocalThresh && vocalRmsCurr > 0.020f
            val isAirOnset = isAirPeak && airFluxCurr > airThresh && airRmsCurr > 0.010f

            val beatCooldownMs = if (estimatedBpm in 60..200) {
                (60_000.0 / estimatedBpm.toDouble() * 0.38).coerceIn(115.0, 220.0)
            } else {
                140.0
            }

            val sinceKick = streamTimeMs - lastKickTimeMs
            val sinceSnare = streamTimeMs - lastSnareTimeMs
            val sinceClap = streamTimeMs - lastClapTimeMs
            val sinceVocal = streamTimeMs - lastVocalTimeMs
            val sinceHihat = streamTimeMs - lastHihatTimeMs
            val sinceCrash = streamTimeMs - lastCrashTimeMs
            val sinceSwell = streamTimeMs - lastSwellTimeMs
            val sinceAnyBeat = streamTimeMs - lastAnyBeatTimeMs

            val normSub = (subRmsCurr * agcGain * 2.2f).coerceIn(0f, 1f)
            val btOffsetMs = if (AudioOutputStatus.current.value.routeKind == AudioRouting.Kind.BLUETOOTH) {
                BLUETOOTH_EXTRA_LATENCY_MS
            } else {
                0L
            }
            val delayMs = (acousticLeadMs + btOffsetMs - engine.motorWakeLatencyMs + cfg.syncOffsetMs)
                .coerceAtLeast(0L)

            val bassW = cfg.bassWeight.coerceIn(0f, 1f)
            val vocalW = cfg.vocalWeight.coerceIn(0f, 1f)
            val percW = cfg.percussionWeight.coerceIn(0f, 1f)

            // 1. SUB_DROP — massive 808 slam or simultaneous sub + kick downbeat
            val isSubDrop = bassW > 0.05f &&
                isSubOnset &&
                sinceKick >= beatCooldownMs &&
                (
                    (isKickOnset && subFluxCurr > subThresh * 1.28f) ||
                        (subRmsCurr > max(0.11f, subMean * 1.85f) && subFluxCurr > subThresh * 1.55f)
                    )

            if (isSubDrop) {
                val strength = (((subFluxCurr / (subThresh * 2.0f)) * 0.60f + normSub * 0.45f) * bassW)
                    .coerceIn(0.45f, 1.0f)
                recordBeatTempo(streamTimeMs)
                lastKickTimeMs = streamTimeMs
                lastAnyBeatTimeMs = streamTimeMs
                firedKind = HapticEventKind.SUB_DROP
                engine.schedule(HapticBeatEvent(HapticEventKind.SUB_DROP, strength, normSub, 54, delayMs))
            }
            // 2. KICK_PUNCH — tight kick drum hit (sub or upper-kick band)
            else if (bassW > 0.05f && (isSubOnset || isKickOnset) && sinceKick >= beatCooldownMs) {
                val ratio = max(
                    if (isSubOnset) subFluxCurr / subThresh else 0f,
                    if (isKickOnset) kickFluxCurr / kickThresh else 0f,
                ).coerceIn(1.0f, 3.2f)
                val strength = ((0.38f + (ratio - 1.0f) * 0.28f + normSub * 0.16f) * bassW)
                    .coerceIn(0.24f, 0.96f)
                recordBeatTempo(streamTimeMs)
                lastKickTimeMs = streamTimeMs
                lastAnyBeatTimeMs = streamTimeMs
                firedKind = HapticEventKind.KICK_PUNCH
                engine.schedule(HapticBeatEvent(HapticEventKind.KICK_PUNCH, strength, normSub, 30, delayMs))
            }
            // 3. SNARE_CRACK — punchy snare body (220–650 Hz) + crack
            else if (percW > 0.05f && isSnareOnset && sinceSnare >= 100.0 && sinceKick >= 50.0) {
                val allowSnare = when (cfg.profile) {
                    MusicHapticsProfile.BASS_BEAT -> snareFluxCurr > snareThresh * 1.45f
                    else -> true
                }
                if (allowSnare) {
                    val ratio = (snareFluxCurr / snareThresh).coerceIn(1.0f, 3.0f)
                    val profileGain = if (cfg.profile == MusicHapticsProfile.CRISP_PERCUSSION) 1.08f else 0.94f
                    val strength = ((0.30f + (ratio - 1.0f) * 0.30f) * profileGain * percW).coerceIn(0.20f, 0.94f)
                    lastSnareTimeMs = streamTimeMs
                    lastAnyBeatTimeMs = streamTimeMs
                    firedKind = HapticEventKind.SNARE_CRACK
                    engine.schedule(HapticBeatEvent(HapticEventKind.SNARE_CRACK, strength, normSub * 0.4f, 24, delayMs))
                }
            }
            // 4. CLAP_SNAP — sharp simultaneous vocal-mid + air burst (handclap / pop snap)
            else if (percW > 0.05f && isVocalOnset && isAirOnset && sinceClap >= 110.0 && sinceSnare >= 70.0) {
                val allowClap = cfg.profile != MusicHapticsProfile.BASS_BEAT
                if (allowClap) {
                    val ratio = ((vocalFluxCurr / vocalThresh + airFluxCurr / airThresh) * 0.5f).coerceIn(1.0f, 3.0f)
                    val strength = ((0.28f + (ratio - 1.0f) * 0.26f) * percW).coerceIn(0.20f, 0.88f)
                    lastClapTimeMs = streamTimeMs
                    lastAnyBeatTimeMs = streamTimeMs
                    firedKind = HapticEventKind.CLAP_SNAP
                    engine.schedule(HapticBeatEvent(HapticEventKind.CLAP_SNAP, strength, 0.2f, 26, delayMs))
                }
            }
            // 5. CRASH_BUILD — broadband cymbal crash / chorus riser
            else if (percW > 0.05f && isAirOnset && airFluxCurr > airThresh * 1.65f &&
                airRmsCurr > 0.030f && sinceCrash >= 320.0 && sinceKick >= 60.0
            ) {
                val allowCrash = cfg.profile == MusicHapticsProfile.IMMERSIVE || cfg.profile == MusicHapticsProfile.BALANCED
                if (allowCrash) {
                    val strength = ((0.34f + (airFluxCurr / (airThresh * 2.5f)) * 0.35f) * percW).coerceIn(0.24f, 0.82f)
                    lastCrashTimeMs = streamTimeMs
                    firedKind = HapticEventKind.CRASH_BUILD
                    engine.schedule(HapticBeatEvent(HapticEventKind.CRASH_BUILD, strength, 0.35f, 56, delayMs))
                }
            }
            // 6. VOCAL_SYLLABLE — sung lyric / lead vocal attack phrasing
            else if (vocalW > 0.05f && isVocalOnset && sinceVocal >= 88.0 && sinceAnyBeat >= 42.0) {
                val allowVocal = when (cfg.profile) {
                    MusicHapticsProfile.IMMERSIVE -> true
                    MusicHapticsProfile.BALANCED -> vocalFluxCurr > vocalThresh * 1.12f
                    MusicHapticsProfile.CRISP_PERCUSSION, MusicHapticsProfile.BASS_BEAT -> false
                }
                if (allowVocal) {
                    val ratio = (vocalFluxCurr / vocalThresh).coerceIn(1.0f, 3.0f)
                    val profileGain = if (cfg.profile == MusicHapticsProfile.IMMERSIVE) 1.05f else 0.86f
                    val strength = ((0.22f + (ratio - 1.0f) * 0.24f) * profileGain * vocalW).coerceIn(0.16f, 0.74f)
                    lastVocalTimeMs = streamTimeMs
                    firedKind = HapticEventKind.VOCAL_SYLLABLE
                    engine.schedule(HapticBeatEvent(HapticEventKind.VOCAL_SYLLABLE, strength, 0.18f, 34, delayMs))
                }
            }
            // 7. HIHAT_SHIMMER — crisp hi-hat / shaker micro-tick
            else if (percW > 0.05f && isAirOnset && sinceHihat >= 68.0 && sinceAnyBeat >= 40.0) {
                val allowHihat = when (cfg.profile) {
                    MusicHapticsProfile.CRISP_PERCUSSION, MusicHapticsProfile.IMMERSIVE -> true
                    MusicHapticsProfile.BALANCED -> airFluxCurr > airThresh * 1.30f
                    MusicHapticsProfile.BASS_BEAT -> false
                }
                if (allowHihat) {
                    val ratio = (airFluxCurr / airThresh).coerceIn(1.0f, 3.0f)
                    val strength = ((0.16f + (ratio - 1.0f) * 0.20f) * percW).coerceIn(0.12f, 0.60f)
                    lastHihatTimeMs = streamTimeMs
                    firedKind = HapticEventKind.HIHAT_SHIMMER
                    engine.schedule(HapticBeatEvent(HapticEventKind.HIHAT_SHIMMER, strength, 0.08f, 10, delayMs))
                }
            }
            // 8. BASS_GROOVE — sustained 808 slide or deep bassline swell
            else if (bassW > 0.05f && sinceAnyBeat >= 95.0 && sinceSwell >= 110.0 &&
                cfg.profile != MusicHapticsProfile.CRISP_PERCUSSION
            ) {
                val subGate = max(0.082f, subMean * 1.14f)
                if (subRmsCurr > subGate) {
                    val strength = (((subRmsCurr - subGate) * 3.8f + 0.22f) * bassW).coerceIn(0.18f, 0.70f)
                    lastSwellTimeMs = streamTimeMs
                    firedKind = HapticEventKind.BASS_GROOVE
                    engine.schedule(HapticBeatEvent(HapticEventKind.BASS_GROOVE, strength, normSub, 62, delayMs))
                }
                // 9. VOCAL_SWELL — held melodic chorus / vocal pad shimmer
                else if (vocalW > 0.05f && cfg.profile == MusicHapticsProfile.IMMERSIVE && sinceVocal >= 75.0) {
                    val vocalGate = max(0.085f, vocalMean * 1.18f)
                    if (vocalRmsCurr > vocalGate) {
                        val strength = (((vocalRmsCurr - vocalGate) * 3.2f + 0.18f) * vocalW).coerceIn(0.14f, 0.56f)
                        lastSwellTimeMs = streamTimeMs
                        firedKind = HapticEventKind.VOCAL_SWELL
                        engine.schedule(HapticBeatEvent(HapticEventKind.VOCAL_SWELL, strength, 0.15f, 52, delayMs))
                    }
                }
            }
        }

        // Push `curr` into rolling history & advance 3-point window
        subHistory[historyIndex] = subRmsCurr
        vocalHistory[historyIndex] = vocalRmsCurr
        subFluxHistory[historyIndex] = subFluxCurr
        kickFluxHistory[historyIndex] = kickFluxCurr
        snareFluxHistory[historyIndex] = snareFluxCurr
        vocalFluxHistory[historyIndex] = vocalFluxCurr
        airFluxHistory[historyIndex] = airFluxCurr
        historyIndex = (historyIndex + 1) % HISTORY_SIZE
        if (historyFilled < HISTORY_SIZE) historyFilled++

        subFluxPrev = subFluxCurr; subFluxCurr = subFluxNext; subRmsCurr = subNext
        kickFluxPrev = kickFluxCurr; kickFluxCurr = kickFluxNext; kickRmsCurr = kickNext
        snareFluxPrev = snareFluxCurr; snareFluxCurr = snareFluxNext; snareRmsCurr = snareNext
        vocalFluxPrev = vocalFluxCurr; vocalFluxCurr = vocalFluxNext; vocalRmsCurr = vocalNext
        airFluxPrev = airFluxCurr; airFluxCurr = airFluxNext; airRmsCurr = airNext
        totalRmsCurr = totalNext

        MusicHapticsTelemetry.publishFrame(
            sub = visSub,
            kick = visKick,
            snare = visSnare,
            vocal = visVocal,
            air = visAir,
            bpm = estimatedBpm,
            eventKind = firedKind,
            leadMs = acousticLeadMs.toInt(),
        )
    }

    private fun recordBeatTempo(nowMs: Double) {
        if (lastKickTimeMs < 0.0) return
        var ioi = (nowMs - lastKickTimeMs).toFloat()
        if (ioi < 160f || ioi > 2200f) return
        while (ioi < 342f) ioi *= 2f
        while (ioi > 860f) ioi *= 0.5f

        beatIntervalsMs[bpmIndex] = ioi
        bpmIndex = (bpmIndex + 1) % BPM_WINDOW
        if (bpmCount < BPM_WINDOW) bpmCount++

        if (bpmCount >= 3) {
            var sum = 0f
            for (i in 0 until bpmCount) sum += beatIntervalsMs[i]
            val avgIoi = sum / bpmCount.toFloat()
            if (avgIoi > 0f) {
                estimatedBpm = (60_000f / avgIoi).roundToInt().coerceIn(55, 210)
            }
        }
    }

    private fun mean(arr: FloatArray, count: Int): Float {
        if (count <= 0) return 0f
        var s = 0f
        for (i in 0 until count) s += arr[i]
        return s / count.toFloat()
    }

    private fun stdDev(arr: FloatArray, count: Int, meanVal: Float): Float {
        if (count <= 1) return 0f
        var sq = 0f
        for (i in 0 until count) {
            val d = arr[i] - meanVal
            sq += d * d
        }
        return sqrt(sq / count.toFloat())
    }

    private class BiquadFilter {
        private var b0: Float = 1f
        private var b1: Float = 0f
        private var b2: Float = 0f
        private var a1: Float = 0f
        private var a2: Float = 0f
        private var z1: Float = 0f
        private var z2: Float = 0f

        fun configureLowPass(sampleRate: Int, cutoffHz: Double, q: Double) {
            val w0 = 2.0 * PI * min(cutoffHz, sampleRate * 0.45) / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) * 0.5 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = b0
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
            reset()
        }

        fun configureHighPass(sampleRate: Int, cutoffHz: Double, q: Double) {
            val w0 = 2.0 * PI * min(cutoffHz, sampleRate * 0.45) / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW0) * 0.5 / a0).toFloat()
            b1 = (-(1.0 + cosW0) / a0).toFloat()
            b2 = b0
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
            reset()
        }

        fun configureBandPass(sampleRate: Int, centerHz: Double, q: Double) {
            val w0 = 2.0 * PI * min(centerHz, sampleRate * 0.45) / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = (alpha / a0).toFloat()
            b1 = 0f
            b2 = (-alpha / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
            reset()
        }

        fun process(input: Float): Float {
            val out = b0 * input + z1
            z1 = b1 * input - a1 * out + z2
            z2 = b2 * input - a2 * out
            return out
        }

        fun reset() {
            z1 = 0f
            z2 = 0f
        }
    }

    companion object {
        private const val HOP_DURATION_SEC = 0.008 // 8 ms ultra-high-res hop (125 Hz)
        private const val HISTORY_SIZE = 124       // ~1.0 s adaptive window
        private const val MIN_WARMUP_HOPS = 14
        private const val BPM_WINDOW = 8
        private const val SILENCE_RMS_GATE = 0.007f
        private const val MIN_SUB_FLUX = 0.0085f
        private const val MIN_KICK_FLUX = 0.0085f
        private const val MIN_SNARE_FLUX = 0.008f
        private const val MIN_VOCAL_FLUX = 0.0075f
        private const val MIN_AIR_FLUX = 0.0060f
        private const val BLUETOOTH_EXTRA_LATENCY_MS = 35L
    }
}
