package vn.unicom.thi360.commerce.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.AuditEventItem;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.BuyerInfo;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.GrantView;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.HistoryView;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.OrderItemView;
import vn.unicom.thi360.commerce.admin.api.AdminOrderDetailResponse.PaymentDetailView;
import vn.unicom.thi360.commerce.admin.api.AdminOrderListResponse;
import vn.unicom.thi360.commerce.admin.api.AdminOrderSummary;
import vn.unicom.thi360.commerce.admin.api.ExamBuyerOrderRef;
import vn.unicom.thi360.commerce.admin.api.ExamBuyerRow;
import vn.unicom.thi360.commerce.admin.api.ExamBuyersResponse;
import vn.unicom.thi360.commerce.admin.api.OrderAttentionSummary;
import vn.unicom.thi360.commerce.admin.domain.AdminPaymentStatus;
import vn.unicom.thi360.commerce.admin.domain.AdminRefundStatus;
import vn.unicom.thi360.commerce.admin.domain.OrderAttentionType;
import vn.unicom.thi360.commerce.admin.domain.OrderStatusDeriver;
import vn.unicom.thi360.commerce.admin.domain.OrderStatusDeriver.PaymentSnapshot;
import vn.unicom.thi360.commerce.admin.domain.OrderStatusDeriver.RefundSnapshot;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.BuyerEntitlementCounters;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.BuyerSummaryRow;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.OrderFilter;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.OrderRow;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.PagedBuyerRows;
import vn.unicom.thi360.commerce.admin.infrastructure.AdminOrderQueryRepository.PagedOrderRows;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrderItem;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderItemRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.refund.api.RefundResponse;
import vn.unicom.thi360.commerce.refund.domain.Refund;
import vn.unicom.thi360.commerce.refund.domain.RefundState;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundRepository;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Truy vấn đọc cho danh sách đơn quản trị, dải "Cần xử lý", chi tiết đơn và người mua theo bài thi (TM 9.1–9.3, BS §14).
 */
@Service
public class AdminOrderQueryService {

    private static final Duration UNKNOWN_ATTENTION_AGE = Duration.ofHours(24);
    private static final int DETAIL_AUDIT_LIMIT = 100;

    private final AdminOrderQueryRepository queryRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final AuditQuery auditQuery;
    private final AppMessages messages;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public AdminOrderQueryService(
            AdminOrderQueryRepository queryRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            OrderHistoryRepository orderHistoryRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            AuditQuery auditQuery,
            AppMessages messages,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.queryRepository = Objects.requireNonNull(queryRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.purchaseOrderItemRepository = Objects.requireNonNull(purchaseOrderItemRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.refundRepository = Objects.requireNonNull(refundRepository);
        this.auditQuery = Objects.requireNonNull(auditQuery);
        this.messages = Objects.requireNonNull(messages);
        TransactionTemplate ro = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        ro.setReadOnly(true);
        this.readOnlyTx = ro;
        this.clock = Objects.requireNonNull(clock);
    }

    public AdminOrderListResponse listOrders(OrderFilter filter, PageQuery pageQuery) {
        if (filter != null && filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new ValidationException(List.of(
                    new FieldError("to", "INVALID_TIME_RANGE", messages.get("validation.audit.timeRange.invalid"))));
        }
        Instant unknownThreshold24h = clock.instant().minus(UNKNOWN_ATTENTION_AGE);
        return readOnlyTx.execute(status -> {
            OrderAttentionSummary attention = queryRepository.getAttentionSummary(unknownThreshold24h);
            PagedOrderRows paged = queryRepository.findOrders(filter, unknownThreshold24h, pageQuery);
            if (paged.rows().isEmpty()) {
                return new AdminOrderListResponse(
                        List.of(), pageQuery.page(), pageQuery.size(), 0L, 0, attention);
            }

            List<UUID> orderIds = paged.rows().stream().map(OrderRow::orderId).toList();
            Map<UUID, List<PaymentSnapshot>> paymentsByOrder =
                    queryRepository.findPaymentSnapshotsByOrderIds(orderIds);
            Map<UUID, List<RefundSnapshot>> refundsByOrder =
                    queryRepository.findRefundSnapshotsByOrderIds(orderIds);

            List<AdminOrderSummary> summaries = new ArrayList<>(paged.rows().size());
            for (OrderRow row : paged.rows()) {
                List<PaymentSnapshot> payments = paymentsByOrder.getOrDefault(row.orderId(), List.of());
                List<RefundSnapshot> refunds = refundsByOrder.getOrDefault(row.orderId(), List.of());
                AdminPaymentStatus paymentStatus =
                        OrderStatusDeriver.derivePaymentStatus(row.paymentMethod(), payments);
                AdminRefundStatus refundStatus =
                        OrderStatusDeriver.deriveRefundStatus(payments, refunds);
                List<OrderAttentionType> attentionTypes = OrderStatusDeriver.deriveAttentionTypes(
                        row.orderState(), row.needsReview(), payments, refunds, unknownThreshold24h);
                summaries.add(new AdminOrderSummary(
                        row.orderId(),
                        row.code(),
                        row.buyerUserId(),
                        row.buyerUsername(),
                        row.buyerFullName(),
                        row.productType(),
                        row.examId(),
                        row.productCode(),
                        row.productName(),
                        row.listPriceVnd(),
                        row.amountDueVnd(),
                        row.paymentMethod(),
                        row.orderState(),
                        paymentStatus,
                        refundStatus,
                        row.needsReview(),
                        attentionTypes,
                        row.createdAt(),
                        row.paidAt(),
                        row.fulfilledAt()));
            }
            int totalPages = (int) Math.ceil((double) paged.totalItems() / pageQuery.size());
            return new AdminOrderListResponse(
                    summaries, pageQuery.page(), pageQuery.size(), paged.totalItems(), totalPages, attention);
        });
    }

    public OrderAttentionSummary getAttentionSummary() {
        Instant unknownThreshold24h = clock.instant().minus(UNKNOWN_ATTENTION_AGE);
        return readOnlyTx.execute(status -> queryRepository.getAttentionSummary(unknownThreshold24h));
    }

    public AdminOrderDetailResponse getOrderDetail(String code) {
        String normalizedCode = normalizeCode(code);
        Instant unknownThreshold24h = clock.instant().minus(UNKNOWN_ATTENTION_AGE);
        return readOnlyTx.execute(status -> {
            PurchaseOrder order = purchaseOrderRepository.findByCode(normalizedCode)
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", null));

            String replacedByCode = order.getReplacedByOrderId() == null
                    ? null
                    : purchaseOrderRepository.findById(order.getReplacedByOrderId())
                            .map(PurchaseOrder::getCode)
                            .orElse(null);

            List<PurchaseOrderItem> items =
                    purchaseOrderItemRepository.findByOrderIdOrderByPositionAsc(order.getId());
            List<Payment> payments =
                    paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                            .sorted(java.util.Comparator.comparingInt(Payment::getAttemptSeq))
                            .toList();
            List<Refund> refunds = refundRepository.findByOrderIdOrderByRequestedAtDesc(order.getId());

            Map<UUID, List<Refund>> refundsByPaymentId = new LinkedHashMap<>();
            for (Refund r : refunds) {
                refundsByPaymentId.computeIfAbsent(r.getPaymentId(), ignored -> new ArrayList<>()).add(r);
            }

            List<PaymentSnapshot> paymentSnapshots = new ArrayList<>(payments.size());
            List<PaymentDetailView> paymentViews = new ArrayList<>(payments.size());
            for (Payment p : payments) {
                List<Refund> pRefunds = refundsByPaymentId.getOrDefault(p.getId(), List.of());
                long refundedVnd = 0L;
                long inProgressVnd = 0L;
                for (Refund r : pRefunds) {
                    if (r.getState() == RefundState.SUCCEEDED) {
                        refundedVnd += r.getAmountVnd();
                    } else if (r.getState() == RefundState.PROCESSING || r.getState() == RefundState.UNKNOWN) {
                        inProgressVnd += r.getAmountVnd();
                    }
                }
                long refundableVnd = p.getState() == PaymentState.SUCCEEDED
                        ? Math.max(0L, p.getAmountVnd() - refundedVnd - inProgressVnd)
                        : 0L;

                paymentSnapshots.add(new PaymentSnapshot(
                        p.getState(),
                        p.isDuplicate(),
                        p.getLateSuccessReview(),
                        p.getAmountVnd(),
                        refundedVnd,
                        p.getCreatedAt()));

                List<RefundResponse> refundDtos = pRefunds.stream()
                        .map(r -> RefundResponse.from(r, order, p))
                        .toList();

                paymentViews.add(new PaymentDetailView(
                        p.getId(),
                        p.getAttemptSeq(),
                        p.getTxnRef(),
                        p.getVnpTransactionNo(),
                        p.getBankCode(),
                        p.getCardType(),
                        p.getPayDate(),
                        p.getAmountVnd(),
                        p.getState(),
                        p.isDuplicate(),
                        p.getExcessReason(),
                        p.getLateSuccessReview(),
                        p.getLateSuccessAt(),
                        p.getResponseCode(),
                        p.getTransactionStatus(),
                        p.getCreatedAt(),
                        refundedVnd,
                        inProgressVnd,
                        refundableVnd,
                        refundDtos));
            }

            List<RefundSnapshot> refundSnapshots = refunds.stream()
                    .map(r -> new RefundSnapshot(r.getState(), r.getAmountVnd()))
                    .toList();

            AdminPaymentStatus paymentStatus =
                    OrderStatusDeriver.derivePaymentStatus(order.getPaymentMethod(), paymentSnapshots);
            AdminRefundStatus refundStatus =
                    OrderStatusDeriver.deriveRefundStatus(paymentSnapshots, refundSnapshots);
            List<OrderAttentionType> attentionTypes = OrderStatusDeriver.deriveAttentionTypes(
                    order.getState(), order.isNeedsReview(), paymentSnapshots, refundSnapshots, unknownThreshold24h);

            List<OrderItemView> itemViews = items.stream()
                    .map(it -> new OrderItemView(
                            it.getId(),
                            it.getExamId(),
                            it.getExamVersionNo(),
                            it.getExamCode(),
                            it.getExamName(),
                            it.getOptionCodes(),
                            it.getAttemptsGranted(),
                            it.getBenefits(),
                            it.getResultView(),
                            it.getPosition()))
                    .toList();

            List<GrantView> grants = queryRepository.findGrantsByOrderId(order.getId());

            List<HistoryView> orderHistory = orderHistoryRepository.findByOrderIdOrderByAtAsc(order.getId()).stream()
                    .map(h -> new HistoryView(
                            h.getId(), h.getFromState(), h.getToState(), h.getActor(), h.getReason(), h.getAt()))
                    .toList();

            List<AuditEventView> auditViews = auditQuery.findByTarget(
                            "PURCHASE_ORDER",
                            order.getId().toString(),
                            PageQuery.of(0, DETAIL_AUDIT_LIMIT, null, Set.of(), Sort.by(Sort.Direction.ASC, "occurredAt")))
                    .items();
            List<AuditEventItem> auditEvents = auditViews.stream().map(AuditEventItem::from).toList();

            return new AdminOrderDetailResponse(
                    order.getId(),
                    order.getCode(),
                    new BuyerInfo(order.getUserId(), order.getBuyerUsername(), order.getBuyerFullName()),
                    order.getProductType(),
                    order.getExamId(),
                    order.getExamVersionNo(),
                    order.getBundleId(),
                    order.getBundleVersionNo(),
                    order.getProductCode(),
                    order.getProductName(),
                    order.getOptionCodes(),
                    order.getListPriceVnd(),
                    order.getPromotionId(),
                    order.getPromotionDiscountVnd(),
                    order.getVoucherId(),
                    order.getVoucherCode(),
                    order.getVoucherDiscountVnd(),
                    order.getDiscountApplied(),
                    order.getAmountDueVnd(),
                    order.getAttemptsGranted(),
                    order.getBenefits(),
                    order.getResultView(),
                    order.getPaymentMethod(),
                    order.getState(),
                    paymentStatus,
                    refundStatus,
                    order.getReplacedByOrderId(),
                    replacedByCode,
                    order.isNeedsReview(),
                    order.getReviewReason(),
                    attentionTypes,
                    order.getExpiresAt(),
                    order.getPaidAt(),
                    order.getFulfilledAt(),
                    order.getFulfillmentError(),
                    order.getFulfillmentTries(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    itemViews,
                    paymentViews,
                    grants,
                    orderHistory,
                    auditEvents);
        });
    }

    public ExamBuyersResponse listExamBuyers(UUID examId, PageQuery pageQuery) {
        return readOnlyTx.execute(status -> {
            PagedBuyerRows paged = queryRepository.findExamBuyers(examId, pageQuery);
            if (paged.rows().isEmpty()) {
                return new ExamBuyersResponse(List.of(), pageQuery.page(), pageQuery.size(), 0L, 0, 0L);
            }
            List<UUID> userIds = paged.rows().stream().map(BuyerSummaryRow::userId).toList();
            Map<UUID, List<ExamBuyerOrderRef>> ordersByUser =
                    queryRepository.findQualifyingOrdersByExamAndUsers(examId, userIds);
            Map<UUID, BuyerEntitlementCounters> countersByUser =
                    queryRepository.findPurchaseEntitlementsByExamAndUsers(examId, userIds);

            List<ExamBuyerRow> items = new ArrayList<>(paged.rows().size());
            for (BuyerSummaryRow row : paged.rows()) {
                BuyerEntitlementCounters counters =
                        countersByUser.getOrDefault(row.userId(), BuyerEntitlementCounters.zero());
                items.add(new ExamBuyerRow(
                        row.userId(),
                        row.username(),
                        row.fullName(),
                        row.purchaseCount(),
                        ordersByUser.getOrDefault(row.userId(), List.of()),
                        row.lastPaidAt(),
                        counters.attemptsTotal(),
                        counters.attemptsUsed(),
                        counters.attemptsReserved(),
                        counters.attemptsRevoked(),
                        counters.attemptsRemaining()));
            }
            int totalPages = (int) Math.ceil((double) paged.totalItems() / pageQuery.size());
            return new ExamBuyersResponse(
                    items, pageQuery.page(), pageQuery.size(), paged.totalItems(), totalPages, paged.totalItems());
        });
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
