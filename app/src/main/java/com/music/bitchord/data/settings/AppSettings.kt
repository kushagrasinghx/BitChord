package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.common.Player
import com.music.bitchord.BuildConfig
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.sources.SourceKind
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stream bitrate ceiling on the YouTube fallback path — MEDIUM, HIGH and
 * LOSSLESS all mean "whatever the best available Opus format is" there; what
 * actually tells them apart is which other sources are allowed to answer
 * *before* YouTube gets asked. That part is [permits], and the rungs read:
 *
 * - [LOSSLESS] — the user's own addons and JioSaavn both asked.
 * - [HIGH] — the addons skipped, JioSaavn asked.
 * - [MEDIUM] and [LOW] — both skipped; YouTube's own Opus ladder is all there
 *   is, capped at [maxKbps].
 *
 * [hourly] is what the ceiling costs in data over an hour of listening, which
 * is the only part of this a user actually cares about on a metered plan.
 */
enum class AudioQuality(
    val maxKbps: Int,
    val label: String,
    val detail: String,
    val hourly: String,
) {
    LOW(64, "Low", "~64 kbps · smallest download", "29 MB/hr"),
    MEDIUM(Int.MAX_VALUE, "Medium", "Best available · ~171 kbps Opus", "77 MB/hr"),
    HIGH(Int.MAX_VALUE, "High", "JioSaavn up to 320kbps, YouTube fallback", "144 MB/hr"),
    LOSSLESS(Int.MAX_VALUE, "Lossless", "Your addons + JioSaavn, bit-exact where available", "300+ MB/hr"),
    ;

    /**
     * Whether a stream started under this ceiling may be served by [kind].
     *
     * Asked per stream rather than written into
     * [SourceConfig.enabled][com.music.bitchord.data.sources.SourceConfig.enabled],
     * which is what this used to do — an `applyQualityPreset` call flipped the
     * module and JioSaavn switches the moment a rung was picked. Two things
     * were wrong with that and both were reported together: picking a rung for
     * *mobile data* turned the sources off while sitting on Wi-Fi, and nothing
     * turned them back on when the connection changed, so a Wi-Fi ceiling of
     * Lossless still had no lossless source to reach. A ceiling is a property
     * of the connection in force; the switches on the Sources screen are the
     * user's standing choice. Storing the first in the second lost the second.
     *
     * [SourceKind.YOUTUBE] is permitted on every rung: it is what [maxKbps]
     * caps, and it is the only source that can answer at all when the ones
     * above it are skipped.
     */
    fun permits(kind: SourceKind): Boolean = when (this) {
        LOSSLESS -> true
        // No lossless answer is wanted here, and a source that can serve one is
        // the slow half of the list: an addon fronting several catalogues walks
        // all of them before it answers, which is seconds spent to land on a
        // transcode JioSaavn already has at 320.
        HIGH -> !kind.canServeLossless
        MEDIUM, LOW -> kind == SourceKind.YOUTUBE
    }
}

/**
 * PCM format requested from Media3's AudioTrack sink.
 *
 * FLOAT_32 is not a cosmetic "hi-res" switch: it makes Media3 convert
 * high-resolution integer PCM to IEEE-754 float and configure AudioTrack for
 * PCM_FLOAT. Android may still route/resample it according to the selected
 * output device, which is why the player exposes the negotiated format.
 */
enum class OutputPcmMode(val label: String) {
    PCM_16("16-bit PCM"),
    FLOAT_32("32-bit float"),
}

/**
 * What to keep when a track is saved to the device.
 *
 * Deliberately not [AudioQuality]. That enum budgets a *stream*, and is priced
 * per hour because the same bytes are spent again on every replay. A download is
 * the opposite trade — paid for once, kept, played from disk forever after — so
 * the figure that decides it is what one track costs, and the rung worth
 * defaulting to is the top one rather than the cheap one.
 *
 * The rungs themselves differ too. On the YouTube fallback path a download is
 * Opus-in-WebM (see
 * [StreamResolver.resolveForDownload][com.music.bitchord.data.innertube.StreamResolver.resolveForDownload]),
 * while configured sources can supply their own AAC or lossless copy. And
 * [LOSSLESS] has no streaming counterpart at all: it is the only rung that lets
 * a configured source's bit-exact file end up as a file on disk.
 */
enum class DownloadQuality(
    /** Ceiling for the YouTube Opus ladder. [Int.MAX_VALUE] means "whichever rung is best". */
    val maxKbps: Int,
    val label: String,
    val detail: String,
    /** Roughly what one four-minute track costs at this rung, sans unit context. */
    val perTrack: String,
    /** Whether a source's bit-exact file is worth keeping, or a transcode will do. */
    val keepsLossless: Boolean,
) {
    STANDARD(128, "Standard", "~128 kbps Opus · fits more on the device", "~4 MB", false),
    HIGH(Int.MAX_VALUE, "High", "Best audio on offer; source quality first", "~8 MB", false),
    LOSSLESS(
        Int.MAX_VALUE,
        "Lossless",
        "Bit-exact if a source has it, best Opus if not",
        "~35 MB",
        true,
    ),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

/** CPU budget for Automix's background analysis, not its audible mix algorithm. */
enum class AutomixPerformanceMode(val inferenceThreads: Int) {
    EFFICIENT(1),
    BALANCED(2),
    PERFORMANCE(4),
}

/** Stable persisted ordering for each on-device music library. */
enum class LocalMusicSort {
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED,
    DATE_MODIFIED,
}

/**
 * Stable persisted ordering for a Library "Show all" grid — playlists or
 * albums. A card there only ever carries a title, so unlike [LocalMusicSort]
 * there is nothing date-based to offer.
 */
enum class LibrarySort {
    /** Whatever order the shelf itself arrived in — YouTube Music's own. */
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
}

/** Display mode for music lists: compact rows or grid cards. */
enum class LibraryViewType {
    LIST,
    GRID,
}

/**
 * App settings, backed by SharedPreferences and exposed as flows.
 *
 * PlaybackService runs in the same process as the UI, so it observes these
 * same flows and applies changes to the live ExoPlayer instance immediately —
 * no restart, no rebinding.
 */
object AppSettings {

    private lateinit var prefs: SharedPreferences

    /** Only for the Discord token — everything else on here is plain prefs. */
    private lateinit var authStore: AuthStore

    /**
     * Quality ceilings, one per kind of connection — the point of the split is
     * that Wi-Fi can stay on Lossless while mobile data is capped. Both
     * default to Lossless; the mobile plan is the user's to budget, not ours
     * to assume.
     */
    val audioQualityWifi = MutableStateFlow(AudioQuality.LOSSLESS)
    val audioQualityCellular = MutableStateFlow(AudioQuality.LOSSLESS)

    /**
     * What a saved file should be, answered on its own terms.
     *
     * Kept apart from the two ceilings above on purpose. Those are about what
     * this minute's connection costs, and a download outlives the minute it was
     * started in — capping a permanent file at whichever network happened to be
     * in hand bakes a temporary decision into a lasting artefact, and the
     * reverse (a High ceiling on Wi-Fi implying 35MB FLACs of everything) is
     * just as wrong in the other direction.
     *
     * Data spend on a download is [wifiOnlyDownloads]' problem, not this
     * setting's, which is what lets this one be purely about the file.
     *
     * Defaults to [DownloadQuality.LOSSLESS] because that is what the download
     * path already did on an uncapped connection, and [migrateDownloadQuality]
     * keeps it that way for the people it didn't.
     */
    val downloadQuality = MutableStateFlow(DownloadQuality.LOSSLESS)

    /**
     * Refuse to start a download while the connection charges for data.
     *
     * Metered rather than literally-Wi-Fi, the same test [effectiveAudioQuality]
     * makes, because the thing worth protecting is the bill and not the radio: a
     * tethered hotspot is Wi-Fi that costs money, and an unmetered home
     * connection is worth using whether or not it arrives over Wi-Fi.
     *
     * On by default, and that is a deliberate change of behaviour for anyone
     * updating. [downloadQuality] defaulting to Lossless means a tap that used
     * to spend four megabytes of mobile data can now spend thirty-five, and of
     * the two ways to get that wrong — silently overspending a data plan, or
     * refusing with a sentence naming the switch that would allow it — only the
     * second is recoverable by the person it happens to.
     */
    val wifiOnlyDownloads = MutableStateFlow(true)

    /**
     * Keep ordinary downloads in Music/BitChord where other music apps can see
     * them. Off (the default) keeps downloads in this app's private storage.
     * HLS downloads always stay private because they are a playlist package,
     * not one portable audio file.
     */
    val exportDownloads = MutableStateFlow(false)

    /** Whether the active network charges for data. `null` while offline. */
    val meteredConnection = MutableStateFlow<Boolean?>(null)

    // `losslessAudio` used to live here, behind a "Prefer lossless" switch on
    // the Sources screen. It is gone: sources are asked for their best and each
    // degrades on its own terms, so the switch's only real effect was to ask a
    // module for a worse file than it was holding. See
    // [SourceResolver.requestForNow][com.music.bitchord.data.sources.SourceResolver.requestForNow],
    // which now reads [effectiveAudioQuality] and nothing else.

    val crossfadeSeconds = MutableStateFlow(0)

    /**
     * Lets Automix's analyzer decide the transition's timing and length
     * from each track's tempo, energy and structure, replacing the fixed
     * [crossfadeSeconds] window rather than needing it set to anything first
     * — [crossfadeSeconds] only matters here as a fallback while a pair is
     * still being analysed. Off by default: analysis costs a background
     * decode per track.
     *
     * See [com.music.bitchord.playback.smart.TransitionPlanner].
     */
    val smartFadeEnabled = MutableStateFlow(false)

    /** The CPU budget used by Beat This! and vocal analysis for Automix. */
    val automixPerformanceMode = MutableStateFlow(AutomixPerformanceMode.BALANCED)
    val skipSilence = MutableStateFlow(false)

    /** Requested PCM representation at the Android AudioTrack boundary. */
    val outputPcmMode = MutableStateFlow(OutputPcmMode.PCM_16)

    /** Prefer an attached USB audio output over the system's normal route. */
    val preferUsbDac = MutableStateFlow(false)

    /**
     * Whether a source offering a Dolby Atmos rendition is allowed to serve it.
     *
     * On by default: where the device can decode it, Atmos is the premium
     * rendition the catalogue holds and the one most people are paying a
     * subscription for.
     *
     * Off is a real preference and not just a safety valve. Atmos is E-AC-3,
     * which is *lossy* — a track with an Atmos master is frequently also held
     * as a FLAC, and someone listening on wired headphones may well prefer the
     * bit-exact stereo copy to a spatial mix their output can't render. Turning
     * this off is how they say so; see
     * [ModuleSource.unplayable][com.music.bitchord.data.sources.ModuleSource],
     * which is where the refusal is applied.
     *
     * Independent of whether the device *can* decode it — that question is
     * [DeviceCodecs.playsDolbyAtmos][com.music.bitchord.data.sources.DeviceCodecs],
     * and the two are deliberately not folded together: this one is the
     * listener's answer, is persisted, and must survive being read on a phone
     * that cannot honour it (a restored backup, a swapped device) without
     * quietly rewriting itself.
     */
    val dolbyAtmos = MutableStateFlow(true)

    /**
     * Widens stereo output via [com.music.bitchord.playback.SpatialAudioProcessor],
     * a stereo widening + cross-feed effect running inside ExoPlayer's own
     * pipeline. Not true object-based spatial audio — YouTube only ever hands
     * us a stereo stream, so there's no Atmos-style source to render.
     */
    val spatialAudio = MutableStateFlow(false)
    val playbackSpeed = MutableStateFlow(1.0f)
    val themeMode = MutableStateFlow(ThemeMode.DARK)

    /** Keep playing similar music once the queue runs out. */
    val autoplay = MutableStateFlow(true)

    /** Whether the queue is held in shuffled order (Mix button). */
    val shuffleEnabled = MutableStateFlow(false)

    /** Repeat mode for the player — Off, All, or One. */
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    /** Put the playing track's codec, bitrate and sample rate on the player. */
    val showNerdStats = MutableStateFlow(false)

    /** Freezes the main player's mesh gradient instead of letting it drift/crossfade. */
    val reduceAnimation = MutableStateFlow(false)

    /** Requests a sustained high-refresh UI. Off keeps Android's automatic policy. */
    val highPerformanceMode = MutableStateFlow(false)

    /** Preferred UI refresh rate while [highPerformanceMode] is enabled. */
    val performanceRefreshRate = MutableStateFlow(DEFAULT_PERFORMANCE_REFRESH_RATE)

    /** Stop playback when the app is swiped away from the recent apps screen. */
    val stopOnTaskRemoved = MutableStateFlow(false)

    /** Hides the volume slider on the main player, leaving the rest of the layout to reflow. */
    val hideVolumeBar = MutableStateFlow(false)

    /** Swiping a song row plays it next instead of adding it to the end of the queue. */
    val swipeToPlayNext = MutableStateFlow(false)

    /** Once a song has been suggested or played this session, AutoPlay won't offer it again. */
    val dontRepeatSuggestions = MutableStateFlow(false)

    /** Drops haze blur (status bar, mini player, bottom fade, lyrics focus) for a solid-fill look. */
    val reduceDynamicBlur = MutableStateFlow(false)

    /** Real backdrop-sampled glass (blur, lens refraction) on the floating nav bar, Android 12+ only. */
    val liquidGlass = MutableStateFlow(false)

    /** Blurs unfocused lyric lines, keeping the active line sharp. */
    val lyricsBlur = MutableStateFlow(true)

    /**
     * Plays a looping video behind the cover art on the player when one is
     * published for the track — Spotify's Canvas, Apple's motion artwork.
     *
     * Costs a video stream on top of the audio one and reaches three
     * services that have nothing to do with playback, so it stays a switch —
     * but it is the better default, and most tracks resolve to no canvas at
     * all. See [CanvasRepository][com.music.bitchord.data.canvas.CanvasRepository].
     */
    val animatedCanvas = MutableStateFlow(true)

    /**
     * Whether [animatedCanvas] is allowed to actually stream on a metered
     * connection, as distinct from the switch that turns the feature off
     * altogether.
     *
     * Off by default. A canvas clip loops for as long as its track plays,
     * and every loop past the first re-fetches the same few seconds of video
     * — see [CanvasCache][com.music.bitchord.data.canvas.CanvasCache] for why
     * that costs network at all rather than being answered from a buffer —
     * so a few-second clip behind a four-minute track on cellular is not a
     * flat video cost, it is that cost repeated dozens of times per song.
     * That is the shape of the reported 8GB day: still art costs nothing
     * here and stays up regardless of this setting.
     */
    val canvasOverCellular = MutableStateFlow(false)

    /**
     * Blows the player's cover art out to a full-bleed banner running off the
     * top of the screen, rather than sitting it in a square card.
     *
     * The treatment motion artwork has always had, applied to still sleeves too.
     * Off restores the card: the sleeve keeps its corners, its shadow and its
     * shrink-while-paused, and only a clip goes full-bleed. Phones only either
     * way — see the hero notes in
     * [NowPlayingScreen][com.music.bitchord.ui.player.NowPlayingScreen].
     */
    val fullBleedArtwork = MutableStateFlow(true)

    /**
     * Puts v1.5's backdrop back on the player: four quantised blobs drifting
     * behind the whole screen, rather than the artwork's own colours hung off
     * the sleeve's bottom edge.
     *
     * Off by default, because the current backdrop replaced it for two reasons
     * that have not gone away — see [ArtworkMesh][com.music.bitchord.ui.player.ArtworkMesh]
     * for the colour one (a cover that is nine-tenths black with a red stripe
     * comes back from the quantiser as a red screen) and
     * [ArtworkMeshBackdrop][com.music.bitchord.ui.player.ArtworkMeshBackdrop]
     * for the cost one (blobs that drift are a full-screen blur redrawn while
     * they move, where a mesh is drawn once per track and then composited).
     * Kept as a switch because people asked for the old look back, and neither
     * reason is one a listener has to agree with.
     */
    val legacyMeshGradient = MutableStateFlow(false)

    /**
     * Time-synced lyrics on the player, lit up as they are sung.
     *
     * On by default — it is most of the point of the player screen — but it
     * reaches third-party lyric databases for every track played, so it stays
     * a switch, and [lyricsSources] narrows which of them get asked.
     */
    val syncedLyrics = MutableStateFlow(true)

    /** The databases [syncedLyrics] may ask. Empty is the same as off. */
    val lyricsSources = MutableStateFlow(LyricsSource.entries.toSet())

    /**
     * The order [lyricsSources] are asked in — see [LyricsRepository][com.music.bitchord.data.lyrics.LyricsRepository]:
     * every enabled source is asked at once, but a higher-priority one still
     * pending is never preempted by a lower one that happened to answer first.
     * Reordered from Settings, so this is a full permutation of
     * [LyricsSource.entries] rather than a subset — enabling and ordering are
     * independent choices.
     */
    val lyricsSourceOrder = MutableStateFlow<List<LyricsSource>>(LyricsSource.entries)

    /**
     * Off, the highest-priority source to answer at all is taken as the
     * lyrics, word-synced or not. On, a merely line-synced answer is held as
     * a fallback while the rest of [lyricsSourceOrder] is still checked for a
     * word-synced one — worth the extra network calls to some, not to others,
     * which is why it defaults off rather than being how [LyricsRepository]
     * always behaved.
     */
    val prioritizeSyllableSync = MutableStateFlow(false)

    /** When enabled, shows lyrics fetching and Genius scraping logs in the lyrics menu/panel. */
    val showLyricsLogs = MutableStateFlow(false)

    /** Disk budget for cached audio. [AudioCache][com.music.bitchord.playback.AudioCache] evicts past it. */
    val audioCacheLimitBytes = MutableStateFlow(DEFAULT_CACHE_LIMIT_BYTES)

    // ── Replay ──────────────────────────────────────────────────────────────

    /**
     * Whether Replay may work out a genre chart.
     *
     * Its own switch because it is the one part of Replay that isn't purely
     * local: everything else on that page is counted on this device and never
     * leaves it, while a genre has to be looked up by artist name — see
     * [ArtistFacts][com.music.bitchord.data.stats.ArtistFacts]. On by default,
     * since it sends a name and nothing else and the answer is what makes a
     * quarter of the page exist; off, the genre chart simply isn't drawn.
     */
    val replayGenres = MutableStateFlow(true)

    // ── Library ─────────────────────────────────────────────────────────────

    /** Hides short clips, recorder output and non-music formats from Local Music. */
    val filterNonMusicAudio = MutableStateFlow(true)

    val localMusicSort = MutableStateFlow(LocalMusicSort.TITLE_ASC)
    val downloadedMusicSort = MutableStateFlow(LocalMusicSort.TITLE_ASC)
    val localMusicViewType = MutableStateFlow(LibraryViewType.LIST)
    val downloadedMusicViewType = MutableStateFlow(LibraryViewType.LIST)
    val librarySort = MutableStateFlow(LibrarySort.DEFAULT)

    /** Empty means every MediaStore folder; otherwise this is a persisted SAF tree URI. */
    val localMusicFolderUri = MutableStateFlow("")

    /**
     * Browse ids of the playlists pinned to the top of the Library tab, in the
     * order they were pinned.
     *
     * A [List] rather than a [Set]: pin order is part of what a pin means here —
     * the whole point is a small, hand-picked front row, and a set would leave
     * that order to hash iteration. Capped at [MAX_PINNED_PLAYLISTS] by
     * [togglePinnedPlaylist], the only way this is ever written.
     */
    val pinnedPlaylists = MutableStateFlow<List<String>>(emptyList())

    /** How many playlists [pinnedPlaylists] can hold at once. */
    const val MAX_PINNED_PLAYLISTS = 5

    // ── Scrobbling ──────────────────────────────────────────────────────

    /** One release gate shared by the settings UI and the playback service. */
    val scrobblingAvailable = true

    val lastfmEnabled = MutableStateFlow(false)
    val lastfmUsername = MutableStateFlow("")
    val lastfmSessionKey = MutableStateFlow("")
    val lastfmApiKey = MutableStateFlow("")
    val lastfmSecret = MutableStateFlow("")
    val lastfmEndpoint = MutableStateFlow("")
    val lastfmScrobbleEnabled = MutableStateFlow(false)
    val lastfmNowPlaying = MutableStateFlow(false)
    val lastfmPrimaryArtistOnly = MutableStateFlow(false)
    val scrobbleMinDuration = MutableStateFlow(30)
    val scrobbleDelayPercent = MutableStateFlow(0.5f)
    val scrobbleDelaySeconds = MutableStateFlow(180)
    val listenBrainzEnabled = MutableStateFlow(false)
    val listenBrainzToken = MutableStateFlow("")
    val listenBrainzPrimaryArtistOnly = MutableStateFlow(false)
    val spotifySpdcToken = MutableStateFlow("")

    // ── Discord Rich Presence ───────────────────────────────────────────

    /**
     * The connected Discord account's token, mirrored out of [AuthStore] so
     * [PlaybackService][com.music.bitchord.playback.PlaybackService] can pick
     * up a login without polling for one. Empty means not connected.
     *
     * Only the mirror is here — the persisted copy is encrypted, because unlike
     * a scrobbler key this one is the account itself.
     */
    val discordToken = MutableStateFlow("")

    /**
     * Who the token belongs to, cached at login. Kept so the settings screen
     * can show the account without a round trip every time it opens, and can
     * still show it offline.
     */
    val discordUsername = MutableStateFlow("")
    val discordName = MutableStateFlow("")
    val discordAvatar = MutableStateFlow("")

    val discordRpcEnabled = MutableStateFlow(true)

    /** Put the track title on the bold profile line, in place of the artist. */
    val discordUseDetails = MutableStateFlow(false)

    /** Reveals the presence-shape controls: status, activity type/name, buttons. */
    val discordAdvancedMode = MutableStateFlow(false)

    val discordStatus = MutableStateFlow("online")
    val discordActivityType = MutableStateFlow("listening")

    /** Overrides the "Listening to ___" line; empty means the app's own name. */
    val discordActivityName = MutableStateFlow("")

    val discordButton1Text = MutableStateFlow("")
    val discordButton1Visible = MutableStateFlow(true)
    val discordButton2Text = MutableStateFlow("")
    val discordButton2Visible = MutableStateFlow(true)

    /** The notice about what connecting an account actually does has been read. */
    val discordInfoDismissed = MutableStateFlow(false)

    /** Published by PlaybackService so the UI can open the system equalizer. */
    val audioSessionId = MutableStateFlow(0)

    /**
     * True only while a Automix transition that is actually *mixing* is
     * audible — one that beat-matched, cued the incoming track into its
     * arrangement, or rode a filter.
     *
     * Deliberately not "a crossfade is running". The fallback case, where
     * neither track was analysed in time and the incoming one starts from 0:00
     * under a plain equal-power fade, is exactly what this must stay dark for:
     * the whole point is that seeing it means the analysis landed and did
     * something a plain crossfade could not.
     */
    val smartMixInProgress = MutableStateFlow(false)

    /**
     * How much of the *upcoming* transition has been analysed, for stats for
     * nerds. Published by the crossfade controller, which is the only thing
     * that knows which two tracks the next transition is between.
     */
    val smartAnalysis = MutableStateFlow(SmartAnalysis())

    /**
     * Where on the *playing* track the next transition is planned to happen, as
     * fractions of its duration, or null when there is nothing worth drawing.
     *
     * Only published once both tracks are measured. Before that the planner is
     * still working from a fallback window that moves as evidence arrives, and
     * a marker that slides around the bar would be worse than no marker.
     */
    val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)

    /** The ceiling that applies to a stream started right now. */
    val effectiveAudioQuality: AudioQuality
        get() = if (meteredConnection.value == true) {
            audioQualityCellular.value
        } else {
            audioQualityWifi.value
        }

    /**
     * Whether a download may start on the connection in hand.
     *
     * A null [meteredConnection] means there is no active network, and that is
     * deliberately allowed through: a download with nothing to download over
     * fails on the network and says so, which is true, where refusing it here
     * would blame a Wi-Fi setting for an outage.
     */
    val downloadsAllowedNow: Boolean
        get() = !wifiOnlyDownloads.value || meteredConnection.value != true

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        authStore = AuthStore(context)
        readAll()
        watchConnection(context)
    }

    /**
     * Re-reads every setting off disk.
     *
     * The one caller is an import ([Backup][com.music.bitchord.data.stats.Backup]),
     * which writes the whole preference file underneath these flows. Nothing
     * else in the app changes a preference without going through the setter
     * beside it, so nothing else has a reason to ask.
     *
     * Deliberately not re-registering the network callback: that watches the
     * device, not the preferences, and a second one would have both firing.
     */
    fun reload() {
        if (!this::prefs.isInitialized) return
        readAll()
    }

    private fun readAll() {
        migrateSingleQuality()
        audioQualityWifi.value = readQuality(KEY_QUALITY_WIFI)
        audioQualityCellular.value = readQuality(KEY_QUALITY_CELLULAR)
        migrateDownloadQuality()
        downloadQuality.value = readDownloadQuality()
        wifiOnlyDownloads.value = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
        exportDownloads.value = prefs.getBoolean(KEY_EXPORT_DOWNLOADS, false)
        crossfadeSeconds.value = prefs.getInt(KEY_CROSSFADE, 0)
        smartFadeEnabled.value = prefs.getBoolean(KEY_SMART_FADE, false)
        automixPerformanceMode.value = runCatching {
            AutomixPerformanceMode.valueOf(
                prefs.getString(KEY_AUTOMIX_PERFORMANCE_MODE, null) ?: AutomixPerformanceMode.BALANCED.name,
            )
        }.getOrDefault(AutomixPerformanceMode.BALANCED)
        skipSilence.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        outputPcmMode.value = runCatching {
            OutputPcmMode.valueOf(
                prefs.getString(KEY_OUTPUT_PCM_MODE, OutputPcmMode.PCM_16.name)
                    ?: OutputPcmMode.PCM_16.name,
            )
        }.getOrDefault(OutputPcmMode.PCM_16)
        preferUsbDac.value = prefs.getBoolean(KEY_PREFER_USB_DAC, false)
        dolbyAtmos.value = prefs.getBoolean(KEY_DOLBY_ATMOS, true)
        spatialAudio.value = prefs.getBoolean(KEY_SPATIAL_AUDIO, false)
        playbackSpeed.value = prefs.getFloat(KEY_SPEED, 1.0f)
        themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "DARK")
        }.getOrDefault(ThemeMode.DARK)
        autoplay.value = prefs.getBoolean(KEY_AUTOPLAY, true)
        shuffleEnabled.value = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
        repeatMode.value = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)
        showNerdStats.value = prefs.getBoolean(KEY_NERD_STATS, false)
        reduceAnimation.value = prefs.getBoolean(KEY_REDUCE_ANIMATION, false)
        highPerformanceMode.value = prefs.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        performanceRefreshRate.value = normalizePerformanceRefreshRate(
            prefs.getInt(KEY_PERFORMANCE_REFRESH_RATE, DEFAULT_PERFORMANCE_REFRESH_RATE),
        )
        stopOnTaskRemoved.value = prefs.getBoolean(KEY_STOP_ON_TASK_REMOVED, false)
        hideVolumeBar.value = prefs.getBoolean(KEY_HIDE_VOLUME_BAR, false)
        swipeToPlayNext.value = prefs.getBoolean(KEY_SWIPE_TO_PLAY_NEXT, false)
        dontRepeatSuggestions.value = prefs.getBoolean(KEY_DONT_REPEAT_SUGGESTIONS, false)
        reduceDynamicBlur.value = prefs.getBoolean(KEY_REDUCE_BLUR, false)
        liquidGlass.value = prefs.getBoolean(KEY_LIQUID_GLASS, false)
        lyricsBlur.value = prefs.getBoolean(KEY_LYRICS_BLUR, true)
        if (highPerformanceMode.value) {
            reduceAnimation.value = false
            reduceDynamicBlur.value = false
        }
        animatedCanvas.value = prefs.getBoolean(KEY_ANIMATED_CANVAS, true)
        canvasOverCellular.value = prefs.getBoolean(KEY_CANVAS_OVER_CELLULAR, false)
        fullBleedArtwork.value = prefs.getBoolean(KEY_FULL_BLEED_ARTWORK, true)
        legacyMeshGradient.value = prefs.getBoolean(KEY_LEGACY_MESH_GRADIENT, false)
        syncedLyrics.value = prefs.getBoolean(KEY_SYNCED_LYRICS, true)
        lyricsSources.value = readLyricsSources()
        lyricsSourceOrder.value = readLyricsSourceOrder()
        prioritizeSyllableSync.value = prefs.getBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, false)
        showLyricsLogs.value = prefs.getBoolean(KEY_SHOW_LYRICS_LOGS, false)
        audioCacheLimitBytes.value = prefs.getLong(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT_BYTES)
            .coerceIn(DEFAULT_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES)
        lastfmEnabled.value = prefs.getBoolean(KEY_LASTFM_ENABLED, false)
        lastfmUsername.value = prefs.getString(KEY_LASTFM_USERNAME, "").orEmpty()
        lastfmSessionKey.value = prefs.getString(KEY_LASTFM_SESSION_KEY, "").orEmpty()
        lastfmApiKey.value = prefs.getString(KEY_LASTFM_API_KEY, "").orEmpty().ifBlank { BuildConfig.LASTFM_API_KEY }
        lastfmSecret.value = prefs.getString(KEY_LASTFM_SECRET, "").orEmpty().ifBlank { BuildConfig.LASTFM_SECRET }
        lastfmEndpoint.value = prefs.getString(KEY_LASTFM_ENDPOINT, "").orEmpty()
        lastfmScrobbleEnabled.value = prefs.getBoolean(KEY_LASTFM_SCROBBLE_ENABLED, false)
        lastfmNowPlaying.value = prefs.getBoolean(KEY_LASTFM_NOW_PLAYING, false) && lastfmScrobbleEnabled.value
        lastfmPrimaryArtistOnly.value = prefs.getBoolean(KEY_LASTFM_PRIMARY_ARTIST_ONLY, false)
        scrobbleMinDuration.value = prefs.getInt(KEY_SCROBBLE_MIN_DURATION, 30)
        scrobbleDelayPercent.value = prefs.getFloat(KEY_SCROBBLE_DELAY_PERCENT, 0.5f)
        scrobbleDelaySeconds.value = prefs.getInt(KEY_SCROBBLE_DELAY_SECONDS, 180)
        listenBrainzEnabled.value = prefs.getBoolean(KEY_LISTENBRAINZ_ENABLED, false)
        listenBrainzToken.value = prefs.getString(KEY_LISTENBRAINZ_TOKEN, "").orEmpty()
        listenBrainzPrimaryArtistOnly.value = prefs.getBoolean(KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY, false)
        spotifySpdcToken.value = prefs.getString(KEY_SPOTIFY_SPDC_TOKEN, "").orEmpty()
        replayGenres.value = prefs.getBoolean(KEY_REPLAY_GENRES, true)
        filterNonMusicAudio.value = prefs.getBoolean(KEY_FILTER_NON_MUSIC_AUDIO, true)
        localMusicSort.value = readLocalMusicSort(KEY_LOCAL_MUSIC_SORT)
        downloadedMusicSort.value = readLocalMusicSort(KEY_DOWNLOADED_MUSIC_SORT)
        localMusicViewType.value = readLibraryViewType(KEY_LOCAL_MUSIC_VIEW_TYPE)
        downloadedMusicViewType.value = readLibraryViewType(KEY_DOWNLOADED_MUSIC_VIEW_TYPE)
        librarySort.value = prefs.getString(KEY_LIBRARY_SORT, null)
            ?.let { saved -> LibrarySort.entries.firstOrNull { it.name == saved } }
            ?: LibrarySort.DEFAULT
        localMusicFolderUri.value = prefs.getString(KEY_LOCAL_MUSIC_FOLDER_URI, "").orEmpty()
        pinnedPlaylists.value = readPinnedPlaylists()
        discordToken.value = authStore.discordToken.orEmpty()
        discordUsername.value = prefs.getString(KEY_DISCORD_USERNAME, "").orEmpty()
        discordName.value = prefs.getString(KEY_DISCORD_NAME, "").orEmpty()
        discordAvatar.value = prefs.getString(KEY_DISCORD_AVATAR, "").orEmpty()
        discordRpcEnabled.value = prefs.getBoolean(KEY_DISCORD_RPC_ENABLED, true)
        discordUseDetails.value = prefs.getBoolean(KEY_DISCORD_USE_DETAILS, false)
        discordAdvancedMode.value = prefs.getBoolean(KEY_DISCORD_ADVANCED_MODE, false)
        discordStatus.value = prefs.getString(KEY_DISCORD_STATUS, "online").orEmpty()
        discordActivityType.value = prefs.getString(KEY_DISCORD_ACTIVITY_TYPE, "listening").orEmpty()
        discordActivityName.value = prefs.getString(KEY_DISCORD_ACTIVITY_NAME, "").orEmpty()
        discordButton1Text.value = prefs.getString(KEY_DISCORD_BUTTON_1_TEXT, "").orEmpty()
        discordButton1Visible.value = prefs.getBoolean(KEY_DISCORD_BUTTON_1_VISIBLE, true)
        discordButton2Text.value = prefs.getString(KEY_DISCORD_BUTTON_2_TEXT, "").orEmpty()
        discordButton2Visible.value = prefs.getBoolean(KEY_DISCORD_BUTTON_2_VISIBLE, true)
        discordInfoDismissed.value = prefs.getBoolean(KEY_DISCORD_INFO_DISMISSED, false)
    }

    /**
     * True the first time this is called after [currentVersionCode] rises above
     * whatever was last recorded — i.e. once per update, on the first launch
     * after it installs. A fresh install has nothing to compare against, so
     * the very first call seeds the stored value from [currentVersionCode]
     * rather than reporting an update.
     *
     * BitChord ships sideloaded (see [com.music.bitchord.data.AppUpdateChecker]),
     * so installing a new APK over the old one is the only "update" there is —
     * app data, this pref included, survives it exactly like a Play Store
     * update. Call once per process start, before anything reads a cache that
     * an update should invalidate.
     */
    fun consumeVersionUpdate(currentVersionCode: Int): Boolean {
        val last = prefs.getInt(KEY_LAST_VERSION_CODE, currentVersionCode)
        if (last != currentVersionCode) {
            prefs.edit().putInt(KEY_LAST_VERSION_CODE, currentVersionCode).apply()
        }
        return currentVersionCode > last
    }

    /**
     * A ceiling saved when there was only one applies to both connections.
     * Someone who picked Low to protect a data plan would not thank us for
     * quietly putting Wi-Fi *and* mobile back on High.
     */
    private fun migrateSingleQuality() {
        val legacy = prefs.getString(KEY_QUALITY_LEGACY, null) ?: return
        prefs.edit()
            .putString(KEY_QUALITY_WIFI, legacy)
            .putString(KEY_QUALITY_CELLULAR, legacy)
            .remove(KEY_QUALITY_LEGACY)
            .apply()
    }

    private fun readQuality(key: String): AudioQuality {
        val stored = prefs.getString(key, null) ?: return AudioQuality.LOSSLESS
        return runCatching { AudioQuality.valueOf(stored) }.getOrDefault(AudioQuality.LOSSLESS)
    }

    /**
     * Write down what the download path was already doing, before it starts
     * being asked instead.
     *
     * Download quality used to be derived rather than chosen: a lossless copy
     * was kept when `SourceResolver.requestForNow()` said Lossless, which meant
     * a download quietly turned on the lossless preference and off again with
     * it. Someone who switched that off on the Sources screen was getting AAC
     * downloads on purpose, and defaulting them to Lossless now would answer a
     * question they had already answered — with thirty-five megabytes a track.
     *
     * The ceilings are deliberately *not* consulted. They were only in that
     * derivation because there was nowhere else to say "not on mobile data",
     * and [wifiOnlyDownloads] is now where that is said.
     */
    private fun migrateDownloadQuality() {
        if (prefs.contains(KEY_QUALITY_DOWNLOAD)) return
        // Was derived from the old `losslessAudio` switch, which defaulted to
        // on; LOSSLESS is what that produced for all but the few installs that
        // had turned it off, and is the default a fresh install gets anyway.
        prefs.edit().putString(KEY_QUALITY_DOWNLOAD, DownloadQuality.LOSSLESS.name).apply()
    }

    private fun readDownloadQuality(): DownloadQuality {
        val stored = prefs.getString(KEY_QUALITY_DOWNLOAD, null) ?: return DownloadQuality.LOSSLESS
        return runCatching { DownloadQuality.valueOf(stored) }.getOrDefault(DownloadQuality.LOSSLESS)
    }

    /**
     * Track the active network so [effectiveAudioQuality] can answer without
     * touching ConnectivityManager. Stream resolution happens off the main
     * thread mid-playback; a callback keeps that lookup off the hot path and
     * lets the settings page show which ceiling is currently in force.
     */
    private fun watchConnection(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val refresh = {
            meteredConnection.value = runCatching {
                if (manager.activeNetwork == null) null else manager.isActiveNetworkMetered
            }.getOrNull()
        }
        refresh()
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = refresh()
                },
            )
        }
    }

    fun setAutoplay(value: Boolean) {
        autoplay.value = value
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun setShuffleEnabled(value: Boolean) {
        shuffleEnabled.value = value
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, value).apply()
    }

    fun setRepeatMode(value: Int) {
        repeatMode.value = value
        prefs.edit().putInt(KEY_REPEAT_MODE, value).apply()
    }

    fun setAudioQualityWifi(value: AudioQuality) {
        audioQualityWifi.value = value
        prefs.edit().putString(KEY_QUALITY_WIFI, value.name).apply()
    }

    fun setAudioQualityCellular(value: AudioQuality) {
        audioQualityCellular.value = value
        prefs.edit().putString(KEY_QUALITY_CELLULAR, value.name).apply()
    }

    fun setDownloadQuality(value: DownloadQuality) {
        downloadQuality.value = value
        prefs.edit().putString(KEY_QUALITY_DOWNLOAD, value.name).apply()
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        wifiOnlyDownloads.value = value
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, value).apply()
    }

    fun setCrossfadeSeconds(value: Int) {
        crossfadeSeconds.value = value
        prefs.edit().putInt(KEY_CROSSFADE, value).apply()
    }

    fun setSmartFadeEnabled(value: Boolean) {
        smartFadeEnabled.value = value
        prefs.edit().putBoolean(KEY_SMART_FADE, value).apply()
    }

    fun setAutomixPerformanceMode(value: AutomixPerformanceMode) {
        automixPerformanceMode.value = value
        prefs.edit().putString(KEY_AUTOMIX_PERFORMANCE_MODE, value.name).apply()
    }

    fun setSkipSilence(value: Boolean) {
        skipSilence.value = value
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()
    }

    fun setDolbyAtmos(value: Boolean) {
        dolbyAtmos.value = value
        prefs.edit().putBoolean(KEY_DOLBY_ATMOS, value).apply()
    }

    fun setSpatialAudio(value: Boolean) {
        spatialAudio.value = value
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO, value).apply()
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed.value = value
        prefs.edit().putFloat(KEY_SPEED, value).apply()
    }

    fun setShowNerdStats(value: Boolean) {
        showNerdStats.value = value
        prefs.edit().putBoolean(KEY_NERD_STATS, value).apply()
    }

    fun setThemeMode(value: ThemeMode) {
        themeMode.value = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setReduceAnimation(value: Boolean) {
        reduceAnimation.value = value
        if (value) highPerformanceMode.value = false
        val editor = prefs.edit().putBoolean(KEY_REDUCE_ANIMATION, value)
        if (value) editor.putBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        editor.apply()
    }

    fun setStopOnTaskRemoved(value: Boolean) {
        stopOnTaskRemoved.value = value
        prefs.edit().putBoolean(KEY_STOP_ON_TASK_REMOVED, value).apply()
    }

    fun setHideVolumeBar(value: Boolean) {
        hideVolumeBar.value = value
        prefs.edit().putBoolean(KEY_HIDE_VOLUME_BAR, value).apply()
    }

    fun setSwipeToPlayNext(value: Boolean) {
        swipeToPlayNext.value = value
        prefs.edit().putBoolean(KEY_SWIPE_TO_PLAY_NEXT, value).apply()
    }

    fun setDontRepeatSuggestions(value: Boolean) {
        dontRepeatSuggestions.value = value
        prefs.edit().putBoolean(KEY_DONT_REPEAT_SUGGESTIONS, value).apply()
    }

    fun setReduceDynamicBlur(value: Boolean) {
        reduceDynamicBlur.value = value
        if (value) highPerformanceMode.value = false
        val editor = prefs.edit().putBoolean(KEY_REDUCE_BLUR, value)
        if (value) editor.putBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        editor.apply()
    }

    fun setLiquidGlass(value: Boolean) {
        liquidGlass.value = value
        prefs.edit().putBoolean(KEY_LIQUID_GLASS, value).apply()
    }

    fun setHighPerformanceMode(value: Boolean) {
        highPerformanceMode.value = value
        if (value) {
            reduceAnimation.value = false
            reduceDynamicBlur.value = false
        }
        val editor = prefs.edit().putBoolean(KEY_HIGH_PERFORMANCE_MODE, value)
        if (value) {
            editor.putBoolean(KEY_REDUCE_ANIMATION, false)
            editor.putBoolean(KEY_REDUCE_BLUR, false)
        }
        editor.apply()
    }

    fun setPerformanceRefreshRate(value: Int) {
        val normalized = normalizePerformanceRefreshRate(value)
        performanceRefreshRate.value = normalized
        prefs.edit().putInt(KEY_PERFORMANCE_REFRESH_RATE, normalized).apply()
    }

    fun setLyricsBlur(value: Boolean) {
        lyricsBlur.value = value
        prefs.edit().putBoolean(KEY_LYRICS_BLUR, value).apply()
    }

    fun setSyncedLyrics(value: Boolean) {
        syncedLyrics.value = value
        prefs.edit().putBoolean(KEY_SYNCED_LYRICS, value).apply()
    }

    fun setLyricsSources(value: Set<LyricsSource>) {
        lyricsSources.value = value
        prefs.edit().putString(KEY_LYRICS_SOURCES, value.joinToString(",") { it.name }).apply()
    }

    /**
     * Stored as a joined list of names rather than a string set: a name that
     * no longer exists — a source dropped in a later build — has to fall out
     * quietly, and the default when nothing has been saved is "all of them",
     * which a missing key and an empty set would otherwise be unable to tell
     * apart.
     */
    private fun readLyricsSources(): Set<LyricsSource> {
        val stored = prefs.getString(KEY_LYRICS_SOURCES, null)
            ?: return LyricsSource.entries.toSet()
        return stored.split(",")
            .mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    fun setLyricsSourceOrder(value: List<LyricsSource>) {
        lyricsSourceOrder.value = value
        prefs.edit().putString(KEY_LYRICS_SOURCE_ORDER, value.joinToString(",") { it.name }).apply()
    }

    /**
     * A named source dropped from the stored order — an upgrade reordered
     * since it was saved — falls out on read; one added since is appended, in
     * [LyricsSource]'s own declared order, so a fresh install and an upgraded
     * one agree on where a new source lands until the user says otherwise.
     */
    private fun readLyricsSourceOrder(): List<LyricsSource> {
        val stored = prefs.getString(KEY_LYRICS_SOURCE_ORDER, null)
            ?: return LyricsSource.entries
        val saved = stored.split(",")
            .mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
        return saved + LyricsSource.entries.filter { it !in saved }
    }

    fun setPrioritizeSyllableSync(value: Boolean) {
        prioritizeSyllableSync.value = value
        prefs.edit().putBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, value).apply()
    }

    fun setShowLyricsLogs(value: Boolean) {
        showLyricsLogs.value = value
        prefs.edit().putBoolean(KEY_SHOW_LYRICS_LOGS, value).apply()
    }

    /**
     * Puts the source list, its order and [prioritizeSyllableSync] back the
     * way a fresh install finds them. [syncedLyrics] itself is left alone —
     * this is "start over on *which* lyrics", not "turn lyrics off".
     */
    fun resetLyricsSourceSettings() {
        setLyricsSources(LyricsSource.entries.toSet())
        setLyricsSourceOrder(LyricsSource.entries)
        setPrioritizeSyllableSync(false)
        setShowLyricsLogs(false)
    }

    fun setAnimatedCanvas(value: Boolean) {
        animatedCanvas.value = value
        prefs.edit().putBoolean(KEY_ANIMATED_CANVAS, value).apply()
    }

    fun setCanvasOverCellular(value: Boolean) {
        canvasOverCellular.value = value
        prefs.edit().putBoolean(KEY_CANVAS_OVER_CELLULAR, value).apply()
    }

    fun setFullBleedArtwork(value: Boolean) {
        fullBleedArtwork.value = value
        prefs.edit().putBoolean(KEY_FULL_BLEED_ARTWORK, value).apply()
    }

    fun setLegacyMeshGradient(value: Boolean) {
        legacyMeshGradient.value = value
        prefs.edit().putBoolean(KEY_LEGACY_MESH_GRADIENT, value).apply()
    }

    /** Clamped to [DEFAULT_CACHE_LIMIT_BYTES]..[MAX_CACHE_LIMIT_BYTES] — the floor is the default, not zero. */
    fun setAudioCacheLimitBytes(value: Long) {
        val clamped = value.coerceIn(DEFAULT_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES)
        audioCacheLimitBytes.value = clamped
        prefs.edit().putLong(KEY_CACHE_LIMIT, clamped).apply()
    }

    fun setLastfmEnabled(value: Boolean) {
        lastfmEnabled.value = value
        prefs.edit().putBoolean(KEY_LASTFM_ENABLED, value).apply()
    }

    fun setLastfmUsername(value: String) {
        lastfmUsername.value = value
        prefs.edit().putString(KEY_LASTFM_USERNAME, value).apply()
    }

    fun setLastfmSessionKey(value: String) {
        lastfmSessionKey.value = value
        prefs.edit().putString(KEY_LASTFM_SESSION_KEY, value).apply()
    }

    fun setLastfmApiKey(value: String) {
        lastfmApiKey.value = value
        prefs.edit().putString(KEY_LASTFM_API_KEY, value).apply()
    }

    fun setLastfmSecret(value: String) {
        lastfmSecret.value = value
        prefs.edit().putString(KEY_LASTFM_SECRET, value).apply()
    }

    fun setLastfmEndpoint(value: String) {
        lastfmEndpoint.value = value
        prefs.edit().putString(KEY_LASTFM_ENDPOINT, value).apply()
    }

    fun setSpotifySpdcToken(value: String) {
        spotifySpdcToken.value = value
        prefs.edit().putString(KEY_SPOTIFY_SPDC_TOKEN, value).apply()
    }

    fun setLastfmScrobbleEnabled(value: Boolean) {
        lastfmScrobbleEnabled.value = value
        if (!value) lastfmNowPlaying.value = false
        prefs.edit()
            .putBoolean(KEY_LASTFM_SCROBBLE_ENABLED, value)
            .putBoolean(KEY_LASTFM_NOW_PLAYING, if (value) lastfmNowPlaying.value else false)
            .apply()
    }

    fun setLastfmNowPlaying(value: Boolean) {
        if (!lastfmScrobbleEnabled.value && value) return
        lastfmNowPlaying.value = value
        prefs.edit().putBoolean(KEY_LASTFM_NOW_PLAYING, value).apply()
    }

    fun setOutputPcmMode(value: OutputPcmMode) {
        outputPcmMode.value = value
        prefs.edit().putString(KEY_OUTPUT_PCM_MODE, value.name).apply()
    }

    fun setPreferUsbDac(value: Boolean) {
        preferUsbDac.value = value
        prefs.edit().putBoolean(KEY_PREFER_USB_DAC, value).apply()
    }

    fun setExportDownloads(value: Boolean) {
        exportDownloads.value = value
        prefs.edit().putBoolean(KEY_EXPORT_DOWNLOADS, value).apply()
    }

    fun setLastfmPrimaryArtistOnly(value: Boolean) {
        lastfmPrimaryArtistOnly.value = value
        prefs.edit().putBoolean(KEY_LASTFM_PRIMARY_ARTIST_ONLY, value).apply()
    }

    fun setScrobbleMinDuration(value: Int) {
        scrobbleMinDuration.value = value
        prefs.edit().putInt(KEY_SCROBBLE_MIN_DURATION, value).apply()
    }

    fun setScrobbleDelayPercent(value: Float) {
        scrobbleDelayPercent.value = value
        prefs.edit().putFloat(KEY_SCROBBLE_DELAY_PERCENT, value).apply()
    }

    fun setScrobbleDelaySeconds(value: Int) {
        scrobbleDelaySeconds.value = value
        prefs.edit().putInt(KEY_SCROBBLE_DELAY_SECONDS, value).apply()
    }

    fun setListenBrainzEnabled(value: Boolean) {
        listenBrainzEnabled.value = value
        prefs.edit().putBoolean(KEY_LISTENBRAINZ_ENABLED, value).apply()
    }

    fun setListenBrainzToken(value: String) {
        listenBrainzToken.value = value
        prefs.edit().putString(KEY_LISTENBRAINZ_TOKEN, value).apply()
    }

    fun setListenBrainzPrimaryArtistOnly(value: Boolean) {
        listenBrainzPrimaryArtistOnly.value = value
        prefs.edit().putBoolean(KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY, value).apply()
    }

    /** Writes through to the encrypted store; pass "" to disconnect. */
    fun setDiscordToken(value: String) {
        discordToken.value = value
        authStore.discordToken = value.ifEmpty { null }
    }

    fun setDiscordAccount(username: String, name: String, avatar: String?) {
        discordUsername.value = username
        discordName.value = name
        discordAvatar.value = avatar.orEmpty()
        prefs.edit()
            .putString(KEY_DISCORD_USERNAME, username)
            .putString(KEY_DISCORD_NAME, name)
            .putString(KEY_DISCORD_AVATAR, avatar.orEmpty())
            .apply()
    }

    fun setDiscordRpcEnabled(value: Boolean) {
        discordRpcEnabled.value = value
        prefs.edit().putBoolean(KEY_DISCORD_RPC_ENABLED, value).apply()
    }

    fun setDiscordUseDetails(value: Boolean) {
        discordUseDetails.value = value
        prefs.edit().putBoolean(KEY_DISCORD_USE_DETAILS, value).apply()
    }

    fun setDiscordAdvancedMode(value: Boolean) {
        discordAdvancedMode.value = value
        prefs.edit().putBoolean(KEY_DISCORD_ADVANCED_MODE, value).apply()
    }

    fun setDiscordStatus(value: String) {
        discordStatus.value = value
        prefs.edit().putString(KEY_DISCORD_STATUS, value).apply()
    }

    fun setDiscordActivityType(value: String) {
        discordActivityType.value = value
        prefs.edit().putString(KEY_DISCORD_ACTIVITY_TYPE, value).apply()
    }

    fun setDiscordActivityName(value: String) {
        discordActivityName.value = value
        prefs.edit().putString(KEY_DISCORD_ACTIVITY_NAME, value).apply()
    }

    fun setDiscordButton1Text(value: String) {
        discordButton1Text.value = value
        prefs.edit().putString(KEY_DISCORD_BUTTON_1_TEXT, value).apply()
    }

    fun setDiscordButton1Visible(value: Boolean) {
        discordButton1Visible.value = value
        prefs.edit().putBoolean(KEY_DISCORD_BUTTON_1_VISIBLE, value).apply()
    }

    fun setDiscordButton2Text(value: String) {
        discordButton2Text.value = value
        prefs.edit().putString(KEY_DISCORD_BUTTON_2_TEXT, value).apply()
    }

    fun setDiscordButton2Visible(value: Boolean) {
        discordButton2Visible.value = value
        prefs.edit().putBoolean(KEY_DISCORD_BUTTON_2_VISIBLE, value).apply()
    }

    fun setDiscordInfoDismissed(value: Boolean) {
        discordInfoDismissed.value = value
        prefs.edit().putBoolean(KEY_DISCORD_INFO_DISMISSED, value).apply()
    }

    fun setReplayGenres(value: Boolean) {
        replayGenres.value = value
        prefs.edit().putBoolean(KEY_REPLAY_GENRES, value).apply()
    }

    fun setFilterNonMusicAudio(value: Boolean) {
        filterNonMusicAudio.value = value
        prefs.edit().putBoolean(KEY_FILTER_NON_MUSIC_AUDIO, value).apply()
    }

    fun setLocalMusicSort(value: LocalMusicSort) {
        localMusicSort.value = value
        prefs.edit().putString(KEY_LOCAL_MUSIC_SORT, value.name).apply()
    }

    fun setDownloadedMusicSort(value: LocalMusicSort) {
        downloadedMusicSort.value = value
        prefs.edit().putString(KEY_DOWNLOADED_MUSIC_SORT, value.name).apply()
    }

    fun setLibrarySort(value: LibrarySort) {
        librarySort.value = value
        prefs.edit().putString(KEY_LIBRARY_SORT, value.name).apply()
    }

    fun setLocalMusicViewType(value: LibraryViewType) {
        localMusicViewType.value = value
        prefs.edit().putString(KEY_LOCAL_MUSIC_VIEW_TYPE, value.name).apply()
    }

    fun setDownloadedMusicViewType(value: LibraryViewType) {
        downloadedMusicViewType.value = value
        prefs.edit().putString(KEY_DOWNLOADED_MUSIC_VIEW_TYPE, value.name).apply()
    }

    fun setLocalMusicFolderUri(value: String) {
        localMusicFolderUri.value = value
        prefs.edit().putString(KEY_LOCAL_MUSIC_FOLDER_URI, value).apply()
    }

    private fun readLocalMusicSort(key: String): LocalMusicSort =
        prefs.getString(key, null)
            ?.let { saved -> LocalMusicSort.entries.firstOrNull { it.name == saved } }
            ?: LocalMusicSort.TITLE_ASC

    private fun readLibraryViewType(key: String): LibraryViewType =
        prefs.getString(key, null)
            ?.let { saved -> LibraryViewType.entries.firstOrNull { it.name == saved } }
            ?: LibraryViewType.LIST

    /**
     * Pins or unpins [browseId], returning whether it is pinned afterwards.
     *
     * Pinning past [MAX_PINNED_PLAYLISTS] is refused rather than evicting the
     * oldest pin: a silent swap would mean a playlist someone pinned on purpose
     * disappears from the row without them ever having touched it, the moment
     * they pin a sixth. Unpinning always succeeds.
     */
    fun togglePinnedPlaylist(browseId: String): Boolean {
        val current = pinnedPlaylists.value
        val updated = when {
            browseId in current -> current - browseId
            current.size >= MAX_PINNED_PLAYLISTS -> return false
            else -> current + browseId
        }
        pinnedPlaylists.value = updated
        prefs.edit().putString(KEY_PINNED_PLAYLISTS, updated.joinToString(",")).apply()
        return browseId in updated
    }

    private fun readPinnedPlaylists(): List<String> {
        val stored = prefs.getString(KEY_PINNED_PLAYLISTS, null) ?: return emptyList()
        return stored.split(",").filter { it.isNotBlank() }
    }

    /** Forgets the account: token and cached profile. */
    fun clearDiscordAccount() {
        setDiscordToken("")
        setDiscordAccount("", "", null)
    }

    // ── Backup ──────────────────────────────────────────────────────────────

    /**
     * Every stored preference, for an export.
     *
     * Read off the preference file wholesale rather than assembled from the
     * flows above, so a setting added in a later build is in the backup the day
     * it is added instead of the day somebody remembers to list it here. What is
     * *left out* is therefore the part worth stating explicitly, and it is
     * [SECRETS]: an export is a file the user is about to put in Drive or a
     * chat, and a scrobbler session key or an API secret in it is a credential
     * that has left the device in plain text. Signing back in after a restore is
     * a minute; a leaked session key is not recoverable at all.
     *
     * The Discord token is not here for the same reason and one more: it never
     * reaches this file. It lives in the encrypted store — see [AuthStore] — and
     * so does the YouTube cookie, which means neither can be exported by
     * accident.
     */
    fun exportPrefs(): Map<String, Any?> {
        if (!this::prefs.isInitialized) return emptyMap()
        return prefs.all.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
    }

    /**
     * Replaces the preference file with [values] and re-reads it.
     *
     * A replace, not a merge: a partial restore leaves a device holding half of
     * one configuration and half of another, which is the one outcome nobody
     * asked for. Keys in [SECRETS] survive untouched — they were never in the
     * file being restored from, and clearing them would sign the user out of
     * services the backup has nothing to say about.
     */
    fun importPrefs(values: Map<String, Any?>) {
        if (!this::prefs.isInitialized) return
        val kept = prefs.all.filterKeys { it in SECRETS || it in DEVICE_LOCAL }
        prefs.edit().apply {
            clear()
            val incoming = values.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
            (kept + incoming).forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> Unit
                }
            }
        }.apply()
        reload()
    }

    /**
     * Preferences an export must not carry — credentials, not configuration.
     * See [exportPrefs].
     */
    private val SECRETS = setOf(
        KEY_LASTFM_SESSION_KEY,
        KEY_LASTFM_API_KEY,
        KEY_LASTFM_SECRET,
        KEY_LISTENBRAINZ_TOKEN,
        KEY_SPOTIFY_SPDC_TOKEN,
    )

    /**
     * Preferences that describe *this device* rather than this configuration,
     * and so are neither exported nor overwritten by an import.
     *
     * [Downloads][com.music.bitchord.download.Downloads] keeps its record of
     * what is saved in this same preference file, and that record is a list of
     * files on this phone's storage. Carrying it into a backup would restore a
     * folder full of tracks that are not here; clearing it on import would leave
     * the files on disk with nothing pointing at them, which is worse — the
     * Downloads page would read as empty while the space stayed used.
     */
    private val DEVICE_LOCAL = setOf(
        "downloaded_tracks",
        "downloaded_tracks_metadata",
        "downloaded_collections",
        KEY_LOCAL_MUSIC_FOLDER_URI,
        KEY_LAST_VERSION_CODE,
    )

    const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
    const val MAX_CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024

    private const val DEFAULT_PERFORMANCE_REFRESH_RATE = 120

    private fun normalizePerformanceRefreshRate(value: Int): Int =
        value.takeIf { it in 50..240 } ?: DEFAULT_PERFORMANCE_REFRESH_RATE

    private const val KEY_QUALITY_LEGACY = "audio_quality"
    private const val KEY_QUALITY_WIFI = "audio_quality_wifi"
    private const val KEY_QUALITY_CELLULAR = "audio_quality_cellular"
    private const val KEY_QUALITY_DOWNLOAD = "audio_quality_download"
    private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    private const val KEY_EXPORT_DOWNLOADS = "export_downloads"
    private const val KEY_LOSSLESS = "lossless_audio"
    private const val KEY_CROSSFADE = "crossfade_seconds"
    private const val KEY_SMART_FADE = "smart_fade_enabled"
    private const val KEY_AUTOMIX_PERFORMANCE_MODE = "automix_performance_mode"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_OUTPUT_PCM_MODE = "output_pcm_mode"
    private const val KEY_PREFER_USB_DAC = "prefer_usb_dac"
    private const val KEY_DOLBY_ATMOS = "dolby_atmos"
    private const val KEY_SPATIAL_AUDIO = "spatial_audio"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_NERD_STATS = "show_nerd_stats"
    private const val KEY_CACHE_LIMIT = "audio_cache_limit_bytes"
    private const val KEY_REDUCE_ANIMATION = "reduce_animation"
    private const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
    private const val KEY_PERFORMANCE_REFRESH_RATE = "performance_refresh_rate"
    private const val KEY_STOP_ON_TASK_REMOVED = "stop_on_task_removed"
    private const val KEY_HIDE_VOLUME_BAR = "hide_volume_bar"
    private const val KEY_SWIPE_TO_PLAY_NEXT = "swipe_to_play_next"
    private const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"
    private const val KEY_REDUCE_BLUR = "reduce_dynamic_blur"
    private const val KEY_LIQUID_GLASS = "liquid_glass"
    private const val KEY_LYRICS_BLUR = "lyrics_blur"
    private const val KEY_ANIMATED_CANVAS = "animated_canvas"
    private const val KEY_CANVAS_OVER_CELLULAR = "canvas_over_cellular"
    private const val KEY_FULL_BLEED_ARTWORK = "full_bleed_artwork"
    private const val KEY_LEGACY_MESH_GRADIENT = "legacy_mesh_gradient"
    private const val KEY_SYNCED_LYRICS = "synced_lyrics"
    private const val KEY_LYRICS_SOURCES = "lyrics_sources"
    private const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_order"
    private const val KEY_PRIORITIZE_SYLLABLE_SYNC = "prioritize_syllable_sync"
    private const val KEY_SHOW_LYRICS_LOGS = "show_lyrics_logs"
    private const val KEY_REPLAY_GENRES = "replay_genres"
    private const val KEY_FILTER_NON_MUSIC_AUDIO = "filter_non_music_audio"
    private const val KEY_LOCAL_MUSIC_SORT = "local_music_sort"
    private const val KEY_DOWNLOADED_MUSIC_SORT = "downloaded_music_sort"
    private const val KEY_LIBRARY_SORT = "library_sort"
    private const val KEY_LOCAL_MUSIC_VIEW_TYPE = "local_music_view_type"
    private const val KEY_DOWNLOADED_MUSIC_VIEW_TYPE = "downloaded_music_view_type"
    private const val KEY_LOCAL_MUSIC_FOLDER_URI = "local_music_folder_uri"
    private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"

    private const val KEY_LASTFM_ENABLED = "lastfm_enabled"
    private const val KEY_LASTFM_USERNAME = "lastfm_username"
    private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
    private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
    private const val KEY_LASTFM_SECRET = "lastfm_secret"
    private const val KEY_LASTFM_ENDPOINT = "lastfm_endpoint"
    private const val KEY_LASTFM_SCROBBLE_ENABLED = "lastfm_scrobble_enabled"
    private const val KEY_LASTFM_NOW_PLAYING = "lastfm_now_playing"
    private const val KEY_LASTFM_PRIMARY_ARTIST_ONLY = "lastfm_primary_artist_only"
    private const val KEY_SCROBBLE_MIN_DURATION = "scrobble_min_duration"
    private const val KEY_SCROBBLE_DELAY_PERCENT = "scrobble_delay_percent"
    private const val KEY_SCROBBLE_DELAY_SECONDS = "scrobble_delay_seconds"
    private const val KEY_LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
    private const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
    private const val KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY = "listenbrainz_primary_artist_only"
    private const val KEY_SPOTIFY_SPDC_TOKEN = "spotify_spdc_token"

    private const val KEY_DISCORD_USERNAME = "discord_username"
    private const val KEY_DISCORD_NAME = "discord_name"
    private const val KEY_DISCORD_AVATAR = "discord_avatar"
    private const val KEY_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
    private const val KEY_DISCORD_USE_DETAILS = "discord_use_details"
    private const val KEY_DISCORD_ADVANCED_MODE = "discord_advanced_mode"
    private const val KEY_DISCORD_STATUS = "discord_status"
    private const val KEY_DISCORD_ACTIVITY_TYPE = "discord_activity_type"
    private const val KEY_DISCORD_ACTIVITY_NAME = "discord_activity_name"
    private const val KEY_DISCORD_BUTTON_1_TEXT = "discord_button_1_text"
    private const val KEY_DISCORD_BUTTON_1_VISIBLE = "discord_button_1_visible"
    private const val KEY_DISCORD_BUTTON_2_TEXT = "discord_button_2_text"
    private const val KEY_DISCORD_BUTTON_2_VISIBLE = "discord_button_2_visible"
    private const val KEY_DISCORD_INFO_DISMISSED = "discord_info_dismissed"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
}

/**
 * Where one track stands in Automix's analysis.
 *
 * The three no-result states are kept apart because they call for different
 * reactions: [WAITING] resolves itself once bytes arrive, [ANALYSING] resolves
 * itself in a few seconds, and [FAILED] never resolves at all. From outside
 * they look identical, which is precisely why the line has to say which.
 */
enum class TrackAnalysisState {
    /** Nothing in flight and no result — usually waiting on bytes to arrive. */
    WAITING,

    /** Decode and inference running now; a result is a few seconds away. */
    ANALYSING,

    /** Measured, with a tempo the planner can actually use. */
    ANALYSED,

    /**
     * Measured off the track's opening, with the whole-track pass running now to
     * replace those numbers with better ones.
     *
     * Its own state rather than either neighbour, because it is genuinely both:
     * reporting [ANALYSING] made a track that was already usable look like it
     * had gone backwards, and reporting [ANALYSED] would hide that the cue and
     * the tempo are about to move.
     */
    REFINING,

    /**
     * Tried and came back with nothing usable — a decode error, or audio that
     * yielded no tempo. Distinct from [WAITING] because nothing further will
     * happen on its own: waiting is a matter of time, this is not.
     */
    FAILED,
}

/**
 * Both sides of the next transition, for stats for nerds.
 *
 * A transition needs *both* tracks measured before it can beat-match or cue the
 * incoming one into its arrangement, so reporting them separately is what makes
 * a plain crossfade explicable rather than mysterious.
 */
data class SmartAnalysis(
    val current: TrackAnalysisState = TrackAnalysisState.WAITING,
    val next: TrackAnalysisState = TrackAnalysisState.WAITING,
)

/**
 * A span of the playing track, in fractions of its duration, that the next
 * transition is planned to occupy.
 */
data class TransitionWindow(val start: Float, val end: Float)
