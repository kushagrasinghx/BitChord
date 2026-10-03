package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop side of an addon's `allowDownloads`. */
class DesktopAddonPolicyTest {

    private fun manifest(extra: String) = DesktopAddonClient.json.decodeFromString(
        DesktopAddonManifest.serializer(),
        """{"id":"x","name":"X","resources":["search","stream"]$extra}""",
    )

    @Test
    fun aMissingKeyAllowsDownloads() {
        assertTrue(manifest("").downloadsAllowed)
    }

    @Test
    fun numbersBooleansAndStringsAreAllRead() {
        for (off in listOf("0", "false", "\"0\"")) assertFalse(manifest(""","allowDownloads":$off""").downloadsAllowed, off)
        for (on in listOf("1", "true", "\"1\"")) assertTrue(manifest(""","allowDownloads":$on""").downloadsAllowed, on)
        // Unreadable means the default, never a flip.
        assertTrue(manifest(""","allowDownloads":"maybe"""").downloadsAllowed)
    }
}
