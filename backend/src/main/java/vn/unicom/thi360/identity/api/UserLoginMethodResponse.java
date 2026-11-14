package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.IdentityProvider;

@Schema(description = "Phương thức đăng nhập đã liên kết của tài khoản")
public record UserLoginMethodResponse(
        @Schema(description = "ID liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nhà cung cấp xác thực (LOCAL, GOOGLE)", requiredMode = Schema.RequiredMode.REQUIRED)
        IdentityProvider provider,
        @Schema(description = "Email tại thời điểm liên kết")
        String emailAtLink,
        @Schema(description = "Thời điểm liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant linkedAt
) {
}
