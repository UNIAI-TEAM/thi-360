package vn.unicom.thi360.core.network

object RetryAfter {
    fun parse(headerValue: String?, error: ApiError?): Long? {
        val fromHeader = headerValue?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        if (fromHeader != null) return fromHeader
        return error?.retryAfter?.takeIf { it > 0L }
    }
}

class RetryAfterGate(
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Volatile
    private var blockedUntilMillis: Long = 0L

    fun recordDelay(retryAfterSeconds: Long?) {
        if (retryAfterSeconds == null || retryAfterSeconds <= 0L) return
        val candidate = clockMillis() + retryAfterSeconds * 1_000L
        if (candidate > blockedUntilMillis) {
            blockedUntilMillis = candidate
        }
    }

    fun canRetry(): Boolean = clockMillis() >= blockedUntilMillis

    fun remainingSeconds(): Long {
        val diff = blockedUntilMillis - clockMillis()
        return if (diff <= 0L) 0L else (diff + 999L) / 1_000L
    }

    fun reset() {
        blockedUntilMillis = 0L
    }
}
