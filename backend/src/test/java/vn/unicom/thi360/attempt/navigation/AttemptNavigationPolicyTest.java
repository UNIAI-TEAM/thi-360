package vn.unicom.thi360.attempt.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptGuard;
import vn.unicom.thi360.attempt.application.AttemptNavigationPolicy;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.shared.api.ConflictException;

/** Unit tests for {@link AttemptNavigationPolicy} and {@link AttemptGuard#requirePartFinishAllowed} (BS §4, AT-8). */
class AttemptNavigationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final AttemptNavigationPolicy policy = new AttemptNavigationPolicy();

    @Test
    void resolveForExamKindReturnsFreeOnlyForThptqg() {
        assertThat(policy.resolveForExamKind("THPTQG")).isEqualTo(NavigationMode.FREE);
        assertThat(policy.resolveForExamKind("thptqg")).isEqualTo(NavigationMode.FREE);
        assertThat(policy.resolveForExamKind("  ThPtQg  ")).isEqualTo(NavigationMode.FREE);

        for (String sequentialKind :
                List.of("HSA", "QDA", "BCA", "VACT", "SP2E", "SPT", "HSCA", "VSAT")) {
            assertThat(policy.resolveForExamKind(sequentialKind))
                    .as("examKindCode=%s", sequentialKind)
                    .isEqualTo(NavigationMode.SEQUENTIAL);
        }

        assertThat(policy.resolveForExamKind(null)).isEqualTo(NavigationMode.SEQUENTIAL);
        assertThat(policy.resolveForExamKind("")).isEqualTo(NavigationMode.SEQUENTIAL);
        assertThat(policy.resolveForExamKind("   ")).isEqualTo(NavigationMode.SEQUENTIAL);
        assertThat(policy.resolveForExamKind("UNKNOWN_KIND")).isEqualTo(NavigationMode.SEQUENTIAL);
    }

    @Test
    void modeCapabilitiesMatchFreeAndSequentialContracts() {
        assertThat(policy.opensAllPartsOnStart(NavigationMode.FREE)).isTrue();
        assertThat(policy.opensAllPartsOnStart(NavigationMode.SEQUENTIAL)).isFalse();
        assertThat(policy.opensAllPartsOnStart(null)).isFalse();

        assertThat(policy.allowsPartFinish(NavigationMode.FREE)).isFalse();
        assertThat(policy.allowsPartFinish(NavigationMode.SEQUENTIAL)).isTrue();
        assertThat(policy.allowsPartFinish(null)).isFalse();

        assertThat(policy.requiresLastPartForSubmit(NavigationMode.FREE)).isFalse();
        assertThat(policy.requiresLastPartForSubmit(NavigationMode.SEQUENTIAL)).isTrue();
        assertThat(policy.requiresLastPartForSubmit(null)).isFalse();
    }

    @Test
    void attemptGuardRequirePartFinishAllowedRejectsFreeAndAllowsSequential() {
        AttemptGuard guard =
                new AttemptGuard(
                        mock(AttemptRepository.class),
                        mock(AttemptPartRepository.class),
                        mock(OverdueAttemptCloser.class),
                        mock(AttemptSessionChecker.class),
                        policy,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        Attempt freeAttempt = openAttempt("THPTQG", NavigationMode.FREE);
        assertThatThrownBy(() -> guard.requirePartFinishAllowed(freeAttempt))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PART_FINISH_NOT_ALLOWED");

        Attempt sequentialAttempt = openAttempt("HSCA", NavigationMode.SEQUENTIAL);
        assertThatCode(() -> guard.requirePartFinishAllowed(sequentialAttempt))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requirePartFinishAllowed(null)).doesNotThrowAnyException();
    }

    private static Attempt openAttempt(String examKindCode, NavigationMode mode) {
        Attempt attempt =
                Attempt.start(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Nav Policy Test",
                        UUID.randomUUID(),
                        examKindCode,
                        "SUBJECT",
                        null,
                        UUID.randomUUID(),
                        AttemptTiming.SHARED,
                        mode,
                        Map.of(),
                        1L,
                        UUID.randomUUID(),
                        "SCORE_AND_ANSWERS",
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        "idem-nav",
                        NOW.minusSeconds(30));
        attempt.open(1, NOW, NOW.plusSeconds(5400));
        return attempt;
    }
}
