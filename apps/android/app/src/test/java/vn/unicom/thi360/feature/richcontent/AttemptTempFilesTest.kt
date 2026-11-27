package vn.unicom.thi360.feature.richcontent

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef
import vn.unicom.thi360.testing.PortalTestHarness

class AttemptTempFilesTest {

    private lateinit var cacheDir: File
    private lateinit var sampleBytes: ByteArray

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("thi360-temp-files-test-").toFile()
        sampleBytes = "%PDF-1.4 sample-attempt-attachment-content".toByteArray(Charsets.UTF_8)
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun testTokens() = Tokens(
        accessToken = "tok-temp",
        refreshToken = "ref-temp",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        accountId = "acc-temp-1",
    )

    @Test
    fun loadKeepsBytesInMemoryOnlyAndOpenForViewerCleansUpOnClosePurgeLogoutAndStart() = runTest {
        val attemptId = "att-temp"
        val assetId = "asset-pdf-1"

        PortalTestHarness.create(initialTokens = testTokens()) {
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId") {
                MockResponse().setResponseCode(200).setBody(Buffer().write(sampleBytes))
            }
        }.use { harness ->
            val source = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
                sessionCleanup = harness.sessionCleanup,
            )

            val bytes = source.load(AttemptAssetRef.Attempt(assetId))
            assertArrayEquals(sampleBytes, bytes)
            assertTrue(
                "load() must not create any files in cacheDir",
                cacheDir.walkTopDown().filter { it.isFile }.toList().isEmpty(),
            )

            val temp = source.openForViewer(AttemptAssetRef.Attempt(assetId), extension = "pdf")
            val expectedParent = File(cacheDir, AttemptAssetSource.TEMP_DIR_NAME).canonicalFile
            assertEquals(expectedParent, temp.file.parentFile?.canonicalFile)
            assertFalse("Temp filename must be random, not original assetId", temp.file.name.contains(assetId))
            assertTrue(temp.file.isFile)
            assertArrayEquals(sampleBytes, temp.file.readBytes())

            temp.close()
            assertFalse("TempAssetFile.close() must delete temp file immediately", temp.file.exists())

            val unclosedForLeave = source.openForViewer(AttemptAssetRef.Attempt(assetId))
            assertTrue(unclosedForLeave.file.exists())
            assertTrue(source.memoryCacheEntryCount() > 0)
            source.purgeAll()
            assertFalse(unclosedForLeave.file.exists())
            assertEquals(0, source.memoryCacheEntryCount())

            val unclosedForLogout = source.openForViewer(AttemptAssetRef.Attempt(assetId))
            assertTrue(unclosedForLogout.file.exists())
            harness.sessionCleanup.onLogout("acc-temp-1")
            assertFalse("Logout cleanup must wipe attempt temp files", unclosedForLogout.file.exists())
            assertEquals(0, source.memoryCacheEntryCount())

            val orphaned = source.openForViewer(AttemptAssetRef.Attempt(assetId))
            assertTrue(orphaned.file.exists())
            harness.sessionCleanup.onAppStart()
            assertFalse("App start cleanup must wipe orphaned attempt temp files", orphaned.file.exists())
        }
    }
}

