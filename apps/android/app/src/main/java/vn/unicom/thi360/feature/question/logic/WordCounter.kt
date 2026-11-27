package vn.unicom.thi360.feature.question.logic

import java.text.Normalizer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object WordCounter {
    private val BLOCK_TAGS = setOf(
        "p",
        "div",
        "li",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "blockquote",
        "tr",
        "td",
        "th",
        "figcaption",
        "ul",
        "ol",
        "table",
    )

    private val SKIP_TAGS = setOf("script", "style", "template")
    private val WHITESPACE_REGEX = Regex("""[\s\u00A0]+""")
    private val WORD_TOKEN_REGEX = Regex("""[\p{L}\p{N}]""")

    fun textForWordCount(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val out = StringBuilder(html.length)
        collectText(doc.body(), out)
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
            .replace('\u00A0', ' ')
    }

    fun count(html: String?): Int {
        val text = textForWordCount(html)
        if (text.isBlank()) return 0
        var count = 0
        for (token in WHITESPACE_REGEX.split(text)) {
            if (token.isNotEmpty() && WORD_TOKEN_REGEX.containsMatchIn(token)) {
                count += 1
            }
        }
        return count
    }

    fun plainText(html: String?): String =
        textForWordCount(html)
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun collectText(node: Node, out: StringBuilder) {
        when (node) {
            is TextNode -> out.append(node.wholeText)
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag in SKIP_TAGS) return
                if (tag == "span" && (node.hasClass("math-inline") || node.hasClass("math-block"))) {
                    return
                }
                if (node.hasClass("math-inline") || node.hasClass("math-block")) {
                    return
                }
                if (tag == "br") {
                    out.append(' ')
                    return
                }
                for (child in node.childNodes()) {
                    collectText(child, out)
                }
                if (tag in BLOCK_TAGS) {
                    out.append(' ')
                }
            }
        }
    }
}
