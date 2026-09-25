package com.music.bitchord.ui

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

data class HomeItemLocation(
    val shelfIndex: Int,
    val itemIndex: Int,
    val shelfTitle: String,
)

data class HomeRecommendationTarget(
    val location: HomeItemLocation,
    val item: ShelfItem,
)

data class RemovedHomeRecommendation(
    val location: HomeItemLocation,
    val item: ShelfItem,
)

data class RecommendationFeedbackNotice(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
)

internal fun removeHomeRecommendation(
    shelves: List<HomeShelf>,
    target: HomeRecommendationTarget,
): Pair<List<HomeShelf>, RemovedHomeRecommendation>? {
    val shelf = shelves.getOrNull(target.location.shelfIndex) ?: return null
    if (shelf.title != target.location.shelfTitle) return null
    val itemIndex = if (shelf.items.getOrNull(target.location.itemIndex) == target.item) {
        target.location.itemIndex
    } else {
        shelf.items.indices.filter { shelf.items[it] == target.item }.singleOrNull() ?: return null
    }
    val items = shelf.items.toMutableList().also { it.removeAt(itemIndex) }
    val updated = shelves.toMutableList().also {
        it[target.location.shelfIndex] = shelf.copy(items = items)
    }
    return updated to RemovedHomeRecommendation(target.location.copy(itemIndex = itemIndex), target.item)
}

internal fun restoreHomeRecommendation(
    shelves: List<HomeShelf>,
    removed: RemovedHomeRecommendation,
): List<HomeShelf>? {
    val shelf = shelves.getOrNull(removed.location.shelfIndex) ?: return null
    if (shelf.title != removed.location.shelfTitle) return null
    if (removed.item in shelf.items) return shelves
    val insertionIndex = removed.location.itemIndex.coerceAtMost(shelf.items.size)
    val items = shelf.items.toMutableList().also { it.add(insertionIndex, removed.item) }
    return shelves.toMutableList().also {
        it[removed.location.shelfIndex] = shelf.copy(items = items)
    }
}
