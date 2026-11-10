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

class AiLyricsRomanizer {
    suspend fun romanize(
        config: AiServiceConfig,
        lines: List<String>,
    ): List<String?> {
        if (lines.isEmpty()) return emptyList()

        val cacheKey = "${config.provider}|${config.model}|${lines.size}|${lines.hashCode()}"
        synchronized(resultCache) { resultCache[cacheKey] }?.let { return it }

        val indexed = lines.withIndex().filter { it.value.isNotBlank() }
        val out = arrayOfNulls<String>(lines.size)

        val batches = indexed.chunkedByBudget()
        val batchResults =
            if (batches.size <= 1) {
                listOf(romanizeBatchResilient(config, batches.firstOrNull().orEmpty()))
            } else {
                coroutineScope {
                    val gate = Semaphore(MaxConcurrentBatches)
                    batches.map { batch ->
                        async {
                            gate.withPermit { romanizeBatchResilient(config, batch) }
                        }
                    }.awaitAll()
                }
            }
        batches.forEachIndexed { batchIndex, batch ->
            val romanized = batchResults[batchIndex]
            batch.forEachIndexed { position, entry ->
                val candidate = romanized.getOrNull(position)?.trim()

                out[entry.index] =
                    candidate?.takeIf { it.isNotEmpty() && !it.equals(entry.value.trim(), ignoreCase = true) }
            }
        }

        return out.toList().also { result ->
            synchronized(resultCache) { resultCache[cacheKey] = result }
        }
    }

    private suspend fun romanizeBatchResilient(
        config: AiServiceConfig,
        batch: List<IndexedValue<String>>,
    ): List<String?> {
        if (batch.isEmpty()) return emptyList()
        return try {
            val result =
                AiTextService.romanizeLines(
                    config = config,
                    lines = batch.map { it.value },
                    formatName = "plain text",
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
                listOf(null)
            } else {
                val mid = batch.size / 2
                romanizeBatchResilient(config, batch.subList(0, mid)) +
                    romanizeBatchResilient(config, batch.subList(mid, batch.size))
            }
        }
    }

    private fun List<IndexedValue<String>>.chunkedByBudget(): List<List<IndexedValue<String>>> {
        val chunks = ArrayList<List<IndexedValue<String>>>()
        val current = ArrayList<IndexedValue<String>>()
        var currentChars = 0
        forEach { entry ->
            val nextSize = currentChars + entry.value.length
            if (current.isNotEmpty() && (current.size >= MaxItemsPerBatch || nextSize > MaxCharsPerBatch)) {
                chunks.add(current.toList())
                current.clear()
                currentChars = 0
            }
            current.add(entry)
            currentChars += entry.value.length
        }
        if (current.isNotEmpty()) chunks.add(current.toList())
        return chunks
    }

    private companion object {
        const val MaxItemsPerBatch = 80
        const val MaxCharsPerBatch = 8000
        const val MaxConcurrentBatches = 8
        const val MaxCachedRomanizations = 32

        val resultCache =
            object : LinkedHashMap<String, List<String?>>(MaxCachedRomanizations, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String?>>): Boolean =
                    size > MaxCachedRomanizations
            }
    }
}
