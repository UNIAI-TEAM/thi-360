package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu tạo tài khoản mới")
public record CreateUserRequest(
        @Schema(
                description = "Tên đăng nhập (4-30 ký tự, a-z0-9._@-, bắt đầu bằng chữ hoặc số)",
                example = "nguyen.van.an",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(
                description = "Mật khẩu ban đầu đạt quy tắc bảo mật",
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password,
        @Schema(
                description = "Họ và tên người dùng",
                example = "Nguyễn Văn An",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 200, message = "Họ và tên tối đa 200 ký tự")
        String fullName,
        @Schema(description = "Email liên hệ", example = "an@thi360.vn")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,
        @Schema(description = "Số điện thoại", example = "0901234567")
        @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
        String phone,
        @Schema(description = "Danh sách ID vai trò (mặc định vai trò STUDENT; gán vai trò khác cần USER.ASSIGN)")
        List<UUID> roleIds,
        @Schema(description = "Danh sách ID lớp học (tạm thời chưa hỗ trợ trước T10)")
        List<UUID> classIds,
        @Schema(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập đầu (mặc định false)")
        Boolean mustChangePassword
) {
    @Override
    public String toString() {
        return "CreateUserRequest["
                + "username=" + username
                + ", password=" + (password == null ? "null" : "[REDACTED]")
                + ", fullName=" + fullName
                + ", email=" + email
                + ", phone=" + phone
                + ", roleIds=" + roleIds
                + ", classIds=" + classIds
                + ", mustChangePassword=" + mustChangePassword
                + ']';
    }
}
