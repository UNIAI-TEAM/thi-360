package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.shared.api.NotFoundException;

class EntitlementRevokeIT extends EntitlementIntegrationTest {

    @Autowired
    private EntitlementGrantService service;

    /**
     * AC-15: revoking takes the attempts neither used nor reserved and leaves the reserved one alone; a repeat
     * changes nothing. The used and reserved attempts are set up directly in the rows, in step on grant and
     * entitlement, because the port that makes them arrives in the next slice.
     */
    @Test
    void revokeTakesOnlyUnusedAttempts() {
        GrantOrderResult.Line granted = grant(newId(), newId(), 5);
        markUsedAndReserved(granted, 1, 1);

        int revoked = service.revokeUnused(granted.grantId());

        assertThat(revoked).isEqualTo(3);
        Map<String, Object> grant = grantRow(granted.grantId());
        assertThat(count(grant, "attempts_used")).isEqualTo(1);
        assertThat(count(grant, "attempts_reserved")).isEqualTo(1);
        assertThat(count(grant, "attempts_revoked")).isEqualTo(3);
        assertThat(grant.get("revoked_at")).isNotNull();
        Map<String, Object> entitlement = entitlementRow(granted.entitlementId());
        assertThat(count(entitlement, "attempts_total")).isEqualTo(5);
        assertThat(count(entitlement, "attempts_used")).isEqualTo(1);
        assertThat(count(entitlement, "attempts_reserved")).isEqualTo(1);
        assertThat(count(entitlement, "attempts_revoked")).isEqualTo(3);
        assertCountsEqualGrantSums(granted.entitlementId());

        Object revokedAt = grant.get("revoked_at");
        assertThat(service.revokeUnused(granted.grantId())).isZero();
        assertThat(count(grantRow(granted.grantId()), "attempts_revoked")).isEqualTo(3);
        assertThat(grantRow(granted.grantId()).get("revoked_at")).isEqualTo(revokedAt);
        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_revoked")).isEqualTo(3);
        assertCountsEqualGrantSums(granted.entitlementId());
    }

    /** Boundary: a grant used up before the refund has nothing to revoke but is still marked refunded. */
    @Test
    void revokeOfFullyUsedGrantMarksItRefundedWithoutRevokingAttempts() {
        GrantOrderResult.Line granted = grant(newId(), newId(), 1);
        markUsedAndReserved(granted, 1, 0);

        assertThat(service.revokeUnused(granted.grantId())).isZero();

        Map<String, Object> grant = grantRow(granted.grantId());
        assertThat(grant.get("revoked_at")).isNotNull();
        assertThat(count(grant, "attempts_revoked")).isZero();
        assertThat(count(grant, "attempts_used")).isEqualTo(1);
        assertCountsEqualGrantSums(granted.entitlementId());
    }

    /** Only the refunded grant is touched: another grant of the same entitlement keeps its attempts. */
    @Test
    void revokeLeavesOtherGrantsOfTheEntitlementAlone() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line refunded = grant(userId, examId, 2);
        GrantOrderResult.Line kept = grant(userId, examId, 4);

        assertThat(service.revokeUnused(refunded.grantId())).isEqualTo(2);

        assertThat(count(grantRow(kept.grantId()), "attempts_revoked")).isZero();
        assertThat(grantRow(kept.grantId()).get("revoked_at")).isNull();
        Map<String, Object> entitlement = entitlementRow(refunded.entitlementId());
        assertThat(count(entitlement, "attempts_total")).isEqualTo(6);
        assertThat(count(entitlement, "attempts_revoked")).isEqualTo(2);
        assertCountsEqualGrantSums(refunded.entitlementId());
    }

    @Test
    void revokeOfUnknownGrantIsNotFound() {
        UUID unknown = newId();

        assertThatThrownBy(() -> service.revokeUnused(unknown))
                .isInstanceOf(NotFoundException.class);
    }

    private void markUsedAndReserved(GrantOrderResult.Line granted, int used, int reserved) {
        jdbc.update("UPDATE commerce.entitlement_grant SET attempts_used = ?, attempts_reserved = ? WHERE id = ?",
                used, reserved, granted.grantId());
        jdbc.update("UPDATE commerce.entitlement SET attempts_used = ?, attempts_reserved = ? WHERE id = ?",
                used, reserved, granted.entitlementId());
    }
}
