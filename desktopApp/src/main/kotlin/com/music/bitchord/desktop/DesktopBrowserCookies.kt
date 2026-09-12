package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
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
     */
    data class Profile(
        val browser: String,
        val profile: String,
        val family: Family,
        val database: Path,
        val secretAttribute: String? = null,
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
        FIREFOX_ROOTS.forEach { (name, relative) ->
            val root = home.resolve(relative)
            if (!Files.isDirectory(root)) return@forEach
            runCatching {
                Files.list(root).use { entries ->
                    entries.filter { Files.isRegularFile(it.resolve(COOKIES_FIREFOX)) }
                        .forEach { add(Profile(name, it.fileName.toString(), Family.FIREFOX, it.resolve(COOKIES_FIREFOX))) }
                }
            }
        }
        CHROMIUM_ROOTS.forEach { (name, spec) ->
            val (relative, attribute) = spec
            val root = home.resolve(relative)
            if (!Files.isDirectory(root)) return@forEach
            runCatching {
                Files.list(root).use { entries ->
                    entries.filter { Files.isRegularFile(it.resolve(COOKIES_CHROMIUM)) }
                        .forEach {
                            add(
                                Profile(
                                    browser = name,
                                    profile = it.fileName.toString(),
                                    family = Family.CHROMIUM,
                                    database = it.resolve(COOKIES_CHROMIUM),
                                    secretAttribute = attribute,
                                ),
                            )
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
        val password = profile.secretAttribute
            ?.let { DesktopSecretStore.lookup(mapOf("application" to it)) }
        return query(database) { connection ->
            val cookies = LinkedHashMap<String, String>()
            var undecipherable = 0
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
                            else -> decrypt(sealed, host, password).also { if (it == null) undecipherable++ }
                        }
                        if (!value.isNullOrBlank()) cookies[name] = value
                    }
                }
            }
            if (cookies.isEmpty() && undecipherable > 0) {
                Result.Unavailable("its cookies are encrypted with a key this session cannot reach")
            } else {
                Result.Session(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
        }
    }

    /** Chromium's cookie encryption on Linux. */
    private fun decrypt(sealed: ByteArray, host: String, keyringPassword: ByteArray?): String? {
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

        val hashed = java.security.MessageDigest.getInstance("SHA-256").digest(host.toByteArray(Charsets.UTF_8))
        val body = if (plain.size > hashed.size && plain.copyOf(hashed.size).contentEquals(hashed)) {
            plain.copyOfRange(hashed.size, plain.size)
        } else {
            plain
        }
        return String(body, Charsets.UTF_8)
    }

    private fun query(database: Path, block: (java.sql.Connection) -> Result): Result = runCatching {
        DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use(block)
    }.getOrElse { Result.Unavailable("its cookie store could not be opened: ${it.message}") }

    /** Whether a jar can actually sign a request. */
    fun hasSigningSecret(cookie: String): Boolean = cookie.split(';').any { entry ->
        entry.substringBefore('=').trim() in SIGNING_COOKIES &&
            entry.substringAfter('=', "").trim().isNotEmpty()
    }

    private val SIGNING_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

    private const val COOKIES_FIREFOX = "cookies.sqlite"
    private const val COOKIES_CHROMIUM = "Cookies"

    /** Chromium's constant when no keyring is in use; its own choice of word. */
    private const val FALLBACK_PASSWORD = "peanuts"
    private val SALT = "saltysalt".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 1
    private const val KEY_BITS = 128
    private const val PREFIX_BYTES = 3

    private val FIREFOX_ROOTS = listOf(
        "Firefox" to ".mozilla/firefox",
        "Firefox" to ".var/app/org.mozilla.firefox/.mozilla/firefox",
        "LibreWolf" to ".librewolf",
        "LibreWolf" to ".var/app/io.gitlab.librewolf-community/.librewolf",
        "Waterfox" to ".waterfox",
        "Zen" to ".zen",
    )

    /** Browser to profile root and the keyring name it files its key under. */
    private val CHROMIUM_ROOTS = listOf(
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
}
