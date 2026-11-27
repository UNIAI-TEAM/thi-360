package vn.unicom.thi360.core.network

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Header
import retrofit2.http.POST
import vn.unicom.thi360.testing.ContractFakeServer

class IdempotentCallTest {

    private interface IdempotentProbeApi {
        @POST("/api/v1/orders")
        suspend fun createOrder(@Header("Idempotency-Key") key: String): Response<String>
    }

    private lateinit var server: ContractFakeServer
    private lateinit var api: IdempotentProbeApi

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(IdempotentProbeApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun retryMatrixKeepsKeyOnNetworkTimeout408429And5xxAndRotatesOn2xxAndOther4xx() = runBlocking(Dispatchers.IO) {
        val seq = AtomicInteger(0)
        val call = IdempotentCall(keyGenerator = { "key-${seq.incrementAndGet()}" })

        // 1. Network disconnect -> keeps same key on retry.
        server.injectNetworkError("POST", "/api/v1/orders", count = 1, policy = SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val netFail = call.execute { key -> safeApiCall { api.createOrder(key) } }
        assertTrue(netFail is ApiResult.NetworkFailure)
        assertEquals("key-1", call.currentKey)

        // 2. Timeout -> keeps same key.
        server.injectNetworkError("POST", "/api/v1/orders", count = 1, policy = SocketPolicy.NO_RESPONSE)
        val timeoutFail = call.execute { key -> safeApiCall { api.createOrder(key) } }
        assertTrue(timeoutFail is ApiResult.NetworkFailure)
        assertEquals("key-1", call.currentKey)

        // 3. 408, 429, 500, 503 -> all keep same key "key-1".
        for (status in listOf(408, 429, 500, 503)) {
            server.respondJson("POST", "/api/v1/orders", status, """{"code":"RETRYABLE_$status"}""")
            val res = call.execute { key -> safeApiCall { api.createOrder(key) } }
            assertTrue("Expected HttpFailure for $status", res is ApiResult.HttpFailure)
            assertEquals("Key must be preserved after HTTP $status", "key-1", call.currentKey)
        }

        // Verify all retryable attempts sent the exact same key-1.
        server.respondJson("POST", "/api/v1/orders", 200, """{"orderCode":"ORD-1"}""")
        val ok = call.execute { key -> safeApiCall { api.createOrder(key) } }
        assertTrue(ok is ApiResult.Success)
        assertNull("Key must clear after 2xx success", call.currentKey)

        val headersSentForOrder1 = server.requests("POST", "/api/v1/orders")
            .mapNotNull { it.getHeader("Idempotency-Key") }
        assertTrue(headersSentForOrder1.size >= 5)
        assertTrue("All retries must send key-1, got $headersSentForOrder1", headersSentForOrder1.all { it == "key-1" })

        // 4. Next call after 2xx generates fresh key-2.
        server.respondJson("POST", "/api/v1/orders", 200, """{"orderCode":"ORD-2"}""")
        call.execute { key -> safeApiCall { api.createOrder(key) } }
        val secondOrderKey = server.lastRequest("POST", "/api/v1/orders")?.getHeader("Idempotency-Key")
        assertEquals("key-2", secondOrderKey)

        // 5. Non-retryable 4xx (400, 404, 409, 410, 422) rotate key on next click.
        var previousKey = secondOrderKey
        for (status in listOf(400, 404, 409, 410, 422)) {
            server.respondJson("POST", "/api/v1/orders", status, """{"code":"CLIENT_$status"}""")
            val res = call.execute { key -> safeApiCall { api.createOrder(key) } }
            assertTrue(res is ApiResult.HttpFailure)
            assertNull("Key must clear after HTTP $status", call.currentKey)
            val usedKey = server.lastRequest("POST", "/api/v1/orders")?.getHeader("Idempotency-Key")
            assertNotNull(usedKey)
            assertNotEquals("HTTP $status must rotate key for subsequent calls", previousKey, usedKey)
            previousKey = usedKey
        }
    }

    @Test
    fun doubleTapWhileInFlightSendsSingleRequestAndRestoreReusesPersistedKey() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ApiResult<String>>()
        val calls = AtomicInteger(0)
        val receivedKeys = mutableListOf<String>()
        val call = IdempotentCall(keyGenerator = { "single-tap-key" })

        val first = async {
            call.execute { key ->
                calls.incrementAndGet()
                receivedKeys += key
                started.complete(Unit)
                release.await()
            }
        }
        started.await()

        // Second tap while first is in flight returns null and does not invoke block.
        val secondResult = call.execute { _ ->
            calls.incrementAndGet()
            ApiResult.Success("should-not-run")
        }
        assertNull(secondResult)
        assertEquals(1, calls.get())

        // Simulate app restart after transient failure: restore saved key and retry.
        release.complete(ApiResult.HttpFailure(503, ApiError("UNAVAILABLE")))
        first.await()
        val persistedKey = checkNotNull(call.currentKey)

        val restoredCall = IdempotentCall(keyGenerator = { "must-not-generate-new-key" })
        restoredCall.restore(persistedKey)
        assertEquals(persistedKey, restoredCall.currentKey)
        val retriedKeys = mutableListOf<String>()
        restoredCall.execute { key ->
            retriedKeys += key
            ApiResult.Success("ok")
        }
        assertEquals(listOf(persistedKey), retriedKeys)
        assertNull(restoredCall.currentKey)
    }
}
