package vn.unicom.thi360.feature.result.review

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ReviewAssetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    private val samplePngBytes: ByteArray = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        Base64.DEFAULT,
    )

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loadsAttemptAndSolutionAssetsWithBearerHandles404AndPurgesOnLeave() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        val stemAssetPath = "/api/v1/me/attempts/$attemptId/assets/asset-stem-1"
        val solAssetPath = "/api/v1/me/attempts/$attemptId/solution-assets/asset-sol-1"
        val missingAssetPath = "/api/v1/me/attempts/$attemptId/solution-assets/asset-sol-missing"

        server.respond("GET", stemAssetPath) {
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(Buffer().write(samplePngBytes))
        }
        server.respond("GET", solAssetPath) {
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(Buffer().write(samplePngBytes))
        }
        server.respond("GET", missingAssetPath) {
            MockResponse().setResponseCode(404)
        }

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder()
            .cache(null)
            .addInterceptor { chain ->
                val authed = chain.request().newBuilder()
                    .header("Authorization", "Bearer review-asset-token-77")
                    .build()
                chain.proceed(authed)
            }
            .build()

        val assetSource = AttemptAssetSource(
            attemptId = attemptId,
            baseUrl = server.url("/"),
            client = client,
            cacheDir = ctx.cacheDir,
        )

        val partId = UUID.randomUUID()
        val response = AttemptReviewResponse(
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
                    content = """<p>Câu có ảnh đề</p><p><img src="$stemAssetPath" alt="Hinh de" /></p>""",
                    body = AttemptReviewQuestionBody(
                        options = listOf(AttemptReviewOption(id = "a", content = "<p>A</p>")),
                    ),
                    pointsMax = BigDecimal.ONE,
                    earnedPoints = BigDecimal.ONE,
                    outcome = AttemptReviewItem.Outcome.CORRECT,
                    solution = """<p>Lời giải có ảnh</p><p><img src="$solAssetPath" alt="Hinh loi giai" /></p><p><img src="$missingAssetPath" alt="Hinh loi 404" /></p>""",
                ),
            ),
        )
        val model = ReviewMapper.map(response)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                if (visible) {
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

        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", stemAssetPath) >= 1 &&
                server.requestCount("GET", solAssetPath) >= 1 &&
                server.requestCount("GET", missingAssetPath) >= 1
        }

        val stemReq = server.lastRequest("GET", stemAssetPath)
        val solReq = server.lastRequest("GET", solAssetPath)
        val missingReq = server.lastRequest("GET", missingAssetPath)
        assertEquals("Bearer review-asset-token-77", stemReq?.getHeader("Authorization"))
        assertEquals("Bearer review-asset-token-77", solReq?.getHeader("Authorization"))
        assertEquals("Bearer review-asset-token-77", missingReq?.getHeader("Authorization"))

        composeRule.onNodeWithTag(ReviewTestTags.questionCard(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertExists()
        assertTrue(assetSource.memoryCacheEntryCount() >= 2)

        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()
        assertEquals(0, assetSource.memoryCacheEntryCount())
        assertFalse(AttemptAssetSource.tempAssetDirectory(ctx.cacheDir).exists())
    }
}
