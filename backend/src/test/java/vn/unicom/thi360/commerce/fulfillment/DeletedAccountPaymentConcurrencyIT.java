package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.identity.application.CurrentPasswordVerifier;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.spi.AccountDeletionState;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

class DeletedAccountPaymentConcurrencyIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";
    private static final int ITERATIONS = 20;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private KeycloakIdentityClient keycloakClient;

    @MockitoSpyBean private CurrentPasswordVerifier currentPasswordVerifier;
    @MockitoSpyBean private FulfillmentService fulfillmentService;
    @MockitoSpyBean private AccountDeletionState accountDeletionState;
    @MockitoSpyBean private MailQueue mailQueue;

    private CommerceTestFixtures fixtures;
    private final List<UUID> createdUserIds = new ArrayList<>();

    private AccountDeletionState deletionStateSpy() {
        return AopTestUtils.getUltimateTargetObject(accountDeletionState);
    }

    private MailQueue mailQueueSpy() {
        return AopTestUtils.getUltimateTargetObject(mailQueue);
    }

    @BeforeEach
    void setUp() {
        reset(currentPasswordVerifier, fulfillmentService, deletionStateSpy(), mailQueueSpy());
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(currentPasswordVerifier, fulfillmentService, deletionStateSpy(), mailQueueSpy());
        for (UUID userId : createdUserIds) {
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redis.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
        fixtures.cleanup();
    }

    @Test
    void twentyIterationsEachForBeforeAndAfterAccountRowLockNeverGrantAfterDeletedAt() throws Exception {
        StoredTestProduct exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int iter = 0; iter < ITERATIONS; iter++) {
                final int iterNo = iter;
                reset(currentPasswordVerifier, fulfillmentService, deletionStateSpy(), mailQueueSpy());
                LearnerAccount buyer = createKeycloakStudent("dpay.pre." + iterNo);
                var order = fixtures.insertStubOrder(
                        buyer, exam, OrderState.CANCELLED, PaymentMethod.VNPAY,
                        200_000L, Instant.now().plusSeconds(900), null);
                var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

                CyclicBarrier barrier = new CyclicBarrier(2);
                doAnswer(inv -> {
                    inv.callRealMethod();
                    barrier.await(10, TimeUnit.SECONDS);
                    return null;
                }).when(currentPasswordVerifier).verify(any(), any());
                doAnswer(inv -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return inv.callRealMethod();
                }).when(fulfillmentService).fulfill(order.id());

                Future<MvcResult> delFuture = pool.submit(() -> callDelete(buyer));
                Future<MvcResult> ipnFuture = pool.submit(() -> callIpn(payment.txnRef(), 200_000L, "9940" + iterNo));

                MvcResult delRes = delFuture.get(30, TimeUnit.SECONDS);
                MvcResult ipnRes = ipnFuture.get(30, TimeUnit.SECONDS);
                assertValidOutcome("before-lock iter " + iterNo, buyer.id(), order.id(), delRes, ipnRes);
            }

            for (int iter = 0; iter < ITERATIONS; iter++) {
                final int iterNo = iter;
                reset(currentPasswordVerifier, fulfillmentService, deletionStateSpy(), mailQueueSpy());
                LearnerAccount buyer = createKeycloakStudent("dpay.post." + iterNo);
                var order = fixtures.insertStubOrder(
                        buyer, exam, OrderState.CANCELLED, PaymentMethod.VNPAY,
                        200_000L, Instant.now().plusSeconds(900), null);
                var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

                CountDownLatch deletionLockedAccount = new CountDownLatch(1);
                CountDownLatch ipnEnteringAccountLock = new CountDownLatch(1);

                doAnswer(inv -> {
                    Object id = inv.callRealMethod();
                    if (inv.getArgument(0) == MailKind.ACCOUNT_DELETED && buyer.id().equals(inv.getArgument(4))) {
                        deletionLockedAccount.countDown();
                        ipnEnteringAccountLock.await(2, TimeUnit.SECONDS);
                    }
                    return id;
                }).when(mailQueueSpy()).enqueue(any(), any(), any(), any(), any());
                doAnswer(inv -> {
                    ipnEnteringAccountLock.countDown();
                    return inv.callRealMethod();
                }).when(deletionStateSpy()).lockAndIsDeleted(buyer.id());

                Future<MvcResult> delFuture = pool.submit(() -> {
                    try {
                        return callDelete(buyer);
                    } finally {
                        deletionLockedAccount.countDown();
                    }
                });
                Future<MvcResult> ipnFuture = pool.submit(() -> {
                    try {
                        deletionLockedAccount.await(10, TimeUnit.SECONDS);
                        return callIpn(payment.txnRef(), 200_000L, "9950" + iterNo);
                    } finally {
                        ipnEnteringAccountLock.countDown();
                    }
                });

                MvcResult delRes = delFuture.get(30, TimeUnit.SECONDS);
                MvcResult ipnRes = ipnFuture.get(30, TimeUnit.SECONDS);
                assertValidOutcome("after-lock iter " + iterNo, buyer.id(), order.id(), delRes, ipnRes);
            }
        }
    }

    private void assertValidOutcome(
            String label, UUID userId, UUID orderId, MvcResult delRes, MvcResult ipnRes) throws Exception {
        int delStatus = delRes.getResponse().getStatus();
        int ipnStatus = ipnRes.getResponse().getStatus();
        assertThat(ipnStatus).as("%s IPN HTTP status", label).isEqualTo(200);
        assertThat(mapper.readTree(ipnRes.getResponse().getContentAsString()).path("RspCode").asText())
                .as("%s IPN RspCode", label).isEqualTo("00");
        assertThat(delStatus)
                .as("%s delete status must be 204 or 409 (never 5xx): body=%s",
                        label, delRes.getResponse().getContentAsString())
                .isIn(204, 409);

        Timestamp deletedAt = jdbc.queryForObject(
                "SELECT deleted_at FROM identity.user_account WHERE id = ?", Timestamp.class, userId);
        List<Timestamp> grantTimes = jdbc.queryForList(
                "SELECT granted_at FROM commerce.entitlement_grant WHERE order_id = ?", Timestamp.class, orderId);
        Map<String, Object> orderRow = jdbc.queryForMap(
                "SELECT state, needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", orderId);

        if (delStatus == 409) {
            assertThat(mapper.readTree(delRes.getResponse().getContentAsString()).path("code").asText())
                    .isEqualTo("ACCOUNT_DELETION_BLOCKED");
            assertThat(deletedAt).as("%s account must remain active on 409", label).isNull();
            return;
        }

        assertThat(deletedAt).as("%s deleted_at on 204", label).isNotNull();
        if (grantTimes.isEmpty()) {
            assertThat(orderRow.get("state")).as("%s order state", label).isEqualTo("PAID");
            assertThat(orderRow.get("needs_review")).as("%s needs_review", label).isEqualTo(true);
            assertThat(orderRow.get("review_reason")).as("%s review_reason", label).isEqualTo("ACCOUNT_DELETED");
        } else {
            assertThat(orderRow.get("state")).as("%s order state when granted", label).isEqualTo("FULFILLED");
            for (Timestamp grantedAt : grantTimes) {
                assertThat(grantedAt.toInstant())
                        .as("%s grant must not be created after deleted_at", label)
                        .isBeforeOrEqualTo(deletedAt.toInstant());
            }
        }
    }

    private MvcResult callDelete(LearnerAccount buyer) throws Exception {
        return mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(testSecuritySupport.apply(new SimulatedPrincipal(
                                buyer.id(), buyer.username(), "STUDENT", false, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andReturn();
    }

    private MvcResult callIpn(String txnRef, long amountVnd, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don race");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        return mockMvc.perform(req).andReturn();
    }

    private LearnerAccount createKeycloakStudent(String prefix) {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "." + sfx;
        String email = username + "@thi360-test.example";
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Race", "Buyer", PASSWORD, true, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'Race Buyer', ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username.toLowerCase(),
                email, email.toLowerCase(), now, now, userId, now, userId);
        createdUserIds.add(userId);
        return new LearnerAccount(userId, username, "Race Buyer", email);
    }
}
