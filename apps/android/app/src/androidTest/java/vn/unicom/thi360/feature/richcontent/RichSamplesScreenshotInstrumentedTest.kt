package vn.unicom.thi360.feature.richcontent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(AndroidJUnit4::class)
class RichSamplesScreenshotInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderAllFortyThreeSamplesInLightAndDarkModes() {
        val samples = RichContentAndroidTestSupport.loadRichTextSamples()
        assertEquals("Expected 43 samples (12 valid + 31 invalid)", 43, samples.size)
        val imagesOffSample = samples.first { it.name == "bad-image-figure-when-images-off" }
        assertEquals(false, imagesOffSample.allowImages)
        assertTrue(
            "bad-image-figure-when-images-off must drop img and figure when allowImages=false",
            RichHtmlParser.parse(
                imagesOffSample.html,
                RichParseOptions(allowImages = imagesOffSample.allowImages),
            ).blocks.isEmpty(),
        )

        val pngBytes = createSyntheticChartPng()
        val server = ServerSocket(0)
        val running = AtomicBoolean(true)
        val serverThread = thread(isDaemon = true, name = "rich-samples-asset-server") {
            while (running.get()) {
                try {
                    val socket = server.accept()
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            line = reader.readLine()
                        }
                        val out = s.getOutputStream()
                        val headers = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${pngBytes.size}\r\nConnection: close\r\n\r\n"
                        out.write(headers.toByteArray(Charsets.US_ASCII))
                        out.write(pngBytes)
                        out.flush()
                    }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }

        val baseUrl = "http://127.0.0.1:${server.localPort}/"
        val client = OkHttpClient.Builder().cache(null).build()
        val assets = AttemptAssetSource(
            attemptId = "att-samples",
            baseUrl = baseUrl,
            client = client,
            cacheDir = composeRule.activity.cacheDir,
        )

        try {
            val engine = RaTeXFormulaEngine(composeRule.activity)
            val activeIndex = mutableIntStateOf(0)
            val activeTheme = mutableStateOf(ThemeMode.LIGHT)

            composeRule.setContent {
                val sample = samples[activeIndex.intValue]
                val doc = remember(sample.name, sample.allowImages) {
                    RichHtmlParser.parse(sample.html, RichParseOptions(allowImages = sample.allowImages))
                }
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = activeTheme.value)) {
                    CompositionLocalProvider(LocalFormulaEngine provides engine) {
                        Box(
                            modifier = Modifier
                                .width(390.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        ) {
                            RichContentView(document = doc, assets = assets, formulaEngine = engine)
                        }
                    }
                }
            }

            val outDir = RichContentAndroidTestSupport.outputDir("rich-samples")
            var writtenCount = 0

            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                val suffix = if (mode == ThemeMode.LIGHT) "light" else "dark"
                val bgColor = if (mode == ThemeMode.LIGHT) Color.WHITE else Color.parseColor("#111417")
                for (i in samples.indices) {
                    val sample = samples[i]
                    composeRule.runOnIdle {
                        activeTheme.value = mode
                        activeIndex.intValue = i
                    }
                    composeRule.waitForIdle()
                    Thread.sleep(60)
                    composeRule.waitForIdle()

                    composeRule.runOnIdle {
                        val contentView = composeRule.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
                        val bmp = RichContentAndroidTestSupport.captureViewBitmap(contentView, bgColor)
                        val outFile = File(outDir, "${sample.name}_$suffix.png")
                        RichContentAndroidTestSupport.savePng(bmp, outFile)
                        RichContentAndroidTestSupport.mirrorToHostTmp(outFile, "rich-samples/${sample.name}_$suffix.png")
                        bmp.recycle()
                        assertTrue("Sample PNG ${outFile.name} must be non-empty", outFile.length() > 100L)
                        writtenCount++
                    }
                }
            }
            assertEquals("Expected 86 PNGs (43 samples x 2 modes)", 86, writtenCount)
        } finally {
            assets.purgeAll()
            running.set(false)
            runCatching { server.close() }
            serverThread.join(1_000)
        }
    }

    private fun createSyntheticChartPng(): ByteArray {
        val bmp = Bitmap.createBitmap(240, 140, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F8FAFC"))
        val barPaint = Paint().apply { color = Color.parseColor("#0D9488") }
        c.drawRect(30f, 80f, 65f, 125f, barPaint)
        c.drawRect(85f, 55f, 120f, 125f, barPaint)
        c.drawRect(140f, 30f, 175f, 125f, barPaint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
