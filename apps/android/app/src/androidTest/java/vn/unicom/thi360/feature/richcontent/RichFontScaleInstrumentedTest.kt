package vn.unicom.thi360.feature.richcontent

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.Spanned
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView
import vn.unicom.thi360.feature.richcontent.ui.RichScriptSpan

@RunWith(AndroidJUnit4::class)
class RichFontScaleInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun WithFontScale(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val currentDensity = LocalDensity.current
        val currentConfig = LocalConfiguration.current
        val scaledConfig = remember(currentConfig, fontScale) {
            Configuration(currentConfig).apply { this.fontScale = fontScale }
        }
        CompositionLocalProvider(
            LocalDensity provides Density(density = currentDensity.density, fontScale = fontScale),
            LocalConfiguration provides scaledConfig,
            content = content,
        )
    }

    @Test
    fun twelveValidSamplesAt200PercentHaveNoClippedOrOverlappingText() {
        val validSamples = RichContentAndroidTestSupport.loadRichTextSamples().filter { it.valid }
        assertEquals("Expected 12 valid samples", 12, validSamples.size)
        val engine = RaTeXFormulaEngine(composeRule.activity)
        val activeIndex = mutableIntStateOf(0)

        composeRule.setContent {
            val sample = validSamples[activeIndex.intValue]
            val doc = remember(sample.name, sample.allowImages) {
                RichHtmlParser.parse(sample.html, RichParseOptions(allowImages = sample.allowImages))
            }
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                WithFontScale(2.0f) {
                    CompositionLocalProvider(LocalFormulaEngine provides engine) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            RichContentView(document = doc, assets = null, formulaEngine = engine)
                        }
                    }
                }
            }
        }

        val outDir = RichContentAndroidTestSupport.outputDir("fontscale-200")
        for (i in validSamples.indices) {
            val sample = validSamples[i]
            composeRule.runOnIdle { activeIndex.intValue = i }
            composeRule.waitForIdle()

            val rootBounds = composeRule.onNodeWithTag(RichContentTestTags.ROOT).getUnclippedBoundsInRoot()
            assertTrue(
                "Sample ${sample.name} at 200% must not horizontally overflow 360dp (width=${rootBounds.right - rootBounds.left})",
                (rootBounds.right - rootBounds.left) <= 360.dp + 2.dp,
            )

            composeRule.runOnIdle {
                val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
                assertTrue("Sample ${sample.name} should have visible text views", tvs.isNotEmpty())
                tvs.forEach { tv ->
                    if (tv.text.isNotBlank()) {
                        assertTrue("TextView height must be positive for ${sample.name}", tv.height > 0)
                        val layout = tv.layout
                        if (layout != null) {
                            assertTrue(
                                "TextView height (${tv.height}) must fit its layout (${layout.height}) for ${sample.name}",
                                tv.height + 2 >= layout.height,
                            )
                            val spanned = tv.text as? Spanned
                            if (spanned != null) {
                                val scriptSpans = spanned.getSpans(0, spanned.length, RichScriptSpan::class.java)
                                for (span in scriptSpans) {
                                    val spanStart = spanned.getSpanStart(span).coerceAtLeast(0)
                                    val line = layout.getLineForOffset(spanStart)
                                    when (span.kind) {
                                        RichScriptSpan.Kind.SUB -> {
                                            val needed = RichScriptSpan.requiredDescentPx(tv.textSize)
                                            val actual = layout.getLineBottom(line) - layout.getLineBaseline(line)
                                            assertTrue(
                                                "Subscript line bottom clearance ($actual px) must cover required ($needed px) for ${sample.name}",
                                                actual >= needed - 1,
                                            )
                                        }
                                        RichScriptSpan.Kind.SUP -> {
                                            val needed = RichScriptSpan.requiredAscentPx(tv.textSize)
                                            val actual = layout.getLineBaseline(line) - layout.getLineTop(line)
                                            assertTrue(
                                                "Superscript line top clearance ($actual px) must cover required ($needed px) for ${sample.name}",
                                                actual >= needed - 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val decorBmp = RichContentAndroidTestSupport.captureViewBitmap(
                    composeRule.activity.window.decorView.findViewById(android.R.id.content),
                )
                val outFile = File(outDir, "${sample.name}_200.png")
                RichContentAndroidTestSupport.savePng(decorBmp, outFile)
                RichContentAndroidTestSupport.mirrorToHostTmp(outFile, "fontscale-200/${sample.name}_200.png")
            }
        }
    }

    @Test
    fun fractionAndTextScaleProportionallyBetween100And200Percent() {
        val doc = RichHtmlParser.parse("""<p>Biết \(\frac{a}{b}\) thì</p>""")
        val engine = RaTeXFormulaEngine(composeRule.activity)
        val scaleState = mutableFloatStateOf(1.0f)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                WithFontScale(scaleState.floatValue) {
                    CompositionLocalProvider(LocalFormulaEngine provides engine) {
                        Box(modifier = Modifier.width(360.dp).padding(12.dp)) {
                            RichContentView(document = doc, assets = null, formulaEngine = engine)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        fun measureBAndNumeratorA(): Pair<Int, Int> {
            var bHeight = 0
            var aHeight = 0
            composeRule.runOnIdle {
                val tv = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
                    .first { it.text.toString().contains("Biết") }
                val rect = Rect()
                tv.paint.getTextBounds("B", 0, 1, rect)
                bHeight = rect.height()

                val span = RichContentAndroidTestSupport.raTeXSpans(tv).single()
                val bmp = Bitmap.createBitmap(
                    span.rendered.width.coerceAtLeast(1),
                    (span.rendered.ascent + span.rendered.descent).coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                span.rendered.drawable.draw(canvas)

                var barY = bmp.height / 2
                var maxDark = -1
                for (y in (bmp.height / 4) until (bmp.height * 3 / 4)) {
                    var dark = 0
                    for (x in 0 until bmp.width) {
                        val px = bmp.getPixel(x, y)
                        val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                        if (lum < 200) dark++
                    }
                    if (dark > maxDark) {
                        maxDark = dark
                        barY = y
                    }
                }
                var aTop = Int.MAX_VALUE
                var aBottom = Int.MIN_VALUE
                for (y in 0 until (barY - 1).coerceAtLeast(1)) {
                    var rowHasInk = false
                    for (x in 0 until bmp.width) {
                        val px = bmp.getPixel(x, y)
                        val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                        if (lum < 200) {
                            rowHasInk = true
                            break
                        }
                    }
                    if (rowHasInk) {
                        if (y < aTop) aTop = y
                        if (y > aBottom) aBottom = y
                    }
                }
                aHeight = if (aBottom >= aTop) (aBottom - aTop + 1) else 0
            }
            return bHeight to aHeight
        }

        val (b1, a1) = measureBAndNumeratorA()
        assertTrue("100% heights must be positive (b1=$b1, a1=$a1)", b1 > 0 && a1 > 0)

        composeRule.runOnIdle { scaleState.floatValue = 2.0f }
        composeRule.waitForIdle()
        val (b2, a2) = measureBAndNumeratorA()
        assertTrue("200% heights must be positive (b2=$b2, a2=$a2)", b2 > 0 && a2 > 0)

        val bRatio = b2.toFloat() / b1.toFloat()
        val aRatio = a2.toFloat() / a1.toFloat()
        assertTrue("Glyph 'B' height ratio must be in 1.8..2.2, got $bRatio ($b1 -> $b2)", bRatio in 1.8f..2.2f)
        assertTrue("Numerator 'a' height ratio must be in 1.8..2.2, got $aRatio ($a1 -> $a2)", aRatio in 1.8f..2.2f)

        composeRule.runOnIdle {
            val bmp = RichContentAndroidTestSupport.captureViewBitmap(
                composeRule.activity.window.decorView.findViewById(android.R.id.content),
            )
            val f = File(RichContentAndroidTestSupport.outputDir("fontscale-200"), "fraction_200_dt719.png")
            RichContentAndroidTestSupport.savePng(bmp, f)
            RichContentAndroidTestSupport.mirrorToHostTmp(f, "fontscale-200/fraction_200_dt719.png")
        }
    }

    @Test
    fun longInlineFormulaAt200PercentScrollsOnOwnLineWithoutVerticalOverlap() {
        val terms = (1..40).joinToString(" + ") { "a_{$it}" }
        val html = """
            <p>Dòng trên</p>
            <p>Trước \($terms = S\) sau</p>
            <p>Dòng dưới</p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                WithFontScale(2.0f) {
                    CompositionLocalProvider(LocalFormulaEngine provides engine) {
                        Box(modifier = Modifier.width(360.dp)) {
                            RichContentView(document = doc, assets = null, formulaEngine = engine)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val beforeBounds = composeRule.onNodeWithContentDescription("Trước").assertIsDisplayed().getUnclippedBoundsInRoot()
        val formulaBounds = composeRule.onNodeWithTag(RichContentTestTags.FORMULA_SCROLL).assertIsDisplayed().getUnclippedBoundsInRoot()
        val afterBounds = composeRule.onNodeWithContentDescription("sau").assertIsDisplayed().getUnclippedBoundsInRoot()
        val rootBounds = composeRule.onNodeWithTag(RichContentTestTags.ROOT).getUnclippedBoundsInRoot()

        assertTrue("Root must stay within 360dp at 200%", (rootBounds.right - rootBounds.left) <= 360.dp + 2.dp)
        assertTrue("Formula must sit below 'Trước' without overlap", formulaBounds.top >= beforeBounds.bottom - 2.dp)
        assertTrue("'sau' must sit below formula without overlap", afterBounds.top >= formulaBounds.bottom - 2.dp)

        composeRule.runOnIdle {
            val bmp = RichContentAndroidTestSupport.captureViewBitmap(
                composeRule.activity.window.decorView.findViewById(android.R.id.content),
            )
            val f = File(RichContentAndroidTestSupport.outputDir("fontscale-200"), "long_inline_200_dt720.png")
            RichContentAndroidTestSupport.savePng(bmp, f)
            RichContentAndroidTestSupport.mirrorToHostTmp(f, "fontscale-200/long_inline_200_dt720.png")
        }
    }

    @Test
    fun rescaleKeepsSlotsAndScroll() {
        val filler = (1..18).joinToString("") { "<p>Đoạn văn kiểm tra cuộn số $it với nội dung dài để đủ chiều cao cuộn.</p>" }
        val html = "<p>Đoạn mở đầu kiểm tra co giãn cỡ chữ.</p><p>Điền kết quả vào ô [[slot_1]] và tiếp tục.</p>$filler"
        val doc = RichHtmlParser.parse(html, RichParseOptions(slotMarkers = true))
        val engine = RaTeXFormulaEngine(composeRule.activity)
        val scaleState = mutableFloatStateOf(1.0f)
        lateinit var scrollStateRef: androidx.compose.foundation.ScrollState

        composeRule.setContent {
            val scrollState = rememberScrollState()
            scrollStateRef = scrollState
            var slotAnswer by rememberSaveable { mutableStateOf("") }
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                WithFontScale(scaleState.floatValue) {
                    CompositionLocalProvider(LocalFormulaEngine provides engine) {
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .height(420.dp)
                                .verticalScroll(scrollState),
                        ) {
                            RichContentView(
                                document = doc,
                                assets = null,
                                formulaEngine = engine,
                                slots = mapOf(
                                    "slot_1" to {
                                        OutlinedTextField(
                                            value = slotAnswer,
                                            onValueChange = { slotAnswer = it },
                                            singleLine = true,
                                            modifier = Modifier
                                                .width(140.dp)
                                                .testTag("slot_input_1"),
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val initialTextSize = composeRule.runOnIdle {
            RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
                .first { it.text.toString().contains("Đoạn văn kiểm tra cuộn số 1") }
                .textSize
        }

        composeRule.onNodeWithTag("slot_input_1").performTextInput("42.5")
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            runBlocking { scrollStateRef.scrollTo(220) }
        }
        composeRule.waitForIdle()
        val scrollAt1x = scrollStateRef.value
        assertTrue("Expected positive scroll offset before rescale", scrollAt1x >= 180)

        composeRule.runOnIdle { scaleState.floatValue = 2.0f }
        composeRule.waitForIdle()
        val scaledTextSize = composeRule.runOnIdle {
            RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
                .first { it.text.toString().contains("Đoạn văn kiểm tra cuộn số 1") }
                .textSize
        }
        assertTrue(
            "Text size must scale up at 2.0x ($initialTextSize -> $scaledTextSize)",
            scaledTextSize / initialTextSize in 1.8f..2.2f,
        )
        composeRule.onNodeWithText("42.5").assertExists()
        assertTrue("Scroll position must remain preserved at 2.0x", scrollStateRef.value >= 180)

        composeRule.runOnIdle { scaleState.floatValue = 1.0f }
        composeRule.waitForIdle()
        val restoredTextSize = composeRule.runOnIdle {
            RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
                .first { it.text.toString().contains("Đoạn văn kiểm tra cuộn số 1") }
                .textSize
        }
        assertEquals(initialTextSize, restoredTextSize, 1.0f)
        composeRule.onNodeWithText("42.5").assertExists()
        assertEquals("Scroll offset must be preserved after returning to 1.0x", scrollAt1x, scrollStateRef.value)
    }
}

