package vn.unicom.thi360.attempt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AdminOriginFilter;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/** Shared test fixtures for AT-1 Slice 1 and Slice 2 integration tests. */
public final class AttemptTestFixtures {

    public static final Instant DEFAULT_CLOCK_START = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptTestFixtures() {
    }

    /** Deterministic {@link MutableClock} starting at {@link #DEFAULT_CLOCK_START}. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class MutableClockConfig {
        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock(Clock.fixed(DEFAULT_CLOCK_START, ZoneOffset.UTC));
        }
    }

    public static UUID createStudentAccount(JdbcTemplate jdbc, String label) {
        return insertAccount(jdbc, label, "Attempt Student");
    }

    public static UUID createManageAccount(JdbcTemplate jdbc, String label) {
        return insertAccount(jdbc, label, "Attempt Manage");
    }

    private static UUID insertAccount(JdbcTemplate jdbc, String label, String fullName) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = label + "." + userId.toString().substring(0, 8);
        String email = userId + "@attempt-test.local";
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId,
                String.format("U%06d", seq),
                username,
                username.toLowerCase(),
                fullName,
                email,
                email.toLowerCase(),
                now,
                userId,
                now,
                userId);
        return userId;
    }

    public static GrantOrderResult.Line grantExam(
            EntitlementGrantService grantService,
            UUID userId,
            UUID examId,
            int attempts,
            List<String> optionCodes,
            String resultView,
            int examVersionNo) {
        UUID orderId = UUID.randomUUID();
        String orderCode = "ORD-" + orderId.toString().substring(0, 8);
        GrantLine line = new GrantLine(
                examId,
                examVersionNo,
                attempts,
                optionCodes == null ? List.of() : optionCodes,
                Map.of(),
                resultView);
        return grantService.grantForOrder(new GrantOrderCommand(orderId, orderCode, userId, List.of(line)))
                .lines()
                .getFirst();
    }

    public static PublishedExamSetSupport.Built ensureDsBuilt(ApplicationContext context) {
        return PublishedExamSetSupport.ensureBuilt(context);
    }

    public static UUID dsExamId(JdbcTemplate jdbc, String examCode) {
        return jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, examCode);
    }

    public static RequestPostProcessor asExamUser(UUID userId, String username) {
        return withPrincipal(userId, username, Access.EXAM, "STUDENT");
    }

    public static RequestPostProcessor asManageUser(UUID userId, String username) {
        return withPrincipal(userId, username, Access.MANAGE, "OPERATIONS");
    }

    private static RequestPostProcessor withPrincipal(
            UUID userId, String username, Access access, String roleCode) {
        CurrentUser currentUser = new CurrentUser(
                userId, username, Realm.APP, access, PermissionSet.empty());
        CurrentUserAuthenticationToken auth = new CurrentUserAuthenticationToken(currentUser, "test-sid");
        RequestPostProcessor authProcessor = authentication(auth);
        SimulatedPrincipal principal = new SimulatedPrincipal(userId, username, roleCode, false, List.of());
        return request -> {
            request.setAttribute(AdminOriginFilter.SIMULATED_PRINCIPAL_ATTR, principal);
            return authProcessor.postProcessRequest(request);
        };
    }

    /** Removes all attempt, entitlement, and account rows owned by {@code userId}, children first. */
    public static void cleanupUser(JdbcTemplate jdbc, UUID userId) {
        if (userId == null) {
            return;
        }
        jdbc.update("DELETE FROM attempt.attempt_event WHERE attempt_id IN (SELECT id FROM attempt.attempt WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM attempt.attempt_item WHERE attempt_id IN (SELECT id FROM attempt.attempt WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM attempt.attempt_part WHERE attempt_id IN (SELECT id FROM attempt.attempt WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM attempt.attempt WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM commerce.entitlement_usage WHERE grant_id IN (
                    SELECT g.id FROM commerce.entitlement_grant g
                    JOIN commerce.entitlement e ON e.id = g.entitlement_id
                    WHERE e.user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM commerce.entitlement_grant WHERE entitlement_id IN (
                    SELECT id FROM commerce.entitlement WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM commerce.entitlement WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM shared.idempotency_key WHERE scope LIKE ?", userId + ":%");
        jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
    }
}
