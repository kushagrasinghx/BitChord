package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The single image a widget draws: the album cover, filling it edge to edge,
 * with its bottom dissolving into a blur for the transport to sit on.
 *
 * All of it is baked into one bitmap because a widget cannot blur anything at
 * runtime — [android.widget.RemoteViews] has no RenderEffect, no Haze, no
 * shaders, and no way to reach a view's render node. So the effect the app gets
 * live from
 * [BottomFadeBlur][com.music.bitchord.ui.components.BottomFadeBlur] has to be
 * drawn here instead, once per track, on the CPU.
 *
 * That component is also where the shape of it comes from, and it is worth
 * repeating its two findings because they are the whole difference between a
 * blur that reads as artwork dissolving and one that reads as a panel stuck over
 * a picture:
 *
 *  - The blur has to **ramp in over a region far taller than the bar it serves**
 *    (180dp of fade for a bar a fraction of that), and spend most of that run
 *    too faint to notice. The long invisible lead-in is what hides the line
 *    where the effect begins. Here that is [BLUR_REGION_SCALE].
 *  - It has to **stop short of full**, because a blur has nothing to sample past
 *    the edge of its own layer, so the harder it is pushed at that edge the more
 *    of what is left is flat colour rather than blurred content — and flat
 *    colour at the bottom of the artwork is exactly the band being avoided.
 *    Here that is the cap on [BLUR_SIGMAS].
 *
 * The ramp is four progressively blurrier copies of the bottom of the cover,
 * drawn back over it softest-first, each masked by a vertical alpha gradient
 * starting lower than the last — which adds up to a blur that accelerates
 * downwards. Each copy is a separable box blur ([blurInPlace]) run on a
 * quarter-ish-scale working image and sampled back up.
 *
 * The strengths cannot come from the downscale itself — from a mip pyramid, the
 * obvious cheap trick. Halving does average each 2×2 block, so a mip really is
 * blurred, but it holds one *sample* per block, and reconstructing a full-size
 * image from samples that far apart is bilinear interpolation between them: at
 * the strengths a band this size needs, the last mip is a handful of pixels
 * across and what lands on screen is its grid, as big soft rectangles. Blurring
 * *after* the downscale instead is what avoids that — it leaves the working
 * image with no detail finer than its own pixels, which is precisely the
 * condition under which sampling back up adds nothing visible.
 */
internal object MediaWidgetArt {

    /**
     * Draws the widget's artwork at exactly [widthPx] × [heightPx].
     *
     * [bandPx] is the height of the transport strip the layout will lay over the
     * result — the blur is sized from it, so the two stay locked together. See
     * `@dimen/widget_band_compact`.
     *
     * [key] identifies the track this is for, and is what the composite is
     * remembered under. Pass null only when there is nothing to remember (no
     * track at all), so the placeholder isn't cached under a shared name.
     */
    suspend fun render(
        context: Context,
        artworkUrl: String?,
        widthPx: Int,
        heightPx: Int,
        bandPx: Int,
        key: String?,
        cornerRadiusPx: Float,
    ): Bitmap {
        peek(key, widthPx, heightPx, bandPx)?.let { return it }
        val cacheKey = key?.let { cacheKey(it, widthPx, heightPx, bandPx) }

        val cover = if (isCoolingOff(key)) null else loadArtwork(context, artworkUrl, maxOf(widthPx, heightPx))
        // A cover that was asked for and didn't arrive is a failure, not an
        // answer. The composite is still drawn — the caller has a widget to
        // fill either way — but it must not be remembered, or one dropped
        // connection would pin the placeholder to this track for as long as the
        // process lives and no amount of reconnecting would shift it. See the
        // `put` at the end.
        val failed = cover == null && !artworkUrl.isNullOrBlank()
        if (failed && key != null) failures[key] = SystemClock.elapsedRealtime()
        val composed = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        if (cover != null) canvas.fillCentreCropped(cover) else canvas.fillPlaceholder()

        // Taller than the band, so the ramp has room to start invisibly above
        // it — but never taller than the widget. On two cells the band is more
        // than half the height and this clamp binds, which is fine: the ramp's
        // first stop is a quarter of the way down and the artwork above it is
        // untouched.
        val blurRegion = (bandPx * BLUR_REGION_SCALE).coerceIn(1, heightPx)
        canvas.blurBottom(composed, blurRegion, bandPx)
        canvas.scrimBottom(bandPx)

        val rounded = composed.withRoundedCorners(cornerRadiusPx)
        composed.recycle()
        if (!failed) cacheKey?.let { composites.put(it, rounded) }
        return rounded
    }

    /**
     * The composite for these arguments if it has already been drawn, without
     * drawing it if it hasn't.
     *
     * Lets a provider find out, on the thread it was called on, whether it can
     * push a finished widget in one go. Without it every update would have to
     * push the controls first and the artwork second, and the gap between the
     * two shows: a play/pause tap — which changes one glyph and nothing else —
     * would blink the cover away and back again.
     */
    fun peek(key: String?, widthPx: Int, heightPx: Int, bandPx: Int): Bitmap? =
        key?.let { composites[cacheKey(it, widthPx, heightPx, bandPx)] }?.takeIf { !it.isRecycled }

    /**
     * The cover cut to a [sizePx] circle, for the 4×1 widget.
     *
     * Cut here rather than by the host, because clipping a RemoteViews image to
     * its outline only exists from API 31. Null when there is no picture to
     * draw, so the layout's own placeholder disc shows instead. Shares the
     * failure cool-off and the cache with [render], under a key of its own.
     */
    suspend fun circle(context: Context, artworkUrl: String?, sizePx: Int, key: String): Bitmap? {
        peekCircle(key, sizePx)?.let { return it }
        if (isCoolingOff(key)) return null
        val cover = loadArtwork(context, artworkUrl, sizePx)
        if (cover == null) {
            if (!artworkUrl.isNullOrBlank()) failures[key] = SystemClock.elapsedRealtime()
            return null
        }
        val square = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(square).fillCentreCropped(cover)
        val round = square.withRoundedCorners(sizePx / 2f)
        square.recycle()
        composites.put(circleKey(key, sizePx), round)
        return round
    }

    /** [circle]'s result if it has already been drawn. @see peek */
    fun peekCircle(key: String, sizePx: Int): Bitmap? =
        composites[circleKey(key, sizePx)]?.takeIf { !it.isRecycled }

    private fun circleKey(key: String, sizePx: Int) = "circle|$key|$sizePx"

    /**
     * The 4×1 widget's body: the cover filling a [widthPx] × [heightPx] capsule,
     * blurred until only its colours are left and darkened just enough for white
     * text — the backdrop the player's lyrics and queue stand on
     * ([ArtworkMeshBackdrop][com.music.bitchord.ui.player.ArtworkMeshBackdrop]),
     * cut to the widget's shape.
     *
     * The blur runs on a working image a few dozen pixels across and is drawn
     * back up. That is the soft-rectangle trap [blurBottom] warns about only
     * when the working image still has detail finer than its pixels; blurred
     * this hard it has none, so sampling it up is invisible. Null key or a
     * failed cover gives the placeholder gradient, uncached.
     */
    suspend fun pill(context: Context, artworkUrl: String?, widthPx: Int, heightPx: Int, key: String?): Bitmap {
        key?.let { peekPill(it, widthPx, heightPx) }?.let { return it }
        val cover = if (key == null || isCoolingOff(key)) null else loadArtwork(context, artworkUrl, ROW_ART_PX)
        val failed = cover == null && !artworkUrl.isNullOrBlank()
        if (failed && key != null) failures[key] = SystemClock.elapsedRealtime()

        val composed = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        if (cover == null) {
            canvas.fillPlaceholder()
        } else {
            val workW = PILL_WORKING_WIDTH_PX
            val workH = (workW * heightPx / widthPx.coerceAtLeast(1)).coerceAtLeast(4)
            // Cropped at 4× the working size and halved down, so the reduction
            // averages the whole cover rather than sampling a sparse grid of it.
            val crop = Bitmap.createBitmap(workW * 4, workH * 4, Bitmap.Config.ARGB_8888)
            Canvas(crop).fillCentreCropped(cover)
            val small = crop.halvedTo(workH.toFloat())
            if (small !== crop) crop.recycle()
            val w = small.width
            val h = small.height
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            blurInPlace(pixels, IntArray(pixels.size), w, h, (w * PILL_BLUR_FRACTION).roundToInt().coerceAtLeast(1))
            small.setPixels(pixels, 0, w, 0, 0, w, h)
            canvas.drawBitmap(
                small,
                Rect(0, 0, w, h),
                Rect(0, 0, widthPx, heightPx),
                Paint().apply { isFilterBitmap = true },
            )
            small.recycle()
        }
        // Heavier than the player's 6–30%: that backdrop carries large type,
        // this one a 16sp title and thin glyphs, over sleeves that can be white.
        canvas.drawRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, heightPx.toFloat(),
                    0x40000000, 0x70000000, Shader.TileMode.CLAMP,
                )
            },
        )

        // A little of the wallpaper through it too, so the capsule sits on
        // the home screen rather than being pasted over it.
        val rounded = composed.withRoundedCorners(heightPx / 2f, PILL_ALPHA)
        composed.recycle()
        if (!failed && key != null) composites.put(pillKey(key, widthPx, heightPx), rounded)
        return rounded
    }

    /** [pill]'s result if it has already been drawn. @see peek */
    fun peekPill(key: String, widthPx: Int, heightPx: Int): Bitmap? =
        composites[pillKey(key, widthPx, heightPx)]?.takeIf { !it.isRecycled }

    private fun pillKey(key: String, widthPx: Int, heightPx: Int) = "pill|$key|$widthPx|$heightPx"

    /** Drops every remembered composite — the last widget has just been removed. */
    fun clear() {
        composites.evictAll()
        failures.clear()
    }

    /**
     * Whether this cover failed recently enough that it isn't worth asking for
     * again yet.
     *
     * The counterweight to not caching failures. A widget redraws on every play
     * and every pause as well as on every track change, so a cover that is not
     * merely slow but genuinely gone — a dead URL, a source that has moved its
     * images — would otherwise be re-fetched, twice over with the fallback, on
     * every tap of the play button. Long enough that a jammed play/pause finger
     * costs one attempt; short enough that walking back into Wi-Fi fixes the
     * widget on the next thing that happens rather than on the next restart.
     */
    private fun isCoolingOff(key: String?): Boolean {
        val failedAt = key?.let { failures[it] } ?: return false
        val since = SystemClock.elapsedRealtime() - failedAt
        // Also the way back out: a clock that has gone backwards (elapsedRealtime
        // does not, but a stale entry from before a process restart would read
        // that way) must not read as "failed in the future" and cool off forever.
        if (since in 0 until FAILURE_COOLDOWN_MS) return true
        failures.remove(key)
        return false
    }

    private fun cacheKey(key: String, widthPx: Int, heightPx: Int, bandPx: Int) =
        "$key|$widthPx|$heightPx|$bandPx"

    // ---- artwork ----

    private suspend fun loadArtwork(context: Context, url: String?, longestSidePx: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        val px = artPxFor(longestSidePx)
        // Second choice, and only when the first misses: the size the media
        // session itself asked for. A widget wider than [NOTIFICATION_ART_PX]
        // lands on a rung above it, which is the right size to *draw* but not
        // one this track is guaranteed to already have on disk — so with no
        // network the correct size fails and the widget falls back to the
        // gradient while the notification, the player and the lock screen are
        // all showing the cover from cache. Asked for second rather than
        // first because on a phone it is an upscale, and one worth avoiding
        // whenever the better copy can actually be had.
        val fallbackPx = NOTIFICATION_ART_PX.takeIf { it != px }
        return load(context, url, px) ?: fallbackPx?.let { load(context, url, it) }
    }

    private suspend fun load(context: Context, url: String, px: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            // Through the app's own size ladder, so this shares a disk-cache
            // entry with the rows, cards, headers and player already drawing the
            // same cover instead of pulling a widget-sized copy of its own over
            // the wire. Local artwork (content://…/albumart/…) carries no size
            // hint and passes through untouched.
            .data(url.artworkAt(px) ?: url)
            .size(px)
            .allowHardware(false) // the blur below reads pixels
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    /**
     * The smallest size in the app's existing artwork ladder that still covers a
     * widget this big.
     *
     * Deliberately not the widget's own pixel width. A size nothing else in the
     * app asks for is a cache entry nothing else in the app fills, so the widget
     * would fetch its own copy of every cover over the network; landing on one of
     * these means the artwork is usually already on disk — and for the playing
     * track, [NOTIFICATION_ART_PX] is the size the media session itself
     * requested, so it is certainly there. A 720px cover in an 860px-wide widget
     * is a 1.2× upscale that no one can see.
     *
     * [PLAYER_ART_PX] is on the ladder for exactly the same reason as the rest of
     * it, and it matters most at the top: a tablet-sized instance is wider than
     * every other rung, so without it the largest widgets were the ones certain
     * to fetch a size of their own. It is what the full player asks for, so a
     * widget that big now shares the player's copy instead.
     */
    private fun artPxFor(longestSidePx: Int): Int = when {
        longestSidePx <= ROW_ART_PX -> ROW_ART_PX
        longestSidePx <= CARD_ART_PX -> CARD_ART_PX
        longestSidePx <= NOTIFICATION_ART_PX -> NOTIFICATION_ART_PX
        longestSidePx <= HEADER_ART_PX -> HEADER_ART_PX
        else -> PLAYER_ART_PX
    }

    /** Fills the canvas with [src], cropped from its centre rather than squashed. */
    private fun Canvas.fillCentreCropped(src: Bitmap) {
        val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val sampleW = (width / scale).coerceAtMost(src.width.toFloat())
        val sampleH = (height / scale).coerceAtMost(src.height.toFloat())
        val left = (src.width - sampleW) / 2f
        val top = (src.height - sampleH) / 2f
        drawBitmap(
            src,
            Rect(
                left.toInt(),
                top.toInt(),
                (left + sampleW).toInt(),
                (top + sampleH).toInt(),
            ),
            Rect(0, 0, width, height),
            Paint().apply { isFilterBitmap = true },
        )
    }

    /**
     * What stands in for a cover there isn't one of: a track with no artwork, a
     * fetch that failed, or nothing ever played.
     *
     * Run through the blur and scrim like real artwork rather than short-circuited
     * past them — one code path, and a gradient blurs to itself, so it costs
     * nothing to leave it in.
     */
    private fun Canvas.fillPlaceholder() {
        drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFF2E3446.toInt(), 0xFF1B2130.toInt(), 0xFF07090E.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    // ---- the blur ----

    /**
     * Blurs the bottom [regionPx] of [source], accelerating downwards.
     *
     * [source] must be the bitmap this canvas draws into: the working image is
     * built from what has already been drawn, and then drawn back over it.
     *
     * [bandPx] is what the strengths are measured against, rather than the region
     * they are spread over. The band is a fixed height in dp, so a blur sized
     * from it looks the same on every widget; sized from the region it would
     * weaken on exactly the small widget where the region had to be clamped and
     * the blur matters most.
     */
    private fun Canvas.blurBottom(source: Bitmap, regionPx: Int, bandPx: Int) {
        val top = source.height - regionPx
        val region = runCatching {
            Bitmap.createBitmap(source, 0, top, source.width, regionPx)
        }.getOrNull() ?: return

        // Blurring at full size would be several hundred thousand pixels per
        // pass for detail that is about to be thrown away regardless. Halved
        // first, every radius below is a quarter of the work per halving and
        // covers four times as much of the picture.
        //
        // How far down is set by the *weakest* level, not by a fixed size: what
        // makes sampling the result back up invisible is that it holds no detail
        // finer than its own pixels, and that is only true of a level whose blur
        // is at least a pixel or so wide. Halve past that and the mildest level
        // is a sharp thumbnail stretched over the widget — which is the blocky
        // bilinear grid this whole approach exists to avoid, showing up in the
        // one band where that level is the only one drawn.
        val floorPx = regionPx * MIN_WORKING_SIGMA / (BLUR_SIGMAS.first() * bandPx)
        val small = region.halvedTo(floorPx)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        if (w >= 2 && h >= 2) small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== region) small.recycle()
        // Not if it came back as [source] itself — which `createBitmap` is
        // allowed to do when the subset is the whole bitmap, and which on a
        // two-cell widget it is. Recycling that would destroy the very bitmap
        // this canvas draws into.
        if (region !== source) region.recycle()
        if (w < 2 || h < 2) return

        // Real pixels to working ones. The two axes are scaled alike, so one
        // factor does for both.
        val toWorking = h.toFloat() / regionPx

        val scratch = IntArray(pixels.size)
        // One working bitmap for all four levels, refilled before each. Safe
        // because a canvas over a bitmap rasterises on the calling thread: the
        // draw below has finished reading these pixels before the next level
        // overwrites them.
        val level = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { isFilterBitmap = true }

        var applied = 0f
        for (index in BLUR_SIGMAS.indices) {
            // Each level carries on from the last rather than starting over.
            // Blurs compose, and their sigmas add in quadrature, so reaching the
            // next strength costs only the difference — which is why four levels
            // are barely dearer than the strongest one alone.
            val target = BLUR_SIGMAS[index] * bandPx * toWorking
            val radius = boxRadiusFor(sqrt((target * target - applied * applied).coerceAtLeast(0f)))
            if (radius >= 1) {
                blurInPlace(pixels, scratch, w, h, radius)
                val step = sigmaOf(radius)
                applied = sqrt(applied * applied + step * step)
            }
            level.setPixels(pixels, 0, w, 0, 0, w, h)

            // Sampled up by the shader rather than scaled into a region-sized
            // bitmap first: four of those would be four full-size allocations
            // for images that are only ever read once.
            val soft = BitmapShader(level, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setScale(
                            source.width.toFloat() / w,
                            regionPx.toFloat() / h,
                        )
                        postTranslate(0f, top.toFloat())
                    },
                )
            }
            // Where this level fades in. Only the gradient's alpha matters —
            // DST_IN keeps the blurred copy in proportion to it.
            val start = top + STOPS[index] * regionPx
            val end = top + (STOPS[index] + STOP_FEATHER).coerceAtMost(1f) * regionPx
            val mask = LinearGradient(
                0f, start, 0f, maxOf(end, start + 1f),
                Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP,
            )
            paint.shader = ComposeShader(soft, mask, PorterDuff.Mode.DST_IN)
            drawRect(0f, top.toFloat(), source.width.toFloat(), source.height.toFloat(), paint)
        }
        level.recycle()
    }

    /**
     * [this] halved until another halving would take it below [target] pixels
     * tall — or [this] itself, if it is already that small.
     *
     * Halving is both the cheap way down and a real low-pass on the way: a
     * bilinear downscale by exactly two averages each 2×2 block. Dropping
     * straight to the target size in one step would still sample only 2×2, so
     * most of the picture would never be looked at and the result would alias —
     * which on a moving queue of covers is visible as the band flickering
     * between tracks that ought to look alike.
     */
    private fun Bitmap.halvedTo(target: Float): Bitmap {
        var current = this
        while (current.height / 2 >= target && current.width / 2 >= 2) {
            val next = Bitmap.createScaledBitmap(
                current,
                current.width / 2,
                current.height / 2,
                true,
            )
            if (current !== this) current.recycle()
            current = next
        }
        return current
    }

    /**
     * Blurs [pixels] in place, using [scratch] as the intermediate.
     *
     * A separable box filter run [BLUR_PASSES] times, which is the standard
     * cheap stand-in for a Gaussian — three passes are within a percent of one,
     * and this costs a handful of integer adds per pixel with no kernel to walk,
     * because each output reuses the previous window's sum.
     *
     * The source is opaque (it was cut out of the fully painted cover), so only
     * the three colour channels are carried through and alpha is written back
     * solid.
     */
    private fun blurInPlace(pixels: IntArray, scratch: IntArray, w: Int, h: Int, radius: Int) {
        val r = radius.coerceAtMost(maxOf(1, minOf(w, h) - 1))
        repeat(BLUR_PASSES) {
            boxPass(pixels, scratch, lines = h, lineStride = w, span = w, step = 1, radius = r)
            boxPass(scratch, pixels, lines = w, lineStride = 1, span = h, step = w, radius = r)
        }
    }

    /**
     * One box-filter pass from [src] to [dst], along whichever axis the strides
     * describe: rows are `lineStride = w, step = 1`, columns the reverse.
     */
    private fun boxPass(
        src: IntArray,
        dst: IntArray,
        lines: Int,
        lineStride: Int,
        span: Int,
        step: Int,
        radius: Int,
    ) {
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[base + i.coerceIn(0, span - 1) * step]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (i in 0 until span) {
                dst[base + i * step] =
                    OPAQUE or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                // Slide the window on by one: drop what leaves the near end,
                // take what enters the far one. Both ends clamp, so the edges
                // hold their own colour instead of averaging in nothing and
                // darkening — a blur that fades to black at the bottom of the
                // artwork would be the band this whole thing exists to avoid.
                val gone = src[base + (i - radius).coerceIn(0, span - 1) * step]
                val come = src[base + (i + radius + 1).coerceIn(0, span - 1) * step]
                r += ((come shr 16) and 0xFF) - ((gone shr 16) and 0xFF)
                g += ((come shr 8) and 0xFF) - ((gone shr 8) and 0xFF)
                b += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }

    /** The Gaussian sigma that [BLUR_PASSES] box passes of this radius add up to. */
    private fun sigmaOf(radius: Int): Float =
        sqrt(BLUR_PASSES * (radius.toFloat() * radius + radius) / 3f)

    /** [sigmaOf] backwards: the radius to pass to reach this sigma. */
    private fun boxRadiusFor(sigma: Float): Int =
        ((-1f + sqrt(1f + 12f * sigma * sigma / BLUR_PASSES)) / 2f).roundToInt()

    // ---- scrim and corners ----

    /**
     * The darkening under the transport.
     *
     * The blur is what makes the band belong to the artwork; this is what makes
     * the glyphs legible on top of it. Both are needed: blur alone leaves white
     * icons invisible over a pale sleeve, and a scrim alone is the hard-edged
     * panel being avoided. Weighted towards the very bottom so the artwork keeps
     * as much of its own brightness as it can.
     */
    private fun Canvas.scrimBottom(bandPx: Int) {
        val top = (height - bandPx * SCRIM_SCALE).coerceAtLeast(0f)
        drawRect(
            0f,
            top,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0x40000000, 0xB8000000.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    /**
     * The same bitmap with its corners rounded off.
     *
     * A second bitmap rather than a mask applied in place: clearing the corners
     * of the original means either an un-antialiased `clipPath` or a
     * difference-of-paths draw in CLEAR mode, and both leave a visibly ragged
     * arc. Drawn through a shader instead, the round rect's own antialiasing
     * does the work.
     */
    private fun Bitmap.withRoundedCorners(radiusPx: Float, alpha: Int = 255): Bitmap {
        if (radiusPx <= 0f && alpha == 255) return this
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radiusPx,
            radiusPx,
            Paint().apply {
                isAntiAlias = true
                this.alpha = alpha
                shader = BitmapShader(this@withRoundedCorners, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            },
        )
        return out
    }

    // ---- tuning ----

    /**
     * How much taller than the transport band the blur runs. The band's own
     * height would put the start of the ramp exactly on the band's top edge,
     * which is a line; at twice it, the blur is already well established by the
     * time it reaches the buttons and still imperceptible where it begins.
     *
     * On a two-cell widget the band is more than half the height, so the region
     * hits the top of the widget and the ramp is compressed. That is the right
     * trade at that size — the alternative is a shorter ramp with a visible start.
     */
    private const val BLUR_REGION_SCALE = 2

    /**
     * The four blur strengths, as Gaussian sigmas in fractions of the transport
     * band's height — so they hold at any widget size rather than being tied to a
     * pixel count, and hold across the two layouts, whose bands differ.
     *
     * The top of the range is about a sixth of the band, some 10dp, which is
     * where `BottomFadeBlur` puts its own `PEAK`. Past roughly there a strip this
     * shape has more average than picture left in it and the bottom edge starts
     * reading as flat colour rather than as blurred artwork — the cap the class
     * comment refers to.
     */
    private val BLUR_SIGMAS = floatArrayOf(0.035f, 0.070f, 0.110f, 0.155f)

    /**
     * The least sigma, in working-image pixels, the mildest level is allowed to
     * come out at — and so how far [halvedTo] may go.
     *
     * A blur narrower than about this leaves detail in the working image finer
     * than its own pixels, and stretching that back over the widget is bilinear
     * interpolation between distant samples: the soft-rectangle grid. Above it
     * the level has nothing left to alias. Set with a little margin over 1, since
     * [boxRadiusFor] rounds and a radius that rounds to zero is no blur at all.
     */
    private const val MIN_WORKING_SIGMA = 1.6f

    /**
     * Box-filter passes per level. Three is where a box stops being
     * distinguishable from a Gaussian; two leaves faint straight-edged
     * shoulders around anything bright, which on album art means around every
     * highlight.
     */
    private const val BLUR_PASSES = 3

    /** Alpha channel for the opaque pixels [boxPass] writes. */
    private const val OPAQUE = 0xFF shl 24

    /**
     * Where each level of [BLUR_SIGMAS] begins, as a fraction of the blur
     * region.
     *
     * Front-loaded rather than evenly spread: the gaps narrow going down, so the
     * blur accelerates. At the top of the region there is nothing at all; by the
     * band's top edge the mildest level is fully in and the next is arriving; by
     * the glyphs the middle two are both fully in. Only the last few pixels of
     * the widget see the strongest.
     */
    private val STOPS = floatArrayOf(0.28f, 0.50f, 0.70f, 0.86f)

    /** How far below its stop a level takes to arrive in full. */
    private const val STOP_FEATHER = 0.26f

    /** [pill]'s opacity: mostly the blurred cover, with some wallpaper through it. */
    private const val PILL_ALPHA = 210

    /** Width of the working image [pill] blurs on. Height follows the capsule. */
    private const val PILL_WORKING_WIDTH_PX = 64

    /**
     * [pill]'s box radius as a fraction of the working width. At a tenth, three
     * passes spread each colour across about a third of the capsule — the sleeve
     * reads as its colours, not as a picture, like the player's backdrop.
     */
    private const val PILL_BLUR_FRACTION = 0.1f

    /** How far above the band the scrim starts, in bands. */
    private const val SCRIM_SCALE = 1.2f

    /**
     * Finished composites, by track and size.
     *
     * Worth keeping because most widget updates do not change the picture at
     * all: a play/pause tap swaps one glyph, and re-deriving the artwork for it
     * would mean a cover decode and four scaling passes to draw the identical
     * bitmap again. Sized for a handful of widgets' worth at phone resolutions.
     */
    private val composites = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** When each cover last failed to load. @see isCoolingOff */
    private val failures = ConcurrentHashMap<String, Long>()

    /** @see isCoolingOff */
    private const val FAILURE_COOLDOWN_MS = 30_000L
}
