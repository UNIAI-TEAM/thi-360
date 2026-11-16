package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Nhân bản bài thi thành bản Nháp mới (POST /exams/{id}/duplicate, spec 5.2)")
public record DuplicateExamRequest(
        @Schema(description = "Tên bài mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String name
) {
}
