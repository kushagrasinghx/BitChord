package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What is signed in to Last.fm and ListenBrainz, and how listens are timed.
 *
 * Keys and defaults are Android's, from `AppSettings`. Held outside the
 * composition because the scrobbler reads them from a background coroutine and
 * Settings writes them.
 */
internal object DesktopScrobbleSettings {

    private val persistence = DesktopPersistence()

    private fun flag(key: String, default: Boolean) =
        MutableStateFlow(persistence.boolean(key, default))

    private fun text(key: String) = MutableStateFlow(persistence.string(key, ""))

    private val _lastfmEnabled = flag(KEY_LASTFM_ENABLED, false)
    private val _lastfmUsername = text(KEY_LASTFM_USERNAME)
    private val _lastfmSessionKey = text(KEY_LASTFM_SESSION_KEY)
    private val _lastfmScrobble = flag(KEY_LASTFM_SCROBBLE, true)
    private val _lastfmNowPlaying = flag(KEY_LASTFM_NOW_PLAYING, true)
    private val _lastfmPrimaryArtistOnly = flag(KEY_LASTFM_PRIMARY_ARTIST, false)

    private val _listenBrainzEnabled = flag(KEY_LISTENBRAINZ_ENABLED, false)
    private val _listenBrainzToken = text(KEY_LISTENBRAINZ_TOKEN)
    private val _listenBrainzPrimaryArtistOnly = flag(KEY_LISTENBRAINZ_PRIMARY_ARTIST, false)

    private val _minDuration = MutableStateFlow(persistence.int(KEY_MIN_DURATION, DEFAULT_MIN_DURATION))
    private val _delayPercent = MutableStateFlow(persistence.float(KEY_DELAY_PERCENT, DEFAULT_DELAY_PERCENT))
    private val _delaySeconds = MutableStateFlow(persistence.int(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS))

    val lastfmEnabled: StateFlow<Boolean> = _lastfmEnabled
    val lastfmUsername: StateFlow<String> = _lastfmUsername
    val lastfmSessionKey: StateFlow<String> = _lastfmSessionKey
    val lastfmScrobble: StateFlow<Boolean> = _lastfmScrobble
    val lastfmNowPlaying: StateFlow<Boolean> = _lastfmNowPlaying
    val lastfmPrimaryArtistOnly: StateFlow<Boolean> = _lastfmPrimaryArtistOnly

    val listenBrainzEnabled: StateFlow<Boolean> = _listenBrainzEnabled
    val listenBrainzToken: StateFlow<String> = _listenBrainzToken
    val listenBrainzPrimaryArtistOnly: StateFlow<Boolean> = _listenBrainzPrimaryArtistOnly

    val minDuration: StateFlow<Int> = _minDuration
    val delayPercent: StateFlow<Float> = _delayPercent
    val delaySeconds: StateFlow<Int> = _delaySeconds

    fun setLastfmEnabled(value: Boolean) = write(_lastfmEnabled, KEY_LASTFM_ENABLED, value)
    fun setLastfmScrobble(value: Boolean) = write(_lastfmScrobble, KEY_LASTFM_SCROBBLE, value)
    fun setLastfmNowPlaying(value: Boolean) = write(_lastfmNowPlaying, KEY_LASTFM_NOW_PLAYING, value)
    fun setLastfmPrimaryArtistOnly(value: Boolean) =
        write(_lastfmPrimaryArtistOnly, KEY_LASTFM_PRIMARY_ARTIST, value)

    fun setListenBrainzEnabled(value: Boolean) = write(_listenBrainzEnabled, KEY_LISTENBRAINZ_ENABLED, value)
    fun setListenBrainzPrimaryArtistOnly(value: Boolean) =
        write(_listenBrainzPrimaryArtistOnly, KEY_LISTENBRAINZ_PRIMARY_ARTIST, value)

    fun setListenBrainzToken(value: String) {
        persistence.saveString(KEY_LISTENBRAINZ_TOKEN, value)
        _listenBrainzToken.value = value
    }

    fun setMinDuration(value: Int) {
        persistence.saveInt(KEY_MIN_DURATION, value)
        _minDuration.value = value
    }

    fun setDelayPercent(value: Float) {
        persistence.saveFloat(KEY_DELAY_PERCENT, value)
        _delayPercent.value = value
    }

    fun setDelaySeconds(value: Int) {
        persistence.saveInt(KEY_DELAY_SECONDS, value)
        _delaySeconds.value = value
    }

    /** Records a successful sign-in and turns scrobbling on, as Android does. */
    fun signedInToLastfm(username: String, sessionKey: String) {
        persistence.saveString(KEY_LASTFM_USERNAME, username)
        persistence.saveString(KEY_LASTFM_SESSION_KEY, sessionKey)
        _lastfmUsername.value = username
        _lastfmSessionKey.value = sessionKey
        setLastfmEnabled(true)
    }

    /** Clears the session and everything that depended on it. */
    fun signOutOfLastfm() {
        persistence.saveString(KEY_LASTFM_USERNAME, "")
        persistence.saveString(KEY_LASTFM_SESSION_KEY, "")
        _lastfmUsername.value = ""
        _lastfmSessionKey.value = ""
        setLastfmEnabled(false)
        setLastfmScrobble(false)
        setLastfmNowPlaying(false)
    }

    private fun write(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        persistence.saveBoolean(key, value)
        flow.value = value
    }

    private const val KEY_LASTFM_ENABLED = "lastfm_enabled"
    private const val KEY_LASTFM_USERNAME = "lastfm_username"
    private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
    private const val KEY_LASTFM_SCROBBLE = "lastfm_scrobble_enabled"
    private const val KEY_LASTFM_NOW_PLAYING = "lastfm_now_playing"
    private const val KEY_LASTFM_PRIMARY_ARTIST = "lastfm_primary_artist_only"
    private const val KEY_LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
    private const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
    private const val KEY_LISTENBRAINZ_PRIMARY_ARTIST = "listenbrainz_primary_artist_only"
    private const val KEY_MIN_DURATION = "scrobble_min_duration"
    private const val KEY_DELAY_PERCENT = "scrobble_delay_percent"
    private const val KEY_DELAY_SECONDS = "scrobble_delay_seconds"

    const val DEFAULT_MIN_DURATION = 30
    const val DEFAULT_DELAY_PERCENT = 0.5f
    const val DEFAULT_DELAY_SECONDS = 180
}

/**
 * The first credited artist, for services that handle joint credits badly.
 *
 * The original is kept when there is no separator, so "AC/DC" stays intact.
 */
internal fun String.primaryArtist(): String =
    split(PRIMARY_ARTIST_SEPARATOR, limit = 2).first().trim().ifBlank { this }

private val PRIMARY_ARTIST_SEPARATOR = Regex("""\s*,\s*|\s+&\s+|\s+＆\s+""")
