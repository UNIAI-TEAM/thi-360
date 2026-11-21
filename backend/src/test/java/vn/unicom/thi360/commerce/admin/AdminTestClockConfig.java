package vn.unicom.thi360.commerce.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Đồng hồ UTC điều khiển được cho kiểm thử biên thời gian 24 giờ trong {@code commerce/admin}.
 */
@TestConfiguration
class AdminTestClockConfig {

    static final class AdminMutableClock extends Clock {
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
    AdminMutableClock adminMutableClock() {
        return new AdminMutableClock();
    }
}
