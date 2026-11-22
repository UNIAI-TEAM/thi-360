package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cập nhật cấu hình tự động cấp thành viên mới của đợt giao bài (UNI-2774)")
public record UpdateClassExamAssignmentRequest(
        @NotNull(message = "{validation.classExamAssignment.autoGrantNewMembers.required}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean autoGrantNewMembers) {}
