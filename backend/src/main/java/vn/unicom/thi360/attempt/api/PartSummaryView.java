package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;

@Schema(description = "Tóm tắt trạng thái và mốc thời gian của một phần thi (LT §6.3)")
public record PartSummaryView(
        @Schema(description = "Id phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID partId,
        @Schema(description = "Mã phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String partCode,
        @Schema(description = "Tên phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String partName,
        @Schema(description = "Thứ tự phần thi (bắt đầu từ 0)", requiredMode = Schema.RequiredMode.REQUIRED)
        int ord,
        @Schema(description = "Trạng thái phần thi", requiredMode = Schema.RequiredMode.REQUIRED)
        PartState state,
        @Schema(description = "Thời điểm bắt đầu làm phần", nullable = true)
        Instant startedAt,
        @Schema(description = "Hạn kết thúc phần", nullable = true)
        Instant deadlineAt,
        @Schema(description = "Thời điểm phần đã đóng", nullable = true)
        Instant closedAt,
        @Schema(description = "Lý do đóng hoặc bỏ qua phần", nullable = true)
        PartCloseReason closeReason,
        @Schema(description = "Hạn chờ chọn phần kế tiếp nếu ở trạng thái AWAITING_CHOICE", nullable = true)
        Instant choiceDeadlineAt) {

    public static PartSummaryView from(AttemptPart part) {
        return new PartSummaryView(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getOrd(),
                part.getState(),
                part.getStartedAt(),
                part.getDeadlineAt(),
                part.getClosedAt(),
                part.getCloseReason(),
                part.getChoiceDeadlineAt());
    }
}
