package com.music.bitchord.data.discord

import android.content.Context
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import java.util.Locale

/**
 * Publishes what's playing to Discord as a Rich Presence activity.
 *
 * This talks to Discord as a *user*, over the same gateway its own client
 * uses — there is no official API for a third-party app to set a user's
 * presence, so the token in [token] is the account's own bearer token and the
 * socket identifies itself as Discord Android (see [SuperProperties]). That is
 * the only way this feature can exist, and it is why the settings screen warns
 * about it before asking for a login.
 *
 * The presence Discord renders from one [updateSong] call:
 *
 * ```
 *   Listening to BitChord          <- activityName, or the app's own name
 *   ┌────┐  Song title             <- details
 *   │art │  Artist                 <- state
 *   └────┘  Hi-Res Lossless · FLAC · 4608 kbps · 24-bit · 96 kHz
 *           ▁▁▁▁▁▁ 1:04 / 3:47     <- from the timestamps
 *   [ Listen on YouTube Music ]    <- button 1
 *   [ Visit BitChord           ]   <- button 2
 * ```
 */
class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(
    token = token,
    os = "Android",
    browser = "Discord Android",
    device = android.os.Build.DEVICE,
    userAgent = SuperProperties.userAgent,
    superPropertiesBase64 = SuperProperties.superPropertiesBase64,
) {
    /**
     * Pushes [song] to Discord as the current activity.
     *
     * [currentPlaybackTimeMillis] and [durationMillis] are turned into a
     * start/end timestamp pair rather than a progress value, because Discord
     * counts the bar down on its own clock from those two instants. So a
     * presence set once stays correct for the rest of the track, and the only
     * reason to send another is that something about the track *changed* —
     * which is also why [playbackSpeed] has to be divided out of both: at 1.5x
     * the wall-clock time left is not the media time left, and a presence that
     * ignored it would finish its countdown while the song was still playing.
     */
    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        useDetails: Boolean = false,
        status: String = "online",
        button1Text: String = "",
        button1Visible: Boolean = true,
        button2Text: String = "",
        button2Visible: Boolean = true,
        activityType: String = "listening",
        activityName: String = "",
        audioQuality: String? = null,
    ) = runCatching {
        val currentTime = System.currentTimeMillis()

        val adjustedPlaybackTime = (currentPlaybackTimeMillis / playbackSpeed).toLong()
        val calculatedStartTime = currentTime - adjustedPlaybackTime

        val songTitleWithRate = if (playbackSpeed != 1.0f) {
            "${song.title} [${String.format(Locale.ROOT, "%.2fx", playbackSpeed)}]"
        } else {
            song.title
        }

        val remainingDuration = durationMillis - currentPlaybackTimeMillis
        val adjustedRemainingDuration = (remainingDuration / playbackSpeed).toLong()

        val buttonsList = mutableListOf<Pair<String, String>>()
        if (button1Visible) {
            val resolvedText = resolveVariables(
                button1Text.ifEmpty { DEFAULT_BUTTON_1 },
                song,
            )
            buttonsList.add(resolvedText to watchUrl(song))
        }
        if (button2Visible) {
            val resolvedText = resolveVariables(
                button2Text.ifEmpty { DEFAULT_BUTTON_2 },
                song,
            )
            buttonsList.add(resolvedText to PROJECT_URL)
        }

        val type = when (activityType) {
            "playing" -> Type.PLAYING
            "watching" -> Type.WATCHING
            "competing" -> Type.COMPETING
            else -> Type.LISTENING
        }

        val name = activityName.ifEmpty { appName() }

        setActivity(
            name = name,
            details = songTitleWithRate,
            state = song.artist,
            detailsUrl = watchUrl(song),
            // Asked for at a size Discord's own card actually draws — the row
            // thumbnail our lists use is 160px and reads soft blown up to the
            // 96dp sleeve in a presence card.
            //
            // A track with no artwork — or with artwork Discord can't reach,
            // which is anything that isn't an http URL — sends none, and Discord
            // draws the BitChord application's own icon in its place.
            largeImage = song.artworkAt(ART_PX)
                ?.takeIf { it.startsWith("http") }
                ?.let(RpcImage::ExternalImage),
            smallImage = null,
            // The card's optional third information line. Kept absent for
            // ordinary lossy streams so Discord only calls attention to a
            // measured premium format.
            largeText = audioQuality,
            smallText = null,
            buttons = if (buttonsList.isNotEmpty()) buttonsList else null,
            type = type,
            statusDisplayType = if (useDetails) StatusDisplayType.DETAILS else StatusDisplayType.STATE,
            since = currentTime,
            startTime = calculatedStartTime,
            endTime = currentTime + adjustedRemainingDuration,
            applicationId = DiscordApplication.ID,
            status = status,
        )
    }

    /**
     * The name Discord puts after "Listening to". Taken from the app's own
     * label so it tracks a rename, with the dev flavor's suffix dropped —
     * a side-by-side dev install should still look like bitchord to everyone
     * else on Discord.
     */
    private fun appName(): String =
        context.getString(R.string.app_name).removeSuffix(" Dev")

    companion object {
        const val PROJECT_URL = "https://github.com/kushagrasinghx/BitChord"

        const val DEFAULT_BUTTON_1 = "Listen on YouTube Music"
        const val DEFAULT_BUTTON_2 = "Visit BitChord"

        /** Discord draws the sleeve at roughly 96dp; 480px covers it on any density. */
        private const val ART_PX = 480

        fun watchUrl(song: Song): String =
            "https://music.youtube.com/watch?v=${song.videoId}"

        /**
         * Resolves template variables in text.
         * Supported: {song_name}, {artist_name}, {album_name}
         */
        fun resolveVariables(text: String, song: Song): String {
            return text
                .replace("{song_name}", song.title)
                .replace("{artist_name}", song.artist)
                .replace("{album_name}", song.albumName ?: "")
        }
    }
}
