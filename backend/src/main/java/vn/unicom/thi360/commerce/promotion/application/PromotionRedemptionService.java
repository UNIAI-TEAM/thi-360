package vn.unicom.thi360.commerce.promotion.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Atomic slot reservation, confirmation, release, and late-payment reclaim for promotions and vouchers (TM 7.2, 8.6,
 * E23).
 *
 * <p>Uses {@link JdbcTemplate} directly inside the caller's transaction (plan B2 lock order: {@code purchase_order} ->
 * {@code promotion}) so row locks ({@code FOR UPDATE}) and conditional {@code UPDATE} statements never interact with a
 * caller's Hibernate L1 cache.
 */
@Service
public class PromotionRedemptionService {

    private static final String LOCK_PROMOTION_SQL = """
            SELECT id, status, starts_at, ends_at, total_limit, per_user_limit, used_count, over_limit_count, deleted_at
            FROM commerce.promotion
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String LOCK_REDEMPTION_SQL = """
            SELECT id, promotion_id, user_id, order_id, order_code, discount_vnd, state, created_at, updated_at
            FROM commerce.promotion_redemption
            WHERE promotion_id = ? AND order_id = ?
            FOR UPDATE
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PromotionRedemptionService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Convenience overload without {@code orderCode}. */
    @Transactional
    public RedemptionView reserve(UUID promotionId, UUID userId, UUID orderId, long discountVnd) {
        return reserve(promotionId, userId, orderId, null, discountVnd);
    }

    /**
     * Reserves one slot on {@code promotionId} for {@code (userId, orderId)} (TM 7.2). Idempotent when a
     * {@code RESERVED} or {@code CONFIRMED} redemption already exists for {@code (promotionId, orderId)}.
     */
    @Transactional
    public RedemptionView reserve(UUID promotionId, UUID userId, UUID orderId, String orderCode, long discountVnd) {
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (discountVnd < 0) {
            throw new IllegalArgumentException("discountVnd must not be negative");
        }

        LockedPromotion promo = lockPromotion(promotionId);
        if (promo == null || promo.deletedAt() != null) {
            throw new BusinessRuleException("VOUCHER_INVALID");
        }

        RedemptionView existing = lockRedemption(promotionId, orderId);
        if (existing != null) {
            if (!existing.userId().equals(userId)) {
                throw new ConflictException("CONFLICT");
            }
            if (existing.state() == RedemptionState.RESERVED || existing.state() == RedemptionState.CONFIRMED) {
                return existing;
            }
            throw new ConflictException("CONFLICT");
        }

        Instant now = clock.instant();
        if (promo.status() != PromotionStatus.ACTIVE || now.isBefore(promo.startsAt())) {
            throw new BusinessRuleException("VOUCHER_INVALID");
        }
        if (!now.isBefore(promo.endsAt())) {
            throw new BusinessRuleException("VOUCHER_EXPIRED");
        }

        if (promo.perUserLimit() != null) {
            Integer userCount = jdbc.queryForObject("""
                    SELECT count(*) FROM commerce.promotion_redemption
                    WHERE promotion_id = ? AND user_id = ? AND state IN ('RESERVED', 'CONFIRMED')
                    """, Integer.class, promotionId, userId);
            if (userCount != null && userCount >= promo.perUserLimit()) {
                throw new BusinessRuleException("VOUCHER_USER_LIMIT");
            }
        }

        int updated = jdbc.update("""
                UPDATE commerce.promotion
                SET used_count = used_count + 1, updated_at = ?
                WHERE id = ? AND (total_limit IS NULL OR used_count < total_limit)
                """, Timestamp.from(now), promotionId);
        if (updated == 0) {
            throw new BusinessRuleException("VOUCHER_EXHAUSTED");
        }

        UUID redemptionId = Ids.newId();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("""
                INSERT INTO commerce.promotion_redemption
                    (id, promotion_id, user_id, order_id, order_code, discount_vnd, state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'RESERVED', ?, ?)
                """, redemptionId, promotionId, userId, orderId, orderCode, discountVnd, ts, ts);

        return new RedemptionView(redemptionId, promotionId, userId, orderId, orderCode, discountVnd,
                RedemptionState.RESERVED, now, now);
    }

    /**
     * Reserves multiple promotions for one order in ascending {@code promotionId} order to avoid lock cycles (plan B2).
     */
    @Transactional
    public List<RedemptionView> reserveForOrder(UUID userId, UUID orderId, String orderCode,
            List<ReserveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<ReserveRequest> ordered = new ArrayList<>(requests);
        ordered.sort((a, b) -> compareUuidUnsigned(a.promotionId(), b.promotionId()));
        List<RedemptionView> result = new ArrayList<>(ordered.size());
        for (ReserveRequest req : ordered) {
            result.add(reserve(req.promotionId(), userId, orderId, orderCode, req.discountVnd()));
        }
        return result;
    }

    /** Matches PostgreSQL {@code uuid} byte/ASC ordering (unsigned 128-bit big-endian). */
    static int compareUuidUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    /**
     * Confirms a reserved redemption when payment succeeds or a 0-VND order is placed (TM 7.2, AC-16, AC-22). Works even
     * if the promotion has since been stopped or expired. Idempotent on {@code CONFIRMED}; rejects {@code RELEASED}.
     */
    @Transactional
    public RedemptionView confirm(UUID promotionId, UUID orderId) {
        if (lockPromotion(promotionId) == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        RedemptionView existing = requireRedemption(promotionId, orderId);
        if (existing.state() == RedemptionState.CONFIRMED) {
            return existing;
        }
        if (existing.state() == RedemptionState.RELEASED) {
            throw new ConflictException("CONFLICT");
        }
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE commerce.promotion_redemption
                SET state = 'CONFIRMED', updated_at = ?
                WHERE id = ?
                """, Timestamp.from(now), existing.id());
        return existing.withState(RedemptionState.CONFIRMED, now);
    }

    /**
     * Releases a {@code RESERVED} redemption when an order expires, is cancelled, or is replaced (TM 7.2, AC-16).
     * Idempotent on {@code RELEASED}. Never releases a {@code CONFIRMED} redemption (AC-17, E23).
     */
    @Transactional
    public RedemptionView release(UUID promotionId, UUID orderId) {
        if (lockPromotion(promotionId) == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        RedemptionView existing = requireRedemption(promotionId, orderId);
        if (existing.state() != RedemptionState.RESERVED) {
            return existing;
        }
        Instant now = clock.instant();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("""
                UPDATE commerce.promotion_redemption
                SET state = 'RELEASED', updated_at = ?
                WHERE id = ?
                """, ts, existing.id());
        jdbc.update("""
                UPDATE commerce.promotion
                SET used_count = used_count - 1, updated_at = ?
                WHERE id = ? AND used_count > 0
                """, ts, promotionId);
        return existing.withState(RedemptionState.RELEASED, now);
    }

    /**
     * Reclaims a redemption for late payment after order expiration/replacement/cancellation (TM 8.6, AC-18).
     * Transitions {@code RELEASED -> CONFIRMED} and increments {@code used_count}, allowing it to exceed
     * {@code total_limit} while incrementing {@code over_limit_count}. Idempotent on {@code CONFIRMED}.
     */
    @Transactional
    public RedemptionView reclaim(UUID promotionId, UUID orderId) {
        if (lockPromotion(promotionId) == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        RedemptionView existing = requireRedemption(promotionId, orderId);
        if (existing.state() == RedemptionState.CONFIRMED) {
            return existing;
        }
        Instant now = clock.instant();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("""
                UPDATE commerce.promotion_redemption
                SET state = 'CONFIRMED', updated_at = ?
                WHERE id = ?
                """, ts, existing.id());
        if (existing.state() == RedemptionState.RELEASED) {
            jdbc.update("""
                    UPDATE commerce.promotion
                    SET used_count = used_count + 1,
                        over_limit_count = CASE
                            WHEN total_limit IS NOT NULL AND used_count + 1 > total_limit
                            THEN over_limit_count + 1
                            ELSE over_limit_count
                        END,
                        updated_at = ?
                    WHERE id = ?
                    """, ts, promotionId);
        }
        return existing.withState(RedemptionState.CONFIRMED, now);
    }

    /** Confirms all redemptions of an order, locking promotions in ascending {@code promotion_id} order. */
    @Transactional
    public List<RedemptionView> confirmByOrder(UUID orderId) {
        List<UUID> promotionIds = redemptionPromotionIdsByOrder(orderId);
        List<RedemptionView> results = new ArrayList<>(promotionIds.size());
        for (UUID promotionId : promotionIds) {
            results.add(confirm(promotionId, orderId));
        }
        return results;
    }

    /** Releases all redemptions of an order, locking promotions in ascending {@code promotion_id} order. */
    @Transactional
    public List<RedemptionView> releaseByOrder(UUID orderId) {
        List<UUID> promotionIds = redemptionPromotionIdsByOrder(orderId);
        List<RedemptionView> results = new ArrayList<>(promotionIds.size());
        for (UUID promotionId : promotionIds) {
            results.add(release(promotionId, orderId));
        }
        return results;
    }

    /** Reclaims all redemptions of an order, locking promotions in ascending {@code promotion_id} order. */
    @Transactional
    public List<RedemptionView> reclaimByOrder(UUID orderId) {
        List<UUID> promotionIds = redemptionPromotionIdsByOrder(orderId);
        List<RedemptionView> results = new ArrayList<>(promotionIds.size());
        for (UUID promotionId : promotionIds) {
            results.add(reclaim(promotionId, orderId));
        }
        return results;
    }

    private List<UUID> redemptionPromotionIdsByOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return jdbc.queryForList("""
                SELECT promotion_id FROM commerce.promotion_redemption
                WHERE order_id = ?
                ORDER BY promotion_id ASC
                """, UUID.class, orderId);
    }

    private LockedPromotion lockPromotion(UUID promotionId) {
        return jdbc.query(LOCK_PROMOTION_SQL, (rs, n) -> new LockedPromotion(
                rs.getObject("id", UUID.class),
                PromotionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant(),
                rs.getObject("total_limit", Integer.class),
                rs.getObject("per_user_limit", Integer.class),
                rs.getInt("used_count"),
                rs.getInt("over_limit_count"),
                toInstant(rs.getTimestamp("deleted_at"))), promotionId)
                .stream().findFirst().orElse(null);
    }

    private RedemptionView lockRedemption(UUID promotionId, UUID orderId) {
        return jdbc.query(LOCK_REDEMPTION_SQL, PromotionRedemptionService::mapRedemption, promotionId, orderId)
                .stream().findFirst().orElse(null);
    }

    private RedemptionView requireRedemption(UUID promotionId, UUID orderId) {
        RedemptionView view = lockRedemption(promotionId, orderId);
        if (view == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return view;
    }

    private static RedemptionView mapRedemption(ResultSet rs, int rowNum) throws SQLException {
        return new RedemptionView(
                rs.getObject("id", UUID.class),
                rs.getObject("promotion_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("order_code"),
                rs.getLong("discount_vnd"),
                RedemptionState.valueOf(rs.getString("state")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private record LockedPromotion(UUID id, PromotionStatus status, Instant startsAt, Instant endsAt,
            Integer totalLimit, Integer perUserLimit, int usedCount, int overLimitCount, Instant deletedAt) {
    }

    public record ReserveRequest(UUID promotionId, long discountVnd) {
    }

    public record RedemptionView(UUID id, UUID promotionId, UUID userId, UUID orderId, String orderCode,
            long discountVnd, RedemptionState state, Instant createdAt, Instant updatedAt) {

        RedemptionView withState(RedemptionState newState, Instant now) {
            return new RedemptionView(id, promotionId, userId, orderId, orderCode, discountVnd, newState,
                    createdAt, now);
        }
    }
}
