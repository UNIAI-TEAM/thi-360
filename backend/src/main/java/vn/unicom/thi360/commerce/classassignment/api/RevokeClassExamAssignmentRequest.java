package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu thu hồi đợt giao bài thi cho lớp (UNI-2774)")
public record RevokeClassExamAssignmentRequest(
        @Size(max = 500, message = "{validation.classExamAssignment.revokeReason.size}")
        @Schema(maxLength = 500, types = {"string", "null"})
        String reason) {}
