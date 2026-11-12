package com.music.bitchord.data.discord

/**
 * BitChord's own Discord application, shared by the phone and desktop presence.
 *
 * Discord reads more off this id than the code suggests. The local socket on
 * the desktop titles the activity with the application's registered name, some
 * Discord surfaces do the same for a gateway activity that carries it, and an
 * activity with no `large_image` shows the application's icon. Both come from
 * the developer portal entry, so the name there has to stay "BitChord".
 *
 * It replaced Metrolist's id, inherited along with the Kizzy port, which made
 * presences read "Listening to Metrolist" wherever Discord preferred the
 * application's name to the activity's.
 */
object DiscordApplication {
    const val ID = "1543679166967193620"
}
