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
}
