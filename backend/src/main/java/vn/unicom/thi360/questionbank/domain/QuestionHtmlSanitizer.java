package vn.unicom.thi360.questionbank.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/**
 * Lọc HTML nội dung câu hỏi theo danh sách cho phép (spec mục 4.2, mở rộng theo spec
 * 2026-11-17 mục 3). Hai lượt: <b>danh sách phẳng</b> (thẻ, thuộc tính, giá trị đóng) rồi
 * <b>lượt theo phần tử</b> cho luật cấu trúc của spec 3.3 mà danh sách phẳng không diễn đạt
 * được (quan hệ cha/con, số và thứ tự con, thuộc tính theo ngữ cảnh).
 *
 * <p>Danh sách phẳng: thẻ {@code p, br, strong, em, u, s, sub, sup, ul, ol, li, table, thead,
 * tbody, tr, th, td, span, img, figure, figcaption, blockquote, footer, cite}; thuộc tính theo
 * {@link #attributeAllowed} (giá trị đóng của {@code class}, {@code scope}, {@code data-*}).
 * Không cho {@code style}, {@code script}, thuộc tính sự kiện {@code on*}, liên kết ngoài
 * (kể cả {@code javascript:}). Lượt cấu trúc: {@link #checkStructure} — vi phạm thì bỏ phần
 * tử (cả khối {@code figure}/{@code blockquote}) và báo theo thẻ, hoặc thẻ + thuộc tính khi
 * sai/thiếu giá trị; con của phần tử đã báo không báo tiếp.
 *
 * <p>{@link #clean(String)} trả kết quả gồm HTML đã lọc và danh sách phần bị bỏ/sửa; lời gọi
 * quyết định theo spec: nội dung không đổi thì lưu như cũ, có phần bị bỏ thì 422
 * {@code QUESTION_CONTENT_UNSAFE}. Phần {@code src} của {@code img} luôn được viết lại từ
 * {@code data-asset-id} qua {@link QuestionAssetResolver}: thiếu id hoặc id không tra được là
 * lỗi; {@code src} đầu vào khác đường phục vụ thật bị loại và báo. Với
 * {@link #clean(String, boolean)} truyền {@code allowImages=false} thì mọi {@code img} và cả
 * {@code figure class="image"} là vi phạm và bị loại (nội dung chỉ chữ).
 */
public class QuestionHtmlSanitizer {

    /** Phần nội dung bị loại hoặc viết lại trong một lần lọc. */
    public record Violation(String tag, String attribute, String value) {

        static Violation tag(String tag) {
            return new Violation(tag, null, null);
        }

        static Violation attribute(String tag, String attribute, String value) {
            return new Violation(tag, attribute, value);
        }
    }

    /**
     * Kết quả lọc một trường HTML.
     *
     * @param html HTML sau khi lọc (và viết lại {@code src} của {@code img})
     * @param violations phần bị bỏ/sửa; rỗng nghĩa là nội dung đã an toàn nguyên trạng
     */
    public record Result(String html, List<Violation> violations) {

        public Result {
            violations = List.copyOf(violations);
        }

        /** {@code true} khi không có vi phạm. */
        public boolean unchanged() {
            return violations.isEmpty();
        }
    }

    private static final Set<String> ALLOWED_TAGS = Set.of(
        "p", "br", "strong", "em", "u", "s", "sub", "sup",
        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
        "span", "img", "figure", "figcaption", "blockquote", "footer", "cite");

    private static final Set<String> ALLOWED_TAGS_NO_IMG = Set.of(
        "p", "br", "strong", "em", "u", "s", "sub", "sup",
        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
        "span", "figure", "figcaption", "blockquote", "footer", "cite");

    private static final Safelist SAFELIST = safelist(true);

    private static final Safelist SAFELIST_NO_IMG = safelist(false);

    private static Safelist safelist(boolean allowImages) {
        // Thuộc tính mở rộng vẫn qua attributeAllowed: Safelist chỉ giới hạn tên.
        Safelist safelist = Safelist.none()
            .addTags(
                "p", "br", "strong", "em", "u", "s", "sub", "sup",
                "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
                "span", "figure", "figcaption", "blockquote", "footer", "cite")
            .addAttributes("span", "class", "data-blank")
            .addAttributes("u", "data-label")
            .addAttributes("p", "class")
            .addAttributes("td", "colspan", "rowspan", "class")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addAttributes("figure", "class", "data-width", "data-zoom")
            .addAttributes("blockquote", "class", "data-kind", "data-line-numbers");
        if (allowImages) {
            safelist.addTags("img")
                .addAttributes("img", "src", "alt", "width", "height", "data-asset-id");
        }
        return safelist;
    }
    private static final Set<String> MATH_CLASSES = Set.of("math-inline", "math-block");
    private static final Set<String> SPAN_CLASSES = Set.of("math-inline", "math-block", "blank");
    private static final Set<String> P_CLASSES = Set.of("figure-note", "figure-source");
    private static final Set<String> FIGURE_CLASSES = Set.of("data-table", "image", "cross");
    private static final Set<String> FIGURE_WIDTHS = Set.of("small", "medium", "full");
    private static final Set<String> FIGURE_ZOOM = Set.of("true", "false");
    private static final Set<String> QUOTE_KINDS = Set.of("poem", "prose", "source");
    private static final Set<String> QUOTE_LINE_NUMBERS = Set.of("0", "5");
    private static final Set<String> TH_SCOPES = Set.of("col", "row");
    /** Nội dung phân tử được phép trong ô của bảng số liệu và sơ đồ lai (spec 3.2). */
    private static final Set<String> FIGURE_CELL_INLINE = Set.of(
        "strong", "em", "sub", "sup", "br");


    private final QuestionAssetResolver assetResolver;

    public QuestionHtmlSanitizer(QuestionAssetResolver assetResolver) {
        this.assetResolver = assetResolver;
    }

    /**
     * Lọc một trường HTML. Không ném lỗi: mọi vấn đề nằm trong {@link Result#violations()},
     * lời gọi map sang {@code QUESTION_CONTENT_UNSAFE} khi danh sách không rỗng.
     */
    public Result clean(String html) {
        return clean(html, true);
    }

    /**
     * {@code allowImages=false} coi mọi {@code img} là vi phạm và loại khỏi kết quả (dùng cho
     * nội dung chỉ chữ như hướng dẫn làm bài, spec 5.2); {@code true} giữ quy tắc câu hỏi.
     */
    public Result clean(String html, boolean allowImages) {
        if (html == null) {
            html = "";
        }
        Set<String> allowedTags = allowImages ? ALLOWED_TAGS : ALLOWED_TAGS_NO_IMG;
        Document source = Jsoup.parseBodyFragment(html);
        // Fragment parsing discards attributes on html/body and the head tag itself.
        // A second parse preserves those input tokens for violation reporting.
        Document document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
        List<Violation> violations = new ArrayList<>();
        if (document.head().sourceRange().isTracked() && !document.head().sourceRange().isImplicit()) {
            violations.add(Violation.tag("head"));
        }
        for (Element shell : List.of(document.children().first(), document.head(), document.body())) {
            for (Attribute attribute : shell.attributes()) {
                violations.add(Violation.attribute(shell.tagName(), attribute.getKey(), attribute.getValue()));
            }
        }
        inspect(source.body(), violations, allowedTags);
        Document clean = new org.jsoup.safety.Cleaner(allowImages ? SAFELIST : SAFELIST_NO_IMG)
            .clean(source);
        clean.outputSettings(new Document.OutputSettings().prettyPrint(false));
        stripDisallowed(clean);
        rewriteImages(clean, violations);
        checkStructure(clean.body(), violations, allowImages);
        return new Result(clean.body().html(), dedup(violations));
    }

    /** Chữ thuần của một đoạn HTML (để dựng {@code content_text} và kiểm tra rỗng). */
    public static String textOf(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text();
    }


    /**
     * {@code true} khi HTML có ít nhất một thẻ {@code img} (P1-22: img làm nội dung không
     * rỗng). Chỉ gọi trên HTML đã qua lọc — img còn sót là img mang {@code data-asset-id}
     * tra được qua resolver.
     */
    public static boolean containsImg(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        return !Jsoup.parseBodyFragment(html).select("img").isEmpty();
    }
    /**
     * Duyệt cây nguồn trước khi lọc để ghi nhận phần sẽ bị bỏ: thẻ ngoài danh sách, thuộc tính
     * không được phép, giá trị {@code class} của {@code span} ngoài hai lớp công thức, và
     * {@code src}/{@code href} không phải đường nội bộ an toàn (liên kết ngoài, {@code javascript:}).
     */
    private void inspect(Element element, List<Violation> violations, Set<String> allowedTags) {
        for (Node node : element.childNodes()) {
            if (node instanceof Element child) {
                String tag = child.tagName().toLowerCase(Locale.ROOT);
                if (!allowedTags.contains(tag)) {
                    violations.add(Violation.tag(tag));
                    // Không đi sâu vào phần tử bị loại: phần tử con hợp lệ của nó vẫn được giữ
                    // bởi jsoup Cleaner, nên vẫn phải duyệt con để báo đủ.
                    inspect(child, violations, allowedTags);
                    continue;
                }
                for (Attribute attribute : child.attributes()) {
                    String name = attribute.getKey().toLowerCase(Locale.ROOT);
                    String value = attribute.getValue();
                    if (!attributeAllowed(tag, name, value)) {
                        violations.add(Violation.attribute(tag, name, value));
                    }
                }
                inspect(child, violations, allowedTags);
            }
            // Comment và DataNode (mã script/style) bị loại cùng thẻ cha.
        }
    }

    /**
     * Sau khi lọc, bỏ tiếp thuộc tính mà Safelist không giới hạn được theo giá trị
     * (ví dụ {@code class} của {@code span} ngoài hai lớp công thức). Không ghi vi phạm —
     * {@link #inspect} đã báo phần bị bỏ ở cây nguồn.
     */
    private void stripDisallowed(Document clean) {
        for (Element element : clean.getAllElements()) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            for (Attribute attribute : element.attributes().asList()) {
                String name = attribute.getKey().toLowerCase(Locale.ROOT);
                if (!attributeAllowed(tag, name, attribute.getValue())) {
                    element.removeAttr(attribute.getKey());
                }
            }
        }
    }

    /**
     * Sau khi lọc: mỗi {@code img} phải có {@code data-asset-id} tra được qua resolver;
     * {@code src} được viết lại bằng đường phục vụ thật. Thiếu id hoặc id không có là vi phạm;
     * {@code src} đầu vào khác đường thật cũng là vi phạm (bị viết lại).
     */
    private void rewriteImages(Document clean, List<Violation> violations) {
        for (Element img : clean.select("img")) {
            String rawId = img.attr("data-asset-id").trim();
            if (rawId.isEmpty()) {
                violations.add(Violation.attribute("img", "data-asset-id", ""));
                continue;
            }
            UUID assetId;
            try {
                assetId = UUID.fromString(rawId);
            } catch (IllegalArgumentException malformed) {
                violations.add(Violation.attribute("img", "data-asset-id", rawId));
                continue;
            }
            var path = assetResolver.servingPath(assetId);
            if (path.isEmpty()) {
                violations.add(Violation.attribute("img", "data-asset-id", rawId));
                continue;
            }
            String previous = img.attr("src");
            if (!previous.isBlank() && !previous.trim().equals(path.get())) {
                // src đầu vào lệch đường phục vụ thật: bị viết lại, báo là phần bị sửa.
                violations.add(Violation.attribute("img", "src", previous));
            }
            if (!previous.equals(path.get())) {
                img.attr("src", path.get());
            }
        }
    }
    /**
     * Danh sách phẳng về thuộc tính (dùng bởi {@link #inspect} và {@link #stripDisallowed}):
     * chỉ trả {@code true} khi tên thuộc tính thuộc thẻ <i>và</i> giá trị nằm trong danh sách
     * đóng của nó. Thuộc tính đúng-tên-nhưng-sai-chỗ ({@code data-width} trên figure không
     * phải ảnh, {@code data-line-numbers} khác 0 khi không thơ) do lượt cấu trúc xử lý.
     */
    private boolean attributeAllowed(String tag, String name, String value) {
        if (name.startsWith("on") || name.equals("style")) {
            return false;
        }
        String v = value == null ? "" : value.trim();
        switch (tag) {
            case "img":
                return switch (name) {
                    case "src" -> value == null || value.isBlank() || isSafeInternalSrc(value);
                    case "alt", "width", "height", "data-asset-id" -> true;
                    default -> false;
                };
            case "span":
                return switch (name) {
                    case "class" -> SPAN_CLASSES.contains(v);
                    case "data-blank" -> isBlankNumber(v);
                    default -> false;
                };
            case "u":
                return name.equals("data-label") && v.matches("[A-H]");
            case "p":
                return name.equals("class") && P_CLASSES.contains(v);
            case "td":
                return switch (name) {
                    case "colspan", "rowspan" -> true;
                    case "class" -> v.equals("num");
                    default -> false;
                };
            case "th":
                return switch (name) {
                    case "colspan", "rowspan" -> true;
                    case "scope" -> TH_SCOPES.contains(v);
                    default -> false;
                };
            case "figure":
                return switch (name) {
                    case "class" -> FIGURE_CLASSES.contains(v);
                    case "data-width" -> FIGURE_WIDTHS.contains(v);
                    case "data-zoom" -> FIGURE_ZOOM.contains(v);
                    default -> false;
                };
            case "blockquote":
                return switch (name) {
                    case "class" -> v.equals("quote");
                    case "data-kind" -> QUOTE_KINDS.contains(v);
                    case "data-line-numbers" -> QUOTE_LINE_NUMBERS.contains(v);
                    default -> false;
                };
            default:
                return false;
        }
    }

    private static boolean isBlankNumber(String value) {
        try {
            int n = Integer.parseInt(value);
            return n >= 1 && n <= 20;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * {@code src} đầu vào chỉ chấp nhận đường phục vụ nội bộ ({@code /api/v1/...}) hoặc để
     * trống (sẽ được điền từ {@code data-asset-id}); URL ngoài và {@code javascript:} bị loại.
     */
    private boolean isSafeInternalSrc(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//");
    }

    // ---------------------------------------------------------------- luật cấu trúc (spec 3.3)

    /**
     * Duyệt cây <b>đã lọc</b> và áp các luật cấu trúc: phần tử vi phạm bị bỏ ({@code figure}
     * và {@code blockquote} bỏ cả khối, phần tử khác được mở bọc giữ chữ) và không báo tiếp con
     * của nó. Báo thẻ kèm thuộc tính khi sai/thiếu giá trị, chỉ thẻ khi sai cấu trúc.
     */
    private void checkStructure(Element parent, List<Violation> violations, boolean allowImages) {
        for (Element el : new ArrayList<>(parent.children())) {
            if (structuralViolation(el, violations, allowImages)) {
                continue; // con của phần tử đã báo không được kiểm tiếp
            }
            checkStructure(el, violations, allowImages);
        }
    }

    /**
     * {@code true} khi {@code el} vi phạm một luật cấu trúc (đã báo và đã bỏ/mở bọc);
     * {@code false} nghĩa là phần tử hợp lệ và duyệt tiếp con của nó.
     */
    private boolean structuralViolation(Element el, List<Violation> violations, boolean allowImages) {
        switch (el.tagName().toLowerCase(Locale.ROOT)) {
            case "figure":
                return checkFigure(el, violations, allowImages);
            case "blockquote":
                return checkQuote(el, violations);
            case "figcaption":
                if (el.parent() == null || !el.parent().tagName().equals("figure")) {
                    return drop(el, violations, "figcaption", null, false);
                }
                return false;
            case "footer":
                if (el.parent() == null || !el.parent().tagName().equals("blockquote")
                        || !"quote".equals(el.parent().attr("class").trim())) {
                    return drop(el, violations, "footer", null, false);
                }
                return false;
            case "cite":
                if (el.parent() == null || !el.parent().tagName().equals("footer")) {
                    return drop(el, violations, "cite", null, false);
                }
                return false;
            case "p": {
                String cls = el.attr("class").trim();
                if (P_CLASSES.contains(cls)
                        && (el.parent() == null || !(el.parent().tagName().equals("figure")
                                || el.parent().tagName().equals("blockquote")))) {
                    return drop(el, violations, "p", "class", false);
                }
                return false;
            }
            case "span": {
                String cls = el.attr("class").trim();
                if (el.hasAttr("data-blank") && !"blank".equals(cls)) {
                    return drop(el, violations, "span", "data-blank", false);
                }
                if ("math-block".equals(cls) && !isTopLevel(el)) {
                    // Công thức khối chỉ ở cấp đầu (spec 3.1).
                    return drop(el, violations, "span", null, false);
                }
                if (isBlankSpan(el)) {
                    if (!el.hasAttr("data-blank")) {
                        return drop(el, violations, "span", "data-blank", false);
                    }
                    if (!el.childNodes().isEmpty()) {
                        return drop(el, violations, "span", null, false);
                    }
                }
                return false;
            }
            case "td":
            case "th": {
                // Ô của bảng trong figure chỉ chứa chữ + phân tử inline (spec 3.2).
                if (el.closest("figure") != null) {
                    for (Element child : el.children()) {
                        String tag = child.tagName();
                        if (tag.equals("span") && child.hasClass("math-inline")) {
                            continue;
                        }
                        if (!FIGURE_CELL_INLINE.contains(tag)) {
                            drop(child, violations, tag, null, false);
                        }
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    private static boolean isBlankSpan(Element el) {
        return "blank".equals(el.attr("class").trim());
    }

    private static boolean isTopLevel(Element el) {
        return el.parent() != null && el.parent().tagName().equals("body");
    }

    /**
     * {@code figure} chỉ có {@code class} ∈ {@code {data-table, image, cross}}, nằm ở cấp đầu,
     * và con trực tiếp đúng dãy của loại (spec 3.3). {@code data-width}/{@code data-zoom}
     * bắt buộc và chỉ có ở {@code figure.image}; lọc không ảnh thì {@code figure.image} là
     * vi phạm.
     */
    private boolean checkFigure(Element el, List<Violation> violations, boolean allowImages) {
        String cls = el.attr("class").trim();
        if (!FIGURE_CLASSES.contains(cls)) {
            return drop(el, violations, "figure", "class", true);
        }
        if (!"image".equals(cls) && (el.hasAttr("data-width") || el.hasAttr("data-zoom"))) {
            return drop(el, violations, "figure",
                    el.hasAttr("data-width") ? "data-width" : "data-zoom", true);
        }
        if ("image".equals(cls)) {
            if (!allowImages) {
                return drop(el, violations, "figure", null, true);
            }
            if (el.attr("data-width").isBlank()) {
                return drop(el, violations, "figure", "data-width", true);
            }
            if (el.attr("data-zoom").isBlank()) {
                return drop(el, violations, "figure", "data-zoom", true);
            }
        }
        if (!isTopLevel(el)) {
            return drop(el, violations, "figure", null, true);
        }
        List<Element> children = el.children();
        return switch (cls) {
            case "image" -> {
                int i = 0;
                boolean ok = children.size() >= 1 && children.get(i).tagName().equals("img");
                if (ok) {
                    i++;
                    if (i < children.size() && children.get(i).tagName().equals("figcaption")) {
                        i++;
                    }
                    if (i < children.size() && isPWithClass(children.get(i), "figure-source")) {
                        i++;
                    }
                    ok = i == children.size() && !hasStrayText(el);
                }
                yield ok ? false : drop(el, violations, "figure", null, true);
            }
            case "data-table" -> {
                int i = 0;
                if (i < children.size() && children.get(i).tagName().equals("figcaption")) {
                    i++;
                }
                boolean ok = i < children.size() && children.get(i).tagName().equals("table");
                if (ok) {
                    i++;
                    if (i < children.size() && isPWithClass(children.get(i), "figure-note")) {
                        i++;
                    }
                    if (i < children.size() && isPWithClass(children.get(i), "figure-source")) {
                        i++;
                    }
                    ok = i == children.size() && !hasStrayText(el);
                }
                yield ok ? false : drop(el, violations, "figure", null, true);
            }
            default -> { // cross
                boolean ok = children.size() == 1
                        && children.get(0).tagName().equals("table")
                        && !hasStrayText(el);
                yield ok ? false : drop(el, violations, "figure", null, true);
            }
        };
    }

    /**
     * {@code blockquote} chỉ {@code class="quote"}, {@code data-kind} ∈ {poem, prose, source},
     * {@code data-line-numbers} ∈ {0, 5} (khác 0 chỉ khi poem), ở cấp đầu; con là một hay
     * nhiều {@code p}, rồi tùy chọn {@code footer} và {@code p.figure-source}.
     */
    private boolean checkQuote(Element el, List<Violation> violations) {
        if (!"quote".equals(el.attr("class").trim())) {
            return drop(el, violations, "blockquote", "class", true);
        }
        String kind = el.attr("data-kind");
        if (kind.isBlank()) {
            return drop(el, violations, "blockquote", "data-kind", true);
        }
        String lineNumbers = el.attr("data-line-numbers");
        if (lineNumbers.isBlank() || (!"0".equals(lineNumbers) && !"poem".equals(kind))) {
            return drop(el, violations, "blockquote", "data-line-numbers", true);
        }
        if (!isTopLevel(el)) {
            return drop(el, violations, "blockquote", null, true);
        }
        List<Element> children = el.children();
        int i = 0;
        while (i < children.size() && children.get(i).tagName().equals("p")
                && !isPWithClass(children.get(i), "figure-source")) {
            i++;
        }
        boolean ok = i >= 1;
        if (i < children.size() && children.get(i).tagName().equals("footer")) {
            i++;
        }
        if (i < children.size() && isPWithClass(children.get(i), "figure-source")) {
            i++;
        }
        ok = ok && i == children.size() && !hasStrayText(el);
        return ok ? false : drop(el, violations, "blockquote", null, true);
    }

    private static boolean isPWithClass(Element el, String cls) {
        return el.tagName().equals("p") && cls.equals(el.attr("class").trim());
    }

    private static boolean hasStrayText(Element el) {
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode text && !text.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Báo vi phạm và bỏ phần tử: {@code whole} (figure/blockquote) bỏ cả khối, còn lại mở
     * bọc để giữ phần chữ con. Trả {@code true} để vòng duyệt không đi tiếp vào con.
     */
    private static boolean drop(
            Element el, List<Violation> violations, String tag, String attribute, boolean whole) {
        violations.add(attribute == null ? Violation.tag(tag)
                : Violation.attribute(tag, attribute, null));
        if (whole) {
            el.remove();
        } else {
            el.unwrap();
        }
        return true;
    }

    /** Gộp vi phạm trùng (thẻ, thuộc tính) — mỗi vi phạm báo một lần (spec 3.3). */
    private static List<Violation> dedup(List<Violation> violations) {
        List<Violation> result = new ArrayList<>(violations.size());
        Set<String> seen = new HashSet<>();
        for (Violation v : violations) {
            if (seen.add(v.tag() + " " + (v.attribute() == null ? "" : v.attribute()))) {
                result.add(v);
            }
        }
        return result;
    }

}
