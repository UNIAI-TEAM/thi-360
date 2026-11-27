package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryService;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.application.AccountSelfDeletionService;
import vn.unicom.thi360.identity.application.AvatarService;
import vn.unicom.thi360.identity.application.EmailCodeService;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.AuthFailureReason;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.KeycloakAuthException;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

@ExtendWith(OutputCaptureExtension.class)
class AccountSelfDeletionIT extends AbstractAvatarMinioIT {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntitlementGrantService entitlementGrantService;
    @Autowired private OrderExpiryService orderExpiryService;
    @Autowired private AvatarService avatarService;
    @Autowired private EmailCodeService emailCodeService;
    @Autowired private UserIdentityRepository userIdentityRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AccountSelfDeletionService accountSelfDeletionService;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakSpy;

    private CommerceTestFixtures commerceFixtures;
    private SelfRegistrationSupport regSupport;
    private final List<UUID> extraUserIds = new ArrayList<>();
    private final List<UUID> attemptIds = new ArrayList<>();
    private final List<UUID> classIds = new ArrayList<>();
    private final List<UUID> importBatchIds = new ArrayList<>();
    private final List<String> usedIps = new ArrayList<>();

    @BeforeEach
    void setUpExtra() {
        reset(keycloakSpy);
        commerceFixtures = new CommerceTestFixtures(
                jdbc, objectMapper, catalogLookup, promotionRepository, testSecuritySupport);
        regSupport = new SelfRegistrationSupport(mockMvc, jdbc, keycloakSpy, redis, objectMapper);
    }

    @AfterEach
    void tearDownExtra() {
        reset(keycloakSpy);
        for (UUID attemptId : attemptIds) {
            jdbc.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
        }
        attemptIds.clear();
        for (UUID batchId : importBatchIds) {
            jdbc.update("DELETE FROM identity.import_batch WHERE id = ?", batchId);
        }
        importBatchIds.clear();
        for (UUID userId : extraUserIds) {
            cleanupUserData(userId);
        }
        extraUserIds.clear();
        for (UUID classId : classIds) {
            jdbc.update("DELETE FROM classroom.class_member_history WHERE class_id = ? OR related_class_id = ?", classId, classId);
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        classIds.clear();
        for (String ip : usedIps) {
            redis.delete(RateLimitFilter.KEY_PREFIX + ip);
        }
        usedIps.clear();
        regSupport.cleanup();
        commerceFixtures.cleanup();
    }

    @Test
    void checkBlockers() throws Exception {
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        Account clean = createStudent("del.blk.clean", "vi", true);
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(clean)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        Account starting = createStudent("del.blk.start", "vi", true);
        insertAttempt(starting.id(), exam.examId(), "STARTING");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(starting)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ATTEMPT_IN_PROGRESS")));

        Account inProgress = createStudent("del.blk.prog", "vi", true);
        insertAttempt(inProgress.id(), exam.examId(), "IN_PROGRESS");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(inProgress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ATTEMPT_IN_PROGRESS")));

        Account paid = createStudent("del.blk.paid", "vi", true);
        commerceFixtures.insertStubOrder(
                asLearner(paid), exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(paid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ORDER_BEING_CHECKED")));

        Account pendingPastExpiry = createStudent("del.blk.pend", "vi", true);
        commerceFixtures.insertStubOrder(
                asLearner(pendingPastExpiry), exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().minusSeconds(3600), null);
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(pendingPastExpiry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ORDER_PENDING_PAYMENT")));

        Account allThree = createStudent("del.blk.all", "vi", true);
        insertAttempt(allThree.id(), exam.examId(), "IN_PROGRESS");
        commerceFixtures.insertStubOrder(
                asLearner(allThree), exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        commerceFixtures.insertStubOrder(
                asLearner(allThree), exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(allThree)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains(
                        "ATTEMPT_IN_PROGRESS", "ORDER_BEING_CHECKED", "ORDER_PENDING_PAYMENT")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ATTEMPT:GRADING",
        "ATTEMPT:PENDING_REVIEW",
        "ATTEMPT:SCORED",
        "ATTEMPT:GRADING_ERROR",
        "ATTEMPT:ABORTED",
        "ORDER:FULFILLED",
        "ORDER:REFUNDED",
        "ORDER:CANCELLED",
        "ORDER:EXPIRED",
        "ORDER:REPLACED"
    })
    void checkNonBlockingStates(String kindAndState) throws Exception {
        String[] parts = kindAndState.split(":");
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        Account acc = createStudent("del.nonblk", "vi", true);
        if ("ATTEMPT".equals(parts[0])) {
            insertAttempt(acc.id(), exam.examId(), parts[1]);
        } else {
            OrderState state = OrderState.valueOf(parts[1]);
            commerceFixtures.insertStubOrder(
                    asLearner(acc), exam, state, PaymentMethod.VNPAY,
                    200_000L, Instant.now().plusSeconds(600),
                    state == OrderState.FULFILLED || state == OrderState.REFUNDED ? Instant.now() : null);
        }

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());
    }

    @Test
    void unusedAttempts() throws Exception {
        Account acc = createStudent("del.unused", "vi", true);
        var exam1 = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 5);
        var exam2 = commerceFixtures.insertOnSaleProduct("QDA", 200_000L, 4);
        var exam3 = commerceFixtures.insertOnSaleProduct("VACT", 200_000L, 2);

        insertEntitlement(acc.id(), exam1.examId(), "PURCHASE", null, 5, 1, 2, 1);
        UUID classId = createClassRoom();
        insertEntitlement(acc.id(), exam2.examId(), "CLASS", classId, 4, 1, 0, 1);
        insertEntitlement(acc.id(), exam3.examId(), "PURCHASE", null, 2, 0, 0, 2);

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.unusedAttempts").value(3));
    }

    @Test
    void freshAccount() throws Exception {
        String sfx = SelfRegistrationSupport.suffix();
        String username = "delfresh." + sfx;
        String email = username + "@thi360.vn";
        MvcResult regRes = regSupport.register(regSupport.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = regRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = regRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        UUID userId = regSupport.accountId(username);
        extraUserIds.add(userId);

        mockMvc.perform(get("/api/v1/me/account/deletion-check").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andExpect(jsonPath("$.unusedAttempts").value(0))
                .andExpect(jsonPath("$.pendingGrading").value(false));

        MvcResult delRes = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", SelfRegistrationSupport.PASSWORD))))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(delRes.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void pendingGrading() throws Exception {
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        Account grading = createStudent("del.pg.g", "vi", true);
        insertAttempt(grading.id(), exam.examId(), "GRADING");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(grading)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andExpect(jsonPath("$.pendingGrading").value(true));

        Account review = createStudent("del.pg.pr", "vi", true);
        insertAttempt(review.id(), exam.examId(), "PENDING_REVIEW");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(review)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andExpect(jsonPath("$.pendingGrading").value(true));

        Account gradingError = createStudent("del.pg.err", "vi", true);
        insertAttempt(gradingError.id(), exam.examId(), "GRADING_ERROR");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(gradingError)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.pendingGrading").value(false));

        Account scored = createStudent("del.pg.sc", "vi", true);
        insertAttempt(scored.id(), exam.examId(), "SCORED");
        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(scored)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.pendingGrading").value(false));
    }

    @Test
    void pendingPaymentUnblocks() throws Exception {
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        Account acc1 = createStudent("del.unblk.can", "vi", true);
        String createRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(asStudent(acc1))
                        .header("Idempotency-Key", "del-unblk-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", exam.code(),
                                "expectedAccountId", acc1.id(),
                                "expectedVersionNo", exam.versionNo(),
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode orderNode = objectMapper.readTree(createRaw).path("order");
        UUID orderId = UUID.fromString(orderNode.path("id").asText());
        String orderCode = orderNode.path("code").asText();
        commerceFixtures.trackOrderId(orderId);

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ORDER_PENDING_PAYMENT")));
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PasswordResetTestSupport.PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_BLOCKED"))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ORDER_PENDING_PAYMENT")));

        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/cancel").with(asStudent(acc1)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PasswordResetTestSupport.PASSWORD))))
                .andExpect(status().isNoContent());

        Account acc2 = createStudent("del.unblk.exp", "vi", true);
        var expOrder = commerceFixtures.insertStubOrder(
                asLearner(acc2), exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().minusSeconds(1800), null);
        commerceFixtures.insertStubPayment(expOrder, 1, 200_000L, PaymentState.FAILED);

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ORDER_PENDING_PAYMENT")));

        orderExpiryService.expirePendingOrders();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, expOrder.id()))
                .isEqualTo("EXPIRED");

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PasswordResetTestSupport.PASSWORD))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRevokesEverything() throws Exception {
        Account acc = createStudent("del.rev", "vi", true);
        String ipWeb = freshIp();
        String ipMobile = freshIp();

        MvcResult webLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ipWeb)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webAccess = webLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie webXsrf = webLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        MvcResult mobileLogin = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ipMobile)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mobileTokens = objectMapper.readTree(mobileLogin.getResponse().getContentAsString());
        String mobileAccess = mobileTokens.path("accessToken").asText();
        String mobileRefresh = mobileTokens.path("refreshToken").asText();

        MvcResult delRes = mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(webAccess, webXsrf)
                        .header(AuthCookieService.XSRF_HEADER, webXsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(delRes.getResponse().getContentAsByteArray()).isEmpty();

        mockMvc.perform(get("/api/v1/me").cookie(webAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + mobileAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", mobileRefresh))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recordAnonymised() throws Exception {
        Account acc = createStudent("del.anon", "vi", true);
        String oldPhone = "0912345678";
        String oldPendingEmail = "pending." + UUID.randomUUID().toString().substring(0, 8) + "@thi360-test.example";
        String oldFullName = "Trần Thị Minh Châu";
        jdbc.update(
                "UPDATE identity.user_account SET full_name = ?, phone = ?, pending_email = ? WHERE id = ?",
                oldFullName, oldPhone, oldPendingEmail, acc.id());
        String expectedCode = jdbc.queryForObject(
                "SELECT code FROM identity.user_account WHERE id = ?", String.class, acc.id());

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        String expectedAnon = "deleted-" + acc.id().toString().toLowerCase();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM identity.user_account WHERE id = ?", acc.id());
        assertThat(row.get("username")).isEqualTo(expectedAnon);
        assertThat(row.get("username_normalized")).isEqualTo(expectedAnon);
        assertThat(row.get("full_name")).isEqualTo(expectedAnon);
        assertThat(row.get("email")).isNull();
        assertThat(row.get("email_normalized")).isNull();
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("pending_email")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(acc.id());
        assertThat(row.get("delete_reason")).isEqualTo("SELF_DELETED");
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(3);
        assertThat(row.get("code")).isEqualTo(expectedCode);

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() instanceof String s) {
                assertThat(s)
                        .as("column %s must not retain old PII", entry.getKey())
                        .doesNotContainIgnoringCase(acc.username())
                        .doesNotContainIgnoringCase(oldFullName)
                        .doesNotContainIgnoringCase(acc.email())
                        .doesNotContain(oldPhone)
                        .doesNotContainIgnoringCase(oldPendingEmail);
            }
        }
    }

    @Test
    void personalRowsRemoved() throws Exception {
        Account acc = createStudent("del.rows", "vi", true);

        MvcResult uploadRes = mockMvc.perform(
                        putAvatar(AvatarTestImages.png(64, 64), "a.png", "image/png").with(asStudent(acc)))
                .andExpect(status().isOk())
                .andReturn();
        String assetId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .path("avatarAssetId").asText();
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(assetId))).isTrue();

        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(s -> emailCodeService.issue(acc.id(), EmailCodePurpose.VERIFY, acc.email()));
        support.insertToken(acc.id(), Instant.now(), Instant.now().plusSeconds(900), null);
        userIdentityRepository.saveAndFlush(new UserIdentity(
                Ids.newId(), acc.id(), IdentityProvider.GOOGLE,
                "google-sub-" + UUID.randomUUID(), acc.email(), Instant.now()));

        assertThat(countRows("identity.user_avatar", acc.id())).isEqualTo(1);
        assertThat(countRows("identity.email_code", acc.id())).isEqualTo(1);
        assertThat(countRows("identity.password_reset_token", acc.id())).isEqualTo(1);
        assertThat(countRows("identity.user_identity", acc.id())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        assertThat(countRows("identity.user_avatar", acc.id())).isZero();
        assertThat(countRows("identity.email_code", acc.id())).isZero();
        assertThat(countRows("identity.password_reset_token", acc.id())).isZero();
        assertThat(countRows("identity.user_identity", acc.id())).isZero();

        avatarService.sweepPendingDeletes();
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(assetId))).isFalse();
    }

    @Test
    void registrationAndImportCleaned() throws Exception {
        String sfx = SelfRegistrationSupport.suffix();
        String selfUsername = "delself." + sfx;
        String selfEmail = selfUsername + "@thi360.vn";
        regSupport.register(regSupport.body(selfUsername, selfEmail), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        UUID selfId = regSupport.accountId(selfUsername);
        extraUserIds.add(selfId);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.pending_registration WHERE username_normalized = ? OR email_normalized = ?",
                Integer.class, selfUsername.toLowerCase(), selfEmail.toLowerCase())).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.pending_registration_send WHERE email_normalized = ?",
                Integer.class, selfEmail.toLowerCase())).isGreaterThanOrEqualTo(1);

        while (outboxRelay.relayBatch() > 0) {
        }
        int auditCountBeforeSelf = jdbc.queryForObject("SELECT count(*) FROM audit.audit_event", Integer.class);
        int mailCountBeforeSelf = jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE lower(to_email) = ?",
                Integer.class, selfEmail.toLowerCase());
        assertThat(mailCountBeforeSelf).isGreaterThanOrEqualTo(1);

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(selfId, selfUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", SelfRegistrationSupport.PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.pending_registration WHERE username_normalized = ? OR email_normalized = ?",
                Integer.class, selfUsername.toLowerCase(), selfEmail.toLowerCase())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.pending_registration_send WHERE email_normalized = ?",
                Integer.class, selfEmail.toLowerCase())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_event", Integer.class))
                .isGreaterThanOrEqualTo(auditCountBeforeSelf);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE lower(to_email) = ?",
                Integer.class, selfEmail.toLowerCase()))
                .isEqualTo(mailCountBeforeSelf + 1);

        Account imported = createStudent("del.imp", "vi", true);
        UUID batchId = Ids.newId();
        importBatchIds.add(batchId);
        jdbc.update("""
                INSERT INTO identity.import_batch (id, status, total_rows, created_by, created_at, updated_at, version)
                VALUES (?, 5, 2, ?, now(), now(), 0)
                """, batchId, imported.id());

        UUID targetRowId = Ids.newId();
        UUID siblingRowId = Ids.newId();
        String siblingUsername = "imp.sibling." + sfx;
        String siblingEmail = siblingUsername + "@thi360.vn";
        jdbc.update("""
                INSERT INTO identity.import_row
                    (id, batch_id, row_no, raw, username_normalized, email_normalized, class_code_normalized,
                     password_generated, errors, status, created_user_id, failure_reason, generated_password_enc)
                VALUES (?, ?, 1, ?::jsonb, ?, ?, 'lop-12a', true,
                        '[{"rowNo":1,"column":"Email","columnKey":"EMAIL","code":"WARN","message":"note"}]'::jsonb,
                        3, ?, 'old failure note', 'enc-secret')
                """,
                targetRowId, batchId,
                objectMapper.writeValueAsString(Map.of(
                        "username", imported.username(),
                        "passwordEnc", "enc-pw",
                        "fullName", "Lê Văn Nhập",
                        "email", imported.email(),
                        "phone", "0988777666",
                        "classCode", "LOP-12A")),
                imported.username().toLowerCase(), imported.email().toLowerCase(), imported.id());
        jdbc.update("UPDATE identity.user_account SET import_row_id = ? WHERE id = ?", targetRowId, imported.id());

        String siblingRawJson = objectMapper.writeValueAsString(Map.of(
                "username", siblingUsername,
                "fullName", "Học Viên Khác",
                "email", siblingEmail,
                "phone", "0977666555",
                "classCode", "LOP-12B"));
        jdbc.update("""
                INSERT INTO identity.import_row
                    (id, batch_id, row_no, raw, username_normalized, email_normalized, class_code_normalized,
                     password_generated, errors, status, created_user_id)
                VALUES (?, ?, 2, ?::jsonb, ?, ?, 'lop-12b', false, '[]'::jsonb, 3, ?)
                """,
                siblingRowId, batchId, siblingRawJson, siblingUsername, siblingEmail, Ids.newId());

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(imported))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", imported.password()))))
                .andExpect(status().isNoContent());

        String expectedAnon = "deleted-" + imported.id().toString().toLowerCase();
        Map<String, Object> targetRow = jdbc.queryForMap(
                "SELECT row_no, status, raw::text AS raw_text, username_normalized, email_normalized, "
                        + "generated_password_enc, failure_reason, errors::text AS errors_text "
                        + "FROM identity.import_row WHERE id = ?",
                targetRowId);
        assertThat(((Number) targetRow.get("row_no")).intValue()).isEqualTo(1);
        assertThat(((Number) targetRow.get("status")).intValue()).isEqualTo(3);
        assertThat(targetRow.get("username_normalized")).isEqualTo(expectedAnon);
        assertThat(targetRow.get("email_normalized")).isNull();
        assertThat(targetRow.get("generated_password_enc")).isNull();
        assertThat(targetRow.get("failure_reason")).isNull();
        assertThat(objectMapper.readTree((String) targetRow.get("errors_text"))).isEmpty();
        JsonNode scrubbedRaw = objectMapper.readTree((String) targetRow.get("raw_text"));
        assertThat(scrubbedRaw.path("username").asText()).isEqualTo(expectedAnon);
        assertThat(scrubbedRaw.path("fullName").asText()).isEqualTo(expectedAnon);
        assertThat(scrubbedRaw.path("passwordEnc").isNull()).isTrue();
        assertThat(scrubbedRaw.path("email").isNull()).isTrue();
        assertThat(scrubbedRaw.path("phone").isNull()).isTrue();
        assertThat(scrubbedRaw.path("classCode").asText()).isEqualTo("LOP-12A");

        Map<String, Object> siblingRow = jdbc.queryForMap(
                "SELECT raw::text AS raw_text, username_normalized, email_normalized FROM identity.import_row WHERE id = ?",
                siblingRowId);
        assertThat(siblingRow.get("username_normalized")).isEqualTo(siblingUsername);
        assertThat(siblingRow.get("email_normalized")).isEqualTo(siblingEmail);
        assertThat(objectMapper.readTree((String) siblingRow.get("raw_text")).path("fullName").asText())
                .isEqualTo("Học Viên Khác");
    }

    @Test
    void auditOutboxAndClass() throws Exception {
        Account acc = createStudent("del.aud", "vi", true);
        String oldPhone = "0933444555";
        jdbc.update("UPDATE identity.user_account SET phone = ? WHERE id = ?", oldPhone, acc.id());
        UUID classId = createClassRoom();
        jdbc.update("INSERT INTO classroom.class_member (class_id, user_id, joined_at) VALUES (?, ?, now())",
                classId, acc.id());

        while (outboxRelay.relayBatch() > 0) {
        }

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        String expectedAnon = "deleted-" + acc.id().toString().toLowerCase();
        List<String> userDeletedPayloads = jdbc.queryForList("""
                SELECT payload::text FROM shared.outbox_event
                WHERE topic = 'identity.user.deleted' AND payload->>'userId' = ?
                """, String.class, acc.id().toString());
        assertThat(userDeletedPayloads).hasSize(1);
        assertThat(objectMapper.readTree(userDeletedPayloads.getFirst()).path("username").asText())
                .isEqualTo(expectedAnon);

        while (outboxRelay.relayBatch() > 0) {
        }

        List<Map<String, Object>> audits = jdbc.queryForList("""
                SELECT actor_id, actor_username, target_id, target_label, before::text AS before_json,
                       after::text AS after_json, reason
                FROM audit.audit_event
                WHERE action = 'USER_SELF_DELETED' AND target_id = ?
                """, acc.id().toString());
        assertThat(audits).hasSize(1);
        Map<String, Object> ev = audits.getFirst();
        assertThat(ev.get("actor_id")).isEqualTo(acc.id());
        assertThat(ev.get("actor_username")).isEqualTo(expectedAnon);
        assertThat(ev.get("target_label")).isEqualTo(expectedAnon);
        assertThat(ev.get("reason")).isEqualTo("SELF_DELETED");
        String combinedAudit = String.valueOf(ev);
        assertThat(combinedAudit)
                .doesNotContainIgnoringCase(acc.username())
                .doesNotContainIgnoringCase(acc.email())
                .doesNotContain(oldPhone)
                .doesNotContain(acc.password());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM classroom.class_member WHERE class_id = ? AND user_id = ?",
                Integer.class, classId, acc.id())).isZero();
        List<String> removalReasons = jdbc.queryForList(
                "SELECT reason FROM classroom.class_member_history WHERE class_id = ? AND user_id = ? AND action = 2",
                String.class, classId, acc.id());
        assertThat(removalReasons).contains("SELF_DELETED");
    }

    @Test
    void dataRetained() throws Exception {
        Account acc = createStudent("del.retain", "vi", true);
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        LearnerAccount learner = asLearner(acc);

        var order = commerceFixtures.insertStubOrder(
                learner, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = commerceFixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID refundId = Ids.newId();
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 50000, 'API', 'SUCCEEDED', 'Retain check', true, 'ops', ?, now(), now(), now(), 0)
                """, refundId, UUID.randomUUID().toString().replace("-", ""), "ret-" + UUID.randomUUID(),
                payment.id(), order.id(), UUID.randomUUID());
        var grant = commerceFixtures.grantOrder(
                entitlementGrantService, order.id(), order.code(), acc.id(), exam.examId(), 3);

        UUID attemptId = insertScoredAttemptWithEssay(acc.id(), exam.examId(), grant.entitlementId(), grant.grantId());
        UUID classId = createClassRoom();
        jdbc.update("INSERT INTO classroom.class_member (class_id, user_id, joined_at) VALUES (?, ?, now())",
                classId, acc.id());

        Map<String, Object> orderBefore = jdbc.queryForMap(
                "SELECT buyer_username, buyer_full_name, state, amount_due_vnd FROM commerce.purchase_order WHERE id = ?",
                order.id());
        Map<String, Object> paymentBefore = jdbc.queryForMap(
                "SELECT state, amount_vnd FROM commerce.payment WHERE id = ?", payment.id());
        Map<String, Object> refundBefore = jdbc.queryForMap(
                "SELECT state, amount_vnd FROM commerce.refund WHERE id = ?", refundId);
        Map<String, Object> entBefore = jdbc.queryForMap(
                "SELECT attempts_total, attempts_used FROM commerce.entitlement WHERE id = ?", grant.entitlementId());
        Map<String, Object> attemptBefore = jdbc.queryForMap(
                "SELECT state, raw_score, display_score FROM attempt.attempt WHERE id = ?", attemptId);
        List<Map<String, Object>> itemsBefore = jdbc.queryForList(
                "SELECT id, answer::text AS answer_text, earned_points FROM attempt.attempt_item WHERE attempt_id = ? ORDER BY ord",
                attemptId);
        List<Map<String, Object>> essaysBefore = jdbc.queryForList("""
                SELECT eg.id, eg.points, eg.comment, eg.state
                FROM attempt.essay_grade eg
                JOIN attempt.attempt_item ai ON ai.id = eg.attempt_item_id
                WHERE ai.attempt_id = ?
                """, attemptId);

        try {
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .with(asStudent(acc))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                    .andExpect(status().isNoContent());

            assertThat(jdbc.queryForMap(
                    "SELECT buyer_username, buyer_full_name, state, amount_due_vnd FROM commerce.purchase_order WHERE id = ?",
                    order.id())).isEqualTo(orderBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT state, amount_vnd FROM commerce.payment WHERE id = ?", payment.id()))
                    .isEqualTo(paymentBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT state, amount_vnd FROM commerce.refund WHERE id = ?", refundId))
                    .isEqualTo(refundBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT attempts_total, attempts_used FROM commerce.entitlement WHERE id = ?", grant.entitlementId()))
                    .isEqualTo(entBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT state, raw_score, display_score FROM attempt.attempt WHERE id = ?", attemptId))
                    .isEqualTo(attemptBefore);
            assertThat(jdbc.queryForList(
                    "SELECT id, answer::text AS answer_text, earned_points FROM attempt.attempt_item WHERE attempt_id = ? ORDER BY ord",
                    attemptId)).isEqualTo(itemsBefore);
            assertThat(jdbc.queryForList("""
                    SELECT eg.id, eg.points, eg.comment, eg.state
                    FROM attempt.essay_grade eg
                    JOIN attempt.attempt_item ai ON ai.id = eg.attempt_item_id
                    WHERE ai.attempt_id = ?
                    """, attemptId)).isEqualTo(essaysBefore);
        } finally {
            jdbc.update("DELETE FROM commerce.refund WHERE id = ?", refundId);
        }
    }

    @Test
    void adminApiShowsSelfDeleted() throws Exception {
        Account acc = createStudent("del.adm", "vi", true);
        String oldFullName = "Phạm Quốc Bảo";
        jdbc.update("UPDATE identity.user_account SET full_name = ? WHERE id = ?", oldFullName, acc.id());

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        String expectedAnon = "deleted-" + acc.id().toString().toLowerCase();
        MvcResult detailRes = mockMvc.perform(get("/api/v1/admin/users/{id}", acc.id())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.deleteReason").value("SELF_DELETED"))
                .andExpect(jsonPath("$.username").value(expectedAnon))
                .andExpect(jsonPath("$.fullName").value(expectedAnon))
                .andExpect(jsonPath("$.email").isEmpty())
                .andReturn();
        String detailBody = detailRes.getResponse().getContentAsString();
        assertThat(detailBody)
                .doesNotContainIgnoringCase(acc.username())
                .doesNotContainIgnoringCase(oldFullName)
                .doesNotContainIgnoringCase(acc.email());

        MvcResult listRes = mockMvc.perform(get("/api/v1/admin/users")
                        .param("includeDeleted", "true")
                        .param("q", expectedAnon)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(acc.id().toString()))
                .andExpect(jsonPath("$.items[0].username").value(expectedAnon))
                .andExpect(jsonPath("$.items[0].fullName").value(expectedAnon))
                .andExpect(jsonPath("$.items[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].email").isEmpty())
                .andReturn();
        String listBody = listRes.getResponse().getContentAsString();
        assertThat(listBody)
                .doesNotContainIgnoringCase(acc.username())
                .doesNotContainIgnoringCase(oldFullName)
                .doesNotContainIgnoringCase(acc.email());
    }

    @Test
    void wrongPasswordChangesNothing(CapturedOutput output) throws Exception {
        Account acc = createStudent("del.wpwd", "vi", true);
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        insertEntitlement(acc.id(), exam.examId(), "PURCHASE", null, 2, 0, 0, 0);
        MvcResult uploadRes = mockMvc.perform(
                        putAvatar(AvatarTestImages.png(64, 64), "a.png", "image/png").with(asStudent(acc)))
                .andExpect(status().isOk())
                .andReturn();
        String assetId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .path("avatarAssetId").asText();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = login.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        String sentWrongPassword = "UltraSecretWrongPwd#8842";
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", sentWrongPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_OLD_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        UserAccount unchanged = userAccountRepository.findById(acc.id()).orElseThrow();
        assertThat(unchanged.isDeleted()).isFalse();
        assertThat(unchanged.getUsername()).isEqualTo(acc.username());
        mockMvc.perform(get("/api/v1/me").cookie(access)).andExpect(status().isOk());
        assertThat(countRows("identity.user_avatar", acc.id())).isEqualTo(1);
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(assetId))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement WHERE user_id = ?", Integer.class, acc.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                Integer.class, acc.id())).isZero();
        while (outboxRelay.relayBatch() > 0) {
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = 'USER_SELF_DELETED' AND target_id = ?",
                Integer.class, acc.id().toString())).isZero();
        assertThat(output.getAll()).doesNotContain(sentWrongPassword);
    }

    @Test
    void throttled() throws Exception {
        Account acc = createStudent("del.throt", "vi", true);
        doThrow(new KeycloakAuthException(AuthFailureReason.ACCOUNT_LOCKED))
                .when(keycloakSpy)
                .passwordGrant(any(), eq(acc.username()), any());

        try {
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .with(asStudent(acc))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
        } finally {
            reset(keycloakSpy);
        }

        UserAccount unchanged = userAccountRepository.findById(acc.id()).orElseThrow();
        assertThat(unchanged.isDeleted()).isFalse();
        assertThat(unchanged.getUsername()).isEqualTo(acc.username());
    }

    @Test
    void blockedChangesNothing() throws Exception {
        var exam = commerceFixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        Account acc = createStudent("del.blkpost", "vi", true);

        mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asStudent(acc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(true));

        insertAttempt(acc.id(), exam.examId(), "IN_PROGRESS");

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_BLOCKED"))
                .andExpect(jsonPath("$.blockers").value(Matchers.contains("ATTEMPT_IN_PROGRESS")));

        UserAccount stillActive = userAccountRepository.findById(acc.id()).orElseThrow();
        assertThat(stillActive.isDeleted()).isFalse();
        assertThat(stillActive.getUsername()).isEqualTo(acc.username());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                Integer.class, acc.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "ADMIN", "TEACHER", "STUDENT_AND_TEACHER", "PLATFORM_ADMIN", "UNAUTHENTICATED"})
    void permissionMatrix(String subject) throws Exception {
        if ("UNAUTHENTICATED".equals(subject)) {
            mockMvc.perform(get("/api/v1/me/account/deletion-check"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", PasswordResetTestSupport.PASSWORD))))
                    .andExpect(status().isUnauthorized());
            return;
        }
        if ("PLATFORM_ADMIN".equals(subject)) {
            mockMvc.perform(get("/api/v1/me/account/deletion-check").with(asPlatformAdmin()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_NOT_ALLOWED"));
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .with(asPlatformAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", PasswordResetTestSupport.PASSWORD))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_NOT_ALLOWED"));
            return;
        }

        Account acc = createStudent("del.perm." + subject.toLowerCase(), "vi", true);
        if ("ADMIN".equals(subject)) {
            assignRole(acc.id(), "system_admin");
        } else if ("TEACHER".equals(subject)) {
            assignRole(acc.id(), "content_admin");
        } else if ("STUDENT_AND_TEACHER".equals(subject)) {
            assignRole(acc.id(), "student");
            assignRole(acc.id(), "content_admin");
        } else {
            assignRole(acc.id(), "student");
        }

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = login.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        if ("STUDENT".equals(subject)) {
            mockMvc.perform(get("/api/v1/me/account/deletion-check").cookie(access))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .cookie(access, xsrf)
                            .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                    .andExpect(status().isNoContent());
        } else {
            mockMvc.perform(get("/api/v1/me/account/deletion-check").cookie(access))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_NOT_ALLOWED"));
            mockMvc.perform(post("/api/v1/me/account/deletion")
                            .cookie(access, xsrf)
                            .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_NOT_ALLOWED"));
            assertThat(userAccountRepository.findById(acc.id()).orElseThrow().isDeleted()).isFalse();
        }
    }

    @Test
    void mail() throws Exception {
        Account viAcc = createStudent("del.mail.vi", "vi", false);
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(viAcc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", viAcc.password()))))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> viMails = jdbc.queryForList(
                "SELECT to_email, subject, locale FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                viAcc.id());
        assertThat(viMails).hasSize(1);
        assertThat(viMails.getFirst().get("to_email")).isEqualTo(viAcc.email());
        assertThat(viMails.getFirst().get("locale")).isEqualTo("vi");
        assertThat(viMails.getFirst().get("subject")).isEqualTo("Tài khoản Thi360 của bạn đã được xóa");

        Account enAcc = createStudent("del.mail.en", "en", true);
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(enAcc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", enAcc.password()))))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> enMails = jdbc.queryForList(
                "SELECT to_email, subject, locale FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                enAcc.id());
        assertThat(enMails).hasSize(1);
        assertThat(enMails.getFirst().get("to_email")).isEqualTo(enAcc.email());
        assertThat(enMails.getFirst().get("locale")).isEqualTo("en");
        assertThat(enMails.getFirst().get("subject")).isEqualTo("Your Thi360 account has been deleted");

        Account noEmail = createStudentWithoutEmail("del.mail.no");
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(noEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", noEmail.password()))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                Integer.class, noEmail.id())).isZero();
    }

    @Test
    void reRegister() throws Exception {
        String sfx = SelfRegistrationSupport.suffix();
        String username = "delrereg." + sfx;
        String email = username + "@thi360.vn";
        regSupport.register(regSupport.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        UUID oldId = regSupport.accountId(username);
        extraUserIds.add(oldId);

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(oldId, username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", SelfRegistrationSupport.PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(keycloakSpy.findUserIdByUsername(username)).isEmpty();

        MvcResult reregRes = regSupport.register(regSupport.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated())
                .andReturn();
        UUID newId = regSupport.accountId(username);
        extraUserIds.add(newId);
        assertThat(newId).isNotNull().isNotEqualTo(oldId);

        Cookie newAccess = reregRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        mockMvc.perform(get("/api/v1/me/exams").cookie(newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/v1/me/orders").cookie(newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/v1/me/attempts").cookie(newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void repeatAfterDelete() throws Exception {
        Account acc = createStudent("del.rep", "vi", true);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", acc.username(), "password", acc.password()))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = login.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        Map<String, Object> afterFirst = jdbc.queryForMap(
                "SELECT version, updated_at FROM identity.user_account WHERE id = ?", acc.id());

        mockMvc.perform(get("/api/v1/me/account/deletion-check").cookie(access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .cookie(access, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForMap(
                "SELECT version, updated_at FROM identity.user_account WHERE id = ?", acc.id()))
                .isEqualTo(afterFirst);
    }

    @Test
    void deletedShortCircuitsBeforePassword() throws Exception {
        Account acc = createStudent("del.sc", "vi", true);
        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(asStudent(acc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", acc.password()))))
                .andExpect(status().isNoContent());

        clearInvocations(keycloakSpy);
        CurrentUser deletedUser = new CurrentUser(
                acc.id(), acc.username(), Realm.APP, Access.EXAM, PermissionSet.empty());

        accountSelfDeletionService.delete(deletedUser, "Wrong@9999");

        verify(keycloakSpy, never()).passwordGrant(any(), any(), any());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE user_id = ? AND kind = 'ACCOUNT_DELETED'",
                Integer.class, acc.id())).isEqualTo(1);
        while (outboxRelay.relayBatch() > 0) {
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = 'USER_SELF_DELETED' AND target_id = ?",
                Integer.class, acc.id().toString())).isEqualTo(1);
    }

    private Account createStudent(String prefix, String locale, boolean emailVerified) {
        Account acc = support.create(prefix, vn.unicom.thi360.identity.domain.UserStatus.ACTIVE, emailVerified, false);
        jdbc.update("UPDATE identity.user_account SET preferred_locale = ? WHERE id = ?", locale, acc.id());
        return acc;
    }

    private Account createStudentWithoutEmail(String prefix) {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "." + sfx;
        UUID userId = keycloakSpy.createUser(new CreateUserCommand(
                username, null, "No", "Email", PasswordResetTestSupport.PASSWORD, true, false, false));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, preferred_locale,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'No Email User', 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username.toLowerCase(), now, userId, now, userId);
        extraUserIds.add(userId);
        return new Account(userId, username, null, PasswordResetTestSupport.PASSWORD);
    }

    private void assignRole(UUID userId, String roleCodeNormalized) {
        UUID roleId = roleRepository.findByCodeNormalized(roleCodeNormalized).orElseThrow().getId();
        jdbc.update("""
                INSERT INTO identity.user_role (user_id, role_id, assigned_at)
                VALUES (?, ?, now()) ON CONFLICT DO NOTHING
                """, userId, roleId);
    }

    private RequestPostProcessor asStudent(Account acc) {
        return asStudent(acc.id(), acc.username());
    }

    private RequestPostProcessor asStudent(UUID userId, String username) {
        return testSecuritySupport.apply(new SimulatedPrincipal(userId, username, "STUDENT", false, List.of()));
    }

    private static LearnerAccount asLearner(Account acc) {
        return new LearnerAccount(acc.id(), acc.username(), "Nguyễn Văn Test", acc.email());
    }

    private UUID createClassRoom() {
        UUID classId = Ids.newId();
        String code = "DELCLS" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO classroom.class_room (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), 0)
                """, classId, code, code.toLowerCase(), "Class " + code);
        classIds.add(classId);
        return classId;
    }

    private void insertEntitlement(
            UUID userId, UUID examId, String source, UUID sourceRef,
            int total, int used, int reserved, int revoked) {
        jdbc.update("""
                INSERT INTO commerce.entitlement
                    (id, user_id, exam_id, source, source_ref, attempts_total, attempts_used,
                     attempts_reserved, attempts_revoked, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                """, Ids.newId(), userId, examId, source, sourceRef, total, used, reserved, revoked);
        extraUserIds.add(userId);
    }

    private UUID insertAttempt(UUID userId, UUID examId, String state) {
        UUID id = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO attempt.attempt
                    (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, exam_name,
                     exam_kind_code, scope, timing, navigation_mode, choices, seed, generation_token,
                     state, regrading, result_view, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, 'Exam Test', 'HSA', 'FULL', 'SHARED', 'FREE', '{}'::jsonb,
                        1, ?, ?, false, 'FULL_SOLUTION', 0, ?, ?)
                """, id, userId, examId, Ids.newId(), Ids.newId(), Ids.newId(), state, now, now);
        attemptIds.add(id);
        return id;
    }

    private UUID insertScoredAttemptWithEssay(UUID userId, UUID examId, UUID entitlementId, UUID grantId) {
        UUID attemptId = Ids.newId();
        UUID partId = Ids.newId();
        UUID mcqItemId = Ids.newId();
        UUID essayItemId = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO attempt.attempt
                    (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, attempt_no, exam_name,
                     exam_kind_code, scope, timing, navigation_mode, choices, seed, generation_token,
                     state, regrading, end_reason, started_at, finished_at, result_view,
                     max_score, raw_score, display_score, graded_at, version, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, 1, 'Retain Exam', 'HSA', 'FULL', 'SHARED', 'FREE', '{}'::jsonb,
                        42, ?, 'SCORED', false, 'SUBMITTED', ?, ?, 'FULL_SOLUTION',
                        10.0000, 8.5000, 8.5000, ?, 0, ?, ?)
                """, attemptId, userId, examId, entitlementId, grantId, Ids.newId(), now, now, now, now, now);
        jdbc.update("""
                INSERT INTO attempt.attempt_part
                    (id, attempt_id, part_code, part_name, ord, state, seed, version)
                VALUES (?, ?, 'P1', 'Part 1', 1, 'CLOSED', 42, 0)
                """, partId, attemptId);
        jdbc.update("""
                INSERT INTO attempt.attempt_item
                    (id, attempt_id, part_id, section_id, block_id, ord, question_id, question_version_no,
                     question_type, points_max, in_scope, answer, answer_seq, earned_points, outcome, version)
                VALUES (?, ?, ?, 'S1', 'B1', 1, ?, 1, 'SINGLE_CHOICE', 5.0000, true, '{"option":"A"}'::jsonb, 1, 5.0000, 'CORRECT', 0),
                       (?, ?, ?, 'S1', 'B2', 2, ?, 1, 'ESSAY', 5.0000, true, '{"text":"Bai tu luan"}'::jsonb, 1, 3.5000, 'PARTIAL', 0)
                """, mcqItemId, attemptId, partId, Ids.newId(), essayItemId, attemptId, partId, Ids.newId());
        jdbc.update("""
                INSERT INTO attempt.essay_grade
                    (id, attempt_item_id, grader_id, points, comment, state, claimed_at, last_activity_at, finalized_at, version)
                VALUES (?, ?, ?, ?, 'Good essay', 'FINAL', ?, ?, ?, 0)
                """, Ids.newId(), essayItemId, Ids.newId(), new BigDecimal("3.5000"), now, now, now);
        attemptIds.add(attemptId);
        return attemptId;
    }

    private int countRows(String table, UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private String freshIp() {
        String ip = SelfRegistrationSupport.freshIp();
        usedIps.add(ip);
        return ip;
    }

    private void cleanupUserData(UUID userId) {
        CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
        jdbc.update("DELETE FROM classroom.class_member_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.email_code WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.password_reset_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.user_avatar WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        redis.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
        try {
            keycloakSpy.deleteUser(userId);
        } catch (Exception ignored) {
        }
    }
}
