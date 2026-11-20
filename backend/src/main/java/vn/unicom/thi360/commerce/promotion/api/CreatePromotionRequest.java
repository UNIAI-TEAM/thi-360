package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;

@Schema(description = "Yêu cầu tạo khuyến mãi tự động hoặc voucher (TM 7.3)")
public record CreatePromotionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.kind.required}")
        PromotionKind kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.promotion.name.notBlank}")
        @Size(max = 200, message = "{validation.promotion.name.size}")
        String name,

        @Schema(nullable = true)
        @Size(max = 2000, message = "{validation.promotion.description.size}")
        String description,

        @Schema(description = "Bắt buộc với VOUCHER (3–50 ký tự); không gửi với AUTO", nullable = true)
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.discountType.required}")
        DiscountType discountType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.discountValue.invalid}")
        @Min(value = 1, message = "{validation.promotion.discountValue.invalid}")
        Long discountValue,

        @Schema(nullable = true)
        Long maxDiscountVnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.startsAt.required}")
        Instant startsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.endsAt.required}")
        Instant endsAt,

        @Schema(nullable = true)
        Integer totalLimit,

        @Schema(nullable = true)
        Integer perUserLimit,

        @Schema(nullable = true)
        Boolean combinable,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.promotion.scope.required}")
        PromotionScope scope,

        @Schema(nullable = true)
        List<UUID> examKindIds,

        @Schema(nullable = true)
        List<UUID> examIds,

        @Schema(nullable = true)
        PromotionAudienceType audience,

        @Schema(nullable = true)
        List<UUID> audienceClassIds,

        @Schema(nullable = true)
        List<UUID> audienceUserIds,

        @Schema(description = "Mặc định ACTIVE nếu không truyền", nullable = true)
        PromotionStatus status
) {
}
