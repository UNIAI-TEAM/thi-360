package vn.unicom.thi360.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.AuthenticationFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailSendException;

/**
 * Kết quả phân loại một lần gửi lỗi. {@link #code()} là chuỗi an toàn ghi vào {@code last_error}:
 * chỉ gồm loại lỗi, mã SMTP và mã trạng thái mở rộng. Không bao giờ chép câu chữ máy chủ SMTP hay
 * thông báo của ngoại lệ vì chúng có thể lặp lại địa chỉ hoặc nội dung thư.
 *
 * @param permanent {@code true} khi 5xx ở {@code RCPT TO}: không gửi lại
 */
record MailFailure(boolean permanent, String code) {

    /**
     * Mã trạng thái mở rộng chỉ được lấy ngay đầu câu trả lời của máy chủ ({@code "550 5.1.1 ..."} hoặc
     * {@code "5.1.1 ..."}). Một chuỗi giống mã ở giữa câu có thể là nội dung thư được máy chủ lặp lại.
     */
    private static final Pattern ENHANCED_STATUS =
            Pattern.compile("^\\s*(?:\\d{3}[ -])?(([245])\\.\\d{1,3}\\.\\d{1,3})(?=\\s|$)");

    /** Lần gửi quá hạn cứng của một lần gửi ({@code thi360.mail.attempt-timeout}). */
    static MailFailure timeout() {
        return new MailFailure(false, "timeout");
    }

    static MailFailure classify(Throwable failure) {
        List<Throwable> chain = flatten(failure);

        for (Throwable t : chain) {
            if (t instanceof SMTPAddressFailedException address && isPermanentReply(address.getReturnCode())) {
                return new MailFailure(true, "smtp-" + address.getReturnCode() + enhanced(address, address.getReturnCode()) + " rcpt-rejected");
            }
        }
        for (Throwable t : chain) {
            if (t instanceof SMTPSendFailedException send) {
                return new MailFailure(false, "smtp-" + send.getReturnCode() + enhanced(send, send.getReturnCode()));
            }
            if (t instanceof SMTPAddressFailedException address) {
                return new MailFailure(false, "smtp-" + address.getReturnCode() + enhanced(address, address.getReturnCode()) + " rcpt-deferred");
            }
        }
        for (Throwable t : chain) {
            if (t instanceof AuthenticationFailedException) {
                return new MailFailure(false, "auth-failed");
            }
            if (t instanceof SocketTimeoutException) {
                return new MailFailure(false, "timeout");
            }
            if (t instanceof ConnectException
                    || t instanceof org.eclipse.angus.mail.util.MailConnectException) {
                return new MailFailure(false, "connect-failed");
            }
        }
        return new MailFailure(false, "error " + failure.getClass().getSimpleName());
    }

    private static boolean isPermanentReply(int returnCode) {
        return returnCode >= 500 && returnCode < 600;
    }

    /** Mã trạng thái mở rộng hợp lệ ở đầu câu trả lời, cùng lớp (2xx/4xx/5xx) với mã trả về; nếu không thì bỏ. */
    private static String enhanced(MessagingException exception, int returnCode) {
        Matcher matcher = ENHANCED_STATUS.matcher(String.valueOf(exception.getMessage()));
        if (!matcher.find() || (matcher.group(2).charAt(0) - '0') != returnCode / 100) {
            return "";
        }
        return " " + matcher.group(1);
    }

    /** Cả chuỗi nguyên nhân, ngoại lệ lồng của JavaMail và từng lỗi của {@link MailSendException}. */
    private static List<Throwable> flatten(Throwable root) {
        List<Throwable> result = new ArrayList<>();
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, result, seen);
        return result;
    }

    private static void collect(Throwable t, List<Throwable> result, Set<Throwable> seen) {
        if (t == null || !seen.add(t)) {
            return;
        }
        result.add(t);
        collect(t.getCause(), result, seen);
        if (t instanceof MessagingException messaging) {
            collect(messaging.getNextException(), result, seen);
        }
        if (t instanceof MailSendException send) {
            for (Exception each : send.getMessageExceptions()) {
                collect(each, result, seen);
            }
        }
    }
}
