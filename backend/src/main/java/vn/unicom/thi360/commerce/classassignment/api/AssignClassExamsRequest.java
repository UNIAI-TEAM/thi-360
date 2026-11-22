package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu giao một hoặc nhiều bài thi cho lớp học (UNI-2774)")
public record AssignClassExamsRequest(
        @NotEmpty(message = "{validation.classExamAssignment.examIds.notEmpty}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull(message = "{validation.classExamAssignment.examIds.invalidItem}") UUID> examIds,

        @NotNull(message = "{validation.classExamAssignment.attemptsGranted.required}")
        @Min(value = 1, message = "{validation.classExamAssignment.attemptsGranted.range}")
        @Max(value = 100, message = "{validation.classExamAssignment.attemptsGranted.range}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "100")
        Integer attemptsGranted,

        @NotBlank(message = "{validation.classExamAssignment.resultView.required}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"})
        String resultView,

        @Schema(description = "Tự động cấp cho học viên vào lớp sau; mặc định true")
        Boolean autoGrantNewMembers,

        @Size(max = 500, message = "{validation.classExamAssignment.note.size}")
        @Schema(maxLength = 500, types = {"string", "null"})
        String note) {}
