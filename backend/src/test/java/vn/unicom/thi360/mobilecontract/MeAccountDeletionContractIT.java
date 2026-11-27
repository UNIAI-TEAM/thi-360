package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.AuthFailureReason;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.KeycloakAuthException;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

public class MeAccountDeletionContractIT extends AbstractKeycloakIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/me-account-deletion-check-200.json",
                "api/me-account-deletion-check-200-unused-attempts.json",
                "api/me-account-deletion-check-200-pending-grading.json",
                "api/me-account-deletion-check-200-blocked-attempt.json",
                "api/me-account-deletion-check-200-blocked-order.json",
                "api/me-account-deletion-check-200-blocked-pending-payment.json",
                "api/me-account-deletion-check-200-blocked.json",
                "api/me-account-deletion-check-403-account-deletion-not-allowed.json",
                "api/me-account-deletion-400-wrong-old-password.json",
                "api/me-account-deletion-403-account-deletion-not-allowed.json",
                "api/me-account-deletion-409-account-deletion-blocked.json",
                "api/me-account-deletion-429-login-throttled.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final String PASSWORD = "Valid@1234";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntitlementGrantService entitlementGrantService;
    @MockitoSpyBean private KeycloakIdentityClient keycloakClient;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdAttemptIds = new ArrayList<>();
    private CommerceTestFixtures commerceFixtures;
    private MobileFixtureCertifier certifier;

    @BeforeEach
    void setUp() {
        reset(keycloakClient);
        commerceFixtures = new CommerceTestFixtures(jdbc, objectMapper, catalogLookup, promotionRepository, testSecuritySupport);
        certifier = new MobileFixtureCertifier(objectMapper);
    }

    @AfterEach
    void tearDown() {
        reset(keycloakClient);
        for (UUID attemptId : createdAttemptIds) {
            jdbc.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
        }
        createdAttemptIds.clear();
        for (UUID userId : createdUserIds) {
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
        commerceFixtures.cleanup();
    }

    @Test
    void allAccountDeletionContractFixturesMatchLiveResponses() throws Exception {
        LearnerAccount clean = createKeycloakStudent("mc.del.clean");
        MvcResult checkClean = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(clean)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200.json", 200, checkClean, Set.of());

        LearnerAccount withUnused = createKeycloakStudent("mc.del.unused");
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var fulfilledOrder = commerceFixtures.insertStubOrder(
                withUnused, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        commerceFixtures.grantOrder(entitlementGrantService, fulfilledOrder.id(), fulfilledOrder.code(),
                withUnused.id(), exam.examId(), 3);
        MvcResult checkUnused = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(withUnused)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-unused-attempts.json", 200, checkUnused, Set.of());

        LearnerAccount withGrading = createKeycloakStudent("mc.del.grading");
        insertAttempt(withGrading.id(), exam.examId(), "GRADING");
        MvcResult checkGrading = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(withGrading)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-pending-grading.json", 200, checkGrading, Set.of());

        LearnerAccount blockedAttempt = createKeycloakStudent("mc.del.blkatt");
        insertAttempt(blockedAttempt.id(), exam.examId(), "IN_PROGRESS");
        MvcResult checkBlockedAttempt = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(blockedAttempt)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-blocked-attempt.json", 200, checkBlockedAttempt, Set.of());

        LearnerAccount blockedOrder = createKeycloakStudent("mc.del.blkord");
        commerceFixtures.insertStubOrder(
                blockedOrder, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        MvcResult checkBlockedOrder = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(blockedOrder)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-blocked-order.json", 200, checkBlockedOrder, Set.of());

        LearnerAccount blockedPending = createKeycloakStudent("mc.del.blkpend");
        commerceFixtures.insertStubOrder(
                blockedPending, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        MvcResult checkBlockedPending = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(blockedPending)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-blocked-pending-payment.json", 200, checkBlockedPending, Set.of());

        LearnerAccount blockedAll = createKeycloakStudent("mc.del.blkall");
        insertAttempt(blockedAll.id(), exam.examId(), "IN_PROGRESS");
        commerceFixtures.insertStubOrder(
                blockedAll, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        commerceFixtures.insertStubOrder(
                blockedAll, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        MvcResult checkBlockedAll = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asStudent(blockedAll)))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-200-blocked.json", 200, checkBlockedAll, Set.of());

        MvcResult checkForbidden = mockMvc.perform(get("/api/v1/me/account/deletion-check")
                        .with(asUser(UUID.randomUUID(), "mc.del.staff")))
                .andReturn();
        certifier.certify("api/me-account-deletion-check-403-account-deletion-not-allowed.json", 403,
                checkForbidden, Set.of("traceId"));

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(clean))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", "Wrong@9999"))))
                .andReturn();
        certifier.certify("api/me-account-deletion-400-wrong-old-password.json", 400, wrongPassword, Set.of("traceId"));

        MvcResult deleteForbidden = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asUser(UUID.randomUUID(), "mc.del.staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andReturn();
        certifier.certify("api/me-account-deletion-403-account-deletion-not-allowed.json", 403,
                deleteForbidden, Set.of("traceId"));

        MvcResult deleteBlocked = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(blockedAll))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andReturn();
        certifier.certify("api/me-account-deletion-409-account-deletion-blocked.json", 409,
                deleteBlocked, Set.of("traceId"));

        LearnerAccount throttled = createKeycloakStudent("mc.del.lock");
        doThrow(new KeycloakAuthException(AuthFailureReason.ACCOUNT_LOCKED))
                .when(keycloakClient)
                .passwordGrant(any(), eq(throttled.username()), any());
        try {
            MvcResult deleteThrottled = mockMvc.perform(post("/api/v1/me/account/deletion")
                            .with(asStudent(throttled))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                    .andReturn();
            certifier.certify("api/me-account-deletion-429-login-throttled.json", 429,
                    deleteThrottled, Set.of("traceId"));
        } finally {
            reset(keycloakClient);
        }

        MvcResult deleteOk = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(clean))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andReturn();
        assertThat(deleteOk.getResponse().getStatus()).isEqualTo(204);
        assertThat(deleteOk.getResponse().getContentAsByteArray()).isEmpty();

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private LearnerAccount createKeycloakStudent(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "." + suffix;
        String email = username + "@thi360.vn";
        String fullName = "Nguyễn Văn An";
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Nguyễn", "Văn An", PASSWORD, true, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId, String.format("U%06d", seq), username, username.toLowerCase(), fullName,
                email, email.toLowerCase(), now, now, userId, now, userId);
        createdUserIds.add(userId);
        return new LearnerAccount(userId, username, fullName, email);
    }

    private RequestPostProcessor asStudent(LearnerAccount account) {
        return testSecuritySupport.apply(new SimulatedPrincipal(
                account.id(), account.username(), "STUDENT", false, List.of()));
    }

    private void insertAttempt(UUID userId, UUID examId, String state) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO attempt.attempt
                    (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, exam_name,
                     exam_kind_code, scope, timing, navigation_mode, choices, seed, generation_token,
                     state, regrading, result_view, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, 'Exam Test', 'HSA', 'FULL', 'SHARED', 'FREE', '{}'::jsonb,
                        1, ?, ?, false, 'FULL_SOLUTION', 0, ?, ?)
                """,
                id, userId, examId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), state, now, now);
        createdAttemptIds.add(id);
    }
}
