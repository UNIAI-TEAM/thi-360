package vn.unicom.thi360.commerce.pricing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Controllable UTC {@link Clock} for deterministic boundary and rolling-window rate-limit tests in {@code pricing}.
 * Delegates to wall-clock UTC unless pinned with {@link MutableClock#fixAt(Instant)}.
 */
@TestConfiguration
class PricingTestClockConfig {

    static final class MutableClock extends Clock {
        private volatile Instant fixedInstant;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant fixed = fixedInstant;
            return fixed != null ? fixed : Instant.now();
        }

        void fixAt(Instant instant) {
            this.fixedInstant = instant;
        }

        void advance(Duration delta) {
            Instant base = fixedInstant != null ? fixedInstant : Instant.now();
            this.fixedInstant = base.plus(delta);
        }

        void reset() {
            this.fixedInstant = null;
        }
    }

    @Bean
    @Primary
    MutableClock pricingMutableClock() {
        return new MutableClock();
    }
}
