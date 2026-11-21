package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CompensateAttemptResponse", description = "Kết quả cấp bù 1 lượt cho lượt thi lỗi hệ thống")
public record CompensateAttemptResponse(
        UUID attemptId,
        UUID entitlementId,
        UUID grantId,
        boolean compensated) {}
