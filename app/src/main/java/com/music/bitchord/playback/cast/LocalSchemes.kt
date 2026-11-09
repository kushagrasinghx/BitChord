package com.music.bitchord.playback.cast

import com.music.bitchord.data.sources.SourceTrackKeys

/**
 * URI schemes a receiver can never open, whatever the resolver makes of them.
 *
 * A receiver fetches an http(s) stream by itself. It cannot reach a file on the
 * phone, cannot be sent the headers a network share needs, and has no way of
 * resolving an address that only this app understands. These are the schemes that
 * mean the audio lives somewhere a receiver has no path to.
 *
 * What is deliberately *not* here is as important. `bitchord://` is how nearly
 * every track in the app is spelled — it names a source and a track id, and the
 * resolver turns it into a real stream before the receiver ever sees it. Asking
 * "is this http(s)?" instead of "is this local?" refuses every track the app
 * owns, which is what happened the first time round.
 */
internal val LOCAL_SCHEMES: Set<String> = setOf(
    "file",
    "content",
    "android.resource",
    "smb",
    "ftp",
    "webdav",
    "dav",
)

/**
 * Whether a receiver could ever open a track spelled with this scheme.
 *
 * Separate from the set so it can be asked without a URI, and so the question
 * itself is under test rather than only the list — the list being right is no
 * use if the test of it is the wrong way round. A track with no scheme at all
 * answers no: there is nothing for the receiver to fetch.
 */
internal fun receiverCanOpen(scheme: String?): Boolean = scheme != null && scheme !in LOCAL_SCHEMES

/**
 * Whether a track whose audio already sits on this phone can still reach a
 * receiver.
 *
 * A download is a copy of a stream, not a track of its own: the file is here,
 * but what it was downloaded from is still addressable, and a receiver fetching
 * that address plays the song it would have played had it never been
 * downloaded. So a local URI does not answer the question by itself — what it
 * asks instead is whether there is anywhere else the audio can be fetched, and
 * for a file the listener imported, a share on the local network or a resource
 * inside the apk there is not.
 *
 * Asked of the id, because that is where the answer lives: `PlayerConnection`
 * builds a downloaded item from the file's own URI with the track's id left
 * untouched beside it. Asked of the scheme as well, because a download is the
 * only thing a local URI can be standing in for — `smb` is not a copy of a
 * track, it is where the track is, and no address of ours goes behind it.
 */
internal fun castableTrack(scheme: String?, mediaId: String?): Boolean = when {
    receiverCanOpen(scheme) -> true
    scheme != "content" && scheme != "file" -> false
    else -> hasRemoteIdentity(mediaId)
}

/**
 * Whether [mediaId] names a track somewhere else rather than a file here.
 *
 * The two shapes a real track id takes: a YouTube id, and a `src:<configId>::<trackId>`
 * key for a module. Everything else — a `content://` path, a `file://` one, a
 * bare `/storage/…` — is the address of a file on this phone, and a file on
 * this phone is the one thing a receiver has no way of fetching.
 */
internal fun hasRemoteIdentity(mediaId: String?): Boolean = when {
    mediaId.isNullOrBlank() -> false
    mediaId.startsWith("content://") || mediaId.startsWith("file://") -> false
    mediaId.startsWith("/") -> false
    SourceTrackKeys.parse(mediaId) != null -> true
    else -> YOUTUBE_ID.matches(mediaId)
}

/**
 * The shape of a YouTube id: eleven characters of the alphabet those use.
 *
 * Checked rather than taken on trust, because the id is about to be pasted into
 * an address of ours — and an id that is itself a path would be pasted into one
 * too, which is how a track with no remote identity behind it ends up asked
 * for by a name that means nothing anywhere.
 */
private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
