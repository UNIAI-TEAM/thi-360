package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu tự đăng ký (TM 5.1). Quy tắc nghiệp vụ (tên đăng nhập, mật khẩu, email, điều khoản) do
 * {@code SelfRegistrationService} kiểm và trả đủ lỗi theo từng trường trong một lần; ở đây chỉ chặn chuỗi quá dài.
 */
@Schema(description = "Yêu cầu đăng ký tài khoản học viên")
public record RegisterRequest(
        @Schema(
                description = "Tên đăng nhập (4-30 ký tự, a-z0-9._@-, bắt đầu bằng chữ hoặc số)",
                example = "nguyen.van.an",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 256, message = "{validation.username.length}")
        String username,

        @Schema(description = "Họ và tên", example = "Nguyễn Văn An", requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 200, message = "{validation.user.fullName.size}")
        String fullName,

        @Schema(description = "Email, dùng để xác minh và khôi phục tài khoản", example = "an@thi360.vn",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 256, message = "{validation.user.email.size}")
        String email,

        @Schema(description = "Mật khẩu đạt quy tắc bảo mật", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 256, message = "{validation.password.length}")
        String password,

        @Schema(description = "Đã chấp nhận điều khoản sử dụng; phải là true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean acceptTerms,

        @Schema(description = "Phiên bản điều khoản đã hiển thị cho người dùng, lưu đúng giá trị gửi lên",
                example = "2026-11", requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 64, message = "{validation.register.termsVersion.size}")
        String termsVersion,

        @Schema(description = "Đường dẫn chuyển hướng sau đăng ký (tương đối hoặc thuộc tên miền cho phép)",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2048, message = "{validation.register.callbackUrl.size}")
        String callbackUrl
) {
    @Override
    public String toString() {
        return "RegisterRequest["
                + "username=" + username
                + ", fullName=" + fullName
                + ", email=" + email
                + ", password=" + (password == null ? "null" : "[REDACTED]")
                + ", acceptTerms=" + acceptTerms
                + ", termsVersion=" + termsVersion
                + ", callbackUrl=" + callbackUrl
                + ']';
    }
}
