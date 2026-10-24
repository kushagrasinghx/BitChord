package com.music.bitchord.device

enum class BudsControlTransport {
    LEGACY,
    STANDARD,
    MODERN,
}

enum class DeviceFamily(
    val budsTransport: BudsControlTransport? = null,
    val supportsAnc: Boolean = false,
    val supportsAmbientMode: Boolean = false,
    val supportsAdaptiveMode: Boolean = false,
    val supportsAncLevel: Boolean = false,
    val supportsAudio360: Boolean = false,
    val supportsPresetEqualizer: Boolean = false,
) {
    GALAXY_BUDS(
        budsTransport = BudsControlTransport.LEGACY,
        supportsAmbientMode = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS_PLUS(
        budsTransport = BudsControlTransport.STANDARD,
        supportsAmbientMode = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS_LIVE(
        budsTransport = BudsControlTransport.STANDARD,
        supportsAnc = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS_PRO(
        budsTransport = BudsControlTransport.STANDARD,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAncLevel = true,
        supportsAudio360 = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS2(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAudio360 = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS2_PRO(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAudio360 = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS_FE(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAncLevel = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS3(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS3_PRO(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAdaptiveMode = true,
        supportsAudio360 = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS3_FE(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAdaptiveMode = true,
        supportsAncLevel = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS_CORE(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS4(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAdaptiveMode = true,
        supportsPresetEqualizer = true,
    ),
    GALAXY_BUDS4_PRO(
        budsTransport = BudsControlTransport.MODERN,
        supportsAnc = true,
        supportsAmbientMode = true,
        supportsAdaptiveMode = true,
        supportsAudio360 = true,
        supportsPresetEqualizer = true,
    ),
    MOTO_BUDS_PLUS,
    HK_ONYX_STUDIO_9;

    val isGalaxyBuds: Boolean
        get() = budsTransport != null

    val supportsAnyNoiseControl: Boolean
        get() = supportsAnc || supportsAmbientMode
}

data class DeviceProfile(
    val family: DeviceFamily,
    val displayName: String,
)

object DeviceRegistry {

    private fun normalize(raw: String): String =
        raw.lowercase().filter { it.isLetterOrDigit() || it == '+' }

    fun identify(rawName: String?): DeviceProfile? {
        if (rawName.isNullOrBlank()) return null
        val n = normalize(rawName)
        val model = n.removePrefix("samsung").removePrefix("galaxy")
        return when {
            "buds4pro" in model || "smr640" in model || "r640" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS4_PRO, "Galaxy Buds4 Pro")
            "buds4" in model || "smr540" in model || "r540" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS4, "Galaxy Buds4")
            "buds3pro" in model || "smr630" in model || "r630" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS3_PRO, "Galaxy Buds3 Pro")
            "buds3fe" in model || "smr420" in model || "r420" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS3_FE, "Galaxy Buds3 FE")
            "buds3" in model || "smr530" in model || "r530" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS3, "Galaxy Buds3")
            "buds2pro" in model || "smr510" in model || "r510" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS2_PRO, "Galaxy Buds2 Pro")
            "buds2" in model || "smr177" in model || "r177" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS2, "Galaxy Buds2")
            "budscore" in model || "smr410" in model || "r410" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS_CORE, "Galaxy Buds Core")
            "budsfe" in model || "smr400n" in model || "r400n" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS_FE, "Galaxy Buds FE")
            "budslive" in model || "smr180" in model || "r180" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS_LIVE, "Galaxy Buds Live")
            "budspro" in model || "smr190" in model || "r190" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS_PRO, "Galaxy Buds Pro")
            "motobuds+" in n || "motobudsplus" in n ->
                DeviceProfile(DeviceFamily.MOTO_BUDS_PLUS, "Moto Buds+")
            "budsplus" in model || "buds+" in model || "smr175" in model || "r175" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS_PLUS, "Galaxy Buds+")
            model == "buds" || "smr170" in model || "r170" in model ->
                DeviceProfile(DeviceFamily.GALAXY_BUDS, "Galaxy Buds")
            "onyxstudio9" in n ->
                DeviceProfile(DeviceFamily.HK_ONYX_STUDIO_9, "Onyx Studio 9")
            else -> null
        }
    }
}
