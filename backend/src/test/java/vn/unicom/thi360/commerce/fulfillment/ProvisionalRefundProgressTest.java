package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.fulfillment.application.ProvisionalRefundProgress;

/** Kiểm tra {@link ProvisionalRefundProgress} luôn cho phép cấp quyền cho tới khi CM-4a thay thế. */
class ProvisionalRefundProgressTest {

    @Test
    void alwaysReturnsFalseWhenNoRealRefundModuleIsInstalled() {
        ProvisionalRefundProgress progress = new ProvisionalRefundProgress();
        assertThat(progress.hasNonRejectedRefund(UUID.randomUUID())).isFalse();
        assertThat(progress.hasNonRejectedRefund(null)).isFalse();
    }
}
