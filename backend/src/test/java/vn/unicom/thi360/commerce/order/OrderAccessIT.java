package vn.unicom.thi360.commerce.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-8 & AC-11: chặn ẩn danh (401), chặn tài khoản MANAGE hoặc không ACTIVE (403), và cô lập đơn giữa hai học viên (404).
 */
class OrderAccessIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void enforcesAuthenticationAndActiveExamRoleAndTenantIsolation() throws Exception {
        var alice = fixtures.createLearner();
        var bob = fixtures.createLearner();
        var pendingLearner = fixtures.createLearner(false, "vi", 1, false);
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var aliceOrder = fixtures.insertStubOrder(
                alice, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);

        String createBody = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", alice.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 200_000L));

        // 1. Ẩn danh -> 401.
        mockMvc.perform(get("/api/v1/me/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/orders/" + aliceOrder.code())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/pay")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Idempotency-Key", "anon-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isUnauthorized());

        // 2. Tài khoản MANAGE hoặc không ACTIVE -> 403 trên tất cả API /me/orders*.
        var manager = fixtures.asManager(UUID.randomUUID(), "ops.mgr", "ORDER.VIEW");
        mockMvc.perform(get("/api/v1/me/orders").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/orders/" + aliceOrder.code()).with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/pay").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/cancel").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(manager)
                        .header("Idempotency-Key", "mgr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me/orders").with(fixtures.asLearner(pendingLearner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(pendingLearner))
                        .header("Idempotency-Key", "pend-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", pendingLearner.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isForbidden());

        // 3. Đơn của Alice: Alice xem danh sách và chi tiết (kể cả mã chữ thường, không lộ trường nội bộ);
        // Bob nhận danh sách rỗng và nhận 404 ở xem, /pay, /cancel.
        mockMvc.perform(get("/api/v1/me/orders").with(fixtures.asLearner(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(aliceOrder.code()));

        mockMvc.perform(get("/api/v1/me/orders/" + aliceOrder.code().toLowerCase(Locale.ROOT))
                        .with(fixtures.asLearner(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(aliceOrder.code()))
                .andExpect(jsonPath("$.productCode").value(product.code()))
                .andExpect(jsonPath("$.items[0].examCode").value(product.code()))
                .andExpect(jsonPath("$.payments").isArray())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.fulfillmentError").doesNotExist())
                .andExpect(jsonPath("$.fulfillmentTries").doesNotExist())
                .andExpect(jsonPath("$.needsReview").doesNotExist())
                .andExpect(jsonPath("$.reviewReason").doesNotExist());

        mockMvc.perform(get("/api/v1/me/orders").with(fixtures.asLearner(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/v1/me/orders/" + aliceOrder.code()).with(fixtures.asLearner(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/pay").with(fixtures.asLearner(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrder.code() + "/cancel").with(fixtures.asLearner(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void respectsCallerRequestedSortOnListMyOrders() throws Exception {
        var buyer = fixtures.createLearner();
        var cheap = fixtures.insertOnSaleProduct("HSA", 100_000L, 1);
        var expensive = fixtures.insertOnSaleProduct("QDA", 300_000L, 2);

        fixtures.insertStubOrder(
                buyer, expensive, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                300_000L, Instant.now().plusSeconds(900), null);
        fixtures.insertStubOrder(
                buyer, cheap, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                100_000L, Instant.now().plusSeconds(900), null);

        mockMvc.perform(get("/api/v1/me/orders")
                        .param("sort", "amountDueVnd,asc")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amountDueVnd").value(100_000L))
                .andExpect(jsonPath("$.items[1].amountDueVnd").value(300_000L));

        mockMvc.perform(get("/api/v1/me/orders")
                        .param("sort", "amountDueVnd,desc")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amountDueVnd").value(300_000L))
                .andExpect(jsonPath("$.items[1].amountDueVnd").value(100_000L));
    }
}
