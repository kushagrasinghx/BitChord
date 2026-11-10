/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.constants.AiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

open class AiServiceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object AiTextService {
    private const val TAG = "AiTextService"
    private const val OpenAiEndpoint = "https://api.openai.com/v1/chat/completions"
    private const val OpenAiModelsEndpoint = "https://api.openai.com/v1/models"
    private const val OpenRouterEndpoint = "https://openrouter.ai/api/v1/chat/completions"
    private const val OpenRouterModelsEndpoint = "https://openrouter.ai/api/v1/models"
    private const val MistralEndpoint = "https://api.mistral.ai/v1/chat/completions"
    private const val MistralModelsEndpoint = "https://api.mistral.ai/v1/models"
    private const val GeminiBaseEndpoint = "https://generativelanguage.googleapis.com/v1beta"

    private val clientHolder = AtomicReference<HttpClient>(createClient())

    private fun createClient(): HttpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)

                    connectionPool(
                        okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS),
                    )
                }
            }
        }

    private val client: HttpClient get() = clientHolder.get()

    private fun recreateClientOnFailure(t: Throwable) {
        val oldClient = clientHolder.getAndSet(createClient())
        Log.w(TAG, "Recreated HttpClient after connection failure: ${t.javaClass.simpleName}: ${t.message}")

        Thread {
            runCatching { oldClient.close() }
        }.start()
    }

    private fun isConnectionLevelFailure(t: Throwable): Boolean =
        when (t) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.SocketException -> true
            is javax.net.ssl.SSLException -> true
            is java.net.UnknownHostException -> true
            is java.io.IOException -> true
            else -> {
                val cause = t.cause
                cause != null && cause !== t && isConnectionLevelFailure(cause)
            }
        }

    suspend fun test(config: AiServiceConfig) {
        if (config.provider == AiProvider.DEEPL) {
            val translated = translateWithDeepL(config, "English", listOf("OK"))
            if (translated.isEmpty() || translated[0].isBlank()) {
                throw AiServiceException("DeepL returned an empty test response")
            }
            return
        }
        val response =
            complete(
                config = config.copy(model = config.model.ifBlank { defaultModelFor(config.provider) }),
                systemPrompt = "You are a health check endpoint. Reply with OK only.",
                userPrompt = "Reply exactly OK.",
                temperature = 0.0,
                maxTokens = 32,
            ).trim()
        if (!response.contains("OK", ignoreCase = true)) {
            throw AiServiceException("AI API returned an unexpected test response")
        }
    }

    suspend fun translateLines(
        config: AiServiceConfig,
        targetLanguage: String,
        lines: List<String>,
        formatName: String,
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        if (config.provider == AiProvider.DEEPL) {
            return AiRateLimiter.withLimit(AiRateLimiter.Feature.LYRICS_TRANSLATION) {
                translateWithDeepL(config, targetLanguage, lines)
            }
        }
        val payload = JSONArray()
        lines.forEach { payload.put(it) }
        val response =
            try {
                AiRateLimiter.withLimit(AiRateLimiter.Feature.LYRICS_TRANSLATION) {
                    complete(
                        config = config,
                        systemPrompt =
                            """
                            You are an expert song lyrics translator.
                            Translate each input string into $targetLanguage with natural, accurate lyric phrasing.
                            Preserve meaning, tone, profanity level, names, repeated hooks, and line-level intent.
                            Do not add timestamps, IDs, XML, markdown, explanations, or extra lines.
                            Return only a JSON array of strings with exactly ${lines.size} items in the same order.
                            The caller will reconstruct the $formatName lyrics container separately.
                            """.trimIndent(),
                        userPrompt = payload.toString(),
                        temperature = 0.15,
                        maxTokens = 8192,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (isConnectionLevelFailure(t)) {
                    recreateClientOnFailure(t)
                }
                throw t
            }
        val array = extractJsonArray(response)
        require(array.length() == lines.size) { "AI response changed the lyric segment count" }
        return List(array.length()) { index -> array.optString(index) }
    }

    suspend fun romanizeLines(
        config: AiServiceConfig,
        lines: List<String>,
        formatName: String,
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        val payload = JSONArray()
        lines.forEach { payload.put(it) }
        val response =
            try {
                AiRateLimiter.withLimit(AiRateLimiter.Feature.LYRICS_ROMANIZATION) {
                    complete(
                        config = config,
                        systemPrompt =
                            """
                            You are an expert lyrics romanisation (transliteration) assistant.
                            Transliterate each input string into the Latin alphabet exactly as it is sung.
                            DO NOT TRANSLATE. Never convey meaning — only how the words sound.
                            Use the standard scheme for the script: Hepburn for Japanese, Revised
                            Romanization for Korean, Hanyu Pinyin without tone marks for Chinese,
                            IAST-style for Devanagari, and the common romanisation otherwise.
                            Keep the original word order, punctuation and casing style.
                            If a line is already written in the Latin alphabet, return it unchanged.
                            Do not add timestamps, IDs, XML, markdown, explanations, or extra lines.
                            Return only a JSON array of strings with exactly ${lines.size} items in the same order.
                            The caller will reconstruct the $formatName lyrics container separately.
                            """.trimIndent(),
                        userPrompt = payload.toString(),

                        temperature = 0.0,
                        maxTokens = 8192,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (isConnectionLevelFailure(t)) {
                    recreateClientOnFailure(t)
                }
                throw t
            }
        val array = extractJsonArray(response)
        require(array.length() == lines.size) { "AI response changed the lyric segment count" }
        return List(array.length()) { index -> array.optString(index) }
    }

    suspend fun complete(
        config: AiServiceConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
        maxTokens: Int = 4096,
    ): String {
        if (!config.canCallApi) throw AiServiceException("AI provider is not configured")
        val model = config.model.ifBlank { defaultModelFor(config.provider) }
        return when (config.provider) {
            AiProvider.CHATGPT -> {
                completeOpenAiCompatible(
                    endpoint = OpenAiEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.OPENROUTER -> {
                completeOpenAiCompatible(
                    endpoint = config.customEndpoint.ifBlank { OpenRouterEndpoint },
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.CUSTOM -> {
                completeOpenAiCompatible(
                    endpoint = config.customEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.GEMINI -> {
                completeGemini(
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.MISTRAL -> {
                completeOpenAiCompatible(
                    endpoint = MistralEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.DEEPL -> {
                throw AiServiceException("DeepL is a translation-only provider; it cannot generate text. Use it for lyrics translation, or pick a chat provider for romanisation")
            }

            AiProvider.NONE -> {
                throw AiServiceException("AI provider is disabled")
            }
        }
    }

    suspend fun fetchModels(config: AiServiceConfig): List<AiModelOption> {
        if (!config.canCallApi) throw AiServiceException("AI provider is not configured")
        return when (config.provider) {
            AiProvider.CHATGPT -> fetchOpenAiModels(OpenAiModelsEndpoint, config.apiKey)
            AiProvider.OPENROUTER -> fetchOpenAiModels(OpenRouterModelsEndpoint, config.apiKey)
            AiProvider.MISTRAL -> fetchOpenAiModels(MistralModelsEndpoint, config.apiKey)
            AiProvider.GEMINI -> fetchGeminiModels(config.apiKey)

            AiProvider.DEEPL, AiProvider.CUSTOM, AiProvider.NONE -> emptyList()
        }
    }

    private suspend fun completeOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String {
        val messages =
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
        val body =
            JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .toString()
        val response =
            client.post(endpoint.trim()) {
                header("Authorization", "Bearer ${apiKey.trim()}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val json = JSONObject(raw)
        val content =
            json
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
        return content ?: throw AiServiceException("AI API returned an empty response")
    }

    private suspend fun completeGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String {
        val endpoint = "$GeminiBaseEndpoint/models/${model.trim()}:generateContent?key=${apiKey.trim()}"
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", "$systemPrompt\n\n$userPrompt"),
                            ),
                        ),
                    ),
                ).put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", temperature)
                        .put("maxOutputTokens", maxTokens),
                ).toString()
        val response =
            client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val content =
            JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
        return content ?: throw AiServiceException("AI API returned an empty response")
    }

    private fun defaultModelFor(provider: AiProvider): String =
        when (provider) {
            AiProvider.CHATGPT -> "gpt-4o"
            AiProvider.GEMINI -> "gemini-3.5-flash"
            AiProvider.MISTRAL -> "mistral-small-latest"
            AiProvider.OPENROUTER -> "~openai/gpt-latest"
            AiProvider.CUSTOM -> throw AiServiceException("No AI model configured")

            AiProvider.DEEPL, AiProvider.NONE -> throw AiServiceException("AI provider is disabled")
        }

    private fun deepLEndpoint(apiKey: String): String =
        if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

    private fun deepLLanguageCode(language: String): String =
        when (language.trim().uppercase()) {
            "ENGLISH", "EN", "EN-US", "EN-GB" -> "EN-US"
            "PORTUGUESE", "PT", "PT-BR" -> "PT-BR"
            "CHINESE", "ZH", "ZH-CN", "ZH-TW" -> "ZH"
            "CHINESE (SIMPLIFIED)", "CHINESE_SIMPLIFIED" -> "ZH"
            else ->
                language
                    .trim()
                    .uppercase()
                    .take(2)
                    .takeIf { it.length == 2 && it[0].isLetter() }
                    ?: "EN-US"
        }

    private suspend fun translateWithDeepL(
        config: AiServiceConfig,
        targetLanguage: String,
        lines: List<String>,
    ): List<String> {
        if (config.apiKey.isBlank()) {
            throw AiServiceException("DeepL API key is not configured")
        }
        val texts = JSONArray()
        lines.forEach { texts.put(it) }
        val body = JSONObject()
            .put("text", texts)
            .put("target_lang", deepLLanguageCode(targetLanguage))
        if (config.deepLFormality.isNotBlank() && config.deepLFormality != "default") {
            body.put("formality", config.deepLFormality)
        }
        val response =
            try {
                client.post(deepLEndpoint(config.apiKey)) {
                    header("Authorization", "DeepL-Auth-Key ${config.apiKey}")
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (isConnectionLevelFailure(t)) recreateClientOnFailure(t)
                throw AiServiceException("DeepL request failed: ${t.message}", t)
            }
        val translations =
            runCatching { JSONObject(response).optJSONArray("translations") }
                .getOrNull()
                ?: throw AiServiceException("DeepL returned no translations")
        if (translations.length() < lines.size) {
            throw AiServiceException("DeepL returned fewer lines than requested")
        }
        return List(lines.size) { index -> translations.getJSONObject(index).optString("text") }
    }

    private suspend fun fetchOpenAiModels(
        endpoint: String,
        apiKey: String,
    ): List<AiModelOption> {
        val response =
            client.get(endpoint) {
                header("Authorization", "Bearer ${apiKey.trim()}")
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(AiModelOption(id = id, displayName = id))
            }
        }.sortedBy { it.id }
    }

    private suspend fun fetchGeminiModels(apiKey: String): List<AiModelOption> {
        val response = client.get("$GeminiBaseEndpoint/models?key=${apiKey.trim()}")
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val models = JSONObject(raw).optJSONArray("models") ?: return emptyList()
        return buildList {
            for (i in 0 until models.length()) {
                val obj = models.optJSONObject(i) ?: continue
                val methods = obj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate =
                    (0 until (methods?.length() ?: 0)).any {
                        methods?.optString(it) == "generateContent"
                    }
                if (!supportsGenerate) continue
                val id = obj.optString("name").removePrefix("models/").takeIf { it.isNotBlank() } ?: continue
                val displayName = obj.optString("displayName").ifBlank { id }
                add(AiModelOption(id = id, displayName = displayName))
            }
        }
    }

    private fun apiException(
        status: Int,
        raw: String,
    ): AiServiceException {
        val message =
            runCatching { JSONObject(raw).readErrorMessage() }.getOrNull()
                ?: raw.take(240).ifBlank { "HTTP $status" }
        return AiServiceException("AI API failed ($status): $message")
    }
}
