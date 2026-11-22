package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả thu hồi đợt giao bài thi cho lớp (UNI-2774)")
public record RevokeClassExamAssignmentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClassExamAssignmentItem assignment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsRevoked) {}
