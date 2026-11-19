package vn.unicom.thi360.identity.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thử lại việc thu hồi phiên sau khi đặt lại mật khẩu mà Redis hoặc Keycloak lỗi (TM 5.4, AC-12): mật khẩu đã
 * đổi và mã đã dùng xong, chỉ còn phiên cũ cần thu hồi. Không bao giờ ghi lại mật khẩu. Cùng lượt chạy còn đối soát
 * các lần ghi mật khẩu có dấu ghi trước mà chưa hoàn tất ({@link PasswordResetService#reconcileUnfinalizedWrites}).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(
        name = "thi360.identity.password-reset.revocation-retry.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PasswordResetRevocationWorker {

    private final PasswordResetService passwordResetService;

    public PasswordResetRevocationWorker(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(
            fixedDelayString = "${thi360.identity.password-reset.revocation-retry-interval:PT30S}",
            initialDelayString = "${thi360.identity.password-reset.revocation-retry-interval:PT30S}")
    public void run() {
        try {
            passwordResetService.retryPendingRevocations();
        } finally {
            // Đối soát các lần ghi mật khẩu chưa xác định; lỗi ở bước trên không được chặn bước này.
            passwordResetService.reconcileUnfinalizedWrites();
        }
    }
}
