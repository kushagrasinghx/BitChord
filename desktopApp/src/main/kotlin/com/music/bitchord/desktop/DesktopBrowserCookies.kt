package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The YouTube session a browser on this machine is already holding. */
internal object DesktopBrowserCookies {

    /** The two cookie-store shapes, which want completely different handling. */
    enum class Family { FIREFOX, CHROMIUM }

    /**
     * One browser profile that could be imported from.
     * @param secretAttribute how the browser's own encryption key is filed in
     * @param localState Chromium's browser-wide state, which holds the DPAPI-wrapped Windows key
     */
    data class Profile(
        val browser: String,
        val profile: String,
        val family: Family,
        val database: Path,
        val secretAttribute: String? = null,
        val localState: Path? = null,
    ) {
        val label: String get() = if (profile.isBlank()) browser else "$browser · $profile"
    }

    /** What a profile turned out to hold. */
    sealed interface Result {
        /** A cookie jar with a signing secret in it — a usable session. */
        data class Session(val cookie: String) : Result

        /** The store was read and there is simply nobody signed in there. */
        data object SignedOut : Result

        /** The store could not be read, and why. */
        data class Unavailable(val reason: String) : Result
    }

    private val home: Path get() = Paths.get(System.getProperty("user.home"))

    /** Every browser profile on this machine that might hold a session. */
    fun profiles(): List<Profile> = buildList {
        firefoxRoots().forEach { (name, root) ->
            if (!Files.isDirectory(root)) return@forEach
            runCatching {
                Files.list(root).use { entries ->
                    entries.filter { Files.isRegularFile(it.resolve(COOKIES_FIREFOX)) }
                        .forEach { add(Profile(name, it.fileName.toString(), Family.FIREFOX, it.resolve(COOKIES_FIREFOX))) }
                }
            }
        }
        chromiumRoots().forEach { spec ->
            val (name, root, attribute) = spec
            if (!Files.isDirectory(root)) return@forEach
            runCatching {
                val localState = root.resolve(LOCAL_STATE).takeIf(Files::isRegularFile)
                val displayNames = localState
                    ?.takeIf(Files::isReadable)
                    ?.let { chromiumProfileNames(Files.readString(it)) }
                    .orEmpty()
                Files.list(root).use { entries ->
                    entries.forEach { directory ->
                        val profileId = directory.fileName.toString()
                        if (profileId in HIDDEN_CHROMIUM_PROFILES) return@forEach
                        chromiumCookieStore(directory)?.let { database ->
                            add(
                                Profile(
                                    browser = name,
                                    profile = displayNames[profileId] ?: profileId,
                                    family = Family.CHROMIUM,
                                    database = database,
                                    secretAttribute = attribute,
                                    localState = localState,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }.sortedBy { it.label }

    /** Reads [profile]'s YouTube cookies as a `Cookie` header value. */
    fun read(profile: Profile): Result {
        if (!Files.isReadable(profile.database)) return Result.Unavailable("its cookie store cannot be read")
        val copy = runCatching {
            Files.createTempFile("bitchord-cookies", ".sqlite").also {
                Files.copy(profile.database, it, StandardCopyOption.REPLACE_EXISTING)
            }
        }.getOrElse { return Result.Unavailable("its cookie store could not be copied: ${it.message}") }

        try {
            val jar = when (profile.family) {
                Family.FIREFOX -> readFirefox(copy)
                Family.CHROMIUM -> readChromium(copy, profile)
            }
            return when {
                jar is Result.Unavailable -> jar
                jar is Result.Session && !hasSigningSecret(jar.cookie) -> Result.SignedOut
                else -> jar
            }
        } finally {
            runCatching { Files.deleteIfExists(copy) }
        }
    }

    private fun readFirefox(database: Path): Result = query(database) { connection ->
        val cookies = LinkedHashMap<String, String>()
        connection.prepareStatement(
            "SELECT name, value FROM moz_cookies WHERE host LIKE '%youtube.com'",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val name = rows.getString(1) ?: continue
                    val value = rows.getString(2) ?: continue
                    if (value.isNotBlank()) cookies[name] = value
                }
            }
        }
        Result.Session(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

    private fun readChromium(database: Path, profile: Profile): Result {
        val linuxPassword = if (DesktopPlatform.isLinux) {
            profile.secretAttribute?.let { DesktopSecretStore.lookup(mapOf("application" to it)) }
        } else {
            null
        }
        val windowsKey = if (DesktopPlatform.isWindows) windowsMasterKey(profile.localState) else null
        return query(database) { connection ->
            val cookies = LinkedHashMap<String, String>()
            var undecipherable = 0
            var appBoundSigningCookie = false
            connection.prepareStatement(
                "SELECT host_key, name, value, encrypted_value FROM cookies WHERE host_key LIKE '%youtube.com'",
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val host = rows.getString(1).orEmpty()
                        val name = rows.getString(2) ?: continue
                        val plain = rows.getString(3)
                        val sealed = rows.getBytes(4)
                        val value = when {
                            !plain.isNullOrBlank() -> plain
                            sealed == null || sealed.isEmpty() -> null
                            else -> decrypt(sealed, host, linuxPassword, windowsKey).also {
                                if (it == null) undecipherable++
                                if (name in SIGNING_COOKIES && sealed.hasPrefix(APP_BOUND_PREFIX)) {
                                    appBoundSigningCookie = true
                                }
                            }
                        }
                        if (!value.isNullOrBlank()) cookies[name] = value
                    }
                }
            }
            val jar = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            when {
                !hasSigningSecret(jar) && appBoundSigningCookie -> Result.Unavailable(
                    "its YouTube sign-in is protected by Windows App-Bound Encryption. " +
                        "Import a signed-in Firefox profile or paste the Cookie header instead",
                )
                cookies.isEmpty() && undecipherable > 0 -> Result.Unavailable(
                    "its cookies are encrypted with a key this session cannot reach",
                )
                else -> Result.Session(jar)
            }
        }
    }

    /** Chromium's platform cookie encryption. */
    private fun decrypt(
        sealed: ByteArray,
        host: String,
        keyringPassword: ByteArray?,
        windowsKey: ByteArray?,
    ): String? = if (DesktopPlatform.isWindows) {
        decryptWindows(sealed, windowsKey, host)
    } else {
        decryptLinux(sealed, host, keyringPassword)
    }

    /** Chromium's AES-GCM cookie format after its browser key has been unwrapped with DPAPI. */
    internal fun decryptWindows(sealed: ByteArray, masterKey: ByteArray?, host: String = ""): String? {
        if (!sealed.hasPrefix(CHROMIUM_V10) && !sealed.hasPrefix(CHROMIUM_V11)) {
            return DesktopWindowsCrypto.unprotect(sealed)?.let { stripHostDigest(it, host) }
                ?.toString(Charsets.UTF_8)
        }
        if (masterKey == null || sealed.size <= PREFIX_BYTES + GCM_NONCE_BYTES + GCM_TAG_BYTES) return null
        return runCatching {
            val nonceOffset = PREFIX_BYTES
            val bodyOffset = nonceOffset + GCM_NONCE_BYTES
            val nonce = sealed.copyOfRange(nonceOffset, bodyOffset)
            val body = sealed.copyOfRange(bodyOffset, sealed.size)
            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                doFinal(body)
            }
            stripHostDigest(plain, host).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    /** Chromium's cookie encryption on Linux. */
    private fun decryptLinux(sealed: ByteArray, host: String, keyringPassword: ByteArray?): String? {
        if (sealed.size <= PREFIX_BYTES) return null
        val version = String(sealed, 0, PREFIX_BYTES, Charsets.US_ASCII)
        val password = when (version) {
            "v10" -> FALLBACK_PASSWORD.toByteArray(Charsets.UTF_8)
            "v11" -> keyringPassword ?: return null
            else -> return null
        }
        val plain = runCatching {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(PBEKeySpec(String(password, Charsets.UTF_8).toCharArray(), SALT, ITERATIONS, KEY_BITS))
                .encoded
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16) { ' '.code.toByte() }))
                doFinal(sealed, PREFIX_BYTES, sealed.size - PREFIX_BYTES)
            }
        }.getOrElse { return null }

        return String(stripHostDigest(plain, host), Charsets.UTF_8)
    }

    /** Cookie databases at schema 24+ bind encrypted values to their host with this prefix. */
    private fun stripHostDigest(plain: ByteArray, host: String): ByteArray {
        if (host.isBlank()) return plain
        val hashed = java.security.MessageDigest.getInstance("SHA-256")
            .digest(host.toByteArray(Charsets.UTF_8))
        return if (plain.size > hashed.size && plain.copyOf(hashed.size).contentEquals(hashed)) {
            plain.copyOfRange(hashed.size, plain.size)
        } else {
            plain
        }
    }

    /** Reads and unwraps the per-browser Windows key from Chromium's Local State file. */
    private fun windowsMasterKey(localState: Path?): ByteArray? = runCatching {
        val state = localState?.takeIf(Files::isReadable)?.let(Files::readString) ?: return null
        val encoded = Json.parseToJsonElement(state).jsonObject["os_crypt"]
            ?.jsonObject
            ?.get("encrypted_key")
            ?.jsonPrimitive
            ?.content
            ?: return null
        val wrapped = Base64.getDecoder().decode(encoded)
        if (!wrapped.hasPrefix(DPAPI_PREFIX)) return null
        DesktopWindowsCrypto.unprotect(wrapped.copyOfRange(DPAPI_PREFIX.size, wrapped.size))
    }.getOrNull()

    private fun query(database: Path, block: (java.sql.Connection) -> Result): Result = runCatching {
        DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use(block)
    }.getOrElse { Result.Unavailable("its cookie store could not be opened: ${it.message}") }

    /** Whether a jar can actually sign a request. */
    fun hasSigningSecret(cookie: String): Boolean = cookie.split(';').any { entry ->
        entry.substringBefore('=').trim() in SIGNING_COOKIES &&
            entry.substringAfter('=', "").trim().isNotEmpty()
    }

    private val SIGNING_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

    /**
     * Whether the Chromium user-data directory [userData] already lists a YouTube signing cookie.
     * Only names are read, which Chromium keeps in the clear, and from a copy: the running browser
     * still holds the file.
     */
    internal fun chromiumHasSigningCookie(userData: Path): Boolean {
        val store = chromiumCookieStore(userData.resolve("Default")) ?: return false
        val directory = runCatching { Files.createTempDirectory("bitchord-sign-in") }.getOrNull() ?: return false
        return try {
            val copy = directory.resolve(COOKIES_CHROMIUM)
            Files.copy(store, copy)
            // Recent writes may still sit in the journal beside it.
            listOf("-wal", "-journal").forEach { suffix ->
                store.resolveSibling(store.fileName.toString() + suffix)
                    .takeIf(Files::isRegularFile)
                    ?.let { Files.copy(it, directory.resolve(COOKIES_CHROMIUM + suffix)) }
            }
            DriverManager.getConnection("jdbc:sqlite:${copy.toAbsolutePath()}").use { db ->
                db.prepareStatement(
                    "SELECT 1 FROM cookies WHERE host_key LIKE '%youtube.com' AND name IN " +
                        SIGNING_COOKIES.joinToString(prefix = "(", postfix = ")") { "?" } + " LIMIT 1",
                ).use { statement ->
                    SIGNING_COOKIES.forEachIndexed { index, name -> statement.setString(index + 1, name) }
                    statement.executeQuery().use { it.next() }
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private const val COOKIES_FIREFOX = "cookies.sqlite"
    private const val COOKIES_CHROMIUM = "Cookies"
    private const val NETWORK = "Network"
    private const val LOCAL_STATE = "Local State"

    /** Chromium's constant when no keyring is in use; its own choice of word. */
    private const val FALLBACK_PASSWORD = "peanuts"
    private val SALT = "saltysalt".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 1
    private const val KEY_BITS = 128
    private const val PREFIX_BYTES = 3
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BYTES = 16
    private const val GCM_TAG_BITS = GCM_TAG_BYTES * 8

    private val CHROMIUM_V10 = "v10".toByteArray(Charsets.US_ASCII)
    private val CHROMIUM_V11 = "v11".toByteArray(Charsets.US_ASCII)
    private val APP_BOUND_PREFIX = "v20".toByteArray(Charsets.US_ASCII)
    private val DPAPI_PREFIX = "DPAPI".toByteArray(Charsets.US_ASCII)
    private val HIDDEN_CHROMIUM_PROFILES = setOf("Guest Profile", "System Profile")

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
        size >= prefix.size && indices.take(prefix.size).all { this[it] == prefix[it] }

    private fun chromiumCookieStore(profile: Path): Path? =
        profile.resolve(NETWORK).resolve(COOKIES_CHROMIUM).takeIf(Files::isRegularFile)
            ?: profile.resolve(COOKIES_CHROMIUM).takeIf(Files::isRegularFile)

    /** Chrome files profiles under ids such as `Profile 6`; Local State holds their UI names. */
    internal fun chromiumProfileNames(state: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(state).jsonObject["profile"]
            ?.jsonObject
            ?.get("info_cache")
            ?.jsonObject
            ?.mapNotNull { (profileId, details) ->
                details.jsonObject["name"]
                    ?.jsonPrimitive
                    ?.content
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { profileId to it }
            }
            ?.toMap()
            .orEmpty()
    }.getOrDefault(emptyMap())

    private val LINUX_FIREFOX_ROOTS = listOf(
        "Firefox" to ".mozilla/firefox",
        "Firefox" to ".var/app/org.mozilla.firefox/.mozilla/firefox",
        "LibreWolf" to ".librewolf",
        "LibreWolf" to ".var/app/io.gitlab.librewolf-community/.librewolf",
        "Waterfox" to ".waterfox",
        "Zen" to ".zen",
    )

    /** Browser to profile root and the keyring name it files its key under. */
    private val LINUX_CHROMIUM_ROOTS = listOf(
        "Chrome" to (".config/google-chrome" to "chrome"),
        "Chrome" to (".var/app/com.google.Chrome/config/google-chrome" to "chrome"),
        "Chromium" to (".config/chromium" to "chromium"),
        "Chromium" to (".var/app/org.chromium.Chromium/config/chromium" to "chromium"),
        "Brave" to (".config/BraveSoftware/Brave-Browser" to "brave"),
        "Brave" to (".var/app/com.brave.Browser/config/BraveSoftware/Brave-Browser" to "brave"),
        "Vivaldi" to (".config/vivaldi" to "vivaldi"),
        "Edge" to (".config/microsoft-edge" to "chrome"),
        "Opera" to (".config/opera" to "chromium"),
    )

    private data class ChromiumRoot(val browser: String, val root: Path, val secretAttribute: String?)

    private fun firefoxRoots(): List<Pair<String, Path>> = if (DesktopPlatform.isWindows) {
        val roaming = System.getenv("APPDATA")?.let(Paths::get) ?: home.resolve("AppData/Roaming")
        listOf(
            "Firefox" to roaming.resolve("Mozilla/Firefox/Profiles"),
            "LibreWolf" to roaming.resolve("librewolf/Profiles"),
            "Waterfox" to roaming.resolve("Waterfox/Profiles"),
            "Zen" to roaming.resolve("zen/Profiles"),
        )
    } else {
        LINUX_FIREFOX_ROOTS.map { (name, relative) -> name to home.resolve(relative) }
    }

    private fun chromiumRoots(): List<ChromiumRoot> = if (DesktopPlatform.isWindows) {
        val local = System.getenv("LOCALAPPDATA")?.let(Paths::get) ?: home.resolve("AppData/Local")
        listOf(
            ChromiumRoot("Chrome", local.resolve("Google/Chrome/User Data"), null),
            ChromiumRoot("Chromium", local.resolve("Chromium/User Data"), null),
            ChromiumRoot("Edge", local.resolve("Microsoft/Edge/User Data"), null),
            ChromiumRoot("Brave", local.resolve("BraveSoftware/Brave-Browser/User Data"), null),
            ChromiumRoot("Vivaldi", local.resolve("Vivaldi/User Data"), null),
        )
    } else {
        LINUX_CHROMIUM_ROOTS.map { (name, spec) ->
            ChromiumRoot(name, home.resolve(spec.first), spec.second)
        }
    }
}
