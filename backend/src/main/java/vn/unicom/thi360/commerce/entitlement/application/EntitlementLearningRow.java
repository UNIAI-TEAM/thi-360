package vn.unicom.thi360.commerce.entitlement.application;

import java.util.UUID;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource;

/**
 * One entitlement as the learning profile lists it (CM-4a "Sản phẩm"): serialized as an element of
 * {@code StudentLearningResponse.entitlements}, which is a list of objects, so the OpenAPI document is unchanged.
 *
 * @param entitlementId the entitlement
 * @param examId exam it is for
 * @param source purchase or class
 * @param attemptsTotal attempts of all grants
 * @param attemptsUsed attempts already used
 * @param attemptsReserved attempts held by attempts that are still open
 * @param attemptsRevoked attempts taken back by a refund
 * @param attemptsLeft attempts still usable
 */
public record EntitlementLearningRow(
        UUID entitlementId,
        UUID examId,
        EntitlementSource source,
        int attemptsTotal,
        int attemptsUsed,
        int attemptsReserved,
        int attemptsRevoked,
        int attemptsLeft) {
}
