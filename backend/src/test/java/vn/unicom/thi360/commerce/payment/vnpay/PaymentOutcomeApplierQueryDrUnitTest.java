package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.QueryDrMetadata;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.QueryDrOutcomeAction;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;

/**
 * Kiểm thử đơn vị chốt chặn chữ ký fail-closed của {@link PaymentOutcomeApplier#applyQueryDrOutcome} ({@code R1-19}).
 */
class PaymentOutcomeApplierQueryDrUnitTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T03:00:00Z");

    @Test
    void rejectsMutatingActionsWhenSignatureInvalidButAllowsKeepUnchanged() {
        PurchaseOrderRepository orderRepo = mock(PurchaseOrderRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        PaymentEventRepository eventRepo = mock(PaymentEventRepository.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        PaymentOutcomeApplier applier = new PaymentOutcomeApplier(
                orderRepo,
                mock(OrderHistoryRepository.class),
                paymentRepo,
                eventRepo,
                mock(PromotionRedemptionService.class),
                mock(AuditRecorder.class),
                mock(EntityManager.class),
                txManager,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        QueryDrMetadata meta = new QueryDrMetadata(null, null, null, null, null, null);

        for (QueryDrOutcomeAction mutating : List.of(
                QueryDrOutcomeAction.SUCCEEDED,
                QueryDrOutcomeAction.FAILED,
                QueryDrOutcomeAction.UNKNOWN,
                QueryDrOutcomeAction.UNKNOWN_NEEDS_REVIEW)) {
            assertThatThrownBy(() -> applier.applyQueryDrOutcome(
                    paymentId, orderId, mutating, "TAG_" + mutating.name(), "REASON", meta, Map.of(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsigned querydr outcome");
        }
        verifyNoInteractions(orderRepo, paymentRepo, eventRepo);

        PurchaseOrder order = PurchaseOrder.createPendingExamOrder(
                orderId, "DH261121SIG01", UUID.randomUUID(), "u", "User",
                UUID.randomUUID(), 1, List.of(), "HSA01", "Exam", 200_000L,
                null, 0L, null, null, 0L, DiscountApplied.NONE,
                200_000L, 1, Map.of(), "FULL_SOLUTION", FIXED_NOW.plusSeconds(900), FIXED_NOW);
        Payment payment = new Payment(
                paymentId, orderId, 1, "DH261121SIG0101", "20261121100000", "20261121101500", 200_000L, FIXED_NOW);

        when(orderRepo.findReplacedByOrderIdById(orderId)).thenReturn(Optional.empty());
        when(orderRepo.findAllPredecessorOrderIds(orderId)).thenReturn(List.of());
        when(orderRepo.lockAllByIdInOrderByIdAsc(List.of(orderId))).thenReturn(List.of(order));
        when(paymentRepo.lockAllByOrderIdOrderByAttemptSeqAsc(orderId)).thenReturn(List.of(payment));

        ApplyResult kept = applier.applyQueryDrOutcome(
                paymentId, orderId, QueryDrOutcomeAction.KEEP_UNCHANGED,
                "QUERYDR_INVALID_SIGNATURE", null, meta, Map.of("vnp_ResponseCode", "00"), false);

        assertThat(kept.rspCode()).isEqualTo("00");
        assertThat(payment.getState()).isEqualTo(PaymentState.CREATED);
        verify(eventRepo).save(any(PaymentEvent.class));
    }
}
