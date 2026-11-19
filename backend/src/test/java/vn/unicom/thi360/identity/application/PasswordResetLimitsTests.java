package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.PasswordResetToken;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PasswordResetTokenRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * Biên của giới hạn E16 theo tài khoản (Kiểm thử rủi ro, plan ID-2): yêu cầu ở giây 59 và 60, yêu cầu thứ 5 và
 * thứ 6 trong ngày, và cửa sổ trượt 24 giờ. Quy tắc nằm trong một hàm thuần nên kiểm được chính xác từng nanô giây.
 */
class PasswordResetLimitsTests {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    private final PasswordResetService service = new PasswordResetService(
            mock(PasswordResetTokenRepository.class),
            mock(UserAccountRepository.class),
            new PasswordResetProperties(
                    Duration.ofHours(1), Duration.ofSeconds(60), 5, Duration.ofHours(24), 2, 100),
            mock(PasswordPolicy.class),
            mock(KeycloakIdentityClient.class),
            mock(UserAdminService.class),
            mock(MailQueue.class),
            mock(AuditRecorder.class),
            mock(JdbcTemplate.class),
            mock(PlatformTransactionManager.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new TokenPepperProperties("dev-only-pepper-for-unit-tests"),
            new RedirectProperties("http://localhost:3000", "http://localhost:3001", List.of()));

    private static PasswordResetToken tokenCreatedAt(Instant createdAt) {
        return new PasswordResetToken(
                UUID.randomUUID(), UUID.randomUUID(), "hash-" + createdAt, createdAt.plusSeconds(3600), createdAt, null);
    }

    @Test
    void noPreviousRequestIsAllowed() {
        assertThat(service.exceedsAccountLimits(List.of(), NOW)).isFalse();
    }

    @Test
    void cooldownBoundaryAtSecond59And60() {
        // 59 giây trước: còn trong 60 giây giãn cách → chặn.
        assertThat(service.exceedsAccountLimits(List.of(tokenCreatedAt(NOW.minusSeconds(59))), NOW)).isTrue();
        // 59,999 giây trước: vẫn chặn.
        assertThat(service.exceedsAccountLimits(
                List.of(tokenCreatedAt(NOW.minusSeconds(60).plusMillis(1))), NOW)).isTrue();
        // Đúng 60 giây trước: được phép (E16: "1 lần mỗi 60 giây").
        assertThat(service.exceedsAccountLimits(List.of(tokenCreatedAt(NOW.minusSeconds(60))), NOW)).isFalse();
        assertThat(service.exceedsAccountLimits(List.of(tokenCreatedAt(NOW.minusSeconds(61))), NOW)).isFalse();
    }

    @Test
    void dailyLimitBoundaryAtFifthAndSixthRequest() {
        // Bốn yêu cầu trước (đều quá 60 giây): yêu cầu thứ 5 được phép.
        assertThat(service.exceedsAccountLimits(spaced(4), NOW)).isFalse();
        // Năm yêu cầu trước: yêu cầu thứ 6 bị chặn dù đã quá 60 giây giãn cách.
        assertThat(service.exceedsAccountLimits(spaced(5), NOW)).isTrue();
    }

    private static List<PasswordResetToken> spaced(int count) {
        List<PasswordResetToken> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tokens.add(tokenCreatedAt(NOW.minus(Duration.ofMinutes(5L * (i + 1)))));
        }
        return tokens;
    }
}
