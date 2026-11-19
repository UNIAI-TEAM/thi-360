package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PriceResult;

@Schema(description = "Thẻ sản phẩm ở danh mục (TM 10.2). Giá khuyến mãi tính lúc đọc, phần còn lại dựng sẵn.")
public record ProductCard(
        @Schema(description = "Mã sản phẩm, cũng là mã bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Tóm tắt; trống nếu quản trị viên chưa nhập", nullable = true) String summary,
        @Schema(description = "Đường dẫn ảnh bìa; trống thì dùng ảnh mặc định", nullable = true) String coverUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamKindRef examKind,
        @Schema(description = "Hình thức (E4): FULL, PART hay SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        String format,
        @Schema(description = "Tên phần (PART) hoặc môn (SUBJECT) ghi trên thẻ; null với FULL", nullable = true)
        LocalizedName scopeLabel,
        @Schema(description = "STANDARD hoặc REVIEW; bài ôn (REVIEW) có nhãn riêng (BS 2)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String buildMode,
        @Schema(description = "Số câu phải làm; khoảng khi các đường khác nhau", nullable = true) IntRange questionCount,
        @Schema(description = "Thời lượng, phút; khoảng khi các đường khác nhau", nullable = true) IntRange durationMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
        @Schema(description = "Giá gốc, đồng", requiredMode = Schema.RequiredMode.REQUIRED) long listPrice,
        @Schema(description = "Giá phải trả sau khuyến mãi tự động, đồng", requiredMode = Schema.RequiredMode.REQUIRED)
        long price,
        @Schema(description = "Khuyến mãi tự động đang áp; null nếu không có", nullable = true) PromotionBadge promotion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo) {

    /** The same card with the price computed for now. */
    public ProductCard withPrice(PriceResult result) {
        PromotionBadge badge = result.promotion() == null
                ? null : new PromotionBadge(result.promotion().name(), result.promotion().endsAt());
        return new ProductCard(code, name, summary, coverUrl, examKind, format, scopeLabel, buildMode, questionCount,
                durationMinutes, attemptsGranted, result.listPrice(), result.price(), badge, versionNo);
    }
}
