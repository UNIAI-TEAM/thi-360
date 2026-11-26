package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.application.SelfRegistrationService;

@RestController
@RequestMapping(value = "/api/v1/auth/mobile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Xác thực và phiên đăng nhập")
public class MobileAuthController {

    private final AuthService authService;
    private final SelfRegistrationService selfRegistrationService;

    public MobileAuthController(
            AuthService authService,
            SelfRegistrationService selfRegistrationService) {
        this.authService = authService;
        this.selfRegistrationService = selfRegistrationService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "mobileLogin",
            summary = "Đăng nhập trên ứng dụng di động (chỉ tài khoản học viên)",
            description = "Công khai. Trả cặp token trong thân phản hồi và không đặt cookie.")
    @ApiResponse(responseCode = "200", description = "Đăng nhập thành công")
    public MobileTokenResponse login(
            @Valid @RequestBody MobileLoginRequest request,
            HttpServletRequest httpRequest) {
        AuthService.MobileLoginResult result = authService.loginMobile(
                request.identifier(),
                request.password(),
                RequestLocaleResolver.resolveCookieOrAcceptLanguage(httpRequest));
        return toResponse(result);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "mobileRefresh",
            summary = "Làm mới phiên đăng nhập trên ứng dụng di động")
    @ApiResponse(responseCode = "200", description = "Làm mới phiên thành công")
    public MobileTokenResponse refresh(@Valid @RequestBody MobileRefreshRequest request) {
        return toResponse(authService.refreshMobile(request.refreshToken()));
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "mobileLogout",
            summary = "Đăng xuất phiên hiện tại trên ứng dụng di động")
    @ApiResponse(responseCode = "204", description = "Đã thu hồi phiên (idempotent)")
    public void logout(@Valid @RequestBody MobileLogoutRequest request) {
        authService.logoutMobile(request.refreshToken());
    }

    @PostMapping(value = "/register/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "mobileVerifyRegistration",
            summary = "Bước 2 tự đăng ký học viên trên ứng dụng di động: xác minh OTP, tạo tài khoản và cấp token",
            description = "Công khai. Tạo tài khoản đã xác minh email và trả token trong thân phản hồi, không đặt cookie.")
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản học viên và mở phiên di động")
    @ApiResponse(responseCode = "409", description = "Trùng tên đăng nhập hoặc email (USERNAME_TAKEN, EMAIL_TAKEN)")
    @ApiResponse(
            responseCode = "422",
            description = "EMAIL_CODE_INVALID (kèm remainingAttempts) hoặc EMAIL_CODE_EXPIRED",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public MobileTokenResponse verifyRegistration(@Valid @RequestBody RegisterVerifyRequest request) {
        return toResponse(selfRegistrationService.verifyAndRegisterMobile(request.registrationId(), request.code()));
    }

    private static MobileTokenResponse toResponse(AuthService.MobileLoginResult result) {
        return new MobileTokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.accessExpiresIn(),
                result.refreshExpiresIn(),
                result.mustChangePassword());
    }
}
