package com.music.bitchord.data.stats

import java.security.MessageDigest

/**
 * Hex SHA-256 of [bytes]: what a month is recognised by between syncs. The JVM's
 * half of [BucketMath], which is shared with the web player.
 */
fun BucketMath.hash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
