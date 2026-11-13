package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.shared.security.AuthCookieService;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Xác thực và phiên đăng nhập")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đăng nhập bằng tên đăng nhập hoặc email")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request.identifier(),
                request.password(),
                request.callbackUrl(),
                response);
        return new LoginResponse(result.redirectTo(), result.mustChangePassword());
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
}
