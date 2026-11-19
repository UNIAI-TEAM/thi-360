package vn.unicom.thi360.commerce.spi;

import java.util.UUID;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Port of the commerce module the attempt module calls (TM 8.5, LT 5.1, LT 12).
 * Implemented inside {@code commerce.entitlement}.
 *
 * <p>Repeat and missing-usage behavior (decision AD-8; CM-4a implements, C-0 only
 * states it):
 * <ul>
 *   <li>{@link #reserve}, {@link #confirmUse} and {@link #release} are idempotent per
 *       {@code attemptId}: a repeat returns the same result and does not change any
 *       count (CM-4a AC-5/6/7).</li>
 *   <li>{@link #release} for an {@code attemptId} with no usage is a no-op.</li>
 *   <li>{@link #confirmUse}, {@link #benefitsOf} and {@link #compensate} for an
 *       {@code attemptId} with no usage throw {@link NotFoundException}.</li>
 *   <li>{@link #compensate} a second time for the same attempt fails with 409
 *       {@code ALREADY_COMPENSATED} (CM-4a AC-19). This is not an idempotent
 *       replay: an HTTP replay with the same idempotency key is AT-5 AC-14, a
 *       different layer.</li>
 * </ul>
 *
 * <p>Other failures:
 * <ul>
 *   <li>{@link #describe} and {@link #reserve} throw {@link NotFoundException}
 *       when the entitlement does not exist or belongs to another account; the
 *       ownership check is part of {@code describe}.</li>
 *   <li>{@link #reserve} throws {@link ConflictException}
 *       ({@code NO_ATTEMPTS_LEFT}) when the entitlement has no usable attempt.</li>
 *   <li>{@link #compensate} throws {@link ConflictException}
 *       ({@code GRANT_REFUNDED}) when the grant the attempt drew from was fully
 *       refunded.</li>
 * </ul>
 *
 * <p>Attempts are consumed in grant order: oldest grant with attempts left
 * first (E21). Benefit flags, {@code result_view} and {@code option_codes}
 * always come from the grant the attempt actually uses.
 */
public interface EntitlementPort {

    /**
     * Reads the entitlement for the prepare step (LT 5.1): the exam it is for,
     * attempts left and the grant the next {@code reserve} would consume, with its source
     * (purchase order code and date, or compensation).
     */
    EntitlementDescription describe(UUID userId, UUID entitlementId);

    /**
     * Reserves one attempt on the oldest grant with attempts left. Returns the
     * benefits snapshot of that grant, including the exam of the grant; the same
     * {@code attemptId} always returns the same reservation.
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
