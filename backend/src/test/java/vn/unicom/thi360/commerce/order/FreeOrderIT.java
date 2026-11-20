package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-16 & AC-34: đơn 0 đồng ({@code FREE}) không tạo {@code payment}, cấp quyền ở transaction riêng;
 * khi cấp quyền lỗi vẫn trả {@code next = CHECKING} (không 500) để worker/hệ thống cấp bù sau.
 */
class FreeOrderIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;

    @MockitoSpyBean
    private EntitlementGrantService entitlementGrantService;

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
    void freeOrderGrantsImmediatelyWhenFulfillmentSucceedsAndReturnsCheckingWithout500WhenGrantFails()
            throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 100_000L, 4);
        var fullVoucher = fixtures.createVoucher(
                "FREE100", DiscountType.PERCENT, 100L, null,
                10, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        AtomicBoolean failGrant = new AtomicBoolean(false);
        doAnswer(inv -> {
            if (failGrant.get()) {
                throw new IllegalStateException("Transient entitlement storage outage");
            }
            return inv.callRealMethod();
        }).when(entitlementGrantService).grantForOrder(any());

        // 1. Thành công -> next = FULFILLED, 0 dòng payment.
        String okRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer1))
                        .header("Idempotency-Key", "free-ok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", fullVoucher.getCode(),
                                "expectedAccountId", buyer1.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("FULFILLED"))
                .andExpect(jsonPath("$.paymentUrl").doesNotExist())
                .andExpect(jsonPath("$.order.paymentMethod").value("FREE"))
                .andExpect(jsonPath("$.order.state").value("FULFILLED"))
                .andReturn().getResponse().getContentAsString();

        UUID okOrderId = UUID.fromString(mapper.readTree(okRaw).path("order").path("id").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, okOrderId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer1.id(), product.examId())).isEqualTo(4);

        // 2. Lỗi khi cấp quyền -> vẫn tạo đơn PAID, trả 201 với next = CHECKING, KHÔNG trả 500 và KHÔNG lộ trường nội bộ!
        failGrant.set(true);
        String freeCheckingKey = "free-chk-" + UUID.randomUUID();
        String freeCheckingBody = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "voucherCode", fullVoucher.getCode(),
                "expectedAccountId", buyer2.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 0L));
        String checkingRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer2))
                        .header("Idempotency-Key", freeCheckingKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeCheckingBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("CHECKING"))
                .andExpect(jsonPath("$.order.state").value("PAID"))
                .andExpect(jsonPath("$.order.fulfillmentError").doesNotExist())
                .andExpect(jsonPath("$.order.fulfillmentTries").doesNotExist())
                .andExpect(jsonPath("$.order.needsReview").doesNotExist())
                .andExpect(jsonPath("$.order.reviewReason").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(checkingRaw).doesNotContain("Transient entitlement storage outage");
        UUID checkingOrderId = UUID.fromString(mapper.readTree(checkingRaw).path("order").path("id").asText());
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_tries FROM commerce.purchase_order WHERE id = ?", Integer.class, checkingOrderId))
                .isEqualTo(1);

        // R1-28: replay khi chưa khôi phục không gọi lại fulfill (fulfillment_tries vẫn = 1).
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer2))
                        .header("Idempotency-Key", freeCheckingKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeCheckingBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("CHECKING"))
                .andExpect(jsonPath("$.order.state").value("PAID"));
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_tries FROM commerce.purchase_order WHERE id = ?", Integer.class, checkingOrderId))
                .isEqualTo(1);

        failGrant.set(false);
        assertThat(fulfillmentService.fulfill(checkingOrderId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, checkingOrderId))
                .isEqualTo("FULFILLED");

        // R1-23: sau khi phục hồi, replay cùng Idempotency-Key tính lại next từ trạng thái mới -> FULFILLED!
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer2))
                        .header("Idempotency-Key", freeCheckingKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeCheckingBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("FULFILLED"))
                .andExpect(jsonPath("$.order.state").value("FULFILLED"));
    }
}
