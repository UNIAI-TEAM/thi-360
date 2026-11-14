package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dữ liệu hồ sơ học tập của học viên (từ module thi, rỗng khi chưa kết nối)")
public record StudentLearningResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Cho biết dữ liệu học tập đã sẵn sàng từ module thi hay chưa", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean dataAvailable,
        @Schema(description = "Quyền sử dụng và gói bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> entitlements,
        @Schema(description = "Lịch sử lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> attempts,
        @Schema(description = "Kết quả đánh giá năng lực", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> assessments,
        @Schema(description = "Lịch sử ôn luyện", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> practice,
        @Schema(description = "Yêu cầu hỗ trợ", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> supportRequests
) {
}
