package vn.unicom.thi360.feature.question.logic

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

enum class EssayListKind {
    BULLET,
    ORDERED,
}

class EssayListItemSpan(
    val listStack: List<EssayListKind>,
    val ordinal: Int = 1,
    private val indentPx: Int = 48,
) : LeadingMarginSpan {
    val kind: EssayListKind
        get() = listStack.last()

    val depth: Int
        get() = listStack.size

    override fun getLeadingMargin(first: Boolean): Int = indentPx * depth.coerceAtLeast(1)

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        if (!first) return
        val marker = when (kind) {
            EssayListKind.BULLET -> if (depth % 2 == 1) "\u2022" else "\u25E6"
            EssayListKind.ORDERED -> "$ordinal."
        }
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        val levelOffset = ((depth - 1).coerceAtLeast(0) * indentPx) + (indentPx / 4)
        val drawX = x + dir * levelOffset
        canvas.drawText(marker, drawX.toFloat(), baseline.toFloat(), paint)
        paint.style = oldStyle
    }

    fun copy(
        listStack: List<EssayListKind> = this.listStack,
        ordinal: Int = this.ordinal,
        indentPx: Int = this.indentPx,
    ): EssayListItemSpan = EssayListItemSpan(listStack, ordinal, indentPx)
}

class EssayLineBreakSpan

object EssayHtml {
    private val ALLOWED_BLOCK_CONTAINERS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")
    private val SKIP_TAGS = setOf("script", "style", "template", "img", "figure", "svg", "iframe", "object", "embed", "video", "audio")

    fun export(spanned: Spanned): String {
        val cleanedLength = spanned.length
        if (cleanedLength == 0) return ""
        val paragraphs = extractParagraphs(spanned)
        return exportParagraphs(paragraphs, cleanedLength * 2)
    }

    private fun exportParagraphs(paragraphs: List<ParsedParagraph>, initialCapacity: Int): String {
        val trimmed = dropTrailingEmptyParagraphs(paragraphs)
        if (trimmed.isEmpty()) return ""
        val hasAnyNonBlankContent = trimmed.any { para ->
            para.runs.any { it.text.any { ch -> !ch.isWhitespace() && ch != '\u00A0' && ch != '\u0000' } }
        }
        if (!hasAnyNonBlankContent) return ""

        val out = StringBuilder(initialCapacity.coerceAtLeast(32))
        val openLists = ArrayList<EssayListKind>()

        for (para in trimmed) {
            val targetStack = para.listStack
            var common = 0
            while (common < openLists.size && common < targetStack.size && openLists[common] == targetStack[common]) {
                common += 1
            }

            if (targetStack.size == openLists.size && common == openLists.size && openLists.isNotEmpty()) {
                out.append("</li><li>")
            } else {
                while (openLists.size > common) {
                    val closing = openLists.removeAt(openLists.lastIndex)
                    out.append("</li>")
                    out.append(if (closing == EssayListKind.BULLET) "</ul>" else "</ol>")
                }
                if (common > 0 && targetStack.size == common) {
                    out.append("</li><li>")
                } else {
                    for (depth in common until targetStack.size) {
                        val opening = targetStack[depth]
                        out.append(if (opening == EssayListKind.BULLET) "<ul><li>" else "<ol><li>")
                        openLists.add(opening)
                    }
                }
            }

            out.append("<p>")
            appendInlineRuns(out, para.runs)
            out.append("</p>")
        }

        while (openLists.isNotEmpty()) {
            val closing = openLists.removeAt(openLists.lastIndex)
            out.append("</li>")
            out.append(if (closing == EssayListKind.BULLET) "</ul>" else "</ol>")
        }

        return out.toString()
    }

    fun importToSpannable(html: String, indentPx: Int = 48): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        if (html.isBlank()) return builder
        val sanitized = AnswerValues.stripNullChars(html)
        val doc = Jsoup.parseBodyFragment(sanitized)
        val paragraphs = ArrayList<ParsedParagraph>()
        parseChildrenIntoParagraphs(doc.body(), emptyList(), paragraphs)
        val trimmed = dropTrailingEmptyParagraphs(paragraphs)
        if (trimmed.isEmpty()) return builder

        val ordinalCounters = HashMap<List<EssayListKind>, Int>()
        var previousStack: List<EssayListKind> = emptyList()

        trimmed.forEachIndexed { index, para ->
            if (index > 0) {
                builder.append('\n')
            }
            val paraStart = builder.length
            for (run in para.runs) {
                if (run.isLineBreak) {
                    val brStart = builder.length
                    builder.append('\n')
                    builder.setSpan(EssayLineBreakSpan(), brStart, brStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (run.text.isNotEmpty()) {
                    val runStart = builder.length
                    builder.append(run.text)
                    val runEnd = builder.length
                    if (run.bold && run.italic) {
                        builder.setSpan(StyleSpan(Typeface.BOLD), runStart, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(StyleSpan(Typeface.ITALIC), runStart, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (run.bold) {
                        builder.setSpan(StyleSpan(Typeface.BOLD), runStart, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (run.italic) {
                        builder.setSpan(StyleSpan(Typeface.ITALIC), runStart, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            val paraEnd = builder.length

            if (para.listStack.isNotEmpty()) {
                val stack = para.listStack
                var common = 0
                while (common < previousStack.size && common < stack.size && previousStack[common] == stack[common]) {
                    common += 1
                }
                val keysToReset = ordinalCounters.keys.filter { it.size > common }
                for (key in keysToReset) {
                    ordinalCounters.remove(key)
                }
                val ordinal = (ordinalCounters[stack] ?: 0) + 1
                ordinalCounters[stack] = ordinal
                val flags = if (paraStart == paraEnd) {
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                } else {
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                }
                builder.setSpan(
                    EssayListItemSpan(listStack = stack, ordinal = ordinal, indentPx = indentPx),
                    paraStart,
                    paraEnd,
                    flags,
                )
            } else {
                ordinalCounters.clear()
            }
            previousStack = para.listStack
        }
        return builder
    }

    fun sanitizePastedHtml(html: String): String {
        if (html.isBlank()) return ""
        val sanitized = AnswerValues.stripNullChars(html)
        val doc = Jsoup.parseBodyFragment(sanitized)
        val paragraphs = ArrayList<ParsedParagraph>()
        parseChildrenIntoParagraphs(doc.body(), emptyList(), paragraphs)
        return exportParagraphs(paragraphs, sanitized.length)
    }

    fun sanitizePlainText(text: String): String {
        val cleaned = AnswerValues.stripNullChars(text)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (cleaned.isBlank()) return ""
        val lines = cleaned.split('\n')
        val out = StringBuilder(cleaned.length + lines.size * 7)
        for (line in lines) {
            out.append("<p>")
            appendEscapedText(out, line)
            out.append("</p>")
        }
        return out.toString()
    }

    fun recomputeListOrdinals(spannable: Spannable, indentPx: Int = 48) {
        val text = spannable.toString()
        val lineBreakPositions = spannable.getSpans(0, spannable.length, EssayLineBreakSpan::class.java)
            .mapTo(HashSet()) { spannable.getSpanStart(it) }
        val paragraphRanges = computeParagraphRanges(text, lineBreakPositions)
        val ordinalCounters = HashMap<List<EssayListKind>, Int>()
        var previousStack: List<EssayListKind> = emptyList()

        for (range in paragraphRanges) {
            val spans = spannable.getSpans(range.first, range.last, EssayListItemSpan::class.java)
                .filter { span ->
                    val s = spannable.getSpanStart(span)
                    val e = spannable.getSpanEnd(span)
                    s <= range.first && e >= range.last || (range.first == range.last && s == range.first)
                }
            val currentSpan = spans.lastOrNull()
            for (old in spans) {
                spannable.removeSpan(old)
            }
            if (currentSpan != null && currentSpan.listStack.isNotEmpty()) {
                val stack = currentSpan.listStack
                var common = 0
                while (common < previousStack.size && common < stack.size && previousStack[common] == stack[common]) {
                    common += 1
                }
                val keysToReset = ordinalCounters.keys.filter { it.size > common }
                for (key in keysToReset) {
                    ordinalCounters.remove(key)
                }
                val ordinal = (ordinalCounters[stack] ?: 0) + 1
                ordinalCounters[stack] = ordinal
                val flags = if (range.first == range.last) {
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                } else {
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                }
                spannable.setSpan(
                    EssayListItemSpan(listStack = stack, ordinal = ordinal, indentPx = indentPx),
                    range.first,
                    range.last,
                    flags,
                )
                previousStack = stack
            } else {
                ordinalCounters.clear()
                previousStack = emptyList()
            }
        }
    }

    fun findParagraphRange(spanned: Spanned, cursor: Int): IntRange {
        val len = spanned.length
        val safeCursor = cursor.coerceIn(0, len)
        val lineBreakPositions = spanned.getSpans(0, len, EssayLineBreakSpan::class.java)
            .mapTo(HashSet()) { spanned.getSpanStart(it) }
        var start = safeCursor
        while (start > 0) {
            val prev = start - 1
            if (spanned[prev] == '\n' && prev !in lineBreakPositions) break
            start -= 1
        }
        var end = safeCursor
        while (end < len) {
            if (spanned[end] == '\n' && end !in lineBreakPositions) break
            end += 1
        }
        return start..end
    }

    fun listSpanAtParagraph(spanned: Spanned, range: IntRange): EssayListItemSpan? {
        val spans = spanned.getSpans(range.first, range.last, EssayListItemSpan::class.java)
        return spans.lastOrNull { span ->
            val s = spanned.getSpanStart(span)
            val e = spanned.getSpanEnd(span)
            if (range.first == range.last) {
                s <= range.first && e >= range.first
            } else {
                s < range.last && e > range.first
            }
        }
    }

    private data class InlineRun(
        val text: String = "",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val isLineBreak: Boolean = false,
    )

    private data class ParsedParagraph(
        val listStack: List<EssayListKind>,
        val runs: List<InlineRun>,
    )

    private fun computeParagraphRanges(text: String, lineBreakPositions: Set<Int>): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n' && i !in lineBreakPositions) {
                ranges.add(start..i)
                start = i + 1
            }
        }
        ranges.add(start..text.length)
        return ranges
    }

    private fun extractParagraphs(spanned: Spanned): List<ParsedParagraph> {
        val len = spanned.length
        val text = spanned.toString()
        val lineBreakPositions = spanned.getSpans(0, len, EssayLineBreakSpan::class.java)
            .mapTo(HashSet()) { spanned.getSpanStart(it) }
        val ranges = computeParagraphRanges(text, lineBreakPositions)
        val result = ArrayList<ParsedParagraph>(ranges.size)

        for (range in ranges) {
            val listSpan = listSpanAtParagraph(spanned, range)
            val runs = ArrayList<InlineRun>()
            var pos = range.first
            val buffer = StringBuilder()
            var curBold = false
            var curItalic = false

            fun flushBuffer() {
                if (buffer.isNotEmpty()) {
                    runs.add(InlineRun(text = buffer.toString(), bold = curBold, italic = curItalic))
                    buffer.setLength(0)
                }
            }

            while (pos < range.last) {
                val ch = text[pos]
                if (ch == '\u0000') {
                    pos += 1
                    continue
                }
                if (ch == '\n' && pos in lineBreakPositions) {
                    flushBuffer()
                    runs.add(InlineRun(isLineBreak = true))
                    pos += 1
                    continue
                }
                val (bold, italic) = styleAt(spanned, pos)
                if (buffer.isEmpty()) {
                    curBold = bold
                    curItalic = italic
                } else if (bold != curBold || italic != curItalic) {
                    flushBuffer()
                    curBold = bold
                    curItalic = italic
                }
                buffer.append(ch)
                pos += 1
            }
            flushBuffer()
            result.add(
                ParsedParagraph(
                    listStack = listSpan?.listStack ?: emptyList(),
                    runs = runs,
                ),
            )
        }
        return result
    }

    private fun styleAt(spanned: Spanned, index: Int): Pair<Boolean, Boolean> {
        var bold = false
        var italic = false
        val spans = spanned.getSpans(index, index + 1, StyleSpan::class.java)
        for (span in spans) {
            val s = spanned.getSpanStart(span)
            val e = spanned.getSpanEnd(span)
            if (index in s until e) {
                when (span.style) {
                    Typeface.BOLD -> bold = true
                    Typeface.ITALIC -> italic = true
                    Typeface.BOLD_ITALIC -> {
                        bold = true
                        italic = true
                    }
                }
            }
        }
        return bold to italic
    }

    private fun dropTrailingEmptyParagraphs(paragraphs: List<ParsedParagraph>): List<ParsedParagraph> {
        var end = paragraphs.size
        while (end > 0) {
            val last = paragraphs[end - 1]
            val empty = last.listStack.isEmpty() && last.runs.all { !it.isLineBreak && it.text.isEmpty() }
            if (empty) {
                end -= 1
            } else {
                break
            }
        }
        return paragraphs.subList(0, end)
    }

    private fun appendInlineRuns(out: StringBuilder, runs: List<InlineRun>) {
        var boldOpen = false
        var italicOpen = false

        fun closeAll() {
            if (italicOpen) {
                out.append("</em>")
                italicOpen = false
            }
            if (boldOpen) {
                out.append("</strong>")
                boldOpen = false
            }
        }

        for (run in runs) {
            if (run.isLineBreak) {
                closeAll()
                out.append("<br>")
                continue
            }
            if (run.text.isEmpty()) continue
            if (boldOpen != run.bold || italicOpen != run.italic) {
                when {
                    boldOpen && !run.bold -> {
                        if (italicOpen) {
                            out.append("</em>")
                            italicOpen = false
                        }
                        out.append("</strong>")
                        boldOpen = false
                        if (run.italic) {
                            out.append("<em>")
                            italicOpen = true
                        }
                    }
                    !boldOpen && run.bold -> {
                        if (italicOpen) {
                            out.append("</em>")
                            italicOpen = false
                        }
                        out.append("<strong>")
                        boldOpen = true
                        if (run.italic) {
                            out.append("<em>")
                            italicOpen = true
                        }
                    }
                    italicOpen && !run.italic -> {
                        out.append("</em>")
                        italicOpen = false
                    }
                    !italicOpen && run.italic -> {
                        out.append("<em>")
                        italicOpen = true
                    }
                }
            }
            appendEscapedText(out, run.text)
        }
        closeAll()
    }

    private fun appendEscapedText(out: StringBuilder, text: String) {
        for (ch in text) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '\u00A0' -> out.append("&nbsp;")
                '\u0000' -> Unit
                else -> out.append(ch)
            }
        }
    }

    private fun parseChildrenIntoParagraphs(
        container: Element,
        listStack: List<EssayListKind>,
        out: MutableList<ParsedParagraph>,
    ) {
        val pendingInline = ArrayList<InlineRun>()

        fun flushPending() {
            if (pendingInline.isEmpty()) return
            val hasMeaningful = pendingInline.any { it.isLineBreak || it.text.isNotBlank() || it.text.contains('\u00A0') }
            if (hasMeaningful) {
                out.add(ParsedParagraph(listStack = listStack, runs = pendingInline.toList()))
            }
            pendingInline.clear()
        }

        for (child in container.childNodes()) {
            when (child) {
                is TextNode -> {
                    val raw = child.wholeText
                    if (raw.isNotEmpty()) {
                        pendingInline.add(InlineRun(text = raw))
                    }
                }
                is Element -> {
                    val tag = child.tagName().lowercase()
                    if (tag in SKIP_TAGS) continue
                    when (tag) {
                        "ul" -> {
                            flushPending()
                            parseListElement(child, listStack + EssayListKind.BULLET, out)
                        }
                        "ol" -> {
                            flushPending()
                            parseListElement(child, listStack + EssayListKind.ORDERED, out)
                        }
                        "li" -> {
                            flushPending()
                            val effectiveStack = if (listStack.isEmpty()) listOf(EssayListKind.BULLET) else listStack
                            parseListItemElement(child, effectiveStack, out)
                        }
                        in ALLOWED_BLOCK_CONTAINERS -> {
                            flushPending()
                            val runs = ArrayList<InlineRun>()
                            val hasBlockChild = child.children().any {
                                val childTag = it.tagName().lowercase()
                                childTag == "ul" || childTag == "ol" || childTag in ALLOWED_BLOCK_CONTAINERS
                            }
                            if (hasBlockChild) {
                                parseChildrenIntoParagraphs(child, listStack, out)
                            } else {
                                collectInlineRuns(child, bold = false, italic = false, out = runs)
                                out.add(ParsedParagraph(listStack = listStack, runs = runs))
                            }
                        }
                        "br" -> pendingInline.add(InlineRun(isLineBreak = true))
                        else -> collectInlineRuns(child, bold = false, italic = false, out = pendingInline)
                    }
                }
            }
        }
        flushPending()
    }

    private fun parseListElement(
        listEl: Element,
        listStack: List<EssayListKind>,
        out: MutableList<ParsedParagraph>,
    ) {
        for (child in listEl.children()) {
            val tag = child.tagName().lowercase()
            when (tag) {
                "li" -> parseListItemElement(child, listStack, out)
                "ul" -> parseListElement(child, listStack + EssayListKind.BULLET, out)
                "ol" -> parseListElement(child, listStack + EssayListKind.ORDERED, out)
            }
        }
    }

    private fun parseListItemElement(
        liEl: Element,
        listStack: List<EssayListKind>,
        out: MutableList<ParsedParagraph>,
    ) {
        val beforeSize = out.size
        parseChildrenIntoParagraphs(liEl, listStack, out)
        if (out.size == beforeSize) {
            out.add(ParsedParagraph(listStack = listStack, runs = emptyList()))
        }
    }

    private fun collectInlineRuns(
        node: Node,
        bold: Boolean,
        italic: Boolean,
        out: MutableList<InlineRun>,
    ) {
        when (node) {
            is TextNode -> {
                val raw = node.wholeText
                if (raw.isNotEmpty()) {
                    out.add(InlineRun(text = raw, bold = bold, italic = italic))
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag in SKIP_TAGS) return
                if (tag == "br") {
                    out.add(InlineRun(isLineBreak = true))
                    return
                }
                val nextBold = bold || tag == "strong" || tag == "b"
                val nextItalic = italic || tag == "em" || tag == "i"
                for (child in node.childNodes()) {
                    collectInlineRuns(child, nextBold, nextItalic, out)
                }
            }
        }
    }
}
