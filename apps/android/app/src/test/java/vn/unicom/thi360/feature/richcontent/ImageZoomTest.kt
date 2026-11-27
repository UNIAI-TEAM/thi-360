package vn.unicom.thi360.feature.richcontent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
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
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.MAX_IMAGE_ZOOM_SCALE
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView
import vn.unicom.thi360.feature.richcontent.ui.clampImagePanOffset
import vn.unicom.thi360.feature.richcontent.ui.clampImageZoomScale
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageZoomTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var cacheDir: File
    private lateinit var samplePng: ByteArray

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("thi360-zoom-test-").toFile()
        samplePng = requireNotNull(javaClass.classLoader?.getResourceAsStream("richcontent/sample.png"))
            .use { it.readBytes() }
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun testTokens() = Tokens(
        accessToken = "tok-zoom",
        refreshToken = "ref-zoom",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        accountId = "acc-zoom",
    )

    @Test
    fun zoomScaleClampsToMaximumFourTimesAndPanClampsToBounds() {
        assertEquals(1f, clampImageZoomScale(0.5f), 0.001f)
        assertEquals(2.5f, clampImageZoomScale(2.5f), 0.001f)
        assertEquals(MAX_IMAGE_ZOOM_SCALE, clampImageZoomScale(7.5f), 0.001f)

        assertEquals(Offset.Zero, clampImagePanOffset(Offset(100f, 100f), 1f, IntSize(400, 600)))
        val clamped = clampImagePanOffset(Offset(900f, -900f), 2f, IntSize(400, 600))
        assertEquals(200f, clamped.x, 0.001f)
        assertEquals(-300f, clamped.y, 0.001f)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun zoomableImageOpensFullscreenInAllModesAndClosePreservesSlotValue() {
        val assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
        val attemptId = "att-zoom"
        val html = """
            <p>Giá trị nhập là [[b1]] đơn vị.</p>
            <figure class="image" data-width="medium" data-zoom="true"><img src="" data-asset-id="$assetId" alt="Sơ đồ mạch điện"><figcaption>Hình 2</figcaption></figure>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html, RichParseOptions(slotMarkers = true))

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
            var currentMode by mutableStateOf(RichContentMode.Answering)
            var latestSlotValue = ""

            composeRule.setContent {
                val scrollState = rememberScrollState()
                var slotText by remember { mutableStateOf("") }
                latestSlotValue = slotText
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                        Column(modifier = Modifier.verticalScroll(scrollState)) {
                            RichContentView(
                                document = doc,
                                assets = assetSource,
                                mode = currentMode,
                                slots = mapOf(
                                    "b1" to {
                                        TextField(
                                            value = slotText,
                                            onValueChange = { slotText = it },
                                            modifier = Modifier.testTag("slot_b1_input"),
                                        )
                                    },
                                ),
                            )
                            Spacer(modifier = Modifier.height(600.dp))
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

            composeRule.onNodeWithTag("slot_b1_input").performTextInput("42")
            composeRule.waitForIdle()
            assertEquals("42", latestSlotValue)

            for (mode in listOf(RichContentMode.Answering, RichContentMode.Locked, RichContentMode.Review)) {
                currentMode = mode
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(RichContentTestTags.IMAGE_CONTENT).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_DIALOG).assertIsDisplayed()
                composeRule.onNodeWithText("Đóng").assertIsDisplayed()

                composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_CLOSE).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_DIALOG).assertDoesNotExist()
                assertEquals("Slot value must stay intact after closing zoom dialog in mode $mode", "42", latestSlotValue)
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun nonZoomableAndStandaloneImagesDoNotOpenDialog() {
        val assetId1 = "11111111-1111-4111-8111-111111111111"
        val assetId2 = "22222222-2222-4222-8222-222222222222"
        val attemptId = "att-nozoom"
        val html = """
            <figure class="image" data-width="medium" data-zoom="false"><img src="" data-asset-id="$assetId1" alt="No zoom figure"></figure>
            <p><img src="" data-asset-id="$assetId2" alt="Standalone image"></p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)

        PortalTestHarness.create(initialTokens = testTokens()) {
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId1") {
                MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(samplePng))
            }
            respond("GET", "/api/v1/me/attempts/$attemptId/assets/$assetId2") {
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
                        RichContentView(document = doc, assets = assetSource)
                    }
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(RichContentTestTags.IMAGE_FIGURE).assertIsDisplayed()
                    composeRule.onNodeWithTag(RichContentTestTags.IMAGE_LOADING).assertDoesNotExist()
                    true
                }.getOrDefault(false)
            }

            val nodes = composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag(RichContentTestTags.IMAGE_CONTENT),
            )
            assertTrue(nodes.fetchSemanticsNodes().size == 2)
            nodes[0].performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_DIALOG).assertDoesNotExist()

            nodes[1].performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_DIALOG).assertDoesNotExist()
        }
    }
}

