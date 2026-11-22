package vn.unicom.thi360.commerce.entitlement.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementUsage;
import vn.unicom.thi360.commerce.entitlement.domain.UsageState;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementDescriptionQuery;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementGrantRepository;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementUsageRepository;
import vn.unicom.thi360.commerce.spi.CompensationResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Commerce side of {@link EntitlementPort} (TM 8.5, LT 5.1, LT 12; AD-8 rules in the port's javadoc).
 *
 * <p>Every write takes the {@code entitlement} row lock first and the {@code entitlement_grant} row second
 * (plan B2), and changes the grant and the entitlement counters in step. Each call joins the transaction of the
 * attempt module, or opens its own when there is none.
 */
@Service
public class EntitlementPortImpl implements EntitlementPort {

    static final String ERROR_NO_ATTEMPTS_LEFT = "NO_ATTEMPTS_LEFT";
    static final String ERROR_GRANT_REFUNDED = "GRANT_REFUNDED";
    static final String ERROR_ALREADY_COMPENSATED = "ALREADY_COMPENSATED";

    private final EntitlementRowLocker locker;
    private final EntitlementRepository entitlementRepository;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementUsageRepository usageRepository;
    private final EntitlementDescriptionQuery descriptionQuery;
    private final Clock clock;

    public EntitlementPortImpl(
            EntitlementRowLocker locker,
            EntitlementRepository entitlementRepository,
            EntitlementGrantRepository grantRepository,
            EntitlementUsageRepository usageRepository,
            EntitlementDescriptionQuery descriptionQuery,
            Clock clock) {
        this.locker = locker;
        this.entitlementRepository = entitlementRepository;
        this.grantRepository = grantRepository;
        this.usageRepository = usageRepository;
        this.descriptionQuery = descriptionQuery;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementDescription describe(UUID userId, UUID entitlementId) {
        return descriptionQuery.find(userId, entitlementId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    @Override
    @Transactional
    public EntitlementBenefits reserve(UUID userId, UUID entitlementId, UUID attemptId) {
        // Ownership is checked without loading the row, so the entity read after the lock is fresh.
        if (!entitlementRepository.existsByIdAndUserId(entitlementId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        Entitlement entitlement = locker.lockById(entitlementId);

        EntitlementUsage existing = usageRepository.findByAttemptId(attemptId).orElse(null);
        if (existing != null) {
            return replayReservation(existing, entitlement);
        }

        List<EntitlementGrant> next =
                grantRepository.lockWithAttemptsLeft(entitlement.getId(), PageRequest.of(0, 1));
        if (next.isEmpty()) {
            throw new ConflictException(ERROR_NO_ATTEMPTS_LEFT);
        }
        EntitlementGrant grant = next.get(0);
        Instant now = clock.instant();
        grant.reserveOne();
        entitlement.reserveOne(now);
        usageRepository.saveAndFlush(new EntitlementUsage(Ids.newId(), grant.getId(), attemptId, now));
        return benefitsOf(grant);
    }

    @Override
    @Transactional
    public void confirmUse(UUID attemptId) {
        UUID grantId = usageRepository.findGrantIdByAttemptId(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Locked locked = lockUsage(grantId, attemptId);
        if (locked.usage().getState() != UsageState.RESERVED) {
            return;
        }
        Instant now = clock.instant();
        locked.grant().confirmOne();
        locked.entitlement().confirmOne(now);
        locked.usage().markUsed(now);
    }

    @Override
    @Transactional
    public void release(UUID attemptId) {
        UUID grantId = usageRepository.findGrantIdByAttemptId(attemptId).orElse(null);
        if (grantId == null) {
            return;
        }
        Locked locked = lockUsage(grantId, attemptId);
        if (locked.usage().getState() != UsageState.RESERVED) {
            return;
        }
        Instant now = clock.instant();
        if (locked.grant().isRefunded()) {
            locked.grant().releaseOneToRevoked();
            locked.entitlement().releaseOneToRevoked(now);
        } else {
            locked.grant().releaseOne();
            locked.entitlement().releaseOne(now);
        }
        locked.usage().markReleased(now);
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementBenefits benefitsOf(UUID attemptId) {
        UUID grantId = usageRepository.findGrantIdByAttemptId(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return benefitsOf(grantRepository.findById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null)));
    }

    @Override
    @Transactional
    public CompensationResult compensate(UUID attemptId, UUID adminId, String reason) {
        UUID grantId = usageRepository.findGrantIdByAttemptId(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        UUID entitlementId = grantRepository.findEntitlementIdById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Entitlement entitlement = locker.lockById(entitlementId);
        if (grantRepository.findByCompensationAttemptId(attemptId).isPresent()) {
            throw new ConflictException(ERROR_ALREADY_COMPENSATED);
        }
        EntitlementGrant source = grantRepository.lockById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (source.isRefunded()) {
            throw new ConflictException(ERROR_GRANT_REFUNDED);
        }
        Instant now = clock.instant();
        EntitlementGrant compensation = EntitlementGrant.compensation(
                Ids.newId(), source, attemptId, now, String.valueOf(adminId), reason);
        grantRepository.saveAndFlush(compensation);
        entitlement.addAttempts(compensation.getAttempts(), now);
        return new CompensationResult(entitlement.getId(), compensation.getId());
    }

    /**
     * A repeated {@code reserve} returns the reservation it already made, whatever state that reservation is in
     * now: it changes no count (AD-8). An attempt id that belongs to another entitlement is a caller error and a
     * plain conflict. AT-1 uses a fresh attempt id to reserve the slot of a released attempt again.
     */
    private EntitlementBenefits replayReservation(EntitlementUsage usage, Entitlement entitlement) {
        EntitlementGrant grant = grantRepository.findById(usage.getGrantId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!grant.getEntitlementId().equals(entitlement.getId())) {
            throw new ConflictException("CONFLICT");
        }
        return benefitsOf(grant);
    }

    /** Locks entitlement, grant and usage of an attempt in the plan B2 order. */
    private Locked lockUsage(UUID grantId, UUID attemptId) {
        UUID entitlementId = grantRepository.findEntitlementIdById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Entitlement entitlement = locker.lockById(entitlementId);
        EntitlementGrant grant = grantRepository.lockById(grantId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        EntitlementUsage usage = usageRepository.lockByAttemptId(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return new Locked(entitlement, grant, usage);
    }

    private static EntitlementBenefits benefitsOf(EntitlementGrant grant) {
        return new EntitlementBenefits(
                grant.getId(),
                grant.getExamId(),
                grant.getBenefits(),
                grant.getResultView(),
                grant.getOptionCodes(),
                grant.getExamVersionNo());
    }

    private record Locked(Entitlement entitlement, EntitlementGrant grant, EntitlementUsage usage) {
    }
}
