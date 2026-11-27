package vn.unicom.thi360.feature.richcontent.formula

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.LruCache
import io.ratex.DisplayItem
import io.ratex.DisplayList
import io.ratex.RaTeXEngine
import io.ratex.RaTeXFontLoader
import io.ratex.RaTeXRenderer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.RichInline

sealed interface FormulaRenderResult {
    data class Rendered(
        val drawable: Drawable,
        val width: Int,
        val ascent: Int,
        val descent: Int,
    ) : FormulaRenderResult

    data class Unsupported(val source: String) : FormulaRenderResult
}

internal sealed interface DetailedFormulaOutcome {
    data class Rendered(val value: FormulaRenderResult.Rendered) : DetailedFormulaOutcome
    data class Fallback(val source: String, val detail: String) : DetailedFormulaOutcome
    data class Timeout(val source: String, val detail: String) : DetailedFormulaOutcome
    data class Crash(val source: String, val detail: String) : DetailedFormulaOutcome
}

interface FormulaEngine {
    fun render(
        formula: RichInline.Formula,
        textSizePx: Float,
        color: Int,
    ): FormulaRenderResult
}

object FormulaRenderDefaults {
    const val RENDER_TIMEOUT_MS: Long = 5_000L
}

private object FormulaRenderExecutor {
    private val counter = AtomicInteger(1)
    val executor = Executors.newCachedThreadPool(
        ThreadFactory { runnable ->
            Thread(runnable, "thi360-formula-${counter.getAndIncrement()}").apply {
                isDaemon = true
            }
        },
    )
}

class GuardedFormulaEngine(
    private val delegate: FormulaEngine,
    private val timeoutMs: Long = FormulaRenderDefaults.RENDER_TIMEOUT_MS,
) : FormulaEngine {
    override fun render(
        formula: RichInline.Formula,
        textSizePx: Float,
        color: Int,
    ): FormulaRenderResult {
        val future = try {
            FormulaRenderExecutor.executor.submit<FormulaRenderResult> {
                delegate.render(formula, textSizePx, color)
            }
        } catch (_: Throwable) {
            return FormulaRenderResult.Unsupported(formula.delimitedSource())
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            FormulaRenderResult.Unsupported(formula.delimitedSource())
        } catch (_: ExecutionException) {
            FormulaRenderResult.Unsupported(formula.delimitedSource())
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            FormulaRenderResult.Unsupported(formula.delimitedSource())
        } catch (_: Throwable) {
            future.cancel(true)
            FormulaRenderResult.Unsupported(formula.delimitedSource())
        }
    }
}

class RaTeXDrawable(
    internal val renderer: RaTeXRenderer,
    private val intrinsicW: Int,
    private val ascentPx: Int,
    private val descentPx: Int,
) : Drawable() {
    init {
        setBounds(0, 0, intrinsicW, ascentPx + descentPx)
    }

    override fun getIntrinsicWidth(): Int = intrinsicW

    override fun getIntrinsicHeight(): Int = ascentPx + descentPx

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        // Shift drawing by (ascentPx - renderer.heightPx) so the formula's baseline aligns exactly at y = ascentPx.
        val baselineDelta = ascentPx.toFloat() - renderer.heightPx
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat() + baselineDelta)
        renderer.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Android Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal object KatexGrammarGuard {
    private val HFILL_REGEX = Regex("""\\hfill(?![A-Za-z])""")
    private val TAG_REGEX = Regex("""\\tag\*?(?![A-Za-z])""")
    private val DISPLAY_ONLY_ENV_REGEX = Regex(
        """\\begin\{(align|align\*|equation|equation\*|gather|gather\*|alignat|alignat\*)\}""",
    )
    private val LIMITS_REGEX = Regex("""\\(?:limits|nolimits)(?![A-Za-z])""")

    private val MATH_OPERATORS = setOf(
        "sum", "prod", "coprod", "int", "iint", "iiint", "oint", "oiint", "oiiint", "smallint",
        "lim", "limsup", "liminf", "max", "min", "sup", "inf", "det", "gcd", "Pr", "arg", "deg",
        "dim", "hom", "ker", "lg", "ln", "log", "sin", "cos", "tan", "csc", "sec", "cot", "sinh",
        "cosh", "tanh", "coth", "arcsin", "arccos", "arctan", "exp", "injlim", "projlim",
        "varliminf", "varlimsup", "varinjlim", "varprojlim", "bigcup", "bigcap", "bigvee",
        "bigwedge", "bigoplus", "bigotimes", "bigodot", "bigsqcup", "biguplus", "mathop",
        "operatorname", "overset", "underset", "stackrel",
    )

    fun rejectReason(latex: String, display: FormulaDisplay): String? {
        if (hasTopLevelUnescapedDollar(latex)) {
            return "Can't use unescaped '$' in math mode"
        }
        if (HFILL_REGEX.containsMatchIn(latex)) {
            return "Unsupported control sequence \\hfill"
        }
        if (display == FormulaDisplay.Inline) {
            if (TAG_REGEX.containsMatchIn(latex)) {
                return "\\tag is only allowed in display mode"
            }
            if (DISPLAY_ONLY_ENV_REGEX.containsMatchIn(latex)) {
                return "Display-only environment is not allowed in inline mode"
            }
        }
        if (hasInvalidLimitsControl(latex)) {
            return "Limit controls must follow a math operator"
        }
        return null
    }

    private fun hasTopLevelUnescapedDollar(s: String): Boolean {
        var depth = 0
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    if (depth > 0) depth--
                    i++
                }
                '$' -> {
                    if (depth == 0) return true
                    i++
                }
                else -> i++
            }
        }
        return false
    }

    private fun hasInvalidLimitsControl(s: String): Boolean {
        for (match in LIMITS_REGEX.findAll(s)) {
            var end = match.range.first - 1
            while (end >= 0 && s[end].isWhitespace()) end--
            if (end < 0) return true
            while (end >= 0 && s[end] == '}') {
                var depth = 1
                end--
                while (end >= 0 && depth > 0) {
                    val escaped = end > 0 && s[end - 1] == '\\'
                    if (!escaped) {
                        if (s[end] == '}') depth++
                        else if (s[end] == '{') depth--
                    }
                    end--
                }
                while (end >= 0 && s[end].isWhitespace()) end--
                if (end >= 0 && s[end] == '*') end--
            }
            var start = end
            while (start >= 0 && s[start].isLetter()) start--
            if (start >= 0 && s[start] == '\\') {
                val cmd = s.substring(start + 1, end + 1)
                if (cmd in MATH_OPERATORS) continue
            }
            return true
        }
        return false
    }
}

internal object VietnameseGlyphShield {
    private const val VIET_MIN = 0x1EA0
    private const val VIET_MAX = 0x1EF9
    private const val PUA_BASE = 0xE100

    fun shield(latex: String): Pair<String, Boolean> {
        var changed = false
        val sb = StringBuilder(latex.length)
        for (ch in latex) {
            val code = ch.code
            if (code in VIET_MIN..VIET_MAX) {
                sb.append((PUA_BASE + (code - VIET_MIN)).toChar())
                changed = true
            } else {
                sb.append(ch)
            }
        }
        return (if (changed) sb.toString() else latex) to changed
    }

    fun restore(displayList: DisplayList): DisplayList {
        val maxPua = PUA_BASE + (VIET_MAX - VIET_MIN)
        var changed = false
        val restored = displayList.items.map { item ->
            if (item is DisplayItem.GlyphPath && item.charCode in PUA_BASE..maxPua) {
                changed = true
                item.copy(charCode = VIET_MIN + (item.charCode - PUA_BASE))
            } else {
                item
            }
        }
        return if (changed) displayList.copy(items = restored) else displayList
    }
}

class RaTeXFormulaEngine(
    context: Context,
    private val timeoutMs: Long = FormulaRenderDefaults.RENDER_TIMEOUT_MS,
    maxCacheEntries: Int = 512,
) : FormulaEngine {
    private val appContext = context.applicationContext

    @Volatile
    private var nativeWarmedUp: Boolean = false

    private fun ensureNativeInitialized() {
        if (nativeWarmedUp) return
        synchronized(NativeInitLock) {
            if (nativeWarmedUp) return
            RaTeXFontLoader.ensureLoaded(appContext)
            runCatching { RaTeXEngine.parseBlocking("x", false, android.graphics.Color.BLACK) }
            nativeWarmedUp = true
        }
    }

    private companion object {
        private val NativeInitLock = Any()
    }

    private data class LayoutKey(
        val source: String,
        val display: FormulaDisplay,
        val color: Int,
    )

    private sealed interface CachedLayout {
        data class Ok(val displayList: DisplayList) : CachedLayout
        data class Fallback(val detail: String) : CachedLayout
        data class Timeout(val detail: String) : CachedLayout
        data class Crash(val detail: String) : CachedLayout
    }

    private val layoutCache = LruCache<LayoutKey, CachedLayout>(maxCacheEntries)

    override fun render(
        formula: RichInline.Formula,
        textSizePx: Float,
        color: Int,
    ): FormulaRenderResult = when (val detailed = renderDetailed(formula, textSizePx, color)) {
        is DetailedFormulaOutcome.Rendered -> detailed.value
        is DetailedFormulaOutcome.Fallback -> FormulaRenderResult.Unsupported(detailed.source)
        is DetailedFormulaOutcome.Timeout -> FormulaRenderResult.Unsupported(detailed.source)
        is DetailedFormulaOutcome.Crash -> FormulaRenderResult.Unsupported(detailed.source)
    }

    internal fun renderDetailed(
        formula: RichInline.Formula,
        textSizePx: Float,
        color: Int,
    ): DetailedFormulaOutcome {
        val delimited = formula.delimitedSource()
        if (formula.source.isBlank() || textSizePx <= 0f || !textSizePx.isFinite()) {
            return DetailedFormulaOutcome.Fallback(delimited, "Blank formula or invalid textSizePx")
        }
        KatexGrammarGuard.rejectReason(formula.source, formula.display)?.let { reason ->
            return DetailedFormulaOutcome.Fallback(delimited, reason)
        }

        val key = LayoutKey(formula.source, formula.display, color)
        val cached = layoutCache.get(key)
        val displayList = when (cached) {
            is CachedLayout.Ok -> cached.displayList
            is CachedLayout.Fallback -> return DetailedFormulaOutcome.Fallback(delimited, cached.detail)
            is CachedLayout.Timeout -> return DetailedFormulaOutcome.Timeout(delimited, cached.detail)
            is CachedLayout.Crash -> return DetailedFormulaOutcome.Crash(delimited, cached.detail)
            null -> {
                val parsed = parseWithTimeout(formula, color)
                layoutCache.put(key, parsed)
                when (parsed) {
                    is CachedLayout.Ok -> parsed.displayList
                    is CachedLayout.Fallback -> return DetailedFormulaOutcome.Fallback(delimited, parsed.detail)
                    is CachedLayout.Timeout -> return DetailedFormulaOutcome.Timeout(delimited, parsed.detail)
                    is CachedLayout.Crash -> return DetailedFormulaOutcome.Crash(delimited, parsed.detail)
                }
            }
        }

        return try {
            RaTeXFontLoader.ensureLoaded(appContext)
            val renderer = RaTeXRenderer(
                displayList = displayList,
                fontSize = textSizePx,
                typefaceLoader = { fontName -> RaTeXFontLoader.getTypeface(fontName) },
            )
            val rawWidth = renderer.widthPx
            val rawHeight = renderer.heightPx
            val rawDepth = renderer.depthPx
            if (!rawWidth.isFinite() || !rawHeight.isFinite() || !rawDepth.isFinite() || rawWidth < 0f) {
                return DetailedFormulaOutcome.Fallback(delimited, "Non-finite RaTeX layout dimensions")
            }
            val width = ceil(rawWidth.toDouble()).toInt().coerceAtLeast(1)
            val ascent = ceil(rawHeight.toDouble()).toInt().coerceAtLeast(1)
            val descent = ceil(rawDepth.toDouble()).toInt().coerceAtLeast(0)
            val drawable = RaTeXDrawable(
                renderer = renderer,
                intrinsicW = width,
                ascentPx = ascent,
                descentPx = descent,
            )
            DetailedFormulaOutcome.Rendered(
                FormulaRenderResult.Rendered(
                    drawable = drawable,
                    width = width,
                    ascent = ascent,
                    descent = descent,
                ),
            )
        } catch (t: Throwable) {
            DetailedFormulaOutcome.Crash(delimited, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun parseWithTimeout(formula: RichInline.Formula, color: Int): CachedLayout {
        ensureNativeInitialized()
        val displayMode = formula.display == FormulaDisplay.Block
        val (shieldedLatex, wasShielded) = VietnameseGlyphShield.shield(formula.source)
        val future = try {
            FormulaRenderExecutor.executor.submit<DisplayList> {
                val raw = RaTeXEngine.parseBlocking(
                    latex = shieldedLatex,
                    displayMode = displayMode,
                    color = color,
                )
                if (wasShielded) VietnameseGlyphShield.restore(raw) else raw
            }
        } catch (t: Throwable) {
            return CachedLayout.Crash("Executor submit failed: ${t.message}")
        }
        return try {
            CachedLayout.Ok(future.get(timeoutMs, TimeUnit.MILLISECONDS))
        } catch (_: TimeoutException) {
            future.cancel(true)
            CachedLayout.Timeout("Timed out after ${timeoutMs}ms")
        } catch (exec: ExecutionException) {
            val cause = exec.cause ?: exec
            if (cause.javaClass.name.startsWith("io.ratex.")) {
                CachedLayout.Fallback(cause.message ?: cause.javaClass.simpleName)
            } else {
                CachedLayout.Crash("${cause.javaClass.simpleName}: ${cause.message}")
            }
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            CachedLayout.Fallback("Interrupted during formula parse")
        } catch (t: Throwable) {
            future.cancel(true)
            CachedLayout.Crash("${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

class SourceBoxDrawable(
    val source: String,
    val color: Int,
    private val textSizePx: Float,
    private val widthPx: Int,
    private val ascentPx: Int,
    private val descentPx: Int,
) : Drawable() {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@SourceBoxDrawable.color
        textSize = textSizePx * 0.85f
        typeface = Typeface.MONOSPACE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@SourceBoxDrawable.color
        alpha = 72
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    init {
        setBounds(0, 0, widthPx, ascentPx + descentPx)
    }

    override fun getIntrinsicWidth(): Int = widthPx

    override fun getIntrinsicHeight(): Int = ascentPx + descentPx

    override fun draw(canvas: Canvas) {
        val rect = RectF(
            bounds.left.toFloat() + 0.5f,
            bounds.top.toFloat() + 0.5f,
            bounds.right.toFloat() - 0.5f,
            bounds.bottom.toFloat() - 0.5f,
        )
        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
        val padX = textSizePx * 0.25f
        canvas.drawText(source, bounds.left + padX, bounds.top + ascentPx.toFloat(), textPaint)
    }

    override fun setAlpha(alpha: Int) {
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * JVM/Robolectric placeholder FormulaEngine (B1.6) that never loads RaTeX native libraries.
 * Used by JVM tests and downstream screen snapshot tests (AND-Q, AND-PREP, AND-PLAYER, AND-RESULT).
 */
class SourceBoxFormulaEngine(
    private val unsupportedPredicate: (RichInline.Formula) -> Boolean = { false },
) : FormulaEngine {
    override fun render(
        formula: RichInline.Formula,
        textSizePx: Float,
        color: Int,
    ): FormulaRenderResult {
        if (formula.source.isBlank() || unsupportedPredicate(formula)) {
            return FormulaRenderResult.Unsupported(formula.delimitedSource())
        }
        val label = formula.source.trim()
        val effectiveSize = textSizePx.coerceAtLeast(8f)
        val charWidth = effectiveSize * 0.52f
        val padX = effectiveSize * 0.5f
        val width = max(
            ceil(effectiveSize * 1.2).toInt(),
            ceil(label.length * charWidth + padX).toInt(),
        )
        val isTall = label.contains("\\frac") || label.contains("\\int") || label.contains("\\sum")
        val ascent = (effectiveSize * if (isTall) 1.25f else 0.90f).roundToInt().coerceAtLeast(1)
        val descent = (effectiveSize * if (isTall) 0.55f else 0.25f).roundToInt().coerceAtLeast(1)
        val drawable = SourceBoxDrawable(
            source = label,
            color = color,
            textSizePx = effectiveSize,
            widthPx = width,
            ascentPx = ascent,
            descentPx = descent,
        )
        return FormulaRenderResult.Rendered(
            drawable = drawable,
            width = width,
            ascent = ascent,
            descent = descent,
        )
    }
}
