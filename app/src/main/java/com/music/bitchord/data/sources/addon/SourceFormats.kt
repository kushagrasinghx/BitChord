package com.music.bitchord.data.sources.addon

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * What kind of thing is on the end of a URL somebody pasted.
 *
 * The screen used to assume: whatever you typed was an addon whose manifest sat
 * at `/manifest.json`, and if it wasn't you got "that URL answered, but not
 * with an addon manifest" — true, unhelpful, and identical for a JSON file that
 * was simply a different *kind* of index. People paste all sorts of links at a
 * box that says "link".
 *
 * Two ideas run through this file. The first is that a manifest is a
 * *description* of an addon and the endpoints are the addon — so a missing or
 * oddly-named manifest is not a reason to refuse anything, and the last thing
 * tried is always "does `/search` actually answer". The second is that a
 * refusal should say what was found, not just that it wasn't wanted.
 */
sealed interface DetectedFormat {

    /**
     * An addon server — this app's own protocol. See [AddonClient].
     *
     * [manifest] may be one the addon published or one synthesised from a
     * working `/search`; nothing downstream depends on which, because
     * everything that reads it treats every field as optional.
     */
    data class Addon(val manifest: AddonManifest, val baseUrl: String) : DetectedFormat

    /**
     * A module index: JS plugins listed under `category:*` keys, run in the
     * QuickJS sandbox. See [ModuleIndex][com.music.bitchord.data.sources.module.ModuleIndex].
     */
    data class ModuleIndex(val moduleCount: Int, val url: String) : DetectedFormat

    /** Recognised, and not something this app can play. [reason] is shown to the user. */
    data class Unsupported(val reason: String) : DetectedFormat
}

object SourceFormats {

    private const val TAG = "BitChord"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Fetches [rawUrl], works out what it is, and falls back to asking the
     * server directly when no document settles it.
     *
     * Three passes, cheapest first:
     *
     *  1. **The link as given**, when it names a `.json` document. That is the
     *     whole point of accepting any JSON link, and appending `/manifest.json`
     *     to a document would ask for a path that cannot exist.
     *  2. **`{base}/manifest.json`**, the conventional location — tried second
     *     for a `.json` link and first for anything else, since a bare address
     *     is an addon's root.
     *  3. **`{base}/search`**, which is not a document at all but the addon
     *     answering for itself. This is what makes a manifest optional: an
     *     addon that never published one, or published it somewhere nothing
     *     guessed, still works, and there is no honest reason to turn it away
     *     over a missing description of something that demonstrably functions.
     */
    suspend fun identify(rawUrl: String): Result<DetectedFormat> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().trimEnd('/')
        if (url.toHttpUrlOrNull() == null) {
            return@withContext Result.success(
                DetectedFormat.Unsupported("That is not a web address BitChord can open"),
            )
        }

        val base = AddonClient.normalizeBase(url)
        val isDocument = url.substringBefore('?').endsWith(".json", ignoreCase = true)
        val attempts = if (isDocument) {
            listOf(url, AddonClient.manifestUrl(base))
        } else {
            listOf(AddonClient.manifestUrl(url), url)
        }

        var lastFailure: Throwable? = null
        var firstUnsupported: DetectedFormat.Unsupported? = null

        for (attempt in attempts) {
            val body = fetch(attempt).getOrElse { failure ->
                lastFailure = failure
                continue
            }
            when (val found = detect(body, attempt)) {
                is DetectedFormat.Unsupported -> firstUnsupported = firstUnsupported ?: found
                else -> return@withContext Result.success(found)
            }
        }

        // Nothing readable was published. Ask the endpoints instead — this is
        // the pass that lets an addon with no manifest be added at all.
        val probed = AddonClient(base).probeSearch()
        if (probed.isSuccess) {
            TrackLog.d(TAG, "identify(${AddonClient.redact(base)}) — no manifest, but /search answered")
            return@withContext Result.success(
                DetectedFormat.Addon(manifest = synthesised(base), baseUrl = base),
            )
        }

        // Report what was actually found over the transport error from a URL
        // the user never typed.
        firstUnsupported?.let { return@withContext Result.success(it) }
        Result.failure(lastFailure ?: AddonUnavailable("Nothing answered at that address"))
    }

    /**
     * Which format [body] is, decided on its shape alone.
     *
     * Kept pure and separate from the fetching so every branch below is a test
     * against a real payload rather than a live server. Order matters: several
     * of these formats are objects with an array in them, and a looser test
     * would swallow the ones beneath it.
     */
    fun detect(body: String, url: String = ""): DetectedFormat {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return DetectedFormat.Unsupported("That address did not return JSON")

        val obj = root as? JsonObject
            ?: return DetectedFormat.Unsupported(
                "That JSON is a list, and every format BitChord reads is an object",
            )

        // 1. A module index: JS plugins filed under "category:*" keys. First
        //    because its marker is unmistakable and belongs to no other format.
        if (obj.keys.any { it.startsWith("category:") }) {
            val modules = runCatching {
                com.music.bitchord.data.sources.module.ModuleIndex.parseModules(json, body)
            }.getOrDefault(emptyList())
            return if (modules.isEmpty()) {
                DetectedFormat.Unsupported("That module index listed no modules")
            } else {
                DetectedFormat.ModuleIndex(modules.size, url)
            }
        }

        // 2. An addon manifest. Recognised by `resources`, or failing that by
        //    the id/name/version trio every manifest carries — a manifest that
        //    omits `resources` is under-described, not disqualified, and what
        //    it can actually do is settled by asking rather than by reading.
        val looksLikeManifest = obj["resources"] is JsonArray ||
            (obj.containsKey("id") && (obj.containsKey("name") || obj.containsKey("version")))
        if (looksLikeManifest) {
            val manifest = runCatching { json.decodeFromJsonElement(AddonManifest.serializer(), obj) }
                .getOrNull()
                ?: return DetectedFormat.Unsupported("That looks like an addon manifest, but it could not be read")

            // The one thing genuinely required. Everything this app does with
            // an addon starts by asking it for a track by name, so an addon
            // that cannot be searched has nothing to offer — where one that
            // cannot `stream` may still work, since a search row is allowed to
            // carry its own URL. Only enforced when the addon actually said
            // what it does; silence is not a claim to refuse over.
            if (manifest.resources.isNotEmpty() && !manifest.declares("search")) {
                val declared = manifest.resources.joinToString(", ")
                return DetectedFormat.Unsupported(
                    "This addon declares $declared — BitChord needs search",
                )
            }
            return DetectedFormat.Addon(manifest, AddonClient.normalizeBase(url))
        }

        // 3. Recognisably an index of *something*, just not one of ours. Worth
        //    separating from "no idea": it says the link was the right sort of
        //    thing and this is the wrong app for it, rather than sending
        //    someone hunting for a typo in a URL that is perfectly correct.
        val listKey = obj.entries
            .firstOrNull { it.value is JsonArray && (it.value as JsonArray).isNotEmpty() }?.key
        if (listKey != null) {
            return DetectedFormat.Unsupported(
                "That JSON lists \"$listKey\", which is not a format BitChord reads",
            )
        }

        val keys = obj.keys.take(4).joinToString(", ").ifBlank { "nothing" }
        return DetectedFormat.Unsupported("Unrecognised JSON — it holds $keys")
    }

    /**
     * A stand-in for an addon that answered but never described itself.
     *
     * Named for its host, which is the only thing known about it, and declaring
     * the two resources it has just demonstrated it has. The sources screen
     * shows this until the addon publishes something better.
     */
    private fun synthesised(base: String) = AddonManifest(
        id = base,
        name = base.toHttpUrlOrNull()?.host.orEmpty().ifBlank { base },
        resources = listOf("search", "stream"),
    )

    /** One GET, as plain as it gets: this runs before anything is configured. */
    private suspend fun fetch(url: String): Result<String> = runCatching {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "BitChord")
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code >= 500) throw AddonUnavailable("HTTP ${response.code}")
                throw AddonException("HTTP ${response.code}")
            }
            response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw AddonException("Empty response")
        }
    }.onFailure {
        TrackLog.d(TAG, "identify(${AddonClient.redact(url)}) failed: ${it.message}")
    }
}
