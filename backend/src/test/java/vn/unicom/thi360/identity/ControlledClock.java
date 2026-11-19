package vn.unicom.thi360.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Đồng hồ ứng dụng cho test cần ranh giới thời gian chính xác (E15, E17: 60 giây, 10 phút, 24 giờ). Mặc định chạy theo
 * giờ hệ thống, nên test không đụng tới đồng hồ không bị ảnh hưởng. {@link #freeze()} đứng yên tại giờ hiện tại để
 * {@link #advance(Duration)} đặt giờ chính xác, không phụ thuộc tốc độ máy chạy test. Gọi {@link #release()} khi xong ca.
 */
public final class ControlledClock extends Clock {

    private final Clock base = Clock.systemUTC();
    private volatile Instant frozenAt;

    public void freeze() {
        // PostgreSQL giữ tới micro giây: giờ đóng băng cũng ở độ chính xác đó để so sánh với giá trị đã lưu là chính xác.
        frozenAt = base.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        Instant current = frozenAt;
        if (current == null) {
            throw new IllegalStateException("clock is not frozen");
        }
        frozenAt = current.plus(duration);
    }

    public void release() {
        frozenAt = null;
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
        Instant frozen = frozenAt;
        return frozen != null ? frozen : base.instant();
    }
}
