package vn.unicom.thi360.questionbank.domain;

import java.util.List;
import java.util.UUID;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đơn vị đếm vào ma trận của một câu hỏi (spec 4.3, D22), hàm {@code units(q)} dùng chung cho
 * đếm nguồn, kiểm tra và sinh đề — BE-5b phụ thuộc hàm này.
 *
 * <p>Quy tắc: Bộ câu hỏi đếm bằng số câu con; Điền khuyết đếm bằng số vị trí; mọi dạng khác
 * đếm một (kể cả Đúng/Sai nhiều mệnh đề, Ghép đôi nhiều cặp). Dạng trả lời của một đơn vị là
 * mã dạng câu của nó: câu độc lập lấy dạng của câu, câu con lấy dạng của câu con, mỗi vị trí
 * của Điền khuyết là {@code CLOZE} (D22).
 *
 * <p>Mỗi đơn vị mang định danh (questionId, versionNo) của bản ghi câu nó thuộc về: câu con
 * của bộ là dòng {@code question} riêng có {@code question_version} cùng {@code version_no}
 * với bộ (spec 4.1), nên sổ đã dùng ({@code question_usage_ledger}) ghi được từng câu con.
 */
public final class QuestionUnits {

    private QuestionUnits() {}

    /**
     * Một đơn vị đếm của câu.
     *
     * @param index thứ tự đơn vị trong câu (0-based): chỉ số câu con / vị trí; 0 với câu đơn
     * @param questionId id bản ghi câu của đơn vị: id câu con với đơn vị trong bộ, id chính câu
     *                   với các dạng khác; {@code null} khi câu con chưa được lưu (chưa có id)
     * @param versionNo số phiên bản phục vụ: của bản ghi câu đơn / vị trí Điền khuyết; của <b>bộ
     *                  </b> với câu con (snapshot câu con dùng chung version_no với bộ);
     *                  {@code null} khi chưa có bản phục vụ
     * @param reference tham chiếu phụ trong thân câu: id ô trống ({@code blanks[i].id}) với vị
     *                  trí Điền khuyết; {@code null} với câu đơn và câu con (đã có questionId)
     * @param answerType dạng trả lời của đơn vị theo D22
     */
    public record QuestionUnit(
            int index, UUID questionId, Integer versionNo, String reference, QuestionType answerType) {}

    /**
     * Đơn vị đếm của một phần thân; rỗng khi {@code body} là {@code null}.
     *
     * @param questionId id bản ghi của chính câu (đơn vị câu đơn và vị trí Điền khuyết)
     * @param versionNo số phiên bản đang xét của câu (câu con dùng version_no của bộ)
     * @param body phần thân đã lọc
     */
    public static List<QuestionUnit> units(UUID questionId, Integer versionNo, QuestionBody body) {
        if (body == null) {
            return List.of();
        }
        return switch (body) {
            case QuestionBody.QuestionSetBody set -> {
                var units = new java.util.ArrayList<QuestionUnit>(set.children().size());
                for (int i = 0; i < set.children().size(); i++) {
                    QuestionBody.ChildQuestion child = set.children().get(i);
                    units.add(new QuestionUnit(i, child.id(), versionNo, null, child.type()));
                }
                yield List.copyOf(units);
            }
            case QuestionBody.ClozeBody cloze -> {
                var units = new java.util.ArrayList<QuestionUnit>(cloze.blanks().size());
                for (int i = 0; i < cloze.blanks().size(); i++) {
                    units.add(new QuestionUnit(
                            i, questionId, versionNo, cloze.blanks().get(i).id(), QuestionType.CLOZE));
                }
                yield List.copyOf(units);
            }
            default -> List.of(new QuestionUnit(0, questionId, versionNo, null, body.type()));
        };
    }

    /** Số đơn vị đếm của câu (0 khi {@code body} là {@code null}). */
    public static int count(QuestionBody body) {
        return units(null, null, body).size();
    }
}
