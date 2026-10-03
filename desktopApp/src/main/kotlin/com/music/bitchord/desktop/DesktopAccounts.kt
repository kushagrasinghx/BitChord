package com.music.bitchord.desktop

import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.AccountChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** One Personal or Brand identity available under a Google login. */
@Serializable
data class DesktopYouTubeProfile(
    val profileId: String,
    val name: String,
    val handle: String = "",
    val avatar: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val authUser: String = "0",
    val isBrandAccount: Boolean = false,
)

/** A Google login kept independently from every other captured login. */
@Serializable
data class DesktopGoogleAccount(
    val accountId: String,
    val name: String = "",
    val email: String = "",
    val avatar: String? = null,
    val profiles: List<DesktopYouTubeProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** The browser profile this session was taken from, as the path of its cookie store. */
    val sourceProfile: String? = null,
)

/** Every Google login this app is holding, and which channel it is acting as. */
internal object DesktopAccounts {

    private const val KEY_ACCOUNTS = "google_accounts_v1"
    private const val KEY_ACTIVE_ACCOUNT = "active_google_account_v1"
    private const val KEY_ACTIVE_PROFILE = "active_google_profile_v1"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val persistence get() = DesktopPersistence()

    fun accounts(): List<DesktopGoogleAccount> {
        val raw = DesktopPreferenceChunks.read(persistence.preferences, KEY_ACCOUNTS) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DesktopGoogleAccount>>(raw) }.getOrDefault(emptyList())
    }

    fun activeAccountId(): String? = persistence.string(KEY_ACTIVE_ACCOUNT).takeIf { it.isNotBlank() }

    fun activeProfileId(): String? = persistence.string(KEY_ACTIVE_PROFILE).takeIf { it.isNotBlank() }

    fun active(): DesktopGoogleAccount? =
        accounts().firstOrNull { it.accountId == activeAccountId() } ?: accounts().firstOrNull()

    fun activeProfile(): DesktopYouTubeProfile? {
        val account = active() ?: return null
        return account.profiles.firstOrNull { it.profileId == activeProfileId() }
            ?: account.profiles.firstOrNull { it.profileId == account.activeProfileId }
            ?: account.profiles.firstOrNull()
    }

    /** The identity for a stored account, or null when its cookie has gone. */
    fun sessionFor(account: DesktopGoogleAccount, profile: DesktopYouTubeProfile?): DesktopYouTubeAuth.Session? {
        val cookie = cookieOf(account.accountId) ?: return null
        return DesktopYouTubeAuth.Session(
            cookie = cookie,
            authUser = profile?.authUser ?: "0",
            pageId = profile?.pageId,
            dataSyncId = profile?.dataSyncId,
        )
    }

    /** Puts the chosen account into force, renewing it first if it has gone flat. */
    suspend fun activate(): DesktopYouTubeAuth.Session? {
        val account = active() ?: run {
            DesktopYouTubeAuth.adopt(null)
            return null
        }
        val stored = sessionFor(account, activeProfile())
        if (stored == null) {
            DesktopTrackLog.log("account '${account.email.ifBlank { account.name }}' has no stored credential any more")
            DesktopYouTubeAuth.adopt(null)
            return null
        }
        DesktopYouTubeSession.adoptSessionScope(stored.cookie)?.let { live ->
            DesktopYouTubeAuth.adopt(stored)
            return stored
        }

        DesktopTrackLog.log("the stored session has expired; looking for a fresh one in the browser it came from")
        val renewed = renewFromBrowser(account)
        if (renewed == null) {
            // Anonymous rather than broken.
            DesktopTrackLog.log("could not renew the session; browsing as a guest until it is signed in again")
            DesktopYouTubeAuth.adopt(null)
        }
        return renewed
    }

    /** Reads the source browser's cookies again and saves them if they are live. */
    private suspend fun renewFromBrowser(account: DesktopGoogleAccount): DesktopYouTubeAuth.Session? {
        val candidates = DesktopBrowserCookies.profiles()
        // The one it came from first, then any other holding a live session for the *same account*
        // — matched on the id the shell reports, not on the path.
        val ordered = candidates.sortedByDescending { it.database.toString() == account.sourceProfile }
        for (profile in ordered) {
            val jar = (DesktopBrowserCookies.read(profile) as? DesktopBrowserCookies.Result.Session)?.cookie
                ?: continue
            val scope = DesktopYouTubeSession.adoptSessionScope(jar) ?: continue
            val belongs = scope.dataSyncId == account.accountId ||
                (account.sourceProfile != null && profile.database.toString() == account.sourceProfile)
            if (!belongs) continue
            DesktopYouTubeAuth.adopt(scope)
            writeCookie(account.accountId, jar)
            rememberSource(account, profile.database.toString())
            DesktopTrackLog.log("renewed the session from ${profile.label}")
            return scope
        }
        return null
    }

    /** Notes where a renewal came from, so the next one starts there. */
    private fun rememberSource(account: DesktopGoogleAccount, source: String) {
        if (account.sourceProfile == source) return
        write(accounts().map { if (it.accountId == account.accountId) it.copy(sourceProfile = source) else it })
    }

    /** Puts the chosen account and channel into force for this process. */
    fun applyActive(): DesktopYouTubeAuth.Session? {
        val account = active()
        if (account == null) {
            DesktopYouTubeAuth.adopt(null)
            return null
        }
        val session = sessionFor(account, activeProfile())
        DesktopYouTubeAuth.adopt(session)
        if (session == null) {
            DesktopTrackLog.log("account '${account.email.ifBlank { account.name }}' has no stored credential any more")
        }
        return session
    }

    /** Saves a captured login and makes it the active one. */
    fun save(
        cookie: String,
        scope: DesktopYouTubeAuth.Session,
        details: Account?,
        channels: List<AccountChannel>,
        sourceProfile: String? = null,
    ): DesktopGoogleAccount {
        val accountId = scope.dataSyncId?.takeIf { it.isNotBlank() } ?: digest(cookie).take(24)
        val profiles = channels.map { channel ->
            DesktopYouTubeProfile(
                profileId = channel.pageId ?: channel.dataSyncId ?: "profile:" + digest(channel.name).take(16),
                name = channel.name,
                handle = channel.subtitle,
                avatar = channel.thumbnailUrl,
                pageId = channel.pageId,
                dataSyncId = channel.dataSyncId,
                authUser = scope.authUser,
                isBrandAccount = channel.pageId != null,
            )
        }.ifEmpty {
            // No switcher answer is not the same as no channels: every login has at least the one
            // the shell is already acting as.
            listOf(
                DesktopYouTubeProfile(
                    profileId = scope.pageId ?: scope.dataSyncId ?: accountId,
                    name = details?.name?.ifBlank { null } ?: "YouTube Music",
                    handle = details?.email.orEmpty(),
                    avatar = details?.thumbnailUrl,
                    pageId = scope.pageId,
                    dataSyncId = scope.dataSyncId,
                    authUser = scope.authUser,
                    isBrandAccount = scope.pageId != null,
                ),
            )
        }
        val chosen = profiles.firstOrNull { it.pageId != null && it.pageId == scope.pageId }
            ?: profiles.firstOrNull { it.dataSyncId != null && it.dataSyncId == scope.dataSyncId }
            ?: profiles.first()
        val account = DesktopGoogleAccount(
            accountId = accountId,
            name = details?.name.orEmpty(),
            email = details?.email.orEmpty(),
            avatar = details?.thumbnailUrl,
            profiles = profiles,
            activeProfileId = chosen.profileId,
            sourceProfile = sourceProfile ?: accounts().firstOrNull { it.accountId == accountId }?.sourceProfile,
        )
        writeCookie(accountId, cookie)
        write(accounts().filterNot { it.accountId == accountId } + account)
        select(accountId, chosen.profileId)
        return account
    }

    fun select(accountId: String, profileId: String?) {
        persistence.saveString(KEY_ACTIVE_ACCOUNT, accountId)
        persistence.saveString(KEY_ACTIVE_PROFILE, profileId.orEmpty())
        applyActive()
    }

    fun remove(accountId: String) {
        DesktopSecretStore.remove(attributesFor(accountId))
        persistence.saveString(cookieFallbackKey(accountId), "")
        val remaining = accounts().filterNot { it.accountId == accountId }
        write(remaining)
        val next = remaining.firstOrNull()
        persistence.saveString(KEY_ACTIVE_ACCOUNT, next?.accountId.orEmpty())
        persistence.saveString(KEY_ACTIVE_PROFILE, next?.activeProfileId.orEmpty())
        applyActive()
    }

    private fun write(value: List<DesktopGoogleAccount>) {
        DesktopPreferenceChunks.write(persistence.preferences, KEY_ACCOUNTS, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DesktopGoogleAccount.serializer()), value))
    }

    // ---- Where the credential itself lives ---------------------------------

    private fun attributesFor(accountId: String) =
        mapOf("application" to "bitchord", "account" to accountId)

    /** The keyring first, and a preference only when there is no keyring. */
    private fun writeCookie(accountId: String, cookie: String) {
        val stored = DesktopSecretStore.store(
            label = DesktopStrings["d_bitchord_youtube_music_session", "BitChord — YouTube Music session"],
            attributes = attributesFor(accountId),
            secret = cookie.toByteArray(Charsets.UTF_8),
        )
        if (stored) {
            persistence.saveString(cookieFallbackKey(accountId), "")
        } else {
            DesktopTrackLog.log("no keyring available; keeping the session in preferences instead")
            DesktopPreferenceChunks.write(persistence.preferences, cookieFallbackKey(accountId), cookie)
        }
    }

    fun cookieOf(accountId: String): String? =
        DesktopSecretStore.lookup(attributesFor(accountId))?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
            ?: DesktopPreferenceChunks.read(persistence.preferences, cookieFallbackKey(accountId))
                ?.takeIf { it.isNotBlank() }

    private fun cookieFallbackKey(accountId: String) = "google_cookie_$accountId"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Reads the account renderers out of YouTube Music's own account endpoints. */
internal object DesktopAccountParser {

    /** Name, email and photo, from the header the account menu opens with. */
    fun account(root: JsonElement): Account? {
        val header = DesktopSearchClient.renderers(root, "activeAccountHeaderRenderer").firstOrNull() ?: return null
        val name = header.text("accountName")
        if (name.isBlank()) return null
        return Account(
            name = name,
            email = header.text("email").ifBlank { header.text("channelHandle") },
            thumbnailUrl = header.photo(),
        )
    }

    /** The channels a switcher response offers, in the order YouTube lists them. */
    fun channels(root: JsonElement): List<AccountChannel> =
        DesktopSearchClient.renderers(root, "accountItem").mapNotNull { item ->
            val name = item.text("accountName")
            if (name.isBlank()) return@mapNotNull null
            val pageId = item.findString("pageId")
            // `<accountSyncId>||<sessionSyncId>`; only the first half names the account, exactly as
            // in the shell's own DATASYNC_ID.
            val dataSyncId = item.findString("datasyncIdToken")?.substringBefore("||")?.takeIf { it.isNotBlank() }
            if (pageId == null && dataSyncId == null) return@mapNotNull null
            AccountChannel(
                name = name,
                subtitle = item.text("channelHandle").ifBlank { item.text("accountByline") },
                thumbnailUrl = item.photo(),
                pageId = pageId,
                dataSyncId = dataSyncId,
                activeOnWeb = (item["isSelected"] as? JsonPrimitive)?.content == "true",
            )
        }.distinctBy { it.key }

    /** A renderer's `runs`/`simpleText` field, whichever it used. */
    private fun JsonObject.text(field: String): String {
        val node = this[field] as? JsonObject ?: return ""
        (node["runs"] as? JsonArray)?.let { runs ->
            return runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        }
        return node["simpleText"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /** The largest thumbnail the account photo offers. */
    private fun JsonObject.photo(): String? =
        ((this["accountPhoto"] as? JsonObject)?.get("thumbnails") as? JsonArray)
            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

    /** The first value of [key] anywhere inside, whatever it is wrapped in. */
    private fun JsonElement.findString(key: String): String? = when (this) {
        is JsonObject -> (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: values.firstNotNullOfOrNull { it.findString(key) }
        is JsonArray -> firstNotNullOfOrNull { it.findString(key) }
        else -> null
    }
}
