package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-33: Verifies PostgreSQL constraints on {@code commerce.promotion*}, {@code commerce.promotion_redemption},
 * and {@code commerce.promotion_change_log}, running on full Flyway + {@code db/devdata} schema.
 */
class PromotionConstraintsIT extends PromotionIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-21T00:00:00Z");

    @Test
    void databaseConstraintsRejectInvalidRows() {
        UUID validAutoId = insertAuto("Valid Auto", "PERCENT", 10L, null, START, END, 5, 0);
        assertThat(validAutoId).isNotNull();

        // discount_value <= 0
        assertThatThrownBy(() -> insertAuto("Zero value", "AMOUNT", 0L, null, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuto("Negative value", "AMOUNT", -100L, null, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // PERCENT outside 1..100
        assertThatThrownBy(() -> insertAuto("Zero percent", "PERCENT", 0L, null, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuto("Over 100 percent", "PERCENT", 101L, null, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // max_discount_vnd only allowed for PERCENT and > 0
        assertThatThrownBy(() -> insertAuto("Amount with cap", "AMOUNT", 10_000L, 5_000L, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuto("Zero cap", "PERCENT", 10L, 0L, START, END, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // ends_at <= starts_at (equal and before)
        assertThatThrownBy(() -> insertAuto("Equal times", "AMOUNT", 10_000L, null, START, START, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuto("Reversed times", "AMOUNT", 10_000L, null, END, START, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // total_limit < 1 (0, -1)
        assertThatThrownBy(() -> insertAuto("Zero limit", "AMOUNT", 10_000L, null, START, END, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuto("Negative limit", "AMOUNT", 10_000L, null, START, END, -1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // used_count < 0
        assertThatThrownBy(() -> insertAuto("Negative used", "AMOUNT", 10_000L, null, START, END, 5, -1))
                .isInstanceOf(DataIntegrityViolationException.class);

        // CRITICAL: used_count > total_limit MUST be allowed by DB (late reclaim, TM 7.1 / 8.6)
        UUID lateReclaimedId = insertAuto("Late reclaim over limit", "AMOUNT", 10_000L, null, START, END, 1, 2);
        assertThat(count(promotionRow(lateReclaimedId), "used_count")).isEqualTo(2);

        // promotion_scope_item must have exactly one of exam_item_id, exam_id, bundle_id
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, exam_item_id, exam_id, bundle_id)
                VALUES (?, ?, NULL, NULL, NULL)
                """, Ids.newId(), validAutoId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, exam_item_id, exam_id, bundle_id)
                VALUES (?, ?, ?, ?, NULL)
                """, Ids.newId(), validAutoId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, exam_item_id, exam_id, bundle_id)
                VALUES (?, ?, NULL, ?, ?)
                """, Ids.newId(), validAutoId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // promotion_audience must have exactly one of class_id, user_id
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_audience (id, promotion_id, class_id, user_id)
                VALUES (?, ?, NULL, NULL)
                """, Ids.newId(), validAutoId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_audience (id, promotion_id, class_id, user_id)
                VALUES (?, ?, ?, ?)
                """, Ids.newId(), validAutoId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Duplicate (promotion_id, order_id) in promotion_redemption rejected
        UUID userId = UUID.randomUUID();
        UUID orderId = stubOrder(userId);
        jdbc.update("""
                INSERT INTO commerce.promotion_redemption
                    (id, promotion_id, user_id, order_id, discount_vnd, state, created_at, updated_at)
                VALUES (?, ?, ?, ?, 5000, 'RESERVED', ?, ?)
                """, Ids.newId(), validAutoId, userId, orderId, Timestamp.from(START), Timestamp.from(START));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion_redemption
                    (id, promotion_id, user_id, order_id, discount_vnd, state, created_at, updated_at)
                VALUES (?, ?, ?, ?, 5000, 'RESERVED', ?, ?)
                """, Ids.newId(), validAutoId, userId, orderId, Timestamp.from(START), Timestamp.from(START)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Unique code_normalized across all vouchers including stopped/soft-deleted
        String code = "UNIQ-" + UUID.randomUUID().toString().substring(0, 6);
        UUID v1 = insertVoucher(code, code.toLowerCase(), "STOPPED", START);
        assertThat(v1).isNotNull();
        assertThatThrownBy(() -> insertVoucher(code.toLowerCase(), code.toLowerCase(), "ACTIVE", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Multiple AUTO promotions with NULL code_normalized allowed
        UUID secondAuto = insertAuto("Second Auto", "AMOUNT", 5_000L, null, START, END, null, 0);
        assertThat(secondAuto).isNotNull();

        // AUTO rejecting voucher fields
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce.promotion
                    (id, kind, name, code, code_normalized, discount_type, discount_value, starts_at, ends_at,
                     scope, status, created_at, updated_at)
                VALUES (?, 'AUTO', 'Bad Auto', 'ABC', 'abc', 'AMOUNT', 1000, ?, ?, 'ALL', 'ACTIVE', ?, ?)
                """, trackPromotion(Ids.newId()), Timestamp.from(START), Timestamp.from(END),
                Timestamp.from(START), Timestamp.from(START)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertAuto(String name, String discountType, long value, Long maxDiscount, Instant startsAt,
            Instant endsAt, Integer totalLimit, int usedCount) {
        UUID id = trackPromotion(Ids.newId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO commerce.promotion
                    (id, kind, name, discount_type, discount_value, max_discount_vnd, starts_at, ends_at,
                     total_limit, scope, status, used_count, created_at, updated_at)
                VALUES (?, 'AUTO', ?, ?, ?, ?, ?, ?, ?, 'ALL', 'ACTIVE', ?, ?, ?)
                """, id, name, discountType, value, maxDiscount, Timestamp.from(startsAt), Timestamp.from(endsAt),
                totalLimit, usedCount, Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID insertVoucher(String code, String codeNormalized, String status, Instant deletedAt) {
        UUID id = trackPromotion(Ids.newId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO commerce.promotion
                    (id, kind, name, code, code_normalized, discount_type, discount_value, starts_at, ends_at,
                     combinable, scope, audience, status, deleted_at, created_at, updated_at)
                VALUES (?, 'VOUCHER', 'Voucher', ?, ?, 'AMOUNT', 10000, ?, ?, false, 'ALL', 'ALL', ?, ?, ?, ?)
                """, id, code, codeNormalized, Timestamp.from(START), Timestamp.from(END), status,
                deletedAt == null ? null : Timestamp.from(deletedAt), Timestamp.from(now), Timestamp.from(now));
        return id;
    }
}
