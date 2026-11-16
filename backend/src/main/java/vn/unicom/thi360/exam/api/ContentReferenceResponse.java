package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Dữ liệu tham chiếu gộp cho màn Ngân hàng câu hỏi và Bài thi (spec mục 5.3, 9.2)")
public record ContentReferenceResponse(
        @Schema(description = "Các môn đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogOption> subjects,
        @Schema(description = "Các khối lớp đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogOption> grades,
        @Schema(description = "Các mức độ câu hỏi đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogOption> difficulties,
        @Schema(description = "Các nhãn câu hỏi đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogOption> tags,
        @Schema(description = "Các kỳ thi kèm hồ sơ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamKindOption> examKinds,
        @Schema(description = "Các mã tổ hợp đang hiệu lực", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CombinationOption> combinations,
        @Schema(description = "Các năng lực đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CompetencyOption> competencies,
        @Schema(description = "Cây kiến thức đầy đủ", requiredMode = Schema.RequiredMode.REQUIRED)
        List<KnowledgeNode> knowledgeTree,
        @Schema(description = "Các dạng bài đang hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogOption> taskTypes,
        @Schema(description = "Tám dạng câu cố định; luôn đủ 8 kể cả khi chưa có giá trị QUESTION_FORMAT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<QuestionTypeOption> questionTypes
) {
    public ContentReferenceResponse {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        grades = grades == null ? List.of() : List.copyOf(grades);
        difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
        tags = tags == null ? List.of() : List.copyOf(tags);
        examKinds = examKinds == null ? List.of() : List.copyOf(examKinds);
        combinations = combinations == null ? List.of() : List.copyOf(combinations);
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
        knowledgeTree = knowledgeTree == null ? List.of() : List.copyOf(knowledgeTree);
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
        questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
    }
}
