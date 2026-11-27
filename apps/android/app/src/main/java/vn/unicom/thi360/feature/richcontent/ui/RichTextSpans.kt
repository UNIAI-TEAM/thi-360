package vn.unicom.thi360.feature.richcontent.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderResult
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.model.RichTextStyle

class RaTeXSpan(
    val rendered: FormulaRenderResult.Rendered,
    val latexSource: String,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val base = paint.fontMetricsInt
            fm.ascent = min(base.ascent, -rendered.ascent)
            fm.descent = max(base.descent, rendered.descent)
            fm.top = min(base.top, fm.ascent)
            fm.bottom = max(base.bottom, fm.descent)
        }
        return rendered.width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val save = canvas.save()
        canvas.translate(x, (y - rendered.ascent).toFloat())
        rendered.drawable.setBounds(0, 0, rendered.width, rendered.ascent + rendered.descent)
        rendered.drawable.draw(canvas)
        canvas.restoreToCount(save)
    }
}

class FormulaFallbackSpan(
    val source: String,
    val fgColor: Int,
    val bgColor: Int,
    val borderColor: Int,
    private val density: Float,
) : ReplacementSpan() {
    private fun configureMonoPaint(base: Paint): Paint = Paint(base).apply {
        typeface = Typeface.MONOSPACE
        textSize = base.textSize * 0.85f
        color = fgColor
    }

    private fun padX(): Float = 6f * density
    private fun padY(): Float = 2f * density

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val mono = configureMonoPaint(paint)
        if (fm != null) {
            val base = paint.fontMetricsInt
            val monoFm = mono.fontMetricsInt
            val vPad = ceil(padY().toDouble()).toInt()
            fm.ascent = min(base.ascent, monoFm.ascent - vPad)
            fm.descent = max(base.descent, monoFm.descent + vPad)
            fm.top = min(base.top, fm.ascent)
            fm.bottom = max(base.bottom, fm.descent)
        }
        return ceil((mono.measureText(source) + padX() * 2f).toDouble()).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val mono = configureMonoPaint(paint)
        val monoFm = mono.fontMetrics
        val width = mono.measureText(source) + padX() * 2f
        val rectTop = y + monoFm.ascent - padY()
        val rectBottom = y + monoFm.descent + padY()
        val rect = RectF(x, rectTop, x + width, rectBottom)
        val radius = 4f * density

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = max(1f, density)
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        canvas.drawText(source, x + padX(), y.toFloat(), mono)
    }
}

class BlankChipSpan(
    val chipText: String,
    val fgColor: Int,
    val bgColor: Int,
    val borderColor: Int,
    private val density: Float,
) : ReplacementSpan() {
    private fun configureChipPaint(base: Paint): Paint = Paint(base).apply {
        typeface = Typeface.create(base.typeface, Typeface.BOLD)
        textSize = base.textSize * 0.85f
        color = fgColor
    }

    private fun padX(): Float = 8f * density
    private fun padY(): Float = 2f * density

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val chipPaint = configureChipPaint(paint)
        if (fm != null) {
            val base = paint.fontMetricsInt
            val chipFm = chipPaint.fontMetricsInt
            val vPad = ceil(padY().toDouble()).toInt()
            fm.ascent = min(base.ascent, chipFm.ascent - vPad)
            fm.descent = max(base.descent, chipFm.descent + vPad)
            fm.top = min(base.top, fm.ascent)
            fm.bottom = max(base.bottom, fm.descent)
        }
        return ceil((chipPaint.measureText(chipText) + padX() * 2f).toDouble()).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val chipPaint = configureChipPaint(paint)
        val chipFm = chipPaint.fontMetrics
        val width = chipPaint.measureText(chipText) + padX() * 2f
        val rect = RectF(x, y + chipFm.ascent - padY(), x + width, y + chipFm.descent + padY())
        val radius = 6f * density

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val stroke = max(1f, density)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = stroke
            pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        canvas.drawText(chipText, x + padX(), y.toFloat(), chipPaint)
    }
}

class LabeledUnderlineSpan(
    val label: Char,
    val textColor: Int,
    val accentColor: Int,
    private val density: Float,
) : ReplacementSpan() {
    private fun configureLabelPaint(base: Paint): Paint = Paint(base).apply {
        typeface = Typeface.create(base.typeface, Typeface.BOLD)
        textSize = max(11f * density, base.textSize * 0.68f)
        color = accentColor
        textAlign = Paint.Align.CENTER
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val labelPaint = configureLabelPaint(paint)
        val segmentWidth = if (text != null && end > start) paint.measureText(text, start, end) else 0f
        val labelWidth = labelPaint.measureText(label.toString())
        val width = ceil(max(segmentWidth, labelWidth).toDouble()).toInt()
        if (fm != null) {
            val base = paint.fontMetricsInt
            val labelFm = labelPaint.fontMetricsInt
            val underlineGap = ceil((3f * density).toDouble()).toInt()
            val labelHeight = labelFm.descent - labelFm.ascent
            val extraBottom = underlineGap + labelHeight + ceil((2f * density).toDouble()).toInt()
            fm.ascent = base.ascent
            fm.top = base.top
            fm.descent = max(base.descent, base.descent + extraBottom)
            fm.bottom = max(base.bottom, fm.descent)
        }
        return width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        if (text == null || end <= start) return
        val segmentWidth = paint.measureText(text, start, end)
        val labelPaint = configureLabelPaint(paint)
        val labelStr = label.toString()
        val labelWidth = labelPaint.measureText(labelStr)
        val spanWidth = max(segmentWidth, labelWidth)

        val textPaint = Paint(paint).apply { color = textColor }
        val textX = x + (spanWidth - segmentWidth) / 2f
        canvas.drawText(text, start, end, textX, y.toFloat(), textPaint)

        val lineY = y + 2.5f * density
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = max(1.5f, 1.5f * density)
            style = Paint.Style.STROKE
        }
        canvas.drawLine(x, lineY, x + spanWidth, lineY, linePaint)

        val labelFm = labelPaint.fontMetrics
        val labelBaseline = lineY + 2f * density - labelFm.ascent
        canvas.drawText(labelStr, x + spanWidth / 2f, labelBaseline, labelPaint)
    }
}

class SlotPlaceholderSpan(
    val slotId: String,
    val widthPx: Int,
    val heightPx: Int,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val base = paint.fontMetricsInt
            val textMid = (base.ascent + base.descent) / 2
            val halfH = heightPx / 2
            val slotAscent = textMid - halfH
            val slotDescent = textMid + (heightPx - halfH)
            fm.ascent = min(base.ascent, slotAscent)
            fm.descent = max(base.descent, slotDescent)
            fm.top = min(base.top, fm.ascent)
            fm.bottom = max(base.bottom, fm.descent)
        }
        return widthPx
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) = Unit
}

internal sealed interface ParagraphSegment {
    data class TextRun(
        val inlines: List<RichInline>,
        val preRenderedFormulas: Map<Int, FormulaRenderResult>,
    ) : ParagraphSegment

    data class OverflowFormula(
        val formula: RichInline.Formula,
        val result: FormulaRenderResult,
    ) : ParagraphSegment

    data class InlineImage(
        val image: RichInline.Image,
    ) : ParagraphSegment
}

internal data class BuiltSpannable(
    val spannable: SpannableStringBuilder,
    val accessibleText: String,
    val slotRanges: List<SlotRange>,
    val hasFallback: Boolean,
)

internal data class SlotRange(
    val slotId: String,
    val start: Int,
    val end: Int,
    val widthPx: Int,
    val heightPx: Int,
)

internal object RichInlineLayoutPlanner {
    fun splitIntoSegments(
        inlines: List<RichInline>,
        availableWidthPx: Int,
        textSizePx: Float,
        palette: RichContentPalette,
        density: Float,
        formulaEngine: FormulaEngine,
    ): List<ParagraphSegment> {
        val segments = mutableListOf<ParagraphSegment>()
        val runInlines = mutableListOf<RichInline>()
        val runFormulas = mutableMapOf<Int, FormulaRenderResult>()
        val textColorArgb = palette.text.toArgb()

        var afterBlockBreak = false

        fun flushRun(trimTrailing: Boolean) {
            if (runInlines.isEmpty()) return
            val trimmed = runInlines.toMutableList()
            val trimmedFormulas = runFormulas.toMutableMap()
            if (afterBlockBreak) {
                while (trimmed.isNotEmpty() && trimmed.first() is RichInline.Text) {
                    val first = trimmed.first() as RichInline.Text
                    val stripped = first.text.trimStart()
                    if (stripped.isEmpty()) {
                        trimmed.removeAt(0)
                        val shifted = mutableMapOf<Int, FormulaRenderResult>()
                        trimmedFormulas.forEach { (k, v) -> if (k > 0) shifted[k - 1] = v }
                        trimmedFormulas.clear()
                        trimmedFormulas.putAll(shifted)
                    } else {
                        trimmed[0] = first.copy(text = stripped)
                        break
                    }
                }
            }
            if (trimTrailing) {
                while (trimmed.isNotEmpty() && trimmed.last() is RichInline.Text) {
                    val lastIdx = trimmed.lastIndex
                    val last = trimmed[lastIdx] as RichInline.Text
                    val stripped = last.text.trimEnd()
                    if (stripped.isEmpty()) {
                        trimmed.removeAt(lastIdx)
                        trimmedFormulas.remove(lastIdx)
                    } else {
                        trimmed[lastIdx] = last.copy(text = stripped)
                        break
                    }
                }
            }
            if (trimmed.isNotEmpty()) {
                segments += ParagraphSegment.TextRun(trimmed.toList(), trimmedFormulas.toMap())
            }
            runInlines.clear()
            runFormulas.clear()
        }

        for (inline in inlines) {
            when (inline) {
                is RichInline.Image -> {
                    flushRun(trimTrailing = true)
                    segments += ParagraphSegment.InlineImage(inline)
                    afterBlockBreak = true
                }
                is RichInline.Formula -> {
                    val result = formulaEngine.render(inline, textSizePx, textColorArgb)
                    val itemWidthPx = when (result) {
                        is FormulaRenderResult.Rendered -> result.width
                        is FormulaRenderResult.Unsupported -> estimateFallbackWidthPx(result.source, textSizePx, density)
                    }
                    if (availableWidthPx > 0 && itemWidthPx > availableWidthPx) {
                        flushRun(trimTrailing = true)
                        segments += ParagraphSegment.OverflowFormula(inline, result)
                        afterBlockBreak = true
                    } else {
                        val idx = runInlines.size
                        runInlines += inline
                        runFormulas[idx] = result
                    }
                }
                else -> runInlines += inline
            }
        }
        flushRun(trimTrailing = false)
        return segments
    }

    fun buildSpannable(
        inlines: List<RichInline>,
        preRenderedFormulas: Map<Int, FormulaRenderResult>,
        palette: RichContentPalette,
        density: Float,
        effectiveBlankStart: Int,
        slotSizes: Map<String, Pair<Int, Int>>,
        formulaEngine: FormulaEngine,
        textSizePx: Float,
        baseTextColorArgb: Int = palette.text.toArgb(),
    ): BuiltSpannable {
        val sb = SpannableStringBuilder()
        val accessible = StringBuilder()
        val slotRanges = mutableListOf<SlotRange>()
        var hasFallback = false

        fun appendInline(inline: RichInline, indexInParent: Int?) {
            when (inline) {
                is RichInline.Text -> {
                    val start = sb.length
                    sb.append(inline.text)
                    accessible.append(inline.text)
                    val end = sb.length
                    applyStyles(sb, start, end, inline.styles)
                }
                RichInline.LineBreak -> {
                    sb.append('\n')
                    accessible.append('\n')
                }
                is RichInline.Formula -> {
                    val result = (indexInParent?.let { preRenderedFormulas[it] })
                        ?: formulaEngine.render(inline, textSizePx, baseTextColorArgb)
                    val delimited = inline.delimitedSource()
                    val start = sb.length
                    sb.append(delimited)
                    accessible.append(delimited)
                    val end = sb.length
                    when (result) {
                        is FormulaRenderResult.Rendered -> {
                            sb.setSpan(RaTeXSpan(result, delimited), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        is FormulaRenderResult.Unsupported -> {
                            hasFallback = true
                            sb.setSpan(
                                FormulaFallbackSpan(
                                    source = result.source,
                                    fgColor = palette.warningFg.toArgb(),
                                    bgColor = palette.warningBg.toArgb(),
                                    borderColor = palette.warningFg.toArgb(),
                                    density = density,
                                ),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                }
                is RichInline.LabeledUnderline -> {
                    val start = sb.length
                    val plainText = flattenText(inline.children)
                    sb.append(plainText)
                    accessible.append(plainText).append(" (").append(inline.label).append(')')
                    val end = sb.length
                    if (end > start) {
                        sb.setSpan(
                            LabeledUnderlineSpan(
                                label = inline.label,
                                textColor = baseTextColorArgb,
                                accentColor = palette.primaryText.toArgb(),
                                density = density,
                            ),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
                is RichInline.Blank -> {
                    val displayNumber = effectiveBlankStart + inline.rawNumber - 1
                    val chipText = "($displayNumber) ____"
                    val start = sb.length
                    sb.append(chipText)
                    accessible.append(chipText)
                    val end = sb.length
                    sb.setSpan(
                        BlankChipSpan(
                            chipText = chipText,
                            fgColor = palette.blankFg.toArgb(),
                            bgColor = palette.blankBg.toArgb(),
                            borderColor = palette.blankFg.toArgb(),
                            density = density,
                        ),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                is RichInline.Slot -> {
                    val size = slotSizes[inline.id]
                    if (size != null) {
                        val start = sb.length
                        sb.append('\uFFFC')
                        val end = sb.length
                        sb.setSpan(
                            SlotPlaceholderSpan(inline.id, size.first, size.second),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        slotRanges += SlotRange(inline.id, start, end, size.first, size.second)
                    }
                }
                is RichInline.Image -> Unit
            }
        }

        inlines.forEachIndexed { idx, inline ->
            appendInline(inline, idx)
        }

        return BuiltSpannable(
            spannable = sb,
            accessibleText = accessible.toString(),
            slotRanges = slotRanges,
            hasFallback = hasFallback,
        )
    }

    private fun applyStyles(
        sb: SpannableStringBuilder,
        start: Int,
        end: Int,
        styles: Set<RichTextStyle>,
    ) {
        if (end <= start || styles.isEmpty()) return
        val bold = RichTextStyle.Bold in styles
        val italic = RichTextStyle.Italic in styles
        when {
            bold && italic -> sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bold -> sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            italic -> sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (RichTextStyle.Underline in styles) {
            sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (RichTextStyle.Strike in styles) {
            sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (RichTextStyle.Sub in styles) {
            sb.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (RichTextStyle.Sup in styles) {
            sb.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun flattenText(inlines: List<RichInline>): String = buildString {
        for (inline in inlines) {
            when (inline) {
                is RichInline.Text -> append(inline.text)
                RichInline.LineBreak -> append(' ')
                is RichInline.LabeledUnderline -> append(flattenText(inline.children))
                is RichInline.Formula -> append(inline.delimitedSource())
                is RichInline.Blank -> append("(${inline.number}) ____")
                is RichInline.Slot -> append("[[${inline.id}]]")
                is RichInline.Image -> append(inline.alt)
            }
        }
    }

    private fun estimateFallbackWidthPx(source: String, textSizePx: Float, density: Float): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizePx * 0.85f
        }
        return (paint.measureText(source) + 12f * density).roundToInt()
    }
}
