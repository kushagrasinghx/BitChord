package com.music.bitchord.playback.smart

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisStoreTest {
    private fun temporary(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("bitchord-analysis-test-").toFile()
        try { test(directory) } finally {
            File(directory, "smart_analysis").listFiles()?.forEach { it.delete() }
            File(directory, "smart_analysis").delete()
            directory.delete()
        }
    }

    private fun analysis(id: String, bpm: Double = 120.0) = TrackAnalysis(
        trackId = id, status = TrackAnalysis.STATUS_READY, duration = 180.0,
        bpm = bpm, beatInterval = 60 / bpm, beatConfidence = 0.9,
    )

    @Test fun collidingJavaIdsKeepTheirOwnSongProfiles() = temporary { directory ->
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val store = AnalysisStore { directory }
        store.save("Aa", analysis("Aa", 120.0))
        store.save("BB", analysis("BB", 140.0))
        assertEquals(2, File(directory, "smart_analysis").listFiles()!!.size)
        assertEquals(120.0, store.load("Aa")!!.bpm)
        assertEquals(140.0, store.load("BB")!!.bpm)
    }

    @Test fun outdatedModelRevisionsAreReanalysed() = temporary { directory ->
        AnalysisStore { directory }.save("song", analysis("song"))
        val file = File(directory, "smart_analysis").listFiles()!!.single()
        file.writeText(file.readText().replace(AutomixModels.ANALYSIS_REVISION, "old-models"))
        assertNull(AnalysisStore { directory }.load("song"))
        assertTrue(!file.exists())
    }

    @Test fun theStoredTrackMustMatchTheRequestedTrack() = temporary { directory ->
        AnalysisStore { directory }.save("Aa", analysis("Aa"))
        val file = File(directory, "smart_analysis").listFiles()!!.single()
        file.writeText(file.readText().replace("\"trackId\":\"Aa\"", "\"trackId\":\"BB\""))
        assertNull(AnalysisStore { directory }.load("Aa"))
    }

    @Test fun aMismatchedAnalysisCannotBeSavedUnderAnotherId() = temporary { directory ->
        AnalysisStore { directory }.save("Aa", analysis("BB"))
        assertNull(AnalysisStore { directory }.load("Aa"))
    }
}
