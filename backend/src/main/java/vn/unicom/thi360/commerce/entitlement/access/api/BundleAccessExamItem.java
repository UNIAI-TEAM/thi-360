package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;

@Schema(description = "Trạng thái quyền dùng của một bài thi thành phần khi hỏi theo mã bộ đề (TM 16.4)")
public record BundleAccessExamItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamAccessState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsLeft,
        @Schema(types = {"string", "null"}) UUID entitlementId,
        @Schema(types = {"string", "null"}) UUID activeAttemptId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasPendingFulfillment,
        @Schema(types = {"string", "null"}) String pendingOrderCode) {
}
