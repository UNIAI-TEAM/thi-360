package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-21: what the database itself refuses, whatever the application code does. Runs on the real migrations
 * on {@code postgres:16-alpine} ({@code NULLS NOT DISTINCT} needs PostgreSQL 15 or later).
 */
class EntitlementConstraintsIT extends EntitlementIntegrationTest {

    @Test
    void secondEntitlementOfSameAccountExamAndSourceIsRejectedWhenSourceRefIsNull() {
        UUID userId = newId();
        UUID examId = newId();
        insertEntitlement(newId(), userId, examId, "PURCHASE", null, 1);

        assertConstraint(
                () -> insertEntitlement(newId(), userId, examId, "PURCHASE", null, 1), "uq_entitlement_scope");

        // Another exam, another account and another source are different scopes.
        assertThatCode(() -> {
            insertEntitlement(newId(), userId, newId(), "PURCHASE", null, 1);
            insertEntitlement(newId(), newId(), examId, "PURCHASE", null, 1);
            insertEntitlement(newId(), userId, examId, "CLASS", newId(), 1);
        }).doesNotThrowAnyException();
    }

    @Test
    void sameSourceRefTwiceIsRejectedAndDifferentSourceRefsAreNot() {
        UUID userId = newId();
        UUID examId = newId();
        UUID classId = newId();
        insertEntitlement(newId(), userId, examId, "CLASS", classId, 1);

        assertConstraint(
                () -> insertEntitlement(newId(), userId, examId, "CLASS", classId, 1), "uq_entitlement_scope");
        assertThatCode(() -> insertEntitlement(newId(), userId, examId, "CLASS", newId(), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void entitlementCountsMayReachTotalButNotExceedIt() {
        UUID exactlyTotal = newId();
        insertEntitlement(exactlyTotal, newId(), newId(), "PURCHASE", null, 3);

        assertThatCode(() -> jdbc.update(
                "UPDATE commerce.entitlement SET attempts_used = 1, attempts_reserved = 1, attempts_revoked = 1"
                        + " WHERE id = ?", exactlyTotal)).doesNotThrowAnyException();
        assertConstraint(() -> jdbc.update(
                "UPDATE commerce.entitlement SET attempts_revoked = 2 WHERE id = ?", exactlyTotal),
                "ck_entitlement_attempts_within_total");
        assertConstraint(() -> jdbc.update(
                "UPDATE commerce.entitlement SET attempts_used = -1 WHERE id = ?", exactlyTotal),
                "ck_entitlement_counts_non_negative");
        assertConstraint(
                () -> insertEntitlementWithCounts(newId(), newId(), newId(), 3, 2, 2, 0),
                "ck_entitlement_attempts_within_total");
    }

    @Test
    void grantCountsMayReachAttemptsButNotExceedThem() {
        UUID entitlementId = newId();
        UUID examId = newId();
        insertEntitlement(entitlementId, newId(), examId, "PURCHASE", null, 3);
        UUID grantId = newId();
        insertPurchaseGrant(grantId, entitlementId, examId, newId(), 3);

        assertThatCode(() -> jdbc.update(
                "UPDATE commerce.entitlement_grant SET attempts_used = 1, attempts_reserved = 1, attempts_revoked = 1"
                        + " WHERE id = ?", grantId)).doesNotThrowAnyException();
        assertConstraint(() -> jdbc.update(
                "UPDATE commerce.entitlement_grant SET attempts_reserved = 2 WHERE id = ?", grantId),
                "ck_entitlement_grant_attempts_within_total");
    }

    @Test
    void secondGrantOfSameOrderAndExamIsRejected() {
        UUID entitlementId = newId();
        UUID examId = newId();
        UUID orderId = newId();
        insertEntitlement(entitlementId, newId(), examId, "PURCHASE", null, 4);
        insertPurchaseGrant(newId(), entitlementId, examId, orderId, 2);

        assertConstraint(
                () -> insertPurchaseGrant(newId(), entitlementId, examId, orderId, 2),
                "uq_entitlement_grant_order_exam");

        // The same order may still hold another exam (bundle, TM 16.3).
        UUID otherExam = newId();
        UUID otherEntitlement = newId();
        insertEntitlement(otherEntitlement, newId(), otherExam, "PURCHASE", null, 2);
        assertThatCode(() -> insertPurchaseGrant(newId(), otherEntitlement, otherExam, orderId, 2))
                .doesNotThrowAnyException();
    }

    @Test
    void secondCompensationForTheSameAttemptIsRejected() {
        UUID entitlementId = newId();
        UUID examId = newId();
        UUID attemptId = newId();
        insertEntitlement(entitlementId, newId(), examId, "PURCHASE", null, 2);
        insertCompensationGrant(newId(), entitlementId, examId, attemptId);

        assertConstraint(
                () -> insertCompensationGrant(newId(), entitlementId, examId, attemptId),
                "uq_entitlement_grant_compensation_attempt");
    }

    @Test
    void grantMustBelongToTheExamOfItsEntitlement() {
        UUID entitlementId = newId();
        insertEntitlement(entitlementId, newId(), newId(), "PURCHASE", null, 2);

        assertConstraint(
                () -> insertPurchaseGrant(newId(), entitlementId, newId(), newId(), 1),
                "fk_entitlement_grant_entitlement");
    }

    @Test
    void usageAttemptIdIsUnique() {
        UUID entitlementId = newId();
        UUID examId = newId();
        UUID grantId = newId();
        UUID attemptId = newId();
        insertEntitlement(entitlementId, newId(), examId, "PURCHASE", null, 2);
        insertPurchaseGrant(grantId, entitlementId, examId, newId(), 2);
        insertUsage(grantId, attemptId);

        assertConstraint(() -> insertUsage(grantId, attemptId), "uq_entitlement_usage_attempt");
    }

    private static void assertConstraint(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void insertEntitlement(UUID id, UUID userId, UUID examId, String source, UUID sourceRef, int total) {
        insertEntitlementWithCounts(id, userId, examId, total, 0, 0, 0, source, sourceRef);
    }

    private void insertEntitlementWithCounts(
            UUID id, UUID userId, UUID examId, int total, int used, int reserved, int revoked) {
        insertEntitlementWithCounts(id, userId, examId, total, used, reserved, revoked, "PURCHASE", null);
    }

    private void insertEntitlementWithCounts(
            UUID id, UUID userId, UUID examId, int total, int used, int reserved, int revoked,
            String source, UUID sourceRef) {
        jdbc.update(
                """
                INSERT INTO commerce.entitlement
                    (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used, attempts_reserved,
                     attempts_revoked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, userId, examId, source, sourceRef, total, used, reserved, revoked);
    }

    private void insertPurchaseGrant(UUID id, UUID entitlementId, UUID examId, UUID orderId, int attempts) {
        String orderCode = "ORD-" + orderId.toString().replace("-", "").substring(0, 12).toUpperCase();
        CommerceTestFixtures.insertMinimalStubOrder(jdbc, orderId, newId(), examId, orderCode);
        createdStubOrderIds.add(orderId);
        jdbc.update(
                """
                INSERT INTO commerce.entitlement_grant
                    (id, entitlement_id, kind, order_id, order_code, exam_id, attempts, result_view, exam_version_no,
                     granted_at, granted_by)
                VALUES (?, ?, 'PURCHASE', ?, ?, ?, ?, 'FULL', 1, now(), 'SYSTEM')
                """,
                id, entitlementId, orderId, orderCode, examId, attempts);
    }

    private void insertCompensationGrant(UUID id, UUID entitlementId, UUID examId, UUID attemptId) {
        jdbc.update(
                """
                INSERT INTO commerce.entitlement_grant
                    (id, entitlement_id, kind, exam_id, compensation_attempt_id, attempts, result_view,
                     exam_version_no, granted_at, granted_by, reason)
                VALUES (?, ?, 'COMPENSATION', ?, ?, 1, 'FULL', 1, now(), ?, 'test')
                """,
                id, entitlementId, examId, attemptId, newId().toString());
    }

    private void insertUsage(UUID grantId, UUID attemptId) {
        jdbc.update(
                "INSERT INTO commerce.entitlement_usage (id, grant_id, attempt_id, state) VALUES (?, ?, ?, 'RESERVED')",
                newId(), grantId, attemptId);
    }
}
