package vn.unicom.thi360.commerce.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the grant used by an attempt carries (TM 8.5, E21): benefit flags of the
 * {@code entitlement_grant.benefits} column, the result-view level, the purchased
 * serving options and the exam version snapshot of the grant.
 *
 * @param grantId grant the attempt draws from
 * @param examId exam of the grant ({@code entitlement_grant.exam_id}); never {@code null}
 * @param benefits benefit flags of the grant (may be empty, never {@code null})
 * @param resultView result-view level of the grant
 * @param optionCodes purchased serving options (E25)
 * @param examVersionNo exam version snapshot on the grant
 */
public record EntitlementBenefits(
        UUID grantId,
        UUID examId,
        Map<String, Object> benefits,
        String resultView,
        List<String> optionCodes,
        int examVersionNo) {

    public EntitlementBenefits {
        benefits = benefits == null ? Map.of() : Map.copyOf(benefits);
        optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
    }
}
