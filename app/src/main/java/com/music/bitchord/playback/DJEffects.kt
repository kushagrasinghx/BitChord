package com.music.bitchord.playback

/**
 * Orchestrates all DJ-gated audio processors. Groups the per-deck
 * sends, EQ, loop vamp, splice guard, loudness stage, and brake/dive
 * behind a single handle so [CrossfadeController] receives
 * one composition rather than ten loose parameters.
 *
 * Each field exposes its interface directly (e.g. [EchoFilters],
 * [EqFilters]) so the controller's existing constructor wiring stays
 * intact. Role-swap at handoff is the same pattern as before.
 */
class DJEffects {

    val echoSendA = EchoSendProcessor()
    val echoSendB = EchoSendProcessor()
    var activeEcho: EchoSendProcessor = echoSendA
    var spareEcho: EchoSendProcessor = echoSendB

    val reverbSendA = ReverbProcessor()
    val reverbSendB = ReverbProcessor()
    var activeReverb: ReverbProcessor = reverbSendA
    var spareReverb: ReverbProcessor = reverbSendB

    val loopVampA = LoopVampProcessor()
    val loopVampB = LoopVampProcessor()
    var activeVamp: LoopVampProcessor = loopVampA
    var spareVamp: LoopVampProcessor = loopVampB

    val djEqA = DJBandEQ()
    val djEqB = DJBandEQ()
    var activeEq: DJBandEQ = djEqA
    var spareEq: DJBandEQ = djEqB

    val spliceGuardA = SpliceGuardProcessor()
    val spliceGuardB = SpliceGuardProcessor()

    val loudnessGainA = LoudnessGainProcessor()
    val loudnessGainB = LoudnessGainProcessor()
    var activeLoudness: LoudnessGainProcessor = loudnessGainA
    var spareLoudness: LoudnessGainProcessor = loudnessGainB

    val brakeDiveA = BrakeDiveProcessor()
    val brakeDiveB = BrakeDiveProcessor()
    var activeBrakeDive: BrakeDiveProcessor = brakeDiveA
    var spareBrakeDive: BrakeDiveProcessor = brakeDiveB

    /** Returns the active brake/dive processor for the outgoing deck. */
    fun activeBrakeDive(): BrakeDiveProcessor = activeBrakeDive

    /** Open all processors â€” tails ring out naturally. */
    fun open() {
        activeEcho.open()
        activeReverb.open()
        activeVamp.open()
        activeEq.open()
        activeLoudness.open()
        activeBrakeDive.ride()
    }

    /** Close all processors â€” wipe tails immediately. */
    fun close() {
        activeEcho.clear()
        activeReverb.clear()
        activeVamp.clear()
        activeEq.open()
        activeLoudness.open()
        activeBrakeDive.ride()
    }

    /** Emergency bail â€” cut all tails immediately. */
    fun bail() {
        activeEcho.clear()
        activeReverb.clear()
        activeVamp.clear()
        activeEq.open()
        activeLoudness.open()
        activeBrakeDive.ride()
    }

    /** Reset all processors to idle state. */
    fun reset() {
        echoSendA.reset()
        echoSendB.reset()
        reverbSendA.reset()
        reverbSendB.reset()
        loopVampA.reset()
        loopVampB.reset()
        spliceGuardA.reset()
        spliceGuardB.reset()
        loudnessGainA.reset()
        loudnessGainB.reset()
        brakeDiveA.reset()
        brakeDiveB.reset()
    }

    /** Swap active/spare roles (handoff). */
    fun swapRoles() {
        val tmpEcho = activeEcho
        activeEcho = spareEcho
        spareEcho = tmpEcho

        val tmpReverb = activeReverb
        activeReverb = spareReverb
        spareReverb = tmpReverb

        val tmpVamp = activeVamp
        activeVamp = spareVamp
        spareVamp = tmpVamp

        val tmpEq = activeEq
        activeEq = spareEq
        spareEq = tmpEq

        val tmpLoudness = activeLoudness
        activeLoudness = spareLoudness
        spareLoudness = tmpLoudness

        val tmpBrake = activeBrakeDive
        activeBrakeDive = spareBrakeDive
        spareBrakeDive = tmpBrake
    }
}
