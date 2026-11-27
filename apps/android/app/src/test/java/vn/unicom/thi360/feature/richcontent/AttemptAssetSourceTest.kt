package vn.unicom.thi360.feature.richcontent

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetBlockedException
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetLoadException
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.testing.ContractFixtureIndex
import vn.unicom.thi360.testing.PortalTestHarness

class AttemptAssetSourceTest {

    private lateinit var cacheDir: File
    private lateinit var samplePngBytes: ByteArray

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("thi360-asset-source-test-").toFile()
        samplePngBytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("richcontent/sample.png")) {
            "Missing test resource richcontent/sample.png"
        }.use { it.readBytes() }
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun testTokens() = Tokens(
        accessToken = "bearer-test-token-123",
        refreshToken = "refresh-token-456",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        accountId = "acc-student-1",
    )

    @Test
    fun externalSrcNeverRequested() = runTest {
        val externalServer = MockWebServer()
        externalServer.start()
        try {
            val externalUrl = externalServer.url("/evil.png").toString()
            val protocolRelative = "//${externalServer.hostName}:${externalServer.port}/evil.png"
            val html = """
                <p><img src="$externalUrl" alt="ext1"><img src="https://evil.example/ext2.png" alt="ext2"><img src="$protocolRelative" alt="ext3"></p>
            """.trimIndent()

            val doc = RichHtmlParser.parse(html)
            val images = (doc.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Image>()
            assertEquals(3, images.size)
            for (img in images) {
                assertEquals(AttemptAssetRef.Blocked, img.ref)
            }

            PortalTestHarness.create(initialTokens = testTokens()).use { harness ->
                val source = AttemptAssetSource(
                    attemptId = "att-1",
                    baseUrl = harness.server.url("/"),
                    client = harness.stack.authenticatedOkHttpClient,
                    cacheDir = cacheDir,
                )
                for (img in images) {
                    try {
                        source.load(img.ref)
                        fail("Expected AttemptAssetBlockedException for external img src")
                    } catch (_: AttemptAssetBlockedException) {
                    }
                }
                assertEquals(0, externalServer.requestCount)
                assertEquals(0, harness.server.requestCount("GET"))
            }
        } finally {
            externalServer.shutdown()
        }
    }

    @Test
    fun mapsAssetIdAndAdminPath() = runTest {
        val assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
        val assetId2 = "11111111-2222-4333-8444-555555555555"
        val attemptId = "att-100"

        val emptySrcDoc = RichHtmlParser.parse("""<p><img src="" data-asset-id="$assetId" alt="a1"></p>""")
        val adminPathDoc = RichHtmlParser.parse("""<p><img src="/api/v1/admin/question-assets/$assetId2" data-asset-id="$assetId2" alt="a2"></p>""")

        val img1 = (emptySrcDoc.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Image>().single()
        val img2 = (adminPathDoc.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Image>().single()
        assertEquals(AttemptAssetRef.Attempt(assetId), img1.ref)
        assertEquals(AttemptAssetRef.Attempt(assetId2), img2.ref)

        PortalTestHarness.create(initialTokens = testTokens()) {
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId") {
                MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(samplePngBytes))
            }
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId2") {
                MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(samplePngBytes))
            }
        }.use { harness ->
            val source = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
            )

            assertArrayEquals(samplePngBytes, source.load(img1.ref))
            assertArrayEquals(samplePngBytes, source.load(img2.ref))

            val req1 = harness.server.lastRequest("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId")
            val req2 = harness.server.lastRequest("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId2")
            assertEquals("Bearer bearer-test-token-123", req1?.getHeader("Authorization"))
            assertEquals("Bearer bearer-test-token-123", req2?.getHeader("Authorization"))
        }
    }

    @Test
    fun solutionAssetPath() = runTest {
        val assetId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        val attemptId = "att-200"
        val html = """<p><img src="/api/v1/me/attempts/$attemptId/solution-assets/$assetId" alt="solution"></p>"""
        val doc = RichHtmlParser.parse(html)
        val img = (doc.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Image>().single()
        assertEquals(AttemptAssetRef.Solution(assetId), img.ref)

        val notFoundFixture = ContractFixtureIndex.find("downloadSolutionAsset", 404)
        val missingId = "99999999-9999-4999-8999-999999999999"

        PortalTestHarness.create(initialTokens = testTokens()) {
            respond("GET", "/api/v1/me/attempts/$attemptId/solution-assets/$assetId") {
                MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(samplePngBytes))
            }
            respondJson(
                "GET",
                "/api/v1/me/attempts/$attemptId/solution-assets/$missingId",
                404,
                notFoundFixture.file.readText(Charsets.UTF_8),
            )
        }.use { harness ->
            val source = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
            )

            assertArrayEquals(samplePngBytes, source.load(img.ref))
            val req = harness.server.lastRequest("GET", "/api/v1/me/attempts/$attemptId/solution-assets/$assetId")
            assertEquals("Bearer bearer-test-token-123", req?.getHeader("Authorization"))

            try {
                source.load(AttemptAssetRef.Solution(missingId))
                fail("Expected 404 AttemptAssetLoadException")
            } catch (e: AttemptAssetLoadException) {
                assertEquals(404, e.statusCode)
            }
        }
    }

    @Test
    fun concurrentTwentyLoadsAndInFlightDeduplication() = runBlocking {
        val attemptId = "att-concurrent"
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val dedupCalls = AtomicInteger(0)

        PortalTestHarness.create(initialTokens = testTokens()) {
            respondDynamic("GET", "/api/v1/me/attempts/$attemptId/assets/dedup-id") {
                dedupCalls.incrementAndGet()
                startedLatch.countDown()
                releaseLatch.await(5, TimeUnit.SECONDS)
                MockResponse().setResponseCode(200).setBody(Buffer().write(samplePngBytes))
            }
            for (i in 1..20) {
                respond("GET", "/api/v1/me/attempts/$attemptId/assets/img-$i") {
                    MockResponse().setResponseCode(200).setBody(Buffer().write(samplePngBytes))
                }
            }
        }.use { harness ->
            val source = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
            )

            val results = (1..20).map { i ->
                async(Dispatchers.Default) { source.load(AttemptAssetRef.Attempt("img-$i")) }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertArrayEquals(samplePngBytes, it) }

            val d1 = async(Dispatchers.Default) { source.load(AttemptAssetRef.Attempt("dedup-id")) }
            assertTrue(startedLatch.await(5, TimeUnit.SECONDS))
            val d2 = async(Dispatchers.Default) { source.load(AttemptAssetRef.Attempt("dedup-id")) }
            releaseLatch.countDown()
            assertArrayEquals(samplePngBytes, d1.await())
            assertArrayEquals(samplePngBytes, d2.await())
            assertEquals(1, dedupCalls.get())
        }
    }
}
