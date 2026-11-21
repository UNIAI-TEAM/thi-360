package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử đồng thời và ranh giới giao dịch 2 pha của hoàn tiền API (AC-10, AC-14).
 */
class RefundConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private SecurityProperties securityProperties;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        Mockito.reset(vnpayGateway);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(vnpayGateway);
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void tenConcurrentRefundRequestsCreateAtMostOneAndNeverExceedPaidAmount() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90020011', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, payment.id());

        doAnswer(inv -> {
            Thread.sleep(40L);
            return inv.callRealMethod();
        }).when(vnpayGateway).refund(any());

        int threads = 10;
        UUID adminId = UUID.randomUUID();
        String payload = mapper.writeValueAsString(Map.of(
                "amountVnd", 150_000L,
                "kind", "API",
                "reason", "Hoan dong thoi 10 luong",
                "verifiedSystemError", true));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final String idemKey = "conc-" + i + "-" + UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                                    + "/payments/" + payment.id() + "/refunds")
                                    .with(asUser(adminId, "conc-admin", Permissions.ORDER_REFUND))
                                    .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                                    .header("Idempotency-Key", idemKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1L);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threads - 1L);
        } finally {
            pool.shutdownNow();
        }

        Integer succeededCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.refund WHERE payment_id = ? AND state = 'SUCCEEDED'",
                Integer.class, payment.id());
        Long succeededSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_vnd), 0) FROM commerce.refund WHERE payment_id = ? AND state = 'SUCCEEDED'",
                Long.class, payment.id());
        assertThat(succeededCount).isEqualTo(1);
        assertThat(succeededSum).isEqualTo(150_000L).isLessThanOrEqualTo(200_000L);
    }

    @Test
    void gatewayCallExecutesOutsideDbTransactionAndTimeoutLeavesUnknownUnlocked() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90020022', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, payment.id());

        AtomicReference<Boolean> txActiveDuringGateway = new AtomicReference<>();
        doAnswer(inv -> {
            txActiveDuringGateway.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new RuntimeException("Read timed out");
        }).when(vnpayGateway).refund(any());

        UUID adminId = UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of(
                "amountVnd", 200_000L,
                "kind", "API",
                "reason", "Kiem tra timeout ngoai DB TX",
                "verifiedSystemError", true));

        String raw = mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "timeout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("UNKNOWN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refundId = mapper.readTree(raw).path("id").asText();

        assertThat(txActiveDuringGateway.get()).isFalse();

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT signature_valid, raw->>'error' AS err FROM commerce.payment_event WHERE payment_id = ? AND source = 'REFUND_API'",
                payment.id());
        assertThat(event.get("signature_valid")).isEqualTo(false);
        assertThat(event.get("err")).isEqualTo("Read timed out");

        // Dòng DB không bị giữ khóa: có thể mark-failed ngay lập tức.
        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "reason", "Xac nhan timeout tu doi soat",
                                "evidenceRef", "TICKET-TIMEOUT-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }
}
