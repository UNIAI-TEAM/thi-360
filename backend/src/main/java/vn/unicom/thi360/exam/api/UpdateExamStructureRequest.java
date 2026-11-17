package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamQuota;

@Schema(description = "Thay toàn bộ cấu trúc bản làm việc (PUT /exams/{id}/structure, spec 9.2)")
public record UpdateExamStructureRequest(
        @Schema(description = "Cấu trúc mới; server giữ giá trị của trường thuộc mẫu "
                + "(answerTypeLocked, templateGroupCode)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<ExamPart> parts,
        List<ExamQuota> examQuotas,
        @Schema(description = "Revision đang xem; lệch → 412 EXAM_REVISION_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long expectedRevision
) {
    public UpdateExamStructureRequest {
        parts = parts == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parts));
        examQuotas = examQuotas == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(examQuotas));
    }

    public UpdateExamStructureRequest(List<ExamPart> parts, Long expectedRevision) {
        this(parts, List.of(), expectedRevision);
    }
}
