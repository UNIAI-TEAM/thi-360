package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;

@Schema(description = "Quyền dùng của tài khoản đang đăng nhập với một mã sản phẩm (TM 10.2, 16.4)")
public record ExamAccessItem(
        @Schema(description = "Mã như học viên gửi (đã bỏ khoảng trắng đầu cuối)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Giá trị đầu tiên thỏa theo thứ tự IN_PROGRESS, CAN_START, CHECKING, EXHAUSTED, NONE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ExamAccessState state,
        @Schema(description = "Lượt còn dùng được; 0 khi chưa có quyền dùng, hoặc khi lượt cuối đang được giữ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptsLeft,
        @Schema(description = "Quyền dùng của tài khoản với bài này; trống khi chưa có", types = {"string", "null"})
        UUID entitlementId,
        @Schema(description = "Lượt đang mở (kể cả STARTING); có khi state là IN_PROGRESS", types = {"string", "null"})
        UUID activeAttemptId,
        @Schema(description = "Có đơn đã thu tiền chưa cấp xong cho bài này (kể cả khi vẫn còn lượt dùng)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasPendingFulfillment,
        @Schema(description = "Mã đơn đã thu tiền chưa cấp xong; trống khi không có", types = {"string", "null"})
        String pendingOrderCode,
        @Schema(description = "Trạng thái quyền từng bài khi mã là bộ đề (TM 16.4); null với bài lẻ",
                types = {"array", "null"},
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<BundleAccessExamItem> items) {

    public ExamAccessItem(
            String code,
            ExamAccessState state,
            int attemptsLeft,
            UUID entitlementId,
            UUID activeAttemptId,
            boolean hasPendingFulfillment,
            String pendingOrderCode) {
        this(code, state, attemptsLeft, entitlementId, activeAttemptId, hasPendingFulfillment,
                pendingOrderCode, null);
    }

    public ExamAccessItem {
        items = items == null ? null : List.copyOf(items);
    }
}
