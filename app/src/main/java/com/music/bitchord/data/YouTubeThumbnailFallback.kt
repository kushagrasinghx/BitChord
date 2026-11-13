package com.music.bitchord.data

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import com.music.bitchord.data.model.youTubeThumbnailFallback
import java.util.concurrent.ConcurrentHashMap

/**
 * Falls back to a YouTube video's `hqdefault` when the larger cover
 * [artworkAt][com.music.bitchord.data.model.artworkAt] asked for isn't there.
 *
 * The player, a detail header, the widget and the watch ask for
 * `maxresdefault` or `sddefault`, which only videos uploaded at that
 * resolution have. An old or low-resolution upload answers 404 for both, and
 * without this its cover went blank in exactly the places it is drawn largest,
 * where `hqdefault` had shown before.
 *
 * A URL found missing is remembered for the process, so the same cover isn't
 * asked for in vain on every recomposition. Every other request passes through
 * untouched.
 */
class YouTubeThumbnailFallback : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed()
        val fallback = youTubeThumbnailFallback(url) ?: return chain.proceed()
        if (url !in missing) {
            val result = chain.proceed()
            if (result !is ErrorResult) return result
            missing.add(url)
        }
        return chain.withRequest(request.newBuilder().data(fallback).build()).proceed()
    }

    private companion object {
        val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
