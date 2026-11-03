package com.music.bitchord.data.applemusic

/**
 * Shared across Activity/controller recreation. All authorization commits and disconnects
 * use this gate. Existing token storage is unchanged. The identity check also rejects
 * externally replaced/cleared credentials; a disconnect while already signed out must
 * still use [disconnect], because a boolean connected flow cannot represent that event.
 */
internal class AppleMusicAuthSessionStore(
    private val readToken: () -> String?,
    private val save: (userToken: String, storefront: String, playbackStorefront: String?) -> Unit,
    private val clear: () -> Unit,
) : AppleMusicAuthSession {
    private val lock = Any()
    private var revision = 0L
    private var identity = readToken()

    override fun generation(): Long = synchronized(lock) {
        refresh()
        revision
    }

    override fun commit(expectedGeneration: Long, userToken: String, storefront: String, playbackStorefront: String?): Boolean = synchronized(lock) {
        refresh()
        if (revision != expectedGeneration) return@synchronized false
        require(validAuthToken(userToken)) { "Apple Music authorization returned an invalid credential" }
        require(storefront.matches(Regex("[a-z]{2}"))) { "Apple Music storefront was invalid" }
        save(userToken, storefront, playbackStorefront)
        identity = readToken()
        revision++
        true
    }

    override fun disconnect() = synchronized(lock) {
        revision++
        try {
            clear()
        } finally {
            identity = readToken()
        }
    }

    private fun refresh() {
        val current = readToken()
        if (current != identity) {
            identity = current
            revision++
        }
    }
}
