package vn.unicom.thi360.shared.mail;

import java.time.Duration;
import java.util.List;

/** Lịch gửi lại (TM 4): 1 phút, 5 phút, 30 phút, 2 giờ; lần lỗi thứ 5 thì thất bại. */
public final class MailRetrySchedule {

    public static final int MAX_ATTEMPTS = 5;

    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

    private MailRetrySchedule() {
    }

    /** Có còn được gửi lại sau {@code failedAttempts} lần lỗi không. */
    public static boolean hasRetryLeft(int failedAttempts) {
        return failedAttempts < MAX_ATTEMPTS;
    }

    /** Chờ bao lâu sau lần lỗi thứ {@code failedAttempts} (1..4). */
    public static Duration delayAfter(int failedAttempts) {
        if (failedAttempts < 1 || failedAttempts >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("No retry delay after failure #" + failedAttempts);
        }
        return DELAYS.get(failedAttempts - 1);
    }
}
