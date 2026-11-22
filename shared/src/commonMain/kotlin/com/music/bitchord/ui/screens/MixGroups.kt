package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.KeyedMix
import com.music.bitchord.data.model.ShelfItem

/**
 * One mood's mixes: its Supermix first, then the numbered ones in order.
 *
 * @property key the mood's English name, lower-case — stable across languages.
 * @property name the heading, in the language the mixes are shown in.
 */
data class MoodMixes(val key: String, val name: String, val items: List<ShelfItem>)

/** The mixes sorted into the page's three kinds; see [groupMixes]. */
class MixSections(
    val stations: List<ShelfItem>,
    val yours: List<ShelfItem>,
    val moods: List<MoodMixes>,
) {
    val isEmpty: Boolean get() = stations.isEmpty() && yours.isEmpty() && moods.isEmpty()
}

/**
 * Sorts the "Mixed for you" cards by name, since the page itself lists them in
 * one undivided run.
 *
 * Sorted by each card's English title, which says what kind of mix it is in
 * every language the app is shown in; the cards keep their own titles to show.
 *
 * - The named mixes ([SIGNATURE_MIXES]) become the tiles, in a fixed order.
 * - A mood's mixes — "Feel Good Supermix", "Feel Good Mix 1", "Feel Good Mix
 *   2" — are gathered under the name in front of them, in the order the page
 *   first names each mood; the heading is worked out from the shown titles
 *   (see [moodHeading]).
 * - Everything else is the listener's own: the My Mixes, which YouTube titles
 *   by what they sound like ("Modern Punjabi Beats") rather than by number.
 */
fun groupMixes(mixes: List<KeyedMix>): MixSections {
    val unique = mixes.distinctBy { it.item.browseId ?: it.item.title }
    val stations = mutableListOf<Pair<Int, ShelfItem>>()
    val yours = mutableListOf<ShelfItem>()
    // Keyed by the lower-cased English name; the value keeps the English
    // name as the page first spelled it. A Supermix sorts ahead of the
    // numbered mixes, as number zero.
    val moods = LinkedHashMap<String, Pair<String, MutableList<Pair<Int, ShelfItem>>>>()
    fun addMood(name: String, order: Int, item: ShelfItem) {
        moods.getOrPut(name.lowercase()) { name to mutableListOf() }.second += order to item
    }
    unique.forEach { mix ->
        val key = mix.englishTitle.trim()
        val rank = SIGNATURE_MIXES.indexOf(key.lowercase())
        val supermix = MOOD_SUPERMIX.matchEntire(key)
        val numbered = NUMBERED_MIX.matchEntire(key)
        when {
            rank >= 0 -> stations += rank to mix.item
            supermix != null -> addMood(supermix.groupValues[1], 0, mix.item)
            numbered != null && !numbered.groupValues[1].equals("My", ignoreCase = true) ->
                addMood(numbered.groupValues[1], numbered.groupValues[2].toInt(), mix.item)
            else -> yours += mix.item
        }
    }
    val englishByItem = unique.associate { it.item to it.englishTitle.trim() }
    return MixSections(
        stations = stations.sortedBy { it.first }.map { it.second },
        yours = yours,
        moods = moods.map { (key, entry) ->
            val (englishName, members) = entry
            val sorted = members.sortedBy { it.first }
            MoodMixes(
                key = key,
                name = moodHeading(englishName, sorted, englishByItem),
                items = sorted.map { it.second },
            )
        },
    )
}

/**
 * A mood row's heading, in the language its mixes are shown in.
 *
 * Shown in English, it is the mood's own name ("Feel Good"). In any other
 * language there is no word list to look the mood up in, so it is read off
 * the mixes themselves: what their shown titles have in common up to the
 * number — "Chill-Mix 1" and "Chill-Mix 2" give "Chill-Mix", "Mix Détente 1"
 * and "Mix Détente 2" give "Mix Détente". Wherever the language puts the
 * mood's name, the numbered mixes all carry it and differ only in the number.
 * With one numbered mix, its title loses the number; with none, the
 * Supermix's title is the heading as it stands.
 */
private fun moodHeading(
    englishName: String,
    members: List<Pair<Int, ShelfItem>>,
    englishByItem: Map<ShelfItem, String>,
): String {
    val shownInEnglish = members.all { (_, item) -> item.title.trim() == englishByItem[item] }
    if (shownInEnglish) return englishName
    val numbered = members.filter { it.first > 0 }.map { it.second.title.trim() }
    val heading = when {
        numbered.size >= 2 -> numbered.reduce { acc, title -> acc.commonPrefixWith(title) }
        numbered.size == 1 -> numbered.single()
        else -> members.firstOrNull()?.second?.title?.trim().orEmpty()
    }.trimEnd { !it.isLetter() }
    // Too little left to name anything — titles that differ before the
    // number does. The first mix's own title still says which mood it is.
    return heading.takeIf { it.length >= 2 }
        ?: members.firstOrNull()?.second?.title?.trim()
        ?: englishName
}

/** The named mixes' English titles, lower-case, in the order the tiles show them. */
private val SIGNATURE_MIXES = listOf(
    "my supermix", "discover mix", "new release mix", "replay mix", "archive mix", "liked music",
)

/** "Feel Good Supermix" → "Feel Good". "My Supermix" is a tile, matched before this. */
private val MOOD_SUPERMIX = Regex("""^(.+?)\s+supermix$""", RegexOption.IGNORE_CASE)

/** "Feel Good Mix 2" → "Feel Good", 2. */
private val NUMBERED_MIX = Regex("""^(.+?)\s+mix\s*(\d+)$""", RegexOption.IGNORE_CASE)
