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

import java.net.URI
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives the identifiers sent to one party server from a local secret plus
 * that server's canonical base URL. The same device/account therefore keeps
 * a stable identity on a server for rejoin, while two servers cannot compare
 * their identifiers to link the listener.
 *
 * HMAC-SHA-256 is used rather than a bare concatenation hash, and every field
 * is length-framed so the server URL and subject cannot be shifted into one
 * another. These are pseudonyms, not authentication.
 */
internal object PartyPseudonyms {
    private const val VERSION = "bit-chord/party-pseudonym/v1"

    fun derive(secret: String, serverBase: String, subject: String): String {
        val message = field(VERSION) + field(canonicalServer(serverBase)) + field(subject)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    private fun field(value: String): String = "${value.length}:$value"

    private fun canonicalServer(raw: String): String {
        val fallback = raw.trim().trimEnd('/').lowercase(Locale.ROOT)
        return runCatching {
            val uri = URI(raw.trim())
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return@runCatching fallback
            val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching fallback
            val port = when {
                (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80) -> -1
                else -> uri.port
            }
            val path = uri.path.orEmpty().trimEnd('/')
            buildString {
                append(scheme).append("://").append(host)
                if (port >= 0) append(':').append(port)
                append(path)
            }
        }.getOrDefault(fallback)
    }
}
