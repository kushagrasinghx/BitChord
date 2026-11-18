/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.data.listentogether

import java.util.UUID

/**
 * Migration policy for the party pseudonym key. The key is only usable when
 * it has been written to secure storage and read back successfully. A failed
 * secure write never falls back to a plaintext key and never removes the old
 * preference before the encrypted copy is verified.
 */
internal class PartySecretStore(
    private val readSecure: () -> String?,
    private val writeSecureVerified: (String) -> Boolean,
    private val readLegacy: () -> String?,
    private val removeLegacy: () -> Unit,
    private val generate: () -> String = { UUID.randomUUID().toString() },
) {
    fun secretOrNull(): String? {
        readSecure()?.takeIf { it.isNotBlank() }?.let { return it }
        val candidate = readLegacy()?.takeIf { it.isNotBlank() } ?: generate()
        if (!writeSecureVerified(candidate)) return null
        removeLegacy()
        return candidate
    }
}
