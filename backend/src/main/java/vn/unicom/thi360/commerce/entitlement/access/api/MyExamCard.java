package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.domain.MyExamAction;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;

@Schema(description = "Thẻ một bài thi trong \"Bài thi của tôi\" (TM 12): một quyền dùng nguồn mua, hoặc một đơn đã thu "
        + "tiền chưa cấp quyền (state CHECKING, không có entitlementId)")
public record MyExamCard(
        @Schema(description = "Quyền dùng; trống ở thẻ \"Đang kiểm tra\" của đơn chưa cấp quyền", nullable = true)
        UUID entitlementId,
        @Schema(description = "Mã sản phẩm, cũng là mã bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên sản phẩm", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamKindRef examKind,
        @Schema(description = "Hình thức (E4): FULL, PART hay SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        String format,
        @Schema(description = "Tên phần (PART) hoặc môn (SUBJECT); null với FULL", nullable = true)
        LocalizedName scopeLabel,
        @Schema(description = "STANDARD hoặc REVIEW; bài ôn (REVIEW) có nhãn riêng (BS 2)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String buildMode,
        @Schema(description = "Đường dẫn ảnh bìa; trống thì dùng ảnh mặc định", nullable = true) String coverUrl,
        @Schema(description = "Số câu học viên làm theo các đường đã mua; khoảng khi các đường khác nhau", nullable = true)
        IntRange questionCount,
        @Schema(description = "Thời lượng một lượt, phút, theo các đường đã mua; khoảng khi các đường khác nhau",
                nullable = true)
        IntRange durationMinutes,
        @Schema(description = "Lượt còn dùng được, từ quyền đã cấp (không từ sản phẩm đang bán)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptsLeft,
        @Schema(description = "Tổng lượt của quyền sau khi trừ lượt đã thu hồi; 0 ở thẻ chưa có quyền",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptsTotal,
        @Schema(description = "Trạng thái bán của sản phẩm; ON_SALE thì có nút mua", requiredMode = Schema.RequiredMode.REQUIRED)
        SaleState saleState,
        @Schema(description = "Tình trạng: IN_PROGRESS (Đang làm), CAN_START (Có thể bắt đầu), EXHAUSTED (Hết lượt), "
                + "CHECKING (Đang kiểm tra)", requiredMode = Schema.RequiredMode.REQUIRED)
        ExamAccessState state,
        @Schema(description = "Lượt đang mở (kể cả STARTING)", nullable = true) UUID activeAttemptId,
        @Schema(description = "Trạng thái của lượt đang mở", nullable = true) OpenAttemptState activeAttemptState,
        @Schema(description = "Lượt mới nhất đã có kết quả, đích của nút VIEW_RESULT", nullable = true)
        UUID resultAttemptId,
        @Schema(description = "Có đơn đã thu tiền chưa cấp xong cho bài này", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasPendingFulfillment,
        @Schema(description = "Mã đơn đã thu tiền chưa cấp xong", nullable = true) String pendingOrderCode,
        @Schema(description = "Nút của thẻ theo TM 12, nút chính đứng đầu", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MyExamAction> actions) {
}
