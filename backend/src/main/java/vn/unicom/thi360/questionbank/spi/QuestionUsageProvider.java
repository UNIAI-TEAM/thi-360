package vn.unicom.thi360.questionbank.spi;

import java.util.List;
import java.util.UUID;

/**
 * Cổng ngược: {@code exam} cài interface này (cùng cách {@code CatalogUsageProvider}) để
 * {@code questionbank} hỏi câu hỏi đang nằm trong nguồn của khối nào, ở bài nào — phục vụ màn
 * nơi đang dùng và ảnh hưởng khi ngừng câu (spec mục 3).
 *
 * <p>Each result identifies one working exam block whose source includes the question.
 */
public interface QuestionUsageProvider {

    /** Working exam blocks whose source contains the serving question. */
    List<ExamUsageRef> examUsagesOf(UUID questionId);

    @io.swagger.v3.oas.annotations.media.Schema(name = "QuestionbankExamUsageRef")
    record ExamUsageRef(
            UUID examId, String examCode, String examName, String examStatus,
            String partId, String partName, String optionId, String optionCode, String optionName,
            String sectionId, String blockId, String blockName) {}
}
