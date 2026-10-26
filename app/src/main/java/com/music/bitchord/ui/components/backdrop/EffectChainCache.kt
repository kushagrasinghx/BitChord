package com.music.bitchord.ui.components.backdrop

/**
 * One retained result per effect position, never a history of animated values.
 * Keys include each stage's input so changing an earlier stage also replaces
 * every downstream result that depends on it.
 */
internal class EffectChainCache<T> {
    private data class Entry<T>(val key: Any, val result: T)
    private val entries = ArrayList<Entry<T>>()
    private var position = 0

    fun begin() { position = 0 }

    fun obtain(key: Any, create: () -> T): T {
        val index = position++
        entries.getOrNull(index)?.let { if (it.key == key) return it.result }
        val result = create()
        val entry = Entry(key, result)
        if (index < entries.size) entries[index] = entry else entries.add(entry)
        return result
    }

    fun end() {
        while (entries.size > position) entries.removeAt(entries.lastIndex)
    }

    fun clear() {
        entries.clear()
        position = 0
    }
}
