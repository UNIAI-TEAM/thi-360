package vn.unicom.thi360.identity.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đối soát các lần ghi mật khẩu có dấu ghi trước mà chưa chốt (TM 5.4, AC-12): thu hồi phiên cũ (Redis và Keycloak,
 * fail-closed) rồi chốt dòng, kể cả khi tiến trình chết sau khi Keycloak đổi mật khẩu hoặc thu hồi lỗi. Không bao giờ
 * ghi lại mật khẩu ({@link PasswordResetService#reconcileUnfinalizedWrites}).
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
        passwordResetService.reconcileUnfinalizedWrites();
    }
}
