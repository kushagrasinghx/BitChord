package com.music.bitchord

import com.music.bitchord.data.lyrics.KuGou
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KuGou]'s header/footer credit stripping — see [KuGou.stripCredits]. The
 * fixture below is a live `lyrics.kugou.com/download` response for
 * "Heartbreak Anniversary" (Giveon), captured while building this source, so
 * this is what the real header block actually looks like: a non-stamped
 * `[id:]`/`[ti:]`/`[ar:]`/`[al:]`/`[by:]` preamble, then three stamped lines —
 * the title repeated, a "Lyrics by：" credit, and a "Composed by：" credit —
 * before the first sung word.
 */
class KuGouTest {

    private val fixture = """
        [id:${'$'}00000000]
        [ti:Heartbreak Anniversary]
        [ar:Giveon]
        [al:Heartbreak Anniversary]
        [by:]
        [00:00.00]Heartbreak Anniversary - GIVĒON
        [00:04.12]Lyrics by：Giveon Evans/Varren Wade/Sevn Thomas
        [00:08.24]Composed by：Giveon Evans/Varren Wade/Sevn Thomas/Maneesh
        [00:12.36]Ooh
        [00:15.90]Balloons are deflated
        [00:19.73]Guess they look lifeless like me
        [03:04.12]'Cause I think of you think of you
        [03:07.90]Ooh ooh ooh-ooh
    """.trimIndent()

    private fun strip(text: String): String = with(KuGou) { text.stripCredits() }

    @Test
    fun `drops the unstamped header tags entirely`() {
        val stripped = strip(fixture)
        assertFalse(stripped.contains("[id:"))
        assertFalse(stripped.contains("[ti:"))
        assertFalse(stripped.contains("[by:]"))
    }

    @Test
    fun `cuts everything through the last credit line, title restated and all`() {
        val stripped = strip(fixture)
        assertFalse("title-as-lyric line", stripped.contains("GIVĒON"))
        assertFalse("Lyrics by credit", stripped.contains("Lyrics by"))
        assertFalse("Composed by credit", stripped.contains("Composed by"))
        assertTrue("first real line survives", stripped.startsWith("[00:12.36]Ooh"))
    }

    @Test
    fun `real sung lines after the header survive untouched`() {
        val stripped = strip(fixture)
        assertTrue(stripped.contains("[00:15.90]Balloons are deflated"))
        assertTrue(stripped.contains("[00:19.73]Guess they look lifeless like me"))
    }

    @Test
    fun `the outro is not mistaken for a credit and cut`() {
        val stripped = strip(fixture)
        assertTrue(stripped.contains("[03:07.90]Ooh ooh ooh-ooh"))
    }

    @Test
    fun `a lyric with no credit block anywhere is left whole`() {
        val clean = """
            [00:01.00]first line
            [00:05.00]second line
        """.trimIndent()
        assertEquals(clean, strip(clean))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals("", strip(""))
    }
}
