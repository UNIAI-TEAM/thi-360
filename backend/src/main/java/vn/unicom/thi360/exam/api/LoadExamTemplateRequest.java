package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Nạp lại cấu trúc từ nguyên mẫu (POST /exams/{id}/template, spec 5.2)")
public record LoadExamTemplateRequest(
        @Schema(description = "Mã nguyên mẫu; phải khớp kỳ thi + phạm vi (+ phần/môn) của bài",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String templateCode,
        @Schema(description = "Bắt buộc true khi cấu trúc hiện tại đã sửa; thiếu → 409 "
                + "EXAM_STRUCTURE_REPLACE_CONFIRM")
        Boolean confirmReplace,
        @Schema(description = "Cách xử lý tổng quan đã sửa",
                allowableValues = {"USE_TEMPLATE", "KEEP_EDITED"})
        String overviewPolicy,
        @Schema(description = "Revision đang xem; lệch → 412 EXAM_REVISION_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long expectedRevision
) {
}
