package vn.unicom.thi360.commerce.order.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryRepository.ExpiredOrderCandidate;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryRepository.LockedOrder;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryRepository.LockedPayment;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.ApplyResult;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrProperties;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrService;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;

/**
 * Kiểm thử đơn vị {@link OrderExpiryService}: lỗi ở một lần thanh toán không làm bỏ qua các lần
 * thanh toán còn lại trên cùng đơn ({@code R1-33}).
 */
class OrderExpiryServiceUnitTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T08:00:00Z");

    @Test
    void continuesQueryingRemainingClaimedPaymentsWhenFirstThrowsRuntimeException() {
        UUID orderId = UUID.randomUUID();
        OrderExpiryRepository expiryRepo = mock(OrderExpiryRepository.class);
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        QueryDrService queryDrService = mock(QueryDrService.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        OrderExpiryProperties expiryProps = new OrderExpiryProperties(Duration.ofMinutes(1), 20);
        QueryDrProperties queryDrProps = new QueryDrProperties(null, null, null, null, null, null, null, null);

        when(expiryRepo.findExpiredPendingOrderIds(any(), eq(queryDrProps.maxAge()), eq(null), eq(20)))
                .thenReturn(List.of(new ExpiredOrderCandidate(orderId, FIXED_NOW.minusSeconds(60))));

        ClaimedPayment p1 = new ClaimedPayment(
                UUID.randomUUID(), orderId, "ORD1", "ORD101",
                "20261121140000", "20261121141500", 100_000L, null, PaymentState.CREATED);
        ClaimedPayment p2 = new ClaimedPayment(
                UUID.randomUUID(), orderId, "ORD1", "ORD102",
                "20261121140500", "20261121142000", 100_000L, null, PaymentState.CREATED);

        when(claimRepo.claimOpenForOrder(eq(orderId), any(), eq(queryDrProps.maxAge()), eq(queryDrProps.cooldown())))
                .thenReturn(List.of(p1, p2));

        doThrow(new IllegalStateException("transient DB error")).when(queryDrService).executeClaimedQuery(p1);
        doReturn(new ApplyResult("00", "Marked failed", null)).when(queryDrService).executeClaimedQuery(p2);

        when(expiryRepo.lockOrder(orderId)).thenReturn(new LockedOrder(
                orderId, "ORD1", OrderState.PENDING_PAYMENT, FIXED_NOW.minusSeconds(60), null, null));
        when(expiryRepo.lockPayments(orderId)).thenReturn(List.of(
                new LockedPayment(p1.paymentId(), PaymentState.CREATED, null),
                new LockedPayment(p2.paymentId(), PaymentState.FAILED, null)));

        OrderExpiryService service = new OrderExpiryService(
                expiryRepo,
                expiryProps,
                claimRepo,
                queryDrProps,
                queryDrService,
                mock(OrderHistoryRepository.class),
                mock(PromotionRedemptionService.class),
                mock(AuditRecorder.class),
                txManager,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        int expired = service.expirePendingOrders();

        assertThat(expired).isZero();
        verify(queryDrService).executeClaimedQuery(p1);
        verify(queryDrService).executeClaimedQuery(p2);
        verify(expiryRepo).lockOrder(orderId);
    }
}
