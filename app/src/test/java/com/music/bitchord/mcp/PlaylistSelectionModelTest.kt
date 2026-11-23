package com.music.bitchord.mcp

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PlaylistSelectionModelTest {
    @Test fun emptyIsNotAllSelected() {
        val model = PlaylistSelectionModel(emptyList(), emptySet())
        assertFalse(model.allSelected)
        assertFalse(model.partiallySelected)
        assertEquals(0, model.selectedCount)
    }

    @Test fun partialAndAllSelection() {
        val partial = PlaylistSelectionModel(listOf("a", "b"), setOf("a", "unrelated"))
        assertFalse(partial.allSelected)
        assertTrue(partial.partiallySelected)
        assertEquals(1, partial.selectedCount)
        val all = partial.copy(selectedIds = setOf("a", "b"))
        assertTrue(all.allSelected)
        assertFalse(all.partiallySelected)
        assertFalse(all.bulkSelection().second)
    }

    @Test fun turkishFilterHandlesDottedAndDotlessI() {
        val names = listOf("IŞIK", "İNCİ", "Başka")
        val locale = Locale.forLanguageTag("tr")
        assertEquals(listOf("IŞIK"), PlaylistSelectionModel.filterAndSort(names, " ış ", locale) { it })
        assertEquals(listOf("İNCİ"), PlaylistSelectionModel.filterAndSort(names, "inci", locale) { it })
        assertTrue(PlaylistSelectionModel.filterAndSort(names, "isik", locale) { it }.isEmpty())
    }

    @Test fun bulkAppliesToAllDespiteSearch() {
        val ids = listOf("visible", "hidden")
        val filtered = PlaylistSelectionModel.filterAndSort(ids, "visible", Locale.ENGLISH) { it }
        assertEquals(listOf("visible"), filtered)
        val (targets, selected) = PlaylistSelectionModel(ids, emptySet()).bulkSelection()
        assertEquals(ids, targets)
        assertTrue(selected)
    }

    @Test fun sortsAndEmptyQueryShowsAll() {
        assertEquals(listOf("Alpha", "beta"), PlaylistSelectionModel.filterAndSort(
            listOf("beta", "Alpha"), "  ", Locale.ENGLISH,
        ) { it })
    }
}
