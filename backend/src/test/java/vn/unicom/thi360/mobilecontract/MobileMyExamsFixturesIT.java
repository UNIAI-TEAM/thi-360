package vn.unicom.thi360.mobilecontract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.AttemptTestFixtures;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.ids.Ids;

public class MobileMyExamsFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/my-exams-list-200.json",
                "api/my-exams-list-200-empty.json",
                "api/my-exams-list-403-forbidden.json",
                "api/attempts-list-200.json",
                "api/attempts-list-200-empty.json",
                "api/attempts-list-403-attempt-access-forbidden.json",
                "api/attempts-list-422-invalid-date-filter.json",
                "api/catalog-options-200-exam.json",
                "api/catalog-options-200-subject.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private EntitlementPort entitlementPort;

    private CommerceTestFixtures fixtures;
    private MobileFixtureCertifier certifier;
    private final List<UUID> attemptIds = new ArrayList<>();
    private final List<UUID> extraEntitlementIds = new ArrayList<>();
    private final List<UUID> extraOrderIds = new ArrayList<>();
    private final List<UUID> extraBundleIds = new ArrayList<>();
    private final List<UUID> extraAssignmentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, objectMapper, catalogLookup, promotionRepository, testSecuritySupport);
        certifier = new MobileFixtureCertifier(objectMapper);
        attemptIds.clear();
        extraEntitlementIds.clear();
        extraOrderIds.clear();
        extraBundleIds.clear();
        extraAssignmentIds.clear();
    }

    @AfterEach
    void tearDown() {
        for (UUID attemptId : attemptIds) {
            jdbc.update("DELETE FROM attempt.attempt_event WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt_item WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt_part WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
        }
        attemptIds.clear();
        for (UUID entId : extraEntitlementIds) {
            jdbc.update("DELETE FROM commerce.entitlement_usage WHERE grant_id IN (SELECT id FROM commerce.entitlement_grant WHERE entitlement_id = ?)", entId);
            jdbc.update("DELETE FROM commerce.entitlement_grant WHERE entitlement_id = ?", entId);
            jdbc.update("DELETE FROM commerce.entitlement WHERE id = ?", entId);
        }
        extraEntitlementIds.clear();
        for (UUID assignmentId : extraAssignmentIds) {
            jdbc.update("DELETE FROM commerce.class_exam_assignment WHERE id = ?", assignmentId);
        }
        extraAssignmentIds.clear();
        for (UUID orderId : extraOrderIds) {
            CommerceTestFixtures.deleteStubOrder(jdbc, orderId);
        }
        extraOrderIds.clear();
        for (UUID bundleId : extraBundleIds) {
            jdbc.update("DELETE FROM commerce.bundle WHERE id = ?", bundleId);
        }
        extraBundleIds.clear();
        fixtures.cleanup();
    }

    @Test
    void allMyExamsHistoryAndCatalogContractFixturesMatchLiveResponses() throws Exception {
        var learner = fixtures.createLearner();
        var emptyLearner = fixtures.createLearner();
        var manager = fixtures.createLearner();
        Instant baseTime = Instant.parse("2026-11-20T09:00:00Z");

        var checkingProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        fixtures.insertStubOrder(
                learner, checkingProduct, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, baseTime.plusSeconds(900), baseTime);

        var bundleExamProduct = fixtures.insertOnSaleProduct("HSA", 150_000L, 3);
        UUID bundleId = Ids.newId();
        UUID bundleOrderId = Ids.newId();
        String bundleOrderCode = "DH261120" + CommerceTestFixtures.shortToken().substring(0, 6);
        String bundleCode = "BD" + CommerceTestFixtures.shortToken();
        UUID hsaItemId = catalogLookup.findActiveItem("EXAM", "HSA").orElseThrow().id();
        jdbc.update("""
                INSERT INTO commerce.bundle
                    (id, code, code_normalized, name, exam_kind_item_id, list_price_vnd, attempts_per_exam,
                     benefit_ai_explanation, benefit_strength_weakness, benefit_practice, result_view, status,
                     has_unpublished_changes, serving_version, revision, created_at, updated_at)
                VALUES (?, ?, ?, 'Bộ đề luyện thi HSA', ?, 300000, 3, true, true, false, 'FULL_SOLUTION', 'PUBLISHED',
                        false, 1, 1, ?, ?)
                """,
                bundleId, bundleCode, bundleCode.toLowerCase(), hsaItemId,
                Timestamp.from(baseTime), Timestamp.from(baseTime));
        extraBundleIds.add(bundleId);
        jdbc.update("""
                INSERT INTO commerce.purchase_order
                    (id, code, user_id, buyer_username, buyer_full_name, product_type, bundle_id, bundle_version_no,
                     option_codes, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                     attempts_granted, benefits, result_view, payment_method, state, paid_at, fulfilled_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'BUNDLE', ?, 1,
                        '{}'::text[], ?, 'Bộ đề luyện thi HSA', 300000, 'NONE', 300000,
                        3, '{"aiExplanation":true}'::jsonb, 'FULL_SOLUTION', 'VNPAY', 'FULFILLED', ?, ?, ?, ?, 0)
                """,
                bundleOrderId, bundleOrderCode, learner.id(), learner.username(), learner.fullName(),
                bundleId, bundleCode, Timestamp.from(baseTime), Timestamp.from(baseTime),
                Timestamp.from(baseTime), Timestamp.from(baseTime));
        jdbc.update("""
                INSERT INTO commerce.purchase_order_item
                    (id, order_id, exam_id, exam_version_no, exam_code, exam_name, option_codes,
                     attempts_granted, benefits, result_view, position)
                VALUES (?, ?, ?, 1, ?, ?, '{}'::text[], 3, '{"aiExplanation":true}'::jsonb, 'FULL_SOLUTION', 1)
                """,
                Ids.newId(), bundleOrderId, bundleExamProduct.examId(), bundleExamProduct.code(), bundleExamProduct.name());
        extraOrderIds.add(bundleOrderId);
        var bundleLine = fixtures.grantOrder(
                grantService, bundleOrderId, bundleOrderCode, learner.id(), bundleExamProduct.examId(), 3);
        jdbc.update("UPDATE commerce.entitlement SET created_at = ? WHERE id = ?",
                Timestamp.from(baseTime.plusSeconds(10)), bundleLine.entitlementId());

        var inProgressProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var inProgressOrder = fixtures.insertStubOrder(
                learner, inProgressProduct, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, baseTime);
        var inProgressLine = fixtures.grantOrder(
                grantService, inProgressOrder.id(), inProgressOrder.code(), learner.id(), inProgressProduct.examId(), 2);
        jdbc.update("UPDATE commerce.entitlement SET created_at = ? WHERE id = ?",
                Timestamp.from(baseTime.plusSeconds(20)), inProgressLine.entitlementId());
        UUID openAttemptId = Ids.newId();
        entitlementPort.reserve(learner.id(), inProgressLine.entitlementId(), openAttemptId);
        insertAttemptRow(
                openAttemptId, learner.id(), inProgressProduct.examId(),
                inProgressLine.entitlementId(), inProgressLine.grantId(),
                inProgressProduct.name(), "HSA", 1, "IN_PROGRESS",
                baseTime.plusSeconds(600), null, null, null, null, null);

        var exhaustedProduct = fixtures.insertOnSaleProduct("HSA", 180_000L, 1);
        var exhaustedOrder = fixtures.insertStubOrder(
                learner, exhaustedProduct, OrderState.FULFILLED, PaymentMethod.VNPAY, 180_000L, null, baseTime);
        var exhaustedLine = fixtures.grantOrder(
                grantService, exhaustedOrder.id(), exhaustedOrder.code(), learner.id(), exhaustedProduct.examId(), 1);
        jdbc.update("UPDATE commerce.entitlement SET created_at = ? WHERE id = ?",
                Timestamp.from(baseTime.plusSeconds(30)), exhaustedLine.entitlementId());
        UUID scoredAttemptId = Ids.newId();
        entitlementPort.reserve(learner.id(), exhaustedLine.entitlementId(), scoredAttemptId);
        entitlementPort.confirmUse(scoredAttemptId);
        insertAttemptRow(
                scoredAttemptId, learner.id(), exhaustedProduct.examId(),
                exhaustedLine.entitlementId(), exhaustedLine.grantId(),
                exhaustedProduct.name(), "HSA", 1, "SCORED",
                baseTime.plusSeconds(100), baseTime.plusSeconds(2500), baseTime.plusSeconds(2510),
                new BigDecimal("82.50"), new BigDecimal("82.50"), new BigDecimal("100.00"));

        var classProduct = fixtures.insertOnSaleProduct("HSA", 150_000L, 2);
        UUID classId = fixtures.createClassRoom();
        fixtures.addClassMember(classId, learner.id());
        UUID classEntitlementId = Ids.newId();
        UUID classGrantId = Ids.newId();
        UUID classAssignmentId = Ids.newId();
        Timestamp classTime = Timestamp.from(baseTime.plusSeconds(40));
        jdbc.update("""
                INSERT INTO commerce.class_exam_assignment
                    (id, class_id, exam_id, exam_version_no, exam_code, exam_name, attempts_granted,
                     result_view, assigned_by, assigned_at, updated_at, version)
                VALUES (?, ?, ?, 1, ?, ?, 2, 'FULL_SOLUTION', ?, ?, ?, 0)
                """,
                classAssignmentId, classId, classProduct.examId(), classProduct.code(), classProduct.name(),
                learner.id(), classTime, classTime);
        extraAssignmentIds.add(classAssignmentId);
        jdbc.update("""
                INSERT INTO commerce.entitlement
                    (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used, attempts_reserved,
                     attempts_revoked, created_at, updated_at, version)
                VALUES (?, ?, ?, 'CLASS', ?, 2, 0, 0, 0, ?, ?, 0)
                """,
                classEntitlementId, learner.id(), classProduct.examId(), classId, classTime, classTime);
        jdbc.update("""
                INSERT INTO commerce.entitlement_grant
                    (id, entitlement_id, kind, exam_id, class_assignment_id, option_codes, attempts, attempts_used,
                     attempts_reserved, attempts_revoked, benefits, result_view, exam_version_no, granted_at,
                     granted_by, version)
                VALUES (?, ?, 'CLASS', ?, ?, '{}'::text[], 2, 0, 0, 0, '{"aiExplanation":true}'::jsonb,
                        'FULL_SOLUTION', 1, ?, 'TEACHER', 0)
                """,
                classGrantId, classEntitlementId, classProduct.examId(), classAssignmentId, classTime);
        extraEntitlementIds.add(classEntitlementId);

        certifier.certify("api/my-exams-list-200.json", 200,
                mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asLearner(learner))).andReturn(),
                Set.of(
                        "entitlementId",
                        "$.items[*].code",
                        "$.items[*].name",
                        "activeAttemptId",
                        "resultAttemptId",
                        "pendingOrderCode",
                        "bundleId",
                        "$.items[*].bundles[*].code",
                        "classId",
                        "className"));

        certifier.certify("api/my-exams-list-200-empty.json", 200,
                mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asLearner(emptyLearner))).andReturn(),
                Set.of());

        certifier.certify("api/my-exams-list-403-forbidden.json", 403,
                mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asManager(manager.id(), manager.username()))).andReturn(),
                Set.of("traceId"));

        certifier.certify("api/attempts-list-200.json", 200,
                mockMvc.perform(get("/api/v1/me/attempts").with(AttemptTestFixtures.asExamUser(learner.id(), learner.username()))).andReturn(),
                Set.of("attemptId", "examId", "examName", "startedAt", "finishedAt", "gradedAt"));

        certifier.certify("api/attempts-list-200-empty.json", 200,
                mockMvc.perform(get("/api/v1/me/attempts").with(AttemptTestFixtures.asExamUser(emptyLearner.id(), emptyLearner.username()))).andReturn(),
                Set.of());

        certifier.certify("api/attempts-list-403-attempt-access-forbidden.json", 403,
                mockMvc.perform(get("/api/v1/me/attempts").with(AttemptTestFixtures.asManageUser(manager.id(), manager.username()))).andReturn(),
                Set.of("traceId"));

        certifier.certify("api/attempts-list-422-invalid-date-filter.json", 422,
                mockMvc.perform(get("/api/v1/me/attempts")
                                .param("from", "not-a-date")
                                .with(AttemptTestFixtures.asExamUser(learner.id(), learner.username())))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/catalog-options-200-exam.json", 200,
                mockMvc.perform(get("/api/v1/catalog/options")
                                .param("group", "EXAM")
                                .with(fixtures.asLearner(learner)))
                        .andReturn(),
                Set.of("id"));

        certifier.certify("api/catalog-options-200-subject.json", 200,
                mockMvc.perform(get("/api/v1/catalog/options")
                                .param("group", "SUBJECT")
                                .with(fixtures.asLearner(learner)))
                        .andReturn(),
                Set.of("id"));

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private void insertAttemptRow(
            UUID attemptId,
            UUID userId,
            UUID examId,
            UUID entitlementId,
            UUID grantId,
            String examName,
            String kindCode,
            int attemptNo,
            String state,
            Instant startedAt,
            Instant finishedAt,
            Instant gradedAt,
            BigDecimal rawScore,
            BigDecimal displayScore,
            BigDecimal maxScore) {
        UUID kindItemId = catalogLookup.findActiveItem("EXAM", kindCode).orElseThrow().id();
        jdbc.update("""
                INSERT INTO attempt.attempt
                    (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, attempt_no,
                     exam_name, exam_kind_item_id, exam_kind_code, scope, timing, navigation_mode,
                     choices, seed, generation_token, state, regrading, end_reason,
                     started_at, finished_at, graded_at, result_view, practice_scoring,
                     max_score, raw_score, display_score, created_at, updated_at, version)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 'FULL', 'PER_PART', 'SEQUENTIAL',
                        '{}'::jsonb, 12345, ?, ?, false, ?, ?, ?, ?, 'FULL_SOLUTION', 'RAW',
                        ?, ?, ?, ?, ?, 0)
                """,
                attemptId, userId, examId, entitlementId, grantId, attemptNo,
                examName, kindItemId, kindCode, Ids.newId(), state,
                "SCORED".equals(state) ? "SUBMITTED" : null,
                Timestamp.from(startedAt),
                finishedAt == null ? null : Timestamp.from(finishedAt),
                gradedAt == null ? null : Timestamp.from(gradedAt),
                maxScore, rawScore, displayScore,
                Timestamp.from(startedAt), Timestamp.from(startedAt));
        attemptIds.add(attemptId);
    }
}
