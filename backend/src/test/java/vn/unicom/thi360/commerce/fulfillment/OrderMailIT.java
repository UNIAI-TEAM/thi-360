package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-43: chỉ xếp hàng thư {@code ORDER_FULFILLED} cho tài khoản đã xác minh email ({@code emailVerified=true}),
 * đúng ngôn ngữ {@code vi}/{@code en}; tài khoản chưa xác minh vẫn cấp quyền nhưng không gửi thư.
 */
class OrderMailIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;

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
    void queuesLocalizedOrderFulfilledMailOnlyForEmailVerifiedLearners() {
        var viVerified = fixtures.createLearner(true, "vi");
        var enVerified = fixtures.createLearner(true, "en");
        var unverified = fixtures.createLearner(false, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        var viOrder = fixtures.insertStubOrder(
                viVerified, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var enOrder = fixtures.insertStubOrder(
                enVerified, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var unverifiedOrder = fixtures.insertStubOrder(
                unverified, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());

        assertThat(fulfillmentService.fulfill(viOrder.id())).isTrue();
        assertThat(fulfillmentService.fulfill(enOrder.id())).isTrue();
        assertThat(fulfillmentService.fulfill(unverifiedOrder.id())).isTrue();

        Map<String, Object> viMail = jdbc.queryForMap(
                "SELECT kind, to_email, locale, subject, text, html FROM shared.email_message WHERE user_id = ?",
                viVerified.id());
        assertThat(viMail.get("kind")).isEqualTo("ORDER_FULFILLED");
        assertThat(viMail.get("locale")).isEqualTo("vi");
        assertThat(viMail.get("to_email")).isEqualTo(viVerified.email());
        assertThat(viMail.get("text").toString()).contains(viOrder.code()).contains(product.name());

        Map<String, Object> enMail = jdbc.queryForMap(
                "SELECT kind, to_email, locale, subject, text, html FROM shared.email_message WHERE user_id = ?",
                enVerified.id());
        assertThat(enMail.get("kind")).isEqualTo("ORDER_FULFILLED");
        assertThat(enMail.get("locale")).isEqualTo("en");
        assertThat(enMail.get("text").toString()).contains(enOrder.code()).contains(product.name());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.email_message WHERE user_id = ?",
                Integer.class, unverified.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?",
                String.class, unverifiedOrder.id())).isEqualTo("FULFILLED");
    }
}
