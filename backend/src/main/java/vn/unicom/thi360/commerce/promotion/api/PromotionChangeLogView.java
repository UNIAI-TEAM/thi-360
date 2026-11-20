package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.PromotionChangeAction;
import vn.unicom.thi360.commerce.promotion.domain.PromotionFieldChange;

@Schema(description = "Bản ghi lịch sử thay đổi khuyến mãi (BS §10)")
public record PromotionChangeLogView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PromotionChangeAction action,
        @Schema(nullable = true) UUID actorId,
        @Schema(nullable = true) String actorUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PromotionFieldChange> changes
) {
}
