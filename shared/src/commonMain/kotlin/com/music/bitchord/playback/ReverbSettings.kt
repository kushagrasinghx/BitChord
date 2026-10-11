package com.music.bitchord.playback

/**
 * The listener's reverb, everything but the on/off switch.
 *
 * The room is described by its tail length (RT60), pre-delay and the
 * frequency the tail starts losing treble from, plus the wet share and the
 * stereo width of the tail. Same numbers on every
 * platform: both engines run [ReverbProcessor] over them.
 *
 * The switch lives beside this rather than in it, so the player's toggle can
 * flip it without touching the room the Settings page dialled in.
 *
 * @param decayMs time for the tail to fall 60 dB — how big the space sounds.
 * @param preDelayMs silence between the dry sound and the tail — how far away the walls are.
 * @param dampingHz where the tail starts losing highs; lower is darker.
 * @param mix wet share, blended as `(1 - mix) * dry + mix * wet`.
 * @param width stereo spread of the tail, 0 mono to 1 full.
 */
data class ReverbSettings(
    val decayMs: Int = ReverbPreset.HALL.decayMs,
    val preDelayMs: Int = ReverbPreset.HALL.preDelayMs,
    val dampingHz: Int = ReverbPreset.HALL.dampingHz,
    val mix: Float = DEFAULT_MIX,
    val width: Float = 1f,
) {
    /** Every field pulled back into the range the sliders offer. */
    fun clamped(): ReverbSettings = ReverbSettings(
        decayMs = decayMs.coerceIn(DECAY_RANGE_MS),
        preDelayMs = preDelayMs.coerceIn(PRE_DELAY_RANGE_MS),
        dampingHz = dampingHz.coerceIn(DAMPING_RANGE_HZ),
        mix = mix.coerceIn(0f, 1f),
        width = width.coerceIn(0f, 1f),
    )

    /** The room of [preset], keeping this mix and width — picking a room is not picking a level. */
    fun withPreset(preset: ReverbPreset): ReverbSettings =
        copy(decayMs = preset.decayMs, preDelayMs = preset.preDelayMs, dampingHz = preset.dampingHz)

    /** Which preset's room this is, or null once a room slider has moved off every one of them. */
    val preset: ReverbPreset?
        get() = ReverbPreset.entries.firstOrNull {
            it.decayMs == decayMs && it.preDelayMs == preDelayMs && it.dampingHz == dampingHz
        }

    /**
     * One string, so it is one preference to keep in step and one thing a
     * backup carries — the same reason the equaliser's bands are stored this way.
     */
    fun encode(): String = "$decayMs,$preDelayMs,$dampingHz,$mix,$width"

    companion object {
        const val DEFAULT_MIX = 0.35f
        val DEFAULT = ReverbSettings()

        val DECAY_RANGE_MS = 200..6_000
        val PRE_DELAY_RANGE_MS = 0..200
        val DAMPING_RANGE_HZ = 1_000..16_000

        /**
         * Reads what [encode] wrote. A field missing or unreadable — a file from
         * a build that stored fewer of them, or none at all — keeps its default
         * rather than throwing the rest away.
         */
        fun decode(stored: String?): ReverbSettings {
            val parts = stored.orEmpty().split(",").map { it.trim() }
            fun int(index: Int) = parts.getOrNull(index)?.toIntOrNull()
            fun float(index: Int) = parts.getOrNull(index)?.toFloatOrNull()?.takeIf { !it.isNaN() }
            return ReverbSettings(
                decayMs = int(0) ?: DEFAULT.decayMs,
                preDelayMs = int(1) ?: DEFAULT.preDelayMs,
                dampingHz = int(2) ?: DEFAULT.dampingHz,
                mix = float(3) ?: DEFAULT.mix,
                width = float(4) ?: DEFAULT.width,
            ).clamped()
        }
    }
}

/**
 * The four rooms offered. Each is a size, distance and darkness of room that
 * [ReverbProcessor]'s tank is aimed at, not a recorded impulse.
 */
enum class ReverbPreset(val decayMs: Int, val preDelayMs: Int, val dampingHz: Int) {
    ROOM(decayMs = 600, preDelayMs = 5, dampingHz = 6_000),
    HALL(decayMs = 2_200, preDelayMs = 20, dampingHz = 4_500),
    PLATE(decayMs = 1_500, preDelayMs = 0, dampingHz = 9_000),
    CATHEDRAL(decayMs = 4_000, preDelayMs = 30, dampingHz = 3_000),
}
