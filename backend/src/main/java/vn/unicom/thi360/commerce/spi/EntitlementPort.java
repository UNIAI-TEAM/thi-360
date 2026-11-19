package vn.unicom.thi360.commerce.spi;

import java.util.UUID;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Port of the commerce module the attempt module calls (TM 8.5, LT 5.1, LT 12).
 * Implemented inside {@code commerce.entitlement}; every mutating call is
 * idempotent per {@code attemptId}: repeating a call with the same attempt
 * returns the same result and changes nothing else.
 *
 * <p>Contract for implementations (checked by CM-4a / AT-1 tests):
 * <ul>
 *   <li>{@link #describe} and {@link #reserve} throw {@link NotFoundException}
 *       when the entitlement does not exist or belongs to another account.</li>
 *   <li>{@link #reserve} throws {@link ConflictException}
 *       ({@code NO_ATTEMPTS_LEFT}) when the entitlement has no usable attempt.</li>
 *   <li>Calls for an unknown {@code attemptId} or out-of-order usage state
 *       ({@code release} after {@code USED}, {@code confirmUse} after
 *       {@code RELEASED}) change nothing and do not fail.</li>
 *   <li>{@link #compensate} throws {@link ConflictException}
 *       ({@code ALREADY_COMPENSATED} for a second grant on the same attempt,
 *       {@code GRANT_REFUNDED} when the grant the attempt drew from was fully
 *       refunded).</li>
 * </ul>
 *
 * <p>Attempts are consumed in grant order: oldest grant with attempts left
 * first (E21). Benefit flags, {@code result_view} and {@code option_codes}
 * always come from the grant the attempt actually uses.
 */
public interface EntitlementPort {

    /**
     * Reads the entitlement for the prepare step (LT 5.1): attempts left and
     * the grant the next {@code reserve} would consume, with its source
     * (purchase order code and date, or compensation).
     */
    EntitlementDescription describe(UUID userId, UUID entitlementId);

    /**
     * Reserves one attempt on the oldest grant with attempts left. Returns the
     * benefits snapshot of that grant; the same {@code attemptId} always returns
     * the same reservation.
     */
    EntitlementBenefits reserve(UUID userId, UUID entitlementId, UUID attemptId);

    /**
     * Marks the reserved attempt as used; called when the attempt ends, for any
     * end reason of the attempt spec (submitted, time up, choice timeout,
     * fault-closed).
     */
    void confirmUse(UUID attemptId);

    /**
     * Gives the reserved attempt back; called when paper generation fails
     * before the attempt opens. On a grant already fully refunded the attempt
     * goes straight to revoked instead of becoming usable again.
     */
    void release(UUID attemptId);

    /**
     * Returns the benefits of the grant the attempt drew from (E21), including
     * for a reserved-but-not-yet-used attempt.
     */
    EntitlementBenefits benefitsOf(UUID attemptId);

    /**
     * Grants exactly one compensation attempt for a faulted attempt, copying
     * the benefits of the grant the attempt drew from. Each attempt is
     * compensated at most once.
     */
    CompensationResult compensate(UUID attemptId, UUID adminId, String reason);
}
