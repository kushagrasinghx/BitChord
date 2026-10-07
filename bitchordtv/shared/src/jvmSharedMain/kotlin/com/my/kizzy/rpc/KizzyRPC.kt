/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRPC.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.rpc

import com.my.kizzy.gateway.DiscordWebSocket
import com.my.kizzy.gateway.entities.presence.Activity
import com.my.kizzy.gateway.entities.presence.Assets
import com.my.kizzy.gateway.entities.presence.Metadata
import com.my.kizzy.gateway.entities.presence.Presence
import com.my.kizzy.gateway.entities.presence.Timestamps
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Modified by Zion Huang
 */
open class KizzyRPC(
    private val token: String,
    os: String = "Android",
    browser: String = "Discord Android",
    device: String = "Generic Android Device",
    private val userAgent: String = "Discord-Android/314013;RNA",
    private val superPropertiesBase64: String? = null,
) {
    private val discordWebSocket = DiscordWebSocket(token, os, browser, device)
    private val discordApiClient = HttpClient()

    fun closeRPC() {
        discordWebSocket.close()
        discordApiClient.close()
    }

    fun isRpcRunning(): Boolean {
        return discordWebSocket.isWebSocketConnected()
    }

    /**
     * Tells the gateway that conditions have changed and it is worth trying
     * again now — a network coming back, typically.
     *
     * Connects if nothing is connected, and cuts short a reconnect backoff that
     * has climbed while there was no network to reconnect over.
     */
    fun wakeUp() {
        discordWebSocket.connect()
        discordWebSocket.retryNow()
    }

    /**
     * Clears the card by publishing a presence with no activities.
     *
     * Routed through the socket's own clear rather than sent as a plain presence
     * so that it becomes the payload a later reconnect replays: otherwise the
     * last *song* stays queued, and a socket that dropped while paused comes
     * back advertising a track that stopped minutes ago.
     */
    open suspend fun close() {
        discordWebSocket.clearActivity()
    }

    suspend fun setActivity(
        name: String,
        state: String?,
        stateUrl: String? = null,
        details: String?,
        detailsUrl: String? = null,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String? = null,
        smallText: String? = null,
        buttons: List<Pair<String, String>>? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        type: Type = Type.LISTENING,
        statusDisplayType: StatusDisplayType = StatusDisplayType.NAME,
        streamUrl: String? = null,
        applicationId: String? = null,
        status: String? = "online",
        since: Long? = null,
    ) {
        // Kicked off before the artwork is mirrored below, so a handshake the
        // push is going to need anyway overlaps the round trip to Discord's CDN
        // instead of queueing behind it. A no-op when a session is already up.
        discordWebSocket.connect()
        discordWebSocket.retryNow()

        val resolveExternal: suspend (String) -> String? = { image ->
            if (applicationId.isNullOrBlank()) {
                null
            } else {
                fetchExternalAsset(
                    client = discordApiClient,
                    applicationId = applicationId,
                    token = token,
                    imageUrl = image,
                    userAgent = userAgent,
                    superPropertiesBase64 = superPropertiesBase64,
                )
            }
        }

        val presence = Presence(
            activities = listOf(
                Activity(
                    name = name,
                    state = state,
                    stateUrl = stateUrl,
                    details = details,
                    detailsUrl = detailsUrl,
                    type = type.value,
                    statusDisplayType = statusDisplayType.value,
                    timestamps = Timestamps(startTime, endTime),
                    assets = Assets(
                        largeImage = largeImage?.resolveImage(resolveExternal),
                        smallImage = smallImage?.resolveImage(resolveExternal),
                        largeText = largeText,
                        smallText = smallText,
                    ),
                    buttons = buttons?.map { it.first },
                    metadata = Metadata(buttonUrls = buttons?.map { it.second }),
                    applicationId = applicationId.takeIf { !buttons.isNullOrEmpty() },
                    url = streamUrl,
                ),
            ),
            afk = true,
            since = since,
            status = status ?: "online",
        )
        discordWebSocket.sendActivity(presence)
    }

    enum class Type(val value: Int) {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        COMPETING(5),
    }

    enum class StatusDisplayType(val value: Int) {
        NAME(0),
        STATE(1),
        DETAILS(2),
    }

    companion object {
        suspend fun getUserInfo(
            token: String,
            userAgent: String = "Discord-Android/314013;RNA",
            superPropertiesBase64: String? = null,
        ): Result<UserInfo> = runCatching {
            val client = HttpClient()
            val response = client.get("https://discord.com/api/v9/users/@me") {
                header("Authorization", token)
                header("User-Agent", userAgent)
                if (superPropertiesBase64 != null) {
                    header("X-Super-Properties", superPropertiesBase64)
                }
            }.bodyAsText()
            // kotlinx rather than org.json: this module is shared with the
            // desktop, which has no Android JSON on its classpath.
            val json = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response).jsonObject
            fun field(name: String): String? =
                json[name]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
            val id = field("id") ?: error("Discord returned no user id")
            val username = field("username") ?: error("Discord returned no username")
            val name = field("global_name") ?: username
            val avatarHash = field("avatar").orEmpty()
            val avatar = if (avatarHash.isNotEmpty()) {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
            } else {
                null
            }
            client.close()

            UserInfo(id, username, name, avatar)
        }
    }
}
