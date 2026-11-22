package vn.unicom.thi360.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Cửa vào của {@code POST /auth/password/forgot} (TM 5.4): controller chỉ <b>ghi nhận</b> việc cần làm vào request;
 * {@code PasswordForgotHandoffFilter} giao việc cho bộ thực thi riêng sau khi phản hồi 202 đã được ghi và commit
 * (không phải chỉ "khác luồng"), nên việc tra tài khoản, tạo mã và xếp thư không thể chạy trước phản hồi và thời
 * gian phản hồi không phân biệt email có tài khoản hay không. Hàng đợi đầy hoặc việc lỗi thì chỉ ghi log (không có
 * email, không có mã): phản hồi của caller vẫn là 202 cùng một thân.
 */
@Service
public class PasswordForgotDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PasswordForgotDispatcher.class);
    public static final String PENDING_ATTRIBUTE = PasswordForgotDispatcher.class.getName() + ".pending";

    private final Executor executor;
    private final PasswordResetService passwordResetService;

    public PasswordForgotDispatcher(
            @Qualifier(PasswordResetExecutorConfiguration.BEAN_NAME) Executor executor,
            PasswordResetService passwordResetService) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.passwordResetService = Objects.requireNonNull(passwordResetService, "passwordResetService must not be null");
    }

    /** Ghi nhận việc chờ giao; chưa chạy gì. Không bao giờ ném lỗi cho caller. */
    public void dispatch(HttpServletRequest request, String email, String clientIp) {
        request.setAttribute(PENDING_ATTRIBUTE, new Pending(email, clientIp));
    }

    /**
     * Giao việc đã ghi nhận của {@code request} cho bộ thực thi (gọi sau khi phản hồi đã commit). Hàng đợi đầy
     * thì bỏ việc và ghi log. Trả {@code true} khi có việc chờ.
     */
    public boolean handOff(HttpServletRequest request) {
        if (!(request.getAttribute(PENDING_ATTRIBUTE) instanceof Pending pending)) {
            return false;
        }
        request.removeAttribute(PENDING_ATTRIBUTE);
        try {
            executor.execute(() -> process(pending.email(), pending.clientIp()));
        } catch (RejectedExecutionException ex) {
            log.atWarn()
                    .addKeyValue("event", "password_reset.queue_full")
                    .log("Password forgot request dropped: executor queue is full");
        }
        return true;
    }

    private void process(String email, String clientIp) {
        try {
            passwordResetService.issue(email, clientIp);
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.issue_failed")
                    .log("Password forgot request failed in background");
        }
    }

    private record Pending(String email, String clientIp) {
    }
}
