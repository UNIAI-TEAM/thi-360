package vn.unicom.thi360.commerce.payment.vnpay;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.ApplyResult;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.QueryDrMetadata;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.QueryDrOutcomeAction;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrMapper.DecisionKind;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrMapper.QueryDrDecision;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Điều phối tra cứu trạng thái giao dịch VNPAY {@code querydr} ngoài transaction (plan B2, TM 8.4):
 * nhận việc bằng {@code SKIP LOCKED} ({@code REQUIRES_NEW}), gọi HTTP ngoài mọi DB transaction,
 * áp dụng kết quả qua {@link PaymentOutcomeApplier}, và cấp quyền sau khi transaction kết quả đã commit.
 */
@Service
public class QueryDrService {

    private static final Logger log = LoggerFactory.getLogger(QueryDrService.class);
    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_BATCHES = 25;
    private static final String REDACTED = "[REDACTED]";
    private static final QueryDrMetadata EMPTY_META = new QueryDrMetadata(null, null, null, null, null, null);

    private record QueryExecutionOutcome(ApplyResult applyResult, boolean transportFailure) {
    }

    private final QueryDrClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VnpayGateway vnpayGateway;
    private final VnpayClientProperties clientProperties;
    private final QueryDrProperties queryDrProperties;
    private final PaymentOutcomeApplier paymentOutcomeApplier;
    private final FulfillmentService fulfillmentService;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;
    private final VnpayProperties vnpayProperties;

    public QueryDrService(
            QueryDrClaimRepository claimRepository,
            PaymentRepository paymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            VnpayGateway vnpayGateway,
            VnpayClientProperties clientProperties,
            QueryDrProperties queryDrProperties,
            PaymentOutcomeApplier paymentOutcomeApplier,
            FulfillmentService fulfillmentService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(
                claimRepository,
                paymentRepository,
                purchaseOrderRepository,
                vnpayGateway,
                clientProperties,
                queryDrProperties,
                paymentOutcomeApplier,
                fulfillmentService,
                transactionManager,
                clock,
                null);
    }

    @Autowired
    public QueryDrService(
            QueryDrClaimRepository claimRepository,
            PaymentRepository paymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            VnpayGateway vnpayGateway,
            VnpayClientProperties clientProperties,
            QueryDrProperties queryDrProperties,
            PaymentOutcomeApplier paymentOutcomeApplier,
            FulfillmentService fulfillmentService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            VnpayProperties vnpayProperties) {
        this.claimRepository = Objects.requireNonNull(claimRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway);
        this.clientProperties = Objects.requireNonNull(clientProperties);
        this.queryDrProperties = Objects.requireNonNull(queryDrProperties);
        this.paymentOutcomeApplier = Objects.requireNonNull(paymentOutcomeApplier);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        TransactionTemplate ro = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        ro.setReadOnly(true);
        this.readOnlyTx = ro;
        this.clock = Objects.requireNonNull(clock);
        this.vnpayProperties = vnpayProperties;
    }

    /** Chạy tối đa 25 lô tra cứu các lần thanh toán mở đủ tuổi trong cửa sổ {@code [minAge, maxAge]}. */
    public int runQueryCycle() {
        Instant cycleStart = clock.instant();
        claimRepository.transitionAgedPaymentsPastMaxAge(
                cycleStart.minus(queryDrProperties.maxAge()), cycleStart);

        Instant deadline = cycleStart.plus(queryDrProperties.cycleBudget());
        int maxConsecutiveTransportFailures = queryDrProperties.maxConsecutiveTransportFailures();
        int consecutiveTransportFailures = 0;
        int totalQueried = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline)) {
                break;
            }
            List<ClaimedPayment> claimed = claimRepository.claimEligibleForWorker(
                    clock.instant(),
                    queryDrProperties.minAge(),
                    queryDrProperties.maxAge(),
                    queryDrProperties.cooldown(),
                    queryDrProperties.batchSize());
            if (claimed.isEmpty()) {
                break;
            }
            boolean stopCycle = false;
            for (ClaimedPayment item : claimed) {
                if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline)) {
                    stopCycle = true;
                    break;
                }
                try {
                    QueryExecutionOutcome outcome = executeClaimedQueryInternal(item);
                    totalQueried++;
                    if (outcome.transportFailure()) {
                        consecutiveTransportFailures++;
                        if (consecutiveTransportFailures >= maxConsecutiveTransportFailures) {
                            log.warn("Stopping querydr cycle after {} consecutive transport failures",
                                    consecutiveTransportFailures);
                            stopCycle = true;
                            break;
                        }
                    } else {
                        consecutiveTransportFailures = 0;
                    }
                } catch (RuntimeException ex) {
                    log.warn("Unexpected error executing querydr for payment {} order {}",
                            item.paymentId(), item.orderCode(), ex);
                }
            }
            if (stopCycle) {
                break;
            }
        }
        return totalQueried;
    }

    /** Tra cứu trực tiếp một lần thanh toán theo ID (kể cả khi đã {@code FAILED}, phục vụ đối soát). */
    public ApplyResult queryPaymentNow(UUID paymentId) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("QueryDrService.queryPaymentNow must run outside any database transaction");
        }
        ClaimedPayment target = readOnlyTx.execute(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null) {
                return null;
            }
            PurchaseOrder order = purchaseOrderRepository.findById(payment.getOrderId()).orElse(null);
            if (order == null) {
                return null;
            }
            return new ClaimedPayment(
                    payment.getId(),
                    order.getId(),
                    order.getCode(),
                    payment.getTxnRef(),
                    payment.getVnpCreateDate(),
                    payment.getVnpExpireDate(),
                    payment.getAmountVnd(),
                    payment.getVnpTransactionNo(),
                    payment.getState());
        });
        if (target == null) {
            throw new NotFoundException("PAYMENT_NOT_FOUND", null);
        }
        if (target.stateBefore() == PaymentState.CREATED || target.stateBefore() == PaymentState.UNKNOWN) {
            claimRepository.extendCooldownIfOpen(
                    target.paymentId(), clock.instant(), queryDrProperties.cooldown());
        }
        return executeClaimedQuery(target);
    }

    /** Thực hiện gọi VNPAY {@code querydr} ngoài transaction cho một lần thanh toán đã được nhận việc. */
    public ApplyResult executeClaimedQuery(ClaimedPayment claimed) {
        return executeClaimedQueryInternal(claimed).applyResult();
    }

    private QueryExecutionOutcome executeClaimedQueryInternal(ClaimedPayment claimed) {
        Objects.requireNonNull(claimed, "claimed must not be null");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("QueryDrService.executeClaimedQuery must run outside any database transaction");
        }

        Instant now = clock.instant();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = VNP_TIME_FMT.format(now.atZone(GMT_PLUS_7));
        String orderInfo = "Kiem tra ket qua GD " + claimed.orderCode();

        QueryDrRequest request = new QueryDrRequest(
                requestId,
                claimed.txnRef(),
                claimed.vnpCreateDate(),
                createDate,
                clientProperties.serverIp(),
                orderInfo,
                claimed.vnpTransactionNo());

        QueryDrResult result;
        try {
            result = vnpayGateway.queryDr(request);
        } catch (VnpayTransportException ex) {
            return new QueryExecutionOutcome(
                    recordTransportFailure(claimed, requestId, ex.isTimeout(), ex.getHttpStatus(), ex.getSafeDetail()),
                    true);
        } catch (RuntimeException ex) {
            return new QueryExecutionOutcome(recordLocalFailure(claimed, requestId, ex), false);
        }

        QueryDrDecision decision = QueryDrMapper.decide(
                result,
                claimed.txnRef(),
                claimed.amountVnd(),
                claimed.vnpExpireDate(),
                clock.instant(),
                queryDrProperties.notFoundGrace());

        if (decision.logConfigError()) {
            log.error("VNPAY querydr config/code error: responseCode={}, txnRef={}, orderCode={}",
                    result != null ? result.responseCode() : null,
                    claimed.txnRef(),
                    claimed.orderCode());
        }

        boolean signatureValid = result != null && result.signatureValid();
        boolean attachMetadata = signatureValid
                && !"QUERYDR_TXN_REF_MISMATCH".equals(decision.outcomeTag());
        QueryDrMetadata metadata = attachMetadata
                ? new QueryDrMetadata(
                        normalizeTransactionNo(result.transactionNo()),
                        trimToNull(result.bankCode()),
                        null,
                        trimToNull(result.payDate()),
                        trimToNull(result.responseCode()),
                        trimToNull(result.transactionStatus()))
                : EMPTY_META;

        Map<String, Object> rawPayload = buildSanitizedResultRaw(result, requestId, claimed.txnRef());

        ApplyResult applyResult = paymentOutcomeApplier.applyQueryDrOutcome(
                claimed.paymentId(),
                claimed.orderId(),
                toOutcomeAction(decision.kind()),
                decision.outcomeTag(),
                decision.reviewReason(),
                metadata,
                rawPayload,
                signatureValid);

        if (applyResult.fulfillOrderId() != null) {
            try {
                fulfillmentService.fulfill(applyResult.fulfillOrderId());
            } catch (RuntimeException ex) {
                log.warn("Post-querydr fulfillment failed for order {}", applyResult.fulfillOrderId(), ex);
            }
        }
        return new QueryExecutionOutcome(applyResult, false);
    }

    private ApplyResult recordTransportFailure(
            ClaimedPayment claimed,
            String requestId,
            boolean timeout,
            Integer httpStatus,
            String safeDetail) {
        String redactedDetail = redactConfiguredSecret(safeDetail);
        String safeError = StringUtils.hasText(redactedDetail) ? redactedDetail.trim() : "VNPAY transport error";
        log.warn("VNPAY querydr transport failure: txnRef={}, orderCode={}, requestId={}, timeout={}, httpStatus={}, detail={}",
                claimed.txnRef(), claimed.orderCode(), requestId, timeout, httpStatus, safeError);

        String outcomeTag = timeout ? "QUERYDR_TRANSPORT_TIMEOUT" : "QUERYDR_TRANSPORT_ERROR";
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("_requestId", requestId);
        rawPayload.put("vnp_RequestId", requestId);
        rawPayload.put("vnp_TxnRef", claimed.txnRef());
        rawPayload.put("_error", safeError);
        rawPayload.put("_timeout", timeout);
        if (httpStatus != null) {
            rawPayload.put("_httpStatus", httpStatus);
        }
        return paymentOutcomeApplier.applyQueryDrOutcome(
                claimed.paymentId(),
                claimed.orderId(),
                QueryDrOutcomeAction.KEEP_UNCHANGED,
                outcomeTag,
                null,
                EMPTY_META,
                rawPayload,
                false);
    }

    private ApplyResult recordLocalFailure(
            ClaimedPayment claimed,
            String requestId,
            RuntimeException ex) {
        String errorType = ex.getClass().getSimpleName();
        String redactedMessage = redactConfiguredSecret(ex.getMessage());
        String safeDetail = StringUtils.hasText(redactedMessage) ? redactedMessage : errorType;
        log.error("Unexpected non-transport error calling VNPAY querydr: txnRef={}, orderCode={}, requestId={}, errorType={}, detail={}",
                claimed.txnRef(), claimed.orderCode(), requestId, errorType, safeDetail);

        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("_requestId", requestId);
        rawPayload.put("vnp_RequestId", requestId);
        rawPayload.put("vnp_TxnRef", claimed.txnRef());
        rawPayload.put("_error", errorType);
        return paymentOutcomeApplier.applyQueryDrOutcome(
                claimed.paymentId(),
                claimed.orderId(),
                QueryDrOutcomeAction.KEEP_UNCHANGED,
                "QUERYDR_LOCAL_ERROR",
                null,
                EMPTY_META,
                rawPayload,
                false);
    }

    private Map<String, Object> buildSanitizedResultRaw(
            QueryDrResult result, String requestId, String txnRef) {
        if (result == null) {
            return Map.of(
                    "_requestId", requestId,
                    "vnp_RequestId", requestId,
                    "vnp_TxnRef", txnRef,
                    "_error", "NULL_QUERYDR_RESULT");
        }
        Map<String, Object> sanitized = PaymentOutcomeApplier.sanitizeRawPayload(result.rawResponse());
        Map<String, Object> raw = new LinkedHashMap<>();
        if (sanitized.isEmpty()) {
            raw.put("vnp_RequestId", requestId);
            raw.put("vnp_TxnRef", txnRef);
        } else {
            sanitized.forEach((key, value) -> {
                String cleanKey = redactConfiguredSecret(key);
                Object cleanVal = redactValue(value);
                if (cleanKey != null && cleanVal != null) {
                    raw.put(cleanKey, cleanVal);
                }
            });
        }
        raw.put("_requestId", requestId);
        return Map.copyOf(raw);
    }

    private Object redactValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return redactConfiguredSecret(str);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    String cleanK = redactConfiguredSecret(String.valueOf(k));
                    Object cleanV = redactValue(v);
                    if (cleanV != null) {
                        clean.put(cleanK, cleanV);
                    }
                }
            });
            return Map.copyOf(clean);
        }
        if (value instanceof List<?> list) {
            List<Object> clean = new ArrayList<>(list.size());
            for (Object item : list) {
                Object cleanItem = redactValue(item);
                if (cleanItem != null) {
                    clean.add(cleanItem);
                }
            }
            return List.copyOf(clean);
        }
        return value;
    }

    private String redactConfiguredSecret(String text) {
        if (text == null || vnpayProperties == null || !StringUtils.hasText(vnpayProperties.hashSecret())) {
            return text;
        }
        String secret = vnpayProperties.hashSecret().trim();
        return text.contains(secret) ? text.replace(secret, REDACTED) : text;
    }

    private static QueryDrOutcomeAction toOutcomeAction(DecisionKind kind) {
        return switch (kind) {
            case SUCCEEDED -> QueryDrOutcomeAction.SUCCEEDED;
            case FAILED -> QueryDrOutcomeAction.FAILED;
            case UNKNOWN -> QueryDrOutcomeAction.UNKNOWN;
            case UNKNOWN_NEEDS_REVIEW -> QueryDrOutcomeAction.UNKNOWN_NEEDS_REVIEW;
            case KEEP_UNCHANGED -> QueryDrOutcomeAction.KEEP_UNCHANGED;
        };
    }

    private static String normalizeTransactionNo(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "0".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
