package vn.unicom.thi360.core.appearance

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.testing.ContractFakeServer

class AppearanceRefreshTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun coldStartAndResumeCadenceHonorSixtySecondThrottleIfNoneMatch304AndVersionUpdates() = runTest {
        var nowMillis = 10_000L
        val cache = FileAppearanceCache(File(tempFolder.newFolder("appearance"), "cache.json"))
        val repository = AppearanceRepository(
            remoteSource = OkHttpAppearanceRemoteSource(
                webBaseUrl = server.url("/"),
                okHttpClient = OkHttpClient(),
            ),
            cache = cache,
            clockMillis = { nowMillis },
        )

        val v1Json = ContractFixtureLoader.readAppearanceFixture("tokens-thi360-green.json")
            .replace("\"version\": 0", "\"version\": 1")
        val v2Json = PaletteApplicationTest.NAVY_TEAL_JSON

        server.respondSequence(
            "GET",
            "/mobile/appearance-tokens.json",
            {
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("ETag", "\"1\"")
                    .setBody(v1Json)
            },
            {
                MockResponse()
                    .setResponseCode(304)
                    .addHeader("ETag", "\"1\"")
            },
            {
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("ETag", "\"3\"")
                    .setBody(v2Json)
            },
        )

        val first = repository.refresh(force = true)
        assertEquals(AppearanceRefreshResult.Updated(1), first)
        assertEquals("thi360-green", repository.current.value.presetId)
        assertEquals(1, repository.currentVersion)
        val req1 = server.requests("GET", "/mobile/appearance-tokens.json")[0]
        assertEquals("/mobile/appearance-tokens.json", req1.path)
        assertNull(req1.getHeader("If-None-Match"))

        nowMillis += 59_000L
        val throttled = repository.refreshOnResume()
        assertEquals(AppearanceRefreshResult.SkippedThrottle, throttled)
        assertEquals(1, server.requestCount("GET", "/mobile/appearance-tokens.json"))

        nowMillis += 1_000L
        val notModified = repository.refreshOnResume()
        assertEquals(AppearanceRefreshResult.NotModified, notModified)
        assertEquals("thi360-green", repository.current.value.presetId)
        val req2 = server.requests("GET", "/mobile/appearance-tokens.json")[1]
        assertEquals("\"1\"", req2.getHeader("If-None-Match"))

        nowMillis += 60_000L
        val updated = repository.refreshOnResume()
        assertEquals(AppearanceRefreshResult.Updated(3), updated)
        assertEquals("navy-teal", repository.current.value.presetId)
        assertEquals(3, repository.currentVersion)
        val req3 = server.requests("GET", "/mobile/appearance-tokens.json")[2]
        assertEquals("\"1\"", req3.getHeader("If-None-Match"))
    }
}
