package app.stemsep.separation

import java.io.File

data class LiveStemPreview(
    val vocalsFile: File,
    val instrumentalFile: File,
    val availableFrames: Long,
    val totalFrames: Long,
    val sampleRate: Int,
)
