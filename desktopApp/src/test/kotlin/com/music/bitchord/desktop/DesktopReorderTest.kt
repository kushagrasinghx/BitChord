package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a dragged row lands. */
class DesktopReorderTest {

    /** Two wrapped rows among short ones, which is what the dialog now has. */
    private val mixed = listOf(52f, 70f, 52f, 52f, 70f, 52f)
    private val uniform = List(6) { 52f }

    @Test
    fun aRowNotDraggedAnywhereStaysWhereItIs() {
        assertEquals(2, reorderTargetSlot(mixed, from = 2, dragPx = 0f))
        assertEquals(0, reorderTargetSlot(mixed, from = 0, dragPx = 0f))
    }

    @Test
    fun aRowChangesPlacesOnceItIsMoreThanHalfwayPastItsNeighbour() {
        // Row 2 is 52 tall and row 3 below it is 52: it takes 26px to be halfway past, and not 25.
        assertEquals(2, reorderTargetSlot(mixed, from = 2, dragPx = 25f))
        assertEquals(3, reorderTargetSlot(mixed, from = 2, dragPx = 27f))
    }

    @Test
    fun aTallNeighbourTakesMoreDraggingToPass() {
        // Row 0 is short, row 1 below it is a wrapped 70.
        assertEquals(0, reorderTargetSlot(mixed, from = 0, dragPx = 26f))
        assertEquals(1, reorderTargetSlot(mixed, from = 0, dragPx = 40f))
    }

    @Test
    fun draggingUpwardsWorksTheSameWay() {
        assertEquals(3, reorderTargetSlot(mixed, from = 3, dragPx = -20f))
        assertEquals(2, reorderTargetSlot(mixed, from = 3, dragPx = -30f))
        // Past the wrapped row at slot 1 as well: clearing it means covering its 70, not the 52
        // that would clear a short one.
        assertEquals(2, reorderTargetSlot(mixed, from = 3, dragPx = -80f))
        assertEquals(1, reorderTargetSlot(mixed, from = 3, dragPx = -100f))
    }

    @Test
    fun aRowCannotBeDraggedOffEitherEndOfTheList() {
        assertEquals(0, reorderTargetSlot(mixed, from = 2, dragPx = -10_000f))
        assertEquals(mixed.lastIndex, reorderTargetSlot(mixed, from = 2, dragPx = 10_000f))
    }

    @Test
    fun onAUniformListItIsStillJustRoundingToTheNearestRow() {
        // The behaviour this replaced, which was correct as far as it went.
        (0..5).forEach { from ->
            listOf(-130f, -78f, -26f, 0f, 26f, 78f, 130f).forEach { drag ->
                val rounded = (from + Math.round(drag / 52f)).coerceIn(0, 5)
                assertEquals(rounded, reorderTargetSlot(uniform, from, drag), "from=$from drag=$drag")
            }
        }
    }
}
