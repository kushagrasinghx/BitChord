package com.music.bitchord.playback.smart

/**
 * P2-smart: the mix conductor. One decision, made once at plan time, that
 * the renderer then performs without improvisation.
 *
 * Before this, five mechanisms (duck keys, proactive cut, separation,
 * mid-kill, volume mute) each read their own gate every tick — sometimes two
 * of them fought over the same band (proactive opening what separation was
 * shaping), sometimes none of them fired (vocalOverlap == 0 → whole cast
 * stands down) and the EQ did nothing. The conductor replaces the cast's
 * independent judgment with a single per-pair recipe: it reads the model's
 * evidence once — vocal masks on both sides, drop trust, buildup shape,
 * intro/outro quality, trajectory — and states which of the four DJ moves
 * this pair gets. Render actors keep their hands (they still voice their
 * band) but lose their vote (they never re-decide).
 */
enum class MixRecipe {
    /**
     * Both sides sing (or the incoming side enters singing): the classic DJ
     * vocal handoff. Outgoing mids dive early so the incoming vocal owns the
     * band from the midpoint; bass swaps on phrase; highs leave last. The
     * listener only notices the track changed near the end of the blend.
     */
    VOCAL_DUEL,
    /**
     * No vocal collision: the long smooth instrumental blend. Mids stay open
     * longer, bass swap lands late, nothing is hurried.
     */
    INSTRUMENTAL_BED,
    /**
     * Unblendable pair (weak tempo/key, weak ends, heavy clash): the wash.
     * Echo/reverb carry the exit while the outgoing mids still cut early —
     * a wash with no mud underneath. Exempt from the volume mute, as before.
     */
    WASH_OUT,
    /** A trusted drop with colliding energy: the honest cut on the downbeat. */
    CUT_DROP,
}

/**
 * Picks the recipe. Pure — all inputs are plan-time evidence, so the same
 * pair always gets the same show.
 *
 * @param type the matrix verdict (routes wash/cut families first — a pair
 *   the matrix refused to blend never auditions for a blend recipe).
 * @param duckA delayB forceDuck the vocal evidence the renderer will ride on
 *   (ARM flags + choke keys): any of them true means a voice is in play.
 * @param vocalOverlap planned simultaneous-vocal fraction 0..1.
 * @param dropConfidence selectFirstDrop winner score, null = unmeasured
 *   fallback drop (max-RMS) that must never unlock a cut.
 */
fun selectMixRecipe(
    type: TransitionType,
    duckA: Boolean,
    delayB: Boolean,
    forceDuck: Boolean,
    vocalOverlap: Double,
    dropConfidence: Double?,
): MixRecipe {
    when (type) {
        TransitionType.ECHO_REVERB_OUT,
        TransitionType.PLAIN_DISSOLVE,
        -> return MixRecipe.WASH_OUT
        TransitionType.HARD_CUT,
        TransitionType.LOOP_CUT_DROP,
        TransitionType.LOOP_ROLL,
        -> {
            // A cut on an unmeasured drop is a guess with a knife: wash it.
            if ((type == TransitionType.LOOP_CUT_DROP || type == TransitionType.LOOP_ROLL) &&
                dropConfidence == null
            ) {
                return MixRecipe.WASH_OUT
            }
            return MixRecipe.CUT_DROP
        }
        else -> Unit
    }
    if (duckA || delayB || forceDuck || vocalOverlap > 0.2) {
        return MixRecipe.VOCAL_DUEL
    }
    return MixRecipe.INSTRUMENTAL_BED
}
