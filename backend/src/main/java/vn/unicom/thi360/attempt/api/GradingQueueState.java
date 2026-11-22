package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GradingQueueState", description = "Trạng thái hàng chờ chấm tự luận (LT §9.2)")
public enum GradingQueueState {
    WAITING,
    IN_PROGRESS,
    DONE
}
