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

@Schema(description = "Thẻ một bài thi trong \"Bài thi của tôi\" (TM 12, 16.4, UNI-2774): một quyền dùng nguồn mua hoặc lớp học, "
        + "hoặc một đơn đã thu tiền chưa cấp quyền (state CHECKING, không có entitlementId)")
public record MyExamCard(
        @Schema(description = "Quyền dùng; trống ở thẻ \"Đang kiểm tra\" của đơn chưa cấp quyền",
                types = {"string", "null"})
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
        @Schema(description = "Đường dẫn ảnh bìa; trống thì dùng ảnh mặc định", types = {"string", "null"})
        String coverUrl,
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
        @Schema(description = "Lượt đang mở (kể cả STARTING)", types = {"string", "null"}) UUID activeAttemptId,
        @Schema(description = "Trạng thái của lượt đang mở", types = {"string", "null"})
        OpenAttemptState activeAttemptState,
        @Schema(description = "Lượt mới nhất đã có kết quả, đích của nút VIEW_RESULT", types = {"string", "null"})
        UUID resultAttemptId,
        @Schema(description = "Có đơn đã thu tiền chưa cấp xong cho bài này", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasPendingFulfillment,
        @Schema(description = "Mã đơn đã thu tiền chưa cấp xong", types = {"string", "null"}) String pendingOrderCode,
        @Schema(description = "Nút của thẻ theo TM 12, nút chính đứng đầu", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MyExamAction> actions,
        @Schema(description = "Các bộ đề đã mua chứa bài thi này (TM 16.4)",
                types = {"array", "null"},
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<MyExamBundleRef> bundles,
        @Schema(description = "Nguồn quyền dùng: PURCHASE hoặc CLASS; null ở thẻ đang kiểm tra chưa cấp quyền",
                types = {"string", "null"},
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String entitlementSource,
        @Schema(description = "ID lớp học khi entitlementSource = CLASS",
                types = {"string", "null"},
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID classId,
        @Schema(description = "Tên lớp học khi entitlementSource = CLASS",
                types = {"string", "null"},
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String className) {

    public MyExamCard(
            UUID entitlementId,
            String code,
            String name,
            ExamKindRef examKind,
            String format,
            LocalizedName scopeLabel,
            String buildMode,
            String coverUrl,
            IntRange questionCount,
            IntRange durationMinutes,
            int attemptsLeft,
            int attemptsTotal,
            SaleState saleState,
            ExamAccessState state,
            UUID activeAttemptId,
            OpenAttemptState activeAttemptState,
            UUID resultAttemptId,
            boolean hasPendingFulfillment,
            String pendingOrderCode,
            List<MyExamAction> actions) {
        this(entitlementId, code, name, examKind, format, scopeLabel, buildMode, coverUrl, questionCount,
                durationMinutes, attemptsLeft, attemptsTotal, saleState, state, activeAttemptId, activeAttemptState,
                resultAttemptId, hasPendingFulfillment, pendingOrderCode, actions, List.of(), null, null, null);
    }

    public MyExamCard(
            UUID entitlementId,
            String code,
            String name,
            ExamKindRef examKind,
            String format,
            LocalizedName scopeLabel,
            String buildMode,
            String coverUrl,
            IntRange questionCount,
            IntRange durationMinutes,
            int attemptsLeft,
            int attemptsTotal,
            SaleState saleState,
            ExamAccessState state,
            UUID activeAttemptId,
            OpenAttemptState activeAttemptState,
            UUID resultAttemptId,
            boolean hasPendingFulfillment,
            String pendingOrderCode,
            List<MyExamAction> actions,
            List<MyExamBundleRef> bundles) {
        this(entitlementId, code, name, examKind, format, scopeLabel, buildMode, coverUrl, questionCount,
                durationMinutes, attemptsLeft, attemptsTotal, saleState, state, activeAttemptId, activeAttemptState,
                resultAttemptId, hasPendingFulfillment, pendingOrderCode, actions, bundles, null, null, null);
    }

    public MyExamCard {
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
    }
}
