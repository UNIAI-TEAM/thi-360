package vn.unicom.thi360.shared.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Hạn tuyệt đối của một lần gửi, tính một lần từ dòng giữ chỗ: nhỏ hơn trong (a) hạn giữ chỗ trừ
 * {@link MailProperties#SAFETY_MARGIN} và (b) {@code attemptTimeout} kể từ lúc bắt đầu lần gửi.
 * Mọi thời gian chờ của lần gửi (chờ kết quả, thời gian chờ kết nối và đọc của socket) đều lấy từ
 * {@link #remaining()} <em>tại lúc dùng</em>, không từ một giá trị đã đo trước đó; còn lại dưới 1 ms thì
 * không bắt đầu SMTP.
 *
 * <p>Phần giữ chỗ đo theo {@link Clock} được tiêm (cùng đồng hồ với {@code next_attempt_at}); phần
 * {@code attemptTimeout} đo theo {@link System#nanoTime()} nên không nhảy theo đồng hồ.
 */
final class SendDeadline {

    /** Hết hạn: không được bắt đầu hoặc tiếp tục SMTP. */
    static final class ExpiredException extends Exception {

        ExpiredException() {
            super("The send deadline has passed; SMTP was not started", null, false, false);
        }
    }

    private static final Duration ONE_MILLI = Duration.ofMillis(1);

    private final Clock clock;
    private final Instant leaseDeadline;
    private final long attemptEndNanos;

    SendDeadline(Clock clock, Instant heldUntil, Duration attemptTimeout) {
        this.clock = clock;
        this.leaseDeadline = heldUntil.minus(MailProperties.SAFETY_MARGIN);
        this.attemptEndNanos = System.nanoTime() + attemptTimeout.toNanos();
    }

    /** Thời gian còn lại tới hạn tuyệt đối; âm hoặc không khi đã quá hạn. */
    Duration remaining() {
        Duration lease = Duration.between(Instant.now(clock), leaseDeadline);
        Duration attempt = Duration.ofNanos(attemptEndNanos - System.nanoTime());
        return lease.compareTo(attempt) < 0 ? lease : attempt;
    }

    boolean expired() {
        return remaining().compareTo(ONE_MILLI) < 0;
    }

    /**
     * Số mili giây còn lại dùng làm thời gian chờ của socket, làm tròn xuống.
     *
     * @throws ExpiredException khi còn lại dưới 1 ms
     */
    int remainingMillis() throws ExpiredException {
        Duration left = remaining();
        if (left.compareTo(ONE_MILLI) < 0) {
            throw new ExpiredException();
        }
        return (int) Math.min(left.toMillis(), Integer.MAX_VALUE);
    }
}
