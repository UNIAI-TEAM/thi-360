package vn.unicom.thi360.core.network

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.testing.ContractFakeServer

class RetryAfterTest {

    private interface RateLimitApi {
        @GET("/api/v1/test-limit")
        suspend fun call(): Response<String>
    }

    private lateinit var server: ContractFakeServer
    private lateinit var api: RateLimitApi

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(RateLimitApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesHeaderThenBodyAndHandlesMissingOrEdgeOneSecond() = runTest {
        // 1. Header takes precedence over body.
        assertEquals(12L, RetryAfter.parse("12", ApiError(code = "RATE_LIMITED", retryAfter = 30L)))
        assertEquals(1L, RetryAfter.parse(" 1 ", null))

        // 2. Falls back to body retryAfter when header missing or invalid.
        assertEquals(45L, RetryAfter.parse(null, ApiError(code = "RATE_LIMITED", retryAfter = 45L)))
        assertEquals(45L, RetryAfter.parse("invalid", ApiError(code = "RATE_LIMITED", retryAfter = 45L)))

        // 3. Both missing or <= 0 -> null.
        assertNull(RetryAfter.parse(null, ApiError(code = "RATE_LIMITED", retryAfter = null)))
        assertNull(RetryAfter.parse("0", ApiError(code = "RATE_LIMITED", retryAfter = 0L)))

        // 4. End-to-end via safeApiCall with 429 Retry-After header = 1.
        server.inject429("GET", "/api/v1/test-limit", retryAfterHeaderSeconds = 1L)
        val headerFailure = safeApiCall { api.call() } as ApiResult.HttpFailure
        assertEquals(429, headerFailure.statusCode)
        assertEquals(1L, headerFailure.retryAfterSeconds)
        val headerFailed = headerFailure.toLoadFailed()
        assertEquals(1L, headerFailed.retryAfter)
        assertEquals(LoadFailureKind.Server, headerFailed.kind)

        // 5. End-to-end via safeApiCall with 429 body retryAfter = 60 and no header.
        server.inject429("GET", "/api/v1/test-limit", retryAfterHeaderSeconds = null, bodyRetryAfterSeconds = 60L)
        val bodyFailure = safeApiCall { api.call() } as ApiResult.HttpFailure
        assertEquals(60L, bodyFailure.retryAfterSeconds)
        assertEquals(60L, bodyFailure.toLoadFailed().retryAfter)

        // 6. End-to-end via safeApiCall with 429 and neither header nor body retryAfter.
        server.inject429("GET", "/api/v1/test-limit", retryAfterHeaderSeconds = null, bodyRetryAfterSeconds = null)
        val missingFailure = safeApiCall { api.call() } as ApiResult.HttpFailure
        assertNull(missingFailure.retryAfterSeconds)
        assertNull(missingFailure.toLoadFailed().retryAfter)
    }

    @Test
    fun retryAfterGateBlocksUntilExactElapsedSeconds() {
        var nowMillis = 50_000L
        val gate = RetryAfterGate(clockMillis = { nowMillis })
        assertTrue(gate.canRetry())

        gate.recordDelay(1L)
        assertFalse(gate.canRetry())
        assertEquals(1L, gate.remainingSeconds())

        nowMillis += 999L
        assertFalse(gate.canRetry())
        assertEquals(1L, gate.remainingSeconds())

        nowMillis += 1L
        assertTrue(gate.canRetry())
        assertEquals(0L, gate.remainingSeconds())
    }
}
