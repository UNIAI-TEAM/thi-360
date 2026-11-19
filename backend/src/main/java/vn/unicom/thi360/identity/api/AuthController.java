package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.application.SelfRegistrationService;
import vn.unicom.thi360.shared.security.AuthCookieService;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Xác thực và phiên đăng nhập")
public class AuthController {

    private final AuthService authService;
    private final SelfRegistrationService selfRegistrationService;

    public AuthController(AuthService authService, SelfRegistrationService selfRegistrationService) {
        this.authService = authService;
        this.selfRegistrationService = selfRegistrationService;
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
                readCookie(httpRequest, AuthService.LOCALE_COOKIE),
                response);
        return new LoginResponse(result.redirectTo(), result.mustChangePassword());
    }

    @PostMapping(value = "/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Tự đăng ký tài khoản học viên",
            description = "Công khai. Tạo tài khoản và user Keycloak, đặt phiên (cookie HttpOnly), xếp thư mã OTP xác minh email.")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String redirectTo = selfRegistrationService.register(
                request, readCookie(httpRequest, AuthService.LOCALE_COOKIE), response);
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

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
