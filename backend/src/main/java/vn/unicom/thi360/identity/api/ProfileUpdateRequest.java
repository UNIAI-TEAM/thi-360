package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sửa hồ sơ: trường không gửi (null) giữ nguyên. Tên đăng nhập không sửa được nên không có trong yêu cầu; trường
 * lạ trong thân bị bỏ qua.
 */
@Schema(description = "Yêu cầu sửa hồ sơ; trường không gửi thì giữ nguyên")
public record ProfileUpdateRequest(
        @Schema(description = "Họ và tên, bắt buộc khi gửi, tối đa 200 ký tự", maxLength = 200)
        String fullName,

        @Schema(description = "Số điện thoại Việt Nam 10 chữ số bắt đầu bằng 0; chuỗi rỗng để xóa số",
                example = "0912345678")
        String phone
) {
}
