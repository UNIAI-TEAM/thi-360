package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
import vn.unicom.thi360.commerce.entitlement.access.application.ExamAccessService;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PurchaseHistory;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-41 & AC-42: kiểm chứng cổng thật {@link PurchaseHistory} (chỉ đơn {@code FULFILLED} mới mất quyền
 * "tài khoản mới" E8) và {@link PendingOrderLookup} (trả đơn {@code PAID} chưa cấp quyền cho {@code CHECKING}).
 */
class OrderPortsIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PurchaseHistory purchaseHistory;
    @Autowired private PendingOrderLookup pendingOrderLookup;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private ExamAccessService examAccessService;

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
    void purchaseHistoryRecognizesOnlyFulfilledOrdersForFirstOrderEligibilityE8() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var firstOrderVoucher = fixtures.createVoucher(
                "E8FIRST", DiscountType.AMOUNT, 50_000L, null,
                10, 1, false, PromotionScope.ALL, PromotionAudienceType.NEW_ACCOUNT);

        // Chưa có đơn hoặc chỉ có PENDING_PAYMENT / CANCELLED / PAID chưa FULFILLED -> hasFulfilledOrder = false.
        assertThat(purchaseHistory.hasFulfilledOrder(buyer.id())).isFalse();
        fixtures.insertStubOrder(
                buyer, product, OrderState.CANCELLED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(600), null);
        var paidOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now());
        assertThat(purchaseHistory.hasFulfilledOrder(buyer.id())).isFalse();

        // Báo giá FIRST_ORDER vẫn hợp lệ khi chưa có đơn FULFILLED.
        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(fixtures.asLearner(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", firstOrderVoucher.getCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPrice").value(150_000L));

        // Cấp quyền xong -> đơn thành FULFILLED -> hasFulfilledOrder = true -> voucher FIRST_ORDER báo 422 VOUCHER_NOT_ELIGIBLE!
        assertThat(fulfillmentService.fulfill(paidOrder.id())).isTrue();
        assertThat(purchaseHistory.hasFulfilledOrder(buyer.id())).isTrue();

        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(fixtures.asLearner(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", firstOrderVoucher.getCode()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_ELIGIBLE"));
    }

    @Test
    void pendingOrderLookupSurfacesPaidUnfulfilledOrdersAndDrivesCheckingAccessState() {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(600), null);
        assertThat(pendingOrderLookup.findPaidUnfulfilled(buyer.id())).isEmpty();

        var paidOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now());

        var found = pendingOrderLookup.findPaidUnfulfilled(buyer.id());
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().orderCode()).isEqualTo(paidOrder.code());
        assertThat(found.getFirst().examId()).isEqualTo(product.examId());

        // ExamAccessService chuyển trạng thái bài thi thành CHECKING.
        var access = examAccessService.query(buyer.id(), List.of(product.code()));
        assertThat(access.getFirst().state()).isEqualTo(ExamAccessState.CHECKING);
        assertThat(access.getFirst().pendingOrderCode()).isEqualTo(paidOrder.code());
    }
}
