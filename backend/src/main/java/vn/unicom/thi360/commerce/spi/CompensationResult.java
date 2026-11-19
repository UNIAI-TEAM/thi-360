package vn.unicom.thi360.commerce.spi;

import java.util.UUID;

/**
 * Outcome of {@link EntitlementPort#compensate}: the one-attempt compensation
 * grant created for a faulted attempt.
 *
 * @param entitlementId entitlement the grant was added to
 * @param grantId the new {@code COMPENSATION} grant
 */
public record CompensationResult(UUID entitlementId, UUID grantId) {
}
