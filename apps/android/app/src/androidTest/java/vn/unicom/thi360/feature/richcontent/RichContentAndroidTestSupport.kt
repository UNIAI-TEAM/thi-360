package vn.unicom.thi360.feature.richcontent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.unicom.thi360.feature.richcontent.ui.FormulaFallbackSpan
import vn.unicom.thi360.feature.richcontent.ui.RaTeXSpan

@Serializable
data class AndroidFormulaCatalogueCounts(
    val groups: Int,
    val formulas: Int,
    val curriculumFormulas: Int,
    val syntaxFormulas: Int,
    val mhchemFormulas: Int,
    val blockOnly: Int,
    val rejected: Int,
)

@Serializable
data class AndroidFormulaCatalogueMeta(
    val path: String,
    val date: String,
    val counts: AndroidFormulaCatalogueCounts,
)

@Serializable
data class AndroidFormulaEntry(
    val latex: String,
    val display: String,
    val origin: String? = null,
    val group: String? = null,
    val kind: String? = null,
    val rejectedArea: String? = null,
    val katexRenders: Boolean,
    val sheetKey: String? = null,
) {
    fun sheetGroup(): String? = sheetKey?.substringBefore('/')?.takeIf { it.isNotBlank() }
    fun sheetItemNumber(): String? = sheetKey?.substringAfter('/')?.takeIf { it.isNotBlank() }
}

@Serializable
data class AndroidFormulaCorpusRoot(
    val katexVersion: String,
    val mhchem: Boolean,
    val catalogue: AndroidFormulaCatalogueMeta,
    val entries: List<AndroidFormulaEntry>,
)

@Serializable
data class AndroidFormulaExemption(
    val latex: String,
    val display: String,
    val app: String,
    val reason: String,
    val beyondCurriculum: Boolean,
    val origin: String,
    val group: String? = null,
    val kind: String? = null,
    val approvalRef: String = "",
)

@Serializable
data class AndroidFormulaExemptionsRoot(
    val version: Int = 1,
    val rows: List<AndroidFormulaExemption> = emptyList(),
)

@Serializable
data class AndroidFormulaCheckRow(
    val index: Int,
    val latex: String,
    val sheetKey: String? = null,
    val group: String? = null,
    val kind: String? = null,
    val form: String,
    val outcome: String,
)

@Serializable
data class AndroidRichTextSample(
    val name: String,
    val valid: Boolean,
    val html: String,
    val description: String = "",
)

@Serializable
data class AndroidRichTextSamplesRoot(
    val samples: List<AndroidRichTextSample>,
)

internal object RichContentAndroidTestSupport {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedCorpus: AndroidFormulaCorpusRoot? = null

    @Volatile
    private var cachedExemptions: AndroidFormulaExemptionsRoot? = null

    @Volatile
    private var cachedSamples: List<AndroidRichTextSample>? = null

    fun loadFormulaCorpus(): AndroidFormulaCorpusRoot {
        cachedCorpus?.let { return it }
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("rich-text/formulas.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return json.decodeFromString<AndroidFormulaCorpusRoot>(text).also { cachedCorpus = it }
    }

    fun loadFormulaExemptions(): AndroidFormulaExemptionsRoot {
        cachedExemptions?.let { return it }
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("rich-text/formula-exemptions.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return json.decodeFromString<AndroidFormulaExemptionsRoot>(text).also { cachedExemptions = it }
    }

    fun loadRichTextSamples(): List<AndroidRichTextSample> {
        cachedSamples?.let { return it }
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("rich-text/rich-text-samples.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return json.decodeFromString<AndroidRichTextSamplesRoot>(text).samples.also { cachedSamples = it }
    }

    fun loadWebRenderBitmap(sheetKey: String): Bitmap {
        return InstrumentationRegistry.getInstrumentation().context.assets
            .open("rich-text/web-renders/$sheetKey.png")
            .use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) {
                    "Failed to decode web-render PNG for $sheetKey"
                }
            }
    }

    fun outputDir(subpath: String): File {
        val base = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return File(base, "and-rich-outputs/$subpath").apply { mkdirs() }
    }

    fun mirrorToHostTmp(sourceFile: File, relativePath: String) {
        val targetPath = "/data/local/tmp/and-rich-outputs/$relativePath"
        val parentPath = targetPath.substringBeforeLast('/')
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("mkdir -p $parentPath").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
        val err = ui.executeShellCommand("cp ${sourceFile.absolutePath} $targetPath").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
        require(err.isBlank()) { "Failed to mirror ${sourceFile.absolutePath} -> $targetPath: $err" }
    }

    fun deviceTag(): String {
        val explicit = InstrumentationRegistry.getArguments().getString("formulaCheckDevice")
        if (!explicit.isNullOrBlank()) return explicit.trim()
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return "api${android.os.Build.VERSION.SDK_INT}-$abi"
    }

    fun collectTextViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(v: View) {
            if (v is TextView) out += v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        return out
    }

    fun collectViews(root: View, predicate: (View) -> Boolean): List<View> {
        val out = mutableListOf<View>()
        fun walk(v: View) {
            if (predicate(v)) out += v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        return out
    }

    fun raTeXSpans(tv: TextView): List<RaTeXSpan> {
        val spanned = tv.text as? Spanned ?: return emptyList()
        return spanned.getSpans(0, spanned.length, RaTeXSpan::class.java).toList()
    }

    fun fallbackSpans(tv: TextView): List<FormulaFallbackSpan> {
        val spanned = tv.text as? Spanned ?: return emptyList()
        return spanned.getSpans(0, spanned.length, FormulaFallbackSpan::class.java).toList()
    }

    fun captureViewBitmap(view: View, backgroundColor: Int = Color.WHITE): Bitmap {
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(backgroundColor)
        view.draw(canvas)
        return bmp
    }

    fun savePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun countDarkPixels(bitmap: Bitmap, thresholdLuminance: Int = 220): Int {
        var dark = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val alpha = Color.alpha(px)
                if (alpha < 32) continue
                val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                if (lum < thresholdLuminance) dark++
            }
        }
        return dark
    }
}
