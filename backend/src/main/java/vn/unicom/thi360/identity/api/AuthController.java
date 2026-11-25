package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.application.PasswordForgotDispatcher;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.identity.application.SelfRegistrationService;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.web.AppMessages;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Xác thực và phiên đăng nhập")
public class AuthController {

    private final AuthService authService;
    private final SelfRegistrationService selfRegistrationService;

    private final PasswordForgotDispatcher passwordForgotDispatcher;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;
    private final AppMessages appMessages;

    public AuthController(
            AuthService authService,
            SelfRegistrationService selfRegistrationService,
            PasswordForgotDispatcher passwordForgotDispatcher,
            PasswordResetService passwordResetService,
            ClientIpResolver clientIpResolver,
            AppMessages appMessages) {
        this.authService = authService;
        this.selfRegistrationService = selfRegistrationService;
        this.passwordForgotDispatcher = passwordForgotDispatcher;
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
        this.appMessages = appMessages;
    }

    @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đăng nhập bằng tên đăng nhập hoặc email")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        // Đọc cookie ngôn ngữ trực tiếp từ request để không thêm tham số vào OpenAPI.
        AuthService.LoginResult result = authService.login(
                request.identifier(),
                request.password(),
                request.callbackUrl(),
                RequestLocaleResolver.readCookie(httpRequest, AuthService.LOCALE_COOKIE),
                response);
        return new LoginResponse(result.redirectTo(), result.mustChangePassword());
    }

    @PostMapping(value = "/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "startRegistration",
            summary = "Bước 1 tự đăng ký học viên: kiểm tra thông tin và gửi mã OTP 6 chữ số qua email",
            description = "Công khai. Chưa tạo tài khoản trong identity hay Keycloak; xếp thư mã OTP xác minh email.")
    @ApiResponse(responseCode = "200", description = "Đã tạo phiên đăng ký chờ và xếp thư mã OTP")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "409", description = "Trùng tên đăng nhập hoặc email (USERNAME_TAKEN, EMAIL_TAKEN)")
    @ApiResponse(
            responseCode = "429",
            description = "Gửi quá nhanh hoặc quá giới hạn mỗi ngày: kèm Retry-After và retryAfter",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public RegisterStartResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        return selfRegistrationService.startRegistration(
                request, RequestLocaleResolver.resolveCookieOrAcceptLanguage(httpRequest));
    }

    @PostMapping(value = "/auth/register/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "resendRegistrationCode",
            summary = "Gửi lại mã OTP 6 chữ số cho phiên tự đăng ký đang chờ")
    @ApiResponse(responseCode = "200", description = "Đã cấp mã OTP mới và xếp thư")
    @ApiResponse(
            responseCode = "422",
            description = "Phiên đăng ký đã hết hạn hoặc hoàn tất (EMAIL_CODE_EXPIRED)",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Chưa đủ 60 giây hoặc quá 5 mã trong 24 giờ: kèm Retry-After và retryAfter",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public RegisterStartResponse resendRegistrationCode(
            @Valid @RequestBody RegisterResendRequest request,
            HttpServletRequest httpRequest) {
        return selfRegistrationService.resendCode(
                request.registrationId(), RequestLocaleResolver.resolveCookieOrAcceptLanguage(httpRequest));
    }

    @PostMapping(value = "/auth/register/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "verifyRegistration",
            summary = "Bước 2 tự đăng ký học viên: xác nhận mã OTP email, tạo tài khoản và mở phiên đăng nhập",
            description = "Công khai. Tạo tài khoản đã xác minh email trong identity và Keycloak, đặt cookie HttpOnly.")
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản học viên và mở phiên đăng nhập")
    @ApiResponse(responseCode = "409", description = "Trùng tên đăng nhập hoặc email (USERNAME_TAKEN, EMAIL_TAKEN)")
    @ApiResponse(
            responseCode = "422",
            description = "EMAIL_CODE_INVALID (kèm remainingAttempts) hoặc EMAIL_CODE_EXPIRED",
            content = @Content(schema = @Schema(implementation = EmailCodeErrorResponse.class)))
    public RegisterResponse verifyRegistration(
            @Valid @RequestBody RegisterVerifyRequest request,
            HttpServletResponse response) {
        String redirectTo = selfRegistrationService.verifyAndRegister(
                request.registrationId(), request.code(), response);
        return new RegisterResponse(redirectTo);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Làm mới phiên đăng nhập")
    public LoginResponse refresh(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.refresh(refreshToken, response);
        return new LoginResponse(result.redirectTo(), result.mustChangePassword());
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Đăng xuất khỏi phiên hiện tại")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @CookieValue(name = AuthCookieService.ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            HttpServletResponse response) {
        authService.logout(refreshToken, accessToken, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/auth/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đổi mật khẩu tài khoản hiện tại")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.oldPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/auth/password/forgot", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "forgotPassword", summary = "Yêu cầu liên kết đặt lại mật khẩu (công khai, luôn 202 cùng một thân)")
    public ResponseEntity<PasswordForgotResponse> forgot(
            @Valid @RequestBody PasswordForgotRequest request, HttpServletRequest httpRequest) {
        // Chỉ ghi nhận việc rồi trả ngay: bộ lọc giao việc sau khi 202 đã commit, nên thời gian phản hồi không lộ email có hay không.
        passwordForgotDispatcher.dispatch(httpRequest, request.email(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted()
                .body(new PasswordForgotResponse(appMessages.get("PASSWORD_FORGOT_ACCEPTED")));
    }

    @PostMapping(value = "/auth/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "resetPasswordByToken", summary = "Đặt lại mật khẩu bằng mã trong thư (công khai, không đăng nhập)")
    public void reset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại")
    public MeResponse me() {
        AuthService.MeResult result = authService.me();
        return new MeResponse(
                result.id(),
                result.username(),
                result.fullName(),
                result.realm(),
                result.access(),
                result.permissions(),
                result.homeUrl());
    }
}
