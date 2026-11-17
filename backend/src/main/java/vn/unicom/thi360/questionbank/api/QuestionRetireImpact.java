package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ảnh hưởng khi ngừng câu: các bài bị ảnh hưởng (exam) và dòng sổ đã dùng")
public record QuestionRetireImpact(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionUsage.ExamSourceRef> affectedBlocks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionUsage.LedgerRef> usedIn) {}
