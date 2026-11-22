package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
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
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Kiểm thử danh sách người mua theo bài thi {@code GET /api/v1/admin/exams/{id}/buyers} (TM 9.3, AC-6).
 */
class BuyersIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private EntitlementPort entitlementPort;

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
    void groupsByDistinctBuyerUserExcludesUnpaidOrdersAndIncludesEntitlementCounters() throws Exception {
        var alice = fixtures.createLearner(true, "vi");
        var bob = fixtures.createLearner(true, "vi");
        var pendingOnlyUser = fixtures.createLearner(true, "vi");
        var refundedOnlyUser = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var otherExam = fixtures.insertOnSaleProduct("QDA", 150_000L, 1);

        Instant t0 = Instant.parse("2026-11-20T09:00:00Z");

        // Alice mua 2 đơn cho cùng bài thi (1 FULFILLED + 1 PAID).
        var aliceOrder1 = fixtures.insertStubOrder(
                alice, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, t0);
        assertThat(fulfillmentService.fulfill(aliceOrder1.id())).isTrue();

        var aliceOrder2 = fixtures.insertStubOrder(
                alice, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, t0.plusSeconds(1200));

        // Cho Alice dùng 1 lượt từ entitlement để kiểm tra attemptsUsed / attemptsRemaining.
        UUID aliceEntId = jdbc.queryForObject(
                "SELECT id FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                UUID.class, alice.id(), exam.examId());
        UUID attemptId = UUID.randomUUID();
        entitlementPort.reserve(alice.id(), aliceEntId, attemptId);
        entitlementPort.confirmUse(attemptId);

        // Bob mua 1 đơn FULFILLED cho bài thi này (paidAt cũ hơn Alice).
        var bobOrder = fixtures.insertStubOrder(
                bob, exam, OrderState.PAID, PaymentMethod.FREE, 0L, null, t0.minusSeconds(600));
        assertThat(fulfillmentService.fulfill(bobOrder.id())).isTrue();

        // Các đơn KHÔNG đủ điều kiện không được tính vào người mua:
        fixtures.insertStubOrder(
                pendingOnlyUser, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, t0.plusSeconds(900), null);
        fixtures.insertStubOrder(
                refundedOnlyUser, exam, OrderState.REFUNDED, PaymentMethod.VNPAY, 200_000L, null, t0);
        var bobOtherExam = fixtures.insertStubOrder(
                bob, otherExam, OrderState.PAID, PaymentMethod.VNPAY, 150_000L, null, t0);
        assertThat(fulfillmentService.fulfill(bobOtherExam.id())).isTrue();

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/admin/exams/" + exam.examId() + "/buyers")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalBuyers").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                // Alice có lastPaidAt mới hơn nên đứng đầu.
                .andExpect(jsonPath("$.items[0].userId").value(alice.id().toString()))
                .andExpect(jsonPath("$.items[0].username").value(alice.username()))
                .andExpect(jsonPath("$.items[0].fullName").value(alice.fullName()))
                .andExpect(jsonPath("$.items[0].purchaseCount").value(2))
                .andExpect(jsonPath("$.items[0].orders.length()").value(2))
                .andExpect(jsonPath("$.items[0].orders[0].orderCode").value(aliceOrder2.code()))
                .andExpect(jsonPath("$.items[0].orders[1].orderCode").value(aliceOrder1.code()))
                .andExpect(jsonPath("$.items[0].lastPaidAt").value(t0.plusSeconds(1200).toString()))
                .andExpect(jsonPath("$.items[0].attemptsTotal").value(2))
                .andExpect(jsonPath("$.items[0].attemptsUsed").value(1))
                .andExpect(jsonPath("$.items[0].attemptsReserved").value(0))
                .andExpect(jsonPath("$.items[0].attemptsRevoked").value(0))
                .andExpect(jsonPath("$.items[0].attemptsRemaining").value(1))
                // Bob đứng thứ 2.
                .andExpect(jsonPath("$.items[1].userId").value(bob.id().toString()))
                .andExpect(jsonPath("$.items[1].purchaseCount").value(1))
                .andExpect(jsonPath("$.items[1].orders.length()").value(1))
                .andExpect(jsonPath("$.items[1].orders[0].orderCode").value(bobOrder.code()))
                .andExpect(jsonPath("$.items[1].lastPaidAt").value(t0.minusSeconds(600).toString()))
                .andExpect(jsonPath("$.items[1].attemptsTotal").value(2))
                .andExpect(jsonPath("$.items[1].attemptsUsed").value(0))
                .andExpect(jsonPath("$.items[1].attemptsRemaining").value(2));

        // Trang vượt quá giới hạn (page=5) vẫn giữ totalItems, totalPages, totalBuyers.
        mockMvc.perform(get("/api/v1/admin/exams/" + exam.examId() + "/buyers")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("page", "5")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalBuyers").value(2));

        // Kiểm tra sort theo username,asc và userId,desc hoạt động bình thường.
        mockMvc.perform(get("/api/v1/admin/exams/" + exam.examId() + "/buyers")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("sort", "username,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/exams/" + exam.examId() + "/buyers")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("sort", "userId,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }
}
