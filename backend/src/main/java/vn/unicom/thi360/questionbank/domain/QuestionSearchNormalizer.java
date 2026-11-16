package vn.unicom.thi360.questionbank.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa văn bản tiếng Việt cho cột {@code search_text} (spec D16): chữ thường, bỏ dấu
 * (NFD + bỏ dấu tổ hợp), đổi {@code đ}/{@code Đ} thành {@code d} (ký tự này không tách qua NFD),
 * gộp khoảng trắng. Nhờ đó tìm {@code "toan"} khớp {@code "Toán"}, {@code "dao dong"} khớp
 * {@code "dao động"}.
 *
 * <p>{@link #searchText} dựng chuỗi tìm kiếm từ mã câu và phần chữ của thân câu (HTML đã tách):
 * đề bài, ngữ liệu chùm/điền khuyết, phương án, mệnh đề, mục ghép đôi và nội dung câu con —
 * cùng phần chữ hiện ở {@code content_text} cho xem trước danh sách.
 */
public final class QuestionSearchNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QuestionSearchNormalizer() {}

    /** Chuẩn hóa một đoạn chữ thuần (đã tách HTML) cho {@code search_text}. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutD = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(withoutD, Normalizer.Form.NFD);
        String stripped = MARKS.matcher(decomposed).replaceAll("");
        String lowered = stripped.toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(lowered).replaceAll(" ").trim();
    }

    /**
     * Dựng {@code search_text} của một câu: mã câu + phần chữ của đề bài và thân câu,
     * đã tách HTML rồi chuẩn hóa. Thuần hàm, không đụng dịch vụ.
     */
    public static String searchText(String code, String content, QuestionBody body) {
        List<String> parts = new ArrayList<>();
        if (code != null) {
            parts.add(code);
        }
        appendPlain(parts, content);
        appendBody(parts, body);
        String joined = String.join(" ", parts);
        return normalize(joined);
    }

    /** Chữ thuần của câu để ghi {@code content_text} (xem trước danh sách). */
    public static String contentText(String content, QuestionBody body) {
        List<String> parts = new ArrayList<>();
        appendPlain(parts, content);
        appendBody(parts, body);
        return WHITESPACE.matcher(String.join(" ", parts).trim()).replaceAll(" ");
    }

    private static void appendBody(List<String> parts, QuestionBody body) {
        if (body == null) {
            return;
        }
        switch (body) {
            case QuestionBody.SingleChoiceBody b -> b.options().forEach(o -> appendPlain(parts, o.content()));
            case QuestionBody.MultipleChoiceBody b -> b.options().forEach(o -> appendPlain(parts, o.content()));
            case QuestionBody.ShortAnswerBody b -> b.answers().forEach(a -> {
                if (a != null && !a.isBlank()) {
                    parts.add(a);
                }
            });
            case QuestionBody.ClozeBody b -> appendPlain(parts, b.passage());
            case QuestionBody.QuestionSetBody b -> {
                appendPlain(parts, b.passage());
                for (QuestionBody.ChildQuestion child : b.children()) {
                    appendPlain(parts, child.content());
                    appendBody(parts, child.body());
                }
            }
            case QuestionBody.TrueFalseBody b -> b.statements().forEach(s -> appendPlain(parts, s.content()));
            case QuestionBody.MatchingBody b -> {
                b.left().forEach(s -> appendPlain(parts, s.content()));
                b.right().forEach(s -> appendPlain(parts, s.content()));
            }
            case QuestionBody.EssayBody b -> appendPlain(parts, b.gradingGuide());
        }
    }

    private static void appendPlain(List<String> parts, String html) {
        String text = QuestionHtmlSanitizer.textOf(html);
        if (!text.isBlank()) {
            parts.add(text);
        }
    }

}
