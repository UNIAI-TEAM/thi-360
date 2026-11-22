package vn.unicom.thi360.commerce.payment.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.ExcessReason;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Áp dụng kết quả thanh toán VNPAY vào đơn hàng và giao dịch theo đúng ma trận đếm BS §14 (Ca A/B/C/D).
 * Luôn khóa theo thứ tự toàn cục {@code purchase_order (ORDER BY id ASC) -> payment -> promotion} để
 * chống deadlock khi hai đơn thay thế hoặc hai lần thanh toán về đồng thời.
 */
@Service
public class PaymentOutcomeApplier {

    private static final String DOUBLE_CHARGE_REASON = "DOUBLE_CHARGE_REPLACED_ORDER";
    private static final String ACTOR_IPN = "VNPAY_IPN";
    private static final String ACTOR_QUERYDR = "VNPAY_QUERYDR";

    public enum QueryDrOutcomeAction {
        SUCCEEDED,
        FAILED,
        UNKNOWN,
        UNKNOWN_NEEDS_REVIEW,
        KEEP_UNCHANGED
    }

    public record QueryDrMetadata(
            String vnpTransactionNo,
            String bankCode,
            String cardType,
            String payDate,
            String responseCode,
            String transactionStatus) {
    }

    public record ApplyResult(String rspCode, String message, UUID fulfillOrderId) {
    }

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PromotionRedemptionService promotionRedemptionService;
    private final AuditRecorder auditRecorder;
    private final EntityManager entityManager;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public PaymentOutcomeApplier(
            PurchaseOrderRepository purchaseOrderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            PromotionRedemptionService promotionRedemptionService,
            AuditRecorder auditRecorder,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.paymentEventRepository = Objects.requireNonNull(paymentEventRepository);
        this.promotionRedemptionService = Objects.requireNonNull(promotionRedemptionService);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.entityManager = Objects.requireNonNull(entityManager);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    public ApplyResult applyIpnOutcome(
            UUID paymentId,
            UUID orderId,
            Map<String, String> vnpParams,
            Map<String, Object> rawPayload) {
        Map<String, String> params = vnpParams == null ? Map.of() : vnpParams;
        PaymentState signal = VnpayCallbackSupport.classifyOutcome(params);
        QueryDrOutcomeAction action = switch (signal) {
            case SUCCEEDED -> QueryDrOutcomeAction.SUCCEEDED;
            case FAILED -> QueryDrOutcomeAction.FAILED;
            default -> QueryDrOutcomeAction.UNKNOWN;
        };
        QueryDrMetadata metadata = new QueryDrMetadata(
                VnpayCallbackSupport.normalizeVnpTransactionNo(params.get("vnp_TransactionNo")),
                VnpayCallbackSupport.trimToNull(params.get("vnp_BankCode")),
                VnpayCallbackSupport.trimToNull(params.get("vnp_CardType")),
                VnpayCallbackSupport.trimToNull(params.get("vnp_PayDate")),
                VnpayCallbackSupport.trimToNull(params.get("vnp_ResponseCode")),
                VnpayCallbackSupport.trimToNull(params.get("vnp_TransactionStatus")));
        return txTemplate.execute(status -> applyInTx(
                paymentId, orderId, PaymentEventSource.IPN, ACTOR_IPN,
                action, null, null, metadata, rawPayload, true));
    }

    public ApplyResult applyQueryDrOutcome(
            UUID paymentId,
            UUID orderId,
            QueryDrOutcomeAction action,
            String outcomeTag,
            String reviewReason,
            QueryDrMetadata metadata,
            Map<String, Object> rawPayload,
            boolean signatureValid) {
        Objects.requireNonNull(action, "action must not be null");
        if (!signatureValid && action != QueryDrOutcomeAction.KEEP_UNCHANGED) {
            throw new IllegalArgumentException(
                    "Unsigned querydr outcome cannot mutate payment state: action=" + action);
        }
        String effectiveOutcomeTag = StringUtils.hasText(outcomeTag)
                ? outcomeTag.trim()
                : "QUERYDR_" + action.name();
        QueryDrMetadata normalizedMeta = normalizeMetadata(metadata);
        Map<String, Object> sanitizedRaw = sanitizeRawPayload(rawPayload);
        return txTemplate.execute(status -> applyInTx(
                paymentId, orderId, PaymentEventSource.QUERYDR, ACTOR_QUERYDR,
                action, effectiveOutcomeTag, reviewReason, normalizedMeta, sanitizedRaw, signatureValid));
    }

    private ApplyResult applyInTx(
            UUID paymentId,
            UUID orderId,
            PaymentEventSource source,
            String actor,
            QueryDrOutcomeAction action,
            String outcomeTag,
            String reviewReason,
            QueryDrMetadata metadata,
            Map<String, Object> rawPayload,
            boolean signatureValid) {
        Instant now = clock.instant();
        // Xóa sạch L1 cache (phòng OpenEntityManagerInView đã giữ phiên bản cũ) trước khi SELECT FOR UPDATE.
        entityManager.clear();

        // Chỉ đọc scalar UUID để thu thập toàn bộ chuỗi đơn thay thế trước khi khóa tất cả theo thứ tự unsigned id ASC.
        UUID replacedByOrderId = purchaseOrderRepository.findReplacedByOrderIdById(orderId).orElse(null);
        List<UUID> predecessorIds = purchaseOrderRepository.findAllPredecessorOrderIds(orderId);
        List<UUID> replacementChainIds = replacedByOrderId == null
                ? List.of()
                : purchaseOrderRepository.findAllReplacementChainOrderIds(replacedByOrderId);

        Set<UUID> orderIdsToLock = new LinkedHashSet<>();
        orderIdsToLock.add(orderId);
        orderIdsToLock.addAll(predecessorIds);
        orderIdsToLock.addAll(replacementChainIds);

        List<UUID> sortedIds = orderIdsToLock.stream()
                .sorted(PaymentOutcomeApplier::compareUuidUnsigned)
                .toList();
        List<PurchaseOrder> lockedOrders = purchaseOrderRepository.lockAllByIdInOrderByIdAsc(sortedIds);
        PurchaseOrder order = lockedOrders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .orElse(null);
        if (order == null) {
            recordEvent(paymentId, source, rawPayload, signatureValid, now, "ORDER_NOT_FOUND_01");
            return new ApplyResult("01", "Order not Found", null);
        }

        List<Payment> orderPayments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(orderId);
        Payment payment = orderPayments.stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElse(null);
        if (payment == null) {
            recordEvent(null, source, rawPayload, signatureValid, now, "ORDER_NOT_FOUND_01");
            return new ApplyResult("01", "Order not Found", null);
        }

        boolean alreadyHasCounted = hasCountedPayment(orderPayments) || isOrderAlreadyPaid(order.getState());

        // 1. Lần thanh toán đã SUCCEEDED -> luôn trả 02 (idempotent), tuyệt đối không hạ trạng thái (AC-26 / BS 14).
        if (payment.getState() == PaymentState.SUCCEEDED) {
            String eventOutcome = source == PaymentEventSource.IPN
                    ? "ALREADY_CONFIRMED_02"
                    : (action == QueryDrOutcomeAction.SUCCEEDED
                            ? "QUERYDR_ALREADY_CONFIRMED"
                            : "QUERYDR_LATE_IGNORED:" + outcomeTag);
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, eventOutcome);
            return new ApplyResult("02", "Order already confirmed", null);
        }

        // 2. Ca C (BS 14): lần đã FAILED.
        if (payment.getState() == PaymentState.FAILED) {
            return handleOnAlreadyFailedPayment(
                    order, payment, action, alreadyHasCounted,
                    source, actor, outcomeTag, metadata, rawPayload, signatureValid, now);
        }

        // 3. Lần CREATED hoặc UNKNOWN nhận tín hiệu giữ nguyên / thất bại / chưa xác định / cần rà soát.
        if (action == QueryDrOutcomeAction.KEEP_UNCHANGED) {
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, outcomeTag);
            return new ApplyResult("00", "No state change", null);
        }
        if (action == QueryDrOutcomeAction.FAILED) {
            payment.markFailed(
                    metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                    metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
            paymentRepository.save(payment);
            String eventOutcome = source == PaymentEventSource.IPN ? "PAYMENT_FAILED_00" : outcomeTag;
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, eventOutcome);
            return new ApplyResult(
                    "00", source == PaymentEventSource.IPN ? "Confirm Success" : "Marked failed", null);
        }
        if (action == QueryDrOutcomeAction.UNKNOWN) {
            payment.markUnknown(
                    metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                    metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
            paymentRepository.save(payment);
            String eventOutcome = source == PaymentEventSource.IPN ? "PAYMENT_UNKNOWN_00" : outcomeTag;
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, eventOutcome);
            return new ApplyResult(
                    "00", source == PaymentEventSource.IPN ? "Confirm Success" : "Marked unknown", null);
        }
        if (action == QueryDrOutcomeAction.UNKNOWN_NEEDS_REVIEW) {
            payment.markUnknown(
                    metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                    metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
            paymentRepository.save(payment);
            String effectiveReason = StringUtils.hasText(reviewReason) ? reviewReason.trim() : outcomeTag;
            if (!order.isNeedsReview()) {
                order.flagNeedsReview(effectiveReason, now);
                purchaseOrderRepository.save(order);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        order.getState(),
                        order.getState(),
                        actor,
                        effectiveReason + ":" + payment.getTxnRef(),
                        now));
                auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_NEEDS_REVIEW")
                        .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                        .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                        .reason(effectiveReason)
                        .after(Map.of("txnRef", payment.getTxnRef(), "outcome", outcomeTag))
                        .build());
            }
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, outcomeTag);
            return new ApplyResult("00", "Marked unknown needs review", null);
        }

        // 4. Lần CREATED hoặc UNKNOWN nhận tín hiệu thành công 00/00.
        // Ca A: đơn đã có khoản được tính hoặc đang PAID/FULFILLED/REFUNDED -> thu thừa (AFTER_COUNTED).
        if (alreadyHasCounted) {
            payment.markExcessSuccess(
                    ExcessReason.AFTER_COUNTED,
                    metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                    metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
            paymentRepository.save(payment);
            orderHistoryRepository.save(new OrderHistory(
                    Ids.newId(),
                    order.getId(),
                    order.getState(),
                    order.getState(),
                    actor,
                    "EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                    now));
            auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                    .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                    .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                    .reason(ExcessReason.AFTER_COUNTED.name())
                    .after(Map.of("txnRef", payment.getTxnRef(), "amountVnd", payment.getAmountVnd()))
                    .build());
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "EXCESS_AFTER_COUNTED_00");
            return new ApplyResult("00", "Confirm Success", null);
        }

        // Ca B: PENDING_PAYMENT (kể cả quá hạn expires_at theo E12).
        if (order.getState() == OrderState.PENDING_PAYMENT) {
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);
            return acceptCountedPaymentAndMarkPaid(
                    order, orderPayments, payment, paidPredecessors, false,
                    source, actor, metadata, rawPayload, signatureValid, now,
                    "PAYMENT_SUCCEEDED:" + payment.getTxnRef());
        }

        // Ca D1: CANCELLED hoặc EXPIRED -> thu hồi khuyến mãi (kể cả vượt giới hạn, ghi over_limit_count) và cấp quyền.
        if (order.getState() == OrderState.CANCELLED || order.getState() == OrderState.EXPIRED) {
            if (order.getPromotionId() != null || order.getVoucherId() != null) {
                promotionRedemptionService.reclaimByOrder(order.getId());
            }
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);
            return acceptCountedPaymentAndMarkPaid(
                    order, orderPayments, payment, paidPredecessors, false,
                    source, actor, metadata, rawPayload, signatureValid, now,
                    "LATE_PAYMENT_RECLAIMED:" + payment.getTxnRef());
        }

        // Ca D2: REPLACED (TM 8.6, BS 14, AC-27, AC-48) -> vẫn là khoản được tính của đơn REPLACED, thu hồi
        // khuyến mãi (tăng over_limit_count nếu vượt trần) và trả fulfillOrderId để cấp quyền "coi như mua thêm".
        // Chỉ gắn needs_review = true (DOUBLE_CHARGE_REPLACED_ORDER) khi chuỗi thay thế đã có đơn trả tiền.
        if (order.getState() == OrderState.REPLACED) {
            if (order.getPromotionId() != null || order.getVoucherId() != null) {
                promotionRedemptionService.reclaimByOrder(order.getId());
            }
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);
            boolean hasPaidReplacement = hasPaidReplacement(order, replacementChainIds, lockedOrders);
            String reason = hasPaidReplacement
                    ? "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE:" + payment.getTxnRef()
                    : "LATE_PAYMENT_REPLACED_RECLAIMED:" + payment.getTxnRef();
            return acceptCountedPaymentAndMarkPaid(
                    order, orderPayments, payment, paidPredecessors, hasPaidReplacement,
                    source, actor, metadata, rawPayload, signatureValid, now,
                    reason);
        }

        recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "UNHANDLED_ORDER_STATE_99");
        return new ApplyResult("99", "Unknown error", null);
    }

    private ApplyResult handleOnAlreadyFailedPayment(
            PurchaseOrder order,
            Payment payment,
            QueryDrOutcomeAction action,
            boolean alreadyHasCounted,
            PaymentEventSource source,
            String actor,
            String outcomeTag,
            QueryDrMetadata metadata,
            Map<String, Object> rawPayload,
            boolean signatureValid,
            Instant now) {
        if (action != QueryDrOutcomeAction.SUCCEEDED) {
            String eventOutcome = source == PaymentEventSource.IPN
                    ? "ALREADY_CONFIRMED_02"
                    : "QUERYDR_LATE_IGNORED:" + outcomeTag;
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, eventOutcome);
            return new ApplyResult("02", "Order already confirmed", null);
        }
        if (alreadyHasCounted) {
            if (payment.getLateSuccessReview() == null) {
                // Ca B thuần trên lần đã FAILED: chuyển SUCCEEDED (thu thừa AFTER_COUNTED), giữ nguyên late_success_review = null và lưu đủ tham số VNPAY.
                payment.markExcessSuccess(
                        ExcessReason.AFTER_COUNTED,
                        metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                        metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
                paymentRepository.save(payment);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        order.getState(),
                        order.getState(),
                        actor,
                        "EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                        now));
                auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                        .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                        .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                        .reason(ExcessReason.AFTER_COUNTED.name())
                        .after(Map.of("txnRef", payment.getTxnRef(), "amountVnd", payment.getAmountVnd()))
                        .build());
                recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "EXCESS_AFTER_COUNTED_00");
                return new ApplyResult("00", "Confirm Success", null);
            }
            if (payment.getLateSuccessReview() == LateSuccessReview.PENDING) {
                payment.markAutoExcessFromPendingReview(
                        metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                        metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
                paymentRepository.save(payment);
                recordAutoExcessAuditAndHistory(order, payment, actor, now);
                recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "AUTO_EXCESS_00");
                return new ApplyResult("00", "Confirm Success", null);
            }
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }
        if (payment.getLateSuccessReview() != null) {
            recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }
        // Ca C (BS 14): giữ Payment ở FAILED, gắn PENDING để đối soát; không tự chuyển đơn sang PAID và không đặt needs_review trên đơn.
        payment.markLateSuccessPendingReview(
                metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
        paymentRepository.save(payment);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                order.getState(),
                order.getState(),
                actor,
                "LATE_SUCCESS_PENDING_REVIEW:" + payment.getTxnRef(),
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_LATE_SUCCESS_PENDING")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .reason("LATE_SUCCESS_ON_FAILED_PAYMENT")
                .after(Map.of("txnRef", payment.getTxnRef()))
                .build());
        recordEvent(payment.getId(), source, rawPayload, signatureValid, now, "LATE_SUCCESS_PENDING_00");
        return new ApplyResult("00", "Confirm Success", null);
    }

    private ApplyResult acceptCountedPaymentAndMarkPaid(
            PurchaseOrder order,
            List<Payment> orderPayments,
            Payment countedPayment,
            List<PurchaseOrder> paidPredecessors,
            boolean flagCurrentOrderDoubleCharge,
            PaymentEventSource source,
            String actor,
            QueryDrMetadata metadata,
            Map<String, Object> rawPayload,
            boolean signatureValid,
            Instant now,
            String historyReason) {
        OrderState previousState = order.getState();
        countedPayment.markCountedSuccess(
                metadata.vnpTransactionNo(), metadata.bankCode(), metadata.cardType(),
                metadata.payDate(), metadata.responseCode(), metadata.transactionStatus(), now);
        paymentRepository.save(countedPayment);

        for (Payment other : orderPayments) {
            if (!other.getId().equals(countedPayment.getId())
                    && other.getLateSuccessReview() == LateSuccessReview.PENDING) {
                other.markAutoExcessFromPendingReview(now);
                paymentRepository.save(other);
                recordAutoExcessAuditAndHistory(order, other, actor, now);
            }
        }

        order.markPaid(now);
        if (flagCurrentOrderDoubleCharge) {
            flagSingleOrderDoubleCharge(order, actor, now, countedPayment.getTxnRef());
        } else {
            purchaseOrderRepository.save(order);
        }
        if (!paidPredecessors.isEmpty()) {
            flagPredecessorsDoubleCharge(order, paidPredecessors, actor, now);
        }

        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                previousState,
                OrderState.PAID,
                actor,
                historyReason,
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_SUCCEEDED")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .after(Map.of(
                        "txnRef", countedPayment.getTxnRef(),
                        "amountVnd", countedPayment.getAmountVnd(),
                        "state", OrderState.PAID.name()))
                .build());
        recordEvent(countedPayment.getId(), source, rawPayload, signatureValid, now, "PAYMENT_SUCCEEDED_00");
        return new ApplyResult("00", "Confirm Success", order.getId());
    }

    private void flagSingleOrderDoubleCharge(
            PurchaseOrder target, String actor, Instant now, String txnRef) {
        recordDoubleChargeFlag(target, actor, txnRef, Map.of("txnRef", txnRef), now);
    }

    private void flagPredecessorsDoubleCharge(
            PurchaseOrder replacementOrder, List<PurchaseOrder> paidPredecessors, String actor, Instant now) {
        for (PurchaseOrder pred : paidPredecessors) {
            recordDoubleChargeFlag(
                    pred, actor, replacementOrder.getCode(),
                    Map.of("replacementOrderCode", replacementOrder.getCode()), now);
        }
    }

    private void recordDoubleChargeFlag(
            PurchaseOrder target, String actor, String reasonSuffix, Map<String, Object> auditAfter, Instant now) {
        target.flagNeedsReview(DOUBLE_CHARGE_REASON, now);
        purchaseOrderRepository.save(target);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                target.getId(),
                target.getState(),
                target.getState(),
                actor,
                DOUBLE_CHARGE_REASON + ":" + reasonSuffix,
                now));
        auditRecorder.record(AuditEvent.builder("commerce", DOUBLE_CHARGE_REASON)
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", target.getId().toString(), target.getCode())
                .reason(DOUBLE_CHARGE_REASON)
                .after(auditAfter)
                .build());
    }

    private List<PurchaseOrder> findPaidOrdersByIds(
            List<UUID> candidateIds, UUID currentOrderId, List<PurchaseOrder> lockedOrders) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> candidateSet = new LinkedHashSet<>(candidateIds);
        return lockedOrders.stream()
                .filter(o -> !o.getId().equals(currentOrderId)
                        && candidateSet.contains(o.getId())
                        && (o.getPaidAt() != null || isOrderAlreadyPaid(o.getState())))
                .toList();
    }

    private void recordAutoExcessAuditAndHistory(
            PurchaseOrder order, Payment payment, String actor, Instant now) {
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                order.getState(),
                order.getState(),
                actor,
                "AUTO_EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .reason("AUTO_EXCESS")
                .after(Map.of("txnRef", payment.getTxnRef()))
                .build());
    }

    private boolean hasPaidReplacement(
            PurchaseOrder replacedOrder, List<UUID> preLockChainIds, List<PurchaseOrder> lockedOrders) {
        if (replacedOrder.getReplacedByOrderId() == null) {
            return false;
        }
        if (!findPaidOrdersByIds(preLockChainIds, replacedOrder.getId(), lockedOrders).isEmpty()) {
            return true;
        }
        // Duyệt toàn bộ các nút đã khóa từ replacedByOrderId: nếu nút nào vừa mọc thêm nhánh mới ngoài lockedById
        // (vd. O1 -> O2 -> O3 xảy ra song song sau lần đọc trước khi khóa) thì kiểm tra trực tiếp trên DB.
        Map<UUID, PurchaseOrder> lockedById = new LinkedHashMap<>();
        for (PurchaseOrder locked : lockedOrders) {
            lockedById.put(locked.getId(), locked);
        }
        Set<UUID> visited = new LinkedHashSet<>();
        UUID cursor = replacedOrder.getReplacedByOrderId();
        while (cursor != null && visited.add(cursor)) {
            PurchaseOrder node = lockedById.get(cursor);
            if (node == null) {
                return purchaseOrderRepository.existsPaidInReplacementChainStartingAt(cursor);
            }
            if (!node.getId().equals(replacedOrder.getId())
                    && (node.getPaidAt() != null || isOrderAlreadyPaid(node.getState()))) {
                return true;
            }
            cursor = node.getReplacedByOrderId();
        }
        return false;
    }

    public static Map<String, Object> sanitizeRawPayload(Map<?, ?> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawPayload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Object cleanValue = sanitizeRawValue(entry.getValue());
            if (cleanValue != null) {
                clean.put(String.valueOf(entry.getKey()), cleanValue);
            }
        }
        return clean.isEmpty() ? Map.of() : Map.copyOf(clean);
    }

    private static Object sanitizeRawValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return sanitizeRawPayload(nestedMap);
        }
        if (value instanceof List<?> nestedList) {
            List<Object> cleanList = new ArrayList<>(nestedList.size());
            for (Object item : nestedList) {
                Object cleanItem = sanitizeRawValue(item);
                if (cleanItem != null) {
                    cleanList.add(cleanItem);
                }
            }
            return List.copyOf(cleanList);
        }
        return value;
    }

    private static QueryDrMetadata normalizeMetadata(QueryDrMetadata metadata) {
        if (metadata == null) {
            return new QueryDrMetadata(null, null, null, null, null, null);
        }
        return new QueryDrMetadata(
                VnpayCallbackSupport.normalizeVnpTransactionNo(metadata.vnpTransactionNo()),
                VnpayCallbackSupport.trimToNull(metadata.bankCode()),
                VnpayCallbackSupport.trimToNull(metadata.cardType()),
                VnpayCallbackSupport.trimToNull(metadata.payDate()),
                VnpayCallbackSupport.trimToNull(metadata.responseCode()),
                VnpayCallbackSupport.trimToNull(metadata.transactionStatus()));
    }

    private static int compareUuidUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private static boolean hasCountedPayment(List<Payment> payments) {
        return payments.stream()
                .anyMatch(p -> p.getState() == PaymentState.SUCCEEDED && !p.isDuplicate());
    }

    private static boolean isOrderAlreadyPaid(OrderState state) {
        return state == OrderState.PAID || state == OrderState.FULFILLED || state == OrderState.REFUNDED;
    }

    private void recordEvent(
            UUID paymentId,
            PaymentEventSource source,
            Map<String, Object> raw,
            boolean signatureValid,
            Instant receivedAt,
            String outcome) {
        Map<String, Object> safeRaw = sanitizeRawPayload(raw);
        paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), paymentId, source, safeRaw, signatureValid, receivedAt, outcome));
    }
}
