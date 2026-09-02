package com.music.bitchord

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.jiosaavn.RawSongItem
import com.music.bitchord.data.jiosaavn.prioritizeExplicit
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.ModuleSource
import com.music.bitchord.data.sources.MusicSource
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * The parts of the source layer that can be wrong quietly.
 *
 * The cross-source matcher gets most of the attention because it is the one
 * piece here whose failure isn't visible: a bad match doesn't crash or show an
 * error, it plays a different recording under the right title.
 */
class SourcesTest {

    private companion object {
        /** What the raced fakes below all claim to hold, so the matcher accepts them. */
        const val RACE_TITLE = "Paniyon Sa"
        const val RACE_ARTIST = "Atif Aslam"
    }

    // ---- Track identity -----------------------------------------------------

    @Test
    fun `track key round-trips`() {
        val key = SourceRegistry.trackKey("cfg-1", "track-42")
        assertEquals("cfg-1" to "track-42", SourceRegistry.parseTrackKey(key))
    }

    /** A module's own track ids are opaque and some issue ones containing colons. */
    @Test
    fun `track key survives separators inside the track id`() {
        val key = SourceRegistry.trackKey("cfg-1", "al::bum::7")
        assertEquals("cfg-1" to "al::bum::7", SourceRegistry.parseTrackKey(key))
    }

    /** A bare YouTube video id must not be mistaken for a source-backed one. */
    @Test
    fun `plain video ids are not source keys`() {
        assertNull(SourceRegistry.parseTrackKey("dQw4w9WgXcQ"))
        assertNull(SourceRegistry.parseTrackKey(""))
    }

    // ---- Format reporting ---------------------------------------------------

    @Test
    fun `lossless is decided by codec, not bitrate`() {
        assertEquals(true, StreamFormat(codec = "flac", kbps = 900).isLossless)
        assertEquals(true, StreamFormat(codec = "alac").isLossless)
        // A high sample rate does not rescue a lossy codec.
        assertEquals(false, StreamFormat(codec = "opus", sampleRateHz = 192_000).isLossless)
        // Unknown stays unknown rather than defaulting to "no".
        assertNull(StreamFormat().isLossless)
    }

    @Test
    fun `summary states depth and rate and drops bitrate when lossless`() {
        val hiRes = StreamFormat(codec = "flac", kbps = 4608, sampleRateHz = 192_000, bitDepth = 24)
        assertEquals("FLAC · 24-bit · 192 kHz", hiRes.summary)

        val lossy = StreamFormat(codec = "mp3", kbps = 320, sampleRateHz = 44_100)
        assertEquals("MP3 · 44.1 kHz · 320 kbps", lossy.summary)

        assertEquals("Unknown format", StreamFormat().summary)
    }

    @Test
    fun `Dolby Atmos is a distinct premium codec`() {
        val atmos = StreamFormat(codec = "eac3-joc")
        assertTrue(atmos.isDolbyAtmos)
        assertEquals(false, atmos.isLossless)
        assertEquals("Dolby Atmos", atmos.summary)
        assertTrue(SourceResolver.worthSwapping(atmos, StreamFormat(codec = "aac", kbps = 332)))
    }

    /**
     * The badge tiers. "Hi-Quality" exists to separate a module's 320kbps
     * stream from YouTube's 160kbps Opus, which the screen otherwise renders
     * identically — as nothing at all.
     */
    @Test
    fun `names a high-bitrate lossy stream without calling it lossless`() {
        val aac320 = NerdStats.Snapshot(mimeType = "audio/mp4a-latm", bitrateKbps = 320, sampleRateHz = 44_100, channels = 2)
        assertFalse(aac320.isLossless)
        assertTrue(aac320.isHiQuality)

        val opus160 = NerdStats.Snapshot(mimeType = "audio/opus", bitrateKbps = 160, sampleRateHz = 48_000, channels = 2)
        assertFalse(opus160.isHiQuality)

        // Lossless is its own badge and never doubles as this one, however
        // large the bitrate a FLAC reports.
        val flac = NerdStats.Snapshot(mimeType = "audio/flac", bitrateKbps = 1411, sampleRateHz = 44_100, channels = 2)
        assertTrue(flac.isLossless)
        assertFalse(flac.isHiQuality)
    }

    /** With no measured bitrate, what the source said it was sending will do. */
    @Test
    fun `falls back to the declared bitrate for the quality tier`() {
        val claimed = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = null,
            sampleRateHz = 44_100,
            channels = 2,
            claimed = StreamFormat(codec = "aac", kbps = 320),
        )
        assertTrue(claimed.isHiQuality)

        val silent = NerdStats.Snapshot(mimeType = "audio/mp4a-latm", bitrateKbps = null, sampleRateHz = null, channels = null)
        assertFalse(silent.isHiQuality)
    }

    // ---- Cross-source matching ---------------------------------------------

    private fun song(title: String, artist: String, duration: String? = null) =
        Song(videoId = "x", title = title, artist = artist, thumbnailUrl = null, durationText = duration)

    private fun matches(candidate: Song, title: String, artist: String, durationSec: Int? = null) =
        TrackMatcher.matches(candidate, title, artist, durationSec)

    @Test
    fun `matches the same recording across differing catalogue titles`() {
        assertTrue(
            matches(
                song("Bohemian Rhapsody (Remastered 2011)", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
        assertTrue(
            matches(
                song("Sunflower", "Post Malone, Swae Lee"),
                title = "Sunflower (feat. Swae Lee)",
                artist = "Post Malone",
            ),
        )
        // Punctuation and case are not identity.
        assertTrue(
            matches(
                song("Don't Stop Me Now", "QUEEN"),
                title = "Dont Stop Me Now",
                artist = "Queen",
            ),
        )
    }

    /**
     * The one that sent this back for a rewrite. YouTube files the track under
     * the film it is from and credits the lead singer; the module holds the
     * same audio under the bare title and credits the duet. Every part of that
     * disagreement is packaging.
     */
    @Test
    fun `matches a film credit against a bare catalogue listing`() {
        assertTrue(
            matches(
                song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:07"),
                title = "Paniyon Sa (From \"Satyameva Jayate\")",
                artist = "Atif Aslam",
                durationSec = 247,
            ),
        )
        // And the other way round, which is how a module-queued track finds
        // its YouTube seed for radio.
        assertTrue(
            matches(
                song("Paniyon Sa (From \"Satyameva Jayate\")", "Atif Aslam"),
                title = "Paniyon Sa",
                artist = "Atif Aslam, Tulsi Kumar",
            ),
        )
    }

    /** The trailing labels an upload hangs on a title with no brackets to hold them. */
    @Test
    fun `strips upload labelling from either side`() {
        assertTrue(matches(song("Tum Hi Ho", "Arijit Singh"), "Tum Hi Ho Full Song", "Arijit Singh"))
        assertTrue(
            matches(
                song("Kesariya", "Arijit Singh"),
                title = "Kesariya - Brahmastra | Official Video",
                artist = "Arijit Singh",
            ),
        )
        // "Artist - Title" uploads: the head is the credit, not the song.
        assertTrue(
            matches(
                song("Believer", "Imagine Dragons"),
                title = "Imagine Dragons - Believer",
                artist = "Imagine Dragons",
            ),
        )
    }

    @Test
    fun `refuses a different song by the same artist`() {
        assertFalse(
            matches(
                song("The Show Must Go On", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
    }

    /**
     * The dangerous case: same title, different artist. A cover, a tribute
     * album, or a completely unrelated song that happens to share a name — all
     * of which a loose matcher would happily play instead.
     */
    @Test
    fun `refuses a cover by a different artist`() {
        assertFalse(
            matches(
                song("Hurt", "Johnny Cash"),
                title = "Hurt",
                artist = "Nine Inch Nails",
            ),
        )
    }

    @Test
    fun `accepts a shared artist when catalogues credit differently`() {
        assertTrue(
            matches(
                song("Numb / Encore", "Jay-Z & Linkin Park"),
                title = "Numb / Encore",
                artist = "Linkin Park",
            ),
        )
    }

    /**
     * Film catalogues credit from opposite ends: YouTube Music files this
     * under the composer, every store under the singer, and the two credits
     * share nothing at all. An exact runtime is what says they are the same
     * master anyway.
     */
    @Test
    fun `accepts a composer credit against a singer credit on an exact runtime`() {
        assertTrue(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
        // The remix and the acoustic cover from the same result set are the
        // reason this is safe: neither agrees on length.
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Leo Lz Mix", duration = "4:01"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
    }

    /**
     * The runtime may only stand in for a credit when there *is* a runtime.
     * Without one there is nothing corroborating anything, and an unrelated
     * song sharing a title must still lose.
     */
    @Test
    fun `will not waive the credit check without a runtime to back it`() {
        assertFalse(matches(song("Jhak Maar Ke", "Neeraj Shridhar"), "Jhak Maar Ke", "Pritam"))
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = null,
            ),
        )
    }

    /** A properly credited copy always outranks one the runtime merely vouched for. */
    @Test
    fun `prefers a credited match over a runtime-vouched one`() {
        val target = TrackMatcher.Target("Jhak Maar Ke", "Pritam, Neeraj Shridhar", durationSec = 233)
        val vouched = song("Jhak Maar Ke", "Some Uploader", duration = "3:53")
        val credited = song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53")
        assertEquals(credited, TrackMatcher.best(listOf(vouched, credited), target))
    }

    /** A name inside another name is not a shared credit. */
    @Test
    fun `refuses an artist whose name merely contains the one asked for`() {
        assertFalse(matches(song("No One Knows", "Queens of the Stone Age"), "No One Knows", "Queen"))
    }

    /**
     * A different take is a different recording, and the direction it is asked
     * for in doesn't change that.
     */
    @Test
    fun `refuses a different take of the same song`() {
        assertFalse(matches(song("Shape of You (Acoustic)", "Ed Sheeran"), "Shape of You", "Ed Sheeran"))
        assertFalse(matches(song("Shape of You", "Ed Sheeran"), "Shape of You (Acoustic)", "Ed Sheeran"))
        assertFalse(matches(song("Creep (Live)", "Radiohead"), "Creep", "Radiohead"))
        assertFalse(matches(song("Faded", "Alan Walker"), "Faded (Slowed + Reverb)", "Alan Walker"))
        // A stem carries the right title and the right artist and is not the
        // song — this one was one candidate away from playing.
        assertFalse(
            matches(
                song("Apna Bana Le - Arijit Singh Vocals Only", "Arijit Singh, Sachin-Jigar"),
                title = "Apna Bana Le (From \"Bhediya\")",
                artist = "Arijit Singh",
            ),
        )
        assertFalse(matches(song("Kesariya (Instrumental)", "Arijit Singh"), "Kesariya", "Arijit Singh"))
        // Both sides saying the same thing is still a match.
        assertTrue(matches(song("Creep (Live)", "Radiohead"), "Creep [Live]", "Radiohead"))
    }

    /** Version-shaped words that describe the ordinary release, not a new take. */
    @Test
    fun `treats an album or radio version as the plain track`() {
        assertTrue(matches(song("Africa", "Toto"), "Africa (Album Version)", "Toto"))
        assertTrue(matches(song("Clocks", "Coldplay"), "Clocks (Radio Edit)", "Coldplay"))
    }

    /** The signal a title can't give: a loop, a snippet, or a whole album side. */
    @Test
    fun `refuses a candidate whose runtime is nowhere near`() {
        assertFalse(
            matches(
                song("Levitating", "Dua Lipa", duration = "1:00:12"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
        // A few seconds of trimmed silence is not a different recording.
        assertTrue(
            matches(
                song("Levitating", "Dua Lipa", duration = "3:25"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
    }

    /** With no artist to check against, the title alone has to carry it. */
    @Test
    fun `falls back to title alone when no artist is known`() {
        assertTrue(matches(song("Clair de Lune", "Debussy"), "Clair de Lune", ""))
        assertFalse(matches(song("Reverie", "Debussy"), "Clair de Lune", ""))
    }

    // ---- Choosing between candidates ---------------------------------------

    /**
     * Search backends rank however they like. The right copy is the one whose
     * runtime and credit agree, not the one that came back first.
     */
    @Test
    fun `picks the closest candidate rather than the first acceptable one`() {
        val target = TrackMatcher.Target("Paniyon Sa", "Atif Aslam", durationSec = 247)
        val wrongLength = song("Paniyon Sa", "Atif Aslam", duration = "4:32")
        val right = song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:06")
        assertEquals(right, TrackMatcher.best(listOf(wrongLength, right), target))
    }

    /**
     * JioSaavn currently lists these as the same title and artist even though
     * they are different recordings. The requested release must beat the row
     * whose runtime happens to be closer.
     */
    @Test
    fun `uses the album to separate duplicate JioSaavn recordings`() {
        val target = TrackMatcher.Target(
            "Brown Rang",
            "Yo Yo Honey Singh",
            durationSec = 175,
            album = "International Villager",
        )
        val wanted = song("Brown Rang", "Yo Yo Honey Singh", duration = "2:59")
            .copy(albumName = "International Villager")
        val wrongButCloser = song("Brown Rang", "Yo Yo Honey Singh", duration = "2:54")
            .copy(albumName = "Chaar Ikke")

        assertEquals(wanted, TrackMatcher.best(listOf(wrongButCloser, wanted), target))
    }

    @Test
    fun `detects conflicting releases when the requested album is unknown`() {
        val target = TrackMatcher.Target("Brown Rang", "Yo Yo Honey Singh", durationSec = 175)
        val internationalVillager = song("Brown Rang", "Yo Yo Honey Singh", "2:59")
            .copy(albumName = "International Villager")
        val chaarIkke = song("Brown Rang", "Yo Yo Honey Singh", "2:54")
            .copy(albumName = "Chaar Ikke")

        assertTrue(
            TrackMatcher.hasConflictingAlbums(
                listOf(internationalVillager, chaarIkke),
                target,
            ),
        )
        assertFalse(
            TrackMatcher.hasConflictingAlbums(
                listOf(
                    internationalVillager,
                    internationalVillager.copy(albumName = "International Villager (Deluxe Edition)"),
                ),
                target,
            ),
        )
        assertFalse(
            TrackMatcher.hasConflictingAlbums(
                listOf(internationalVillager, chaarIkke),
                target.copy(album = "International Villager"),
            ),
        )
    }

    @Test
    fun `uses the uniquely fullest credit to resolve a JioSaavn release collision`() {
        val target = TrackMatcher.Target("Ek Dil Ek Jaan", "Shivam Pathak", durationSec = 220)
        val original = song(
            "Ek Dil Ek Jaan",
            "Shivam Pathak, Mujtaba Aziz Naza, Kunal Pandit, Farhan Sabri",
            "3:40",
        ).copy(albumName = "Padmaavat")
        val compilation = song("Ek Dil Ek Jaan", "Shivam Pathak", "3:39")
            .copy(albumName = "Top 20 - Romantic Songs 2018")
        val fromCompilation = song(
            "Ek Dil Ek Jaan (From Padmaavat)",
            "Shivam Pathak, Sanjay Leela Bhansali, A.M. Turaz",
            "3:39",
        ).copy(albumName = "Bollywood Magic Mix")

        assertEquals(
            original,
            TrackMatcher.uniquelyMostCreditedCloseMatch(
                listOf(original, compilation, fromCompilation),
                target,
            ),
        )
    }

    @Test
    fun `retains JioSaavn refusal when conflicting releases tie on credit coverage`() {
        val target = TrackMatcher.Target("Mere Bina", "Pritam, Nikhil D'Souza", durationSec = 290)
        val first = song("Mere Bina", "Pritam, Nikhil D'Souza", "4:49").copy(albumName = "Crook")
        val second = song("Mere Bina", "Pritam, Nikhil D'Souza", "4:51").copy(albumName = "Sad Love Hits")

        assertNull(TrackMatcher.uniquelyMostCreditedCloseMatch(listOf(first, second), target))
    }

    @Test
    fun `target keeps the queued album identity`() {
        val queued = song("Brown Rang", "Yo Yo Honey Singh", "2:59")
            .copy(albumName = "International Villager")
        assertEquals("International Villager", TrackMatcher.targetOf(queued).album)
    }

    @Test
    fun `JioSaavn prioritizes the uncensored duplicate`() {
        val clean = RawSongItem(id = "clean", title = "Starboy", explicitContent = "0")
        val explicit = RawSongItem(id = "explicit", title = "Starboy", explicitContent = "1")
        val anotherClean = RawSongItem(id = "clean-2", title = "Starboy", explicitContent = "0")

        assertEquals(
            listOf("explicit", "clean", "clean-2"),
            prioritizeExplicit(listOf(clean, explicit, anotherClean)).map { it.id },
        )
    }

    @Test
    fun `JioSaavn accepts textual explicit flags defensively`() {
        assertTrue(RawSongItem(explicitContent = "true").isExplicit)
        assertFalse(RawSongItem(explicitContent = "false").isExplicit)
        assertFalse(RawSongItem(explicitContent = "").isExplicit)
    }

    @Test
    fun `explicit YouTube track cannot match the censored JioSaavn edition`() {
        val target = TrackMatcher.Target(
            "Starboy",
            "The Weeknd",
            durationSec = 230,
            album = "Starboy",
            isExplicit = true,
        )
        val clean = song("Starboy", "The Weeknd", "3:50")
            .copy(albumName = "Starboy", isExplicit = false)
        val uncensored = clean.copy(videoId = "uncensored", isExplicit = true)

        assertEquals(uncensored, TrackMatcher.best(listOf(clean, uncensored), target))
        assertNull(TrackMatcher.score(clean, target))
    }

    @Test
    fun `unknown YouTube explicit state does not reject the credited JioSaavn track`() {
        val target = TrackMatcher.Target(
            "For A Reason",
            "Karan Aujla, IKKY",
            durationSec = 180,
            isExplicit = null,
        )
        val wanted = song(
            "For A Reason",
            "Karan Aujla, IKKY, Ikwinder Sahota, Milan D'Agostini",
            "3:00",
        ).copy(videoId = "vLSaC03b", albumName = "P-POP CULTURE", isExplicit = true)
        val remix = song("For A Reason", "Aye Manny", "3:00")
            .copy(videoId = "sN24LH3L", albumName = "For A Reason (Remix)", isExplicit = false)

        assertEquals(wanted, TrackMatcher.best(listOf(remix, wanted), target))
        assertEquals(listOf(wanted), TrackMatcher.ranked(listOf(remix, wanted), target))
    }

    @Test
    fun `missing YouTube badge does not reject explicit I Really Do`() {
        val target = TrackMatcher.Target(
            "I Really Do...",
            "Karan Aujla",
            durationSec = 193,
            isExplicit = null,
        )
        val wanted = song(
            "I Really Do...",
            "Karan Aujla, IKKY, Ikwinder Sahota, Jamal Europe",
            "3:13",
        ).copy(videoId = "1vja2Ptl", albumName = "P-POP CULTURE", isExplicit = true)
        val otherSong = song("I Really Do...", "Arjun Viraat, Shefali Alvares", "3:02")
            .copy(videoId = "WeCT149y", albumName = "I Really Do...", isExplicit = false)

        assertEquals(wanted, TrackMatcher.best(listOf(otherSong, wanted), target))
    }

    @Test
    fun `unlabelled music video timing cannot make another artist the Brown Rang match`() {
        val target = TrackMatcher.Target(
            "Brown Rang",
            "Yo Yo Honey Singh",
            durationSec = 211,
            // This is the phone's real failure: YouTube presented the official
            // video as a song row, so the explicit video flag was false.
            isVideo = false,
        )
        val wantedAudio = song("Brown Rang", "Yo Yo Honey Singh", "2:59")
            .copy(videoId = "vj2tW1iy", albumName = "International Villager")
        val wrongExactRuntime = song("Brown Rang", "Lovely, Jais Rikhi, Love Sagar", "3:30")
            .copy(videoId = "UgOpg53G", albumName = "Brown Rang")

        assertEquals(wantedAudio, TrackMatcher.best(listOf(wrongExactRuntime, wantedAudio), target))
        assertEquals(
            listOf(wantedAudio),
            TrackMatcher.ranked(listOf(wrongExactRuntime, wantedAudio), target),
        )
        assertFalse(TrackMatcher.hasConflictingAlbums(listOf(wantedAudio), target))
    }

    @Test
    fun `manual video audio switch accepts the official song despite a long visual intro`() {
        val target = TrackMatcher.Target(
            "Big Dawgs",
            "Hanumankind, Kalmi",
            // A visual short film can be much longer than its song release.
            durationSec = 391,
            isVideo = true,
        )
        val officialAudio = song("Big Dawgs", "Hanumankind, Kalmi", "3:11")
            .copy(videoId = "official-audio")
        val sameTitleCover = song("Big Dawgs", "Unrelated Cover Artist", "6:31")
            .copy(videoId = "cover")

        // The ordinary source matcher is right to refuse a multi-minute gap;
        // the explicit video-to-audio action is allowed to use YTM's official
        // song row after title, version and artist all agree.
        assertNull(TrackMatcher.best(listOf(officialAudio, sameTitleCover), target))
        assertEquals(
            officialAudio,
            TrackMatcher.bestOfficialAudioForVideo(listOf(sameTitleCover, officialAudio), target),
        )
    }

    /**
     * A declared tier is a reason to prefer one copy of a recording over
     * another. It is not a reason to play a different recording — the DJ edit
     * on a compilation carries the right title and the right artist, and only
     * its runtime gives it away.
     */
    @Test
    fun `refuses to let a lossless label outrank the right runtime`() {
        val target = TrackMatcher.Target("Sakhiyaan", "Maninder Buttar", durationSec = 180)
        val djEdit = song("Sakhiyaan", "Maninder Buttar", duration = "3:05")
            .copy(albumName = "Punjabi Dj Holi songs", sourceQuality = "LOSSLESS")
        val albumCut = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
            .copy(albumName = "Sakhiyaan")
        // Both are acceptable matches on title and artist alone...
        assertTrue(TrackMatcher.matches(djEdit, target.title, target.artist))
        // ...and the runtime is the only thing that separates them.
        assertEquals(albumCut, TrackMatcher.best(listOf(djEdit, albumCut), target))
        // Including when lossless is being asked for and only the wrong cut
        // claims to have it, which is the case that actually shipped broken.
        assertEquals(
            listOf(albumCut),
            SourceResolver.preferred(listOf(djEdit, albumCut), target, wantsLossless = true),
        )
    }

    /** With no runtime to separate them, the declared tier is the tiebreak again. */
    @Test
    fun `prefers the lossless copy when nothing separates the recordings`() {
        val target = TrackMatcher.Target("Sakhiyaan", "Maninder Buttar", durationSec = 180)
        val plain = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
        val lossless = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
            .copy(sourceQuality = "LOSSLESS")
        assertEquals(
            listOf(lossless, plain),
            SourceResolver.preferred(listOf(plain, lossless), target, wantsLossless = true),
        )
    }

    // ---- Deciding whether an upgrade is worth the seam -----------------------

    /**
     * Lossless is always worth it — it is what was asked for, and the reason
     * the second look happens at all.
     */
    @Test
    fun `always swaps to lossless`() {
        val youtube = StreamFormat(codec = "opus", kbps = 160)
        assertTrue(SourceResolver.worthSwapping(StreamFormat(codec = "flac"), youtube))
        // Even against a lossy stream that is nominally the higher bitrate.
        assertTrue(
            SourceResolver.worthSwapping(StreamFormat(codec = "flac"), StreamFormat(codec = "aac", kbps = 320)),
        )
    }

    /**
     * The Shaayraana case: no catalogue had a lossless copy, one had a 320kbps
     * AAC, and the track played on YouTube's 160kbps Opus because the only
     * question being asked was "is this lossless".
     */
    @Test
    fun `swaps to a lossy stream that is clearly better than what is playing`() {
        val youtube = StreamFormat(codec = "opus", kbps = 160)
        assertTrue(SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), youtube))
    }

    /** A margin too narrow to hear does not earn a break in the audio. */
    @Test
    fun `refuses a lossy swap that gains little`() {
        assertFalse(
            SourceResolver.worthSwapping(
                StreamFormat(codec = "mp3", kbps = 192),
                StreamFormat(codec = "aac", kbps = 128),
            ),
        )
        // And never a downgrade, however the codecs compare.
        assertFalse(
            SourceResolver.worthSwapping(
                StreamFormat(codec = "mp3", kbps = 128),
                StreamFormat(codec = "opus", kbps = 160),
            ),
        )
    }

    /**
     * An unstated bitrate on either side is not evidence of an improvement.
     * Swapping on one would be gambling the listener's audio on a guess.
     */
    @Test
    fun `refuses a lossy swap it cannot measure`() {
        val playing = StreamFormat(codec = "opus", kbps = 160)
        assertFalse(SourceResolver.worthSwapping(StreamFormat(codec = "aac"), playing))
        assertFalse(SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), null))
        assertFalse(
            SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), StreamFormat(codec = "opus")),
        )
    }

    // ---- Ranking two copies of the same recording ---------------------------

    /**
     * The '9:45' case, reduced to the comparison at the heart of it: a module
     * ranked above JioSaavn offered 128kbps and JioSaavn held 320kbps, and the
     * walk has to be able to say which of those it would rather have.
     *
     * Note this is a different question from [SourceResolver.worthSwapping] —
     * that one asks whether a difference earns a break in the audio, this one
     * only asks which is better.
     */
    @Test
    fun `ranks a higher-bitrate lossy stream above a lower one`() {
        assertTrue(
            SourceResolver.isBetter(
                StreamFormat(codec = "mp4", kbps = 320),
                StreamFormat(codec = "mp3", kbps = 128),
            ),
        )
        assertFalse(
            SourceResolver.isBetter(
                StreamFormat(codec = "mp3", kbps = 128),
                StreamFormat(codec = "mp4", kbps = 320),
            ),
        )
    }

    /** Codec decides before bitrate: no lossy rendition outranks a lossless one. */
    @Test
    fun `ranks lossless above any lossy bitrate`() {
        assertTrue(
            SourceResolver.isBetter(
                StreamFormat(codec = "flac"),
                StreamFormat(codec = "mp4", kbps = 320),
            ),
        )
        assertFalse(
            SourceResolver.isBetter(
                StreamFormat(codec = "mp4", kbps = 320),
                StreamFormat(codec = "flac"),
            ),
        )
    }

    /**
     * Nothing to compare against is beaten by anything — this is what makes the
     * first source's answer the floor rather than a special case.
     */
    @Test
    fun `anything beats no stream at all`() {
        assertTrue(SourceResolver.isBetter(StreamFormat(codec = "mp3", kbps = 128), null))
    }

    /**
     * An equal stream does not displace the one already held. The walk asks
     * sources in rank order, so a tie has to leave the higher-ranked source's
     * copy in place rather than drifting to whoever answered last.
     */
    @Test
    fun `an equal stream does not displace the one already held`() {
        assertFalse(
            SourceResolver.isBetter(
                StreamFormat(codec = "mp4", kbps = 320),
                StreamFormat(codec = "aac", kbps = 320),
            ),
        )
        assertFalse(
            SourceResolver.isBetter(StreamFormat(codec = "flac"), StreamFormat(codec = "alac")),
        )
    }

    /**
     * An unstated bitrate ranks as nothing rather than as a win. A source that
     * declines to describe its stream should not be able to displace one that
     * has stated a good rate — see [StreamFormat] on why null means "not
     * stated" and is never inferred.
     */
    @Test
    fun `an undescribed lossy stream does not outrank a stated one`() {
        assertFalse(
            SourceResolver.isBetter(StreamFormat(codec = "aac"), StreamFormat(codec = "mp4", kbps = 320)),
        )
        assertTrue(
            SourceResolver.isBetter(StreamFormat(codec = "mp4", kbps = 320), StreamFormat(codec = "aac")),
        )
    }

    @Test
    fun `has nothing to offer when no candidate is the recording`() {
        val target = TrackMatcher.Target("Paniyon Sa", "Atif Aslam")
        assertNull(TrackMatcher.best(listOf(song("Paniyon Sa", "Some Cover Band")), target))
        assertNull(TrackMatcher.best(emptyList(), target))
    }

    // ---- What a lossy source has to beat to become a file -------------------

    /**
     * The case the floor exists for: JioSaavn's top rendition is better than
     * anything YouTube's AAC ladder holds, so a download takes it rather than
     * filing a copy worse than the one that would have been streamed.
     */
    @Test
    fun `a 320 from a source is worth keeping over youtube's aac`() {
        assertTrue(SourceResolver.beatsYouTubeAac(StreamFormat(codec = "mp4", kbps = 320)))
    }

    /**
     * Below the top of YouTube's own ladder, a source's copy is trading one
     * lossy file for another and giving up the more reliable fetch to do it —
     * including at 256, where the two are a wash and the tie goes to YouTube.
     */
    @Test
    fun `a thinner rendition loses to youtube's aac`() {
        assertFalse(SourceResolver.beatsYouTubeAac(StreamFormat(codec = "mp3", kbps = 128)))
        assertFalse(SourceResolver.beatsYouTubeAac(StreamFormat(codec = "mp4", kbps = 160)))
        assertFalse(SourceResolver.beatsYouTubeAac(StreamFormat(codec = "aac", kbps = 256)))
    }

    /**
     * A download has to name the file before the first byte lands, so a source
     * that described its rendition as nothing has said nothing worth keeping —
     * unlike playback, which can hand the URL to the decoder and find out.
     */
    @Test
    fun `an unstated rendition is not worth keeping`() {
        assertFalse(SourceResolver.beatsYouTubeAac(StreamFormat()))
        assertFalse(SourceResolver.beatsYouTubeAac(StreamFormat(codec = "mp4")))
    }

    // ---- Which sources are worth asking before a track is played ------------

    /**
     * Read-ahead resolves the track *after* the one playing, so a source that
     * needs ten seconds to answer is usually still answering when the listener
     * arrives — and a wasted module resolve costs a QuickJS engine and several
     * backend searches, where a wasted JioSaavn one costs a round trip. Only
     * JioSaavn earns the speculative ask.
     */
    @Test
    fun `only the quick source is worth resolving ahead of playback`() {
        assertTrue(SourceKind.JIOSAAVN.worthPrefetching)
        assertFalse(SourceKind.MODULE.worthPrefetching)
        assertFalse(SourceKind.CUSTOM_MODULE.worthPrefetching)
    }

    /**
     * YouTube is warmed ahead of time too, but through its own read-ahead,
     * which speaks video ids and needs no cross-source match. Marking it here
     * would send it through the substitution path to find itself.
     */
    @Test
    fun `youtube is not prefetched as a substitute for itself`() {
        assertFalse(SourceKind.YOUTUBE.worthPrefetching)
    }

    // ---- Racing the sources -------------------------------------------------

    /**
     * A source that answers after [answerAfterMs] with a stream of [format], or
     * with nothing at all when [format] is null.
     *
     * [asked] records that it was reached, which is how the tests below tell a
     * source that was beaten from one that was never asked — the distinction
     * the whole '9:45' investigation turned on.
     */
    private class FakeSource(
        override val displayName: String,
        private val answerAfterMs: Long,
        private val format: StreamFormat?,
        override val kind: SourceKind = SourceKind.JIOSAAVN,
        private val candidates: List<Song>? = null,
    ) : MusicSource {
        override val configId = displayName
        var asked = false
            private set
        var cancelled = false
            private set

        override suspend fun health() = SourceHealth.Ok()

        override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> {
            asked = true
            try {
                delay(answerAfterMs)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
            if (format == null) return emptyList()
            return candidates ?: listOf(
                Song(
                    videoId = "$displayName-1",
                    title = RACE_TITLE,
                    artist = RACE_ARTIST,
                    thumbnailUrl = null,
                    durationText = "2:00",
                ),
            )
        }

        override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? =
            format?.let { SourceStream(url = "https://$displayName/$trackId", format = it) }
    }

    private fun raceTarget() = TrackMatcher.Target(RACE_TITLE, RACE_ARTIST, durationSec = 120)

    @Test
    fun `JioSaavn substitution refuses conflicting albums without a target album`() = runBlocking {
        val source = FakeSource(
            displayName = "JioSaavn",
            answerAfterMs = 0,
            format = StreamFormat("mp4", kbps = 320),
            candidates = listOf(
                song("Brown Rang", "Yo Yo Honey Singh", "2:59")
                    .copy(videoId = "international-villager", albumName = "International Villager"),
                song("Brown Rang", "Yo Yo Honey Singh", "2:54")
                    .copy(videoId = "chaar-ikke", albumName = "Chaar Ikke"),
            ),
        )
        val target = TrackMatcher.Target("Brown Rang", "Yo Yo Honey Singh", durationSec = 175)

        assertNull(SourceResolver.bestAcross(listOf(source), target, StreamRequest.Best))
    }

    /**
     * The '9:45' case itself, as a race rather than a queue.
     *
     * JioSaavn answered in ~0.4s and the module in ~13.5s. Walked in rank order
     * the module's slowness was JioSaavn's too, so the whole lookup lost the
     * race against YouTube and the listener got 160kbps Opus. Raced, the quick
     * answer is the one that starts the track — and the slow source is left to
     * [SourceResolver.upgradeFor], which judges it against what is playing.
     */
    @Test
    fun `takes the quick answer rather than waiting for a slow better one`() = runBlocking {
        val slow = FakeSource("Ricky's Addon", answerAfterMs = 2_000, format = StreamFormat("flac"))
        val quick = FakeSource("JioSaavn", answerAfterMs = 5, format = StreamFormat("mp4", kbps = 320))
        val elapsed = measureTimeMillis {
            val (source, stream) = SourceResolver.bestAcross(
                listOf(slow, quick), raceTarget(), StreamRequest.Lossless,
            )!!
            assertEquals("JioSaavn", source.displayName)
            assertEquals(320, stream.format.kbps)
        }
        // Nowhere near the slow source's two seconds.
        assertTrue("took ${elapsed}ms, so it waited for the slow source", elapsed < 1_000)
        // Asked, though — being beaten is not the same as being skipped, and
        // skipping is the bug this replaced.
        assertTrue(slow.asked)
    }

    /**
     * A slow source is cancelled once an answer is in hand rather than left
     * running: nothing is waiting on it, and the second look re-asks it
     * properly. Left running it would spend a listener's radio for nothing.
     */
    @Test
    fun `abandons the sources still running once it has an answer`() = runBlocking {
        val slow = FakeSource("Ricky's Addon", answerAfterMs = 2_000, format = StreamFormat("flac"))
        val quick = FakeSource("JioSaavn", answerAfterMs = 5, format = StreamFormat("mp4", kbps = 320))
        SourceResolver.bestAcross(listOf(slow, quick), raceTarget(), StreamRequest.Lossless)
        assertTrue("the slow source was left running", slow.cancelled)
    }

    /**
     * Answers that arrive together are still ranked. Racing gives up ordering
     * between a fast source and a slow one; it does not give up ordering
     * between two that answered at the same moment.
     */
    @Test
    fun `prefers the better of two answers that arrive together`() = runBlocking {
        val worse = FakeSource("Ricky's Addon", answerAfterMs = 5, format = StreamFormat("mp3", kbps = 128))
        val better = FakeSource("JioSaavn", answerAfterMs = 5, format = StreamFormat("mp4", kbps = 320))
        val (source, stream) = SourceResolver.bestAcross(
            listOf(worse, better), raceTarget(), StreamRequest.Lossless,
        )!!
        assertEquals("JioSaavn", source.displayName)
        assertEquals(320, stream.format.kbps)
    }

    /**
     * A source that simply doesn't have the track must not end the race. This
     * is the difference between "nobody has it" and "the first one to answer
     * didn't have it", and returning null on the latter is how a catalogue that
     * held the track went unheard.
     */
    @Test
    fun `keeps waiting when the first source to answer has nothing`() = runBlocking {
        val empty = FakeSource("Ricky's Addon", answerAfterMs = 5, format = null)
        val holder = FakeSource("JioSaavn", answerAfterMs = 200, format = StreamFormat("mp4", kbps = 320))
        val (source, _) = SourceResolver.bestAcross(
            listOf(empty, holder), raceTarget(), StreamRequest.Lossless,
        )!!
        assertEquals("JioSaavn", source.displayName)
    }

    /** Nobody has it: the race ends when the last source has said so. */
    @Test
    fun `has nothing when no source holds the track`() = runBlocking {
        val a = FakeSource("Ricky's Addon", answerAfterMs = 5, format = null)
        val b = FakeSource("JioSaavn", answerAfterMs = 10, format = null)
        assertNull(SourceResolver.bestAcross(listOf(a, b), raceTarget(), StreamRequest.Lossless))
    }

    /**
     * The background upgrade is deliberately patient. Its source searches have
     * already been given their full budget, so it must not cancel a slower FLAC
     * merely because JioSaavn's valid 320kbps answer arrived first.
     */
    @Test
    fun `patient upgrade chooses the better later answer`() = runBlocking {
        val playing = StreamFormat(codec = "opus", kbps = 141)
        val slowLossless = FakeSource("Ricky's Addon", answerAfterMs = 200, format = StreamFormat("flac"))
        val quickLossy = FakeSource("JioSaavn", answerAfterMs = 5, format = StreamFormat("mp4", kbps = 320))
        val (source, stream) = SourceResolver.bestAcross(
            listOf(slowLossless, quickLossy),
            raceTarget(),
            StreamRequest.Lossless,
            waitForAll = true,
            strictLength = true,
        ) { _, candidate -> SourceResolver.worthSwapping(candidate.format, playing) }!!
        assertEquals("Ricky's Addon", source.displayName)
        assertEquals("flac", stream.format.codec)
        assertFalse("the patient lookup cancelled the later source", slowLossless.cancelled)
    }

    @Test
    fun `upgrade refuses same title and runtime from a different artist`() = runBlocking {
        val wrongMexico = FakeSource(
            displayName = "Ricky's Addon",
            answerAfterMs = 0,
            format = StreamFormat("flac"),
            candidates = listOf(song("Mexico", "CAKE", "3:26")),
        )
        val target = TrackMatcher.Target("Mexico", "Karan Aujla", durationSec = 207)

        assertNull(
            SourceResolver.bestAcross(
                listOf(wrongMexico),
                target,
                StreamRequest.Lossless,
                waitForAll = true,
                strictLength = true,
                requireSharedArtist = true,
            ),
        )
    }

    /**
     * A refused answer is not an answer. When the only quick source is one whose
     * stream fails the predicate, the race has to keep running rather than
     * report that nothing was found.
     */
    @Test
    fun `keeps waiting when the quick answer is refused`() = runBlocking {
        val playing = StreamFormat(codec = "opus", kbps = 141)
        val quickRefused = FakeSource("Ricky's Addon", answerAfterMs = 5, format = StreamFormat("mp3", kbps = 128))
        val slowTaken = FakeSource("JioSaavn", answerAfterMs = 200, format = StreamFormat("mp4", kbps = 320))
        val (source, _) = SourceResolver.bestAcross(
            listOf(quickRefused, slowTaken),
            raceTarget(),
            StreamRequest.Lossless,
        ) { _, candidate -> SourceResolver.worthSwapping(candidate.format, playing) }!!
        assertEquals("JioSaavn", source.displayName)
    }

    // ---- Asking ------------------------------------------------------------

    /**
     * What a source is asked for. The raw title is never one of the queries:
     * a catalogue that lists "Paniyon Sa" has never stored the film name
     * YouTube prints alongside it, and scoring against words it doesn't hold
     * is how a source that had the track answered as if it didn't.
     */
    @Test
    fun `asks for the title a catalogue would file the track under`() {
        val queries = TrackMatcher.queries(
            TrackMatcher.Target("Paniyon Sa (From \"Satyameva Jayate\") | Official Video", "Atif Aslam, Tulsi Kumar"),
        )
        assertEquals(listOf("paniyon sa atif aslam", "paniyon sa"), queries)
    }

    /** A version marker is part of what to search for, not packaging to drop. */
    @Test
    fun `keeps the version marker in the query`() {
        assertEquals(
            "shape of you acoustic",
            TrackMatcher.queries(TrackMatcher.Target("Shape of You (Acoustic)", "")).single(),
        )
    }

    @Test
    fun `has nothing to ask for without a title`() {
        assertTrue(TrackMatcher.queries(TrackMatcher.Target("", "Atif Aslam")).isEmpty())
    }

    // ---- The mid-track swap guard ------------------------------------------

    /**
     * The check standing between a listener and having their audio cut for a
     * different recording. Stricter than ordinary matching on purpose, and it
     * refuses anything it cannot actually check.
     */
    @Test
    fun `only swaps in a copy of demonstrably the same length`() {
        val playing = TrackMatcher.Target("Jo Tere Sang", "Jeet Gannguli", durationSec = 306)
        assertTrue(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:06"), playing, 2))
        assertTrue(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:04"), playing, 2))
        assertFalse(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:12"), playing, 2))
        // A candidate that never said how long it is cannot be checked, and an
        // unverifiable swap is not worth making.
        assertFalse(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x"), playing, 2))
        // Neither is one where nothing is playing to compare against.
        assertFalse(
            TrackMatcher.withinSeconds(
                song("Jo Tere Sang", "x", "5:06"),
                TrackMatcher.Target("Jo Tere Sang", "Jeet Gannguli"),
                2,
            ),
        )
    }

    // ---- Stream URLs a module should not be trusted with --------------------

    /**
     * The August 2026 Tidal fault: the module pasted its own origin into the
     * path of the URL it was building, and the server 404'd every one. Rejected
     * on sight so the resolver walks on to the next source instead of spending
     * a playback attempt discovering it.
     */
    @Test
    fun `rejects a stream URL carrying a second copy of its own origin`() {
        val blob = "eyJhbGciOiJIUzI1NiJ9"
        assertTrue(
            ModuleSource.malformed(
                "https://sp-ad-fa.audio.tidal.com/mediatracks/$blob/" +
                    "https://sp-ad-fa.audio.tidal.com/mediatracks/$blob/0.mp4?token=1756000000~c2ln",
            ),
        )
        // The URL the module meant to send, which must still be played.
        assertFalse(
            ModuleSource.malformed(
                "https://sp-ad-fa.audio.tidal.com/mediatracks/$blob/0.mp4?token=1756000000~c2ln",
            ),
        )
    }

    /**
     * Two schemes in a URL is not the fault — handing a proxy its target is a
     * legitimate thing for a module to do, in the query or in the path, and
     * refusing those would take working catalogues offline.
     */
    @Test
    fun `accepts a URL that passes another URL along to a proxy`() {
        assertFalse(ModuleSource.malformed("https://cdn.example.com/get?url=https://real.host/f.flac"))
        assertFalse(ModuleSource.malformed("https://cdn.example.com/https://real.host/f.flac"))
    }

    /**
     * The Xiaomi report, which failed a step earlier than the doubled URL: the
     * player threw `HttpDataSourceException: Malformed URL` out of OkHttp's
     * parser without making a request. Anything that parser refuses has to be
     * refused here too, or it becomes an unplayable track.
     */
    @Test
    fun `rejects a stream URL the player's own parser would refuse`() {
        assertTrue(ModuleSource.malformed("/mediatracks/blob/0.mp4"))
        assertTrue(ModuleSource.malformed("sp-ad-fa.audio.tidal.com/mediatracks/blob/0.mp4"))
        assertTrue(ModuleSource.malformed("bitchord://watch?v=rpemDBaFK0c"))
        assertTrue(ModuleSource.malformed(""))
        // A module returning its error text, or nothing, in the URL field.
        assertTrue(ModuleSource.malformed("undefined"))
        assertTrue(ModuleSource.malformed("null"))
    }

    /**
     * A module gets to name a server, not a file on this device. Anything but
     * http(s) is refused, so a module cannot have the player read local storage
     * on its behalf.
     */
    @Test
    fun `refuses to let a module point the player at anything but http`() {
        assertTrue(ModuleSource.malformed("file:///data/data/com.music.bitchord/files/x.flac"))
        assertTrue(ModuleSource.malformed("content://media/external/audio/media/42"))
        assertTrue(ModuleSource.malformed("ftp://cdn.example.com/f.mp3"))
    }

    @Test
    fun `accepts an origin with no path of its own`() {
        // Nothing duplicated and the parser is happy; whether a server answers
        // it is for the server to say.
        assertFalse(ModuleSource.malformed("https://sp-ad-fa.audio.tidal.com"))
    }

    // ---- Quality tiers -----------------------------------------------------

    /**
     * Every module spells its quality differently, and the spelling is all
     * there is to go on when choosing which catalogue to open a track from.
     */
    @Test
    fun `reads a tier out of whatever a module calls it`() {
        assertEquals("LOSSLESS", ModuleSource.qualityTier("LOSSLESS"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("FLAC 16-bit / 44.1kHz"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("hires-96"))
        assertEquals("HIGH", ModuleSource.qualityTier("HIGH"))
        assertEquals("HIGH", ModuleSource.qualityTier("320kbps"))
        assertEquals("LOW", ModuleSource.qualityTier("128kbps"))
        assertEquals("LOW", ModuleSource.qualityTier("LOW"))
        assertNull(ModuleSource.qualityTier(""))
        assertNull(ModuleSource.qualityTier("Deadbeat"))
    }

    /** The codec wins the tie: a bit depth alongside FLAC is still FLAC. */
    @Test
    fun `does not mistake a bit depth for a bitrate tier`() {
        assertEquals("LOSSLESS", ModuleSource.qualityTier("24-bit / 192 kHz"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("FLAC 128"))
    }

    @Test
    fun `orders tiers worst to best`() {
        assertEquals(listOf("LOW", "HIGH", "LOSSLESS"), ModuleSource.TIERS)
    }

    // ---- Runtime parsing ---------------------------------------------------

    @Test
    fun `reads a runtime off a queue row`() {
        assertEquals(225, TrackMatcher.secondsOf("3:45"))
        assertEquals(3723, TrackMatcher.secondsOf("1:02:03"))
        assertNull(TrackMatcher.secondsOf(null))
        assertNull(TrackMatcher.secondsOf("live"))
        assertNull(TrackMatcher.secondsOf("0:00"))
    }
}
