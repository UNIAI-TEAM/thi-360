package vn.unicom.thi360.commerce.entitlement.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One exam of an order as {@link EntitlementGrantService#grantForOrder} receives it: the snapshot the order
 * took (E21, E25), copied onto the grant.
 *
 * @param examId exam of the order line
 * @param examVersionNo exam version the order snapshot refers to
 * @param attempts attempts the line grants, at least 1
 * @param optionCodes serving options bought with the line (E25)
 * @param benefits benefit flags of the line
 * @param resultView result-view level of the line
 */
public record GrantLine(
        UUID examId,
        int examVersionNo,
        int attempts,
        List<String> optionCodes,
        Map<String, Object> benefits,
        String resultView) {

    public GrantLine {
        if (examId == null) {
            throw new IllegalArgumentException("examId must not be null");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1");
        }
        if (resultView == null || resultView.isBlank()) {
            throw new IllegalArgumentException("resultView must not be blank");
        }
        optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
        benefits = benefits == null ? Map.of() : Map.copyOf(benefits);
    }
}
