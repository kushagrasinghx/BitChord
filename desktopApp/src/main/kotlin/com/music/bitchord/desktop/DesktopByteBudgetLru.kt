package com.music.bitchord.desktop

/**
 * A least-recently-used cache held to a budget of bytes rather than a count of entries.
 *
 * By bytes because what goes in here ranges from a 120px row thumbnail to a 720px sleeve, and
 * counting them would treat those as the same thing.
 *
 * Separate from what it holds so the policy can be tested without decoding anything: the sizes are
 * whatever [sizeOf] says they are.
 */
internal class DesktopByteBudgetLru<V : Any>(
    private val budgetBytes: Long,
    private val sizeOf: (V) -> Long,
) {

    /** Access-ordered, so iteration hands back the coldest entry first. */
    private val entries = LinkedHashMap<String, V>(64, 0.75f, true)

    private var held = 0L

    /** How much is being held right now. */
    val heldBytes: Long get() = synchronized(entries) { held }

    fun get(key: String): V? = synchronized(entries) { entries[key] }

    fun contains(key: String): Boolean = synchronized(entries) { entries.containsKey(key) }

    fun put(key: String, value: V) = synchronized(entries) {
        // Put first, then trim: the entry that has just arrived is the one the caller is about to
        // use, and it is the one eviction may not take.
        val replaced = entries.put(key, value)
        if (replaced != null) held -= sizeOf(replaced)
        held += sizeOf(value)
        trim()
    }

    fun clear() = synchronized(entries) {
        entries.clear()
        held = 0L
    }

    /** Drops the coldest entries until the budget is met, always leaving the newest. */
    private fun trim() {
        val iterator = entries.entries.iterator()
        while (held > budgetBytes && entries.size > 1 && iterator.hasNext()) {
            held -= sizeOf(iterator.next().value)
            iterator.remove()
        }
    }
}
