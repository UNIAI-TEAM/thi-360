package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamLearnerGate;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.application.OrderCodeGenerator;
import vn.unicom.thi360.commerce.order.application.OrderService;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderItemRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.order.infrastructure.StorefrontProductLockRepository;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.MailContact;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.LoginChannel;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Unit test cho giới hạn 99 lần thanh toán ({@code R1-29/30}) và ánh xạ locale VNPAY 2.1.0 ({@code R1-19}).
 */
class OrderCodeGeneratorAndLocaleTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T03:00:00Z");

    @Test
    void txnRefRejectsAttemptSeqAbove99() {
        PurchaseOrderRepository repo = mock(PurchaseOrderRepository.class);
        OrderCodeGenerator generator = new OrderCodeGenerator(
                repo, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        assertThat(generator.txnRef("DH261121ABCDEF", 99)).isEqualTo("DH261121ABCDEF99");
        assertThatThrownBy(() -> generator.txnRef("DH261121ABCDEF", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.txnRef("DH261121ABCDEF", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payOrderRejectsAttemptSeqAbove99AndMapsVietnameseLocaleToVnAndEnglishToEn() {
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        String orderCode = "DH261121UNIT01";
        PurchaseOrder order = PurchaseOrder.createPendingExamOrder(
                UUID.randomUUID(), orderCode, userId, "hocvien", "Hoc Vien",
                examId, 1, List.of(), "HSA01", "Exam HSA01", 200_000L,
                null, 0L, null, null, 0L, DiscountApplied.NONE,
                200_000L, 2, Map.of(), "FULL_SOLUTION", FIXED_NOW.plusSeconds(900), FIXED_NOW);

        ExamLearnerGate learnerGate = mock(ExamLearnerGate.class);
        when(learnerGate.requireLearner()).thenReturn(userId);
        StorefrontProductLockRepository lockRepo = mock(StorefrontProductLockRepository.class);
        when(lockRepo.findByNormalizedCode("hsa01")).thenReturn(Optional.of(new StoredProduct(
                UUID.randomUUID(), examId, UUID.randomUUID(), "HSA01", "Exam HSA01",
                SaleState.ON_SALE, 1, 200_000L, 2, "{}", "{}")));

        PurchaseOrderRepository orderRepo = mock(PurchaseOrderRepository.class);
        when(orderRepo.lockByCode(orderCode)).thenReturn(Optional.of(order));
        when(orderRepo.findById(any())).thenReturn(Optional.of(order));

        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findMaxAttemptSeqByOrderId(order.getId())).thenReturn(99);

        VnpayGateway vnpayGateway = mock(VnpayGateway.class);
        when(vnpayGateway.pay(any())).thenReturn("/mock-vnpay?ok=1");
        UserDirectory userDirectory = mock(UserDirectory.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        OrderService service = new OrderService(
                learnerGate,
                lockRepo,
                orderRepo,
                mock(PurchaseOrderItemRepository.class),
                mock(OrderHistoryRepository.class),
                paymentRepo,
                mock(PriceCalculator.class),
                mock(PromotionRedemptionService.class),
                mock(FulfillmentService.class),
                vnpayGateway,
                new OrderCodeGenerator(orderRepo, Clock.fixed(FIXED_NOW, ZoneOffset.UTC)),
                userDirectory,
                mock(IdempotentCommand.class),
                mock(AuditRecorder.class),
                AppMessages.defaultInstance(),
                new ObjectMapper(),
                txManager,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                "http://localhost:3000");

        // R1-29/30: nextSeq = 100 -> BusinessRuleException("PAYMENT_ATTEMPTS_EXCEEDED").
        assertThatThrownBy(() -> service.payOrder(orderCode, null, "127.0.0.1", LoginChannel.WEB))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("PAYMENT_ATTEMPTS_EXCEEDED");
        assertThatThrownBy(() -> service.payOrder(orderCode, null, "127.0.0.1", LoginChannel.MOBILE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("PAYMENT_ATTEMPTS_EXCEEDED");

        // R1-19: "vi" / null -> PayRequest.locale() == "vn", "EN" -> "en".
        when(paymentRepo.findMaxAttemptSeqByOrderId(order.getId())).thenReturn(1);
        when(paymentRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDirectory.findMailContact(userId)).thenReturn(Optional.of(
                new MailContact(userId, "Hoc Vien", "hv@test.local", true, "vi")));

        service.payOrder(orderCode, null, "127.0.0.1", LoginChannel.WEB);
        ArgumentCaptor<PayRequest> reqCaptor = ArgumentCaptor.forClass(PayRequest.class);
        verify(vnpayGateway).pay(reqCaptor.capture());
        assertThat(reqCaptor.getValue().locale()).isEqualTo("vn");
        assertThat(reqCaptor.getValue().returnUrl()).isEqualTo("http://localhost:3000/checkout/result");

        when(userDirectory.findMailContact(userId)).thenReturn(Optional.of(
                new MailContact(userId, "Hoc Vien", "hv@test.local", true, null)));
        service.payOrder(orderCode, null, "127.0.0.1", LoginChannel.MOBILE);
        verify(vnpayGateway, org.mockito.Mockito.times(2)).pay(reqCaptor.capture());
        assertThat(reqCaptor.getValue().locale()).isEqualTo("vn");
        assertThat(reqCaptor.getValue().returnUrl()).isEqualTo("http://localhost:3000/checkout/result/app");

        when(userDirectory.findMailContact(userId)).thenReturn(Optional.of(
                new MailContact(userId, "Hoc Vien", "hv@test.local", true, "EN")));
        service.payOrder(orderCode, null, "127.0.0.1", LoginChannel.WEB);
        verify(vnpayGateway, org.mockito.Mockito.times(3)).pay(reqCaptor.capture());
        assertThat(reqCaptor.getValue().locale()).isEqualTo("en");
        assertThat(reqCaptor.getValue().returnUrl()).isEqualTo("http://localhost:3000/checkout/result");
    }
}
