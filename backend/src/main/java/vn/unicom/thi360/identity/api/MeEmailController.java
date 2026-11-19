package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.EmailChangeService;
import vn.unicom.thi360.identity.application.EmailCodeVerificationService;

/**
 * Xác minh và đổi email của chính tài khoản đang đăng nhập (TM 5.3). Không có id trong đường dẫn: người gọi chỉ
 * tác động được lên tài khoản của mình, nên không gắn {@code @RequirePermission} (plan A7.3).
 */
@RestController
@RequestMapping(value = "/api/v1/me/email", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Me", description = "Tài khoản đang đăng nhập")
public class MeEmailController {

    private final EmailCodeVerificationService verificationService;
    private final EmailChangeService changeService;

    public MeEmailController(EmailCodeVerificationService verificationService, EmailChangeService changeService) {
        this.verificationService = verificationService;
        this.changeService = changeService;
    }

    @PostMapping("/verification")
    @Operation(summary = "Gửi hoặc gửi lại mã xác minh tới email hiện tại")
    @ApiResponse(responseCode = "202", description = "Đã xếp thư mã xác minh")
    @ApiResponse(responseCode = "422", description = "Tài khoản chưa có email (EMAIL_REQUIRED)")
    @ApiResponse(responseCode = "429", description = "Gửi quá nhanh hoặc quá 5 lần mỗi ngày: có Retry-After và retryAfter",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public ResponseEntity<Void> sendVerification() {
        verificationService.sendVerificationCode();
        return ResponseEntity.accepted().build();
    }

    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Xác minh email hiện tại bằng mã OTP")
    @ApiResponse(responseCode = "204", description = "Email đã được xác minh")
    @ApiResponse(responseCode = "422",
            description = "EMAIL_CODE_INVALID (kèm remainingAttempts) hoặc EMAIL_CODE_EXPIRED",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public ResponseEntity<Void> verify(@Valid @RequestBody EmailCodeVerifyRequest request) {
        verificationService.verify(request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Yêu cầu đổi email: gửi mã tới email mới")
    @ApiResponse(responseCode = "202", description = "Đã xếp thư mã xác nhận tới email mới")
    @ApiResponse(responseCode = "409", description = "Email đã thuộc tài khoản khác (EMAIL_TAKEN)")
    @ApiResponse(responseCode = "429", description = "Gửi quá nhanh hoặc quá 5 lần mỗi ngày: có Retry-After và retryAfter",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public ResponseEntity<Void> requestChange(@Valid @RequestBody EmailCodeChangeRequest request) {
        changeService.requestChange(request.newEmail(), request.currentPassword());
        return ResponseEntity.accepted().build();
    }

    @PostMapping(value = "/change/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Xác nhận đổi email bằng mã OTP")
    @ApiResponse(responseCode = "204", description = "Email đã đổi, đã xác minh, đã đồng bộ Keycloak")
    @ApiResponse(responseCode = "409", description = "Email bị tài khoản khác lấy trong lúc chờ (EMAIL_TAKEN)")
    @ApiResponse(responseCode = "422",
            description = "EMAIL_CODE_INVALID (kèm remainingAttempts) hoặc EMAIL_CODE_EXPIRED",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Không đồng bộ được email sang Keycloak; không có thay đổi nào",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public ResponseEntity<Void> confirmChange(@Valid @RequestBody EmailCodeVerifyRequest request) {
        changeService.confirmChange(request.code());
        return ResponseEntity.noContent().build();
    }
}
