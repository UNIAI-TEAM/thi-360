package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Nơi đang dùng câu: các bài có khối lấy câu làm nguồn (exam, qua " +
        "QuestionUsageProvider — rỗng tới BE-6) cộng các dòng sổ đã dùng (ledger, D18)")
public record QuestionUsage(
        @Schema(description = "Các bài/khối đang lấy câu làm nguồn (module exam)",
                requiredMode = Schema.RequiredMode.REQUIRED) List<ExamSourceRef> sourceBlocks,
        @Schema(description = "Các dòng sổ đã dùng (xem trước, công bố, lượt thi)",
                requiredMode = Schema.RequiredMode.REQUIRED) List<LedgerRef> usedIn) {

    @Schema(description = "Một bài có câu trong nguồn của một khối")
    public record ExamSourceRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID examId) {}

    @Schema(description = "Một dòng sổ đã dùng")
    public record LedgerRef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.UUID examId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.time.Instant firstAt) {}
}
