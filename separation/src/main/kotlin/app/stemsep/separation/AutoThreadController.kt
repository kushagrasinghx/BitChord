package app.stemsep.separation

internal class AutoThreadController(
    threadLimit: Int,
    private val targetLeadSeconds: Float = 5f,
    private val resumeLeadSeconds: Float = 8f,
    private val cooldownChunks: Int = 2,
) {
    private val maxThreads = threadLimit.coerceAtLeast(1)
    private val startThreads = minOf(DEFAULT_THREADS, maxThreads)
    private var cooldown = 0

    var threads: Int = startThreads
        private set

    fun observe(leadSeconds: Float?): Int {
        if (leadSeconds == null) return threads
        if (cooldown > 0) {
            cooldown--
            return threads
        }
        val desired = when {
            leadSeconds < URGENT_LEAD_SECONDS && threads < maxThreads -> maxThreads
            leadSeconds < targetLeadSeconds && threads < maxThreads ->
                (threads + 2).coerceAtMost(maxThreads)
            leadSeconds > resumeLeadSeconds && threads > startThreads -> threads - 1
            else -> threads
        }
        if (desired != threads) {
            threads = desired
            cooldown = cooldownChunks
        }
        return threads
    }

    private companion object {
        const val DEFAULT_THREADS = 4
        const val URGENT_LEAD_SECONDS = 2f
    }
}
