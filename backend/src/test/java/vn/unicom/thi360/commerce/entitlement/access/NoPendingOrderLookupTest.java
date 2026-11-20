package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.entitlement.access.application.NoPendingOrderLookup;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup;

/**
 * States the INTERIM behavior of plan B1 (CM-4b AC-22): until CM-3a ships the order tables, no order is ever reported as
 * paid-but-unfulfilled, whatever the account. CM-3a replaces this class and this test with the real lookup.
 */
class NoPendingOrderLookupTest {

    private final PendingOrderLookup lookup = new NoPendingOrderLookup();

    @Test
    void interimLookupAlwaysReportsNoPendingOrder() {
        assertThat(lookup.findPaidUnfulfilled(UUID.randomUUID())).isEmpty();
        assertThat(lookup.findPaidUnfulfilled(UUID.randomUUID())).isEmpty();
    }
}
