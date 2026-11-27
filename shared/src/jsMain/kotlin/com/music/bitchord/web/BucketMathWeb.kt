@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.stats.BucketMath

/*
 * Month arithmetic for backup and Drive sync, as the phone does it (BucketMath.kt), over the
 * months' canonical JSON bytes (Int8Array) so the web never parses a month itself.
 */

/** BucketMath.plus: the two months added, as canonical bytes. Throws on different months. */
@JsExport
fun bucketPlus(a: ByteArray, b: ByteArray): ByteArray =
    BucketMath.canonicalBytes(BucketMath.plus(BucketMath.decode(a), BucketMath.decode(b)))

/** BucketMath.minus: what [a] has that [b] doesn't, or null when nothing is left (BucketMath.isEmpty). */
@JsExport
fun bucketMinus(a: ByteArray, b: ByteArray): ByteArray? =
    BucketMath.minus(BucketMath.decode(a), BucketMath.decode(b))
        .takeUnless(BucketMath::isEmpty)
        ?.let(BucketMath::canonicalBytes)
