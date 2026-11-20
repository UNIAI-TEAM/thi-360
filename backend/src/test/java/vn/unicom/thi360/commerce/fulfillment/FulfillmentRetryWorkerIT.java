package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-39: {@link FulfillmentRetryWorker} thử cấp lại cho các đơn {@code PAID} trong 30 phút từ lúc {@code paid_at},
 * bỏ qua đơn đã quá hạn 30 phút.
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class FulfillmentRetryWorkerIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentRetryWorker retryWorker;

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
    void retriesPaidOrdersWithin30MinuteWindowAndSkipsOlderPaidOrders() {
        var buyer = fixtures.createLearner();
        var recentProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var staleProduct = fixtures.insertOnSaleProduct("QDA", 200_000L, 3);

        var recentPaid = fixtures.insertStubOrder(
                buyer, recentProduct, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now().minus(5, ChronoUnit.MINUTES));
        var stalePaid = fixtures.insertStubOrder(
                buyer, staleProduct, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now().minus(35, ChronoUnit.MINUTES));

        retryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, recentPaid.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, stalePaid.id()))
                .isEqualTo("PAID");
    }
}
