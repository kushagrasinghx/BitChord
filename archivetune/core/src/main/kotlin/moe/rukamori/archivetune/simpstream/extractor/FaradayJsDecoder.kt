/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/service/kotlinYtmusicScraper extractor/FaradayJsDecoder.kt —
 * GPL-3.0, © maxrave-dev. Logic kept byte-for-byte; only the package name,
 * the HttpClient engine (SimpMusic's ktorExt getEngine() -> OkHttp) and
 * the Logger (com.maxrave.logger -> SimpStreamLog) changed.
 */

package moe.rukamori.archivetune.simpstream.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import moe.rukamori.archivetune.simpstream.SimpStreamLog
import moe.rukamori.archivetune.simpstream.cipher.FaradayCipherEngine
import moe.rukamori.archivetune.simpstream.cipher.InnerTubeLogLevel
import moe.rukamori.archivetune.simpstream.cipher.InnerTubeLogger
import dev.maxrave.pipepipe.extractor.exceptions.ParsingException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeJavaScriptDecoder

private const val TAG = "FaradayJsDecoder"
private const val JS_THREAD_STACK_BYTES = 32L * 1024L * 1024L

/**
 * Plugs [FaradayCipherEngine] into PipePipe as its local decoder, so signature and `n` challenges
 * are solved on the device instead of at `api.pipepipe.dev`.
 *
 * PipePipe keeps its own server call as the fallback: [YoutubeApiDecoder.decodeBatch] tries this
 * decoder first and drops through to the API when it throws. **Throwing is therefore the way to
 * hand a request back** — returning a half-filled result would be accepted as final and the missing
 * URLs would simply fail later, skipping the fallback entirely.
 */
internal class FaradayJsDecoder : YoutubeJavaScriptDecoder {
    /**
     * How the last decode was answered, for the log line in [SimpMusicExtractor].
     *
     * Process-wide rather than per-track: PipePipe hands the decoder a player id, never a video id,
     * so two tracks resolving at once cannot be told apart here. Good enough to see which path is
     * carrying the load, not for attributing a single stream.
     */
    @Volatile
    var lastOutcome: String = "unused"
        private set

    /** [lastOutcome] reduced to the three words a user can act on. */
    val lastOutcomeLabel: String
        get() =
            when {
                lastOutcome.startsWith("local") -> "local"
                lastOutcome.startsWith("fallback") -> "pipepipe.dev"
                else -> "cached"
            }

    /**
     * One thread, 32 MB of stack.
     *
     * YouTube's n-transform recurses far past what a default ~1 MB JVM thread holds, and QuickJS
     * runs it on the native stack. When it overruns, QuickJS does not reliably raise a JS error —
     * the process takes SIGSEGV with no hs_err file, because the crash is below the JVM.
     */
    private val jsThread =
        Executors
            .newSingleThreadExecutor { runnable -> Thread(null, runnable, "QuickJs", JS_THREAD_STACK_BYTES) }
            .asCoroutineDispatcher()

    private val engine by lazy { FaradayCipherEngine(HttpClient(OkHttp), jsThread, bridgedLogger) }

    override fun getPlayerData(videoId: String): YoutubeJavaScriptDecoder.PlayerData {
        val info =
            runBlocking { engine.playerInfo() }
                ?: run {
                    lastOutcome = "fallback:no-player-metadata"
                    throw ParsingException("Faraday: no player metadata")
                }
        return YoutubeJavaScriptDecoder.PlayerData(info.playerId, info.signatureTimestamp)
    }

    override fun decodeBatch(
        playerId: String,
        signatures: List<String>?,
        throttlingParameters: List<String>?,
    ): YoutubeApiDecoder.BatchDecodeResult {
        val wantedSignatures = signatures.orEmpty().distinct()
        val wantedNParameters = throttlingParameters.orEmpty().distinct()
        val result = runBlocking { engine.decode(playerId, wantedSignatures, wantedNParameters) }

        // All or nothing: one unsolved challenge means the player table is stale or the script
        // changed shape, and both are reasons to let the whole batch go to the API rather than
        // ship a URL that will come back 403.
        val missing =
            wantedSignatures.count { it !in result.signatures } +
                wantedNParameters.count { it !in result.nParameters }
        val wanted = wantedSignatures.size + wantedNParameters.size
        if (missing > 0) {
            lastOutcome = "fallback:$missing-of-$wanted-unsolved"
            throw ParsingException("Faraday: $missing of $wanted unsolved")
        }
        lastOutcome = "local:sig=${wantedSignatures.size},n=${wantedNParameters.size}"
        SimpStreamLog.d(TAG, "solved $wanted locally (sig=${wantedSignatures.size} n=${wantedNParameters.size})")
        return YoutubeApiDecoder.BatchDecodeResult(result.signatures, result.nParameters)
    }

    /** Discard the cached player table and solver after the CDN rejected a URL we deciphered. */
    fun invalidate() = runBlocking { engine.invalidate() }

    private val bridgedLogger =
        InnerTubeLogger { event ->
            val message = event.message + event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> SimpStreamLog.d(TAG, message)
                InnerTubeLogLevel.INFO -> SimpStreamLog.i(TAG, message)
                InnerTubeLogLevel.WARN -> SimpStreamLog.w(TAG, message)
                InnerTubeLogLevel.ERROR -> SimpStreamLog.e(TAG, message)
            }
        }
}
