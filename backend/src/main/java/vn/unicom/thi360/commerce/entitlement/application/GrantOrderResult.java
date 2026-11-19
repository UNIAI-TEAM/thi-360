package vn.unicom.thi360.commerce.entitlement.application;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of {@link EntitlementGrantService#grantForOrder}, one entry per exam line in exam id order.
 *
 * @param lines the grant of each line
 */
public record GrantOrderResult(List<Line> lines) {

    public GrantOrderResult {
        lines = List.copyOf(lines);
    }

    /**
     * @param examId exam of the line
     * @param entitlementId entitlement the grant belongs to
     * @param grantId grant of this order and exam
     * @param created {@code false} when the grant already existed (replay of the same order): nothing changed
     */
    public record Line(UUID examId, UUID entitlementId, UUID grantId, boolean created) {
    }
}
