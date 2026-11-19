package vn.unicom.thi360.exam.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Daily sweep of cover images that are no longer a cover (TM 6.2: removed 7 days after the first sweep).
 * {@code deleteLease} is how long a sweep owns the covers it is deleting; it only matters when a sweep dies mid-way.
 */
@ConfigurationProperties(prefix = "thi360.exam.cover-cleanup")
public record ExamCoverCleanupProperties(
        @DefaultValue("P7D") Duration orphanAge,
        @DefaultValue("PT24H") Duration interval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("PT1H") Duration deleteLease) {
    public ExamCoverCleanupProperties {
        if (orphanAge.isNegative() || interval.isZero() || interval.isNegative() || batchSize < 1
                || deleteLease.isZero() || deleteLease.isNegative()) {
            throw new IllegalArgumentException("invalid cover cleanup settings");
        }
    }
}
