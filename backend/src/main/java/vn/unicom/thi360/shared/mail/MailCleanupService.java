package vn.unicom.thi360.shared.mail;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Xóa thư đã gửi quá {@code thi360.mail.sent-retention} (mặc định 30 ngày). Thư chưa gửi hoặc thất bại được giữ. */
@Service
class MailCleanupService {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private final EmailMessageStore store;
    private final MailProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    MailCleanupService(
            EmailMessageStore store,
            MailProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** @return số thư đã xóa */
    int deleteExpired() {
        Instant cutoff = Instant.now(clock).minus(properties.sentRetention());
        Integer deleted = tx.execute(status -> store.deleteSentBefore(cutoff));
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            jobLog.atInfo().addKeyValue("deleted", count).log("Sent mail cleaned up");
        }
        return count;
    }
}
