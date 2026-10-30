package app.stemsep.dsp

enum class StemMode {
    VOCALS,
    INSTRUMENTAL,
    MIX,
}

data class StemGains(val vocal: Float, val instrumental: Float)

object StemMixer {
    fun gains(mode: StemMode, vocalLevel: Float = 1f): StemGains = when (mode) {
        StemMode.VOCALS -> StemGains(1f, 0f)
        StemMode.INSTRUMENTAL -> StemGains(0f, 1f)
        StemMode.MIX -> StemGains(vocalLevel.coerceIn(0f, 1f), 1f)
    }

    fun mixFrame(
        vocalLeft: Float,
        vocalRight: Float,
        instrumentalLeft: Float,
        instrumentalRight: Float,
        gains: StemGains,
        out: FloatArray,
        offset: Int = 0,
    ) {
        out[offset] = vocalLeft * gains.vocal + instrumentalLeft * gains.instrumental
        out[offset + 1] = vocalRight * gains.vocal + instrumentalRight * gains.instrumental
    }
}
