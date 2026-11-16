package vn.unicom.thi360.questionbank.domain.importing;

import java.math.BigDecimal;
import java.util.List;

import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Một câu hỏi đọc được từ tệp nhập — đầu vào mà tầng ứng dụng (W-FLOW) ánh xạ lên
 * {@code SaveQuestionRequest}/{@code QuestionInput} rồi qua lộ trình lưu của
 * {@code QuestionService}. Mọi tham chiếu danh mục giữ ở dạng mã cùng giá trị đã nhận diện;
 * HTML đã qua xử lý ô của quyết định 8.4 (chữ thuần được escape + {@code <br>}, ô có thẻ giữ
 * nguyên) nhưng CHƯA lọc an toàn — việc lọc thuộc lộ trình lưu.
 *
 * @param type dạng câu đã nhận diện; {@code null} khi ô {@code type} trống/không đọc được
 * @param sourceCode mã nguồn của dòng (câu con: luôn {@code null} vì bị bỏ qua, quyết định 8.8)
 * @param content đề bài HTML; với dòng bộ là ngữ liệu chung
 * @param solution lời giải (nullable)
 * @param commonMistakes các lỗi sai thường gặp (HTML, có thể rỗng)
 * @param points điểm câu; {@code null} = mặc định 1. Bộ câu hỏi/Điền khuyết để {@code null}
 *               (tính từ thành phần khi lưu)
 * @param body phần thân theo dạng; với {@code QUESTION_SET} luôn chứa
 *             {@code QuestionSetBody(children = List.of())} — câu con thật nằm ở
 *             {@link #children} với phân loại còn là mã, chưa thể dựng {@code ChildQuestion}
 * @param subjectCode giá trị môn đã nhận diện (nullable khi chưa khớp/để trống)
 * @param gradeCode giá trị khối lớp (nullable)
 * @param difficultyCode giá trị mức độ (nullable)
 * @param taskTypeCode giá trị dạng bài (nullable)
 * @param knowledge các nút MAPID đã nhận diện, nút đầu là chính
 * @param tagCodes mã nhãn đã nhận diện
 * @param setCode mã chùm của dòng (dòng bộ và dòng câu con)
 * @param children các câu con đã đọc (chỉ có trên bản nháp của dòng bộ); trên câu con,
 *                 {@code subjectCode} luôn {@code null} (lấy môn của bộ) và {@code gradeCode}/
 *                 {@code difficultyCode}/{@code knowledge} là giá trị ghi đè — {@code null}/
 *                 rỗng nghĩa là kế thừa của bộ (spec 4.6, 9.2)
 */
public record ImportQuestionDraft(
    QuestionType type,
    String sourceCode,
    String content,
    String solution,
    List<String> commonMistakes,
    BigDecimal points,
    QuestionBody body,
    String subjectCode,
    String gradeCode,
    String difficultyCode,
    String taskTypeCode,
    List<ImportKnowledgeRef> knowledge,
    List<String> tagCodes,
    String setCode,
    List<ImportQuestionDraft> children) {

    public ImportQuestionDraft {
        commonMistakes = commonMistakes == null ? List.of() : List.copyOf(commonMistakes);
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
