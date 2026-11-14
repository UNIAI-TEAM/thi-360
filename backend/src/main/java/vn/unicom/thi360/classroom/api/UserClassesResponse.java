package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Danh sách lớp học hiện tại và lịch sử tham gia/chuyển lớp của tài khoản")
public record UserClassesResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Danh sách lớp học hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserCurrentClassResponse> currentClasses,
        @Schema(description = "Lịch sử tham gia, rời và chuyển lớp (mới nhất trước)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserClassHistoryResponse> history
) {
}
