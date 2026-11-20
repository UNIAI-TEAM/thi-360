package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Danh sách thẻ \"Bài thi của tôi\": thẻ \"Đang kiểm tra\" trước, rồi các quyền dùng mới nhất trước")
public record MyExamsResponse(
        @Schema(description = "Rỗng khi tài khoản chưa có bài nào", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MyExamCard> items) {
}
