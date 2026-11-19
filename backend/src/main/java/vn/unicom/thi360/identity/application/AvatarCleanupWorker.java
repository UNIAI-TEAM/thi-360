package vn.unicom.thi360.identity.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thử lại việc xóa đối tượng ảnh đại diện đã ghi nhận chờ xóa (xóa trực tiếp sau commit bị lỗi). */
@Component
@Profile("worker")
@ConditionalOnProperty(
        name = "thi360.identity.avatar-cleanup.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AvatarCleanupWorker {

    private final AvatarService avatarService;

    public AvatarCleanupWorker(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @Scheduled(
            fixedDelayString = "${thi360.identity.avatar-cleanup-interval:PT1M}",
            initialDelayString = "${thi360.identity.avatar-cleanup-interval:PT1M}")
    public void run() {
        avatarService.sweepPendingDeletes();
    }
}
