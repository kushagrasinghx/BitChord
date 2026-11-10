package moe.rukamori.archivetune.qobuz

import org.json.JSONArray
import org.json.JSONObject

data class QobuzToken(
    val token: String,
    val userId: String = "",
    val appId: String,
    val appSecret: String,
    val label: String = "",
    val subscription: String = "",
    val poolId: Long? = null,
) {
    val id: String get() = token.take(12)

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("token", token)
            put("userId", userId)
            put("appId", appId)
            put("appSecret", appSecret)
            put("label", label)
            put("subscription", subscription)
            poolId?.let { put("poolId", it) }
        }

    companion object {
        fun fromJson(obj: JSONObject): QobuzToken? {
            val token = obj.optString("token").trim()
            val appId = obj.optString("appId").trim()
            val appSecret = obj.optString("appSecret").trim()
            if (token.isEmpty() || appId.isEmpty() || appSecret.isEmpty()) return null
            return QobuzToken(
                token = token,
                userId = obj.optString("userId").trim(),
                appId = appId,
                appSecret = appSecret,
                label = obj.optString("label").trim(),
                subscription = obj.optString("subscription").trim(),
                poolId = obj.optLong("poolId", 0L).takeIf { it > 0L },
            )
        }

        fun listToJson(tokens: List<QobuzToken>): String =
            JSONArray().apply { tokens.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(raw: String?): List<QobuzToken> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { fromJson(it) }
            }
        }
    }
}
