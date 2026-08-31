package com.music.bitchord.playback.smart

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Analysis results kept on disk, so a track is measured once and stays measured.
 *
 * ## Why this exists
 *
 * Analysis was in memory only, which meant every app start threw away
 * everything and every track had to earn its result again — from audio that may
 * no longer be on disk to earn it from. Two things conspire against re-earning
 * it:
 *
 *  - The analyzer needs a track's opening, contiguously, and the cache evicts
 *    openings first because they are read once and never touched again. See
 *    [com.music.bitchord.playback.DynamicLruCacheEvictor], which now holds them
 *    back — but only within a budget, and only for tracks played recently.
 *  - Even with the bytes present, analysis costs a decode plus two model
 *    inferences, several seconds, and it has to finish *before* the transition
 *    that wants it. Losing that work to a restart means the next few
 *    transitions after every launch are plain crossfades.
 *
 * Neither applies to a result already computed. The audio is a means to the
 * numbers, and the numbers are small.
 *
 * ## Shape
 *
 * One file per track, named by cache key rather than written into a single
 * index, so a write cannot corrupt anything but its own entry and a prune is a
 * file delete. Coordinates are seconds on the track's own timeline, which are
 * identical across renditions of the same recording — the whole reason an
 * analysis is worth keeping in the first place.
 *
 * Only fields the planner reads are stored, and the curves are rounded to
 * milliseconds: they are the bulk of the payload and nothing downstream can
 * tell the difference.
 */
class AnalysisStore(private val context: Context) {

    /**
     * Resolved on first use, not at construction. [TrackAnalyzer] is a field
     * initializer on the playback service, which runs before the service has a
     * base context attached — asking for [Context.getFilesDir] there returns
     * null and takes the whole process down before it can start.
     */
    private val directory by lazy { File(context.filesDir, DIRECTORY) }

    /**
     * Track ids known to have no file, so a track analysed in neither this
     * session nor a previous one does not hit the filesystem on every tick.
     * Only ever grows within a session, and a write clears the entry.
     */
    private val known = ConcurrentHashMap<String, Boolean>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Reads [trackId]'s stored analysis, or null when there isn't one. */
    fun load(trackId: String): TrackAnalysis? {
        if (trackId.isBlank()) return null
        if (known[trackId] == false) return null
        val file = File(directory, fileNameFor(trackId))
        if (!file.exists()) {
            known[trackId] = false
            return null
        }
        return runCatching {
            val stored = json.decodeFromString(Stored.serializer(), file.readText())
            // v1 remains a valid beat/key foundation. Missing v2 fields are
            // represented by defaults and completed lazily when audio is next
            // available, rather than invalidating the whole library.
            require(stored.version in 1..SCHEMA_VERSION) { "schema ${stored.version}" }
            stored.toAnalysis(trackId)
        }
            .onFailure {
                // A half-written or outdated file is worth exactly nothing and
                // costs a re-analysis to replace, so it goes rather than being
                // returned as a partly-filled result.
                Log.w(TAG, "Discarding unreadable analysis for $trackId", it)
                file.delete()
                known[trackId] = false
            }
            .getOrNull()
    }

    /**
     * Stores [analysis]. Silently does nothing for a result with no usable
     * tempo: a failure is cheap to rediscover and worth rediscovering, since
     * the reason for it is usually missing bytes rather than the track itself.
     */
    fun save(trackId: String, analysis: TrackAnalysis) {
        if (trackId.isBlank() || !analysis.isUsable) return
        runCatching {
            directory.mkdirs()
            val file = File(directory, fileNameFor(trackId))
            // Written aside and renamed, so a kill mid-write leaves the old
            // entry rather than a truncated one.
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeText(json.encodeToString(Stored.serializer(), Stored.of(analysis)))
            if (!temporary.renameTo(file)) temporary.delete()
            known[trackId] = true
        }.onFailure { Log.w(TAG, "Could not store analysis for $trackId", it) }
        prune()
    }

    /**
     * Keeps the directory under [MAX_ENTRIES], oldest first.
     *
     * Cheap because it only lists when the count is plausibly over — a
     * directory listing per save would otherwise be a filesystem walk on every
     * analysis.
     */
    private fun prune() {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }

    /** Hashed rather than used raw: a track id is not guaranteed to be a legal filename. */
    private fun fileNameFor(trackId: String): String = "${trackId.hashCode().toUInt()}_${trackId.length}.json"

    /**
     * The persisted subset, kept separate from [TrackAnalysis] so that adding a
     * field to the in-memory type is not silently a schema change.
     *
     * [version] is checked on read through [ignoreUnknownKeys] plus an explicit
     * comparison: an entry written by an older build may hold numbers computed a
     * different way, and a wrong beat grid is worse than no beat grid.
     */
    @Serializable
    private data class Stored(
        val version: Int = SCHEMA_VERSION,
        val duration: Double = 0.0,
        val bpm: Double = 0.0,
        val beatInterval: Double = 0.0,
        val beatConfidence: Double = 0.0,
        val firstBeat: Double = 0.0,
        val downbeats: List<Double> = emptyList(),
        val phraseBoundaries: List<Double> = emptyList(),
        val key: String = "",
        val keyConfidence: Double = 0.0,
        val audibleStartTime: Double? = null,
        val pickupTime: Double? = null,
        val introEndTime: Double = 0.0,
        val outroStartTime: Double = 0.0,
        val contentEndTime: Double = 0.0,
        val mixInTime: Double = 0.0,
        val mixOutTime: Double = 0.0,
        val mixInCandidates: List<StoredCue> = emptyList(),
        val mixOutCandidates: List<StoredCue> = emptyList(),
        val energyCurve: List<StoredEnergy> = emptyList(),
        val lowEnergyCurve: List<StoredEnergy> = emptyList(),
        val vocalActivityMask: List<Double> = emptyList(),
        val vocalProbability: Double = 0.0,
        val integratedLoudnessLufs: Double? = null,
        val shortTermLoudnessLufs: Double? = null,
        val truePeakDbtp: Double? = null,
        val sections: List<StoredSection> = emptyList(),
        val structuralConfidence: Double = 0.0,
    ) {
        fun toAnalysis(trackId: String) = TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = duration,
            bpm = bpm,
            beatInterval = beatInterval,
            beatConfidence = beatConfidence,
            firstBeat = firstBeat,
            downbeats = downbeats,
            phraseBoundaries = phraseBoundaries,
            key = key,
            keyConfidence = keyConfidence,
            audibleStartTime = audibleStartTime,
            pickupTime = pickupTime,
            introEndTime = introEndTime,
            outroStartTime = outroStartTime,
            contentEndTime = contentEndTime,
            mixInTime = mixInTime,
            mixOutTime = mixOutTime,
            mixInCandidates = mixInCandidates.map { it.toCue() },
            mixOutCandidates = mixOutCandidates.map { it.toCue() },
            energyCurve = energyCurve.map { it.toSample() },
            lowEnergyCurve = lowEnergyCurve.map { it.toSample() },
            vocalActivityMask = vocalActivityMask,
            vocalProbability = vocalProbability,
            integratedLoudnessLufs = integratedLoudnessLufs,
            shortTermLoudnessLufs = shortTermLoudnessLufs,
            truePeakDbtp = truePeakDbtp,
            sections = sections.map { it.toSection() },
            structuralConfidence = structuralConfidence,
        )

        companion object {
            fun of(analysis: TrackAnalysis) = Stored(
                duration = analysis.duration,
                bpm = analysis.bpm,
                beatInterval = analysis.beatInterval,
                beatConfidence = analysis.beatConfidence,
                firstBeat = analysis.firstBeat,
                downbeats = analysis.downbeats.map(::round),
                phraseBoundaries = analysis.phraseBoundaries.map(::round),
                key = analysis.key,
                keyConfidence = analysis.keyConfidence,
                audibleStartTime = analysis.audibleStartTime,
                pickupTime = analysis.pickupTime,
                introEndTime = analysis.introEndTime,
                outroStartTime = analysis.outroStartTime,
                contentEndTime = analysis.contentEndTime,
                mixInTime = analysis.mixInTime,
                mixOutTime = analysis.mixOutTime,
                mixInCandidates = analysis.mixInCandidates.map(StoredCue::of),
                mixOutCandidates = analysis.mixOutCandidates.map(StoredCue::of),
                energyCurve = analysis.energyCurve.map(StoredEnergy::of),
                lowEnergyCurve = analysis.lowEnergyCurve.map(StoredEnergy::of),
                vocalActivityMask = analysis.vocalActivityMask.map(::round),
                vocalProbability = analysis.vocalProbability,
                integratedLoudnessLufs = analysis.integratedLoudnessLufs?.let(::round),
                shortTermLoudnessLufs = analysis.shortTermLoudnessLufs?.let(::round),
                truePeakDbtp = analysis.truePeakDbtp?.let(::round),
                sections = analysis.sections.map(StoredSection::of),
                structuralConfidence = round(analysis.structuralConfidence),
            )
        }
    }

    @Serializable
    private data class StoredCue(val time: Double, val score: Double, val type: String) {
        fun toCue() = MixCandidate(time = time, score = score, type = type)

        companion object {
            fun of(cue: MixCandidate) = StoredCue(round(cue.time), round(cue.score), cue.type)
        }
    }

    @Serializable
    private data class StoredEnergy(val time: Double, val energy: Double) {
        fun toSample() = EnergySample(time = time, energy = energy)

        companion object {
            fun of(sample: EnergySample) = StoredEnergy(round(sample.time), round(sample.energy))
        }
    }

    @Serializable
    private data class StoredSection(
        val start: Double,
        val end: Double,
        val type: String,
        val confidence: Double,
        val energy: Double,
        val vocalActivity: Double,
    ) {
        fun toSection() = MusicalSection(
            start = start,
            end = end,
            type = runCatching { MusicalSectionType.valueOf(type) }
                .getOrDefault(MusicalSectionType.UNKNOWN),
            confidence = confidence,
            energy = energy,
            vocalActivity = vocalActivity,
        )

        companion object {
            fun of(section: MusicalSection) = StoredSection(
                round(section.start),
                round(section.end),
                section.type.name,
                round(section.confidence),
                round(section.energy),
                round(section.vocalActivity),
            )
        }
    }

    private companion object {
        const val TAG = "BitChordAnalysisStore"
        const val DIRECTORY = "smart_analysis"

        /**
         * Bump whenever a stored number starts being computed differently.
         * Entries from an older schema are ignored rather than migrated: a
         * re-analysis costs seconds, and a beat grid interpreted under the wrong
         * assumptions is silently wrong for the life of the file.
         */
        const val SCHEMA_VERSION = 2

        /** A few thousand tracks' worth, at tens of kilobytes each. */
        const val MAX_ENTRIES = 2_000

        /** Milliseconds is finer than anything downstream distinguishes. */
        fun round(value: Double): Double =
            if (value.isFinite()) Math.round(value * 1000.0) / 1000.0 else 0.0
    }
}
