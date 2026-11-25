package com.music.bitchord.playback

/**
 * Whether the Karaoke vocal reduction can run on the audio that is actually
 * playing and — when it cannot — the first reason why, in the order the
 * signal path fails.
 *
 * Runtime fact, not a preference: published by the playback service the way
 * `AppSettings.audioSessionId` is, never persisted. A preference left enabled
 * while a route is incompatible reads as the matching non-[AVAILABLE] state
 * rather than silently doing nothing, and returns to [AVAILABLE] on its own
 * when a compatible local route comes back.
 */
enum class KaraokeProcessingAvailability {
    /** Local stereo decoded PCM through the normal processed sink: the vocal stage runs. */
    AVAILABLE,

    /** The track is mono or multichannel: channel cancellation needs stereo, and mono is not upmixed. */
    NEEDS_STEREO,

    /** The stream was never decoded to linear PCM (passthrough/offload): there is nothing to filter. */
    NEEDS_LINEAR_PCM,

    /** Bit-perfect output is active and bypasses the DSP chain entirely. */
    BIT_PERFECT_ACTIVE,

    /** Cast or the watch is the speaker, and it plays the original stream rather than this phone's PCM. */
    REMOTE_OUTPUT_ACTIVE,
    ;

    companion object {
        /**
         * The first failure in signal-path order: a remote speaker playing the
         * original stream outranks everything, then the bit-perfect bypass,
         * then whether there is decoded PCM to filter at all, and only then
         * whether that PCM is stereo. Unknown channels are not a failure —
         * nothing has reported a format yet, not "the track is mono".
         */
        fun resolve(
            channels: Int?,
            dspAvailable: Boolean,
            bitPerfectActive: Boolean,
            remoteOutputActive: Boolean,
        ): KaraokeProcessingAvailability = when {
            remoteOutputActive -> REMOTE_OUTPUT_ACTIVE
            bitPerfectActive -> BIT_PERFECT_ACTIVE
            !dspAvailable -> NEEDS_LINEAR_PCM
            channels != null && channels != 2 -> NEEDS_STEREO
            else -> AVAILABLE
        }
    }
}
