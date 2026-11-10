/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Last.fm returns `"track": { … }` (a single JSON object) instead of an array when
 * the list holds exactly one entry — sparse accounts, single scrobble days, one
 * top artist. Without this normalization the whole response fails to decode and
 * the dashboard renders empty. Mirrors LastWave's RecentTracksListSerializer.
 */
private fun singleObjectAsArray(element: JsonElement): JsonElement =
    when (element) {
        is JsonArray -> element
        is JsonNull -> JsonArray(emptyList())
        else -> JsonArray(listOf(element))
    }

object UserImageListSerializer : JsonTransformingSerializer<List<UserImage>>(ListSerializer(UserImage.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

object RecentTrackListSerializer : JsonTransformingSerializer<List<RecentTrack>>(ListSerializer(RecentTrack.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

object TopTrackListSerializer : JsonTransformingSerializer<List<TopTrack>>(ListSerializer(TopTrack.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

object TopArtistListSerializer : JsonTransformingSerializer<List<TopArtist>>(ListSerializer(TopArtist.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

object TopAlbumListSerializer : JsonTransformingSerializer<List<TopAlbum>>(ListSerializer(TopAlbum.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

object LastFmTagListSerializer : JsonTransformingSerializer<List<LastFmTag>>(ListSerializer(LastFmTag.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = singleObjectAsArray(element)
}

@Serializable
data class UserInfoResponse(
    val user: UserInfo,
)

@Serializable
data class UserInfo(

    val name: String? = null,
    val realname: String? = null,
    val url: String? = null,
    @Serializable(with = UserImageListSerializer::class) val image: List<UserImage>? = null,
    val country: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val subscriber: Int? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val playlists: Int? = null,
    val registered: UserRegistered? = null,
) {
    val playcount: Long? get() = _playcount?.toLongOrNull()
}

@Serializable
data class UserImage(
    @SerialName("#text") val text: String,
    val size: String? = null,
)

@Serializable
data class UserRegistered(
    val unixtime: String? = null,
    @SerialName("#text") val text: Int? = null,
)

@Serializable
data class RecentTracksResponse(
    val recenttracks: RecentTracks,
)

@Serializable
data class RecentTracks(
    @Serializable(with = RecentTrackListSerializer::class) val track: List<RecentTrack> = emptyList(),
    @SerialName("@attr") val attr: RecentTracksAttr? = null,
)

@Serializable
data class RecentTracksAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class RecentTrack(
    val artist: RecentTrackArtist? = null,
    val name: String? = null,
    val album: RecentTrackAlbum? = null,
    val url: String? = null,
    val date: RecentTrackDate? = null,
    @Serializable(with = UserImageListSerializer::class) val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: RecentTrackAttr? = null,
) {
    val isNowPlaying: Boolean get() = attr?.nowplaying == "true"
}

@Serializable
data class RecentTrackAttr(
    val nowplaying: String? = null,
)

@Serializable
data class RecentTrackArtist(
    @SerialName("#text") val text: String? = null,
    val mbid: String? = null,
)

@Serializable
data class RecentTrackAlbum(
    @SerialName("#text") val text: String? = null,
    val mbid: String? = null,
)

@Serializable
data class RecentTrackDate(
    val uts: String? = null,
    @SerialName("#text") val text: String? = null,
)

@Serializable
data class TopTracksResponse(
    val toptracks: TopTracks,
)

@Serializable
data class TopTracks(
    @Serializable(with = TopTrackListSerializer::class) val track: List<TopTrack> = emptyList(),
    @SerialName("@attr") val attr: TopTracksAttr? = null,
)

@Serializable
data class TopTracksAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopTrack(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val artist: RecentTrackArtist? = null,
    val url: String? = null,
    @Serializable(with = UserImageListSerializer::class) val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
    val rank: String? get() = attr?.rank
}

@Serializable
data class TopTrackAttr(
    val rank: String? = null,
)

@Serializable
data class TrackInfoResponse(
    val track: TrackInfo,
)

@Serializable
data class TrackInfo(
    val toptags: TopTags? = null,
)

@Serializable
data class TopTags(
    @Serializable(with = LastFmTagListSerializer::class) val tag: List<LastFmTag> = emptyList(),
)

@Serializable
data class LastFmTag(
    val name: String? = null,
)

@Serializable
data class TopArtistsResponse(
    val topartists: TopArtists,
)

@Serializable
data class TopArtists(
    @Serializable(with = TopArtistListSerializer::class) val artist: List<TopArtist> = emptyList(),
    @SerialName("@attr") val attr: TopArtistsAttr? = null,
)

@Serializable
data class TopArtistsAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopArtist(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val url: String? = null,
    @Serializable(with = UserImageListSerializer::class) val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
}

@Serializable
data class TopAlbumsResponse(
    val topalbums: TopAlbums,
)

@Serializable
data class TopAlbums(
    @Serializable(with = TopAlbumListSerializer::class) val album: List<TopAlbum> = emptyList(),
    @SerialName("@attr") val attr: TopAlbumsAttr? = null,
)

@Serializable
data class TopAlbumsAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopAlbum(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val artist: RecentTrackArtist? = null,
    val url: String? = null,
    @Serializable(with = UserImageListSerializer::class) val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
}

@Serializable
data class RawJson(
    val raw: JsonElement? = null,
)
