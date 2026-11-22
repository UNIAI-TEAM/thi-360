package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả giao bài thi cho lớp học (UNI-2774)")
public record AssignClassExamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClassExamAssignmentItem> assignments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalGrantsCreated) {}
