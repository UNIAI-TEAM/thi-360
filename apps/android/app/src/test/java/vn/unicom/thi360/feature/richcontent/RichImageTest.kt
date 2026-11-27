package vn.unicom.thi360.feature.richcontent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView
import vn.unicom.thi360.testing.ContractFixtureIndex
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichImageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var cacheDir: File
    private lateinit var samplePng: ByteArray

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("thi360-rich-image-test-").toFile()
        samplePng = requireNotNull(javaClass.classLoader?.getResourceAsStream("richcontent/sample.png"))
            .use { it.readBytes() }
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun testTokens() = Tokens(
        accessToken = "tok-image-test",
        refreshToken = "ref-image-test",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        accountId = "acc-img",
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun mediumIs70PercentCentered() {
        val assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
        val attemptId = "att-img-1"
        val html = """<figure class="image" data-width="medium" data-zoom="true"><img src="/api/v1/admin/question-assets/$assetId" alt="Biểu đồ số lượng lợn 2015 - 2021" data-asset-id="$assetId"><figcaption>Biểu đồ số lượng lợn và sản lượng thịt lợn</figcaption><p class="figure-source">Nguồn: GSO</p></figure>"""
        val doc = RichHtmlParser.parse(html)

        PortalTestHarness.create(initialTokens = testTokens()) {
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId") {
                MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(samplePng))
            }
        }.use { harness ->
            val assetSource = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
            )

            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                        Box(modifier = Modifier.width(390.dp)) {
                            RichContentView(document = doc, assets = assetSource)
                        }
                    }
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(RichContentTestTags.IMAGE_CONTENT).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            val figBounds = composeRule.onNodeWithTag(RichContentTestTags.IMAGE_FIGURE).getUnclippedBoundsInRoot()
            val imgBounds = composeRule.onNodeWithTag(RichContentTestTags.IMAGE_CONTENT).getUnclippedBoundsInRoot()
            val captionBounds = composeRule.onNodeWithContentDescription("Biểu đồ số lượng lợn và sản lượng thịt lợn").getUnclippedBoundsInRoot()

            val innerFigWidth = (figBounds.right - figBounds.left).value - 24f
            val imgWidth = (imgBounds.right - imgBounds.left).value
            val ratio = imgWidth / innerFigWidth
            assertTrue("Medium image must be ~70% of column width, got ratio=$ratio", ratio in 0.65f..0.75f)

            val figCenterX = (figBounds.left.value + figBounds.right.value) / 2f
            val imgCenterX = (imgBounds.left.value + imgBounds.right.value) / 2f
            assertTrue("Medium image must be horizontally centered", abs(figCenterX - imgCenterX) <= 2f)
            assertTrue("Caption must appear below image", captionBounds.top >= imgBounds.bottom - 2.dp)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun errorThenTapRetries() {
        val assetId = "12345678-1234-4234-8234-1234567890ab"
        val attemptId = "att-retry-1"
        val html = """
            <p>Nội dung câu hỏi giữ nguyên</p>
            <figure class="image" data-width="full" data-zoom="false"><img src="" data-asset-id="$assetId" alt="Mạch điện R1 R2"></figure>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        val notFoundFixture = ContractFixtureIndex.find("downloadAttemptAsset", 404)
        val hits = AtomicInteger(0)

        PortalTestHarness.create(initialTokens = testTokens()) {
            respondDynamic("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId") {
                val n = hits.incrementAndGet()
                if (n == 1) {
                    MockResponse()
                        .setResponseCode(404)
                        .addHeader("Content-Type", "application/json")
                        .setBody(notFoundFixture.file.readText(Charsets.UTF_8))
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "image/png")
                        .setBody(Buffer().write(samplePng))
                }
            }
        }.use { harness ->
            val assetSource = AttemptAssetSource(
                attemptId = attemptId,
                baseUrl = harness.server.url("/"),
                client = harness.stack.authenticatedOkHttpClient,
                cacheDir = cacheDir,
            )

            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                        RichContentView(document = doc, assets = assetSource)
                    }
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ERROR).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithContentDescription("Nội dung câu hỏi giữ nguyên").assertIsDisplayed()
            composeRule.onNodeWithText("Mạch điện R1 R2").assertIsDisplayed()
            composeRule.onNodeWithText("Không tải được ảnh. Bấm để tải lại.").assertIsDisplayed()
            assertEquals(1, hits.get())

            composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ERROR).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(RichContentTestTags.IMAGE_CONTENT).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertEquals(2, hits.get())
            composeRule.onNodeWithContentDescription("Nội dung câu hỏi giữ nguyên").assertIsDisplayed()
        }
    }
}
