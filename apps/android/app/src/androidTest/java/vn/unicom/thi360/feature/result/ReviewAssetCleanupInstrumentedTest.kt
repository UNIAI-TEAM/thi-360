package vn.unicom.thi360.feature.result

import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.result.review.ReviewMapper
import vn.unicom.thi360.feature.result.review.ReviewScreen
import vn.unicom.thi360.feature.result.review.ReviewUiState
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

@RunWith(AndroidJUnit4::class)
class ReviewAssetCleanupInstrumentedTest {

    private val samplePngBytes: ByteArray = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        Base64.DEFAULT,
    )

    private fun listAllRegularFiles(root: File?): Set<String> {
        if (root == null || !root.exists()) return emptySet()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.canonicalPath }
            .toSet()
    }

    @Test
    fun closingReviewActivityLeavesNoSolutionAssetFilesInAppStorage() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AttemptAssetSource.purgeTempDirectory(ctx.cacheDir)

        val baselineFiles = listAllRegularFiles(ctx.filesDir)
        val baselineCache = listAllRegularFiles(ctx.cacheDir)
        val baselineNoBackup = listAllRegularFiles(ctx.noBackupFilesDir)

        val attemptId = "00000000-0000-7000-8000-000000000001"
        val solutionAssetId = "sol-asset-device-99"
        val solutionAssetPath = "/api/v1/me/attempts/$attemptId/solution-assets/$solutionAssetId"

        val fetchedLatch = CountDownLatch(1)
        val authHeaders = CopyOnWriteArrayList<String>()
        val requestedPaths = CopyOnWriteArrayList<String>()
        val running = AtomicBoolean(true)
        val server = ServerSocket(0)

        val serverThread = thread(isDaemon = true, name = "review-asset-cleanup-server") {
            while (running.get()) {
                try {
                    val socket = server.accept()
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        val path = requestLine.split(" ").getOrNull(1).orEmpty()
                        requestedPaths += path
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            if (line.startsWith("Authorization:", ignoreCase = true)) {
                                authHeaders += line.substringAfter(':').trim()
                            }
                            line = reader.readLine()
                        }
                        val out = s.getOutputStream()
                        val hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${samplePngBytes.size}\r\nConnection: close\r\n\r\n"
                        out.write(hdr.toByteArray(Charsets.US_ASCII))
                        out.write(samplePngBytes)
                        out.flush()
                        if (path == solutionAssetPath) {
                            fetchedLatch.countDown()
                        }
                    }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }

        val client = OkHttpClient.Builder()
            .cache(null)
            .addInterceptor { chain ->
                val authed = chain.request().newBuilder()
                    .header("Authorization", "Bearer device-review-token")
                    .build()
                chain.proceed(authed)
            }
            .build()

        val assetSource = AttemptAssetSource(
            attemptId = attemptId,
            baseUrl = "http://127.0.0.1:${server.localPort}/",
            client = client,
            cacheDir = ctx.cacheDir,
        )

        val partId = UUID.randomUUID()
        val model = ReviewMapper.map(
            AttemptReviewResponse(
                attemptId = UUID.fromString(attemptId),
                state = AttemptReviewResponse.State.SCORED,
                resultView = "FULL_SOLUTION",
                parts = listOf(AttemptReviewPart(partId = partId, partCode = "P1", ord = 0, state = "CLOSED")),
                items = listOf(
                    AttemptReviewItem(
                        itemId = UUID.randomUUID(),
                        partId = partId,
                        partCode = "P1",
                        displayNo = 1,
                        ord = 1,
                        questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                        content = "<p>Câu kiểm tra ảnh lời giải</p>",
                        body = AttemptReviewQuestionBody(
                            options = listOf(AttemptReviewOption(id = "a", content = "<p>A</p>")),
                        ),
                        pointsMax = BigDecimal.ONE,
                        earnedPoints = BigDecimal.ONE,
                        outcome = AttemptReviewItem.Outcome.CORRECT,
                        solution = """<p>Lời giải</p><p><img src="$solutionAssetPath" alt="Anh loi giai" /></p>""",
                    ),
                ),
            ),
        )

        try {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            scenario.onActivity { activity ->
                activity.setContent {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        DisposableEffect(assetSource) {
                            onDispose { assetSource.close() }
                        }
                        ReviewScreen(
                            uiState = ReviewUiState(
                                attemptId = attemptId,
                                loadState = LoadState.Loaded(model),
                            ),
                            onBack = {},
                            onRetry = {},
                            onSelectPart = {},
                            onSelectGroup = {},
                            onPreviousGroup = { null },
                            onNextGroup = { null },
                            onGoToMyExams = {},
                            onGoToHistory = {},
                            assets = assetSource,
                            formulaEngine = SourceBoxFormulaEngine(),
                        )
                    }
                }
            }

            assertTrue(
                "Expected ReviewScreen to fetch solution asset $solutionAssetPath",
                fetchedLatch.await(10, TimeUnit.SECONDS),
            )
            assertTrue(
                "Expected Bearer Authorization header on solution-assets request",
                authHeaders.any { it == "Bearer device-review-token" },
            )

            val viewerFile = runBlocking {
                assetSource.openForViewer(AttemptAssetRef.Solution(solutionAssetId), "png").file
            }
            assertTrue("Temp viewer file should exist before closing Review", viewerFile.isFile)

            scenario.close()

            assertFalse("Temp viewer file must be deleted on leave", viewerFile.exists())
            assertFalse(
                "attempt-assets temp directory must be purged on leave",
                AttemptAssetSource.tempAssetDirectory(ctx.cacheDir).exists(),
            )

            val afterFiles = listAllRegularFiles(ctx.filesDir) - baselineFiles
            val afterCache = listAllRegularFiles(ctx.cacheDir) - baselineCache
            val afterNoBackup = listAllRegularFiles(ctx.noBackupFilesDir) - baselineNoBackup
            val remainingAssetFiles = (afterFiles + afterCache + afterNoBackup).filter { path ->
                path.contains(AttemptAssetSource.TEMP_DIR_NAME, ignoreCase = true) ||
                    path.contains("solution-assets", ignoreCase = true) ||
                    path.contains(solutionAssetId, ignoreCase = true)
            }
            assertEquals("No solution-asset files may remain on disk", emptyList<String>(), remainingAssetFiles)
        } finally {
            assetSource.close()
            running.set(false)
            runCatching { server.close() }
            serverThread.join(1_000)
        }
    }
}
