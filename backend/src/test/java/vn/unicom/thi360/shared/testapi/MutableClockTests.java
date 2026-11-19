package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MutableClockTests {

    /** Đồng hồ nền do test điều khiển, để kiểm "giờ vẫn chạy sau khi tua" mà không phải chờ. */
    private static final class ManualBase extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-19T00:00:00Z"));

        void tick(Duration by) {
            now.updateAndGet(i -> i.plus(by));
        }

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
            return now.get();
        }
    }

    @Test
    void startsAtBaseTime() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        assertThat(clock.instant()).isEqualTo(base.instant());
        assertThat(clock.offset()).isEqualTo(Duration.ZERO);
    }

    @Test
    void timeKeepsRunningAfterAdvance() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        Instant afterAdvance = clock.advance(Duration.ofHours(3));
        assertThat(afterAdvance).isEqualTo(base.instant().plus(Duration.ofHours(3)));

        base.tick(Duration.ofSeconds(5));
        assertThat(clock.instant()).isEqualTo(afterAdvance.plusSeconds(5));
    }

    @Test
    void timeKeepsRunningAfterSetAt() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        Instant target = Instant.parse("2027-01-01T08:00:00Z");

        assertThat(clock.setAt(target)).isEqualTo(target);

        base.tick(Duration.ofMinutes(2));
        assertThat(clock.instant()).isEqualTo(target.plus(Duration.ofMinutes(2)));
    }

    @Test
    void advancesAccumulate() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        clock.advance(Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(45));

        assertThat(clock.instant()).isEqualTo(base.instant().plusSeconds(75));
    }

    @Test
    void resetReturnsToBaseTime() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        clock.advance(Duration.ofDays(2));

        assertThat(clock.reset()).isEqualTo(base.instant());

        base.tick(Duration.ofSeconds(7));
        assertThat(clock.instant()).isEqualTo(base.instant());
        assertThat(clock.offset()).isEqualTo(Duration.ZERO);
    }

    @Test
    void negativeAdvanceIsRejectedAndKeepsOffset() {
        MutableClock clock = new MutableClock(new ManualBase());
        clock.advance(Duration.ofMinutes(1));

        assertThatThrownBy(() -> clock.advance(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(clock.offset()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void withZoneViewFollowsTheMutableClock() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        ZoneId hanoi = ZoneId.of("Asia/Ho_Chi_Minh");
        Clock view = clock.withZone(hanoi);

        clock.advance(Duration.ofHours(1));

        assertThat(view.getZone()).isEqualTo(hanoi);
        assertThat(view.instant()).isEqualTo(clock.instant());
        assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
    }
}
