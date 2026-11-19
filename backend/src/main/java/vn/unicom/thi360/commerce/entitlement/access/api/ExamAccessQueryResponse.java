package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Quyền dùng theo từng mã đã hỏi")
public record ExamAccessQueryResponse(
        @Schema(description = "Một dòng cho mỗi mã khác nhau, theo thứ tự gửi lên", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExamAccessItem> items) {
}
