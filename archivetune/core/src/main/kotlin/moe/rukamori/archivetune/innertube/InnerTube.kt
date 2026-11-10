/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.Context
import moe.rukamori.archivetune.innertube.models.CreditsRow
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.ReturnYouTubeDislikeResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.*
import moe.rukamori.archivetune.innertube.models.response.NextResponse
import moe.rukamori.archivetune.innertube.proxy.RotatingProxySelector
import moe.rukamori.archivetune.innertube.utils.sha1
import moe.rukamori.archivetune.innertube.utils.youtubeLoginCookieValue
import okhttp3.Dns
import java.io.IOException
import java.net.Proxy
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
@OptIn(ExperimentalEncodingApi::class)
class InnerTube {
    private var httpClient = createClient()

    private companion object {
        const val HTTP_HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        const val HTTP_HEADER_CACHE_CONTROL = "Cache-Control"
        const val PLAYBACK_TELEMETRY_VER = "2"
        const val PLAYBACK_TELEMETRY_ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        const val PLAYBACK_TELEMETRY_CACHE_CONTROL = "no-cache"
    }

    var locale =
        YouTubeLocale(
            gl = Locale.getDefault().country,
            hl = Locale.getDefault().toLanguageTag(),
        )
    private val queueLocale = YouTubeLocale(gl = "US", hl = "en")

    @Volatile
    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value).normalized()
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value).normalized()
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value).normalized()
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value).normalized()
        }

    var proxy: Proxy? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var proxyUsername: String? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var proxyPassword: String? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var dns: Dns = Dns.SYSTEM
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    /**
     * Rotating-proxy selector installed by [moe.rukamori.archivetune.innertube.YouTube.enableIpRotation].
     * Non-null means every InnerTube request goes out through the next live proxy in the pool.
     * Setting it rebuilds the HTTP client, because OkHttp's proxy selector is fixed at build time.
     */
    internal var proxySelector: RotatingProxySelector? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var useLoginForBrowse: Boolean = false

    fun currentAuthState(): PlaybackAuthState = authState

    fun applyAuthState(value: PlaybackAuthState) {
        authState = value.normalized()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() =
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            engine {
                config {
                    dns(this@InnerTube.dns)
                    val sel = this@InnerTube.proxySelector
                    if (sel != null) {
                        // The selector picks (and rotates) the proxy per request, so it must take
                        // precedence over the single static `proxy` below.
                        proxySelector(sel)
                    } else if (this@InnerTube.proxy == null) {
                        proxy(Proxy.NO_PROXY)
                    } else if (this@InnerTube.proxy != null && !proxyUsername.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                        proxyAuthenticator { _, response ->
                            val credential = okhttp3.Credentials.basic(proxyUsername!!, proxyPassword!!)
                            response.request
                                .newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    }
                }
                if (this@InnerTube.proxySelector == null && this@InnerTube.proxy != null) {
                    proxy = this@InnerTube.proxy
                }
            }

            defaultRequest {
                url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            }
        }

    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        authState: PlaybackAuthState = currentAuthState(),
        includeVisitorData: Boolean = true,
    ) {
        val requestOrigin = client.requestOrigin()
        val requestReferer = client.requestReferer()
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", requestOrigin)
            append("Referer", requestReferer)
            if (includeVisitorData) {
                authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            }
            // Two mutually exclusive auth schemes. Cookie + SAPISIDHASH is the WEB path; Bearer is
            // the OAuth device-code path used by the ANDROID_VR clients, which ignore cookies.
            // Sending both would be wrong, so this is an if/else on the client's own capability
            // rather than a fallthrough — and never an early `return`, which would skip the
            // request-body context assembled after this block.
            if (setLogin && client.supportsOAuth2Authentication) {
                authState.oauthToken?.let { token ->
                    append("Authorization", "Bearer $token")
                    append("X-Goog-AuthUser", "0")
                }
            } else if (setLogin && client.supportsCookieAuthentication) {
                authState.cookie?.let { cookie ->
                    append("cookie", cookie)
                    val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                    append("X-Goog-AuthUser", "0")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    private fun HttpRequestBuilder.ytPlaybackTrackingClient(
        client: YouTubeClient,
        authState: PlaybackAuthState = currentAuthState(),
    ) {
        val requestOrigin = client.requestOrigin()
        contentType(ContentType.Application.Json)
        headers {
            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
            append(HTTP_HEADER_ACCEPT_LANGUAGE, PLAYBACK_TELEMETRY_ACCEPT_LANGUAGE)
            append(HTTP_HEADER_CACHE_CONTROL, PLAYBACK_TELEMETRY_CACHE_CONTROL)
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", requestOrigin)
            append("Referer", client.requestReferer())
            authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            // Same two-scheme split as ytClient(): playback tracking pings have to carry the same
            // identity as the player request that produced them, or the view is attributed to a
            // signed-out session.
            if (client.supportsOAuth2Authentication) {
                authState.oauthToken?.let { token ->
                    append("Authorization", "Bearer $token")
                    append("X-Goog-AuthUser", "0")
                }
            } else if (client.supportsCookieAuthentication) {
                authState.cookie?.let { cookie ->
                    append("cookie", cookie)
                    val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                    append("X-Goog-AuthUser", "0")
                }
            }
        }
        userAgent(client.userAgent)
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        // With rotation on, allow one attempt per live proxy (capped) so a transient failure
        // moves to the next IP instead of giving up after the default attempt budget.
        val resolvedMaxAttempts = proxySelector?.activeCount()?.coerceIn(maxAttempts, 6) ?: maxAttempts
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException || !e.isTransientNetworkFailure()) throw e
                attempt++
                proxySelector?.markLastSelectedFailed()
                proxySelector?.rotate()
                if (attempt >= resolvedMaxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException || current is HttpRequestTimeoutException) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        useAccountContext: Boolean = true,
    ) = withRetry {
        httpClient.post("search") {
            ytClient(
                client = client,
                setLogin = useAccountContext && useLoginForBrowse,
                includeVisitorData = useAccountContext,
            )
            setBody(
                SearchBody(
                    context =
                        client.toContext(
                            locale,
                            if (useAccountContext) visitorData else null,
                            if (useAccountContext && useLoginForBrowse) dataSyncId else null,
                        ),
                    query = query,
                    params = params,
                ),
            )
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
        }
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
        cpn: String? = null,
    ) = withRetry {
        val includeDataSyncId = setLogin && client.supportsCookieAuthentication && authState.hasPlaybackLoginContext
        try {
            executePlayerRequest(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = setLogin,
                authState = authState,
                includeDataSyncId = includeDataSyncId,
                cpn = cpn,
            )
        } catch (failure: Throwable) {
            if (!shouldRetryPlayerRequestWithoutDataSyncId(failure, includeDataSyncId)) throw failure
            executePlayerRequest(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = setLogin,
                authState = authState,
                includeDataSyncId = false,
                cpn = cpn,
            )
        }
    }

    private suspend fun executePlayerRequest(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String?,
        setLogin: Boolean,
        authState: PlaybackAuthState,
        includeDataSyncId: Boolean,
        cpn: String? = null,
    ) = httpClient.post(client.requestApiUrl("player")) {
        ytClient(client = client, setLogin = setLogin, authState = authState)
        setBody(
            PlayerBody(
                context =
                    client
                        .toContext(
                            locale = locale,
                            visitorData = authState.visitorData,
                            dataSyncId = if (includeDataSyncId) authState.dataSyncId else null,
                        ).let {
                            if (client.isEmbedded) {
                                it.copy(
                                    thirdParty =
                                        Context.ThirdParty(
                                            embedUrl = "https://www.youtube.com/watch?v=$videoId",
                                        ),
                                )
                            } else {
                                it
                            }
                        },
                videoId = videoId,
                playlistId = playlistId,
                cpn = cpn,
                playbackContext =
                    if (client.useSignatureTimestamp) {
                        PlayerBody.PlaybackContext(
                            PlayerBody.PlaybackContext.ContentPlaybackContext(
                                signatureTimestamp,
                            ),
                        )
                    } else {
                        null
                    },
                serviceIntegrityDimensions =
                    poToken?.let {
                        PlayerBody.ServiceIntegrityDimensions(poToken = it)
                    },
            ),
        )
    }

    private fun shouldRetryPlayerRequestWithoutDataSyncId(
        failure: Throwable,
        includeDataSyncId: Boolean,
    ): Boolean {
        if (!includeDataSyncId) return false
        val clientError = failure as? ClientRequestException ?: return false
        if (clientError.response.status != HttpStatusCode.BadRequest) return false
        val message = clientError.message.orEmpty()
        if (!message.contains("/youtubei/v1/player", ignoreCase = true)) return false
        if (message.contains("Origin doesn't match Host", ignoreCase = true)) return false
        return message.contains("INVALID_ARGUMENT", ignoreCase = true) ||
            message.contains("invalid argument", ignoreCase = true)
    }

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState = currentAuthState(),
    ) = withRetry {
        httpClient.get(url) {
            ytPlaybackTrackingClient(client, authState = authState)
            parameter("ver", PLAYBACK_TELEMETRY_VER)
            parameter("c", client.clientName)
            parameter("cpn", cpn)
            parameter("prettyPrint", false)

            if (playlistId != null) {
                parameter("list", playlistId)
                parameter("referrer", "https://music.youtube.com/playlist?list=$playlistId")
            }
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
        useAccountContext: Boolean = true,
    ) = withRetry {
        httpClient.post("browse") {
            // useAccountContext == false strips every trace of the session (cookie, dataSyncId and
            // visitorData) so YouTube has nothing to derive a country from but the `gl` in `locale`.
            // That is what makes the region spoofer actually move the personalised surfaces.
            val shouldUseLogin = useAccountContext && (setLogin || useLoginForBrowse)
            ytClient(
                client = client,
                setLogin = shouldUseLogin,
                includeVisitorData = useAccountContext,
            )
            setBody(
                BrowseBody(
                    context =
                        client.toContext(
                            locale,
                            if (useAccountContext) visitorData else null,
                            if (shouldUseLogin) dataSyncId else null,
                        ),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = withRetry {
        httpClient.post("next") {
            ytClient(client, setLogin = true)
            setBody(
                NextBody(
                    context = client.toContext(queueLocale, visitorData, dataSyncId),
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = withRetry {
        httpClient.post("music/get_search_suggestions") {
            ytClient(client)
            setBody(
                GetSearchSuggestionsBody(
                    context = client.toContext(locale, visitorData, null),
                    input = input,
                ),
            )
        }
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = withRetry {
        httpClient.post("music/get_queue") {
            ytClient(client)
            setBody(
                GetQueueBody(
                    context = client.toContext(locale, visitorData, null),
                    videoIds = videoIds,
                    playlistId = playlistId,
                ),
            )
        }
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = withRetry {
        httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
            headers {
                append("Content-Type", "application/json")
            }
            setBody(
                GetTranscriptBody(
                    context = client.toContext(locale, null, null),
                    params =
                        Base64.Default.encode(
                            "\n${11.toChar()}$videoId".encodeToByteArray(),
                        ),
                ),
            )
        }
    }

    suspend fun getSwJsData() = withRetry { httpClient.get("https://music.youtube.com/sw.js_data") }

    suspend fun accountMenu(client: YouTubeClient) =
        withRetry {
            httpClient.post("account/account_menu") {
                ytClient(client, setLogin = true)
                setBody(AccountMenuBody(client.toContext(locale, visitorData, dataSyncId)))
            }
        }

    suspend fun accountChannels(client: YouTubeClient) =
        withRetry {
            httpClient.post("account/accounts_list") {
                ytClient(client, setLogin = true)
                setBody(AccountsListBody(client.toContext(locale, visitorData, dataSyncId)))
            }
        }

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = withRetry {
        httpClient.post("like/like") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.VideoTarget(videoId),
                ),
            )
        }
    }

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = withRetry {
        httpClient.post("like/removelike") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.VideoTarget(videoId),
                ),
            )
        }
    }

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
    ) = withRetry {
        httpClient.post("subscription/subscribe") {
            ytClient(client, setLogin = true)
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
    ) = withRetry {
        httpClient.post("subscription/unsubscribe") {
            ytClient(client, setLogin = true)
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetry {
        httpClient.post("like/like") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetry {
        httpClient.post("like/removelike") {
            ytClient(client, setLogin = true)
            setBody(
                LikeBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.AddVideoAction(addedVideoId = videoId),
                        ),
                ),
            )
        }
    }

    suspend fun addSongsToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoIds: List<String>,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        videoIds.map { videoId ->
                            Action.AddVideoAction(addedVideoId = videoId)
                        },
                ),
            )
        }
    }

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.AddPlaylistAction(addedFullListId = addPlaylistId),
                        ),
                ),
            )
        }
    }

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId.removePrefix("VL"),
                    actions =
                        listOf(
                            Action.RemoveVideoAction(
                                removedVideoId = videoId,
                                setVideoId = setVideoId,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions =
                        listOf(
                            Action.MoveVideoAction(
                                movedSetVideoIdSuccessor = successorSetVideoId,
                                setVideoId = setVideoId,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
        videoIds: List<String> = emptyList(),
    ) = withRetry {
        httpClient.post("playlist/create") {
            ytClient(client, true)
            setBody(
                CreatePlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    title = title,
                    videoIds = videoIds,
                ),
            )
        }
    }

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions =
                        listOf(
                            Action.RenamePlaylistAction(
                                playlistName = name,
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun startPlaylistCoverUpload(
        client: YouTubeClient,
        contentLength: Int,
    ) = withRetry {
        httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
            ytClient(client, setLogin = true)
            headers {
                append("X-Goog-Upload-Command", "start")
                append("X-Goog-Upload-Protocol", "resumable")
                append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
            }
        }
    }

    suspend fun uploadPlaylistCover(
        client: YouTubeClient,
        uploadId: String,
        image: ByteArray,
    ) = withRetry {
        httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
            ytClient(client, setLogin = true)
            parameter("upload_id", uploadId)
            parameter("upload_protocol", "resumable")
            headers {
                append("X-Goog-Upload-Command", "upload, finalize")
                append("X-Goog-Upload-Offset", "0")
            }
            contentType(ContentType.Application.OctetStream)
            setBody(image)
        }
    }

    suspend fun setPlaylistCustomCover(
        client: YouTubeClient,
        playlistId: String,
        encryptedBlobId: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions =
                        listOf(
                            Action.SetCustomThumbnailAction(
                                addedCustomThumbnail =
                                    Action.SetCustomThumbnailAction.AddedCustomThumbnail(
                                        playlistScottyEncryptedBlobId = encryptedBlobId,
                                    ),
                            ),
                        ),
                ),
            )
        }
    }

    suspend fun removePlaylistCustomCover(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetry {
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(
                EditPlaylistBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                    actions = listOf(Action.RemoveCustomThumbnailAction()),
                ),
            )
        }
    }

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = withRetry {
        httpClient.post("playlist/delete") {
            println("deleting $playlistId")
            ytClient(client, setLogin = true)
            setBody(
                PlaylistDeleteBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    playlistId = playlistId,
                ),
            )
        }
    }

    private suspend fun returnYouTubeDislike(videoId: String) =
        withRetry {
            httpClient.get("https://returnyoutubedislikeapi.com/Votes?videoId=$videoId") {
                contentType(ContentType.Application.Json)
            }
        }

    /**
     * Lightweight like-count lookup (user request 2026-09-03: TikTok-style
     * like label in the player). Fetches ONLY the ReturnYouTubeDislike votes
     * payload — a single cheap GET — instead of the full `next` + RYD pair
     * that [getMediaInfo] performs, so it is safe to call on every song
     * change in the player.
     *
     * @return the like count, or null when RYD has no data for this video.
     */
    suspend fun getLikeCount(videoId: String): Result<Int?> =
        runCatching {
            returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>().likes
        }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            val response = next(client = YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

            val baseForInfo =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoSecondaryInfoRenderer != null
                    }?.videoSecondaryInfoRenderer

            val baseForTitle =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find {
                        it?.videoPrimaryInfoRenderer != null
                    }?.videoPrimaryInfoRenderer

            val returnYouTubeDislikeResponse =
                returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>()

            // YouTube's own song credits: the structured-description panel's
            // music attribution cards each carry a "Song credits" dialog
            // (overflow menu) with "Label: value" blocks separated by blank
            // lines - Song / Artist / Album / Writers / Licensed to YouTube
            // by / Produced by / Released, exactly the rows YouTube shows in
            // its own track information sheet.
            val credits =
                response.engagementPanels
                    ?.asSequence()
                    ?.mapNotNull { it.engagementPanelSectionListRenderer }
                    ?.firstOrNull { it.panelIdentifier == "engagement-panel-structured-description" }
                    ?.content
                    ?.structuredDescriptionContentRenderer
                    ?.items
                    ?.asSequence()
                    ?.mapNotNull { it.horizontalCardListRenderer }
                    ?.flatMap { it.cards.orEmpty() }
                    ?.mapNotNull { it.videoAttributeViewModel }
                    ?.mapNotNull { viewModel ->
                        val dialogText =
                            viewModel.overflowMenuOnTap
                                ?.innertubeCommand
                                ?.confirmDialogEndpoint
                                ?.content
                                ?.confirmDialogRenderer
                                ?.dialogMessages
                                .orEmpty()
                                .flatMap { it.runs.orEmpty() }
                                .joinToString("") { run -> run.text }
                        dialogText.takeIf { it.isNotBlank() }
                    }
                    ?.flatMap { dialogText -> parseCreditRows(dialogText) }
                    ?.toList()
                    ?.takeIf { it.isNotEmpty() }

            return@runCatching MediaInfo(
                videoId = videoId,
                title =
                    baseForTitle
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                author =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                authorId =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.navigationEndpoint
                        ?.browseEndpoint
                        ?.browseId,
                authorThumbnail =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.thumbnail
                        ?.thumbnails
                        ?.find {
                            it.height == 48
                        }?.url
                        ?.replace("s48", "s960"),
                description = baseForInfo?.attributedDescription?.content,
                subscribers =
                    baseForInfo
                        ?.owner
                        ?.videoOwnerRenderer
                        ?.subscriberCountText
                        ?.simpleText
                        ?.split(" ")
                        ?.firstOrNull(),
                uploadDate = baseForTitle?.dateText?.simpleText,
                viewCount = returnYouTubeDislikeResponse.viewCount,
                like = returnYouTubeDislikeResponse.likes,
                dislike = returnYouTubeDislikeResponse.dislikes,
                credits = credits,
            )
        }

    private fun parseCreditRows(dialogText: String): List<CreditsRow> =
        dialogText
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { block ->
                val separator = block.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val label = block.substring(0, separator).trim()
                val value = block.substring(separator + 1).trim()
                if (label.isEmpty() || value.isEmpty()) return@mapNotNull null
                CreditsRow(label = label, value = value)
            }
}
