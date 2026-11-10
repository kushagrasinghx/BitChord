package com.music.bitchord.data.sources.addon

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Proof, for the addons that ask for it, that a request comes from the genuine
 * BitChord app and not a fork, another app or a script.
 *
 * The client name and User-Agent cannot do this: this app is open source, so
 * anything it sends can be copied. The one thing a copy cannot have is the
 * release signing key, and Android will vouch for that itself. A key generated
 * in the device's secure hardware comes with a certificate chain, rooted at
 * Google, in which Android states the package name and signing certificate of
 * the app that asked for the key, and whether the OS was booted verified. The
 * BitChord auth service checks that chain once and returns a short-lived token
 * bound to the key. Each addon request then carries the token and a signature
 * made with the hardware key, which never leaves the device. A token copied off
 * a phone is therefore useless anywhere else.
 *
 * Opt-in per addon. Nothing is sent unless the addon's manifest declares
 * `bitchordAuth`, or it answers a request with `401` and
 * `WWW-Authenticate: BitChord`. Other addons never see the token. Even when
 * they do, a proof is bound to one host, so it cannot be replayed to another.
 *
 * Wire format: "Addon auth" in the BitChord addon docs, protocol version 1.
 */
object AddonAuth {
    const val TOKEN_HEADER = "X-BitChord-Token"
    const val PROOF_HEADER = "X-BitChord-Proof"
    private const val PROOF_CONTEXT = "bitchord-addon-proof/v1"

    private const val TAG = "BitChord"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS_PREFIX = "bitchord_addon_auth_"

    /** Refreshed this long before expiry, so an in-flight search never carries a token about to lapse. */
    private const val REFRESH_BEFORE_MS = 5 * 60_000L

    /**
     * A proof is reused for this long per host. Signing in the TEE costs a few
     * milliseconds, and a search fans out several requests at once, so one
     * signature covers the burst. Addons accept a proof for 120 s.
     */
    private const val PROOF_REUSE_MS = 30_000L

    /** After the service or the device says no. Retrying sooner would get the same answer. */
    private const val REFUSED_BACKOFF_MS = 15 * 60_000L

    /** After a network failure or a busy service. */
    private const val TRANSIENT_BACKOFF_MS = 30_000L

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mutex = Mutex()

    private class Session(val token: String, val expiresAtMs: Long, val alias: String) {
        val tokenHash: String = b64url(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
    }

    private class Refusal(val message: String, val untilMs: Long, val transient: Boolean) {
        /** Transient ones are [AddonUnavailable], so the sources screen shows an outage, not a broken addon. */
        fun raise(): Nothing = throw if (transient) AddonUnavailable(message) else AddonException(message)
    }

    @Volatile private var prefs: SharedPreferences? = null

    /**
     * Separate from [prefs], which [drop] clears whenever the token is thrown
     * away, and left out of backups (see backup_rules.xml): a tester code is a
     * credential for this phone's owner, not data to restore elsewhere.
     */
    @Volatile private var testerPrefs: SharedPreferences? = null
    @Volatile private var session: Session? = null
    @Volatile private var refusal: Refusal? = null
    @Volatile private var clockOffsetMs = 0L

    /** JVM tests only: stands in for attestation, which needs real secure hardware. */
    @Volatile
    internal var testHeaders: (suspend (host: String, rejectedToken: String?) -> Map<String, String>)? = null

    /** `host|tokenHash` to (made at, header value). */
    private val proofs = ConcurrentHashMap<String, Pair<Long, String>>()

    /** Restores the last token, so a process restart doesn't cost a fresh attestation. */
    fun init(context: Context) {
        testerPrefs = context.getSharedPreferences("bitchord_tester", Context.MODE_PRIVATE)
        val p = context.getSharedPreferences("bitchord_addon_auth", Context.MODE_PRIVATE)
        prefs = p
        val token = p.getString("token", null)
        val alias = p.getString("alias", null)
        val expiresAt = p.getLong("expiresAt", 0L)
        clockOffsetMs = p.getLong("clockOffset", 0L)
        if (token != null && alias != null && expiresAt > System.currentTimeMillis()) {
            session = Session(token, expiresAt, alias)
        }
    }

    /**
     * The two headers for one request to [host].
     *
     * [rejectedToken] is the token an addon just answered `expired` or
     * `invalid_token` to. A new one is fetched only if the current session
     * still holds that token, so a burst of rejected requests triggers one
     * refresh between them, not one each.
     *
     * Throws [AddonException] with a reason the sources screen can show when
     * this device cannot get a token at all.
     */
    suspend fun headersFor(host: String, rejectedToken: String? = null): Map<String, String> {
        val normalized = normalizeHost(host)
        testHeaders?.let { return it(normalized, rejectedToken) }
        repeat(2) {
            val current = session(rejectedToken)
            try {
                return mapOf(TOKEN_HEADER to current.token, PROOF_HEADER to proof(current, normalized))
            } catch (e: KeyGone) {
                // Keystore wiped under a restored token (restore from backup, new device): start over.
                TrackLog.w(TAG, "addon auth: hardware key missing, attesting again")
                drop(current)
            }
        }
        throw AddonException("BitChord couldn't use this device's secure key store")
    }

    /**
     * Why this device can't get a token right now, while the last refusal's
     * backoff runs; null when it has one or may ask again.
     *
     * Read by [SourceRegistry.active][com.music.bitchord.data.sources.SourceRegistry.active]
     * to leave addons that ask for auth out of the walks meanwhile: each request
     * would only re-throw this, and a resolve tries several queries per source,
     * so the same refusal filled the log a dozen times per track.
     */
    fun standingRefusal(): String? = refusal?.takeIf { it.untilMs > System.currentTimeMillis() }?.message

    /** JVM tests only: a refusal as [session] would record it, without attesting. */
    internal fun refuseForTest(message: String?, transient: Boolean = false) {
        refusal = message?.let { Refusal(it, System.currentTimeMillis() + REFUSED_BACKOFF_MS, transient) }
    }

    // ── Tester code ───────────────────────────────────────────────────────

    /**
     * Whether a tester code is saved. The code itself is never read back out
     * for display: the hidden field in the app-language dialog only replaces
     * or removes it.
     */
    fun hasTesterCode(): Boolean = !testerPrefs?.getString(TESTER_KEY, null).isNullOrEmpty()

    /**
     * Saves (or with blank, removes) the tester code sent with each
     * attestation. The auth service accepts a valid one in place of a locked
     * bootloader and an unrevoked attestation key; nothing else changes.
     *
     * Forgets the current token and any standing refusal, so the next addon
     * request attests again with the new code instead of waiting out the
     * 15-minute backoff from the last "no".
     */
    fun setTesterCode(raw: String?) {
        val code = normalizeTesterCode(raw)
        testerPrefs?.edit()?.apply { if (code.isEmpty()) remove(TESTER_KEY) else putString(TESTER_KEY, code) }?.apply()
        refusal = null
        session?.let(::drop)
        TrackLog.i(TAG, "addon auth: tester code ${if (code.isEmpty()) "removed" else "saved"}")
    }

    /** Same rule as the auth service: case, spaces and dashes don't matter. */
    internal fun normalizeTesterCode(raw: String?): String =
        raw.orEmpty().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private const val TESTER_KEY = "code"

    /** What to tell the listener when an addon refused credentials that were sent. */
    fun rejectionMessage(code: String?): String = when (code) {
        "stale_proof" -> "This addon rejected the request because this phone's clock is wrong"
        "unavailable" -> "This addon couldn't check BitChord's verification right now"
        else -> "This addon didn't accept BitChord's verification" + (code?.let { " ($it)" } ?: "")
    }

    // ── Session ───────────────────────────────────────────────────────────

    private fun usable(s: Session?, rejectedToken: String?): Session? =
        s?.takeIf { it.token != rejectedToken && it.expiresAtMs - REFRESH_BEFORE_MS > System.currentTimeMillis() }

    private suspend fun session(rejectedToken: String?): Session {
        usable(session, rejectedToken)?.let { return it }
        return mutex.withLock {
            usable(session, rejectedToken)?.let { return@withLock it }
            refusal?.takeIf { it.untilMs > System.currentTimeMillis() }?.raise()
            val fresh = try {
                attest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AttestRefused) {
                TrackLog.w(TAG, "addon auth: ${e.message}")
                Refusal(e.message ?: "Verification refused", System.currentTimeMillis() + e.backoffMs, transient = false)
                    .also { refusal = it }.raise()
            } catch (e: Exception) {
                val message = "Couldn't reach BitChord's addon verification service"
                TrackLog.w(TAG, "addon auth: $message", e)
                Refusal(message, System.currentTimeMillis() + TRANSIENT_BACKOFF_MS, transient = true)
                    .also { refusal = it }.raise()
            }
            refusal = null
            session?.let { old -> if (old.alias != fresh.alias) deleteKey(old.alias) }
            session = fresh
            proofs.clear()
            prefs?.edit()
                ?.putString("token", fresh.token)
                ?.putString("alias", fresh.alias)
                ?.putLong("expiresAt", fresh.expiresAtMs)
                ?.putLong("clockOffset", clockOffsetMs)
                ?.apply()
            fresh
        }
    }

    private fun drop(s: Session) {
        if (session === s) {
            session = null
            prefs?.edit()?.clear()?.apply()
        }
        deleteKey(s.alias)
    }

    // ── Attestation ───────────────────────────────────────────────────────

    private class AttestRefused(message: String, val backoffMs: Long = REFUSED_BACKOFF_MS) : Exception(message)

    private class KeyGone : Exception()

    @Serializable private data class ChallengeResponse(val challenge: String, val serverTime: Long)

    @Serializable private data class AttestRequest(
        val challenge: String,
        val chain: List<String>,
        /** Left out of the JSON when null (see [json]'s explicitNulls). */
        val testerCode: String? = null,
    )

    @Serializable private data class AttestResponse(val token: String, val expiresAt: Long, val serverTime: Long? = null)

    @Serializable private data class ServiceError(val error: String = "", val message: String = "")

    private suspend fun attest(): Session = withContext(Dispatchers.IO) {
        val base = BuildConfig.ADDON_AUTH_URL
        val started = System.currentTimeMillis()
        val challenge = json.decodeFromString<ChallengeResponse>(call(Request.Builder().url("$base/v1/challenge").build()))
        // NTP-style midpoint: the proof's timestamp is checked against the addon's
        // clock, and phones are often minutes out.
        clockOffsetMs = challenge.serverTime - (started + System.currentTimeMillis()) / 2

        val alias = ALIAS_PREFIX + UUID.randomUUID()
        val chain = try {
            generateAttestedKey(alias, Base64.getUrlDecoder().decode(challenge.challenge))
        } catch (e: Exception) {
            deleteKey(alias)
            throw AttestRefused("This device can't prove it is running the official BitChord app (no hardware key attestation)")
        }
        try {
            val body = json.encodeToString(
                AttestRequest.serializer(),
                AttestRequest(
                    challenge.challenge,
                    chain.map { Base64.getEncoder().encodeToString(it) },
                    testerPrefs?.getString(TESTER_KEY, null)?.ifEmpty { null },
                ),
            )
            val request = Request.Builder().url("$base/v1/attest")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val answer = json.decodeFromString<AttestResponse>(call(request))
            TrackLog.i(TAG, "addon auth: device verified, token valid until ${answer.expiresAt}")
            Session(answer.token, answer.expiresAt, alias)
        } catch (e: Exception) {
            deleteKey(alias)
            throw e
        }
    }

    /** One call to the auth service. Its refusals are [AttestRefused]; anything else is transient. */
    private fun call(request: Request): String =
        client.newCall(request.newBuilder().header("Accept", "application/json").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> text
                response.code == 429 || response.code >= 500 -> throw java.io.IOException("auth service HTTP ${response.code}")
                else -> {
                    val error = runCatching { json.decodeFromString<ServiceError>(text) }.getOrNull()
                    // Fixable by updating rather than waiting: check again soon after.
                    val backoff = if (error?.error == "outdated_app") TRANSIENT_BACKOFF_MS else REFUSED_BACKOFF_MS
                    throw AttestRefused(
                        "BitChord couldn't verify this device: " + (error?.message?.ifBlank { null } ?: "HTTP ${response.code}"),
                        backoff,
                    )
                }
            }
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun generateAttestedKey(alias: String, challenge: ByteArray): List<ByteArray> {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
        val chain = keyStore().getCertificateChain(alias)
        require(!chain.isNullOrEmpty() && chain.size >= 2) { "no attestation chain" }
        return chain.map { it.encoded }
    }

    private fun deleteKey(alias: String) {
        runCatching { keyStore().deleteEntry(alias) }
    }

    // ── Proof ─────────────────────────────────────────────────────────────

    private fun proof(s: Session, host: String): String {
        val cacheKey = "$host|${s.tokenHash}"
        val now = System.currentTimeMillis()
        proofs[cacheKey]?.let { (madeAt, header) -> if (now - madeAt < PROOF_REUSE_MS) return header }

        val privateKey = runCatching { keyStore().getKey(s.alias, null) as? PrivateKey }.getOrNull() ?: throw KeyGone()
        val timestamp = now + clockOffsetMs
        val message = proofMessage(host, timestamp, s.tokenHash)
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(message.toByteArray())
            sign()
        }
        val header = "v1.$timestamp.${b64url(derToRaw(der))}"
        if (proofs.size > 64) proofs.clear()
        proofs[cacheKey] = now to header
        return header
    }

    internal fun proofMessage(host: String, timestampMs: Long, tokenHash: String) =
        "$PROOF_CONTEXT\n$host\n$timestampMs\n$tokenHash"

    /** Lowercase, no port, no trailing dot: the same normalisation the addon-side verifiers apply. */
    internal fun normalizeHost(host: String): String =
        host.trim().lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')

    internal fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * An ECDSA P-256 signature from DER (`SEQUENCE { INTEGER r, INTEGER s }`,
     * which is what Android's `Signature` produces) to the fixed 64-byte `r || s`
     * that JOSE and WebCrypto verify.
     */
    internal fun derToRaw(der: ByteArray): ByteArray {
        var i = 0
        fun byte(): Int = der[i++].toInt() and 0xff
        fun length(): Int {
            val first = byte()
            if (first < 0x80) return first
            var n = 0
            repeat(first and 0x7f) { n = (n shl 8) or byte() }
            return n
        }
        fun integer(): ByteArray {
            require(byte() == 0x02) { "expected INTEGER" }
            val len = length()
            val value = der.copyOfRange(i, i + len)
            i += len
            val trimmed = value.dropWhile { it == 0.toByte() }.toByteArray()
            require(trimmed.size <= 32) { "integer too large for P-256" }
            return ByteArray(32 - trimmed.size) + trimmed
        }
        require(byte() == 0x30) { "expected SEQUENCE" }
        length()
        return integer() + integer()
    }

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    }
}
