package com.music.bitchord

import com.music.bitchord.playback.HanVariants
import org.junit.Assert.assertEquals
import org.junit.Test

class HanVariantsTest {

    @Test
    fun `traditional characters fold to their simplified form`() {
        assertEquals("理性与任性之间", HanVariants.fold("理性與任性之間"))
        assertEquals("说好的幸福呢", HanVariants.fold("說好的幸福呢"))
        assertEquals("后来", HanVariants.fold("後來"))
    }

    @Test
    fun `text with nothing to fold comes back as it was`() {
        assertEquals("Kesariya", HanVariants.fold("Kesariya"))
        assertEquals("", HanVariants.fold(""))
        assertEquals("사랑해", HanVariants.fold("사랑해"))
    }

    @Test
    fun `simplified text is left alone`() {
        assertEquals("理性与任性之间", HanVariants.fold("理性与任性之间"))
    }

    @Test
    fun `folding is idempotent`() {
        val once = HanVariants.fold("愛與痛的邊緣")
        assertEquals(once, HanVariants.fold(once))
    }

    @Test
    fun `mixed text folds only the characters that vary`() {
        assertEquals("Love 爱 → Love 爱", HanVariants.fold("Love 愛 → Love 爱"))
    }
}
