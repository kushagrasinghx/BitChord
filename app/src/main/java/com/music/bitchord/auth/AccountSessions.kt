package com.music.bitchord.auth

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** A Google login kept independently from every other captured login. */
data class GoogleAccountSession(
    val accountId: String,
    val cookie: String,
    val name: String = "",
    val email: String = "",
    val profiles: List<YouTubeProfile> = emptyList(),
    val activeProfileId: String? = null,
)

/** One Personal or Brand identity available under a Google login. */
data class YouTubeProfile(
    val profileId: String,
    val name: String,
    val handle: String = "",
    val avatar: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val authUser: String? = null,
    val isBrandAccount: Boolean = false,
)

/** Stable selector/swipe order: account insertion order, then profile order. */
fun flattenedProfiles(accounts: List<GoogleAccountSession>): List<Pair<String, String>> =
    accounts.flatMap { account -> account.profiles.map { account.accountId to it.profileId } }

fun adjacentProfile(
    accounts: List<GoogleAccountSession>, accountId: String?, profileId: String?, forward: Boolean,
): Pair<String, String>? {
    val items = flattenedProfiles(accounts)
    val index = items.indexOf(accountId to profileId)
    return items.getOrNull(index + if (forward) 1 else -1)
}

internal fun sessionId(cookie: String, dataSyncId: String?): String =
    dataSyncId?.takeIf { it.isNotBlank() } ?: sha256(cookie).take(24)

internal fun profileId(pageId: String?, dataSyncId: String?, name: String): String =
    pageId ?: dataSyncId ?: "profile:${sha256(name).take(16)}"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

internal fun List<GoogleAccountSession>.toJson(): String = JSONArray().apply {
    forEach { session ->
        put(JSONObject().apply {
            put("id", session.accountId); put("cookie", session.cookie)
            put("name", session.name); put("email", session.email)
            put("activeProfileId", session.activeProfileId)
            put("profiles", JSONArray().apply {
                session.profiles.forEach { profile -> put(JSONObject().apply {
                    put("id", profile.profileId); put("name", profile.name)
                    put("handle", profile.handle); put("avatar", profile.avatar)
                    put("pageId", profile.pageId); put("dataSyncId", profile.dataSyncId)
                    put("authUser", profile.authUser); put("brand", profile.isBrandAccount)
                }) }
            })
        })
    }
}.toString()

internal fun sessionsFromJson(raw: String?): List<GoogleAccountSession> = runCatching {
    val root = JSONArray(raw ?: "[]")
    buildList {
        for (index in 0 until root.length()) {
            val item = root.getJSONObject(index)
            val cookie = item.optString("cookie")
            if (cookie.isNotBlank()) {
            val profiles = item.optJSONArray("profiles") ?: JSONArray()
            add(GoogleAccountSession(
                accountId = item.optString("id", sessionId(cookie, null)), cookie = cookie,
                name = item.optString("name"), email = item.optString("email"),
                activeProfileId = item.optString("activeProfileId").ifBlank { null },
                profiles = buildList {
                    for (p in 0 until profiles.length()) {
                        val value = profiles.getJSONObject(p)
                        val name = value.optString("name")
                        add(YouTubeProfile(
                            profileId = value.optString("id", profileId(null, null, name)),
                            name = name, handle = value.optString("handle"),
                            avatar = value.optString("avatar").ifBlank { null },
                            pageId = value.optString("pageId").ifBlank { null },
                            dataSyncId = value.optString("dataSyncId").ifBlank { null },
                            authUser = value.optString("authUser").ifBlank { null },
                            isBrandAccount = value.optBoolean("brand"),
                        ))
                    }
                },
            ))
            }
        }
    }
}.getOrDefault(emptyList())
