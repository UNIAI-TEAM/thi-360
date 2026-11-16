package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.unicom.thi360.questionbank.domain.QuestionBody.ChildQuestion;
import vn.unicom.thi360.questionbank.domain.QuestionBody.ChoiceOption;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Chuẩn hóa tìm kiếm tiếng Việt (D16): bỏ dấu, {@code đ} → {@code d}, chữ thường, gộp khoảng
 * trắng; {@code search_text} gom chữ từ mã, đề bài và phần chữ của thân (HTML đã tách).
 */
class QuestionSearchNormalizerTests {

    @Test
    void stripsDiacriticsAndLowercases() {
        assertEquals("toan", QuestionSearchNormalizer.normalize("Toán"));
        assertEquals("dao dong", QuestionSearchNormalizer.normalize("dao động"));
        assertEquals("truong dai hoc", QuestionSearchNormalizer.normalize("Trường Đại học"));
    }

    @Test
    void dWithStrokeBecomesD() {
        assertEquals("d", QuestionSearchNormalizer.normalize("đ"));
        assertEquals("d", QuestionSearchNormalizer.normalize("Đ"));
        assertEquals("dieu kien", QuestionSearchNormalizer.normalize("Điều kiện"));
    }

    @Test
    void collapsesWhitespace() {
        assertEquals("a b c", QuestionSearchNormalizer.normalize("  a\t b\n  c  "));
    }

    @Test
    void nullIsEmpty() {
        assertEquals("", QuestionSearchNormalizer.normalize(null));
    }

    @Test
    void searchTextFindsAsciiEquivalent() {
        // "toan" khớp nội dung "Toán", "dao dong" khớp "dao động" (nghiệm thu BE-2a).
        QuestionBody body = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>dao động điều hòa</p>", true),
            new ChoiceOption("a2", "<p>chuyển động tròn</p>", false)), true);
        String search = QuestionSearchNormalizer.searchText("CH-0001", "<p>Hàm số Toán học</p>", body);
        assertTrue(search.contains("toan"));
        assertTrue(search.contains("dao dong"));
        assertTrue(search.contains("ch-0001"));
    }

    @Test
    void searchTextWalksChildren() {
        QuestionBody body = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>Đoạn văn đọc hiểu</p>",
            List.of(
                new ChildQuestion(QuestionType.SHORT_ANSWER, "<p>Câu hỏi con</p>",
                    new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of("4"), null),
                    BigDecimal.ONE, null, null, List.of())));
        String search = QuestionSearchNormalizer.searchText("CH-2", null, body);
        assertTrue(search.contains("doan van doc hieu"));
        assertTrue(search.contains("cau hoi con"));
    }

    @Test
    void contentTextIsPlain() {
        String text = QuestionSearchNormalizer.contentText(
            "<p>Hàm <strong>số</strong> bậc nhất</p>", null);
        assertEquals("Hàm số bậc nhất", text);
    }
}
