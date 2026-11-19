package vn.unicom.thi360.shared.testapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Đồng hồ điều khiển được cho profile {@code e2e}. Giữ <em>độ lệch</em> so với đồng hồ nền (giờ hệ
 * thống), không giữ một mốc cố định: sau khi tua giờ vẫn chạy tiếp. Mọi nơi trong ứng dụng đọc giờ
 * qua bean {@link Clock} sẽ thấy giờ đã tua; JWT của Keycloak thì không (xem {@code E2eConfig}).
 */
public final class MutableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public MutableClock() {
        this(Clock.systemUTC());
    }

    public MutableClock(Clock base) {
        this.base = Objects.requireNonNull(base, "base must not be null");
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(base.getZone())) {
            return this;
        }
        MutableClock self = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return self.withZone(other);
            }

            @Override
            public Instant instant() {
                return self.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    /** Đặt giờ hiện tại đúng bằng {@code at}; từ đó giờ chạy tiếp. */
    public Instant setAt(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        offset.set(Duration.between(base.instant(), at));
        return instant();
    }

    /** Tua tới thêm {@code amount} (không âm). */
    public Instant advance(Duration amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        offset.updateAndGet(current -> current.plus(amount));
        return instant();
    }

    /** Trả về giờ hệ thống (độ lệch 0). */
    public Instant reset() {
        offset.set(Duration.ZERO);
        return instant();
    }

    /** Độ lệch hiện tại so với đồng hồ nền. */
    public Duration offset() {
        return offset.get();
    }
}
