package vn.unicom.thi360.feature.richcontent.parser

import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.FigureWidth
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.FormulaNotation
import vn.unicom.thi360.feature.richcontent.model.QuoteKind
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichDocument
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.model.RichParseResult
import vn.unicom.thi360.feature.richcontent.model.RichSanitizeViolation
import vn.unicom.thi360.feature.richcontent.model.RichTable
import vn.unicom.thi360.feature.richcontent.model.RichTableCell
import vn.unicom.thi360.feature.richcontent.model.RichTableRow
import vn.unicom.thi360.feature.richcontent.model.RichTextStyle
import vn.unicom.thi360.feature.richcontent.model.TableHeaderScope

object RichHtmlParser {
    private val ALLOWED_TAGS = setOf(
        "p", "br", "strong", "em", "u", "s", "sub", "sup",
        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
        "span", "img", "figure", "figcaption", "blockquote", "footer", "cite",
    )
    private val ALLOWED_TAGS_NO_IMG = ALLOWED_TAGS - "img"
    private val DROP_SUBTREE_TAGS = setOf("script", "style", "template", "iframe", "object", "embed")

    private val MATH_CLASSES = setOf("math-inline", "math-block")
    private val FIGURE_CLASSES = setOf("data-table", "image", "cross")
    private val PARAGRAPH_CLASSES = setOf("figure-note", "figure-source")
    private val TD_CLASSES = setOf("num")
    private val FIGURE_WIDTHS = setOf("small", "medium", "full")
    private val FIGURE_ZOOMS = setOf("true", "false")
    private val QUOTE_KINDS = setOf("poem", "prose", "source")
    private val QUOTE_LINE_NUMBERS = setOf("0", "5")
    private val TH_SCOPES = setOf("col", "row")

    private val UNDERLINE_LABEL_PATTERN = Regex("^[A-H]$")
    private val BLANK_NUMBER_PATTERN = Regex("^(?:[1-9]|1[0-9]|20)$")
    private val INTEGER_PATTERN = Regex("^\\d+$")
    private val UUID_PATTERN = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE,
    )

    private val CE_COMMAND_PATTERN = Regex("""\\ce(?![A-Za-z])""")
    private val PU_COMMAND_PATTERN = Regex("""\\pu(?![A-Za-z])""")
    private val SLOT_MARKER_PATTERN = Regex("""\[\[([^\]]+)\]\]""")

    private const val MAX_WALK_DEPTH = 512
    private const val MAX_TABLE_COLSPAN = 64
    private const val MAX_TABLE_ROWSPAN = 200

    fun parse(
        html: String,
        options: RichParseOptions = RichParseOptions(),
    ): RichDocument = parseWithViolations(html, options).document

    fun parseWithViolations(
        html: String,
        options: RichParseOptions = RichParseOptions(),
    ): RichParseResult {
        if (html.isEmpty()) {
            return RichParseResult(
                document = RichDocument(blocks = emptyList(), blankNumberStart = options.blankNumberStart),
                violations = emptyList(),
            )
        }

        val doc = Jsoup.parseBodyFragment(html)
        val body = doc.body()
        val violations = mutableListOf<RichSanitizeViolation>()
        val allowedTags = if (options.allowImages) ALLOWED_TAGS else ALLOWED_TAGS_NO_IMG

        sanitizeChildren(
            parent = body,
            parentIsRoot = true,
            allowedTags = allowedTags,
            allowImages = options.allowImages,
            violations = violations,
            depth = 0,
        )

        val blocks = buildBlocksFromContainer(body, options, depth = 0)
        return RichParseResult(
            document = RichDocument(blocks = blocks, blankNumberStart = options.blankNumberStart),
            violations = violations,
        )
    }

    internal fun detectNotation(latex: String): FormulaNotation = when {
        CE_COMMAND_PATTERN.containsMatchIn(latex) -> FormulaNotation.Ce
        PU_COMMAND_PATTERN.containsMatchIn(latex) -> FormulaNotation.Pu
        else -> FormulaNotation.Tex
    }

    internal fun splitRawMathAndSlots(
        text: String,
        styles: Set<RichTextStyle>,
        slotMarkers: Boolean,
    ): List<RichInline> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<RichInline>()
        var cursor = 0
        while (cursor < text.length) {
            val openInline = text.indexOf("\\(", cursor)
            val openBlock = text.indexOf("\\[", cursor)
            val openIdx = when {
                openInline == -1 -> openBlock
                openBlock == -1 -> openInline
                else -> minOf(openInline, openBlock)
            }
            if (openIdx == -1) {
                appendPlainText(out, text.substring(cursor), styles, slotMarkers)
                break
            }
            val isBlock = text[openIdx + 1] == '['
            val closeDelim = if (isBlock) "\\]" else "\\)"
            val closeIdx = text.indexOf(closeDelim, openIdx + 2)
            if (closeIdx == -1) {
                appendPlainText(out, text.substring(cursor), styles, slotMarkers)
                break
            }
            if (openIdx > cursor) {
                appendPlainText(out, text.substring(cursor, openIdx), styles, slotMarkers)
            }
            val latex = text.substring(openIdx + 2, closeIdx)
            val display = if (isBlock) FormulaDisplay.Block else FormulaDisplay.Inline
            out += RichInline.Formula(
                source = latex,
                display = display,
                notation = detectNotation(latex),
            )
            cursor = closeIdx + 2
        }
        return out
    }

    private fun appendPlainText(
        out: MutableList<RichInline>,
        segment: String,
        styles: Set<RichTextStyle>,
        slotMarkers: Boolean,
    ) {
        if (segment.isEmpty()) return
        if (!slotMarkers || !segment.contains("[[")) {
            out += RichInline.Text(segment, styles)
            return
        }
        var cursor = 0
        for (match in SLOT_MARKER_PATTERN.findAll(segment)) {
            if (match.range.first > cursor) {
                out += RichInline.Text(segment.substring(cursor, match.range.first), styles)
            }
            val slotId = match.groupValues[1].trim()
            if (slotId.isNotEmpty()) {
                out += RichInline.Slot(slotId)
            } else {
                out += RichInline.Text(match.value, styles)
            }
            cursor = match.range.last + 1
        }
        if (cursor < segment.length) {
            out += RichInline.Text(segment.substring(cursor), styles)
        }
    }

    private fun sanitizeChildren(
        parent: Element,
        parentIsRoot: Boolean,
        allowedTags: Set<String>,
        allowImages: Boolean,
        violations: MutableList<RichSanitizeViolation>,
        depth: Int,
    ) {
        for (child in parent.childNodes().toList()) {
            when (child) {
                is Comment, is DataNode, is DocumentType -> {
                    child.remove()
                    continue
                }
                !is Element -> continue
            }

            val tag = child.tagName().lowercase(Locale.ROOT)
            if (tag in DROP_SUBTREE_TAGS) {
                violations += RichSanitizeViolation(tag = tag)
                child.remove()
                continue
            }

            if (tag !in allowedTags) {
                violations += RichSanitizeViolation(tag = tag)
                if (depth < MAX_WALK_DEPTH) {
                    sanitizeChildren(
                        parent = child,
                        parentIsRoot = parentIsRoot,
                        allowedTags = allowedTags,
                        allowImages = allowImages,
                        violations = violations,
                        depth = depth + 1,
                    )
                    child.unwrap()
                } else {
                    val plainText = child.text()
                    if (plainText.isNotEmpty()) {
                        child.replaceWith(TextNode(plainText))
                    } else {
                        child.remove()
                    }
                }
                continue
            }

            if (tag == "figure" || tag == "blockquote") {
                val closedViolations = closedAttributeViolations(child, tag)
                if (closedViolations.isNotEmpty()) {
                    violations.addAll(closedViolations)
                    child.remove()
                    continue
                }
            }

            var reported = false
            val isBlankSpan = tag == "span" && child.attr("class").trim() == "blank"
            for (attr in child.attributes().asList()) {
                val name = attr.key.lowercase(Locale.ROOT)
                val value = attr.value
                if (attributeAllowed(tag, name, value)) continue
                violations += RichSanitizeViolation(tag = tag, attribute = name, value = value)
                if (isBlankSpan) {
                    child.remove()
                    break
                }
                child.removeAttr(attr.key)
                reported = true
            }
            if (child.parent() == null) continue

            if (tag == "img" && allowImages) {
                val rawId = child.attr("data-asset-id").trim()
                if (!UUID_PATTERN.matches(rawId)) {
                    violations += RichSanitizeViolation(tag = "img", attribute = "data-asset-id", value = rawId)
                    reported = true
                }
            }

            if (depth < MAX_WALK_DEPTH) {
                sanitizeChildren(
                    parent = child,
                    parentIsRoot = false,
                    allowedTags = allowedTags,
                    allowImages = allowImages,
                    violations = violations,
                    depth = depth + 1,
                )
            } else {
                val plainText = child.text()
                child.empty()
                if (plainText.isNotEmpty()) child.appendChild(TextNode(plainText))
            }

            if (reported) continue

            val attrViolation = checkElementStructure(child, parentIsRoot, checkAttr = true)
            if (attrViolation != null) {
                violations += attrViolation
                if (isBlankSpan) {
                    child.remove()
                    continue
                }
                attrViolation.attribute?.let { child.removeAttr(it) }
                continue
            }

            val structureViolation = checkElementStructure(child, parentIsRoot, checkAttr = false)
            if (structureViolation != null) {
                violations += structureViolation
                child.remove()
            }
        }
    }

    private fun attributeAllowed(tag: String, name: String, value: String): Boolean {
        if (name.startsWith("on") || name == "style") return false
        val trimmed = value.trim()
        return when (tag) {
            "img" -> when (name) {
                "src" -> trimmed.isEmpty() || AttemptAssetSource.isSafeInternalSrc(trimmed)
                "alt", "width", "height", "data-asset-id" -> true
                else -> false
            }
            "span" -> when (name) {
                "class" -> trimmed in MATH_CLASSES || trimmed == "blank"
                "data-blank" -> true
                else -> false
            }
            "u" -> name == "data-label" && UNDERLINE_LABEL_PATTERN.matches(trimmed)
            "td" -> when (name) {
                "class" -> trimmed in TD_CLASSES
                "colspan", "rowspan" -> INTEGER_PATTERN.matches(trimmed)
                else -> false
            }
            "th" -> when (name) {
                "scope" -> trimmed in TH_SCOPES
                "colspan", "rowspan" -> INTEGER_PATTERN.matches(trimmed)
                else -> false
            }
            "p" -> name == "class" && trimmed in PARAGRAPH_CLASSES
            "figure" -> when (name) {
                "class" -> trimmed in FIGURE_CLASSES
                "data-width" -> trimmed in FIGURE_WIDTHS
                "data-zoom" -> trimmed in FIGURE_ZOOMS
                else -> false
            }
            "blockquote" -> when (name) {
                "class" -> trimmed == "quote"
                "data-kind" -> trimmed in QUOTE_KINDS
                "data-line-numbers" -> trimmed in QUOTE_LINE_NUMBERS
                else -> false
            }
            else -> false
        }
    }

    private fun closedAttributeViolations(el: Element, tag: String): List<RichSanitizeViolation> {
        val violations = mutableListOf<RichSanitizeViolation>()
        val seen = HashSet<String>()
        fun report(name: String, value: String?) {
            if (seen.add(name)) {
                violations += RichSanitizeViolation(tag = tag, attribute = name, value = value)
            }
        }
        fun isBlankAttr(name: String): Boolean = el.attr(name).trim().isEmpty()

        for (attr in el.attributes()) {
            val name = attr.key.lowercase(Locale.ROOT)
            if (!attributeAllowed(tag, name, attr.value)) {
                report(name, attr.value)
            }
        }

        if (tag == "figure") {
            val figureClass = el.attr("class").trim()
            if (figureClass !in FIGURE_CLASSES) {
                report("class", if (el.hasAttr("class")) el.attr("class") else null)
            } else if (figureClass == "image") {
                if (isBlankAttr("data-width")) report("data-width", null)
                if (isBlankAttr("data-zoom")) report("data-zoom", null)
            } else {
                if (el.hasAttr("data-width")) report("data-width", el.attr("data-width"))
                if (el.hasAttr("data-zoom")) report("data-zoom", el.attr("data-zoom"))
            }
            return violations
        }

        if (el.attr("class").trim() != "quote") {
            report("class", if (el.hasAttr("class")) el.attr("class") else null)
        }
        val kind = el.attr("data-kind").trim()
        if (kind.isEmpty()) report("data-kind", null)
        val lineNumbers = el.attr("data-line-numbers").trim()
        if (lineNumbers.isEmpty() || (kind.isNotEmpty() && lineNumbers != "0" && kind != "poem")) {
            report("data-line-numbers", if (el.hasAttr("data-line-numbers")) el.attr("data-line-numbers") else null)
        }
        return violations
    }

    private fun checkElementStructure(
        el: Element,
        parentIsRoot: Boolean,
        checkAttr: Boolean,
    ): RichSanitizeViolation? {
        val tag = el.tagName().lowercase(Locale.ROOT)
        fun attrViolation(name: String): RichSanitizeViolation? =
            if (checkAttr) RichSanitizeViolation(tag = tag, attribute = name, value = el.attr(name)) else null

        fun structuralViolation(): RichSanitizeViolation? =
            if (!checkAttr) RichSanitizeViolation(tag = tag) else null

        return when (tag) {
            "figure" -> {
                if (!parentIsRoot) return structuralViolation()
                val figureClass = el.attr("class").trim()
                val children = el.children()
                val legal = when (figureClass) {
                    "image" -> {
                        val first = children.firstOrNull()
                        val rest = children.drop(1)
                        first != null &&
                            first.tagName().equals("img", ignoreCase = true) &&
                            rest.withIndex().all { (index, child) ->
                                (index == 0 && child.tagName().equals("figcaption", ignoreCase = true) && child.classNames().isEmpty()) ||
                                    child.matchesTagClass("p", "figure-source")
                            }
                    }
                    "data-table" -> {
                        val tableCount = children.count { it.tagName().equals("table", ignoreCase = true) }
                        tableCount == 1 &&
                            children.withIndex().all { (index, child) ->
                                child.tagName().equals("table", ignoreCase = true) ||
                                    (index == 0 && child.tagName().equals("figcaption", ignoreCase = true) && child.classNames().isEmpty()) ||
                                    child.matchesTagClass("p", "figure-note") ||
                                    child.matchesTagClass("p", "figure-source")
                            }
                    }
                    "cross" -> children.size == 1 && children[0].tagName().equals("table", ignoreCase = true)
                    else -> false
                }
                if (legal) null else structuralViolation()
            }
            "blockquote" -> {
                if (!parentIsRoot) return structuralViolation()
                val children = el.children()
                val paragraphs = children.filter { it.tagName().equals("p", ignoreCase = true) }
                val footers = children.filter { it.tagName().equals("footer", ignoreCase = true) }
                val sources = children.filter { it.matchesTagClass("p", "figure-source") }
                val plainCount = paragraphs.size - sources.size
                val legal = plainCount >= 1 &&
                    children.size == paragraphs.size + footers.size &&
                    children.withIndex().all { (index, child) ->
                        (index < plainCount && child.tagName().equals("p", ignoreCase = true) && child.classNames().isEmpty()) ||
                            (index == plainCount && child.tagName().equals("footer", ignoreCase = true)) ||
                            child.matchesTagClass("p", "figure-source")
                    } &&
                    sources.all { source -> children.indexOf(source) == children.lastIndex }
                if (legal) null else structuralViolation()
            }
            "figcaption" -> {
                val parent = el.parent()
                if (parent != null && parent.tagName().equals("figure", ignoreCase = true)) null else structuralViolation()
            }
            "footer" -> {
                val parent = el.parent()
                if (parent != null && parent.matchesTagClass("blockquote", "quote")) null else structuralViolation()
            }
            "cite" -> {
                val parent = el.parent()
                if (parent != null && parent.tagName().equals("footer", ignoreCase = true)) null else structuralViolation()
            }
            "p" -> {
                val pClass = el.attr("class").trim()
                val parent = el.parent()
                val allowedParent = parent != null && (
                    parent.tagName().equals("figure", ignoreCase = true) ||
                        parent.tagName().equals("blockquote", ignoreCase = true)
                    )
                if (pClass.isNotEmpty() && !allowedParent) attrViolation("class") else null
            }
            "span" -> {
                val isBlank = el.attr("class").trim() == "blank"
                if (!isBlank) {
                    if (el.hasAttr("data-blank")) attrViolation("data-blank") else null
                } else {
                    val value = if (el.hasAttr("data-blank")) el.attr("data-blank").trim() else null
                    if (value == null || !BLANK_NUMBER_PATTERN.matches(value)) {
                        attrViolation("data-blank")
                    } else if (el.childNodes().isNotEmpty()) {
                        structuralViolation()
                    } else {
                        null
                    }
                }
            }
            else -> null
        }
    }

    private fun Element.matchesTagClass(expectedTag: String, expectedClass: String): Boolean =
        tagName().equals(expectedTag, ignoreCase = true) && attr("class").trim() == expectedClass

    private fun buildBlocksFromContainer(
        container: Element,
        options: RichParseOptions,
        depth: Int,
    ): List<RichBlock> {
        val blocks = mutableListOf<RichBlock>()
        val pendingInlines = mutableListOf<RichInline>()

        fun flushPending() {
            if (pendingInlines.isNotEmpty()) {
                appendParagraphSplitOnBlockFormulas(blocks, pendingInlines.toList())
                pendingInlines.clear()
            }
        }

        for (node in container.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.wholeText
                    if (text.isNotEmpty()) {
                        val parsed = splitRawMathAndSlots(text, emptySet(), options.slotMarkers)
                        if (parsed.any { it !is RichInline.Text || it.text.isNotBlank() } || pendingInlines.isNotEmpty()) {
                            pendingInlines.addAll(parsed)
                        }
                    }
                }
                is Element -> {
                    val tag = node.tagName().lowercase(Locale.ROOT)
                    when {
                        tag == "p" -> {
                            flushPending()
                            val inlines = collectInlines(node, emptySet(), options)
                            appendParagraphSplitOnBlockFormulas(blocks, inlines)
                        }
                        tag == "span" && node.attr("class").trim() == "math-block" -> {
                            flushPending()
                            blocks += RichBlock.FormulaBlock(parseMathSpan(node, FormulaDisplay.Block))
                        }
                        tag == "ul" -> {
                            flushPending()
                            blocks += parseList(node, ordered = false, options = options, depth = depth)
                        }
                        tag == "ol" -> {
                            flushPending()
                            blocks += parseList(node, ordered = true, options = options, depth = depth)
                        }
                        tag == "table" -> {
                            flushPending()
                            blocks += RichBlock.Table(parseTable(node, options, depth))
                        }
                        tag == "figure" -> {
                            flushPending()
                            parseFigure(node, options, depth)?.let { blocks += it }
                        }
                        tag == "blockquote" -> {
                            flushPending()
                            blocks += parseQuote(node, options)
                        }
                        else -> {
                            collectInlineElement(node, emptySet(), options, pendingInlines)
                        }
                    }
                }
            }
        }
        flushPending()
        return blocks
    }

    private fun appendParagraphSplitOnBlockFormulas(
        blocks: MutableList<RichBlock>,
        inlines: List<RichInline>,
    ) {
        if (inlines.isEmpty()) return
        if (inlines.none { it is RichInline.Formula && it.display == FormulaDisplay.Block }) {
            if (!isWhitespaceOnly(inlines)) {
                blocks += RichBlock.Paragraph(inlines)
            }
            return
        }
        val segment = mutableListOf<RichInline>()
        for (inline in inlines) {
            if (inline is RichInline.Formula && inline.display == FormulaDisplay.Block) {
                if (!isWhitespaceOnly(segment)) {
                    blocks += RichBlock.Paragraph(trimEdgeSpaces(segment))
                }
                segment.clear()
                blocks += RichBlock.FormulaBlock(inline)
            } else {
                segment += inline
            }
        }
        if (!isWhitespaceOnly(segment)) {
            blocks += RichBlock.Paragraph(trimEdgeSpaces(segment))
        }
    }

    private fun isWhitespaceOnly(inlines: List<RichInline>): Boolean =
        inlines.isEmpty() || inlines.all { it is RichInline.Text && it.text.isBlank() }

    private fun trimEdgeSpaces(inlines: List<RichInline>): List<RichInline> {
        val list = inlines.toMutableList()
        while (list.isNotEmpty()) {
            val first = list.first()
            if (first is RichInline.Text) {
                val trimmed = first.text.trimStart()
                if (trimmed.isEmpty()) {
                    list.removeAt(0)
                    continue
                }
                if (trimmed != first.text) {
                    list[0] = first.copy(text = trimmed)
                }
            }
            break
        }
        while (list.isNotEmpty()) {
            val last = list.last()
            if (last is RichInline.Text) {
                val trimmed = last.text.trimEnd()
                if (trimmed.isEmpty()) {
                    list.removeAt(list.lastIndex)
                    continue
                }
                if (trimmed != last.text) {
                    list[list.lastIndex] = last.copy(text = trimmed)
                }
            }
            break
        }
        return list
    }

    private fun parseList(
        el: Element,
        ordered: Boolean,
        options: RichParseOptions,
        depth: Int,
    ): RichBlock {
        val items = mutableListOf<List<RichBlock>>()
        for (child in el.children()) {
            if (child.tagName().equals("li", ignoreCase = true)) {
                val itemBlocks = if (depth < MAX_WALK_DEPTH) {
                    buildBlocksFromContainer(child, options, depth + 1)
                } else {
                    val txt = child.text()
                    if (txt.isNotEmpty()) listOf(RichBlock.Paragraph(listOf(RichInline.Text(txt)))) else emptyList()
                }
                items += itemBlocks
            }
        }
        return if (ordered) RichBlock.OrderedList(items) else RichBlock.BulletList(items)
    }

    private fun parseFigure(
        el: Element,
        options: RichParseOptions,
        depth: Int,
    ): RichBlock? {
        return when (el.attr("class").trim()) {
            "data-table" -> {
                val tableEl = el.children().firstOrNull { it.tagName().equals("table", ignoreCase = true) }
                    ?: return null
                val caption = el.children()
                    .firstOrNull { it.tagName().equals("figcaption", ignoreCase = true) }
                    ?.let { collectInlines(it, emptySet(), options) }
                val notes = el.children()
                    .filter { it.matchesTagClass("p", "figure-note") }
                    .map { collectInlines(it, emptySet(), options) }
                val sources = el.children()
                    .filter { it.matchesTagClass("p", "figure-source") }
                    .map { collectInlines(it, emptySet(), options) }
                RichBlock.DataTableFigure(
                    caption = caption,
                    table = parseTable(tableEl, options, depth),
                    notes = notes,
                    sources = sources,
                )
            }
            "cross" -> {
                val tableEl = el.children().firstOrNull { it.tagName().equals("table", ignoreCase = true) }
                    ?: return null
                RichBlock.CrossFigure(parseTable(tableEl, options, depth))
            }
            "image" -> {
                val imgEl = el.children().firstOrNull { it.tagName().equals("img", ignoreCase = true) }
                    ?: return null
                val width = when (el.attr("data-width").trim()) {
                    "small" -> FigureWidth.Small
                    "medium" -> FigureWidth.Medium
                    else -> FigureWidth.Full
                }
                val zoomable = el.attr("data-zoom").trim() == "true"
                val caption = el.children()
                    .firstOrNull { it.tagName().equals("figcaption", ignoreCase = true) }
                    ?.let { collectInlines(it, emptySet(), options) }
                val sources = el.children()
                    .filter { it.matchesTagClass("p", "figure-source") }
                    .map { collectInlines(it, emptySet(), options) }
                RichBlock.ImageFigure(
                    image = parseImageElement(imgEl),
                    width = width,
                    zoomable = zoomable,
                    caption = caption,
                    sources = sources,
                )
            }
            else -> null
        }
    }

    private fun parseQuote(
        el: Element,
        options: RichParseOptions,
    ): RichBlock.Quote {
        val kind = when (el.attr("data-kind").trim()) {
            "poem" -> QuoteKind.Poem
            "source" -> QuoteKind.Source
            else -> QuoteKind.Prose
        }
        val lineNumberStep = if (kind == QuoteKind.Poem && el.attr("data-line-numbers").trim() == "5") 5 else 0
        val paragraphs = mutableListOf<List<RichInline>>()
        var footer: List<RichInline>? = null
        var source: List<RichInline>? = null

        for (child in el.children()) {
            val tag = child.tagName().lowercase(Locale.ROOT)
            when {
                tag == "p" && child.attr("class").trim() == "figure-source" -> {
                    source = collectInlines(child, emptySet(), options)
                }
                tag == "p" -> {
                    paragraphs += collectInlines(child, emptySet(), options)
                }
                tag == "footer" -> {
                    footer = collectInlines(child, emptySet(), options)
                }
            }
        }
        return RichBlock.Quote(
            kind = kind,
            lineNumberStep = lineNumberStep,
            paragraphs = paragraphs,
            footer = footer,
            source = source,
        )
    }

    private fun parseTable(
        tableEl: Element,
        options: RichParseOptions,
        depth: Int,
    ): RichTable {
        val rows = mutableListOf<RichTableRow>()
        fun collectRows(section: Element, isHead: Boolean) {
            for (child in section.children()) {
                val tag = child.tagName().lowercase(Locale.ROOT)
                when (tag) {
                    "thead" -> collectRows(child, isHead = true)
                    "tbody" -> collectRows(child, isHead = false)
                    "tr" -> {
                        val cells = child.children()
                            .filter { it.tagName().equals("td", ignoreCase = true) || it.tagName().equals("th", ignoreCase = true) }
                            .map { cellEl -> parseTableCell(cellEl, options, depth) }
                        if (cells.isNotEmpty()) {
                            rows += RichTableRow(cells = cells, isInHead = isHead)
                        }
                    }
                }
            }
        }
        collectRows(tableEl, isHead = false)
        return RichTable(rows)
    }

    private fun parseTableCell(
        cellEl: Element,
        options: RichParseOptions,
        depth: Int,
    ): RichTableCell {
        val isHeader = cellEl.tagName().equals("th", ignoreCase = true)
        val scope = if (isHeader) {
            when (cellEl.attr("scope").trim()) {
                "col" -> TableHeaderScope.Col
                "row" -> TableHeaderScope.Row
                else -> null
            }
        } else {
            null
        }
        val isNumeric = !isHeader && cellEl.attr("class").trim() == "num"
        val colspan = parseBoundedSpan(cellEl.attr("colspan"), MAX_TABLE_COLSPAN)
        val rowspan = parseBoundedSpan(cellEl.attr("rowspan"), MAX_TABLE_ROWSPAN)
        val blocks = buildBlocksFromContainer(cellEl, options, depth + 1)
        return RichTableCell(
            isHeader = isHeader,
            scope = scope,
            isNumeric = isNumeric,
            colspan = colspan,
            rowspan = rowspan,
            blocks = blocks,
        )
    }

    private fun parseBoundedSpan(raw: String, max: Int): Int {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) return 1
        val parsed = trimmed.toLongOrNull() ?: return max
        return parsed.coerceIn(1L, max.toLong()).toInt()
    }

    private fun collectInlines(
        parent: Element,
        styles: Set<RichTextStyle>,
        options: RichParseOptions,
    ): List<RichInline> {
        val out = mutableListOf<RichInline>()
        for (node in parent.childNodes()) {
            collectInlineNode(node, styles, options, out)
        }
        return out
    }

    private fun collectInlineNode(
        node: Node,
        styles: Set<RichTextStyle>,
        options: RichParseOptions,
        out: MutableList<RichInline>,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotEmpty()) {
                    out.addAll(splitRawMathAndSlots(text, styles, options.slotMarkers))
                }
            }
            is Element -> collectInlineElement(node, styles, options, out)
        }
    }

    private fun collectInlineElement(
        el: Element,
        styles: Set<RichTextStyle>,
        options: RichParseOptions,
        out: MutableList<RichInline>,
    ) {
        when (el.tagName().lowercase(Locale.ROOT)) {
            "br" -> out += RichInline.LineBreak
            "strong" -> out.addAll(collectInlines(el, styles + RichTextStyle.Bold, options))
            "em", "cite" -> out.addAll(collectInlines(el, styles + RichTextStyle.Italic, options))
            "s" -> out.addAll(collectInlines(el, styles + RichTextStyle.Strike, options))
            "sub" -> out.addAll(collectInlines(el, styles + RichTextStyle.Sub, options))
            "sup" -> out.addAll(collectInlines(el, styles + RichTextStyle.Sup, options))
            "u" -> {
                val label = el.attr("data-label").trim()
                if (label.length == 1 && UNDERLINE_LABEL_PATTERN.matches(label)) {
                    val children = collectInlines(el, styles, options)
                    out += RichInline.LabeledUnderline(label = label[0], children = children)
                } else {
                    out.addAll(collectInlines(el, styles + RichTextStyle.Underline, options))
                }
            }
            "span" -> {
                when (el.attr("class").trim()) {
                    "math-inline" -> out += parseMathSpan(el, FormulaDisplay.Inline)
                    "math-block" -> out += parseMathSpan(el, FormulaDisplay.Block)
                    "blank" -> {
                        val raw = el.attr("data-blank").trim().toIntOrNull()
                        if (raw != null && raw in 1..20) {
                            out += RichInline.Blank(
                                number = options.blankNumberStart + raw - 1,
                                rawNumber = raw,
                            )
                        }
                    }
                    else -> out.addAll(collectInlines(el, styles, options))
                }
            }
            "img" -> out += parseImageElement(el)
            else -> out.addAll(collectInlines(el, styles, options))
        }
    }

    private fun parseMathSpan(el: Element, display: FormulaDisplay): RichInline.Formula {
        val raw = el.wholeText()
        val trimmed = raw.trim()
        val open = if (display == FormulaDisplay.Block) "\\[" else "\\("
        val close = if (display == FormulaDisplay.Block) "\\]" else "\\)"
        val latex = if (trimmed.startsWith(open) && trimmed.endsWith(close) && trimmed.length >= 4) {
            trimmed.substring(2, trimmed.length - 2)
        } else {
            raw
        }
        return RichInline.Formula(
            source = latex,
            display = display,
            notation = detectNotation(latex),
        )
    }

    private fun parseImageElement(el: Element): RichInline.Image {
        val src = if (el.hasAttr("src")) el.attr("src") else null
        val dataAssetId = if (el.hasAttr("data-asset-id")) el.attr("data-asset-id") else null
        val ref = AttemptAssetSource.resolveAssetRef(src, dataAssetId)
        val alt = el.attr("alt")
        val width = el.attr("width").trim().toIntOrNull()?.takeIf { it > 0 }
        val height = el.attr("height").trim().toIntOrNull()?.takeIf { it > 0 }
        return RichInline.Image(
            ref = ref,
            alt = alt,
            width = width,
            height = height,
        )
    }
}
