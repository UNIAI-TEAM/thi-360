package vn.unicom.thi360.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/**
 * Giới hạn gửi mã OTP của một tài khoản cho một mục đích (E15, E17): gửi lại sau 60 giây, tối đa 5 mã trong
 * 24 giờ trượt (câu hỏi mở 2, tạm). Hàm thuần, không đọc đồng hồ.
 */
public final class EmailCodeSendPolicy {

    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final Duration DAILY_WINDOW = Duration.ofHours(24);
    public static final int MAX_PER_DAY = 5;

    private EmailCodeSendPolicy() {
    }

    /**
     * @param sentInWindow thời điểm các mã đã gửi trong {@link #DAILY_WINDOW} tính tới {@code now}, mới nhất trước
     * @return số giây phải chờ trước khi được gửi (làm tròn lên), rỗng nếu được gửi ngay
     */
    public static OptionalLong retryAfterSeconds(List<Instant> sentInWindow, Instant now) {
        long wait = 0;
        if (!sentInWindow.isEmpty()) {
            Instant cooldownEnds = sentInWindow.getFirst().plus(RESEND_COOLDOWN);
            wait = secondsUntil(now, cooldownEnds);
        }
        if (sentInWindow.size() >= MAX_PER_DAY) {
            // Mã thứ (size - MAX_PER_DAY + 1) từ cũ nhất rời cửa sổ thì lượt gửi mới được phép.
            Instant frees = sentInWindow.get(MAX_PER_DAY - 1).plus(DAILY_WINDOW);
            wait = Math.max(wait, secondsUntil(now, frees));
        }
        return wait > 0 ? OptionalLong.of(wait) : OptionalLong.empty();
    }

    private static long secondsUntil(Instant now, Instant target) {
        Duration remaining = Duration.between(now, target);
        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }
        long seconds = remaining.getSeconds();
        return remaining.getNano() > 0 ? seconds + 1 : seconds;
    }
}
