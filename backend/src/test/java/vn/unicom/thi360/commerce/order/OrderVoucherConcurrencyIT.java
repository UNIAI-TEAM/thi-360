package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-12 đồng thời: hai học viên bấm tạo đơn cùng lúc với lượt voucher cuối cùng -> chỉ một đơn thành công,
 * đơn còn lại nhận 422 {@code VOUCHER_EXHAUSTED} và không sinh đơn rác.
 */
class OrderVoucherConcurrencyIT extends AbstractIntegrationTest {

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
    void onlyOneLearnerClaimsLastVoucherSlotUnderConcurrentOrderCreation() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var lastSlotVoucher = fixtures.createVoucher(
                "RACE1", DiscountType.AMOUNT, 50_000L, null,
                1, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task1 = () -> submitOrder(buyer1, product.code(), lastSlotVoucher.getCode(), ready, start);
            Callable<Integer> task2 = () -> submitOrder(buyer2, product.code(), lastSlotVoucher.getCode(), ready, start);
            Future<Integer> f1 = pool.submit(task1);
            Future<Integer> f2 = pool.submit(task2);
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(f1.get(), f2.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE promotion_id = ? AND state = 'RESERVED'",
                Integer.class, lastSlotVoucher.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id IN (?, ?)",
                Integer.class, buyer1.id(), buyer2.id())).isEqualTo(1);
    }

    private int submitOrder(
            CommerceTestFixtures.LearnerAccount buyer,
            String productCode,
            String voucherCode,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", productCode,
                "voucherCode", voucherCode,
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 150_000L));
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "race-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }
}
