package vn.unicom.thi360.feature.richcontent.ui

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout as AndroidLayout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import vn.unicom.thi360.R
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderResult
import vn.unicom.thi360.feature.richcontent.model.FigureWidth
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.model.RichDocument
import vn.unicom.thi360.feature.richcontent.model.RichInline

private const val MAX_VISUAL_LIST_DEPTH = 6

@Composable
fun RichContentView(
    document: RichDocument,
    assets: AttemptAssetSource?,
    mode: RichContentMode = RichContentMode.Answering,
    modifier: Modifier = Modifier,
    slots: Map<String, @Composable () -> Unit> = emptyMap(),
    blankNumberStart: Int = 1,
    formulaEngine: FormulaEngine? = null,
    textSizeSp: Float = 16f,
) {
    val palette = RichContentPalette.current()
    val resolvedEngine = formulaEngine ?: rememberDefaultFormulaEngine()
    val effectiveBlankStart = if (blankNumberStart != 1) blankNumberStart else document.blankNumberStart

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(RichContentTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RichBlocksColumn(
            blocks = document.blocks,
            assets = assets,
            mode = mode,
            slots = slots,
            effectiveBlankStart = effectiveBlankStart,
            formulaEngine = resolvedEngine,
            palette = palette,
            textSizeSp = textSizeSp,
        )
    }
}

@Composable
internal fun RichBlocksColumn(
    blocks: List<RichBlock>,
    assets: AttemptAssetSource?,
    mode: RichContentMode,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    listDepth: Int = 0,
    forceBold: Boolean = false,
    alignEnd: Boolean = false,
    tabularNums: Boolean = false,
    insideTableCell: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (insideTableCell) 4.dp else 8.dp),
    ) {
        blocks.forEach { block ->
            RichBlockItem(
                block = block,
                assets = assets,
                mode = mode,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
                listDepth = listDepth,
                forceBold = forceBold,
                alignEnd = alignEnd,
                tabularNums = tabularNums,
                insideTableCell = insideTableCell,
            )
        }
    }
}

@Composable
private fun RichBlockItem(
    block: RichBlock,
    assets: AttemptAssetSource?,
    mode: RichContentMode,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    listDepth: Int,
    forceBold: Boolean,
    alignEnd: Boolean,
    tabularNums: Boolean,
    insideTableCell: Boolean,
) {
    when (block) {
        is RichBlock.Paragraph -> {
            RichParagraphView(
                inlines = block.inlines,
                assets = assets,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
                textColorArgb = palette.text.toArgb(),
                forceBold = forceBold,
                gravity = if (alignEnd) Gravity.END else Gravity.START,
                tabularNums = tabularNums,
                insideTableCell = insideTableCell,
            )
        }

        is RichBlock.FormulaBlock -> {
            val density = LocalDensity.current
            val textSizePx = textSizeSp * density.density * density.fontScale
            val result = remember(block.formula, textSizePx, palette.text, formulaEngine) {
                formulaEngine.render(block.formula, textSizePx, palette.text.toArgb())
            }
            RichFormulaBlockView(
                formula = block.formula,
                result = result,
                palette = palette,
                textSizeSp = textSizeSp,
                centered = true,
                insideTableCell = insideTableCell,
            )
        }

        is RichBlock.BulletList -> {
            RichListView(
                items = block.items,
                ordered = false,
                assets = assets,
                mode = mode,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
                listDepth = listDepth,
            )
        }

        is RichBlock.OrderedList -> {
            RichListView(
                items = block.items,
                ordered = true,
                assets = assets,
                mode = mode,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
                listDepth = listDepth,
            )
        }

        is RichBlock.Table -> {
            RichTableView(
                table = block.table,
                assets = assets,
                mode = mode,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
            )
        }

        is RichBlock.DataTableFigure -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag(RichContentTestTags.TABLE_FIGURE),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.caption?.let { caption ->
                    RichParagraphView(
                        inlines = caption,
                        assets = assets,
                        slots = emptyMap(),
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = 14f,
                        textColorArgb = palette.text.toArgb(),
                        forceBold = true,
                        gravity = Gravity.CENTER_HORIZONTAL,
                    )
                }
                RichTableView(
                    table = block.table,
                    assets = assets,
                    mode = mode,
                    slots = slots,
                    effectiveBlankStart = effectiveBlankStart,
                    formulaEngine = formulaEngine,
                    palette = palette,
                    textSizeSp = textSizeSp,
                )
                block.notes.forEach { note ->
                    RichParagraphView(
                        inlines = note,
                        assets = assets,
                        slots = emptyMap(),
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = 13f,
                        textColorArgb = palette.textMuted.toArgb(),
                    )
                }
                block.sources.forEach { source ->
                    RichParagraphView(
                        inlines = source,
                        assets = assets,
                        slots = emptyMap(),
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = 13f,
                        textColorArgb = palette.textMuted.toArgb(),
                    )
                }
            }
        }

        is RichBlock.CrossFigure -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag(RichContentTestTags.CROSS_FIGURE),
            ) {
                RichTableView(
                    table = block.table,
                    assets = assets,
                    mode = mode,
                    slots = slots,
                    effectiveBlankStart = effectiveBlankStart,
                    formulaEngine = formulaEngine,
                    palette = palette,
                    textSizeSp = textSizeSp,
                )
            }
        }

        is RichBlock.ImageFigure -> {
            val captionPlain = block.caption?.let { extractPlainText(it) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag(RichContentTestTags.IMAGE_FIGURE),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RichImageView(
                    image = block.image,
                    figureWidth = block.width,
                    zoomable = block.zoomable,
                    captionText = captionPlain,
                    assets = assets,
                    palette = palette,
                )
                block.caption?.let { caption ->
                    RichParagraphView(
                        inlines = caption,
                        assets = assets,
                        slots = emptyMap(),
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = 14f,
                        textColorArgb = palette.text.toArgb(),
                        forceBold = true,
                        gravity = Gravity.CENTER_HORIZONTAL,
                    )
                }
                block.sources.forEach { source ->
                    RichParagraphView(
                        inlines = source,
                        assets = assets,
                        slots = emptyMap(),
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = 13f,
                        textColorArgb = palette.textMuted.toArgb(),
                    )
                }
            }
        }

        is RichBlock.Quote -> {
            RichQuoteView(
                quote = block,
                assets = assets,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
            )
        }
    }
}

@Composable
private fun RichListView(
    items: List<List<RichBlock>>,
    ordered: Boolean,
    assets: AttemptAssetSource?,
    mode: RichContentMode,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    listDepth: Int,
) {
    val indentDp = if (listDepth > 0 && listDepth <= MAX_VISUAL_LIST_DEPTH) 16.dp else 0.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { index, itemBlocks ->
            val marker = if (ordered) {
                "${index + 1}."
            } else {
                when (listDepth % 3) {
                    0 -> "•"
                    1 -> "◦"
                    else -> "▪"
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = marker,
                    color = palette.text,
                    fontSize = textSizeSp.sp,
                    modifier = Modifier.width(if (ordered) 28.dp else 20.dp),
                    textAlign = TextAlign.End,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    RichBlocksColumn(
                        blocks = itemBlocks,
                        assets = assets,
                        mode = mode,
                        slots = slots,
                        effectiveBlankStart = effectiveBlankStart,
                        formulaEngine = formulaEngine,
                        palette = palette,
                        textSizeSp = textSizeSp,
                        listDepth = listDepth + 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichQuoteView(
    quote: RichBlock.Quote,
    assets: AttemptAssetSource?,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
) {
    val quoteTextSizeSp = textSizeSp * 0.94f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.quoteBackground)
            .padding(16.dp)
            .testTag(RichContentTestTags.QUOTE_BLOCK),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (quote.lineNumberStep > 0) {
            var lineNumber = 0
            quote.paragraphs.forEach { stanza ->
                val lines = splitLinesOnBreak(stanza)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { lineInlines ->
                        lineNumber += 1
                        val numberLabel = if (lineNumber % quote.lineNumberStep == 0) lineNumber.toString() else ""
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = numberLabel,
                                color = palette.textMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .width(28.dp)
                                    .padding(top = 2.dp)
                                    .testTag("${RichContentTestTags.QUOTE_LINE_NUMBER}_$lineNumber"),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                RichParagraphView(
                                    inlines = lineInlines,
                                    assets = assets,
                                    slots = slots,
                                    effectiveBlankStart = effectiveBlankStart,
                                    formulaEngine = formulaEngine,
                                    palette = palette,
                                    textSizeSp = quoteTextSizeSp,
                                    textColorArgb = palette.text.toArgb(),
                                    lineSpacingMultiplier = 1.35f,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            quote.paragraphs.forEach { paragraph ->
                RichParagraphView(
                    inlines = paragraph,
                    assets = assets,
                    slots = slots,
                    effectiveBlankStart = effectiveBlankStart,
                    formulaEngine = formulaEngine,
                    palette = palette,
                    textSizeSp = quoteTextSizeSp,
                    textColorArgb = palette.text.toArgb(),
                    lineSpacingMultiplier = 1.35f,
                )
            }
        }

        quote.footer?.let { footerInlines ->
            RichParagraphView(
                inlines = footerInlines,
                assets = assets,
                slots = emptyMap(),
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = 14f,
                textColorArgb = palette.textSecondary.toArgb(),
                gravity = Gravity.END,
            )
        }

        quote.source?.let { sourceInlines ->
            RichParagraphView(
                inlines = sourceInlines,
                assets = assets,
                slots = emptyMap(),
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = 13f,
                textColorArgb = palette.textMuted.toArgb(),
            )
        }
    }
}

@Composable
internal fun RichFormulaBlockView(
    formula: RichInline.Formula,
    result: FormulaRenderResult,
    palette: RichContentPalette,
    textSizeSp: Float,
    centered: Boolean,
    insideTableCell: Boolean = false,
) {
    val delimited = formula.delimitedSource()
    val scrollState = rememberScrollState()
    val scrollDesc = stringResource(R.string.richcontent_formula_scroll_region)
    val baseModifier = (if (insideTableCell) Modifier else Modifier.fillMaxWidth())
        .semantics {
            contentDescription = delimited
        }
        .testTag(
            if (result is FormulaRenderResult.Unsupported) {
                RichContentTestTags.FORMULA_FALLBACK
            } else {
                RichContentTestTags.FORMULA_BLOCK
            },
        )

    if (insideTableCell) {
        Box(
            modifier = baseModifier.padding(vertical = 2.dp),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
        ) {
            when (result) {
                is FormulaRenderResult.Rendered -> {
                    AndroidView(
                        factory = { ctx -> FormulaDrawableView(ctx) },
                        update = { view ->
                            view.updateRendered(result)
                            view.contentDescription = delimited
                        },
                    )
                }

                is FormulaRenderResult.Unsupported -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.warningBg)
                            .border(1.dp, palette.warningFg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = result.source,
                            color = palette.warningFg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (textSizeSp * 0.85f).sp,
                        )
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = baseModifier) {
        val availableWidthPx = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: Int.MAX_VALUE
        when (result) {
            is FormulaRenderResult.Rendered -> {
                val overflows = result.width > availableWidthPx
                val alignment = if (centered && !overflows) Alignment.Center else Alignment.CenterStart
                val scrollModifier = if (overflows) {
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "$delimited, $scrollDesc" }
                        .testTag(RichContentTestTags.FORMULA_SCROLL)
                        .nestedScroll(ConsumeHorizontalNestedScroll)
                        .horizontalScroll(scrollState)
                } else {
                    Modifier.fillMaxWidth()
                }
                Box(
                    modifier = scrollModifier.padding(vertical = 4.dp),
                    contentAlignment = alignment,
                ) {
                    AndroidView(
                        factory = { ctx -> FormulaDrawableView(ctx) },
                        update = { view ->
                            view.updateRendered(result)
                            view.contentDescription = delimited
                        },
                    )
                }
            }

            is FormulaRenderResult.Unsupported -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(ConsumeHorizontalNestedScroll)
                        .horizontalScroll(scrollState)
                        .padding(vertical = 2.dp),
                    contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.warningBg)
                            .border(1.dp, palette.warningFg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = result.source,
                            color = palette.warningFg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (textSizeSp * 0.85f).sp,
                        )
                    }
                }
            }
        }
    }
}

private class FormulaDrawableView(context: android.content.Context) : View(context) {
    private var rendered: FormulaRenderResult.Rendered? = null

    fun updateRendered(value: FormulaRenderResult.Rendered) {
        rendered = value
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val r = rendered
        if (r == null) {
            setMeasuredDimension(0, 0)
            return
        }
        val w = max(1, r.width)
        val h = max(1, r.ascent + r.descent)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: AndroidCanvas) {
        val r = rendered ?: return
        r.drawable.setBounds(0, 0, r.width, r.ascent + r.descent)
        r.drawable.draw(canvas)
    }
}

@Composable
internal fun RichParagraphView(
    inlines: List<RichInline>,
    assets: AttemptAssetSource?,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    textColorArgb: Int,
    forceBold: Boolean = false,
    gravity: Int = Gravity.START,
    tabularNums: Boolean = false,
    lineSpacingMultiplier: Float = 1.2f,
    insideTableCell: Boolean = false,
) {
    val density = LocalDensity.current
    val textSizePx = textSizeSp * density.density * density.fontScale

    if (insideTableCell) {
        RichParagraphSegments(
            inlines = inlines,
            availableWidthPx = 0,
            assets = assets,
            slots = slots,
            effectiveBlankStart = effectiveBlankStart,
            formulaEngine = formulaEngine,
            palette = palette,
            textSizeSp = textSizeSp,
            textSizePx = textSizePx,
            textColorArgb = textColorArgb,
            forceBold = forceBold,
            gravity = gravity,
            tabularNums = tabularNums,
            lineSpacingMultiplier = lineSpacingMultiplier,
            insideTableCell = true,
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            RichParagraphSegments(
                inlines = inlines,
                availableWidthPx = availableWidthPx,
                assets = assets,
                slots = slots,
                effectiveBlankStart = effectiveBlankStart,
                formulaEngine = formulaEngine,
                palette = palette,
                textSizeSp = textSizeSp,
                textSizePx = textSizePx,
                textColorArgb = textColorArgb,
                forceBold = forceBold,
                gravity = gravity,
                tabularNums = tabularNums,
                lineSpacingMultiplier = lineSpacingMultiplier,
                insideTableCell = false,
            )
        }
    }
}

@Composable
private fun RichParagraphSegments(
    inlines: List<RichInline>,
    availableWidthPx: Int,
    assets: AttemptAssetSource?,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    textSizePx: Float,
    textColorArgb: Int,
    forceBold: Boolean,
    gravity: Int,
    tabularNums: Boolean,
    lineSpacingMultiplier: Float,
    insideTableCell: Boolean,
) {
    val density = LocalDensity.current
    val segments = remember(inlines, availableWidthPx, textSizePx, palette, formulaEngine) {
        RichInlineLayoutPlanner.splitIntoSegments(
            inlines = inlines,
            availableWidthPx = availableWidthPx,
            textSizePx = textSizePx,
            palette = palette,
            density = density.density,
            formulaEngine = formulaEngine,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ParagraphSegment.OverflowFormula -> {
                    RichFormulaBlockView(
                        formula = segment.formula,
                        result = segment.result,
                        palette = palette,
                        textSizeSp = textSizeSp,
                        centered = false,
                        insideTableCell = insideTableCell,
                    )
                }

                is ParagraphSegment.InlineImage -> {
                    RichImageView(
                        image = segment.image,
                        figureWidth = FigureWidth.Full,
                        zoomable = false,
                        captionText = null,
                        assets = assets,
                        palette = palette,
                    )
                }

                is ParagraphSegment.TextRun -> {
                    val activeSlots = segment.inlines
                        .filterIsInstance<RichInline.Slot>()
                        .map { it.id }
                        .filter { it in slots }
                        .distinct()

                    if (activeSlots.isEmpty()) {
                        val built = remember(
                            segment,
                            palette,
                            density.density,
                            effectiveBlankStart,
                            formulaEngine,
                            textSizePx,
                            textColorArgb,
                        ) {
                            RichInlineLayoutPlanner.buildSpannable(
                                inlines = segment.inlines,
                                preRenderedFormulas = segment.preRenderedFormulas,
                                palette = palette,
                                density = density.density,
                                effectiveBlankStart = effectiveBlankStart,
                                slotSizes = emptyMap(),
                                formulaEngine = formulaEngine,
                                textSizePx = textSizePx,
                                baseTextColorArgb = textColorArgb,
                            )
                        }
                        RichNativeTextView(
                            built = built,
                            textSizePx = textSizePx,
                            textColorArgb = textColorArgb,
                            forceBold = forceBold,
                            gravity = gravity,
                            tabularNums = tabularNums,
                            lineSpacingMultiplier = lineSpacingMultiplier,
                            fillMaxWidth = true,
                        )
                    } else {
                        RichParagraphWithSlots(
                            segment = segment,
                            activeSlotIds = activeSlots,
                            slots = slots,
                            palette = palette,
                            density = density.density,
                            effectiveBlankStart = effectiveBlankStart,
                            formulaEngine = formulaEngine,
                            textSizePx = textSizePx,
                            textColorArgb = textColorArgb,
                            forceBold = forceBold,
                            gravity = gravity,
                            tabularNums = tabularNums,
                            lineSpacingMultiplier = lineSpacingMultiplier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichNativeTextView(
    built: BuiltSpannable,
    textSizePx: Float,
    textColorArgb: Int,
    forceBold: Boolean,
    gravity: Int,
    tabularNums: Boolean,
    lineSpacingMultiplier: Float,
    fillMaxWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { contentDescription = built.accessibleText }
            .testTag(RichContentTestTags.PARAGRAPH),
        factory = { ctx ->
            TextView(ctx).apply {
                isClickable = false
                isLongClickable = false
                isFocusable = false
                linksClickable = false
                autoLinkMask = 0
                includeFontPadding = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
                }
                hyphenationFrequency = AndroidLayout.HYPHENATION_FREQUENCY_NONE
            }
        },
        update = { tv ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            tv.setTextColor(textColorArgb)
            tv.typeface = if (forceBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.gravity = gravity
            tv.fontFeatureSettings = if (tabularNums) "tnum" else null
            tv.setLineSpacing(0f, lineSpacingMultiplier)
            tv.text = built.spannable
            tv.contentDescription = built.accessibleText
        },
    )
}

@Composable
private fun RichParagraphWithSlots(
    segment: ParagraphSegment.TextRun,
    activeSlotIds: List<String>,
    slots: Map<String, @Composable () -> Unit>,
    palette: RichContentPalette,
    density: Float,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    textSizePx: Float,
    textColorArgb: Int,
    forceBold: Boolean,
    gravity: Int,
    tabularNums: Boolean,
    lineSpacingMultiplier: Float,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RichContentTestTags.SLOT_HOST),
    ) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val slotPlaceables = linkedMapOf<String, androidx.compose.ui.layout.Placeable>()
        val slotSizes = linkedMapOf<String, Pair<Int, Int>>()

        activeSlotIds.forEach { slotId ->
            val composable = slots[slotId] ?: return@forEach
            val measurables = subcompose("slot_$slotId", composable)
            if (measurables.isNotEmpty()) {
                val placeable = measurables.first().measure(looseConstraints)
                slotPlaceables[slotId] = placeable
                slotSizes[slotId] = placeable.width to placeable.height
            }
        }

        val built = RichInlineLayoutPlanner.buildSpannable(
            inlines = segment.inlines,
            preRenderedFormulas = segment.preRenderedFormulas,
            palette = palette,
            density = density,
            effectiveBlankStart = effectiveBlankStart,
            slotSizes = slotSizes,
            formulaEngine = formulaEngine,
            textSizePx = textSizePx,
            baseTextColorArgb = textColorArgb,
        )

        val textMeasurable = subcompose("text_${built.spannable.length}_${slotSizes.hashCode()}") {
            RichNativeTextView(
                built = built,
                textSizePx = textSizePx,
                textColorArgb = textColorArgb,
                forceBold = forceBold,
                gravity = gravity,
                tabularNums = tabularNums,
                lineSpacingMultiplier = lineSpacingMultiplier,
                fillMaxWidth = true,
            )
        }.first()
        val textPlaceable = textMeasurable.measure(constraints)

        val layoutWidth = max(1, textPlaceable.width)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = if (forceBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            fontFeatureSettings = if (tabularNums) "tnum" else null
        }
        val staticLayout = StaticLayout.Builder
            .obtain(built.spannable, 0, built.spannable.length, textPaint, layoutWidth)
            .setIncludePad(true)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(AndroidLayout.HYPHENATION_FREQUENCY_NONE)
            .build()

        val placedOffsets = built.slotRanges.map { range ->
            val line = staticLayout.getLineForOffset(range.start)
            val x = staticLayout.getPrimaryHorizontal(range.start).roundToInt()
                .coerceIn(0, max(0, layoutWidth - range.widthPx))
            val lineTop = staticLayout.getLineTop(line)
            val lineBottom = staticLayout.getLineBottom(line)
            val y = (lineTop + ((lineBottom - lineTop - range.heightPx) / 2)).coerceAtLeast(0)
            Triple(range.slotId, x, y)
        }

        val totalHeight = max(
            textPlaceable.height,
            placedOffsets.maxOfOrNull { (id, _, y) -> y + (slotPlaceables[id]?.height ?: 0) } ?: 0,
        )

        layout(textPlaceable.width, totalHeight) {
            textPlaceable.placeRelative(0, 0)
            placedOffsets.forEach { (slotId, x, y) ->
                slotPlaceables[slotId]?.placeRelative(x, y)
            }
        }
    }
}

private fun splitLinesOnBreak(inlines: List<RichInline>): List<List<RichInline>> {
    val lines = mutableListOf(mutableListOf<RichInline>())
    for (inline in inlines) {
        if (inline is RichInline.LineBreak) {
            lines += mutableListOf<RichInline>()
        } else {
            lines.last() += inline
        }
    }
    return lines
}

private fun extractPlainText(inlines: List<RichInline>): String = buildString {
    for (inline in inlines) {
        when (inline) {
            is RichInline.Text -> append(inline.text)
            RichInline.LineBreak -> append(' ')
            is RichInline.Formula -> append(inline.delimitedSource())
            is RichInline.LabeledUnderline -> append(extractPlainText(inline.children))
            is RichInline.Blank -> append("(${inline.number}) ____")
            is RichInline.Slot -> append("[[${inline.id}]]")
            is RichInline.Image -> append(inline.alt)
        }
    }
}
