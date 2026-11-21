package vn.unicom.thi360.commerce.order.expiry;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryRepository.LockedOrder;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryRepository.LockedPayment;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrProperties;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrService;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Xử lý hết hạn đơn chờ thanh toán quá {@code expires_at} (TM 8.6, AC-17..AC-19):
 * nhận và tra {@code querydr} các lần thanh toán còn mở ngoài transaction (chia sẻ cooldown 5 phút),
 * rồi khóa theo thứ tự {@code purchase_order -> payment -> promotion} để chuyển {@code EXPIRED}
 * và trả lượt khuyến mãi/voucher khi toàn bộ lần thanh toán đã thất bại dứt điểm.
 */
@Service
public class OrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);
    private static final int MAX_BATCHES = 25;

    private final OrderExpiryRepository orderExpiryRepository;
    private final OrderExpiryProperties orderExpiryProperties;
    private final QueryDrClaimRepository queryDrClaimRepository;
    private final QueryDrProperties queryDrProperties;
    private final QueryDrService queryDrService;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PromotionRedemptionService promotionRedemptionService;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public OrderExpiryService(
            OrderExpiryRepository orderExpiryRepository,
            OrderExpiryProperties orderExpiryProperties,
            QueryDrClaimRepository queryDrClaimRepository,
            QueryDrProperties queryDrProperties,
            QueryDrService queryDrService,
            OrderHistoryRepository orderHistoryRepository,
            PromotionRedemptionService promotionRedemptionService,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.orderExpiryRepository = Objects.requireNonNull(orderExpiryRepository);
        this.orderExpiryProperties = Objects.requireNonNull(orderExpiryProperties);
        this.queryDrClaimRepository = Objects.requireNonNull(queryDrClaimRepository);
        this.queryDrProperties = Objects.requireNonNull(queryDrProperties);
        this.queryDrService = Objects.requireNonNull(queryDrService);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.promotionRedemptionService = Objects.requireNonNull(promotionRedemptionService);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Quét tối đa 25 lô đơn quá hạn, trả về số đơn đã chuyển sang {@code EXPIRED}. */
    public int expirePendingOrders() {
        int expiredCount = 0;
        Set<UUID> visited = new HashSet<>();
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<UUID> orderIds = orderExpiryRepository.findExpiredPendingOrderIds(
                    clock.instant(), visited, orderExpiryProperties.batchSize());
            if (orderIds.isEmpty()) {
                break;
            }
            for (UUID orderId : orderIds) {
                if (!visited.add(orderId)) {
                    continue;
                }
                try {
                    if (processExpiredOrder(orderId)) {
                        expiredCount++;
                    }
                } catch (RuntimeException ex) {
                    log.warn("Unexpected error expiring pending order {}", orderId, ex);
                }
            }
        }
        return expiredCount;
    }

    private boolean processExpiredOrder(UUID orderId) {
        List<ClaimedPayment> claimedPayments = queryDrClaimRepository.claimOpenForOrder(
                orderId, clock.instant(), queryDrProperties.cooldown());
        for (ClaimedPayment claimed : claimedPayments) {
            queryDrService.executeClaimedQuery(claimed);
        }
        return Boolean.TRUE.equals(txTemplate.execute(status -> tryExpireOrderInTx(orderId)));
    }

    private boolean tryExpireOrderInTx(UUID orderId) {
        Instant now = clock.instant();
        LockedOrder order = orderExpiryRepository.lockOrder(orderId);
        if (order == null
                || order.state() != OrderState.PENDING_PAYMENT
                || order.expiresAt() == null
                || order.expiresAt().isAfter(now)) {
            return false;
        }

        List<LockedPayment> payments = orderExpiryRepository.lockPayments(orderId);
        for (LockedPayment payment : payments) {
            if (payment.state() == PaymentState.SUCCEEDED
                    || payment.state() == PaymentState.CREATED
                    || payment.state() == PaymentState.UNKNOWN
                    || payment.lateSuccessReview() == LateSuccessReview.PENDING) {
                return false;
            }
        }

        if (orderExpiryRepository.markExpired(orderId, now) == 0) {
            return false;
        }

        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                orderId,
                OrderState.PENDING_PAYMENT,
                OrderState.EXPIRED,
                "SYSTEM",
                "ORDER_EXPIRED",
                now));

        if (order.promotionId() != null || order.voucherId() != null) {
            promotionRedemptionService.releaseByOrder(orderId);
        }

        auditRecorder.record(AuditEvent.builder("commerce", "ORDER_EXPIRED")
                .actor(null, "SYSTEM", List.of(), AuditActorType.SYSTEM)
                .target("PURCHASE_ORDER", orderId.toString(), order.code())
                .reason("ORDER_EXPIRED")
                .after(Map.of("state", OrderState.EXPIRED.name()))
                .build());
        return true;
    }
}
