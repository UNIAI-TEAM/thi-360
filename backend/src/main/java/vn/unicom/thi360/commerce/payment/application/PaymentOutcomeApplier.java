package vn.unicom.thi360.commerce.payment.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
        return txTemplate.execute(status -> applyInTx(paymentId, orderId, vnpParams, rawPayload));
    }

    private ApplyResult applyInTx(
            UUID paymentId,
            UUID orderId,
            Map<String, String> vnpParams,
            Map<String, Object> rawPayload) {
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
            recordEvent(paymentId, PaymentEventSource.IPN, rawPayload, true, now, "ORDER_NOT_FOUND_01");
            return new ApplyResult("01", "Order not Found", null);
        }

        List<Payment> orderPayments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(orderId);
        Payment payment = orderPayments.stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElse(null);
        if (payment == null) {
            recordEvent(null, PaymentEventSource.IPN, rawPayload, true, now, "ORDER_NOT_FOUND_01");
            return new ApplyResult("01", "Order not Found", null);
        }

        PaymentState signal = VnpayCallbackSupport.classifyOutcome(vnpParams);
        String vnpTxnNo = VnpayCallbackSupport.normalizeVnpTransactionNo(vnpParams.get("vnp_TransactionNo"));
        String bankCode = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_BankCode"));
        String cardType = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_CardType"));
        String payDate = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_PayDate"));
        String responseCode = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_ResponseCode"));
        String txnStatus = VnpayCallbackSupport.trimToNull(vnpParams.get("vnp_TransactionStatus"));

        boolean alreadyHasCounted = hasCountedPayment(orderPayments) || isOrderAlreadyPaid(order.getState());

        // 1. Lần thanh toán đã SUCCEEDED -> luôn trả 02 (idempotent).
        if (payment.getState() == PaymentState.SUCCEEDED) {
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }

        // 2. Ca C (BS 14): lần đã FAILED.
        if (payment.getState() == PaymentState.FAILED) {
            return handleOnAlreadyFailedPayment(
                    order, payment, signal, alreadyHasCounted,
                    vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, rawPayload, now);
        }

        // 3. Lần CREATED hoặc UNKNOWN nhận kết quả thất bại hoặc chưa xác định.
        if (signal == PaymentState.FAILED) {
            payment.markFailed(vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
            paymentRepository.save(payment);
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "PAYMENT_FAILED_00");
            return new ApplyResult("00", "Confirm Success", null);
        }
        if (signal == PaymentState.UNKNOWN) {
            payment.markUnknown(vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
            paymentRepository.save(payment);
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "PAYMENT_UNKNOWN_00");
            return new ApplyResult("00", "Confirm Success", null);
        }

        // 4. Lần CREATED hoặc UNKNOWN nhận tín hiệu thành công 00/00.
        // Ca A: đơn đã có khoản được tính hoặc đang PAID/FULFILLED/REFUNDED -> thu thừa (AFTER_COUNTED).
        if (alreadyHasCounted) {
            payment.markExcessSuccess(
                    ExcessReason.AFTER_COUNTED,
                    vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
            paymentRepository.save(payment);
            orderHistoryRepository.save(new OrderHistory(
                    Ids.newId(),
                    order.getId(),
                    order.getState(),
                    order.getState(),
                    "VNPAY_IPN",
                    "EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                    now));
            auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                    .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                    .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                    .reason(ExcessReason.AFTER_COUNTED.name())
                    .after(Map.of("txnRef", payment.getTxnRef(), "amountVnd", payment.getAmountVnd()))
                    .build());
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "EXCESS_AFTER_COUNTED_00");
            return new ApplyResult("00", "Confirm Success", null);
        }

        // Ca B: PENDING_PAYMENT (kể cả quá hạn expires_at theo E12).
        if (order.getState() == OrderState.PENDING_PAYMENT) {
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);
            return acceptCountedPaymentAndMarkPaid(
                    order, orderPayments, payment, paidPredecessors, false,
                    vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, rawPayload, now,
                    "PAYMENT_SUCCEEDED:" + payment.getTxnRef());
        }

        // Ca D1: CANCELLED (hoặc EXPIRED nếu phát sinh) -> thu hồi khuyến mãi (kể cả vượt giới hạn, ghi over_limit_count) và cấp quyền.
        if (order.getState() == OrderState.CANCELLED || order.getState() == OrderState.EXPIRED) {
            if (order.getPromotionId() != null || order.getVoucherId() != null) {
                promotionRedemptionService.reclaimByOrder(order.getId());
            }
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);
            return acceptCountedPaymentAndMarkPaid(
                    order, orderPayments, payment, paidPredecessors, false,
                    vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, rawPayload, now,
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
                    vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, rawPayload, now,
                    reason);
        }

        recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "UNHANDLED_ORDER_STATE_99");
        return new ApplyResult("99", "Unknown error", null);
    }

    private ApplyResult handleOnAlreadyFailedPayment(
            PurchaseOrder order,
            Payment payment,
            PaymentState signal,
            boolean alreadyHasCounted,
            String vnpTxnNo,
            String bankCode,
            String cardType,
            String payDate,
            String responseCode,
            String txnStatus,
            Map<String, Object> rawPayload,
            Instant now) {
        if (signal != PaymentState.SUCCEEDED) {
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }
        if (alreadyHasCounted) {
            if (payment.getLateSuccessReview() == null) {
                // Ca B thuần trên lần đã FAILED: chuyển SUCCEEDED (thu thừa AFTER_COUNTED), giữ nguyên late_success_review = null và lưu đủ tham số VNPAY.
                payment.markExcessSuccess(
                        ExcessReason.AFTER_COUNTED,
                        vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
                paymentRepository.save(payment);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        order.getState(),
                        order.getState(),
                        "VNPAY_IPN",
                        "EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                        now));
                auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                        .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                        .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                        .reason(ExcessReason.AFTER_COUNTED.name())
                        .after(Map.of("txnRef", payment.getTxnRef(), "amountVnd", payment.getAmountVnd()))
                        .build());
                recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "EXCESS_AFTER_COUNTED_00");
                return new ApplyResult("00", "Confirm Success", null);
            }
            if (payment.getLateSuccessReview() == LateSuccessReview.PENDING) {
                payment.markAutoExcessFromPendingReview(
                        vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
                paymentRepository.save(payment);
                orderHistoryRepository.save(new OrderHistory(
                        Ids.newId(),
                        order.getId(),
                        order.getState(),
                        order.getState(),
                        "VNPAY_IPN",
                        "AUTO_EXCESS_AFTER_COUNTED:" + payment.getTxnRef(),
                        now));
                auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                        .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                        .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                        .reason("AUTO_EXCESS")
                        .after(Map.of("txnRef", payment.getTxnRef()))
                        .build());
                recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "AUTO_EXCESS_00");
                return new ApplyResult("00", "Confirm Success", null);
            }
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }
        if (payment.getLateSuccessReview() != null) {
            recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "ALREADY_CONFIRMED_02");
            return new ApplyResult("02", "Order already confirmed", null);
        }
        // Ca C (BS 14): giữ Payment ở FAILED, gắn PENDING để đối soát; không tự chuyển đơn sang PAID và không đặt needs_review trên đơn.
        payment.markLateSuccessPendingReview(vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
        paymentRepository.save(payment);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                order.getState(),
                order.getState(),
                "VNPAY_IPN",
                "LATE_SUCCESS_PENDING_REVIEW:" + payment.getTxnRef(),
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_LATE_SUCCESS_PENDING")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .reason("LATE_SUCCESS_ON_FAILED_PAYMENT")
                .after(Map.of("txnRef", payment.getTxnRef()))
                .build());
        recordEvent(payment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "LATE_SUCCESS_PENDING_00");
        return new ApplyResult("00", "Confirm Success", null);
    }

    private ApplyResult acceptCountedPaymentAndMarkPaid(
            PurchaseOrder order,
            List<Payment> orderPayments,
            Payment countedPayment,
            List<PurchaseOrder> paidPredecessors,
            boolean flagCurrentOrderDoubleCharge,
            String vnpTxnNo,
            String bankCode,
            String cardType,
            String payDate,
            String responseCode,
            String txnStatus,
            Map<String, Object> rawPayload,
            Instant now,
            String historyReason) {
        OrderState previousState = order.getState();
        countedPayment.markCountedSuccess(vnpTxnNo, bankCode, cardType, payDate, responseCode, txnStatus, now);
        paymentRepository.save(countedPayment);

        for (Payment other : orderPayments) {
            if (!other.getId().equals(countedPayment.getId())
                    && other.getLateSuccessReview() == LateSuccessReview.PENDING) {
                other.markAutoExcessFromPendingReview(now);
                paymentRepository.save(other);
            }
        }

        order.markPaid(now);
        if (flagCurrentOrderDoubleCharge) {
            flagSingleOrderDoubleCharge(order, now, countedPayment.getTxnRef());
        } else {
            purchaseOrderRepository.save(order);
        }
        if (!paidPredecessors.isEmpty()) {
            flagPredecessorsDoubleCharge(order, paidPredecessors, now);
        }

        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                previousState,
                OrderState.PAID,
                "VNPAY_IPN",
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
        recordEvent(countedPayment.getId(), PaymentEventSource.IPN, rawPayload, true, now, "PAYMENT_SUCCEEDED_00");
        return new ApplyResult("00", "Confirm Success", order.getId());
    }

    private void flagSingleOrderDoubleCharge(PurchaseOrder target, Instant now, String txnRef) {
        recordDoubleChargeFlag(target, txnRef, Map.of("txnRef", txnRef), now);
    }

    private void flagPredecessorsDoubleCharge(
            PurchaseOrder replacementOrder, List<PurchaseOrder> paidPredecessors, Instant now) {
        for (PurchaseOrder pred : paidPredecessors) {
            recordDoubleChargeFlag(
                    pred, replacementOrder.getCode(), Map.of("replacementOrderCode", replacementOrder.getCode()), now);
        }
    }

    private void recordDoubleChargeFlag(
            PurchaseOrder target, String reasonSuffix, Map<String, Object> auditAfter, Instant now) {
        target.flagNeedsReview(DOUBLE_CHARGE_REASON, now);
        purchaseOrderRepository.save(target);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                target.getId(),
                target.getState(),
                target.getState(),
                "VNPAY_IPN",
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

    private boolean hasPaidReplacement(
            PurchaseOrder replacedOrder, List<UUID> preLockChainIds, List<PurchaseOrder> lockedOrders) {
        if (replacedOrder.getReplacedByOrderId() == null) {
            return false;
        }
        if (!findPaidOrdersByIds(preLockChainIds, replacedOrder.getId(), lockedOrders).isEmpty()) {
            return true;
        }
        // Nếu đơn vừa bị thay thế trong lúc chờ khóa thì kiểm tra trực tiếp chuỗi mới.
        if (!preLockChainIds.contains(replacedOrder.getReplacedByOrderId())) {
            return purchaseOrderRepository.existsPaidInReplacementChainStartingAt(replacedOrder.getReplacedByOrderId());
        }
        return false;
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
        paymentEventRepository.save(new PaymentEvent(
                Ids.newId(), paymentId, source, raw, signatureValid, receivedAt, outcome));
    }
}
