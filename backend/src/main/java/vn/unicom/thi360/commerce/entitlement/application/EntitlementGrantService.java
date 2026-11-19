package vn.unicom.thi360.commerce.entitlement.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementGrantRepository;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Writes grants for the other commerce tasks (plan B1: not part of {@code commerce.spi}).
 * {@link #grantForOrder} is called by fulfillment (CM-3a, CM-6), {@link #revokeUnused} by refunds (CM-5).
 * Both join the caller's transaction, or open their own when there is none.
 */
@Service
public class EntitlementGrantService {

    static final String GRANTED_BY_SYSTEM = "SYSTEM";

    private final EntitlementRowLocker locker;
    private final EntitlementGrantRepository grantRepository;
    private final Clock clock;

    public EntitlementGrantService(
            EntitlementRowLocker locker, EntitlementGrantRepository grantRepository, Clock clock) {
        this.locker = locker;
        this.grantRepository = grantRepository;
        this.clock = clock;
    }

    /**
     * Grants every line of the order (TM 8.5 steps 2 and 3). Lines are locked in ascending {@code exam_id}
     * whatever order they arrive in, so two orders sharing exams never wait on each other in a cycle (B2).
     * A line whose {@code (order_id, exam_id)} already has a grant is skipped as already granted: replaying an
     * order adds nothing. A new line adds its attempts to the existing entitlement of (user, exam), or to a new one
     * (C13); the grant keeps its own option codes, benefits and result view (E21, E25).
     */
    @Transactional
    public GrantOrderResult grantForOrder(GrantOrderCommand command) {
        List<GrantLine> ordered = new ArrayList<>(command.lines());
        ordered.sort(Comparator.comparing(GrantLine::examId));

        List<GrantOrderResult.Line> results = new ArrayList<>(ordered.size());
        for (GrantLine line : ordered) {
            Entitlement entitlement = locker.lockOrCreatePurchase(command.userId(), line.examId());
            Optional<EntitlementGrant> existing =
                    grantRepository.findByOrderIdAndExamId(command.orderId(), line.examId());
            if (existing.isPresent()) {
                results.add(new GrantOrderResult.Line(
                        line.examId(), entitlement.getId(), existing.get().getId(), false));
                continue;
            }
            Instant now = clock.instant();
            EntitlementGrant grant = EntitlementGrant.purchase(
                    Ids.newId(), entitlement.getId(), command.orderId(), command.orderCode(), line.examId(),
                    line.optionCodes(), line.attempts(), line.benefits(), line.resultView(),
                    line.examVersionNo(), now, GRANTED_BY_SYSTEM);
            grantRepository.saveAndFlush(grant);
            entitlement.addAttempts(line.attempts(), now);
            results.add(new GrantOrderResult.Line(line.examId(), entitlement.getId(), grant.getId(), true));
        }
        return new GrantOrderResult(results);
    }

    /**
     * Refunds the grant (TM 9.4 step 6, C15): revokes the attempts neither used nor reserved and marks the grant
     * refunded, so a reserved attempt that is handed back later is revoked too and a compensation is refused.
     * Safe to repeat. Returns how many attempts this call revoked.
     */
    @Transactional
    public int revokeUnused(UUID grantId) {
        UUID entitlementId = grantRepository.findEntitlementIdById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Entitlement entitlement = locker.lockById(entitlementId);
        EntitlementGrant grant = grantRepository.lockById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Instant now = clock.instant();
        int revoked = grant.revokeUnused(now);
        if (revoked > 0) {
            entitlement.revoke(revoked, now);
        }
        return revoked;
    }
}
