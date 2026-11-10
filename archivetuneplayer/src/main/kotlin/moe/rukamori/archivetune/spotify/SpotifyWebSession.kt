/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.utils.dataStore

object SpotifyWebSession {
    suspend fun ensureToken(context: Context): String? {
        val token = context.dataStore.data.first()[SpotifyAccessTokenKey].orEmpty()
        val expiry = context.dataStore.data.first()[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiry - 60_000L > System.currentTimeMillis()) {
            return token
        }
        val spDc = context.dataStore.data.first()[SpotifySpDcKey].orEmpty()
        if (spDc.isBlank()) return null
        val spKey = context.dataStore.data.first()[SpotifySpKeyKey].orEmpty()
        val fresh = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrNull() ?: return null
        runCatching {
            context.dataStore.edit {
                it[SpotifyAccessTokenKey] = fresh.accessToken
                it[SpotifyAccessTokenExpiresAtKey] = fresh.accessTokenExpirationTimestampMs
            }
        }
        return fresh.accessToken
    }
}
