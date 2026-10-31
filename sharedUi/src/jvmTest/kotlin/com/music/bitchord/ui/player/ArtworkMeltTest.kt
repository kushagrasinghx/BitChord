package com.music.bitchord.ui.player

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkMeltTest {
    private val width = MELT_WIDTH
    private val height = MELT_WIDTH * 2
    private val banner = MELT_WIDTH.toFloat()

    private fun bake(art: IntArray, artWidth: Int = 64, artHeight: Int = 64, seed: Int = 7) =
        bakeArtworkMelt(art, artWidth, artHeight, width, height, banner, seed)

    private fun flat(argb: Int, size: Int = 64 * 64) = IntArray(size) { argb }

    private fun luma(argb: Int) =
        0.25f * ((argb shr 16) and 0xFF) + 0.68f * ((argb shr 8) and 0xFF) + 0.07f * (argb and 0xFF)

    @Test
    fun everyPixelIsOpaque() {
        // The player is a sheet with the app's pages behind it; a hole shows them.
        val out = bake(IntArray(64 * 64) { 0xFF000000.toInt() or (it * 977) })
        assertEquals(width * height, out.size)
        assertTrue(out.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun sameCoverLandsTheSameWay() {
        val art = IntArray(64 * 64) { 0xFF000000.toInt() or (it * 977) }
        assertContentEquals(bake(art), bake(art))
    }

    @Test
    fun whiteCoverStaysDimEnoughForWhiteControls() {
        val out = bake(flat(0xFFFFFFFF.toInt()))
        val controls = out.copyOfRange(width * (height * 3 / 4), out.size)
        // Grey, and at the mesh's cap rather than a white screen behind white icons.
        assertTrue(controls.all { luma(it) < 150f })
    }

    @Test
    fun blackCoverStaysDark() {
        val out = bake(flat(0xFF000000.toInt()))
        assertTrue(out.all { luma(it) < 60f })
    }

    @Test
    fun singleColourCoverInventsNoOtherHue() {
        val out = bake(flat(0xFF2040C0.toInt()))
        for (pixel in out) {
            val red = (pixel shr 16) and 0xFF
            val blue = pixel and 0xFF
            assertTrue(blue > red, "expected to stay blue: ${pixel.toUInt().toString(16)}")
        }
    }

    @Test
    fun clipTallerThanTheBannerStartsItsMeltLower() {
        val art = IntArray(64 * 64) { if (it % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val square = bakeArtworkMelt(art, 64, 64, width, height, banner, 7, banner)
        val tall = bakeArtworkMelt(art, 64, 64, width, height, banner * 1.33f, 7, banner)
        // Past the square's melt, a taller clip is still melting into the same mesh.
        val row = (banner * 1.5f).toInt()
        assertTrue(square.copyOfRange(row * width, (row + 1) * width).toList() !=
            tall.copyOfRange(row * width, (row + 1) * width).toList())
    }

    @Test
    fun stripIsSolidUpTopAndGoneByItsFoot() {
        val strip = bakeMeltStrip(flat(0xFF804020.toInt()), 64, 64, 432, 416f)
        val rows = strip.size / 432
        assertTrue(strip.copyOfRange(0, 432).all { (it ushr 24) == 0xFF })
        assertTrue(strip.copyOfRange((rows - 1) * 432, rows * 432).all { (it ushr 24) < 8 })
    }

    @Test
    fun coverOfAnyShapeBakes() {
        val out = bake(flat(0xFF804020.toInt(), 80 * 40), artWidth = 80, artHeight = 40)
        assertEquals(width * height, out.size)
        val tiny = bake(flat(0xFF804020.toInt(), 1), artWidth = 1, artHeight = 1)
        assertEquals(width * height, tiny.size)
    }
}
