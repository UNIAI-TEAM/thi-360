package vn.unicom.thi360.commerce.admin.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse;
import vn.unicom.thi360.commerce.admin.api.LateSuccessReviewRequest;
import vn.unicom.thi360.commerce.admin.domain.LateSuccessDecision;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminLateSuccessPaymentRepository;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Xử lý đối soát ca tiền về muộn trên lần thanh toán đã ghi thất bại (BS §14 Ca C, AC-32..AC-35, AC-39, AC-40).
 *
 * <p>Sử dụng đúng thứ tự khóa chuỗi đơn thay thế (unsigned UUID ASC) như {@code PaymentOutcomeApplier}
 * và giữ cấp quyền {@link FulfillmentService#fulfill(UUID)} ở transaction riêng sau commit.
 */
@Service
public class LateSuccessReviewService {

    private static final String DOUBLE_CHARGE_REASON = "DOUBLE_CHARGE_REPLACED_ORDER";

    private final AdminOrderQueryRepository queryRepository;
    private final AdminLateSuccessPaymentRepository lateSuccessPaymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final PromotionRedemptionService promotionRedemptionService;
    private final FulfillmentService fulfillmentService;
    private final AdminOrderQueryService queryService;
    private final IdempotentCommand idempotentCommand;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final EntityManager entityManager;
    private final Clock clock;

    public LateSuccessReviewService(
            AdminOrderQueryRepository queryRepository,
            AdminLateSuccessPaymentRepository lateSuccessPaymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PaymentRepository paymentRepository,
            PromotionRedemptionService promotionRedemptionService,
            FulfillmentService fulfillmentService,
            AdminOrderQueryService queryService,
            IdempotentCommand idempotentCommand,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            EntityManager entityManager,
            Clock clock) {
        this.queryRepository = Objects.requireNonNull(queryRepository);
        this.lateSuccessPaymentRepository = Objects.requireNonNull(lateSuccessPaymentRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.promotionRedemptionService = Objects.requireNonNull(promotionRedemptionService);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        this.queryService = Objects.requireNonNull(queryService);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.entityManager = Objects.requireNonNull(entityManager);
        this.clock = Objects.requireNonNull(clock);
    }

    public AdminOrderDetailResponse reviewLateSuccess(
            String orderCode,
            UUID paymentId,
            String idempotencyKey,
            LateSuccessReviewRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String normalizedCode = normalizeCode(orderCode);
        String requiredKey = idempotentCommand.requireKey(idempotencyKey);
        String trimmedReason = request.reason().trim();
        String trimmedEvidence = request.evidenceRef().trim();

        AtomicBoolean freshAccept = new AtomicBoolean(false);
        ReviewOutcome outcome = idempotentCommand.execute(
                requiredKey,
                "payment.lateSuccess",
                normalizedCode + ":" + paymentId,
                request,
                ReviewOutcome.class,
                () -> {
                    ReviewOutcome decided = reviewInTx(
                            normalizedCode, paymentId, actor, request.decision(), trimmedReason, trimmedEvidence);
                    if (decided.accepted()) {
                        freshAccept.set(true);
                    }
                    return decided;
                });

        if (freshAccept.get()) {
            fulfillmentService.fulfill(outcome.orderId());
        }
        return queryService.getOrderDetail(outcome.orderCode());
    }

    private ReviewOutcome reviewInTx(
            String normalizedCode,
            UUID paymentId,
            CurrentUser actor,
            LateSuccessDecision decision,
            String reason,
            String evidenceRef) {
        entityManager.clear();

        UUID orderId = queryRepository.findOrderIdByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));

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
                .sorted(LateSuccessReviewService::compareUuidUnsigned)
                .toList();
        List<PurchaseOrder> lockedOrders = purchaseOrderRepository.lockAllByIdInOrderByIdAsc(sortedIds);
        PurchaseOrder order = lockedOrders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));

        List<Payment> orderPayments = paymentRepository.lockAllByOrderIdOrderByAttemptSeqAsc(orderId);
        Payment payment = orderPayments.stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ORDER_PAYMENT_NOT_FOUND", null));

        if (payment.getLateSuccessReview() != LateSuccessReview.PENDING
                || payment.getState() != PaymentState.FAILED) {
            throw new ConflictException("LATE_SUCCESS_ALREADY_RESOLVED");
        }

        Instant now = clock.instant();
        String txnRef = payment.getTxnRef();
        long amountVnd = payment.getAmountVnd();

        if (decision == LateSuccessDecision.ACCEPT) {
            boolean alreadyHasCounted = hasCountedPayment(orderPayments) || isOrderAlreadyPaid(order.getState());
            if (alreadyHasCounted) {
                throw new ConflictException("LATE_SUCCESS_ALREADY_RESOLVED");
            }

            entityManager.detach(payment);
            int updated = lateSuccessPaymentRepository.acceptPendingLateSuccess(paymentId, now);
            if (updated != 1) {
                throw new ConflictException("LATE_SUCCESS_ALREADY_RESOLVED");
            }

            for (Payment other : orderPayments) {
                if (!other.getId().equals(paymentId)
                        && other.getLateSuccessReview() == LateSuccessReview.PENDING) {
                    other.markAutoExcessFromPendingReview(now);
                    paymentRepository.save(other);
                    orderHistoryRepository.save(new OrderHistory(
                            Ids.newId(),
                            order.getId(),
                            order.getState(),
                            order.getState(),
                            actor.username(),
                            "AUTO_EXCESS_AFTER_COUNTED:" + other.getTxnRef(),
                            now));
                    auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_EXCESS_RECEIVED")
                            .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                            .reason("AUTO_EXCESS")
                            .after(Map.of("txnRef", other.getTxnRef()))
                            .build());
                }
            }

            OrderState prevState = order.getState();
            if ((prevState == OrderState.CANCELLED
                            || prevState == OrderState.EXPIRED
                            || prevState == OrderState.REPLACED)
                    && (order.getPromotionId() != null || order.getVoucherId() != null)) {
                promotionRedemptionService.reclaimByOrder(order.getId());
            }

            boolean flagCurrentDoubleCharge = prevState == OrderState.REPLACED
                    && hasPaidReplacement(order, replacementChainIds, lockedOrders);
            List<PurchaseOrder> paidPredecessors = findPaidOrdersByIds(predecessorIds, orderId, lockedOrders);

            order.markPaid(now);
            if (flagCurrentDoubleCharge) {
                recordDoubleChargeFlag(order, txnRef, Map.of("txnRef", txnRef), now, actor.username());
            } else {
                purchaseOrderRepository.save(order);
            }
            for (PurchaseOrder pred : paidPredecessors) {
                recordDoubleChargeFlag(
                        pred, order.getCode(), Map.of("replacementOrderCode", order.getCode()), now, actor.username());
            }

            orderHistoryRepository.save(new OrderHistory(
                    Ids.newId(),
                    order.getId(),
                    prevState,
                    OrderState.PAID,
                    actor.username(),
                    "LATE_SUCCESS_ACCEPTED:" + txnRef + ":" + evidenceRef,
                    now));
            auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_LATE_SUCCESS_ACCEPTED")
                    .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                    .reason(reason)
                    .after(Map.of(
                            "orderCode", order.getCode(),
                            "paymentId", paymentId.toString(),
                            "txnRef", txnRef,
                            "amountVnd", amountVnd,
                            "evidenceRef", evidenceRef,
                            "decision", LateSuccessDecision.ACCEPT.name()))
                    .build());

            return new ReviewOutcome(order.getId(), order.getCode(), true);
        }

        // REFUND decision: chuyển lần thanh toán thành khoản thu thừa (REJECTED_AFTER_RECONCILE), không đổi đơn.
        entityManager.detach(payment);
        int updated = lateSuccessPaymentRepository.rejectPendingLateSuccessToExcess(paymentId, now);
        if (updated != 1) {
            throw new ConflictException("LATE_SUCCESS_ALREADY_RESOLVED");
        }

        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                order.getState(),
                order.getState(),
                actor.username(),
                "LATE_SUCCESS_REJECTED:" + txnRef + ":" + evidenceRef,
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "PAYMENT_LATE_SUCCESS_REJECTED")
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .reason(reason)
                .after(Map.of(
                        "orderCode", order.getCode(),
                        "paymentId", paymentId.toString(),
                        "txnRef", txnRef,
                        "amountVnd", amountVnd,
                        "evidenceRef", evidenceRef,
                        "decision", LateSuccessDecision.REFUND.name()))
                .build());

        return new ReviewOutcome(order.getId(), order.getCode(), false);
    }

    private void recordDoubleChargeFlag(
            PurchaseOrder target, String reasonSuffix, Map<String, Object> auditAfter, Instant now, String actorName) {
        target.flagNeedsReview(DOUBLE_CHARGE_REASON, now);
        purchaseOrderRepository.save(target);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                target.getId(),
                target.getState(),
                target.getState(),
                actorName,
                DOUBLE_CHARGE_REASON + ":" + reasonSuffix,
                now));
        auditRecorder.record(AuditEvent.builder("commerce", DOUBLE_CHARGE_REASON)
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

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private record ReviewOutcome(UUID orderId, String orderCode, boolean accepted) {
    }
}
