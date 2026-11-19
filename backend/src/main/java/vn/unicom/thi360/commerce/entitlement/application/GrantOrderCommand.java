package vn.unicom.thi360.commerce.entitlement.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The grant request of one paid order (TM 8.5 step 2 and 3): the order, its code and one line per exam.
 *
 * @param orderId order being fulfilled
 * @param orderCode order code shown to the buyer (LT 5.1), copied onto each grant
 * @param userId buyer
 * @param lines one line per exam of the order, at least one, no exam twice
 */
public record GrantOrderCommand(UUID orderId, String orderCode, UUID userId, List<GrantLine> lines) {

    public GrantOrderCommand {
        if (orderId == null || userId == null) {
            throw new IllegalArgumentException("orderId and userId must not be null");
        }
        if (orderCode == null || orderCode.isBlank()) {
            throw new IllegalArgumentException("orderCode must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
        Set<UUID> exams = new HashSet<>();
        for (GrantLine line : lines) {
            if (!exams.add(line.examId())) {
                throw new IllegalArgumentException("exam " + line.examId() + " appears twice in the order");
            }
        }
    }
}
