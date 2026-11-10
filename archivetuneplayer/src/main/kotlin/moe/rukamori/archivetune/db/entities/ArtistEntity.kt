/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import org.apache.commons.lang3.RandomStringUtils
import timber.log.Timber
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val blockedAt: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun localToggleLike() =
        copy(
            bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
        )

    fun toggleLike() =
        localToggleLike().also {
            if (isLocal) return@also
            CoroutineScope(Dispatchers.IO).launch {
                val targetChannelId =
                    channelId ?: run {
                        val resolved = YouTube.getChannelId(id)
                        if (resolved.isBlank()) null else resolved
                    }
                if (targetChannelId == null) {
                    Timber.tag("ArtistEntity")
                        .w("Subscribe skipped for %s: no channelId could be resolved", id)
                    return@launch
                }
                YouTube
                    .subscribeChannel(targetChannelId, bookmarkedAt == null)
                    .onFailure { throwable ->
                        Timber.tag("ArtistEntity")
                            .w(throwable, "Subscribe failed for %s (channel %s)", id, targetChannelId)
                    }
            }
        }

    companion object {
        fun generateArtistId() = "LA" + RandomStringUtils.insecure().next(8, true, false)
    }
}
