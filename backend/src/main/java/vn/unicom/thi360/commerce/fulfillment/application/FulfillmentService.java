package vn.unicom.thi360.commerce.fulfillment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrderItem;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderItemRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.MailContact;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;

/**
 * Cấp quyền làm bài cho đơn đã thanh toán trong transaction {@code REQUIRES_NEW} riêng (TM 8.5).
 * Không ném ngoại lệ ra IPN hoặc API đơn miễn phí: lỗi cấp quyền được ghi lại bằng transaction
 * độc lập khác để worker thử lại tự động.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final EntitlementGrantService entitlementGrantService;
    private final PromotionRedemptionService promotionRedemptionService;
    private final RefundProgress refundProgress;
    private final UserDirectory userDirectory;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public FulfillmentService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            OrderHistoryRepository orderHistoryRepository,
            EntitlementGrantService entitlementGrantService,
            PromotionRedemptionService promotionRedemptionService,
            RefundProgress refundProgress,
            UserDirectory userDirectory,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.purchaseOrderItemRepository = Objects.requireNonNull(purchaseOrderItemRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.entitlementGrantService = Objects.requireNonNull(entitlementGrantService);
        this.promotionRedemptionService = Objects.requireNonNull(promotionRedemptionService);
        this.refundProgress = Objects.requireNonNull(refundProgress);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.mailQueue = Objects.requireNonNull(mailQueue);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        TransactionTemplate tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Cấp quyền cho đơn {@code orderId}. Trả {@code true} nếu đơn đã ở trạng thái {@code FULFILLED}
     * (bao gồm cấp mới thành công hoặc idempotent); {@code false} nếu bỏ qua hoặc gặp lỗi đã ghi nhận.
     */
    public boolean fulfill(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(requiresNewTx.execute(status -> fulfillInNewTx(orderId)));
        } catch (RuntimeException ex) {
            String message = extractErrorMessage(ex);
            log.warn("Fulfillment failed for order {}: {}", orderId, message, ex);
            try {
                requiresNewTx.executeWithoutResult(status -> recordFailureInNewTx(orderId, message));
            } catch (RuntimeException recordEx) {
                log.error("Failed to record fulfillment failure for order {}", orderId, recordEx);
            }
            return false;
        }
    }

    private boolean fulfillInNewTx(UUID orderId) {
        PurchaseOrder order = purchaseOrderRepository.lockById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        if (order.getState() == OrderState.FULFILLED) {
            return true;
        }
        if (order.getState() != OrderState.PAID) {
            return false;
        }
        if (refundProgress.hasNonRejectedRefund(orderId)) {
            log.info("Skipping fulfillment for order {} because a non-rejected refund exists", order.getCode());
            return false;
        }

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByOrderIdOrderByPositionAsc(orderId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Order " + order.getCode() + " has no purchase_order_item rows");
        }

        // Thứ tự khóa: purchase_order (đã khóa) -> promotion -> entitlement -> entitlement_grant.
        if (order.getPromotionId() != null || order.getVoucherId() != null) {
            promotionRedemptionService.confirmByOrder(orderId);
        }

        List<GrantLine> lines = items.stream()
                .map(item -> new GrantLine(
                        item.getExamId(),
                        item.getExamVersionNo(),
                        item.getAttemptsGranted(),
                        item.getOptionCodes(),
                        item.getBenefits(),
                        item.getResultView()))
                .toList();
        entitlementGrantService.grantForOrder(
                new GrantOrderCommand(order.getId(), order.getCode(), order.getUserId(), lines));

        Instant now = clock.instant();
        order.markFulfilled(now);
        purchaseOrderRepository.save(order);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                OrderState.PAID,
                OrderState.FULFILLED,
                "SYSTEM",
                "FULFILLED",
                now));

        auditRecorder.record(AuditEvent.builder("commerce", "ENTITLEMENT_GRANTED")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .after(Map.of(
                        "orderCode", order.getCode(),
                        "state", OrderState.FULFILLED.name(),
                        "itemCount", items.size()))
                .build());

        enqueueVerifiedOrderFulfilledMail(order, items);
        return true;
    }

    private void enqueueVerifiedOrderFulfilledMail(PurchaseOrder order, List<PurchaseOrderItem> items) {
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
        List<Map<String, Object>> mailItems = items.stream()
                .map(item -> Map.<String, Object>of("name", item.getExamName()))
                .toList();
        mailQueue.enqueue(
                MailKind.ORDER_FULFILLED,
                contact.email(),
                locale,
                Map.of(
                        "fullName", fullName,
                        "orderCode", order.getCode(),
                        "items", mailItems),
                order.getUserId());
    }

    private void recordFailureInNewTx(UUID orderId, String message) {
        PurchaseOrder order = purchaseOrderRepository.lockById(orderId).orElse(null);
        if (order != null && order.getState() == OrderState.PAID) {
            order.recordFulfillmentFailure(message, clock.instant());
            purchaseOrderRepository.save(order);
        }
    }

    private static String extractErrorMessage(Throwable ex) {
        String msg = ex.getMessage();
        return StringUtils.hasText(msg) ? msg : ex.getClass().getSimpleName();
    }
}
