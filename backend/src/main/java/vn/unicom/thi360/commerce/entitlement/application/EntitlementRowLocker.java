package vn.unicom.thi360.commerce.entitlement.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Takes the {@code entitlement} row lock that opens every entitlement write (plan B2 order:
 * {@code entitlement} then {@code entitlement_grant}). Kept apart from the services so a test can observe the
 * moment a lock is taken.
 */
@Component
public class EntitlementRowLocker {

    private final EntitlementRepository entitlementRepository;
    private final Clock clock;

    public EntitlementRowLocker(EntitlementRepository entitlementRepository, Clock clock) {
        this.entitlementRepository = entitlementRepository;
        this.clock = clock;
    }

    /**
     * Locks the purchase entitlement of (user, exam), creating the empty row first when it is missing. Two
     * concurrent callers end up on the same row (unique {@code NULLS NOT DISTINCT}), one after the other.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Entitlement lockOrCreatePurchase(UUID userId, UUID examId) {
        entitlementRepository.insertPurchaseIfAbsent(Ids.newId(), userId, examId, clock.instant());
        return entitlementRepository.lockPurchase(userId, examId)
                .orElseThrow(() -> new IllegalStateException("Entitlement row vanished after insert"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Entitlement lockOrCreateClass(UUID userId, UUID examId, UUID classId) {
        entitlementRepository.insertClassIfAbsent(Ids.newId(), userId, examId, classId, clock.instant());
        return entitlementRepository.lockClass(userId, examId, classId)
                .orElseThrow(() -> new IllegalStateException("Class entitlement row vanished after insert"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Entitlement lockById(UUID entitlementId) {
        return entitlementRepository.lockById(entitlementId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }
}
