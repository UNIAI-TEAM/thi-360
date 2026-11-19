package vn.unicom.thi360.commerce.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of an entitlement as shown before starting an attempt (LT 5.1):
 * how many attempts are left and which grant (oldest with attempts, E21) the
 * next {@code reserve} will consume.
 *
 * @param examId exam the entitlement (and every grant of it) is for, i.e.
 *               {@code entitlement_grant.exam_id}; never {@code null}. The attempt
 *               module needs it to find the published exam it serves and to persist
 *               {@code exam_id} on the attempt (LT 4), without a second port.
 * @param attemptsLeft attempts still usable on the entitlement
 * @param grantId the grant the next {@code reserve} would draw from, or
 *                {@code null} when nothing is left
 * @param source purchase or compensation
 * @param orderCode order code of the grant ({@code null} for compensation grants)
 * @param grantedAt when the grant was created ({@code null} when {@code grantId} is)
 * @param optionCodes purchased serving options of that grant (E25)
 */
public record EntitlementDescription(
        UUID examId,
        int attemptsLeft,
        UUID grantId,
        GrantSource source,
        String orderCode,
        Instant grantedAt,
        List<String> optionCodes) {

    public EntitlementDescription {
        optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
    }
}
