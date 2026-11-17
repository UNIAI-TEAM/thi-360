package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "QuestionbankQuestionUsage", description = "Nơi đang dùng câu: các bài có khối lấy câu làm nguồn và các dòng sổ đã dùng (ledger, D18)")
public record QuestionUsage(
        @Schema(description = "Các bài/khối đang lấy câu làm nguồn (module exam)",
                requiredMode = Schema.RequiredMode.REQUIRED) List<ExamSourceRef> sourceBlocks,
        @Schema(description = "Các dòng sổ đã dùng (xem trước, công bố, lượt thi)",
                requiredMode = Schema.RequiredMode.REQUIRED) List<LedgerRef> usedIn) {

    @Schema(name = "QuestionbankExamSourceRef", description = "Một khối bài thi có câu trong nguồn")
    public record ExamSourceRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID examId,
            String examCode, String examName, String examStatus,
            String partId, String partName, String optionId, String optionCode, String optionName,
            String sectionId, String blockId, String blockName) {}

    @Schema(description = "Một dòng sổ đã dùng")
    public record LedgerRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID examId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.time.Instant firstAt) {}
}
