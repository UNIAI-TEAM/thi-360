package vn.unicom.thi360.commerce.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Kiểm chứng chữ ký URL quay lại VNPAY (TM 8.4, AC-22). Luôn ghi {@code payment_event} nguồn {@code RETURN},
 * không bao giờ đổi trạng thái {@code purchase_order} hay {@code payment}, và không lộ thông tin người mua.
 */
@Service
public class VnpayReturnService {

    public record VnpayReturnView(String orderCode, String paymentState) {
    }

    private final VnpayGateway vnpayGateway;
    private final PaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AppMessages messages;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public VnpayReturnService(
            VnpayGateway vnpayGateway,
            PaymentRepository paymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PaymentEventRepository paymentEventRepository,
            AppMessages messages,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.paymentEventRepository = Objects.requireNonNull(paymentEventRepository);
        this.messages = Objects.requireNonNull(messages);
        TransactionTemplate tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
        this.clock = Objects.requireNonNull(clock);
    }

    public VnpayReturnView inspectReturn(Map<String, String> params, String clientIp) {
        Map<String, String> vnpParams = VnpayCallbackSupport.extractVnpParams(params);
        Map<String, Object> raw = VnpayCallbackSupport.buildRawEventPayload(vnpParams, clientIp);
        String txnRef = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_TxnRef"));
        Payment payment = StringUtils.hasText(txnRef)
                ? paymentRepository.findByTxnRef(txnRef).orElse(null)
                : null;
        UUID paymentId = payment == null ? null : payment.getId();

        if (!vnpayGateway.verify(vnpParams)) {
            saveReturnEvent(paymentId, raw, false, "RETURN_SIGNATURE_INVALID");
            throw new BusinessRuleException("VNPAY_SIGNATURE_INVALID");
        }

        if (!VnpayCallbackSupport.hasValidCallbackShape(vnpParams)) {
            saveReturnEvent(paymentId, raw, false, "RETURN_INVALID_CALLBACK_PARAMS");
            throw new BusinessRuleException("VNPAY_SIGNATURE_INVALID");
        }

        if (payment == null) {
            saveReturnEvent(null, raw, true, "RETURN_ORDER_NOT_FOUND");
            throw new NotFoundException("ORDER_NOT_FOUND", messages.get("ORDER_NOT_FOUND"));
        }

        if (!VnpayCallbackSupport.matchesScaledAmount(vnpParams.get("vnp_Amount"), payment.getAmountVnd())) {
            saveReturnEvent(payment.getId(), raw, false, "RETURN_INVALID_AMOUNT");
            throw new BusinessRuleException("VNPAY_SIGNATURE_INVALID");
        }

        PurchaseOrder order = purchaseOrderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) {
            saveReturnEvent(payment.getId(), raw, true, "RETURN_ORDER_NOT_FOUND");
            throw new NotFoundException("ORDER_NOT_FOUND", messages.get("ORDER_NOT_FOUND"));
        }

        PaymentState effectiveState =
                (payment.getState() == PaymentState.SUCCEEDED || payment.getState() == PaymentState.FAILED)
                        ? payment.getState()
                        : VnpayCallbackSupport.classifyOutcome(vnpParams);
        saveReturnEvent(payment.getId(), raw, true, "RETURN_VERIFIED_" + effectiveState.name());
        return new VnpayReturnView(order.getCode(), effectiveState.name());
    }

    private void saveReturnEvent(UUID paymentId, Map<String, Object> raw, boolean sigValid, String outcome) {
        Instant now = clock.instant();
        requiresNewTx.executeWithoutResult(status -> paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), paymentId, PaymentEventSource.RETURN, raw, sigValid, now, outcome)));
    }
}
