package vn.unicom.thi360.commerce.admin.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse;
import vn.unicom.thi360.commerce.admin.api.RecoverOrderRequest;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.fulfillment.application.RefundProgress;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Tiếp tục cấp quyền thủ công cho đơn hàng đã thu tiền nhưng bị lỗi cấp quyền (TM 9.5, AC-26..AC-28).
 */
@Service
public class OrderRecoverService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundProgress refundProgress;
    private final FulfillmentService fulfillmentService;
    private final AdminOrderQueryService queryService;
    private final IdempotentCommand idempotentCommand;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;

    public OrderRecoverService(
            PurchaseOrderRepository purchaseOrderRepository,
            PaymentRepository paymentRepository,
            RefundProgress refundProgress,
            FulfillmentService fulfillmentService,
            AdminOrderQueryService queryService,
            IdempotentCommand idempotentCommand,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.refundProgress = Objects.requireNonNull(refundProgress);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        this.queryService = Objects.requireNonNull(queryService);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
    }

    public AdminOrderDetailResponse recoverOrder(
            String orderCode, String idempotencyKey, RecoverOrderRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String normalizedCode = normalizeCode(orderCode);
        String requiredKey = idempotentCommand.requireKey(idempotencyKey);
        String trimmedReason = request.reason().trim();

        UUID[] skippedOrderId = new UUID[1];
        RecoveryOutcome outcome;
        try {
            outcome = idempotentCommand.execute(
                    requiredKey,
                    "order.recover",
                    normalizedCode,
                    request,
                    RecoveryOutcome.class,
                    () -> recoverInTx(normalizedCode, actor, trimmedReason, skippedOrderId));
        } catch (ConflictException ex) {
            if (skippedOrderId[0] != null) {
                fulfillmentService.fulfill(skippedOrderId[0]);
            }
            throw ex;
        }

        return queryService.getOrderDetail(outcome.orderCode());
    }

    private RecoveryOutcome recoverInTx(
            String normalizedCode, CurrentUser actor, String reason, UUID[] skippedOrderId) {
        PurchaseOrder order = purchaseOrderRepository.lockByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));

        if (order.getState() != OrderState.PAID) {
            throw new ConflictException("ORDER_RECOVERY_NOT_ALLOWED");
        }

        if (order.getPaymentMethod() != PaymentMethod.FREE) {
            List<Payment> payments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(order.getId());
            boolean hasCountedPayment = payments.stream()
                    .anyMatch(p -> p.getState() == PaymentState.SUCCEEDED && !p.isDuplicate());
            if (!hasCountedPayment) {
                throw new ConflictException("ORDER_RECOVERY_NOT_ALLOWED");
            }
            if (refundProgress.hasNonRejectedRefund(order.getId())
                    || refundProgress.isFullyRefunded(order.getId())) {
                throw new ConflictException("ORDER_RECOVERY_NOT_ALLOWED");
            }
        }

        boolean fulfilled = fulfillmentService.fulfillLockedPaidOrder(order, actor.username(), reason);
        if (!fulfilled) {
            skippedOrderId[0] = order.getId();
            throw new ConflictException("ORDER_RECOVERY_NOT_ALLOWED");
        }

        auditRecorder.record(AuditEvent.builder("commerce", "ORDER_FULFILLED_MANUALLY")
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .reason(reason)
                .after(Map.of(
                        "orderCode", order.getCode(),
                        "state", order.getState().name(),
                        "paymentMethod", order.getPaymentMethod().name()))
                .build());

        return new RecoveryOutcome(order.getId(), order.getCode());
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private record RecoveryOutcome(UUID orderId, String orderCode) {
    }
}
