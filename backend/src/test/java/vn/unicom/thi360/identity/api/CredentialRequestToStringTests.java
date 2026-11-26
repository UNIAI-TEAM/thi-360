package vn.unicom.thi360.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialRequestToStringTests {

    @Test
    void createUserRequest_toStringRedactsPassword() {
        String secret = "Sup3rS3cret!99";
        CreateUserRequest req = new CreateUserRequest(
                "alice.01",
                secret,
                "Nguyen Alice",
                "alice@thi360.vn",
                "0901234567",
                List.of(UUID.randomUUID()),
                List.of(),
                true);

        String rendered = req.toString();
        assertThat(rendered)
                .doesNotContain(secret)
                .contains("password=[REDACTED]")
                .contains("username=alice.01");

        CreateUserRequest nullPasswordReq = new CreateUserRequest(
                "alice.02", null, "Nguyen Alice", null, null, null, null, null);
        assertThat(nullPasswordReq.toString()).contains("password=null");
    }

    @Test
    void resetPasswordRequest_toStringRedactsPassword() {
        String secret = "R3setS3cret!88";
        ResetPasswordRequest req = new ResetPasswordRequest(secret, true, "Quen mat khau");

        String rendered = req.toString();
        assertThat(rendered)
                .doesNotContain(secret)
                .contains("password=[REDACTED]")
                .contains("mustChangePassword=true");

        ResetPasswordRequest nullPasswordReq = new ResetPasswordRequest(null, false, null);
        assertThat(nullPasswordReq.toString()).contains("password=null");
    }

    @Test
    void registerRequest_toStringRedactsPassword() {
        String secret = "Reg1sterS3cret!77";
        RegisterRequest req = new RegisterRequest(
                "alice.03", "Nguyen Alice", "alice@thi360.vn", secret, true, "2026-11", "/checkout/ABC");

        String rendered = req.toString();
        assertThat(rendered)
                .doesNotContain(secret)
                .contains("password=[REDACTED]")
                .contains("username=alice.03")
                .contains("acceptTerms=true");

        RegisterRequest nullPasswordReq = new RegisterRequest("alice.04", null, null, null, null, null, null);
        assertThat(nullPasswordReq.toString()).contains("password=null");
    }

    @Test
    void emailChangeRequests_toStringRedactSecrets() {
        String secret = "Chang3S3cret!66";
        EmailCodeChangeRequest change = new EmailCodeChangeRequest("moi@thi360.vn", secret);
        assertThat(change.toString())
                .doesNotContain(secret)
                .contains("currentPassword=[REDACTED]")
                .contains("newEmail=moi@thi360.vn");
        assertThat(new EmailCodeChangeRequest("moi@thi360.vn", null).toString()).contains("currentPassword=null");

        EmailCodeVerifyRequest verify = new EmailCodeVerifyRequest("482913");
        assertThat(verify.toString()).doesNotContain("482913").contains("code=[REDACTED]");
    }

    @Test
    void passwordResetRequest_toStringRedactsTokenAndPassword() {
        String token = "tok3n-Abc_123-secret-reset-code";
        String secret = "N3wS3cret!77";
        PasswordResetRequest req = new PasswordResetRequest(token, secret);

        assertThat(req.toString())
                .doesNotContain(token)
                .doesNotContain(secret)
                .contains("token=[REDACTED]")
                .contains("newPassword=[REDACTED]");

        assertThat(new PasswordResetRequest(null, null).toString())
                .contains("token=null")
                .contains("newPassword=null");
    }

    @Test
    void mobileAuthRequestsAndResponse_toStringRedactSecretsAndTokens() {
        String password = "Mob1leS3cret!99";
        MobileLoginRequest login = new MobileLoginRequest("student.01", password);
        assertThat(login.toString())
                .doesNotContain(password)
                .contains("password=[REDACTED]")
                .contains("identifier=student.01");
        assertThat(new MobileLoginRequest("student.01", null).toString()).contains("password=null");

        String refreshToken = "eyJhbGciOiJSUzI1NiJ9.secret-refresh-token";
        MobileRefreshRequest refresh = new MobileRefreshRequest(refreshToken);
        assertThat(refresh.toString())
                .doesNotContain(refreshToken)
                .contains("refreshToken=[REDACTED]");
        assertThat(new MobileRefreshRequest(null).toString()).contains("refreshToken=null");

        MobileLogoutRequest logout = new MobileLogoutRequest(refreshToken);
        assertThat(logout.toString())
                .doesNotContain(refreshToken)
                .contains("refreshToken=[REDACTED]");
        assertThat(new MobileLogoutRequest(null).toString()).contains("refreshToken=null");

        String accessToken = "eyJhbGciOiJSUzI1NiJ9.secret-access-token";
        MobileTokenResponse response = new MobileTokenResponse(accessToken, refreshToken, 300, 604800, false);
        assertThat(response.toString())
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken)
                .contains("accessToken=[REDACTED]")
                .contains("refreshToken=[REDACTED]");
        assertThat(new MobileTokenResponse(null, null, 300, 604800, true).toString())
                .contains("accessToken=null")
                .contains("refreshToken=null");

        RegisterVerifyRequest verify = new RegisterVerifyRequest(UUID.randomUUID(), "928374");
        assertThat(verify.toString()).doesNotContain("928374").contains("code=[REDACTED]");
    }
}
