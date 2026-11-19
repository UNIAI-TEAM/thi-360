package vn.unicom.thi360.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Biên của E15: mã còn 1 giây và vừa hết 10 phút; lần sai thứ 5 và lần nhập thứ 6. */
class EmailCodeTests {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    private static EmailCode code() {
        return new EmailCode(UUID.randomUUID(), UUID.randomUUID(), EmailCodePurpose.VERIFY, "a@b.vn", "hash", NOW);
    }

    @Test
    void expiresAtTenMinutes() {
        EmailCode code = code();
        assertThat(code.isUsable(NOW)).isTrue();
        assertThat(code.isUsable(NOW.plus(Duration.ofMinutes(10)).minusSeconds(1))).isTrue();
        assertThat(code.isUsable(NOW.plus(Duration.ofMinutes(10)))).isFalse();
    }

    @Test
    void fifthWrongAttemptReportsZeroRemainingAndSixthIsNotUsable() {
        EmailCode code = code();
        assertThat(code.recordWrongAttempt()).isEqualTo(4);
        assertThat(code.recordWrongAttempt()).isEqualTo(3);
        assertThat(code.recordWrongAttempt()).isEqualTo(2);
        assertThat(code.recordWrongAttempt()).isEqualTo(1);
        assertThat(code.isUsable(NOW)).isTrue();
        assertThat(code.recordWrongAttempt()).isZero();
        assertThat(code.isUsable(NOW)).isFalse();
    }

    @Test
    void usedCodeIsNotUsable() {
        EmailCode code = code();
        code.markUsed(NOW);
        assertThat(code.isUsable(NOW)).isFalse();
    }
}
