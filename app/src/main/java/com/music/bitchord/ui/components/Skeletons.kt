package com.music.bitchord.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grey stand-ins for content still on the wire, laid out to the same metrics as
 * the real rows and cards so nothing jumps when the data lands.
 */

/** How long one highlight sweep takes to cross a placeholder. */
private const val SHIMMER_PERIOD_MS = 1400

private val BlockShape = RoundedCornerShape(6.dp)
private val LineShape = RoundedCornerShape(4.dp)

// Ragged widths, so a run of rows reads as text rather than as a barcode.
private val TitleWidths = listOf(0.68f, 0.46f, 0.58f, 0.74f, 0.52f)
private val SubtitleWidths = listOf(0.34f, 0.44f, 0.27f, 0.38f, 0.31f)

/**
 * One placeholder block, with a highlight sweeping across it.
 *
 * The sweep is read inside the draw block rather than the composable body: a
 * screenful of these would otherwise recompose on every animation frame, and
 * all any of them needs per frame is a fresh gradient.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = BlockShape) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.16f)
        .compositeOver(base)
    val sweep = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing)),
        label = "sweep",
    )
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                // The band travels from fully off one edge to fully off the
                // other, which leaves a beat of flat grey between passes rather
                // than a highlight permanently parked somewhere on the block.
                val band = size.width * 0.5f
                val startX = -band + sweep.value * (size.width + band * 2)
                val brush = Brush.horizontalGradient(
                    colors = listOf(base, highlight, base),
                    startX = startX,
                    endX = startX + band,
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}

/** A placeholder for one line of text, sized as a fraction of its parent. */
@Composable
private fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier.fillMaxWidth(fraction).height(height),
        shape = LineShape,
    )
}

/**
 * Stands in for a section heading. Only the title line is drawn — most shelves
 * come back without a subtitle, and guessing wrong shifts everything under it.
 */
@Composable
private fun SectionHeaderSkeleton(index: Int = 0) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp)) {
        SkeletonLine(fraction = TitleWidths[index % TitleWidths.size] * 0.7f, height = 18.dp)
    }
}

/**
 * Stands in for one `SongRow`, down to the 52dp of artwork and the 20dp gutter.
 * [circular] matches the browse rows, where artists are drawn as circles.
 */
@Composable
fun SongRowSkeleton(index: Int = 0, circular: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(52.dp), if (circular) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

/** A run of track-row placeholders, for search hits and release track lists. */
fun LazyListScope.songListSkeleton(
    count: Int = 8,
    keyPrefix: String = "skeleton:song",
    circular: Boolean = false,
) {
    items(count, key = { "$keyPrefix:$it" }) { index ->
        SongRowSkeleton(index = index, circular = circular)
    }
}

/**
 * The lead shelf on Home and Explore: near-page-width cards that page sideways.
 *
 * Both carousels below are built on a [LazyRow] with scrolling off rather than a
 * plain [Row], so a card running past the right edge is measured and clipped
 * exactly as the real shelf's is — which is the whole point of a skeleton.
 */
@Composable
private fun HeroShelfSkeleton() {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeaderSkeleton()
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(2) {
                ShimmerBox(
                    modifier = Modifier.fillParentMaxWidth(0.82f).aspectRatio(0.92f),
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }
    }
}

/** The compact carousel of square cards used by every shelf below the first. */
@Composable
fun ShelfSkeleton(index: Int = 0, cardWidth: Dp = SHELF_CARD_WIDTH, cardCorner: Dp = 12.dp) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeaderSkeleton(index = index)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(3) { card ->
                Column(Modifier.width(cardWidth)) {
                    ShimmerBox(
                        modifier = Modifier.width(cardWidth).aspectRatio(1f),
                        shape = RoundedCornerShape(cardCorner),
                    )
                    Spacer(Modifier.height(12.dp))
                    SkeletonLine(fraction = TitleWidths[card % TitleWidths.size], height = 13.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(fraction = SubtitleWidths[card % SubtitleWidths.size], height = 11.dp)
                }
            }
        }
    }
}

/** Home and Explore while the first page of shelves is still loading. */
fun LazyListScope.feedSkeleton(shelves: Int = 3) {
    item(key = "skeleton:hero") { HeroShelfSkeleton() }
    items(shelves - 1, key = { "skeleton:shelf:$it" }) { index ->
        ShelfSkeleton(index = index + 1)
    }
}

/** Appended to the feed while a further page of shelves is on its way. */
fun LazyListScope.feedMoreSkeleton() {
    item(key = "skeleton:more") { ShelfSkeleton() }
}

/** The signed-in library: saved collections, then the run of liked tracks. */
fun LazyListScope.librarySkeleton() {
    item(key = "skeleton:library:shelf") { ShelfSkeleton() }
    item(key = "skeleton:library:header") { SectionHeaderSkeleton(index = 1) }
    songListSkeleton(count = 7, keyPrefix = "skeleton:library:song")
}

/** The Play / Shuffle pair, which only appears once there is something to play. */
@Composable
private fun DetailActionsSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(2) {
            ShimmerBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(percent = 50),
            )
        }
    }
}

/**
 * An album, playlist or artist page below its header — the header itself is
 * drawn from what the row that was tapped already knew, so it never waits.
 */
fun LazyListScope.detailSkeleton(isArtist: Boolean) {
    if (isArtist) {
        item(key = "skeleton:detail:top") {
            Column {
                SectionHeaderSkeleton()
                // Top songs page four at a time, in columns 88% of the width.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    item {
                        Column(Modifier.fillParentMaxWidth(0.88f)) {
                            repeat(4) { index -> CompactSongRowSkeleton(index) }
                        }
                    }
                }
            }
        }
        item(key = "skeleton:detail:sections") {
            ShelfSkeleton(index = 2, cardCorner = 10.dp)
        }
        return
    }
    item(key = "skeleton:detail:actions") {
        Column {
            DetailActionsSkeleton()
            Spacer(Modifier.height(10.dp))
        }
    }
    songListSkeleton(count = 8, keyPrefix = "skeleton:detail:song")
}

/** The tighter row used inside the artist page's top-songs pager. */
@Composable
private fun CompactSongRowSkeleton(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(7.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}
