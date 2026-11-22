package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.classassignment.domain.ClassExamAssignmentStatus;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;

@Schema(description = "Một đợt giao bài thi cho lớp học (UNI-2774)")
public record ClassExamAssignmentItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID classId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamKindRef examKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String scope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int examVersionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultView,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean autoGrantNewMembers,
        @Schema(types = {"string", "null"}) String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClassExamAssignmentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int grantedMembersCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalAttemptsUsed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalAttemptsLeft,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalAttemptsRevoked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID assignedBy,
        @Schema(types = {"string", "null"}) String assignedByUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant assignedAt,
        @Schema(types = {"string", "null"}) UUID revokedBy,
        @Schema(types = {"string", "null"}) String revokedByUsername,
        @Schema(types = {"string", "null"}) Instant revokedAt,
        @Schema(types = {"string", "null"}) String revokeReason) {}
