package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PriceResult;

@Schema(description = "Chi tiết sản phẩm (TM 10.1, 10.2). Không có id hay nội dung câu hỏi, đáp án, lời giải.")
public record ProductDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(nullable = true) String summary,
        @Schema(nullable = true) String coverUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamKindRef examKind,
        @Schema(description = "FULL, PART hoặc SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED) String format,
        @Schema(nullable = true) LocalizedName scopeLabel,
        @Schema(description = "STANDARD hoặc REVIEW", requiredMode = Schema.RequiredMode.REQUIRED) String buildMode,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(description = "Giá gốc, đồng", requiredMode = Schema.RequiredMode.REQUIRED) long listPrice,
        @Schema(description = "Giá phải trả sau khuyến mãi tự động, đồng", requiredMode = Schema.RequiredMode.REQUIRED)
        long price,
        @Schema(nullable = true) PromotionBadge promotion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
        @Schema(description = "Mô tả bài thi", nullable = true) String description,
        @Schema(description = "Mục tiêu", nullable = true) String objectives,
        @Schema(description = "Đối tượng phù hợp", nullable = true) String audience,
        @Schema(description = "Các phần theo thứ tự làm bài", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DetailPart> parts,
        @Schema(description = "Các lựa chọn trong phạm vi mua, theo phần", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DetailChoiceGroup> choices,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DetailScoring scoring,
        @Schema(description = "Nhóm tự luận tạm bỏ qua; rỗng nếu không có", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OmittedEssayGroup> omittedEssayGroups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DetailBenefits benefits,
        @Schema(description = "SCORE, SCORE_AND_ANSWERS hoặc FULL_SOLUTION", nullable = true) String resultView) {

    public ProductDetail {
        parts = parts == null ? List.of() : List.copyOf(parts);
        choices = choices == null ? List.of() : List.copyOf(choices);
        omittedEssayGroups = omittedEssayGroups == null ? List.of() : List.copyOf(omittedEssayGroups);
    }

    /** The same detail with the price computed for now. */
    public ProductDetail withPrice(PriceResult result) {
        PromotionBadge badge = result.promotion() == null
                ? null : new PromotionBadge(result.promotion().name(), result.promotion().endsAt());
        return new ProductDetail(code, name, summary, coverUrl, examKind, format, scopeLabel, buildMode, questionCount,
                durationMinutes, maxPoints, attemptsGranted, result.listPrice(), result.price(), badge, versionNo,
                description, objectives, audience, parts, choices, scoring, omittedEssayGroups, benefits, resultView);
    }
}
