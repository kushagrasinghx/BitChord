package moe.rukamori.archivetune.tidal

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object TidalDns : Dns {
    private const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"
    private const val GOOGLE_DOH = "https://dns.google/resolve"

    private val BOOTSTRAP: Map<String, List<String>> =
        mapOf(
            "cloudflare-dns.com" to listOf("104.16.248.249", "104.16.249.249"),
            "dns.google" to listOf("8.8.8.8", "8.8.4.4"),
        )

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private class CacheEntry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val bootstrapDns =
        Dns { hostname ->
            BOOTSTRAP[hostname]?.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
                ?: Dns.SYSTEM.lookup(hostname)
        }

    private val dohClient =
        OkHttpClient
            .Builder()
            .dns(bootstrapDns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { entry ->
            if (entry.expiresAt > System.currentTimeMillis() && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        if (system.isNotEmpty()) {
            cache[hostname] = CacheEntry(system, System.currentTimeMillis() + CACHE_TTL_MS)
            return system
        }

        val viaDoh = resolveOverHttps(hostname)
        if (viaDoh.isNotEmpty()) {
            Timber.tag("TidalDns").d("resolved %s via DoH (%d address(es))", hostname, viaDoh.size)
            cache[hostname] = CacheEntry(viaDoh, System.currentTimeMillis() + CACHE_TTL_MS)
            return viaDoh
        }

        cache[hostname]?.addresses?.takeIf { it.isNotEmpty() }?.let {
            Timber.tag("TidalDns").w("using stale cached DNS for %s (DoH failed)", hostname)
            return it
        }
        throw UnknownHostException("Unable to resolve host \"$hostname\" via system DNS or DoH")
    }

    private fun resolveOverHttps(hostname: String): List<InetAddress> {
        val a = queryDoh(hostname, type = 1)
        val aaaa = if (a.isEmpty()) queryDoh(hostname, type = 28) else emptyList()
        return (a + aaaa).mapNotNull { ip ->

            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }
    }

    private fun queryDoh(hostname: String, type: Int): List<String> {
        for (endpoint in listOf(CLOUDFLARE_DOH, GOOGLE_DOH)) {
            val result =
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url("$endpoint?name=$hostname&type=$type")
                            .header("Accept", "application/dns-json")
                            .get()
                            .build()
                    dohClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful || body.isBlank()) return@use emptyList()
                        val answers = JSONObject(body).optJSONArray("Answer") ?: return@use emptyList()
                        buildList {
                            for (i in 0 until answers.length()) {
                                val entry = answers.optJSONObject(i) ?: continue

                                if (entry.optInt("type") != type) continue
                                entry.optString("data").takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                }.getOrElse { emptyList() }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
