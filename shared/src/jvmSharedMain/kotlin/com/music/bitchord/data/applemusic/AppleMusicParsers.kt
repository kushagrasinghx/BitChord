package com.music.bitchord.data.applemusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun parseTrackPage(root: JsonObject): AppleMusicPage<AppleMusicTrack> =
    parseTrackPage(root, songsOnly = false)

internal fun parseTrackPage(root: JsonObject, songsOnly: Boolean): AppleMusicPage<AppleMusicTrack> =
    parsePage(root) { parseTrack(it, songsOnly) }

internal fun parseSearchSongsPage(root: JsonObject): AppleMusicPage<AppleMusicTrack> {
    val results = root.obj("results")
        ?: throw IllegalStateException("Apple Music search response was invalid")
    val songs = results.obj("songs") ?: return AppleMusicPage(emptyList())
    return parseTrackPage(songs, songsOnly = true)
}

internal fun parsePlaylistPage(root: JsonObject): AppleMusicPage<AppleMusicPlaylist> =
    parsePage(root, ::parsePlaylist)

internal fun parseAlbumPage(root: JsonObject): AppleMusicPage<AppleMusicAlbum> =
    parsePage(root, ::parseAlbum)

internal fun parseArtistPage(root: JsonObject): AppleMusicPage<AppleMusicArtist> =
    parsePage(root, ::parseArtist)

internal fun parseRecentlyAddedPage(root: JsonObject): AppleMusicPage<AppleMusicLibraryItem> =
    parsePage(root) { resource ->
        when (resource.str("type")) {
            "library-songs", "songs", "library-music-videos", "music-videos" -> parseTrack(resource)
            "library-playlists", "playlists" -> parsePlaylist(resource)
            "library-albums", "albums" -> parseAlbum(resource)
            "library-artists", "artists" -> parseArtist(resource)
            else -> null
        }
    }

internal fun parseStorefront(root: JsonObject): String =
    root.arr("data").orEmpty().mapNotNull { element ->
        val resource = element as? JsonObject ?: return@mapNotNull null
        resource.str("id")?.takeIf { resource.str("type") == "storefronts" }
    }.firstOrNull() ?: throw IllegalStateException("Apple Music storefront was missing")

private fun <T> parsePage(root: JsonObject, parse: (JsonObject) -> T?): AppleMusicPage<T> {
    val data = root.arr("data") ?: throw IllegalStateException("Apple Music response data was missing")
    val next = root["next"]
    check(next == null || next is JsonNull || (next is JsonPrimitive && next.isString)) {
        "Apple Music pagination URL was invalid"
    }
    return AppleMusicPage(
        items = data.mapNotNull { (it as? JsonObject)?.let(parse) },
        next = root.str("next"),
    )
}

private fun parseTrack(resource: JsonObject, songsOnly: Boolean = false): AppleMusicTrack? {
    val type = resource.str("type") ?: return null
    val video = type == "music-videos" || type == "library-music-videos"
    if (type != "songs" && type != "library-songs" && !video) return null
    if (songsOnly && video) return null
    val id = resource.str("id") ?: return null
    val catalogType = if (video) "music-videos" else "songs"
    val catalog = resource.catalogResource(catalogType)
    val attributes = resource.obj("attributes")
    val catalogAttributes = catalog?.obj("attributes")
    // A library-only/unavailable resource still has an identity. Keep it even when
    // attributes, duration and playParams are missing; never turn its id into a catalog id.
    fun text(key: String) = attributes?.str(key) ?: catalogAttributes?.str(key)
    return AppleMusicTrack(
        id = id,
        catalogId = resource.catalogId(catalogType),
        title = text("name").orEmpty(),
        artist = text("artistName").orEmpty(),
        album = text("albumName"),
        durationMs = (attributes?.int("durationInMillis") ?: catalogAttributes?.int("durationInMillis") ?: 0)
            .coerceAtLeast(0),
        imageUrl = artworkUrl(attributes?.obj("artwork")) ?: artworkUrl(catalogAttributes?.obj("artwork")),
        isrc = text("isrc"),
        explicit = when (text("contentRating")) {
            "explicit" -> true
            "clean" -> false
            else -> null
        },
        isVideo = video,
    )
}

private fun parsePlaylist(resource: JsonObject): AppleMusicPlaylist? {
    if (resource.str("type") !in setOf("playlists", "library-playlists")) return null
    val id = resource.str("id") ?: return null
    val attributes = resource.obj("attributes")
    val description = attributes?.obj("description")
    return AppleMusicPlaylist(
        id = id,
        name = attributes?.str("name").orEmpty(),
        owner = attributes?.str("curatorName"),
        imageUrl = artworkUrl(attributes?.obj("artwork")),
        catalogId = resource.catalogId("playlists"),
        description = description?.str("standard") ?: description?.str("short"),
        canEdit = attributes?.bool("canEdit") ?: false,
        isFavorite = attributes?.bool("isFavorite"),
        playlistType = attributes?.str("playlistType"),
    )
}

private fun parseAlbum(resource: JsonObject): AppleMusicAlbum? {
    if (resource.str("type") !in setOf("albums", "library-albums")) return null
    val id = resource.str("id") ?: return null
    val attributes = resource.obj("attributes")
    return AppleMusicAlbum(
        id = id,
        name = attributes?.str("name").orEmpty(),
        artist = attributes?.str("artistName").orEmpty(),
        imageUrl = artworkUrl(attributes?.obj("artwork")),
        catalogId = resource.catalogId("albums"),
        trackCount = (attributes?.int("trackCount") ?: 0).coerceAtLeast(0),
        releaseDate = attributes?.str("releaseDate"),
    )
}

private fun parseArtist(resource: JsonObject): AppleMusicArtist? {
    if (resource.str("type") !in setOf("artists", "library-artists")) return null
    val id = resource.str("id") ?: return null
    val attributes = resource.obj("attributes")
    return AppleMusicArtist(
        id = id,
        name = attributes?.str("name").orEmpty(),
        imageUrl = artworkUrl(attributes?.obj("artwork")),
        catalogId = resource.catalogId("artists"),
    )
}

private fun JsonObject.catalogResource(catalogType: String): JsonObject? =
    obj("relationships")?.obj("catalog")?.arr("data").orEmpty()
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.str("type") == catalogType && it.str("id") != null }

private fun JsonObject.catalogId(catalogType: String): String? =
    if (str("type") == catalogType) str("id")
    else obj("attributes")?.obj("playParams")?.str("catalogId") ?: catalogResource(catalogType)?.str("id")

private fun artworkUrl(artwork: JsonObject?): String? = artwork?.str("url")
    ?.replace("{w}", "600")
    ?.replace("{h}", "600")
    ?.replace("{f}", "jpg")

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)
    ?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
