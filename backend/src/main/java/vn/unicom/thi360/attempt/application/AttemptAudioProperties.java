package vn.unicom.thi360.attempt.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-enforced lease and seek-tolerance configuration for attempt audio playback (BS §17.2). */
@ConfigurationProperties(prefix = "thi360.attempt.audio")
public record AttemptAudioProperties(Duration leaseDuration, Duration seekTolerance) {

    public AttemptAudioProperties {
        leaseDuration =
                (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative())
                        ? Duration.ofSeconds(45)
                        : leaseDuration;
        seekTolerance =
                (seekTolerance == null || seekTolerance.isNegative())
                        ? Duration.ofSeconds(3)
                        : seekTolerance;
    }
}
