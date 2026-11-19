package vn.unicom.thi360.shared.testapi;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Đồng hồ điều khiển được cho profile {@code e2e}. Giữ <em>độ lệch</em> so với đồng hồ nền (giờ hệ
 * thống), không giữ một mốc cố định: sau khi tua giờ vẫn chạy tiếp. Mọi nơi trong ứng dụng đọc giờ
 * qua bean {@link Clock} sẽ thấy giờ đã tua; JWT của Keycloak thì không (xem {@code E2eConfig}).
 */
public final class MutableClock extends Clock {

    /** Giờ sớm nhất đồng hồ chấp nhận. */
    public static final Instant SUPPORTED_MIN = Instant.parse("0001-01-01T00:00:00Z");

    /**
     * Giờ muộn nhất đồng hồ chấp nhận. Đặt cách xa mốc 9999 để giờ thật chạy tiếp hàng thế kỷ mà
     * vẫn trong khoảng đọc được.
     */
    public static final Instant SUPPORTED_MAX = Instant.parse("9000-01-01T00:00:00Z");

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

    /**
     * Mẫu đồng hồ nền rồi chỉ trả về nếu độ lệch chưa đổi trong lúc đó: giá trị trả về luôn thuộc
     * đúng một trạng thái (trước hoặc sau một lần đặt), không lai giữa giờ nền của trạng thái này
     * với độ lệch của trạng thái kia.
     */
    @Override
    public Instant instant() {
        while (true) {
            Duration current = offset.get();
            Instant baseNow = base.instant();
            if (offset.get() == current) {
                return baseNow.plus(current);
            }
        }
    }

    /** Đặt giờ hiện tại đúng bằng {@code at}; từ đó giờ chạy tiếp. */
    public Instant setAt(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        requireInRange(at);
        return transition((baseNow, current) -> Duration.between(baseNow, at));
    }

    /** Tua tới thêm {@code amount} (không âm). */
    public Instant advance(Duration amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        return transition((baseNow, current) -> current.plus(amount));
    }

    /** Trả về giờ hệ thống (độ lệch 0). */
    public Instant reset() {
        return transition((baseNow, current) -> Duration.ZERO);
    }

    /** Độ lệch hiện tại so với đồng hồ nền. */
    public Duration offset() {
        return offset.get();
    }

    /**
     * Một lần chuyển trạng thái: mẫu giờ nền và độ lệch cũ, tính độ lệch mới, kiểm giờ kết quả còn
     * trong khoảng hỗ trợ <em>trước khi</em> ghi, rồi ghi nếu độ lệch chưa bị ai đổi (không thì thử
     * lại). Giá trị trả về là giờ của chính trạng thái vừa ghi tại mẫu nền đó, nên không bị lẫn với
     * lần chuyển khác chạy đồng thời. Quá khoảng thì ném {@link OutOfRangeException} và giữ nguyên
     * trạng thái cũ.
     */
    private Instant transition(BiFunction<Instant, Duration, Duration> next) {
        while (true) {
            Duration current = offset.get();
            Instant baseNow = base.instant();
            Duration updated;
            Instant result;
            try {
                updated = next.apply(baseNow, current);
                result = baseNow.plus(updated);
            } catch (DateTimeException | ArithmeticException e) {
                throw new OutOfRangeException();
            }
            requireInRange(result);
            if (offset.compareAndSet(current, updated)) {
                return result;
            }
        }
    }

    private static void requireInRange(Instant instant) {
        if (instant.isBefore(SUPPORTED_MIN) || instant.isAfter(SUPPORTED_MAX)) {
            throw new OutOfRangeException();
        }
    }

    /**
     * Giờ kết quả nằm ngoài {@link #SUPPORTED_MIN}..{@link #SUPPORTED_MAX}: ngoài khoảng này các
     * nơi dùng giờ (cơ sở dữ liệu, định dạng ngày, đổi múi giờ) không còn đọc được, và đồng hồ
     * không thể chạy tiếp mà vẫn nằm trong khoảng.
     */
    public static final class OutOfRangeException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        OutOfRangeException() {
            super("resulting time must be within " + SUPPORTED_MIN + " and " + SUPPORTED_MAX);
        }
    }
}
