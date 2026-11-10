/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AiLyricsTranslator {
    suspend fun translate(
        config: AiServiceConfig,
        lyrics: String,
        targetLanguage: String,
    ): String {
        val normalizedLanguage = normalizeTargetLanguage(targetLanguage)

        val cacheKey = "${config.provider}|${config.model}|$normalizedLanguage|${lyrics.length}|${lyrics.hashCode()}"
        synchronized(resultCache) { resultCache[cacheKey] }?.let { return it }

        val document = AiLyricsDocumentParser.parse(lyrics)
        if (document.segments.isEmpty()) return lyrics
        val translated = mutableMapOf<Int, String>()

        val batches = document.segments.chunkedByBudget()
        val batchResults =
            if (batches.size <= 1) {
                listOf(
                    translateBatchResilient(
                        config = config,
                        targetLanguage = normalizedLanguage,
                        batch = batches.firstOrNull().orEmpty(),
                        formatName = document.formatName,
                    ),
                )
            } else {
                coroutineScope {
                    val gate = Semaphore(MaxConcurrentBatches)
                    batches.map { batch ->
                        async {
                            gate.withPermit {
                                translateBatchResilient(
                                    config = config,
                                    targetLanguage = normalizedLanguage,
                                    batch = batch,
                                    formatName = document.formatName,
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
        batches.forEachIndexed { batchIndex, batch ->
            val batchTranslations = batchResults[batchIndex]
            batch.forEachIndexed { index, segment ->
                translated[segment.id] = batchTranslations.getOrNull(index) ?: segment.text
            }
        }
        return document.rebuild(translated).also { result ->
            synchronized(resultCache) { resultCache[cacheKey] = result }
        }
    }

    private suspend fun translateBatchResilient(
        config: AiServiceConfig,
        targetLanguage: String,
        batch: List<AiLyricsSegment>,
        formatName: String,
    ): List<String> {
        if (batch.isEmpty()) return emptyList()
        return try {
            val result =
                AiTextService.translateLines(
                    config = config,
                    targetLanguage = targetLanguage,
                    lines = batch.map { it.text },
                    formatName = formatName,
                )
            if (result.size == batch.size) {
                result
            } else {
                throw AiServiceException("AI response changed the lyric segment count")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (batch.size <= 1) {
                batch.map { it.text }
            } else {
                val mid = batch.size / 2
                val left =
                    translateBatchResilient(
                        config = config,
                        targetLanguage = targetLanguage,
                        batch = batch.subList(0, mid),
                        formatName = formatName,
                    )
                val right =
                    translateBatchResilient(
                        config = config,
                        targetLanguage = targetLanguage,
                        batch = batch.subList(mid, batch.size),
                        formatName = formatName,
                    )
                left + right
            }
        }
    }

    private fun List<AiLyricsSegment>.chunkedByBudget(): List<List<AiLyricsSegment>> {
        val chunks = ArrayList<List<AiLyricsSegment>>()
        val current = ArrayList<AiLyricsSegment>()
        var currentChars = 0
        forEach { segment ->
            val nextSize = currentChars + segment.text.length
            if (current.isNotEmpty() && (current.size >= MaxItemsPerBatch || nextSize > MaxCharsPerBatch)) {
                chunks.add(current.toList())
                current.clear()
                currentChars = 0
            }
            current.add(segment)
            currentChars += segment.text.length
        }
        if (current.isNotEmpty()) chunks.add(current.toList())
        return chunks
    }

    private fun normalizeTargetLanguage(language: String): String =
        language
            .ifBlank { "ENGLISH" }
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private companion object {
        const val MaxItemsPerBatch = 80
        const val MaxCharsPerBatch = 8000
        const val MaxConcurrentBatches = 8
        const val MaxCachedTranslations = 32

        val resultCache =
            object : LinkedHashMap<String, String>(MaxCachedTranslations, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
                    size > MaxCachedTranslations
            }
    }
}
