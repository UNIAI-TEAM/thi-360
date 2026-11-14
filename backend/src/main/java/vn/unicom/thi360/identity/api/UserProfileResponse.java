package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Hồ sơ tài khoản gồm thông tin chi tiết, phương thức đăng nhập, lớp hiện tại và lịch sử lớp")
public record UserProfileResponse(
        @Schema(description = "Thông tin chi tiết tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UserDetailResponse account,
        @Schema(description = "Danh sách phương thức đăng nhập đã liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserLoginMethodResponse> loginMethods,
        @Schema(description = "Danh sách lớp học hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProfileCurrentClassResponse> currentClasses,
        @Schema(description = "Lịch sử tham gia, rời và chuyển lớp (mới nhất trước)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProfileClassHistoryResponse> classHistory
) {
}
