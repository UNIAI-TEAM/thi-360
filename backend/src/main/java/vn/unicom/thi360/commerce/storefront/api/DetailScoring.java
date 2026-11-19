package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Cách tính giờ và thang điểm của bài thi")
public record DetailScoring(
        @Schema(description = "PER_PART: tính giờ từng phần; SHARED: một đồng hồ chung", nullable = true) String timing,
        @Schema(description = "Điểm tối đa của bài", nullable = true) BigDecimal maxScore,
        @Schema(description = "Mốc tham chiếu của thang điểm (nếu có)", nullable = true) BigDecimal referenceMax,
        @Schema(description = "Cách quy điểm luyện tập, ví dụ LINEAR", nullable = true) String practiceScoring) {
}
