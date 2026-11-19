package vn.unicom.thi360.identity.application;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Cửa vào của {@code POST /auth/password/forgot} (TM 5.4): chỉ giao việc cho bộ thực thi riêng rồi trở về ngay.
 * Việc tra tài khoản, tạo mã và xếp thư chạy sau khi phản hồi đã trả, nên thời gian phản hồi không phân biệt email
 * có tài khoản hay không. Hàng đợi đầy hoặc việc lỗi thì chỉ ghi log (không có email, không có mã): phản hồi của
 * caller vẫn là 202 cùng một thân.
 */
@Service
public class PasswordForgotDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PasswordForgotDispatcher.class);

    private final Executor executor;
    private final PasswordResetService passwordResetService;

    public PasswordForgotDispatcher(
            @Qualifier(PasswordResetExecutorConfiguration.BEAN_NAME) Executor executor,
            PasswordResetService passwordResetService) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.passwordResetService = Objects.requireNonNull(passwordResetService, "passwordResetService must not be null");
    }

    /** Giao việc và trở về; không bao giờ ném lỗi cho caller. */
    public void dispatch(String email, String clientIp) {
        try {
            executor.execute(() -> process(email, clientIp));
        } catch (RejectedExecutionException ex) {
            log.atWarn()
                    .addKeyValue("event", "password_reset.queue_full")
                    .log("Password forgot request dropped: executor queue is full");
        }
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
}
