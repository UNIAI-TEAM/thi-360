package vn.unicom.thi360.exam.domain.solver.oracle;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Đồng hồ test: mỗi lần đọc {@link #instant()} dịch thêm {@code stepMillis} mili-giây. Dùng để
 * ép hết hạn của bộ giải tại một vị trí kiểm tra giờ xác định (R1-4: deadline trong prep, trong
 * mạng luồng, trong quay lui, trong liệt kê đường).
 */
public final class SteppingClock extends Clock {

    private final long stepMillis;
    private Instant now;
    private long ticks;

    /** Đồng hồ bắt đầu ở epoch, nhảy {@code stepMillis} sau mỗi lần đọc. */
    public SteppingClock(long stepMillis) {
        this(Instant.EPOCH, stepMillis);
    }

    public SteppingClock(Instant start, long stepMillis) {
        this.now = start;
        this.stepMillis = stepMillis;
    }

    /** Số lần đã đọc giờ. */
    public long ticks() {
        return ticks;
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        ticks++;
        Instant current = now;
        now = now.plusMillis(stepMillis);
        return current;
    }
}
