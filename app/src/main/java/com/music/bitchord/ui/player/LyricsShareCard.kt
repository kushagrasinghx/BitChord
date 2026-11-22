package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.music.bitchord.data.lyrics.CharGrowth
import com.music.bitchord.data.lyrics.GrowingWord
import com.music.bitchord.data.lyrics.LyricLine
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.res.ResourcesCompat
import com.music.bitchord.R
import com.music.bitchord.ui.replay.Fonts
import com.music.bitchord.ui.replay.drawArtwork
import com.music.bitchord.ui.replay.drawLogo
import com.music.bitchord.ui.replay.ellipsised
import com.music.bitchord.ui.replay.loadBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picked lines as one picture, drawn to a bitmap you can send.
 *
 * ## Why this is drawn rather than screenshotted
 *
 * The same reason the Replay is. A screenshot of the lyrics panel carries the
 * scrubber, the glow falloff, whichever lines happened to be on screen and the
 * status bar of that particular phone, so two people sharing the same verse
 * would send two different-looking pictures. A card laid out here is the verse,
 * the sleeve and this app's mark, and nothing else — and it is only as tall as
 * that verse needs, which holds it inside 1080×1920 (9:16): the shape a story
 * or a chat head expects. Keeping it there is the player's job, by refusing
 * more words at the pick rather than by shrinking them here.
 *
 * ## Why it isn't Compose
 *
 * `Bitmap` + `android.graphics.Canvas`, exactly as
 * [com.music.bitchord.ui.replay.ReplayPoster] does it, and for the same
 * reasons written up there: Compose offscreen on minSdk 26 is a cascade of
 * hardware bitmaps and layers that fails differently per device, while a
 * software canvas draws identically from API 26 up. The backdrop, the logo, the
 * fonts and the sleeve are shared with the Replay by importing them rather than
 * by copying, so the two cards cannot drift apart.
 *
 * ## Fitting the type
 *
 * A passage can be one line or twenty, so both the size and the height are
 * chosen rather than fixed: the layout is retried down a fixed ladder of sizes
 * until the rows stop overflowing, and the card is then grown to exactly the
 * room those rows take. The bottom rung of that ladder is a floor rather than a
 * last resort — no card ever sets type smaller than it to squeeze a passage
 * in, so a passage too long for the frame lengthens the card instead of being
 * cut, or made small enough to hold a phone at arm's length to read.
 */
internal suspend fun renderLyricsShareCard(
    context: Context,
    card: LyricsShareRequest,
    /** The 1080×1920 story layout instead: see [layoutStory]. */
    story: Boolean = false,
    /** The BitChord mark at the top right; without it the passage moves up into its room. */
    showLogo: Boolean = true,
    /** No backdrop: the card alone, to lay over a video of one. */
    transparent: Boolean = false,
): Bitmap = lyricsCardPainter(context, card, story, showLogo, transparent).render(null)

/**
 * The card laid out once, ready to be drawn at any moment of the song.
 *
 * Everything that never moves — the backdrop, the mark, the sleeve and credit,
 * the story's frosted panel — is drawn here into one base picture; the passage
 * is drawn over it per frame by [LyricsCardPainter], so the share video and the
 * sheet's preview can animate the words without laying anything out again.
 */
internal suspend fun lyricsCardPainter(
    context: Context,
    card: LyricsShareRequest,
    story: Boolean = false,
    showLogo: Boolean = true,
    transparent: Boolean = false,
): LyricsCardPainter = withContext(Dispatchers.Default) {
    val type = Fonts(context)
    val cover = card.artworkUrl?.let { loadBitmap(context, it) }
    val layout = if (story) {
        layoutStory(context, type, card, cover, showLogo, transparent)
    } else {
        layoutCard(context, type, card, cover, showLogo, transparent)
    }
    LyricsCardPainter(layout, card.lines)
}

/** A row of the passage, and where it sits on the card. */
internal class PlacedRow internal constructor(val row: Row, val x: Float, val baseline: Float)

internal class CardLayout internal constructor(val width: Int, val height: Int, val base: Bitmap, val rows: List<PlacedRow>)

/** Places [plan]'s rows from [top] down, starting at [x]. */
private fun place(plan: Plan, x: Float, top: Float): List<PlacedRow> {
    var y = top
    return plan.rows.map { row ->
        val baseline = y + row.gapBefore - row.paint.ascent() +
            if (row.isGap) row.paint.textSize * GAP_MARKER_OFFSET else 0f
        y += row.gapBefore + row.height
        PlacedRow(row, x, baseline)
    }
}

private fun layoutCard(
    context: Context,
    type: Fonts,
    card: LyricsShareRequest,
    cover: Bitmap?,
    showLogo: Boolean,
    transparent: Boolean,
): CardLayout {
    // ── Sizing ──────────────────────────────────────────────────────────────
    // No room is reserved for the words to sit in: they get their own height,
    // and the card is built down from that number. A chorus comes out a strip
    // barely taller than its own text instead of a 9:16 sheet with a paragraph
    // floating in the middle of it, and the size comes down only as far as the
    // ladder goes — past that the frame gives rather than the type does.
    // With no mark the passage starts at the same margin the footer keeps below.
    val headTop = if (showLogo) HEADER_BOTTOM + GAP_ABOVE_BODY else FOOTER_PAD
    val frameRoom = MAX_CARD_H - FOOTER_THUMB - FOOTER_PAD - GAP_BELOW_BODY - headTop
    val plan = fittestPlan(type, card.lines, frameRoom)
    val bodyTop = headTop
    // The passage ends where its own rows end, and everything under it — the
    // gap, the sleeve credit, the foot — hangs off that. Because the card is
    // measured from the same sum the rows are drawn from, no line can ever
    // land past the bottom by a rounding error and be replaced by a mark
    // saying something was left out.
    val bodyBottom = bodyTop + plan.content
    val cardH = bodyBottom + GAP_BELOW_BODY + FOOTER_THUMB + FOOTER_PAD

    val bitmap = Bitmap.createBitmap(CARD_W, cardH.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent) drawPlayerBackdrop(canvas, card.backdrop, cardH)

    // ── Header ──────────────────────────────────────────────────────────────
    // The mark alone, at the top right — the same one the Replay carries, so
    // two of these cards still read as one family. Nothing else goes above
    // the passage: the picture is the verse, not a caption for it.
    if (showLogo) {
        val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(BRAND, CARD_W - CARD_MARGIN, HEADER_BASELINE, brand)
        drawLogo(
            canvas,
            context,
            CARD_W - CARD_MARGIN - brand.measureText(BRAND) - LOGO_GAP,
            HEADER_BASELINE,
        )
    }

    // ── Footer ──────────────────────────────────────────────────────────────
    // Which song this is — which only the backdrop had been saying until now.
    val thumbY = cardH - FOOTER_PAD - FOOTER_THUMB
    drawArtwork(canvas, cover, card.song.title, CARD_MARGIN, thumbY, FOOTER_THUMB, false)
    val textX = CARD_MARGIN + FOOTER_THUMB + 34f
    val room = CARD_W - CARD_MARGIN - textX
    val title = type.body(50f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(card.song.title, title, room), textX, thumbY + 74f, title)
    val artist = type.body(42f, 0x99FFFFFF.toInt())
    canvas.drawText(ellipsised(card.song.artist, artist, room), textX, thumbY + 132f, artist)

    // ── The passage ─────────────────────────────────────────────────────────
    // Every row that exists is drawn: the frame is measured from wherever
    // these rows end, so there is never room to be short of and nothing that
    // has to be cut to make it.
    return CardLayout(bitmap.width, bitmap.height, bitmap, place(plan, CARD_MARGIN, bodyTop))
}

/**
 * The story: the whole 9:16 frame is the song's blurred colours, and the
 * passage sits in a small frosted card in the middle of it with the song
 * under the words — the way Apple Music shares lyrics to a story — so the
 * story app's own furniture at the top and bottom never lands on the words.
 */
private fun layoutStory(
    context: Context,
    type: Fonts,
    card: LyricsShareRequest,
    cover: Bitmap?,
    showLogo: Boolean,
    transparent: Boolean,
): CardLayout {
    val height = MAX_CARD_H
    val bitmap = Bitmap.createBitmap(CARD_W, height.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent) drawPlayerBackdrop(canvas, card.backdrop, height)

    val left = (CARD_W - STORY_PANEL_W) / 2f
    val contentW = STORY_PANEL_W - STORY_PAD * 2
    // The song sits on its own lighter band across the foot of the card.
    val bandH = STORY_BAND_PAD * 2 + STORY_THUMB
    val lyricRoom = STORY_PANEL_MAX_H - STORY_PAD * 2 - bandH
    val plan = fittestPlan(type, card.lines, lyricRoom, contentW, STORY_LADDER)
    val panelH = STORY_PAD + plan.content + STORY_PAD + bandH
    val top = (height - panelH) / 2f
    val panel = RectF(left, top, left + STORY_PANEL_W, top + panelH)

    // Frosted glass, faked on a software canvas: the backdrop is already a
    // blur, so a light grey veil — lighter than the frame, as Apple's is — and
    // a hairline rim read as the same material.
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = 0x12000000
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, fill)
    fill.color = 0x1FFFFFFF
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, fill)
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x1FFFFFFF
    }
    val bandTop = panel.bottom - bandH
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(panel, STORY_RADIUS, STORY_RADIUS, Path.Direction.CW) })
    fill.color = 0x1AFFFFFF
    canvas.drawRect(panel.left, bandTop, panel.right, panel.bottom, fill)
    canvas.restore()
    canvas.drawRoundRect(panel, STORY_RADIUS, STORY_RADIUS, rim)

    val x = left + STORY_PAD

    // The song, and under it the app it came from in place of Apple's "Music".
    val thumbY = bandTop + STORY_BAND_PAD
    drawArtwork(canvas, cover, card.song.title, x, thumbY, STORY_THUMB, false)
    val textX = x + STORY_THUMB + 28f
    val room = left + STORY_PANEL_W - STORY_PAD - textX
    // Title and artist at one size and nearly one brightness, as in Apple's:
    // the credit is read, not skimmed past.
    val title = type.body(36f, Color.WHITE, bold = true)
    val artist = type.body(36f, 0xE6FFFFFF.toInt())
    val lines = if (showLogo) 3 else 2
    // The credit's lines, centred as a block on the sleeve beside them.
    val lineH = 44f
    var baseline = thumbY + (STORY_THUMB - lineH * lines) / 2f + lineH - 10f
    canvas.drawText(ellipsised(card.song.title, title, room), textX, baseline, title)
    baseline += lineH
    canvas.drawText(ellipsised(card.song.artist, artist, room), textX, baseline, artist)
    if (showLogo) {
        baseline += lineH
        val brand = type.body(30f, 0x99FFFFFF.toInt(), bold = true)
        val mark = runCatching { ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null) }.getOrNull()
        var brandX = textX
        if (mark != null) {
            val markH = 22f
            val markW = markH * mark.intrinsicWidth / mark.intrinsicHeight.coerceAtLeast(1)
            mark.setTint(0x99FFFFFF.toInt())
            mark.setBounds(
                brandX.toInt(), (baseline - markH).toInt(),
                (brandX + markW).toInt(), baseline.toInt(),
            )
            mark.draw(canvas)
            brandX += markW + 8f
        }
        canvas.drawText(BRAND, brandX, baseline, brand)
    }
    return CardLayout(bitmap.width, bitmap.height, bitmap, place(plan, x, top + STORY_PAD))
}

/**
 * Draws the card at a moment of the song, the way the player's lyrics panel
 * draws the same lines at that moment.
 *
 * Not a lookalike: the arithmetic is the panel's own, read off the same
 * [LyricLine] — [LyricLine.revealedChars] for how far the sweep has got,
 * syllable by syllable; [LyricLine.wordLift] for the word rising as it is sung
 * and settling once it is past; [GrowingWord] for a held word swelling letter
 * by letter, with its bloom. The panel's distances become ratios of the type
 * size here, so they hold at whatever size the card's ladder landed on: the
 * 30dp feather, the 2dp rise and the 6dp glow, all against its 34sp headline.
 * The words not yet sung sit at the panel's [UNSUNG_ALPHA], and lines further
 * from the one being sung fade down its [LINE_FALLOFF] the same way.
 *
 * With no position — the still card — every line is drawn lit.
 *
 * Not thread-safe: it reuses its paints. One instance per thread that draws.
 */
internal class LyricsCardPainter internal constructor(
    private val layout: CardLayout,
    private val lines: List<LyricsShareLine>,
) {
    val width: Int get() = layout.width
    val height: Int get() = layout.height

    private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scratch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val growth = CharGrowth()

    /** Each line's place among the sung lines (gaps don't count), for the falloff. */
    private val rank = IntArray(lines.size).also { ranks ->
        var next = 0
        lines.forEachIndexed { index, line -> ranks[index] = if (line.isGap) -1 else next++ }
    }

    /** The card at [positionMs] (track time; null for the still card), into [into] when given. */
    fun render(positionMs: Long?, into: Bitmap? = null): Bitmap {
        val bitmap = into ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        drawOn(Canvas(bitmap), positionMs)
        return bitmap
    }

    /** Draws the card onto [canvas] at the card's own pixel size. */
    fun drawOn(canvas: Canvas, positionMs: Long?) {
        canvas.drawBitmap(layout.base, 0f, 0f, basePaint)
        val active = positionMs?.let(::activeLine) ?: -1
        for (placed in layout.rows) drawRow(canvas, placed, positionMs, active)
    }

    /** The line being sung at [positionMs]: the last one to have started, or the first. */
    private fun activeLine(positionMs: Long): Int {
        var first = -1
        var last = -1
        lines.forEachIndexed { index, line ->
            if (line.isGap) return@forEachIndexed
            val start = line.startMs ?: line.timing?.timeMs ?: return@forEachIndexed
            if (first < 0) first = index
            if (positionMs >= start) last = index
        }
        return if (last >= 0) last else first
    }

    private fun drawRow(canvas: Canvas, placed: PlacedRow, positionMs: Long?, active: Int) {
        val row = placed.row
        val line = lines.getOrNull(row.line)
        if (positionMs == null || row.isGap || line == null || active < 0) {
            canvas.drawText(row.text, placed.x, placed.baseline, row.paint)
            return
        }
        val timing = line.timing
        val distance = kotlin.math.abs(rank[row.line] - rank[active]).coerceAtMost(LINE_FALLOFF.lastIndex)
        val alpha = row.paint.alpha * LINE_FALLOFF[distance]
        val start = line.startMs ?: timing?.timeMs ?: 0L
        val swept = timing != null && timing.isWordSynced && !row.rtl

        if (!swept) {
            // No word timings (or a script the sweep can't follow): the line
            // lights whole the moment it starts, as the panel's does.
            val lit = if (positionMs >= start) 1f else UNSUNG_ALPHA
            scratch.set(row.paint)
            scratch.alpha = (alpha * lit).roundToInt()
            canvas.drawText(row.text, placed.x, placed.baseline, scratch)
            return
        }
        timing!!
        // How far the sweep has got, in this row's own characters.
        val revealedInLine = if (row.isSub) {
            val lead = timing.text.length.coerceAtLeast(1)
            (timing.revealedChars(positionMs) / lead).coerceIn(0f, 1f) * (line.subText?.length ?: 0)
        } else {
            timing.revealedChars(positionMs)
        }
        val revealed = (revealedInLine - row.start).coerceIn(0f, row.text.length.toFloat())
        val sung = positionMs >= timing.endMs
        val dim = if (sung) 1f else UNSUNG_ALPHA
        val lifts = !row.isSub

        // The unsung copy underneath, then the lit one over it up to the sweep.
        scratch.set(row.paint)
        scratch.alpha = (alpha * dim).roundToInt()
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = false)
        if (revealed <= 0f || (sung && revealed >= row.text.length)) return

        scratch.alpha = alpha.roundToInt()
        if (revealed >= row.text.length) {
            drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
            return
        }
        val size = row.paint.textSize
        val edge = placed.x + sweepX(row, revealed, if (row.isSub) emptyList() else timing.sweepSpans)
        // The feather runs ahead of the sweep, into what is still to come, and
        // grows in over the first stretch of each row — the panel's own wipe.
        val feather = minOf(size * FEATHER_RATIO, edge - placed.x)
        val top = placed.baseline + row.paint.ascent() - size * HEADROOM_RATIO
        val bottom = placed.baseline + row.paint.descent() + size * 0.1f
        val save = canvas.saveLayer(0f, top, layout.width.toFloat(), bottom, null)
        canvas.clipRect(0f, top, edge + feather, bottom)
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
        if (feather > 0.5f) {
            fade.shader = LinearGradient(
                edge, 0f, edge + feather, 0f,
                Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, top, layout.width.toFloat(), bottom, fade)
        }
        canvas.restoreToCount(save)
    }

    /**
     * The row's text with each word at its lift, and a held word letter by
     * letter at its swell — with, when [bloom], the glow its letters give off.
     */
    private fun drawPieces(
        canvas: Canvas,
        placed: PlacedRow,
        paint: Paint,
        positionMs: Long,
        timing: LyricLine,
        lifts: Boolean,
        bloom: Boolean,
    ) {
        val row = placed.row
        val text = row.text
        if (!lifts) {
            canvas.drawText(text, placed.x, placed.baseline, paint)
            return
        }
        val size = paint.textSize
        val rise = size * RISE_RATIO
        val rowStart = row.start
        val rowEnd = rowStart + text.length
        fun run(from: Int, to: Int, dy: Float) {
            if (to <= from) return
            canvas.drawText(text, from, to, placed.x + paint.measureText(text, 0, from), placed.baseline + dy, paint)
        }
        var cursor = 0
        timing.wordSpans.forEachIndexed { index, span ->
            val from = maxOf(span.first, rowStart) - rowStart
            val to = minOf(span.last + 1, rowEnd) - rowStart
            if (to <= from) return@forEachIndexed
            run(cursor, from, 0f)
            val grow = timing.growingAt(index)
            if (grow != null && positionMs >= grow.startMs && positionMs <= grow.restsAtMs) {
                for (k in from until to) {
                    val letter = rowStart + k - span.first
                    grow.sampleInto(letter, positionMs, growth)
                    val left = placed.x + paint.measureText(text, 0, k)
                    val w = paint.measureText(text, k, k + 1)
                    val cx = left + w / 2f + growth.shift * size
                    val y = placed.baseline - growth.rise * rise
                    canvas.save()
                    canvas.scale(growth.scale, growth.scale, cx, y)
                    if (bloom && growth.bloom > 0.01f) {
                        glow.set(paint)
                        glow.alpha = (paint.alpha * growth.bloom.coerceAtMost(1f)).roundToInt()
                        glow.maskFilter = BlurMaskFilter(size * GLOW_RATIO, BlurMaskFilter.Blur.NORMAL)
                        canvas.drawText(text, k, k + 1, cx - w / 2f, y, glow)
                    }
                    canvas.drawText(text, k, k + 1, cx - w / 2f, y, paint)
                    canvas.restore()
                }
            } else {
                run(from, to, -timing.wordLift(index, positionMs) * rise)
            }
            cursor = to
        }
        run(cursor, text.length, 0f)
    }

    /** Where [chars] characters into [row] falls, from the row's left edge. */
    private fun sweepX(row: Row, chars: Float, spans: List<IntRange>): Float {
        val text = row.text
        val paint = row.paint
        val at = row.start + chars
        // Within a timed piece the edge crosses its width at a steady pace.
        val piece = spans.firstOrNull { at >= it.first && at < it.last + 1 }
        if (piece != null && piece.first >= row.start && piece.last + 1 <= row.start + text.length) {
            val from = paint.measureText(text, 0, piece.first - row.start)
            val to = paint.measureText(text, 0, piece.last + 1 - row.start)
            return from + (to - from) * (at - piece.first) / (piece.last + 1 - piece.first)
        }
        // A space between words, or a piece broken over a wrap: letter by letter.
        val k = chars.toInt().coerceIn(0, text.length)
        val here = paint.measureText(text, 0, k)
        val next = paint.measureText(text, 0, (k + 1).coerceAtMost(text.length))
        return here + (next - here) * (chars - k)
    }
}

/**
 * The player's backdrop alone, [height] tall at the card's width: what the
 * share video sits on when it has no animated cover to play under the words.
 */
internal fun renderLyricsShareBackdrop(card: LyricsShareRequest, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_W, height, Bitmap.Config.ARGB_8888)
    drawPlayerBackdrop(Canvas(bitmap), card.backdrop, height.toFloat())
    return bitmap
}

/**
 * The player's own backdrop — the one the lyrics were picked over — cropped to
 * fill the card from its middle, so the card is in the colours the player was.
 * Black where none was recorded (a full-screen Canvas video draws nothing a
 * layer can keep).
 */
private fun drawPlayerBackdrop(canvas: Canvas, backdrop: ImageBitmap?, height: Float) {
    canvas.drawColor(Color.BLACK)
    val source = backdrop?.asAndroidBitmap() ?: return
    // A software canvas can't draw a hardware bitmap, which is what a layer gives.
    val image = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return
    } else {
        source
    }
    val scale = maxOf(CARD_W / image.width.toFloat(), height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val dst = RectF((CARD_W - w) / 2f, (height - h) / 2f, (CARD_W + w) / 2f, (height + h) / 2f)
    canvas.drawBitmap(image, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
}

// ── Fitting ────────────────────────────────────────────────────────────────

internal class Row internal constructor(
    val text: String,
    val paint: Paint,
    val height: Float,
    val gapBefore: Float,
    /** Drawn centred rather than off the left edge — the gap marker. */
    val centered: Boolean = false,
    val isGap: Boolean = false,
    /** Which of the request's lines this row belongs to. */
    val line: Int = -1,
    /** Where the row starts in its line's text (or in its sub-line's, for [isSub]). */
    val start: Int = 0,
    /** The translation or romanization under the line, rather than the line itself. */
    val isSub: Boolean = false,
    /** Right-to-left text, which lights whole rather than sweeping. */
    val rtl: Boolean = false,
)

/** The rows at one size, with everything needed to place them. */
private class Plan(val rows: List<Row>, val ascent: Float) {
    /** Distance from the top of the first line to the bottom of the last. */
    val content: Float
        get() = ascent + rows.fold(0f) { total, row -> total + row.gapBefore + row.height }
}

/**
 * The largest size on [FIT_LADDER] whose rows still fit in [room], or the
 * bottom rung when none of them do.
 *
 * The bottom rung is a floor rather than a last resort: under it the type stops
 * being worth sharing, so the frame grows to hold the words instead of the
 * words shrinking to hold the frame. That the player budgets characters at the
 * pick is what keeps this branch from ever being reached in practice.
 */
private fun fittestPlan(
    type: Fonts,
    lines: List<LyricsShareLine>,
    room: Float,
    width: Float = CARD_CONTENT_W,
    ladder: FloatArray = FIT_LADDER,
): Plan {
    for (size in ladder) {
        val plan = planAt(type, lines, size, width)
        if (plan.content <= room) return plan
    }
    return planAt(type, lines, ladder.last(), width)
}

/**
 * The rows at one [size], set in the face the lyrics panel draws with.
 *
 * SF Pro Display Heavy with the panel's tight tracking and its line advance,
 * carried over as the same ratios the panel's 34sp headline uses — so the card
 * reads as a still of that panel rather than as a different app quoting it.
 * Compose's tracking is absolute and `Paint`'s is a fraction of the size, hence
 * the divisions: -0.7 over 34 for the words, -0.7 over 20 for the smaller line
 * the panel hangs underneath them.
 */
private fun planAt(type: Fonts, lines: List<LyricsShareLine>, size: Float, width: Float): Plan {
    val primary = type.heading(size, Color.WHITE).apply { letterSpacing = TRACKING }
    val subSize = (size * SUB_RATIO).coerceAtLeast(SUB_FLOOR)
    val secondary = type.heading(subSize, SUB_COLOR).apply { letterSpacing = SUB_TRACKING }

    val rows = mutableListOf<Row>()
    lines.forEachIndexed { index, line ->
        if (line.isGap) {
            // The skipped lines, as "(...)" — the conventional editorial mark
            // for an omission. Hung off the left edge with the words, smaller
            // and a dim grey. Keep its row compact so the marker follows the
            // preceding lyric closely; the regular gap on the next lyric line
            // provides the separation below it.
            val marker = type.body(size * 0.65f, 0x66FFFFFF.toInt())
            val markerHeight = marker.descent() - marker.ascent()
            rows += Row("(...)", marker, markerHeight, 0f, isGap = true, line = index)
            return@forEachIndexed
        }
        var firstOfLine = true
        fun add(text: String, paint: Paint, height: Float, start: Int, isSub: Boolean, rtl: Boolean) {
            // One verse line is one paragraph: its first row carries the space
            // above it and its wraps sit tight underneath, so a phrase reads as
            // a block rather than as a list of separate sentences.
            val gap = when {
                rows.isEmpty() -> 0f
                firstOfLine && index > 0 -> size * 0.55f
                else -> 0f
            }
            rows += Row(text, paint, height, gap, line = index, start = start, isSub = isSub, rtl = rtl)
            firstOfLine = false
        }
        // Each row remembers where it starts in its line, so the sweep — which
        // counts characters of the whole line — can be found within it.
        fun rows(source: String, paint: Paint, height: Float, isSub: Boolean) {
            val rtl = isRtl(source)
            var cursor = 0
            wrap(source, paint, width).forEach { piece ->
                val at = source.indexOf(piece, cursor).takeIf { it >= 0 } ?: cursor
                cursor = at + piece.length
                add(piece, paint, height, at, isSub, rtl)
            }
        }
        rows(line.text, primary, size * LEAD, isSub = false)
        val sub = line.subText
        if (!sub.isNullOrBlank() && sub != line.text) {
            rows(sub, secondary, subSize * SUB_LEAD, isSub = true)
        }
    }
    return Plan(rows, -primary.ascent())
}

// ── Text ───────────────────────────────────────────────────────────────────

/**
 * Greedy wrap at [width], breaking wherever the line has to.
 *
 * Not a plain word-wrap: Japanese, Chinese and Korean lyrics carry no spaces at
 * all, and a wrapper that only split on spaces would hand the whole verse to
 * `drawText` and let it run off the card. `breakText` finds the character that
 * fits, and only then is the break walked back to a space — so scripts that keep
 * their words apart lose nothing, and scripts that don't still get several lines.
 */
private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val source = text.trim()
    if (source.isEmpty()) return listOf("")

    val out = mutableListOf<String>()
    var rest = source
    while (rest.isNotEmpty()) {
        val fit = paint.breakText(rest, true, width, null)
        if (fit <= 0) {
            out += rest
            break
        }
        // Walk back to the last space inside the fit, but only when something
        // still has to follow it — otherwise the fit is the whole string and
        // cutting at the space would drop the final word.
        val space = rest.lastIndexOf(' ', fit - 1)
        val take = if (space > 0 && fit < rest.length) space else fit
        if (take <= 0) {
            out += rest
            break
        }
        out += rest.substring(0, take).trimEnd()
        rest = rest.substring(take).trimStart()
    }
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
}

// ── Card geometry ──────────────────────────────────────────────────────────

private const val CARD_W = 1080

/**
 * 9:16 — a ceiling on the height, not the height itself. The card is grown to
 * its passage and only ever comes out this tall when the passage demands it.
 */
private const val MAX_CARD_H = 1920f

private const val CARD_MARGIN = 72f
private const val CARD_CONTENT_W = CARD_W - CARD_MARGIN * 2

private const val LOGO_GAP = 20f
private const val BRAND = "BitChord"

/** Baseline of the mark at the top right, and just under it where the gap starts. */
private const val HEADER_BASELINE = 132f
private const val HEADER_BOTTOM = 170f

/** Air kept between the fixed furniture and the passage, both ends alike. */
private const val GAP_ABOVE_BODY = 20f
private const val GAP_BELOW_BODY = 20f

/** The sleeve square in the footer, and the air kept under it. */
private const val FOOTER_THUMB = 160f
private const val FOOTER_PAD = 96f

/** Smallest the translation line is allowed to get before it stops being legible. */
private const val SUB_FLOOR = 24f

// ── The panel's animation, as ratios of the type size ──────────────────────
// PlayerLyrics' own numbers, divided by the 34sp its headline is set at.

/** The words not yet sung: the panel's UNSUNG_ALPHA. */
private const val UNSUNG_ALPHA = 0.45f

/** Lines further from the one being sung: the panel's LINE_FALLOFF_ALPHA. */
private val LINE_FALLOFF = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)

/** The sweep's soft leading edge: WIPE_FEATHER, 30dp. */
private const val FEATHER_RATIO = 30f / 34f

/** A sung word's lift: WORD_RISE, 2dp. */
private const val RISE_RATIO = 2f / 34f

/** Room above a row for a lifted, swelling letter: GROW_HEADROOM rises and the swell. */
private const val HEADROOM_RATIO = 0.35f

/** The bloom behind a held letter: GLOW_RADIUS, 6dp. */
private const val GLOW_RATIO = 6f / 34f

private fun isRtl(text: String): Boolean =
    !java.text.Bidi(text, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()

/** Small visual correction so the omission marker sits below the line above. */
private const val GAP_MARKER_OFFSET = 0.18f

/**
 * The sizes tried, largest first.
 *
 * A ladder rather than a search: a dozen candidates is nothing to lay out, and
 * stepping by a visible amount keeps two cards with adjacent line counts from
 * coming out nearly the same size and reading as a mistake.
 *
 * The last rung doubles as the floor — see [fittestPlan] for why the frame is
 * what gives way under it rather than the type.
 */
private val FIT_LADDER = floatArrayOf(96f, 88f, 80f, 72f, 64f, 56f, 50f, 44f, 38f, 32f)

// ── The story ──────────────────────────────────────────────────────────────

/** The card in the middle of the story: about 70% of the frame's width. */
private const val STORY_PANEL_W = 760f

/** Tallest it grows, clear of a story app's header and reply bar. */
private const val STORY_PANEL_MAX_H = 1280f
private const val STORY_PAD = 52f
private const val STORY_RADIUS = 44f
private const val STORY_THUMB = 128f
/** Air above and below the sleeve inside the song's band. */
private const val STORY_BAND_PAD = 40f

/** Smaller than the card's: the story is a quote in a frame, not a poster of it. */
private val STORY_LADDER = floatArrayOf(60f, 56f, 52f, 48f, 44f, 40f, 36f, 32f)

// ── The panel's type, as ratios ────────────────────────────────────────────
// Every number here is one the lyrics panel uses, divided by the size it uses
// it at so it holds at whatever size the ladder lands on.

/** Tracking of the panel's headline: -0.7sp against its 34sp. */
private const val TRACKING = -0.7f / 34f

/** The same -0.7sp, against the 20sp it sets the line underneath in. */
private const val SUB_TRACKING = -0.7f / 20f

/** Line advance: 41sp over 34sp for the words, 25sp over 20sp for the sub. */
private const val LEAD = 41f / 34f
private const val SUB_LEAD = 25f / 20f

/** The sub-line against the words above it, and the white it is drawn in. */
private const val SUB_RATIO = 20f / 34f
private val SUB_COLOR = 0xD9FFFFFF.toInt()
