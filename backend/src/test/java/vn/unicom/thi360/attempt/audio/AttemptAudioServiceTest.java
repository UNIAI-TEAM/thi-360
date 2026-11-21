package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptAudioProperties;

class AttemptAudioServiceTest {

    @Test
    void audioPropertiesApplySensibleDefaultsAndKeepExplicitValues() {
        AttemptAudioProperties defaults = new AttemptAudioProperties(null, null);
        assertThat(defaults.leaseDuration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(defaults.seekTolerance()).isEqualTo(Duration.ofSeconds(3));

        AttemptAudioProperties fallbackInvalid =
                new AttemptAudioProperties(Duration.ZERO, Duration.ofMillis(-1));
        assertThat(fallbackInvalid.leaseDuration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(fallbackInvalid.seekTolerance()).isEqualTo(Duration.ofSeconds(3));

        AttemptAudioProperties custom =
                new AttemptAudioProperties(Duration.ofSeconds(30), Duration.ZERO);
        assertThat(custom.leaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(custom.seekTolerance()).isEqualTo(Duration.ZERO);
    }

    @Test
    void audioDomainRejectsInvalidTransitionsCleanly() {
        var now = java.time.Instant.parse("2026-11-21T00:00:00Z");
        var play =
                vn.unicom.thi360.attempt.domain.AttemptAudioPlay.createIdle(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        30_000L,
                        now);
        assertThat(play.playsLeft()).isEqualTo(2);
        assertThatThrownBy(() -> play.resume(now, now.plusSeconds(45)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> play.recordProgress(100, 0, now, now.plusSeconds(45)))
                .isInstanceOf(IllegalStateException.class);
    }
}
