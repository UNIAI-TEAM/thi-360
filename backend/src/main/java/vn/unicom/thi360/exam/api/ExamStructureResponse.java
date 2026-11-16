package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;

@Schema(description = "Cấu trúc bản làm việc của bài thi (GET/PUT /exams/{id}/structure)")
public record ExamStructureResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamPart> parts,
        @Schema(description = "Revision của bài thi — gửi lại trong expectedRevision khi PUT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long revision
) {
    public ExamStructureResponse {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
