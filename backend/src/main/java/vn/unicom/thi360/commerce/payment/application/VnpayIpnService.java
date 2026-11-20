package vn.unicom.thi360.commerce.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.ApplyResult;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayIpnProcessor;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Xử lý VNPAY IPN theo thứ tự VNPAY 2.1.0 và TM 8.4:
 * lọc IP -> kiểm chữ ký -> tìm {@code vnp_TxnRef} -> kiểm số tiền -> cập nhật trong transaction ->
 * cấp quyền sau khi commit. Mọi lần gọi (kể cả lỗi chữ ký/IP) đều ghi {@code payment_event} và luôn trả JSON
 * với mã VNPAY (không ném ngoại lệ ra HTTP 500).
 */
@Service
public class VnpayIpnService implements VnpayIpnProcessor {

    private static final Logger log = LoggerFactory.getLogger(VnpayIpnService.class);

    private final VnpayIpnIpAllowlist ipAllowlist;
    private final VnpayGateway vnpayGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentOutcomeApplier outcomeApplier;
    private final FulfillmentService fulfillmentService;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public VnpayIpnService(
            VnpayIpnIpAllowlist ipAllowlist,
            VnpayGateway vnpayGateway,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            PaymentOutcomeApplier outcomeApplier,
            FulfillmentService fulfillmentService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.ipAllowlist = Objects.requireNonNull(ipAllowlist);
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.paymentEventRepository = Objects.requireNonNull(paymentEventRepository);
        this.outcomeApplier = Objects.requireNonNull(outcomeApplier);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        TransactionTemplate tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public IpnReply process(Map<String, String> params, String clientIp) {
        try {
            Map<String, String> vnpParams = VnpayCallbackSupport.extractVnpParams(params);
            Map<String, Object> raw = VnpayCallbackSupport.buildRawEventPayload(vnpParams, clientIp);
            String txnRef = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_TxnRef"));

            if (!ipAllowlist.isAllowed(clientIp)) {
                UUID optionalPaymentId = findPaymentIdQuietly(txnRef);
                saveAuditEvent(optionalPaymentId, raw, false, "IP_REJECTED_97");
                return new IpnReply("97", "Invalid Checksum");
            }

            if (!vnpayGateway.verify(vnpParams)) {
                UUID optionalPaymentId = findPaymentIdQuietly(txnRef);
                saveAuditEvent(optionalPaymentId, raw, false, "INVALID_SIGNATURE_97");
                return new IpnReply("97", "Invalid Checksum");
            }

            if (!VnpayCallbackSupport.hasValidCallbackShape(vnpParams)) {
                UUID optionalPaymentId = findPaymentIdQuietly(txnRef);
                saveAuditEvent(optionalPaymentId, raw, false, "INVALID_CALLBACK_PARAMS_97");
                return new IpnReply("97", "Invalid Checksum");
            }

            if (!StringUtils.hasText(txnRef)) {
                saveAuditEvent(null, raw, true, "ORDER_NOT_FOUND_01");
                return new IpnReply("01", "Order not Found");
            }

            PaymentRepository.PaymentLookup payment = paymentRepository.findLookupByTxnRef(txnRef).orElse(null);
            if (payment == null) {
                saveAuditEvent(null, raw, true, "ORDER_NOT_FOUND_01");
                return new IpnReply("01", "Order not Found");
            }

            if (!VnpayCallbackSupport.matchesScaledAmount(vnpParams.get("vnp_Amount"), payment.amountVnd())) {
                saveAuditEvent(payment.id(), raw, true, "INVALID_AMOUNT_04");
                return new IpnReply("04", "Invalid Amount");
            }

            ApplyResult result = outcomeApplier.applyIpnOutcome(
                    payment.id(), payment.orderId(), vnpParams, raw);

            if (result.fulfillOrderId() != null) {
                try {
                    fulfillmentService.fulfill(result.fulfillOrderId());
                } catch (RuntimeException ex) {
                    log.warn("Post-IPN fulfillment failed for order {} (IPN still returns 00)",
                            result.fulfillOrderId(), ex);
                }
            }
            return new IpnReply(result.rspCode(), result.message());
        } catch (RuntimeException ex) {
            log.error("Unexpected error while handling VNPAY IPN", ex);
            return new IpnReply("99", "Unknown error");
        }
    }

    private UUID findPaymentIdQuietly(String txnRef) {
        if (!StringUtils.hasText(txnRef)) {
            return null;
        }
        return paymentRepository.findLookupByTxnRef(txnRef).map(PaymentRepository.PaymentLookup::id).orElse(null);
    }

    private void saveAuditEvent(UUID paymentId, Map<String, Object> raw, boolean sigValid, String outcome) {
        Instant now = clock.instant();
        requiresNewTx.executeWithoutResult(status -> paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), paymentId, PaymentEventSource.IPN, raw, sigValid, now, outcome)));
    }
}
