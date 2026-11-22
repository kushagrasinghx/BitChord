package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.ui.replay.year.BestPart
import com.music.bitchord.ui.replay.year.BestPartStart
import com.music.bitchord.ui.replay.year.Heatmap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins where the Replay year story starts each song: the bump in YouTube's
 * "Most replayed" curve, else the chorus in the lyrics, else a fixed point.
 */
class ReplayBestPartTest {

    /** A four-minute song in YouTube's hundred equal segments. */
    private val segment = 2_400L

    /**
     * How a real curve looks: hot at the very start, sagging as people drift
     * off, with the chorus standing up out of the sag at [bumpFrom]..[bumpTo].
     */
    private fun decaying(bumpFrom: Int? = null, bumpTo: Int? = null): Heatmap = Heatmap(
        segment,
        List(100) { i ->
            val base = if (i == 0) 1f else 0.6f - 0.003f * i
            val bump = if (bumpFrom != null && bumpTo != null && i in bumpFrom..bumpTo) 0.25f else 0f
            base + bump
        },
    )

    private fun nextAnswer(markers: List<Float>, type: String = "MARKER_TYPE_HEATMAP"): JsonObject =
        buildJsonObject {
            putJsonObject("frameworkUpdates") {
                putJsonObject("entityBatchUpdate") {
                    putJsonArray("mutations") {
                        // A chapters entity first: it is the same shape and must be skipped.
                        addJsonObject {
                            putJsonObject("payload") {
                                putJsonObject("macroMarkersListEntity") {
                                    putJsonObject("markersList") {
                                        put("markerType", "MARKER_TYPE_CHAPTERS")
                                        putJsonArray("markers") {}
                                    }
                                }
                            }
                        }
                        addJsonObject {
                            putJsonObject("payload") {
                                putJsonObject("macroMarkersListEntity") {
                                    putJsonObject("markersList") {
                                        put("markerType", type)
                                        putJsonArray("markers") {
                                            markers.forEachIndexed { i, heat ->
                                                addJsonObject {
                                                    put("startMillis", (i * segment).toString())
                                                    put("durationMillis", segment.toString())
                                                    put("intensityScoreNormalized", heat)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun theHeatMarkersAreReadOutOfTheNextAnswer() {
        val heat = BestPart.parseHeatmap(nextAnswer(decaying(40, 50).intensity))
        assertNotNull(heat)
        assertEquals(segment, heat!!.segmentMs)
        assertEquals(100, heat.intensity.size)
        assertEquals(1f, heat.intensity[0])
    }

    @Test
    fun anAnswerWithoutHeatMarkersHasNoCurve() {
        assertNull(BestPart.parseHeatmap(nextAnswer(decaying().intensity, type = "MARKER_TYPE_CHAPTERS")))
        assertNull(BestPart.parseHeatmap(buildJsonObject { put("contents", "nothing") }))
    }

    @Test
    fun aVideoWithTooFewMarkersHasNoCurve() {
        assertNull(BestPart.parseHeatmap(nextAnswer(List(6) { 0.5f })))
    }

    @Test
    fun theChorusBumpWinsOverTheHotOpening() {
        val start = BestPart.peakMs(decaying(40, 50), durationMs = 240_000)
        assertNotNull(start)
        // Where the bump starts climbing, not its middle and not the opening.
        assertTrue("started at $start", start!! in 38 * segment..41 * segment)
    }

    @Test
    fun aFlatCurveHasNoBestPart() {
        assertNull(BestPart.peakMs(Heatmap(segment, List(100) { 0.4f }), durationMs = 240_000))
    }

    @Test
    fun aShortSongHasNoBestPart() {
        assertNull(BestPart.peakMs(decaying(40, 50), durationMs = 50_000))
    }

    @Test
    fun aBumpInTheLastHalfMinuteDoesNotCount() {
        // 97..99 is the final seven seconds of a four-minute song.
        assertNull(BestPart.peakMs(decaying(97, 99), durationMs = 240_000))
    }

    private val lyrics = listOf(
        LyricLine(10_000, "Woke up early in the morning"),
        LyricLine(14_000, "Coffee on the window sill"),
        LyricLine(18_000, "Every road is getting longer"),
        LyricLine(22_000, ""),
        LyricLine(30_000, "Hold on to me"),
        LyricLine(34_000, "Never let go"),
        LyricLine(50_000, "Streetlights humming out of tune"),
        LyricLine(54_000, "Somebody singing on the bus"),
        LyricLine(70_000, "Hold on to me"),
        LyricLine(74_000, "Never let go"),
        LyricLine(110_000, "Hold on, to me!"),
        LyricLine(114_000, "Never let go"),
    )

    @Test
    fun theChorusIsWhereTheMostRepeatedLinesFirstCome() {
        assertEquals(30_000L, BestPart.chorusMs(lyrics, durationMs = 180_000))
    }

    @Test
    fun wordsThatNeverRepeatHaveNoChorus() {
        val verses = lyrics.filterNot { it.text.contains("Hold") || it.text.contains("Never") }
        assertNull(BestPart.chorusMs(verses, durationMs = 180_000))
    }

    @Test
    fun theStartIsPulledBackToALineBeginning() {
        // The earlier line is preferred over a slightly nearer later one.
        assertEquals(94_950L, BestPart.snap(96_000, listOf(95_200, 96_500)))
        // No line nearby: a second early.
        assertEquals(95_000L, BestPart.snap(96_000, listOf(10_000, 200_000)))
    }

    @Test
    fun theCurveIsTrustedBeforeTheWordsAndTheWordsBeforeTheFallback() {
        val heat = decaying(40, 50)
        assertEquals(BestPartStart.Source.HEATMAP, BestPart.pick(heat, lyrics, 240_000).source)
        assertEquals(BestPartStart.Source.LYRICS, BestPart.pick(null, lyrics, 240_000).source)
        assertEquals(
            BestPartStart.Source.LYRICS,
            BestPart.pick(Heatmap(segment, List(100) { 0.4f }), lyrics, 240_000).source,
        )
        val nothing = BestPart.pick(null, emptyList(), 200_000)
        assertEquals(BestPartStart.Source.FALLBACK, nothing.source)
        assertEquals(60_000L, nothing.startMs)
    }

    @Test
    fun theFallbackIsPastTheIntroAndBeforeTheEnd() {
        assertEquals(45_000L, BestPart.fallbackMs(0))
        assertEquals(0L, BestPart.fallbackMs(40_000))
        // A minute-long song: 30% would be inside the intro, so it's held at 20 s.
        assertEquals(20_000L, BestPart.fallbackMs(61_000))
        // A long one: 30% in, still well clear of the last half minute.
        assertEquals(90_000L, BestPart.fallbackMs(300_000))
    }
}
