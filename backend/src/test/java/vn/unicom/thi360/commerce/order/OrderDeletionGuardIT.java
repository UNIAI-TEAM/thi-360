package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.application.OrderDeletionGuard;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * AC-40: {@link OrderDeletionGuard} chặn xóa tài khoản đã có đơn hàng qua mã {@code USER_HAS_ORDERS}
 * ở cả kiểm tra trực tiếp SPI và endpoint quản trị {@code /api/v1/admin/users/{id}/deletion-check}.
 */
class OrderDeletionGuardIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private OrderDeletionGuard deletionGuard;
    @Autowired private EntitlementGrantService grantService;

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
    void blocksAccountDeletionWhenUserHasAnyOrderAndAllowsWhenOrderless() throws Exception {
        var orderless = fixtures.createLearner();
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        fixtures.insertStubOrder(
                buyer, product, OrderState.CANCELLED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);

        assertThat(deletionGuard.check(orderless.id())).isEmpty();
        var blockers = deletionGuard.check(buyer.id());
        assertThat(blockers).hasSize(1);
        assertThat(blockers.getFirst().source()).isEqualTo("COMMERCE");
        assertThat(blockers.getFirst().code()).isEqualTo("USER_HAS_ORDERS");

        var admin = fixtures.asManager(UUID.randomUUID(), "user.admin", Permissions.USER_MODIFY);
        mockMvc.perform(get("/api/v1/admin/users/" + orderless.id() + "/deletion-check").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers[?(@.code=='USER_HAS_ORDERS')]").isEmpty());

        mockMvc.perform(get("/api/v1/admin/users/" + buyer.id() + "/deletion-check").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers[*].code", Matchers.hasItem("USER_HAS_ORDERS")));

        mockMvc.perform(delete("/api/v1/admin/users/" + buyer.id())
                        .with(admin)
                        .param("reason", "test blocked delete"))
                .andExpect(status().isConflict());

        // Kiểm tra thêm đơn PENDING_PAYMENT và FULFILLED đều chặn với COMMERCE / USER_HAS_ORDERS.
        for (OrderState state : new OrderState[] {OrderState.PENDING_PAYMENT, OrderState.FULFILLED}) {
            var accountWithState = fixtures.createLearner();
            fixtures.insertStubOrder(
                    accountWithState, product, state, PaymentMethod.VNPAY,
                    200_000L, Instant.now().plusSeconds(900),
                    state == OrderState.FULFILLED ? Instant.now() : null);
            var stateBlockers = deletionGuard.check(accountWithState.id());
            assertThat(stateBlockers).hasSize(1);
            assertThat(stateBlockers.getFirst().source()).isEqualTo("COMMERCE");
            assertThat(stateBlockers.getFirst().code()).isEqualTo("USER_HAS_ORDERS");
        }

        // Tài khoản có CẢ quyền dùng lẫn đơn hàng trả về ĐỦ CẢ HAI blocker (USER_HAS_TRANSACTIONS và USER_HAS_ORDERS).
        var bothAccount = fixtures.createLearner();
        var bothOrder = fixtures.insertStubOrder(
                bothAccount, product, OrderState.FULFILLED, PaymentMethod.VNPAY,
                200_000L, null, Instant.now());
        fixtures.grantOrder(grantService, bothOrder.id(), bothOrder.code(), bothAccount.id(), product.examId(), 2);

        mockMvc.perform(get("/api/v1/admin/users/" + bothAccount.id() + "/deletion-check").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(false))
                .andExpect(jsonPath("$.blockers.length()").value(2))
                .andExpect(jsonPath("$.blockers[*].code",
                        Matchers.containsInAnyOrder("USER_HAS_TRANSACTIONS", "USER_HAS_ORDERS")));
    }
}
