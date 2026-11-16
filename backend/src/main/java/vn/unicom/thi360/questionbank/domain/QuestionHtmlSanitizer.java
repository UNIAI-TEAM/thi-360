package vn.unicom.thi360.questionbank.domain;

import java.util.ArrayList;
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
 * Lọc HTML nội dung câu hỏi theo danh sách cho phép của spec mục 4.2 (plan B6: jsoup).
 *
 * <p>Quy tắc: các thẻ {@code p, br, strong, em, u, s, sub, sup, ul, ol, li, table, thead, tbody,
 * tr, th, td, span, img}; {@code td}/{@code th} thêm {@code colspan, rowspan}; {@code span} chỉ
 * nhận {@code class} là {@code math-inline} hoặc {@code math-block}; {@code img} nhận
 * {@code src, alt, width, height, data-asset-id} và phải trỏ tới tài nguyên của hệ thống.
 * Không cho {@code style}, {@code script}, thuộc tính sự kiện {@code on*}, liên kết ngoài
 * (kể cả {@code javascript:}).
 *
 * <p>{@link #clean(String)} trả kết quả gồm HTML đã lọc và danh sách phần bị bỏ/sửa; lời gọi
 * quyết định theo spec: nội dung không đổi thì lưu như cũ, có phần bị bỏ thì 422
 * {@code QUESTION_CONTENT_UNSAFE}. Phần {@code src} của {@code img} luôn được viết lại từ
 * {@code data-asset-id} qua {@link QuestionAssetResolver}: thiếu id hoặc id không tra được là
 * lỗi; {@code src} đầu vào khác đường phục vụ thật bị loại và báo. Với
 * {@link #clean(String, boolean)} truyền {@code allowImages=false} thì mọi {@code img} là vi
 * phạm và bị loại (nội dung chỉ chữ, ví dụ hướng dẫn làm bài của spec 5.2).
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
        "span", "img");

    private static final Set<String> ALLOWED_TAGS_NO_IMG = Set.of(
        "p", "br", "strong", "em", "u", "s", "sub", "sup",
        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
        "span");

    private static final Safelist SAFELIST = safelist(true);

    private static final Safelist SAFELIST_NO_IMG = safelist(false);

    private static Safelist safelist(boolean allowImages) {
        Safelist safelist = Safelist.none()
            .addTags(
                "p", "br", "strong", "em", "u", "s", "sub", "sup",
                "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
                "span")
            .addAttributes("span", "class")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan");
        if (allowImages) {
            safelist.addTags("img")
                .addAttributes("img", "src", "alt", "width", "height", "data-asset-id");
        }
        return safelist;
    }

    private static final Set<String> MATH_CLASSES = Set.of("math-inline", "math-block");

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
        return new Result(clean.body().html(), violations);
    }

    /** Chữ thuần của một đoạn HTML (để dựng {@code content_text} và kiểm tra rỗng). */
    public static String textOf(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text();
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
    private boolean attributeAllowed(String tag, String name, String value) {
        if (name.startsWith("on") || name.equals("style")) {
            return false;
        }
        switch (tag) {
            case "img":
                return switch (name) {
                    case "src" -> value == null || value.isBlank() || isSafeInternalSrc(value);
                    case "alt", "width", "height", "data-asset-id" -> true;
                    default -> false;
                };
            case "span":
                return name.equals("class") && MATH_CLASSES.contains(value.trim());
            case "td":
            case "th":
                return name.equals("colspan") || name.equals("rowspan");
            default:
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

}
