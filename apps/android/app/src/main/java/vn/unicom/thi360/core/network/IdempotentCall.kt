package vn.unicom.thi360.core.network

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

class IdempotentCall(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var activeKey: String? = null

    val currentKey: String?
        get() = activeKey

    val isInFlight: Boolean
        get() = inFlight.get()

    fun restore(key: String) {
        require(key.isNotBlank()) { "Idempotency key must not be blank" }
        synchronized(lock) {
            if (!inFlight.get()) {
                activeKey = key
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            if (!inFlight.get()) {
                activeKey = null
            }
        }
    }

    suspend fun <T> execute(
        block: suspend (idempotencyKey: String) -> ApiResult<T>,
    ): ApiResult<T>? {
        if (!inFlight.compareAndSet(false, true)) {
            return null
        }

        val key = synchronized(lock) {
            activeKey ?: keyGenerator().also { activeKey = it }
        }

        return try {
            val result = block(key)
            if (!shouldRetainKey(result)) {
                synchronized(lock) {
                    if (activeKey == key) {
                        activeKey = null
                    }
                }
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            inFlight.set(false)
        }
    }

    companion object {
        fun shouldRetainKey(result: ApiResult<*>): Boolean = when (result) {
            is ApiResult.Success -> false
            is ApiResult.NetworkFailure -> true
            is ApiResult.HttpFailure ->
                result.statusCode == HTTP_REQUEST_TIMEOUT ||
                    result.statusCode == HTTP_TOO_MANY_REQUESTS ||
                    result.statusCode in 500..599
        }

        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
