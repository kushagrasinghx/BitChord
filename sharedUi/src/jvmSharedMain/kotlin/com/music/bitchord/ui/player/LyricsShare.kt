package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.cancel
import com.music.bitchord.sharedui.resources.lyrics_share_pick_limit
import com.music.bitchord.sharedui.resources.share
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.Haptics
import com.music.bitchord.ui.haptics.rememberHaptics
import org.jetbrains.compose.resources.stringResource

/**
 * One picked passage, as the string that goes on the card.
 *
 * Held as text rather than as [LyricLine] because the card has no idea what a
 * line's timing is: it is handed what the reader was looking at, translation and
 * all, and nothing else.
 *
 * @property subText The romanization or translation drawn small underneath, or
 * null when the panel was showing the original alone.
 * @property isGap A stand-in for the lines skipped between two picked ones,
 * drawn as a centred ellipsis rather than as words.
 */
data class LyricsShareLine(
    val text: String,
    val subText: String?,
    val isGap: Boolean = false,
    /**
     * Where the line is sung in the track, when the lyrics are synced: the
     * share video plays this stretch of the song and lights the line for it.
     * Null for unsynced lyrics and for gap markers.
     */
    val startMs: Long? = null,
    val endMs: Long? = null,
    /**
     * The line as the panel animates it — word and syllable stamps included —
     * so the share video can sweep, lift and swell the words exactly as the
     * player does. Null for unsynced lyrics.
     */
    val timing: LyricLine? = null,
)

/**
 * Everything the platform's card renderer needs, gathered once by the player.
 *
 * The drawing itself is not here: it needs a bitmap and a canvas, which the
 * desktop has no answer for. The player builds one of these and hands it to
 * [PlayerHost.LyricsShareSheet], and the phone is what turns it into a picture.
 */
data class LyricsShareRequest(
    val song: Song,
    /** Resolved sleeve — remote URL or local file, whatever the player found. */
    val artworkUrl: String?,
    val lines: List<LyricsShareLine>,
    /** The player's backdrop as the lines were picked, for the card to sit on. */
    val backdrop: ImageBitmap? = null,
)

/**
 * How much text one card is allowed to hold: the picked words, in characters.
 *
 * About five short lines — the point where the size ladder still lands near its
 * top rung and the type stays big enough to read in a chat without pinching.
 * Past it the only ways to keep going would be to shrink the type or to grow
 * the card past the 9:16 it is meant to be, so the player stops the pick
 * instead: the line is not added, and the bar says why. Nothing already picked
 * is ever dropped.
 */
const val SHARE_CARD_CHAR_BUDGET = 250

/**
 * Whether a line of [text] can still join a card already holding [taken]
 * characters — the whole of the rule the player enforces at the pick.
 *
 * Only the words are counted. The translation or romanization drawn under each
 * line rides along and costs the card height, which the ladder pays for by
 * taking a rung or two; counting it here instead would make the same five lines
 * pickable or not depending on a toggle that has nothing to do with how long
 * the verse is.
 */
fun fitsOnCard(taken: Int, text: String?): Boolean =
    taken + (text?.length ?: 0) <= SHARE_CARD_CHAR_BUDGET

/**
 * Whether lines can be put on a card at all here.
 *
 * Reading false on a platform is the whole of what keeps the pick mode off a
 * desktop: there is nowhere to put the finished picture, so a mode whose Share
 * button did nothing would be worse than no mode.
 */
val lyricsShareAvailable: Boolean
    get() = PlayerPlatform.host.lyricsShareAvailable

/**
 * The pick mode: which lines are chosen, and what can be done with them.
 *
 * One value rather than a scatter of state, because the panel, the bar and the
 * two layouts read them together and none of them can be right on its own — a
 * line is chosen, over budget, or the mode is off.
 */
internal class LyricsPicker(
    /** Whether the reader is choosing lines to put on a share card. */
    val picking: Boolean,
    /** Which lines are chosen, as indices into the panel's own list. */
    val picks: Set<Int>,
    /** Set when a tap would have pushed the selection past what one card holds. */
    val overBudget: Boolean,
    /** Long-press on a line: start picking, or fold that line into the pick. */
    val pick: (Int) -> Unit,
    /** Tap while picking: add or drop that line. */
    val toggle: (Int) -> Unit,
    val cancel: () -> Unit,
    /** Hands the chosen lines over as a card request. */
    val share: () -> Unit,
    /** Header share button: enter picking with nothing preselected. */
    val beginPicking: () -> Unit,
)

/**
 * The pick mode's state machine: the lines, what may be added to them, and the
 * card the chosen ones make.
 *
 * Adding is subject to [SHARE_CARD_CHAR_BUDGET], counted across everything
 * already chosen: the card draws every line it is handed, so the pick is the
 * only place a limit can sit without dropping words afterwards.
 */
@Composable
internal fun rememberLyricsPicker(
    song: Song,
    /** The panel's lines, index for index with the picks. */
    lines: List<LyricLine>?,
    /** The translation or romanization drawn under each line, if any. */
    subLines: List<LyricLine>?,
    artworkUrl: String?,
    haptics: Haptics,
    /** Where the finished card goes; never called on a platform that has none. */
    onCard: (LyricsShareRequest) -> Unit,
): LyricsPicker {
    // Said once, when a line is refused, and gone by the time the finger moves
    // on — the way every other refusal in this player is worded.
    val cardFullMessage = stringResource(Res.string.lyrics_share_pick_limit)

    // Whether the reader is picking lines for a share card, and which ones.
    // Kept as two values rather than as `Set<Int>?`: the mode opens with nothing
    // chosen yet, and a null-means-off set cannot represent being in the mode and
    // having not picked anything.
    var picking by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Set when a tap would have pushed the selection past what one card can hold,
    // and cleared by the next successful change. Nothing on screen reads it —
    // the bar is two buttons and a row that will not light up is the whole of
    // the answer — but the line that refused says so for itself, the way every
    // other "that will not do" in this player does.
    var overBudget by remember { mutableStateOf(false) }

    // The picks index into [lines], and the panel outlives changes to it —
    // another track or a re-fetched verse would otherwise leave a stale set
    // pointing at lines that are no longer there.
    LaunchedEffect(song.videoId, lines) {
        picking = false
        picks = emptySet()
        overBudget = false
    }

    val usedChars: () -> Int = {
        picks.sumOf { index -> lines.orEmpty().getOrNull(index)?.text?.length ?: 0 }
    }
    val toggle: (Int) -> Unit = { index ->
        val text = lines.orEmpty().getOrNull(index)?.text
        when {
            index in picks -> {
                picks = picks - index
                overBudget = false
            }

            fitsOnCard(usedChars(), text) -> {
                picks = picks + index
                overBudget = false
            }

            // A line that will not fit says so and nothing else lights up. The
            // bar is not the place to say it: it is two buttons over somebody's
            // words, and a line that stays dim is the answer.
            else -> {
                overBudget = true
                PlayerPlatform.host.showMessage(cardFullMessage)
            }
        }
    }
    // The only way in, so it has to open the mode *and* — once the mode is open
    // — behave exactly as a tap does. Otherwise the same press on the same line
    // means one thing before and another after, which is a rule the reader has
    // to learn for no reason.
    val pick: (Int) -> Unit = { index ->
        if (picking) {
            toggle(index)
        } else {
            haptics.play(Haptic.Select)
            picking = true
            picks = emptySet()
            overBudget = false
            // The first line goes through the same budget as the rest, so one
            // very long one opens the mode with nothing chosen rather than
            // opening it already too big to send.
            toggle(index)
        }
    }
    val cancel: () -> Unit = {
        picking = false
        picks = emptySet()
        overBudget = false
    }
    // Header share button: the same mode a long-press opens, but with nothing
    // preselected — the header names no line, so there is nothing to fold in.
    // Kept separate from [pick] so a tap on it can never seek or toggle.
    val beginPicking: () -> Unit = {
        if (!picking) {
            haptics.play(Haptic.Select)
            picking = true
            picks = emptySet()
            overBudget = false
        }
    }
    val share: () -> Unit = {
        val source = lines.orEmpty()
        val chosen = picks.sorted()
        val synced = source.any { it.timeMs > 0L }
        val picked = buildList {
            chosen.forEachIndexed { position, index ->
                // A jump over unchosen lines is marked on the card, so the
                // picture says the verse was cut here rather than letting two
                // distant halves read as if they ran on from each other.
                if (position > 0 && index > chosen[position - 1] + 1) {
                    add(LyricsShareLine(text = "", subText = null, isGap = true))
                }
                val line = source.getOrNull(index) ?: return@forEachIndexed
                val sub = subLines?.getOrNull(index)?.text
                val window = if (synced) singWindow(source, index, chosen.getOrNull(position + 1)) else null
                add(
                    LyricsShareLine(
                        text = line.text,
                        subText = sub?.takeIf { it.isNotBlank() && it != line.text },
                        startMs = window?.first,
                        endMs = window?.second,
                        timing = line.takeIf { synced },
                    ),
                )
            }
        }.filter { it.isGap || it.text.isNotBlank() }
        // An empty pick draws nothing worth sending, so it is simply left alone
        // rather than putting an empty card on screen to be dismissed.
        if (picked.isNotEmpty()) {
            onCard(LyricsShareRequest(song = song, artworkUrl = artworkUrl, lines = picked))
        }
        cancel()
    }

    return LyricsPicker(
        picking = picking,
        picks = picks,
        overBudget = overBudget,
        pick = pick,
        toggle = toggle,
        cancel = cancel,
        share = share,
        beginPicking = beginPicking,
    )
}

/**
 * The floating bar while lines are being picked: Cancel, and Share.
 *
 * A pill over the words rather than a row in the controls, for the reason the
 * translate toggle floats too — the controls fade away on their own, and a mode
 * that can vanish while it is still on reads as the panel having started
 * ignoring taps. It sits centred, where neither the translate nor the romanize
 * corner was, which is why those two stand down for as long as this is up.
 *
 * Nothing else. No count of lines, no budget, no warning: the bar is an
 * instruction over somebody's words, and a line counter is a third thing to read
 * between the verse and the two buttons. What has been chosen is already said by
 * the rows themselves, which light up, and what is left of the budget is not the
 * reader's problem — a line that will not fit simply does not light up.
 */
@Composable
internal fun LyricsPickBar(
    /** False until something is picked, and again once the pick is over budget. */
    shareEnabled: Boolean,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    /** What the bar frosts over: the queue's source, which holds the backdrop and the lyrics. */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .playerBarSurface(RoundedCornerShape(18.dp), hazeState)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickAction(
            label = stringResource(Res.string.cancel),
            icon = Icons.Rounded.Close,
            accent = false,
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = {
                haptics.play(Haptic.Tap)
                onCancel()
            },
        )
        PickAction(
            label = stringResource(Res.string.share),
            icon = Icons.Rounded.IosShare,
            accent = true,
            enabled = shareEnabled,
            modifier = Modifier.weight(1f),
            onClick = {
                haptics.play(Haptic.Select)
                onShare()
            },
        )
    }
}

/**
 * One button of [LyricsPickBar], in the player's own colours.
 *
 * White alphas rather than the theme's, because that is what every other
 * control in this player is drawn with — see [AudioOutputSheet] — and a bar
 * picked out of a different palette reads as a sheet borrowed from another app.
 * Share is the solid one: it is the only thing the whole mode is for, and it
 * should be the thing the eye lands on.
 */
@Composable
private fun RowScope.PickAction(
    label: String,
    icon: ImageVector,
    /** The one that sends it, which is the one that should be reached for. */
    accent: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (accent) {
        Color.White.copy(alpha = if (enabled) 1f else 0.3f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.10f else 0.05f)
    }
    // The drawer's own base colour, so the label on the solid button reads as
    // cut out of it rather than as black paint laid over white.
    val foreground = if (accent) {
        Color(0xFF121212).copy(alpha = if (enabled) 1f else 0.4f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.85f else 0.4f)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The stretch of the track line [index] of [lines] is sung over, for the share
 * video: from its stamp to the next line's, so a run of picked lines plays as
 * one unbroken take. The last line of a run (when [nextPicked] isn't the line
 * right after it) stops where the provider says it was sung until, or at the
 * next stamp — but never runs on through a long instrumental after it.
 */
internal fun singWindow(lines: List<LyricLine>, index: Int, nextPicked: Int?): Pair<Long, Long>? {
    val line = lines.getOrNull(index) ?: return null
    val start = line.timeMs.coerceAtLeast(0L)
    val nextStamp = lines.getOrNull(index + 1)?.timeMs?.takeIf { it > start }
    val end = if (nextPicked == index + 1 && nextStamp != null) {
        nextStamp
    } else {
        val tail = line.sungUntilMs?.takeIf { it > start }?.plus(SHARE_TAIL_MS)
            ?: nextStamp
            ?: (start + SHARE_LINE_FALLBACK_MS)
        minOf(tail, nextStamp ?: Long.MAX_VALUE, start + SHARE_LINE_MAX_MS)
    }
    return start to end
}

/** A beat of air after a line's own end, so its last word isn't clipped. */
private const val SHARE_TAIL_MS = 400L

/** A last line with nothing after it to time it by. */
private const val SHARE_LINE_FALLBACK_MS = 5_000L

/** Longest one line is held for when it ends a run: no minute-long outros. */
private const val SHARE_LINE_MAX_MS = 9_000L
