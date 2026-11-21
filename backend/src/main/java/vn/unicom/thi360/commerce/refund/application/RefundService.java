package vn.unicom.thi360.commerce.refund.application;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.refund.api.CreateRefundRequest;
import vn.unicom.thi360.commerce.refund.api.MarkRefundFailedRequest;
import vn.unicom.thi360.commerce.refund.api.RefundResponse;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryDecision;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryOutcome;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.RefundMappingDecision;
import vn.unicom.thi360.commerce.refund.domain.Refund;
import vn.unicom.thi360.commerce.refund.domain.RefundKind;
import vn.unicom.thi360.commerce.refund.domain.RefundState;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundGrantLookupRepository;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundGrantLookupRepository.GrantRef;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.MailContact;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Nghiệp vụ hoàn tiền VNPAY API, ghi nhận thủ công, tra cứu {@code /query}, đánh dấu thất bại
 * và thu hồi quyền dùng khi hoàn hết khoản được tính (TM 9.4, BS 14, C14, C15, E23, plan B2).
 *
 * <p>Không đặt {@code @Transactional} ở các hàm gọi cổng VNPAY để bảo đảm không giữ bất kỳ giao dịch
 * hoặc khóa dòng DB nào trong lúc gọi mạng.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RefundRepository refundRepository;
    private final RefundGrantLookupRepository grantLookupRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final EntitlementGrantService entitlementGrantService;
    private final VnpayGateway vnpayGateway;
    private final RefundCodeMapper codeMapper;
    private final RefundProperties properties;
    private final UserDirectory userDirectory;
    private final MailQueue mailQueue;
    private final IdempotentCommand idempotentCommand;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final AppMessages messages;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate readOnlyTx;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public RefundService(
            RefundRepository refundRepository,
            RefundGrantLookupRepository grantLookupRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            EntitlementGrantService entitlementGrantService,
            VnpayGateway vnpayGateway,
            RefundCodeMapper codeMapper,
            RefundProperties properties,
            UserDirectory userDirectory,
            MailQueue mailQueue,
            IdempotentCommand idempotentCommand,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            AppMessages messages,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.refundRepository = Objects.requireNonNull(refundRepository);
        this.grantLookupRepository = Objects.requireNonNull(grantLookupRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.paymentEventRepository = Objects.requireNonNull(paymentEventRepository);
        this.entitlementGrantService = Objects.requireNonNull(entitlementGrantService);
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway);
        this.codeMapper = Objects.requireNonNull(codeMapper);
        this.properties = Objects.requireNonNull(properties);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.mailQueue = Objects.requireNonNull(mailQueue);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.messages = Objects.requireNonNull(messages);
        PlatformTransactionManager txMgr = Objects.requireNonNull(transactionManager);
        this.txTemplate = new TransactionTemplate(txMgr);
        TransactionTemplate ro = new TransactionTemplate(txMgr);
        ro.setReadOnly(true);
        this.readOnlyTx = ro;
        TransactionTemplate rn = new TransactionTemplate(txMgr);
        rn.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = rn;
        this.clock = Objects.requireNonNull(clock);
    }

    public RefundResponse createRefund(
            String orderCode,
            UUID paymentId,
            String idempotencyKey,
            CreateRefundRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String normalizedCode = normalizeOrderCode(orderCode);
        String requiredKey = idempotentCommand.requireKey(idempotencyKey);

        AtomicBoolean freshlyCreated = new AtomicBoolean(false);
        CreateRefundTx1Outcome tx1 = idempotentCommand.execute(
                requiredKey,
                "refund.create",
                normalizedCode + ":" + paymentId,
                request,
                CreateRefundTx1Outcome.class,
                () -> {
                    CreateRefundTx1Outcome created = createRefundTx1(normalizedCode, paymentId, actor, requiredKey, request);
                    freshlyCreated.set(true);
                    return created;
                });

        // Khi phát lại idempotent hoặc hoàn thủ công 1 transaction: không gọi lại cổng VNPAY.
        if (!freshlyCreated.get() || tx1.kind() == RefundKind.MANUAL) {
            return loadRefundResponse(tx1.refundId());
        }

        // Gọi cổng VNPAY hoàn toàn ngoài mọi transaction DB (plan B2, AC-14).
        RefundMappingDecision decision;
        Map<String, Object> rawPayload;
        boolean signatureValid;
        boolean gatewayException = false;
        try {
            String transactionType = (tx1.amountVnd() == tx1.paymentAmountVnd() && tx1.succeededSumBefore() == 0L)
                    ? "02"
                    : "03";
            String createDate = VNP_TIME_FMT.format(clock.instant().atZone(GMT_PLUS_7));
            RefundRequest gatewayReq = new RefundRequest(
                    tx1.vnpRequestId(),
                    transactionType,
                    tx1.txnRef(),
                    tx1.amountVnd(),
                    tx1.vnpTransactionNo(),
                    tx1.vnpCreateDate(),
                    tx1.actorUsername(),
                    createDate,
                    properties.serverIp(),
                    "Hoan tien don " + tx1.orderCode());
            RefundResult result = vnpayGateway.refund(gatewayReq);
            decision = codeMapper.mapRefundResult(result, tx1.txnRef(), tx1.amountVnd());
            signatureValid = result != null && result.signatureValid();
            rawPayload = buildRefundRawPayload(tx1.vnpRequestId(), result, decision);
        } catch (RuntimeException ex) {
            decision = codeMapper.mapRefundException(ex);
            signatureValid = false;
            gatewayException = true;
            rawPayload = buildExceptionRawPayload(tx1.vnpRequestId(), decision.message());
            log.warn("VNPAY refund gateway call threw exception for order {} refund {}",
                    tx1.orderCode(), tx1.refundId(), ex);
        }

        final RefundMappingDecision finalDecision = decision;
        final Map<String, Object> finalRaw = rawPayload;
        final boolean finalSigValid = signatureValid;
        final boolean finalGatewayException = gatewayException;
        return requiresNewTx.execute(status -> applyApiRefundResultInTx(
                tx1.refundId(),
                tx1.orderId(),
                tx1.paymentId(),
                tx1.actorUsername(),
                finalDecision,
                finalRaw,
                finalSigValid,
                finalGatewayException));
    }

    public RefundResponse getRefund(UUID refundId) {
        return loadRefundResponse(refundId);
    }

    public RefundResponse queryRefund(UUID refundId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        QueryRefundSnapshot snapshot = readOnlyTx.execute(status -> {
            Refund refund = refundRepository.findById(refundId)
                    .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));
            if (refund.getKind() == RefundKind.MANUAL || refund.getState() == RefundState.FAILED) {
                throw new ConflictException("REFUND_QUERY_NOT_ALLOWED");
            }
            PurchaseOrder order = purchaseOrderRepository.findById(refund.getOrderId())
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
            Payment payment = paymentRepository.findById(refund.getPaymentId())
                    .orElseThrow(() -> new NotFoundException("ORDER_PAYMENT_NOT_FOUND", null));
            String txnNo = StringUtils.hasText(refund.getVnpTransactionNo())
                    ? refund.getVnpTransactionNo()
                    : payment.getVnpTransactionNo();
            return new QueryRefundSnapshot(
                    refund.getId(),
                    order.getId(),
                    order.getCode(),
                    payment.getId(),
                    payment.getTxnRef(),
                    payment.getVnpCreateDate(),
                    txnNo);
        });

        String requestId = Ids.newId().toString().replace("-", "");
        QueryDecision decision;
        Map<String, Object> rawPayload;
        boolean signatureValid;
        boolean gatewayException = false;
        try {
            String createDate = VNP_TIME_FMT.format(clock.instant().atZone(GMT_PLUS_7));
            QueryDrRequest queryReq = new QueryDrRequest(
                    requestId,
                    snapshot.txnRef(),
                    snapshot.vnpCreateDate(),
                    createDate,
                    properties.serverIp(),
                    "Tra cuu hoan tien don " + snapshot.orderCode(),
                    snapshot.vnpTransactionNo());
            QueryDrResult result = vnpayGateway.queryDr(queryReq);
            decision = codeMapper.mapQueryDrResult(result, snapshot.txnRef());
            signatureValid = result != null && result.signatureValid();
            rawPayload = buildQueryRawPayload(requestId, result, decision);
        } catch (RuntimeException ex) {
            String msg = extractExceptionMessage(ex);
            decision = new QueryDecision(QueryOutcome.KEEP, false, null, null, null, msg);
            signatureValid = false;
            gatewayException = true;
            rawPayload = buildExceptionRawPayload(requestId, msg);
            log.warn("VNPAY queryDr gateway call threw exception for order {} refund {}",
                    snapshot.orderCode(), snapshot.refundId(), ex);
        }

        final QueryDecision finalDecision = decision;
        final Map<String, Object> finalRaw = rawPayload;
        final boolean finalSigValid = signatureValid;
        final boolean finalGatewayException = gatewayException;
        QueryApplyResult applyResult = requiresNewTx.execute(status -> applyQueryResultInTx(
                snapshot.refundId(),
                snapshot.orderId(),
                snapshot.paymentId(),
                actor.username(),
                finalDecision,
                finalRaw,
                finalSigValid,
                finalGatewayException));
        if (applyResult == null) {
            throw new IllegalStateException("Unexpected null applyQueryResultInTx result");
        }
        if (applyResult.conflictNotAllowed()) {
            throw new ConflictException("REFUND_QUERY_NOT_ALLOWED");
        }
        return applyResult.response();
    }

    public RefundResponse markFailed(UUID refundId, MarkRefundFailedRequest request) {
        currentUserProvider.requireCurrentUser();
        return txTemplate.execute(status -> {
            UUID orderId = refundRepository.findOrderIdById(refundId)
                    .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));
            PurchaseOrder order = purchaseOrderRepository.lockById(orderId)
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
            List<Payment> payments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(order.getId());
            Refund refund = refundRepository.lockById(refundId)
                    .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));
            Payment payment = findPayment(payments, refund.getPaymentId());

            if (refund.getState() != RefundState.UNKNOWN) {
                throw new ConflictException("REFUND_NOT_UNKNOWN");
            }
            Instant now = clock.instant();
            refund.markFailedManually(request.reason(), request.evidenceRef(), now);
            refundRepository.saveAndFlush(refund);

            auditRecorder.record(AuditEvent.builder("commerce", "REFUND_FAILED")
                    .target("REFUND", refund.getId().toString(), order.getCode())
                    .reason(refund.getMarkFailedReason())
                    .after(Map.of(
                            "orderCode", order.getCode(),
                            "paymentId", payment.getId().toString(),
                            "state", RefundState.FAILED.name(),
                            "markFailedEvidence", refund.getMarkFailedEvidence(),
                            "markFailedReason", refund.getMarkFailedReason()))
                    .build());

            return RefundResponse.from(refund, order, payment);
        });
    }

    private CreateRefundTx1Outcome createRefundTx1(
            String normalizedCode,
            UUID paymentId,
            CurrentUser actor,
            String idempotencyKey,
            CreateRefundRequest request) {
        if (!Boolean.TRUE.equals(request.verifiedSystemError())) {
            String msg = messages.get("REFUND_SYSTEM_ERROR_NOT_VERIFIED");
            throw new BusinessRuleException(
                    "REFUND_SYSTEM_ERROR_NOT_VERIFIED",
                    msg,
                    List.of(new FieldError("verifiedSystemError", "REFUND_SYSTEM_ERROR_NOT_VERIFIED", msg)));
        }
        RefundKind effectiveKind = request.kind() != null ? request.kind() : RefundKind.API;
        if (effectiveKind == RefundKind.MANUAL && !StringUtils.hasText(request.externalRef())) {
            String msg = messages.get("REFUND_EXTERNAL_REF_REQUIRED");
            throw new BusinessRuleException(
                    "REFUND_EXTERNAL_REF_REQUIRED",
                    msg,
                    List.of(new FieldError("externalRef", "REFUND_EXTERNAL_REF_REQUIRED", msg)));
        }

        // Thứ tự khóa B2: purchase_order -> payment -> refund.
        PurchaseOrder order = purchaseOrderRepository.lockByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
        List<Payment> payments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(order.getId());
        Payment payment = findPayment(payments, paymentId);

        if (payment.getState() != PaymentState.SUCCEEDED) {
            throw new ConflictException("REFUND_PAYMENT_NOT_SUCCEEDED");
        }
        if (refundRepository.existsActiveByPaymentId(payment.getId())) {
            throw new ConflictException("REFUND_ACTIVE_EXISTS");
        }
        long succeededSum = refundRepository.sumSucceededAmountByPaymentId(payment.getId());
        long activeSum = refundRepository.sumActiveAmountByPaymentId(payment.getId());
        long refundable = payment.getAmountVnd() - succeededSum - activeSum;
        if (request.amountVnd() > refundable) {
            throw new ConflictException("REFUND_AMOUNT_EXCEEDED");
        }

        if (effectiveKind == RefundKind.API && !StringUtils.hasText(payment.getVnpCreateDate())) {
            throw new IllegalStateException("Payment " + payment.getId() + " is missing vnpCreateDate");
        }
        String scopedColumnKey = actor.id() + ":" + payment.getId() + ":" + idempotencyKey;
        Instant now = clock.instant();

        if (effectiveKind == RefundKind.MANUAL) {
            if (!refundRepository.existsFailedApiByPaymentId(payment.getId())) {
                throw new ConflictException("REFUND_MANUAL_REQUIRES_FAILED_API");
            }
            UUID refundId = Ids.newId();
            Refund refund = Refund.createManualSucceeded(
                    refundId,
                    payment.getId(),
                    order.getId(),
                    request.amountVnd(),
                    request.reason(),
                    request.externalRef(),
                    actor.username(),
                    actor.id(),
                    now,
                    scopedColumnKey);
            refundRepository.saveAndFlush(refund);

            auditRecorder.record(AuditEvent.builder("commerce", "REFUND_RECORDED_MANUALLY")
                    .target("REFUND", refund.getId().toString(), order.getCode())
                    .reason(refund.getReason())
                    .after(Map.of(
                            "orderCode", order.getCode(),
                            "paymentId", payment.getId().toString(),
                            "amountVnd", refund.getAmountVnd(),
                            "externalRef", refund.getExternalRef(),
                            "kind", RefundKind.MANUAL.name(),
                            "state", RefundState.SUCCEEDED.name()))
                    .build());

            applySucceededRefundEffects(order, payment, refund, actor.username(), now);
            return new CreateRefundTx1Outcome(
                    refundId,
                    RefundKind.MANUAL,
                    null,
                    payment.getTxnRef(),
                    payment.getVnpTransactionNo(),
                    payment.getVnpCreateDate(),
                    refund.getAmountVnd(),
                    payment.getAmountVnd(),
                    succeededSum,
                    order.getId(),
                    payment.getId(),
                    order.getCode(),
                    actor.username());
        }

        UUID refundId = Ids.newId();
        String vnpRequestId = Ids.newId().toString().replace("-", "");
        Refund refund = Refund.createApiProcessing(
                refundId,
                vnpRequestId,
                payment.getId(),
                order.getId(),
                request.amountVnd(),
                request.reason(),
                actor.username(),
                actor.id(),
                now,
                scopedColumnKey);
        refundRepository.saveAndFlush(refund);

        auditRecorder.record(AuditEvent.builder("commerce", "REFUND_REQUESTED")
                .target("REFUND", refund.getId().toString(), order.getCode())
                .reason(refund.getReason())
                .after(Map.of(
                        "orderCode", order.getCode(),
                        "paymentId", payment.getId().toString(),
                        "vnpRequestId", vnpRequestId,
                        "amountVnd", refund.getAmountVnd(),
                        "kind", RefundKind.API.name(),
                        "state", RefundState.PROCESSING.name()))
                .build());

        return new CreateRefundTx1Outcome(
                refundId,
                RefundKind.API,
                vnpRequestId,
                payment.getTxnRef(),
                payment.getVnpTransactionNo(),
                payment.getVnpCreateDate(),
                refund.getAmountVnd(),
                payment.getAmountVnd(),
                succeededSum,
                order.getId(),
                payment.getId(),
                order.getCode(),
                actor.username());
    }

    private RefundResponse applyApiRefundResultInTx(
            UUID refundId,
            UUID orderId,
            UUID paymentId,
            String actorUsername,
            RefundMappingDecision decision,
            Map<String, Object> rawPayload,
            boolean signatureValid,
            boolean gatewayException) {
        Instant now = clock.instant();
        PurchaseOrder order = purchaseOrderRepository.lockById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
        List<Payment> payments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(order.getId());
        Payment payment = findPayment(payments, paymentId);
        Refund refund = refundRepository.lockById(refundId)
                .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));

        String outcome = buildEventOutcome(
                "REFUND", decision.state().name(), decision.responseCode(), signatureValid, gatewayException);
        paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), payment.getId(), PaymentEventSource.REFUND_API, rawPayload, signatureValid, now, outcome));

        if (decision.opsAlert()) {
            log.error("VNPAY refund configuration or merchant error for order {} refund {} code={} message={}",
                    order.getCode(), refund.getId(), decision.responseCode(), decision.message());
        }

        if (refund.getState() != RefundState.PROCESSING) {
            if (decision.state() == RefundState.SUCCEEDED && refund.getState() != RefundState.SUCCEEDED) {
                OrderState prevState = order.getState();
                String reviewReason = "REFUND_LATE_SUCCESS_AFTER_FAILED:" + refund.getId();
                log.error("VNPAY refund API returned SUCCEEDED for refund {} on order {} while refund is already in state {}",
                        refund.getId(), order.getCode(), refund.getState());
                order.flagNeedsReview(reviewReason, now);
                purchaseOrderRepository.save(order);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        prevState,
                        prevState,
                        actorUsername,
                        reviewReason,
                        now));
            }
            return RefundResponse.from(refund, order, payment);
        }

        switch (decision.state()) {
            case SUCCEEDED -> {
                refund.markSucceeded(
                        decision.responseCode(),
                        decision.transactionStatus(),
                        decision.transactionNo(),
                        decision.message(),
                        now);
                refundRepository.saveAndFlush(refund);
                auditRecorder.record(AuditEvent.builder("commerce", "REFUND_SUCCEEDED")
                        .target("REFUND", refund.getId().toString(), order.getCode())
                        .reason(refund.getReason())
                        .after(buildRefundAuditMap(order, payment, refund))
                        .build());
                applySucceededRefundEffects(order, payment, refund, actorUsername, now);
            }
            case FAILED -> {
                refund.markFailedFromGateway(
                        decision.responseCode(),
                        decision.transactionStatus(),
                        decision.transactionNo(),
                        decision.message(),
                        now);
                refundRepository.saveAndFlush(refund);
                auditRecorder.record(AuditEvent.builder("commerce", "REFUND_FAILED")
                        .target("REFUND", refund.getId().toString(), order.getCode())
                        .reason(refund.getReason())
                        .after(buildRefundAuditMap(order, payment, refund))
                        .build());
            }
            case PROCESSING -> {
                refund.markProcessing(
                        decision.responseCode(),
                        decision.transactionStatus(),
                        decision.transactionNo(),
                        decision.message(),
                        now);
                refundRepository.saveAndFlush(refund);
            }
            case UNKNOWN -> {
                refund.markUnknown(
                        decision.responseCode(),
                        decision.transactionStatus(),
                        decision.transactionNo(),
                        decision.message(),
                        now);
                refundRepository.saveAndFlush(refund);
            }
        }
        return RefundResponse.from(refund, order, payment);
    }

    private QueryApplyResult applyQueryResultInTx(
            UUID refundId,
            UUID orderId,
            UUID paymentId,
            String actorUsername,
            QueryDecision decision,
            Map<String, Object> rawPayload,
            boolean signatureValid,
            boolean gatewayException) {
        Instant now = clock.instant();
        PurchaseOrder order = purchaseOrderRepository.lockById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
        List<Payment> payments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(order.getId());
        Payment payment = findPayment(payments, paymentId);
        Refund refund = refundRepository.lockById(refundId)
                .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));

        String outcome = buildEventOutcome(
                "REFUND_QUERY", decision.outcome().name(), decision.responseCode(), signatureValid, gatewayException);
        paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), payment.getId(), PaymentEventSource.QUERYDR, rawPayload, signatureValid, now, outcome));

        if (decision.opsAlert()) {
            log.error("VNPAY querydr configuration or merchant error for order {} refund {} code={} message={}",
                    order.getCode(), refund.getId(), decision.responseCode(), decision.message());
        }

        if (refund.getKind() == RefundKind.MANUAL) {
            return new QueryApplyResult(null, true);
        }
        if (refund.getState() == RefundState.FAILED) {
            if (decision.outcome() == QueryOutcome.SUCCEEDED) {
                OrderState prevState = order.getState();
                String reviewReason = "REFUND_QUERY_SUCCEEDED_AFTER_FAILED:" + refund.getId();
                log.error("VNPAY querydr returned SUCCEEDED (00/{}) for already-FAILED refund {} on order {}",
                        decision.transactionStatus(), refund.getId(), order.getCode());
                order.flagNeedsReview(reviewReason, now);
                purchaseOrderRepository.save(order);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        prevState,
                        prevState,
                        actorUsername,
                        reviewReason,
                        now));
                return new QueryApplyResult(RefundResponse.from(refund, order, payment), false);
            }
            return new QueryApplyResult(null, true);
        }

        switch (decision.outcome()) {
            case SUCCEEDED -> {
                if (refund.getState() != RefundState.SUCCEEDED) {
                    refund.markSucceeded(
                            decision.responseCode(),
                            decision.transactionStatus(),
                            decision.transactionNo(),
                            decision.message(),
                            now);
                    refundRepository.saveAndFlush(refund);
                    auditRecorder.record(AuditEvent.builder("commerce", "REFUND_SUCCEEDED")
                            .target("REFUND", refund.getId().toString(), order.getCode())
                            .reason(refund.getReason())
                            .after(buildRefundAuditMap(order, payment, refund))
                            .build());
                    applySucceededRefundEffects(order, payment, refund, actorUsername, now);
                } else {
                    refund.updateGatewayMetadata(
                            decision.responseCode(),
                            decision.transactionStatus(),
                            decision.transactionNo(),
                            decision.message(),
                            now);
                    refundRepository.saveAndFlush(refund);
                }
            }
            case FAILED_REJECTED_09 -> {
                if (refund.getState() != RefundState.FAILED) {
                    OrderState prevState = order.getState();
                    refund.markRejected09(
                            decision.responseCode(),
                            decision.transactionStatus(),
                            decision.transactionNo(),
                            decision.message(),
                            now);
                    refundRepository.saveAndFlush(refund);
                    String reviewReason = "REFUND_REJECTED_09:" + refund.getId();
                    order.flagNeedsReview(reviewReason, now);
                    purchaseOrderRepository.save(order);
                    orderHistoryRepository.save(new OrderHistory(
                            Ids.newId(),
                            order.getId(),
                            prevState,
                            prevState,
                            actorUsername,
                            reviewReason,
                            now));
                    auditRecorder.record(AuditEvent.builder("commerce", "REFUND_FAILED")
                            .target("REFUND", refund.getId().toString(), order.getCode())
                            .reason(reviewReason)
                            .after(buildRefundAuditMap(order, payment, refund))
                            .build());
                }
            }
            case FAILED -> {
                if (refund.getState() == RefundState.SUCCEEDED) {
                    OrderState prevState = order.getState();
                    String reviewReason = "REFUND_SUCCEEDED_QUERY_MISMATCH:"
                            + refund.getId() + ":" + decision.responseCode();
                    log.error("VNPAY querydr returned failure ({}/{}) for already-SUCCEEDED refund {} on order {}",
                            decision.responseCode(), decision.transactionStatus(), refund.getId(), order.getCode());
                    order.flagNeedsReview(reviewReason, now);
                    purchaseOrderRepository.save(order);
                    orderHistoryRepository.save(new OrderHistory(
                            Ids.newId(),
                            order.getId(),
                            prevState,
                            prevState,
                            actorUsername,
                            reviewReason,
                            now));
                } else if (refund.getState() != RefundState.FAILED) {
                    refund.markFailedFromGateway(
                            decision.responseCode(),
                            decision.transactionStatus(),
                            decision.transactionNo(),
                            decision.message(),
                            now);
                    refundRepository.saveAndFlush(refund);
                    auditRecorder.record(AuditEvent.builder("commerce", "REFUND_FAILED")
                            .target("REFUND", refund.getId().toString(), order.getCode())
                            .reason(refund.getReason())
                            .after(buildRefundAuditMap(order, payment, refund))
                            .build());
                }
            }
            case KEEP -> {
                // Giữ nguyên trạng thái hiện tại của refund.
            }
        }
        return new QueryApplyResult(RefundResponse.from(refund, order, payment), false);
    }

    /**
     * Thu hồi lượt chưa dùng và chuyển đơn sang {@code REFUNDED} khi khoản thanh toán được tính đã hoàn đủ
     * (TM 9.4 bước 6, BS 14, C15, E23), đồng thời xếp thư {@code REFUND_DONE} nếu email người mua đã xác minh.
     */
    private void applySucceededRefundEffects(
            PurchaseOrder order,
            Payment payment,
            Refund refund,
            String actorUsername,
            Instant now) {
        if (!payment.isDuplicate()) {
            long totalSucceeded = refundRepository.sumSucceededAmountByPaymentId(payment.getId());
            if (totalSucceeded >= payment.getAmountVnd()) {
                List<GrantRef> grants = grantLookupRepository.findGrantRefsByOrderId(order.getId()).stream()
                        .sorted(Comparator.comparing(GrantRef::examId).thenComparing(GrantRef::id))
                        .toList();
                if (!grants.isEmpty()) {
                    int totalRevokedAttempts = 0;
                    List<String> grantIds = new ArrayList<>(grants.size());
                    boolean anyNewlyRevoked = false;
                    for (GrantRef grant : grants) {
                        if (grant.revokedAt() == null) {
                            anyNewlyRevoked = true;
                        }
                        totalRevokedAttempts += entitlementGrantService.revokeUnused(grant.id());
                        grantIds.add(grant.id().toString());
                    }
                    if (anyNewlyRevoked) {
                        auditRecorder.record(AuditEvent.builder("commerce", "ENTITLEMENT_REVOKED")
                                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                                .reason(refund.getReason())
                                .after(Map.of(
                                        "orderCode", order.getCode(),
                                        "refundId", refund.getId().toString(),
                                        "revokedAttempts", totalRevokedAttempts,
                                        "grantIds", grantIds))
                                .build());
                    }
                }
                if (order.getState() != OrderState.REFUNDED) {
                    OrderState prevState = order.getState();
                    order.markRefunded(now);
                    purchaseOrderRepository.save(order);
                    orderHistoryRepository.save(new OrderHistory(
                            Ids.newId(),
                            order.getId(),
                            prevState,
                            OrderState.REFUNDED,
                            actorUsername,
                            "REFUNDED:" + refund.getId(),
                            now));
                }
            }
        }
        enqueueVerifiedRefundDoneMail(order, refund);
    }

    private void enqueueVerifiedRefundDoneMail(PurchaseOrder order, Refund refund) {
        Optional<MailContact> contactOpt = userDirectory.findMailContact(order.getUserId());
        if (contactOpt.isEmpty()) {
            return;
        }
        MailContact contact = contactOpt.get();
        if (!contact.emailVerified() || !StringUtils.hasText(contact.email())) {
            return;
        }
        String fullName = StringUtils.hasText(contact.fullName()) ? contact.fullName() : order.getBuyerFullName();
        String locale = StringUtils.hasText(contact.preferredLocale()) ? contact.preferredLocale() : "vi";
        String formattedAmount = formatRefundAmount(refund.getAmountVnd(), locale);
        mailQueue.enqueue(
                MailKind.REFUND_DONE,
                contact.email(),
                locale,
                Map.of(
                        "fullName", fullName,
                        "orderCode", order.getCode(),
                        "refundAmount", formattedAmount),
                order.getUserId());
    }

    private RefundResponse loadRefundResponse(UUID refundId) {
        return readOnlyTx.execute(status -> {
            Refund refund = refundRepository.findById(refundId)
                    .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", null));
            PurchaseOrder order = purchaseOrderRepository.findById(refund.getOrderId())
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));
            Payment payment = paymentRepository.findById(refund.getPaymentId())
                    .orElseThrow(() -> new NotFoundException("ORDER_PAYMENT_NOT_FOUND", null));
            return RefundResponse.from(refund, order, payment);
        });
    }

    private static Payment findPayment(List<Payment> payments, UUID paymentId) {
        return payments.stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ORDER_PAYMENT_NOT_FOUND", null));
    }

    private static String formatRefundAmount(long amountVnd, String locale) {
        if ("en".equalsIgnoreCase(locale)) {
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            return nf.format(amountVnd) + " VND";
        }
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(amountVnd) + " ₫";
    }

    private static Map<String, Object> buildRefundAuditMap(PurchaseOrder order, Payment payment, Refund refund) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderCode", order.getCode());
        map.put("paymentId", payment.getId().toString());
        map.put("amountVnd", refund.getAmountVnd());
        map.put("kind", refund.getKind().name());
        map.put("state", refund.getState().name());
        if (refund.getVnpResponseCode() != null) {
            map.put("vnpResponseCode", refund.getVnpResponseCode());
        }
        if (refund.getVnpTransactionStatus() != null) {
            map.put("vnpTransactionStatus", refund.getVnpTransactionStatus());
        }
        if (refund.getVnpTransactionNo() != null) {
            map.put("vnpTransactionNo", refund.getVnpTransactionNo());
        }
        return map;
    }

    private static Map<String, Object> buildRefundRawPayload(
            String requestId, RefundResult result, RefundMappingDecision decision) {
        if (result != null && !result.rawResponse().isEmpty()) {
            return result.rawResponse();
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("vnp_RequestId", requestId);
        if (decision.responseCode() != null) {
            fallback.put("vnp_ResponseCode", decision.responseCode());
        }
        if (decision.transactionStatus() != null) {
            fallback.put("vnp_TransactionStatus", decision.transactionStatus());
        }
        if (decision.message() != null) {
            fallback.put("vnp_Message", decision.message());
        }
        return fallback;
    }

    private static Map<String, Object> buildQueryRawPayload(
            String requestId, QueryDrResult result, QueryDecision decision) {
        if (result != null && !result.rawResponse().isEmpty()) {
            return result.rawResponse();
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("vnp_RequestId", requestId);
        if (decision.responseCode() != null) {
            fallback.put("vnp_ResponseCode", decision.responseCode());
        }
        if (decision.transactionStatus() != null) {
            fallback.put("vnp_TransactionStatus", decision.transactionStatus());
        }
        if (decision.message() != null) {
            fallback.put("vnp_Message", decision.message());
        }
        return fallback;
    }

    private static Map<String, Object> buildExceptionRawPayload(String requestId, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vnp_RequestId", requestId);
        map.put("error", message == null ? "Gateway error" : message);
        return map;
    }

    private static String buildEventOutcome(
            String prefix,
            String outcomeName,
            String responseCode,
            boolean signatureValid,
            boolean gatewayException) {
        if (gatewayException) {
            return prefix + "_GATEWAY_ERROR";
        }
        if (!signatureValid) {
            return prefix + "_INVALID_SIGNATURE";
        }
        return responseCode == null ? prefix + "_" + outcomeName : prefix + "_" + outcomeName + "_" + responseCode;
    }

    private static String extractExceptionMessage(Throwable ex) {
        String msg = ex.getMessage();
        return StringUtils.hasText(msg) ? msg : ex.getClass().getSimpleName();
    }

    private static String normalizeOrderCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private record CreateRefundTx1Outcome(
            UUID refundId,
            RefundKind kind,
            String vnpRequestId,
            String txnRef,
            String vnpTransactionNo,
            String vnpCreateDate,
            long amountVnd,
            long paymentAmountVnd,
            long succeededSumBefore,
            UUID orderId,
            UUID paymentId,
            String orderCode,
            String actorUsername
    ) {
    }

    private record QueryRefundSnapshot(
            UUID refundId,
            UUID orderId,
            String orderCode,
            UUID paymentId,
            String txnRef,
            String vnpCreateDate,
            String vnpTransactionNo
    ) {
    }

    private record QueryApplyResult(RefundResponse response, boolean conflictNotAllowed) {
    }
}
