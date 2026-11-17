package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamQuota;
@Schema(description = "Cấu trúc bản làm việc của bài thi (GET/PUT /exams/{id}/structure)")
public record ExamStructureResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamPart> parts,
        @Schema(description = "Revision của bài thi — gửi lại trong expectedRevision khi PUT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long revision,
        List<ExamQuota> examQuotas,
        List<StructureCombination> combinations
) {
    public ExamStructureResponse {
        parts = parts == null ? List.of() : List.copyOf(parts);
        examQuotas = examQuotas == null ? List.of() : List.copyOf(examQuotas);
        combinations = combinations == null ? List.of() : List.copyOf(combinations);
    }

    public ExamStructureResponse(UUID examId, List<ExamPart> parts, long revision) {
        this(examId, parts, revision, List.of(), List.of());
    }
}
