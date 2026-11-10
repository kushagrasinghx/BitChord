package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class DeezerAudioQuality {
    FLAC,
    MP3_320,
    MP3_128,
}

val DeezerAudioQualityOptions =
    listOf(
        DeezerAudioQuality.FLAC,
        DeezerAudioQuality.MP3_320,
        DeezerAudioQuality.MP3_128,
    )

val DeezerAudioQualityKey = stringPreferencesKey("deezerAudioQuality")

fun DeezerAudioQuality.toFormatName(): String =
    when (this) {
        DeezerAudioQuality.FLAC -> "FLAC"
        DeezerAudioQuality.MP3_320 -> "MP3_320"
        DeezerAudioQuality.MP3_128 -> "MP3_128"
    }

val ListenTogetherServerUrlKey = stringPreferencesKey("listenTogetherServerUrl")
val ListenTogetherUserIdKey = stringPreferencesKey("listenTogetherUserId")
val ListenTogetherRoomCodeKey = stringPreferencesKey("listenTogetherRoomCode")
val ListenTogetherSessionTokenKey = stringPreferencesKey("listenTogetherSessionToken")
val ListenTogetherSessionTimestampKey = longPreferencesKey("listenTogetherSessionTimestamp")
val ListenTogetherIsHostKey = booleanPreferencesKey("listenTogetherIsHost")
val ListenTogetherAvatarIndexKey = intPreferencesKey("listenTogetherAvatarIndex")
val ListenTogetherAutoApprovalKey = booleanPreferencesKey("listenTogetherAutoApproval")
val ListenTogetherSuggestionAutoApproveKey = booleanPreferencesKey("listenTogetherSuggestionAutoApprove")
val ListenTogetherSyncVolumeKey = booleanPreferencesKey("listenTogetherSyncVolume")
val ListenTogetherBlockedUsersKey = stringPreferencesKey("listenTogetherBlockedUsers")
val ListenTogetherChatNotificationsKey = booleanPreferencesKey("listenTogetherChatNotifications")
val ListenTogetherChatHistoryKey = stringPreferencesKey("listenTogetherChatHistory")

val ListenTogetherResyncKey = booleanPreferencesKey("listenTogetherResync")

val ListenTogetherRoomNamesKey = stringPreferencesKey("listenTogetherRoomNames")

val ListenTogetherInAppNotificationsKey = booleanPreferencesKey("listenTogetherInAppNotifications")

val ListenTogetherChatMutedKey = booleanPreferencesKey("listenTogetherChatMuted")

val ListenTogetherChatWallpaperKey = stringPreferencesKey("listenTogetherChatWallpaper")

val ListenTogetherUsernameKey = stringPreferencesKey("listenTogetherUsername")
val ListenTogetherInTopBarKey = booleanPreferencesKey("listenTogetherInTopBar")

val TikTokMainLyricsEnabledKey = booleanPreferencesKey("tiktokMainLyricsEnabled")
