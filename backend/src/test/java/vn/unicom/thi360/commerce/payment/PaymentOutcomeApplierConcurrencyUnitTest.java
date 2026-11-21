package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.ApplyResult;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;

/**
 * Unit test cho {@code R1-8/9}: chuỗi thay thế mở rộng song song {@code O1 -> O2 -> O3} sau lần đọc trước khi khóa.
 */
class PaymentOutcomeApplierConcurrencyUnitTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T03:00:00Z");

    @Test
    void detectsPaidDownstreamSuccessorAppendedBetweenPreLockReadAndLockAcquisition() {
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        UUID o1Id = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID o2Id = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID o3Id = UUID.fromString("00000000-0000-7000-8000-000000000003");

        PurchaseOrder o1 = PurchaseOrder.createPendingExamOrder(
                o1Id, "DH261121ORD001", userId, "u", "User",
                examId, 1, List.of(), "HSA01", "Exam", 200_000L,
                null, 0L, null, null, 0L, DiscountApplied.NONE,
                200_000L, 1, Map.of(), "FULL_SOLUTION", FIXED_NOW.plusSeconds(900), FIXED_NOW);
        o1.markReplaced(o2Id, FIXED_NOW);

        // Giả lập O2 ban đầu không có người kế nhiệm khi pre-lock query chạy, nhưng khi khóa xong O2 đã trỏ sang O3.
        PurchaseOrder o2 = PurchaseOrder.createPendingExamOrder(
                o2Id, "DH261121ORD002", userId, "u", "User",
                examId, 1, List.of(), "HSA01", "Exam", 200_000L,
                null, 0L, null, null, 0L, DiscountApplied.NONE,
                200_000L, 1, Map.of(), "FULL_SOLUTION", FIXED_NOW.plusSeconds(900), FIXED_NOW);
        o2.markReplaced(o3Id, FIXED_NOW);

        UUID p1Id = UUID.randomUUID();
        Payment p1 = new Payment(
                p1Id, o1Id, 1, "DH261121ORD00101", "20261121100000", "20261121101500", 200_000L, FIXED_NOW);

        PurchaseOrderRepository orderRepo = mock(PurchaseOrderRepository.class);
        when(orderRepo.findReplacedByOrderIdById(o1Id)).thenReturn(Optional.of(o2Id));
        when(orderRepo.findAllPredecessorOrderIds(o1Id)).thenReturn(List.of());
        when(orderRepo.findAllReplacementChainOrderIds(o2Id)).thenReturn(List.of(o2Id));
        when(orderRepo.lockAllByIdInOrderByIdAsc(List.of(o1Id, o2Id))).thenReturn(List.of(o1, o2));
        when(orderRepo.existsPaidInReplacementChainStartingAt(o3Id)).thenReturn(true);

        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.lockAllByOrderIdOrderByAttemptSeqAsc(o1Id)).thenReturn(List.of(p1));

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        PaymentOutcomeApplier applier = new PaymentOutcomeApplier(
                orderRepo,
                mock(OrderHistoryRepository.class),
                paymentRepo,
                mock(PaymentEventRepository.class),
                mock(PromotionRedemptionService.class),
                mock(AuditRecorder.class),
                mock(EntityManager.class),
                txManager,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        ApplyResult result = applier.applyIpnOutcome(
                p1Id,
                o1Id,
                Map.of(
                        "vnp_ResponseCode", "00",
                        "vnp_TransactionStatus", "00",
                        "vnp_TransactionNo", "99990001",
                        "vnp_BankCode", "NCB",
                        "vnp_CardType", "ATM",
                        "vnp_PayDate", "20261121100200"),
                Map.of("vnp_TxnRef", "DH261121ORD00101"));

        assertThat(result.rspCode()).isEqualTo("00");
        assertThat(o1.isNeedsReview()).isTrue();
        assertThat(o1.getReviewReason()).isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        verify(orderRepo).existsPaidInReplacementChainStartingAt(o3Id);
    }
}
