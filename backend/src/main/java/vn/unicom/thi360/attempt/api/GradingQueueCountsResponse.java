package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GradingQueueCountsResponse", description = "Số đếm câu tự luận theo từng trạng thái hàng chờ chấm (BS §11)")
public record GradingQueueCountsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long waiting,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inProgress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long done,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total) {}
