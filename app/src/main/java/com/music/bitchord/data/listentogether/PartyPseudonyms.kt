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

import java.security.MessageDigest
import java.util.Locale

/**
 * Derives the identifiers sent to one party server from a local secret plus
 * that server's normalized base URL. The same device/account therefore keeps
 * a stable identity on a server for rejoin, while two servers cannot compare
 * their identifiers to link the listener.
 *
 * These are pseudonyms, not authentication: a server that already knows a
 * listener's secret-derived identifier can recognize it again, but the value
 * contains no account or device identifier in plaintext.
 */
internal object PartyPseudonyms {
    fun derive(secret: String, serverBase: String, subject: String): String {
        val server = serverBase.trim().trimEnd('/').lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$secret|$server|$subject".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
