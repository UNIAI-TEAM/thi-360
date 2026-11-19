package vn.unicom.thi360.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Biên của E15, E17: gửi lại ở giây 59 và 60; lần gửi thứ 5 và thứ 6 trong 24 giờ. */
class EmailCodeSendPolicyTests {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void firstSendIsAllowed() {
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(List.of(), NOW)).isEmpty();
    }

    @Test
    void resendAtSecond59WaitsOneSecondAndAtSecond60IsAllowed() {
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(List.of(NOW.minusSeconds(59)), NOW))
                .isEqualTo(OptionalLong.of(1));
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(List.of(NOW.minusSeconds(60)), NOW)).isEmpty();
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(List.of(NOW.minusSeconds(61)), NOW)).isEmpty();
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(List.of(NOW.minusMillis(58_500)), NOW))
                .isEqualTo(OptionalLong.of(2));
    }

    @Test
    void fifthSendIsAllowedAndSixthIsRefusedUntilOldestLeavesWindow() {
        List<Instant> four = spacedSends(4, Duration.ofHours(2));
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(four, NOW)).isEmpty();

        List<Instant> five = spacedSends(5, Duration.ofHours(2));
        // Mã cũ nhất gửi cách đây 8 giờ 2 phút nên còn 15 giờ 58 phút nữa mới được gửi mã thứ 6.
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(five, NOW))
                .isEqualTo(OptionalLong.of(Duration.ofHours(16).minusSeconds(120).toSeconds()));
    }

    @Test
    void dailyLimitWaitNeverShorterThanCooldown() {
        List<Instant> five = new ArrayList<>(List.of(
                NOW.minusSeconds(10),
                NOW.minusSeconds(20),
                NOW.minusSeconds(30),
                NOW.minusSeconds(40),
                NOW.minus(Duration.ofHours(24)).plusSeconds(5)));
        assertThat(EmailCodeSendPolicy.retryAfterSeconds(five, NOW)).isEqualTo(OptionalLong.of(50));
    }

    private static List<Instant> spacedSends(int count, Duration gap) {
        List<Instant> sends = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sends.add(NOW.minus(gap.multipliedBy(i)).minusSeconds(120));
        }
        return sends;
    }
}
