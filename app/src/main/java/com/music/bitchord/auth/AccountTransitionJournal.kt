/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.auth

/**
 * Persisted journal for an in-flight account transition. Kept separate from
 * [AuthStore] so restart/recovery behavior can be exercised without opening
 * Android encrypted preferences.
 */
internal class AccountTransitionJournal(
    private val fromKey: String,
    private val toKey: String,
    private val startedKey: String,
    private val readString: (String) -> String?,
    private val writeString: (String, String?) -> Unit,
    private val readLong: (String) -> Long,
    private val writeLong: (String, Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun begin(from: String?, to: String) {
        // A blank target cannot be recovered or rolled back meaningfully.
        if (to.isBlank()) return
        writeString(fromKey, from)
        writeString(toKey, to)
        writeLong(startedKey, now())
    }

    fun pending(): AccountTransition? {
        val to = readString(toKey)?.takeIf { it.isNotBlank() } ?: return null
        return AccountTransition(readString(fromKey), to, readLong(startedKey))
    }

    fun hasPending(): Boolean = readString(toKey)?.isNotBlank() == true

    fun clear() {
        writeString(fromKey, null)
        writeString(toKey, null)
        writeLong(startedKey, 0L)
    }
}
