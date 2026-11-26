package com.music.bitchord.data.remote

import android.net.DnsResolver
import android.net.InetAddresses
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import java.net.InetAddress
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Optional read-ahead never starts an uncancellable platform hostname lookup. */
internal suspend fun resolveArtworkHost(host: String): List<InetAddress> {
    if (Build.VERSION.SDK_INT >= 29) return withTimeoutOrNull(2_000L) { resolveArtworkHostOnDefaultNetwork(host) }.orEmpty()
    val octets = host.split('.').map { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return emptyList()
    return listOf(InetAddress.getByAddress(octets.map { it!!.toByte() }.toByteArray()))
}

@RequiresApi(29)
@Suppress("DEPRECATION")
private suspend fun resolveArtworkHostOnDefaultNetwork(host: String): List<InetAddress> {
    if (InetAddresses.isNumericAddress(host)) return listOf(InetAddresses.parseNumericAddress(host))
    return suspendCancellableCoroutine { continuation ->
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        DnsResolver.getInstance().query(null, host, DnsResolver.FLAG_EMPTY, Executor { it.run() }, cancel,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    continuation.resume(if (rcode == 0) answer else emptyList())
                }
                override fun onError(error: DnsResolver.DnsException) {
                    continuation.resume(emptyList())
                }
            },
        )
    }
}
