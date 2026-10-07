package com.music.bitchord

import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The addon priority order.
 *
 * Worth its own tests because the ordering is *implicit*: there is no rank
 * field, the stored list order is the priority, and what makes that work is
 * that `active()` sorts by kind with a stable sort. A change that swapped in an
 * unstable sort, or that rebuilt the list through a map, would break priority
 * silently — every source still present, every one of them tried, just not in
 * the order the user dragged them into.
 */
class AddonOrderTest {

    private val before = SourceRegistry.configs.value

    @After
    fun tearDown() {
        SourceRegistry.configs.value = before
    }

    private fun addon(name: String) = SourceConfig(
        kind = SourceKind.ADDON,
        label = name,
        baseUrl = "https://$name.example.com",
    )

    private fun seed(vararg names: String): List<SourceConfig> {
        val addons = names.map(::addon)
        SourceRegistry.configs.value = addons +
            SourceConfig(kind = SourceKind.JIOSAAVN) +
            SourceConfig(kind = SourceKind.YOUTUBE)
        return addons
    }

    /** The user-added sources in stored order, which is the order they are tried. */
    private fun addonLabels() =
        SourceRegistry.configs.value.filter { it.kind.isUserAdded }.map { it.label }

    @Test
    fun `reordering puts the addons in the order given`() {
        val (a, b, c) = seed("a", "b", "c").let { Triple(it[0], it[1], it[2]) }

        SourceRegistry.reorderAddons(listOf(c.id, a.id, b.id))

        assertEquals(listOf("c", "a", "b"), addonLabels())
    }

    /** A drag moves addons past each other and must not disturb anything else. */
    @Test
    fun `reordering leaves the fixed-rank sources alone`() {
        val addons = seed("a", "b")

        SourceRegistry.reorderAddons(listOf(addons[1].id, addons[0].id))

        assertEquals(
            listOf(SourceKind.ADDON, SourceKind.ADDON, SourceKind.JIOSAAVN, SourceKind.YOUTUBE),
            SourceRegistry.configs.value.map { it.kind },
        )
    }

    /**
     * The list can move under a drag — an addon removed elsewhere, a probe
     * writing a name back. Reordering what it can beats dropping whatever the
     * gesture did not know about.
     */
    @Test
    fun `an addon missing from the order is kept rather than dropped`() {
        val addons = seed("a", "b", "c")

        SourceRegistry.reorderAddons(listOf(addons[2].id, addons[0].id))

        assertEquals(listOf("c", "a", "b"), addonLabels())
    }

    @Test
    fun `an unknown id in the order is ignored`() {
        val addons = seed("a", "b")

        SourceRegistry.reorderAddons(listOf("not-an-id", addons[1].id, addons[0].id))

        assertEquals(listOf("b", "a"), addonLabels())
    }

    /** Nothing to reorder, and nothing to write to disk over. */
    @Test
    fun `a single addon is left untouched`() {
        val addons = seed("only")

        SourceRegistry.reorderAddons(listOf(addons[0].id))

        assertEquals(listOf("only"), addonLabels())
    }

    // ── Duplicates ────────────────────────────────────────────────────────

    /**
     * The same catalogue configured twice is not a harmless mistake: it is
     * every search and every stream lookup run twice per track against one
     * server, which is exactly the "you are spamming me" shape an addon
     * operator notices.
     */
    @Test
    fun `an exact repeat of a configured URL is a duplicate`() {
        val addons = seed("a")
        assertEquals(addons[0].id, SourceRegistry.duplicateOf(addons[0].baseUrl)?.id)
    }

    /** A trailing slash is not a different server. */
    @Test
    fun `a trailing slash does not make a new source`() {
        val addons = seed("a")
        assertEquals(addons[0].id, SourceRegistry.duplicateOf(addons[0].baseUrl + "/")?.id)
    }

    /** Host and scheme are case-insensitive; two spellings are one address. */
    @Test
    fun `a differently-cased host is the same source`() {
        seed("a")
        assertNotNull(SourceRegistry.duplicateOf("https://A.EXAMPLE.COM"))
    }

    /**
     * The path is *not* case-folded, and this is the case that matters: on this
     * protocol a token lives in the path, and two tokens differing only in case
     * are two different credentials, not one address typed twice.
     */
    @Test
    fun `paths differing only in case are different sources`() {
        SourceRegistry.configs.value = listOf(
            SourceConfig(kind = SourceKind.ADDON, baseUrl = "https://host.example.com/AbC"),
        )
        assertNull(SourceRegistry.duplicateOf("https://host.example.com/abc"))
        assertNotNull(SourceRegistry.duplicateOf("https://host.example.com/AbC"))
    }

    /** Editing a source without changing its URL must not refuse itself. */
    @Test
    fun `a source is not its own duplicate when it is the one being edited`() {
        val addons = seed("a")
        assertNull(SourceRegistry.duplicateOf(addons[0].baseUrl, exceptId = addons[0].id))
    }

    @Test
    fun `an unconfigured URL is not a duplicate`() {
        seed("a")
        assertNull(SourceRegistry.duplicateOf("https://b.example.com"))
    }

    /** A built-in source has no address, so nothing can collide with it. */
    @Test
    fun `a blank URL never matches the built-in sources`() {
        seed("a")
        assertNull(SourceRegistry.duplicateOf(""))
        assertNull(SourceRegistry.duplicateOf("   "))
    }
}
