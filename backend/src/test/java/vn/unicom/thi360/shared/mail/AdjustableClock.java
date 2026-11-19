package vn.unicom.thi360.shared.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Đồng hồ của test: đứng yên cho tới khi test đặt hoặc tua. Tiêm thay bean {@link Clock}. */
final class AdjustableClock extends Clock {

    private final AtomicReference<Instant> now;

    AdjustableClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    void set(Instant instant) {
        now.set(instant);
    }

    void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
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
