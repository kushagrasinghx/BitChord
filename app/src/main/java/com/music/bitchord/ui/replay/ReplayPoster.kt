package com.music.bitchord.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.R
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.stats.ReplaySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Replay as one picture, drawn to a bitmap you can send.
 *
 * ## Why this is drawn rather than screenshotted
 *
 * The obvious way to share a story card is to capture the one on screen. It is
 * also the wrong picture: a story card is one fact, and the thing people
 * actually want to send is the whole year — the minutes *and* the five songs
 * *and* the five artists. A screenshot would also carry the status bar, the
 * progress segments and whatever letterboxing that particular phone needed, so
 * the same Replay would come out looking different on every device.
 *
 * ## Why it isn't Compose
 *
 * Composing offscreen means laying out a composition that is not attached to a
 * window and then reading pixels back out of it, which on this app's minimum SDK
 * is a set of caveats about hardware bitmaps and layer support rather than an
 * API. A canvas is a canvas on every version, and the layout below is fixed —
 * nothing here reflows, so nothing here needs a layout system.
 *
 * The size is 1080 × 1920: exactly the 9:16 the story cards are held to (see
 * `StoryFrame`), so what gets shared is the shape of what was being looked at,
 * and it is the size every messaging app and story surface expects.
 */
suspend fun renderReplayPoster(
    context: Context,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
    /**
     * Which story card to draw, or null for the whole Replay.
     *
     * A card's share button sends the card. That is not the same picture as the
     * summary and should not be: somebody taps share on the genre card because
     * the genre surprised them, and receiving a table of every chart instead is
     * a different message. The Replay page's own button is where "all of it"
     * lives.
     */
    page: ReplayStoryPage? = null,
): Bitmap = coroutineScope {
    // The artwork this needs is a dozen small fetches that are all in Coil's
    // disk cache already — the page they came from has been on screen. Started
    // together rather than in a loop so a cold cache costs one round trip
    // rather than twelve.
    val songs = summary.songRows(POSTER_ROWS)
    val artists = summary.artistRows(POSTER_ROWS)
    val albums = summary.albumRows(POSTER_ALBUMS)
    val covers = (songs + artists + albums)
        .mapNotNull { it.artworkUrl }
        .distinct()
        .associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
        .mapValues { it.value.await() }

    withContext(Dispatchers.Default) {
        val lit = page?.let { summary.storyArtwork(it) }
            ?: songs.firstOrNull()?.artworkUrl
        val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)

        // The same cover and the same turn of the colour wheel the card on
        // screen was drawn with, so the picture is recognisably that card.
        drawBackdrop(canvas, covers[lit], page?.let(::storyHue) ?: 0f)
        var y = drawHeader(canvas, context, type, summary, holder, memberSince)
        if (page == null) {
            y = drawTotals(canvas, context, type, summary, y)
            y = drawColumns(canvas, context, type, songs, artists, covers, y)
            y = drawAlbums(canvas, context, type, albums, covers, y)
            drawGenres(canvas, context, type, summary, y)
        } else {
            y = drawRuns(canvas, type, summary.storyHeadline(context, page), MARGIN, y, CONTENT_W)
            drawCard(canvas, context, type, summary, page, covers, y + 56f)
        }
        drawFooter(canvas, context, type)
        bitmap
    }
}

// ── One story card ──────────────────────────────────────────────────────────

/**
 * The body of [page], under its headline.
 *
 * Deliberately not a pixel copy of the composable — a card on screen fills its
 * height with weighted spacers and this cannot, since it has no measurement pass
 * to spend. What it copies is the *content*: the same sentence, the same hero,
 * the same four runners-up, in the same order. Somebody who sends a card and
 * somebody who saw it are looking at the same facts.
 */
private fun drawCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    page: ReplayStoryPage,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    when (page) {
        ReplayStoryPage.INTRO, ReplayStoryPage.MINUTES ->
            drawCollage(canvas, summary, covers, top)
        ReplayStoryPage.SONGS ->
            drawLeaderboard(canvas, context, type, summary.songRows(CARD_ROWS), covers, top, false)
        ReplayStoryPage.ARTISTS ->
            drawLeaderboard(canvas, context, type, summary.artistRows(CARD_ROWS), covers, top, true)
        ReplayStoryPage.ALBUMS ->
            drawLeaderboard(canvas, context, type, summary.albumRows(CARD_ROWS), covers, top, false)
        ReplayStoryPage.GENRES -> drawBigList(canvas, context, type, summary.genreRows(CARD_ROWS), top)
        ReplayStoryPage.HABITS -> drawHabits(canvas, context, type, summary, top)
        ReplayStoryPage.SUMMARY -> drawRecap(canvas, context, type, summary, top)
    }
}

/** The number one, large, with its runners-up under it. */
private fun drawLeaderboard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    rows: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
    circular: Boolean,
) {
    val lead = rows.firstOrNull() ?: return
    val hero = 348f
    drawArtwork(canvas, lead.artworkUrl?.let { covers[it] }, lead.title, MARGIN, top, hero, circular)

    val textX = MARGIN + hero + 48f
    val textWidth = POSTER_W - MARGIN - textX
    val title = type.heading(78f, Color.WHITE)
    canvas.drawText(ellipsised(lead.title, title, textWidth), textX, top + 86f, title)
    var y = top + 86f
    lead.subtitle?.let {
        val sub = type.body(50f, 0xB3FFFFFF.toInt())
        y += 62f
        canvas.drawText(ellipsised(it, sub, textWidth), textX, y, sub)
    }
    val stats = type.body(44f, 0x8CFFFFFF.toInt())
    canvas.drawText(
        "${formatListening(context, lead.ms)} · ${context.replayCount(lead.plays, R.plurals.replay_play_count)}",
        textX,
        y + 62f,
        stats,
    )

    var rowY = top + hero + 90f
    rows.drop(1).forEach { row ->
        canvas.drawText(
            row.rank.toString(),
            MARGIN,
            rowY + 72f,
            type.heading(44f, 0x73FFFFFF),
        )
        val artX = MARGIN + 62f
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, artX, rowY, 108f, circular)
        val name = type.body(48f, Color.WHITE, bold = true)
        val nameX = artX + 108f + 28f
        val stat = type.body(38f, 0x80FFFFFF.toInt())
        val statWidth = stat.measureText(formatListening(context, row.ms))
        canvas.drawText(
            ellipsised(row.title, name, POSTER_W - MARGIN - nameX - statWidth - 32f),
            nameX,
            rowY + 72f,
            name,
        )
        stat.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatListening(context, row.ms), POSTER_W - MARGIN, rowY + 72f, stat)
        rowY += 148f
    }
}

/** The genre card: one word, big, then the rest as a ranked list. */
private fun drawBigList(canvas: Canvas, context: Context, type: Fonts, rows: List<ReplayRow>, top: Float) {
    val lead = rows.firstOrNull() ?: return
    val word = type.heading(150f, Color.WHITE)
    canvas.drawText(ellipsised(lead.title, word, CONTENT_W), MARGIN, top + 120f, word)
    canvas.drawText(formatListening(context, lead.ms), MARGIN, top + 186f, type.body(46f, 0x99FFFFFF.toInt()))

    var y = top + 300f
    rows.drop(1).forEach { row ->
        canvas.drawText(row.rank.toString(), MARGIN, y, type.heading(48f, 0x73FFFFFF))
        val name = type.body(52f, Color.WHITE, bold = true)
        canvas.drawText(row.title, MARGIN + 80f, y, name)
        val stat = type.body(38f, 0x80FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(formatListening(context, row.ms), POSTER_W - MARGIN, y, stat)
        y += 106f
    }
}

private fun drawHabits(canvas: Canvas, context: Context, type: Fonts, summary: ReplaySummary, top: Float) {
    var y = top + 40f
    fun stat(value: String, label: String) {
        canvas.drawText(value, MARGIN, y, type.heading(84f, Color.WHITE))
        canvas.drawText(label, MARGIN, y + 56f, type.body(42f, 0x99FFFFFF.toInt()))
        y += 176f
    }
    if (summary.distinctAlbums > 0) {
        stat(grouped(summary.distinctAlbums.toLong()), context.getString(R.string.different_albums))
    }
    summary.busiestDay?.let {
        stat(
            formatDay(context, it),
            context.getString(R.string.your_biggest_day, formatListening(context, summary.busiestDayMs)),
        )
    }
    summary.peakHour?.let {
        stat(formatHour(context, it), context.getString(R.string.when_you_listen_most))
    }
}

private fun drawRecap(canvas: Canvas, context: Context, type: Fonts, summary: ReplaySummary, top: Float) {
    var y = top + 40f
    fun line(label: String, value: String) {
        canvas.drawText(label, MARGIN, y, type.body(42f, 0x80FFFFFF.toInt()))
        val v = type.body(52f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(value, v, CONTENT_W - 320f), MARGIN + 320f, y, v)
        y += 96f
    }
    line(context.getString(R.string.minutes), formatMinutes(summary.totalMs))
    summary.songs.firstOrNull()?.let { line(context.getString(R.string.top_song), it.song.title) }
    summary.artists.firstOrNull()?.let { line(context.getString(R.string.top_artist), it.title) }
    summary.albums.firstOrNull()?.let { line(context.getString(R.string.top_album), it.title) }
    summary.genres.firstOrNull()?.let { line(context.getString(R.string.top_genre), it.title) }
}

/** The scatter of covers the opening cards are built around. */
private fun drawCollage(
    canvas: Canvas,
    summary: ReplaySummary,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val squares = summary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(3)
    val faces = summary.artists.mapNotNull { it.artworkUrl }.distinct()
        .filterNot { it in squares }.take(3)
    // Fractions of the content box, so the pile keeps its shape at any size.
    val squareAt = listOf(Triple(0.22f, 0.30f, 500f), Triple(0.02f, 0.06f, 260f), Triple(0.66f, 0.00f, 215f))
    val faceAt = listOf(Triple(0.00f, 0.62f, 185f), Triple(0.72f, 0.32f, 225f), Triple(0.46f, 0.76f, 200f))
    val height = 900f
    squares.forEachIndexed { index, url ->
        val (fx, fy, size) = squareAt[index]
        drawArtwork(canvas, covers[url], "", MARGIN + CONTENT_W * fx, top + height * fy, size, false)
    }
    faces.forEachIndexed { index, url ->
        val (fx, fy, size) = faceAt[index]
        drawArtwork(canvas, covers[url], "", MARGIN + CONTENT_W * fx, top + height * fy, size, true)
    }
}

// ── Background ──────────────────────────────────────────────────────────────

/**
 * The mesh, by hand.
 *
 * Four wide radial gradients off the leading sleeve's own colours, laid over a
 * dark base and then flattened under a vertical scrim — the same recipe the
 * player's backdrop uses, at a size where the radii can simply be written down
 * instead of derived from a layout.
 */
private fun drawBackdrop(canvas: Canvas, lead: Bitmap?, hue: Float) {
    val colors = paletteOf(lead).map { rotated(it, hue) }
    canvas.drawColor(dimmed(colors.first()))

    val anchors = listOf(
        0.20f to 0.16f,
        0.84f to 0.22f,
        0.76f to 0.66f,
        0.18f to 0.78f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    colors.forEachIndexed { index, color ->
        val (fx, fy) = anchors[index]
        val cx = POSTER_W * fx
        val cy = POSTER_H * fy
        val radius = POSTER_W * 0.95f
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 210),
                ColorUtils.setAlphaComponent(color, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    paint.shader = null

    // Ink, weighted to the foot where the smaller type is.
    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        0f,
        POSTER_H.toFloat(),
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, POSTER_W.toFloat(), POSTER_H.toFloat(), paint)
    paint.shader = null
}

/**
 * Four colours off the sleeve, saturated and held to a mid lightness so any
 * artwork yields a rich backdrop rather than a muddy or a blown-out one — the
 * same treatment `MeshGradient` gives its own palette, restated here because
 * this runs nowhere near a composition.
 */
private fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        Palette.from(source).maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
    }.getOrNull().orEmpty()
    if (swatches.isEmpty()) return fallback
    return (swatches + fallback).take(4).map(::tuned)
}

/** [color] turned [degrees] around the wheel, tone untouched — see [storyHue]. */
private fun rotated(color: Int, degrees: Float): Int {
    if (degrees == 0f) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}

private fun tuned(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return ColorUtils.HSLToColor(hsl)
}

private fun dimmed(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = 0.10f
    return ColorUtils.HSLToColor(hsl)
}

// ── Bands ───────────────────────────────────────────────────────────────────

private fun drawHeader(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
): Float {
    val label = summary.localizedLabel(context)
    val title = if (label.length == 4 && label.all { it.isDigit() }) {
        "Replay'${label.takeLast(2)}"
    } else {
        "Replay · $label"
    }
    canvas.drawText(title, MARGIN, 132f, type.heading(46f, 0xFFFFFFFF.toInt()))
    val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply {
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("BitChord", POSTER_W - MARGIN, 132f, brand)
    // The mark, to the left of the word, exactly as the story header and the
    // card carry it. Without it the one artefact of this app that ends up in
    // somebody else's chat was the only place the logo didn't appear.
    val wordWidth = brand.measureText("BitChord")
    drawLogo(canvas, context, POSTER_W - MARGIN - wordWidth - LOGO_GAP, 132f)

    val credit = listOfNotNull(
        holder.ifBlank { context.getString(R.string.default_replay_holder) },
        memberSince?.let { context.getString(R.string.member_since_value, it) },
    ).joinToString(" · ")
    canvas.drawText(credit.uppercase(Locale.ROOT), MARGIN, 182f, type.label(24f, 0x8CFFFFFF.toInt()))
    return 300f
}

private fun drawTotals(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    top: Float,
): Float {
    canvas.drawText(
        formatMinutes(summary.totalMs),
        MARGIN,
        top + 120f,
        type.heading(148f, Color.WHITE),
    )
    canvas.drawText(
        context.getString(R.string.minutes_listened).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top + 176f,
        type.label(30f, 0xB3FFFFFF.toInt(), tracking = 0.14f),
    )
    canvas.drawText(
        "${context.replayCount(summary.totalPlays, R.plurals.replay_play_count)} · " +
            "${context.replayCount(summary.distinctSongs, R.plurals.replay_song_count)} · " +
            context.replayCount(summary.distinctArtists, R.plurals.replay_artist_count),
        MARGIN,
        top + 232f,
        type.body(30f, 0x99FFFFFF.toInt()),
    )
    return top + 320f
}

/**
 * The two charts side by side.
 *
 * Songs on the left because that is what people go looking for first, and
 * artists on the right as circles — the one distinction that needs no heading,
 * since every music app on the device makes it.
 */
private fun drawColumns(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    songs: List<ReplayRow>,
    artists: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    val columnWidth = (POSTER_W - MARGIN * 2 - COLUMN_GAP) / 2f
    val right = MARGIN + columnWidth + COLUMN_GAP

    val locale = context.resources.configuration.locales[0]
    canvas.drawText(
        context.getString(R.string.top_songs).uppercase(locale),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    canvas.drawText(
        context.getString(R.string.top_artists).uppercase(locale),
        right,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )

    val rows = maxOf(songs.size, artists.size)
    var y = top + 54f
    repeat(rows) { index ->
        songs.getOrNull(index)?.let {
            drawRow(canvas, context, type, it, covers, MARGIN, y, columnWidth, false)
        }
        artists.getOrNull(index)?.let {
            drawRow(canvas, context, type, it, covers, right, y, columnWidth, true)
        }
        y += ROW_HEIGHT
    }
    return y + 40f
}

private fun drawRow(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    row: ReplayRow,
    covers: Map<String, Bitmap?>,
    x: Float,
    y: Float,
    width: Float,
    circular: Boolean,
) {
    canvas.drawText(
        row.rank.toString(),
        x,
        y + ART * 0.68f,
        type.heading(34f, if (row.rank == 1) ACCENT else 0x73FFFFFF),
    )
    val artX = x + 44f
    val art = row.artworkUrl?.let { covers[it] }
    drawArtwork(canvas, art, row.title, artX, y, ART, circular)

    val textX = artX + ART + 20f
    val textWidth = width - (textX - x)
    val title = type.body(30f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(row.title, title, textWidth), textX, y + 36f, title)
    val sub = type.body(25f, 0x99FFFFFF.toInt())
    val detail = listOfNotNull(row.subtitle, formatListening(context, row.ms)).joinToString(" · ")
    canvas.drawText(ellipsised(detail, sub, textWidth), textX, y + 72f, sub)
}

private fun drawAlbums(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    albums: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    if (albums.isEmpty()) return top
    canvas.drawText(
        context.getString(R.string.top_albums).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    val size = 200f
    val gap = (POSTER_W - MARGIN * 2 - size * POSTER_ALBUMS) / (POSTER_ALBUMS - 1)
    val y = top + 40f
    albums.take(POSTER_ALBUMS).forEachIndexed { index, album ->
        val x = MARGIN + index * (size + gap)
        drawArtwork(canvas, album.artworkUrl?.let { covers[it] }, album.title, x, y, size, false)
        val name = type.body(25f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(album.title, name, size), x, y + size + 38f, name)
        val sub = type.body(22f, 0x8CFFFFFF.toInt())
        canvas.drawText(
            ellipsised(album.subtitle ?: formatListening(context, album.ms), sub, size),
            x,
            y + size + 70f,
            sub,
        )
    }
    return y + size + 130f
}

private fun drawGenres(canvas: Canvas, context: Context, type: Fonts, summary: ReplaySummary, top: Float) {
    val genres = summary.genreRows(POSTER_GENRES)
    if (genres.isEmpty()) return
    canvas.drawText(
        context.getString(R.string.top_genres).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    val paint = type.body(32f, Color.WHITE, bold = true)
    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    var x = MARGIN
    val y = top + 44f
    genres.forEach { genre ->
        val width = paint.measureText(genre.title) + 44f
        if (x + width > POSTER_W - MARGIN) return
        canvas.drawRoundRect(RectF(x, y, x + width, y + 62f), 31f, 31f, chip)
        canvas.drawText(genre.title, x + 22f, y + 42f, paint)
        x += width + 14f
    }
}

private fun drawFooter(canvas: Canvas, context: Context, type: Fonts) {
    canvas.drawText(
        context.getString(R.string.counted_on_device_with_bitchord),
        MARGIN,
        POSTER_H - 64f,
        type.label(24f, 0x73FFFFFF, tracking = 0.08f),
    )
}

// ── Pieces ──────────────────────────────────────────────────────────────────

/**
 * A cover, cropped square and rounded — or, where one never loaded, the same
 * lettered stand-in the lists use, so a missing sleeve is a deliberate-looking
 * tile rather than a hole.
 */
private fun drawArtwork(
    canvas: Canvas,
    bitmap: Bitmap?,
    fallback: String,
    x: Float,
    y: Float,
    size: Float,
    circular: Boolean,
) {
    val bounds = RectF(x, y, x + size, y + size)
    val radius = if (circular) size / 2f else size * 0.10f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bitmap != null) {
        val scale = size / minOf(bitmap.width, bitmap.height).toFloat()
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                x - (bitmap.width * scale - size) / 2f,
                y - (bitmap.height * scale - size) / 2f,
            )
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    } else {
        val hue = (fallback.hashCode().toFloat() % 360f + 360f) % 360f
        paint.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

/**
 * A card's sentence, wrapped, with the emphasised runs in the heavier face.
 *
 * Laid out by hand because a canvas has no notion of a paragraph made of two
 * typefaces: the runs are broken into words, each word keeps the weight of the
 * run it came from, and they are placed greedily until the next one would not
 * fit. Measuring per word rather than per run is what lets a line break in the
 * middle of the bold part — which every one of these sentences does.
 */
private fun drawRuns(
    canvas: Canvas,
    type: Fonts,
    runs: List<HeadlineRun>,
    x: Float,
    top: Float,
    maxWidth: Float,
): Float {
    val bold = type.heading(HEADLINE_SIZE, Color.WHITE)
    val plain = type.body(HEADLINE_SIZE, 0x9EFFFFFF.toInt(), bold = true)

    // Flattened first, with a weight remembered per character, and only then
    // split on whitespace. Splitting the runs individually looked equivalent and
    // was not: a run ending mid-word — "4 songs" bold followed by ", one was…"
    // plain — became two tokens with a space invented between them, and the
    // sentence read "4 songs , one was your anthem". A word that straddles a
    // weight boundary takes the weight of its first letter, which is why the
    // comma there comes out bold; that is the right way to be wrong, since the
    // alternative is a comma floating a space away from what it punctuates.
    val text = StringBuilder()
    val weights = ArrayList<Boolean>()
    runs.forEach { run ->
        text.append(run.text)
        repeat(run.text.length) { weights += run.bold }
    }

    var lineX = x
    var y = top + HEADLINE_SIZE
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
            continue
        }
        var end = index
        while (end < text.length && !text[end].isWhitespace()) end++
        val word = text.substring(index, end)
        val paint = if (weights[index]) bold else plain
        val width = paint.measureText("$word ")
        if (lineX + width - x > maxWidth && lineX > x) {
            lineX = x
            y += HEADLINE_LEADING
        }
        canvas.drawText(word, lineX, y, paint)
        lineX += width
        index = end
    }
    return y + 24f
}

/**
 * The BitChord mark, baseline-aligned with the word beside it.
 *
 * The same vector the app draws everywhere, tinted and given bounds rather than
 * rasterised to a PNG first — a vector drawable renders into an ordinary canvas
 * on every version this app supports, so there is nothing to be gained by
 * keeping a second copy of the logo around at a fixed size.
 */
private fun drawLogo(canvas: Canvas, context: Context, right: Float, baseline: Float) {
    val logo = runCatching {
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null)
    }.getOrNull() ?: return
    val left = right - LOGO_W
    val top = baseline - LOGO_H
    logo.setTint(0xE6FFFFFF.toInt())
    logo.setBounds(left.toInt(), top.toInt(), (left + LOGO_W).toInt(), (top + LOGO_H).toInt())
    logo.draw(canvas)
}

/** Trims [text] to [width], with an ellipsis, the way a single-line row would. */
private fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

/**
 * The app's own face, on a canvas.
 *
 * The poster is the one thing from this app that ends up somewhere else, so it
 * has more reason than any screen to be set in the type the app is set in.
 * Falls back to the platform sans if a weight can't be loaded, which keeps a
 * missing font resource a slightly plainer picture rather than a crash on the
 * share button.
 */
private class Fonts(context: Context) {
    private val heavy = font(context, R.font.sf_pro_display_heavy) ?: Typeface.DEFAULT_BOLD
    private val semibold = font(context, R.font.sf_pro_display_semibold) ?: Typeface.DEFAULT_BOLD
    private val regular = font(context, R.font.sf_pro_display_regular) ?: Typeface.DEFAULT

    fun heading(size: Float, color: Int) = paint(heavy, size, color)

    fun body(size: Float, color: Int, bold: Boolean = false) =
        paint(if (bold) semibold else regular, size, color)

    fun label(size: Float, color: Int, tracking: Float = 0.10f) =
        paint(semibold, size, color).apply { letterSpacing = tracking }

    private fun paint(face: Typeface, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

    private fun font(context: Context, id: Int): Typeface? =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
}

private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url.artworkAt(CARD_ART_PX))
        // Palette needs pixel access, and a hardware bitmap cannot be drawn
        // into a software canvas at all — which is the whole of this file.
        .allowHardware(false)
        .build()
    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}.getOrNull()

/** 9:16, the shape the story cards are held to — see `StoryFrame`. */
private const val POSTER_W = 1080
private const val POSTER_H = 1920

private const val MARGIN = 72f

/** The mark's drawn size. 730×484 in the vector, so this keeps its proportions. */
private const val LOGO_W = 66f
private const val LOGO_H = 44f
private const val LOGO_GAP = 20f

/** The width type and artwork are laid out in. */
private const val CONTENT_W = POSTER_W - MARGIN * 2

/** Matches the 30sp the card on screen sets its sentence at, at 3x. */
private const val HEADLINE_SIZE = 88f
private const val HEADLINE_LEADING = 108f

/** How far down a chart a shared card goes — the same five the card shows. */
private const val CARD_ROWS = 5
private const val COLUMN_GAP = 36f
private const val ROW_HEIGHT = 116f
private const val ART = 84f
private const val ACCENT = 0xFFFA2D48.toInt()

private const val POSTER_ROWS = 5
private const val POSTER_ALBUMS = 3
private const val POSTER_GENRES = 4
