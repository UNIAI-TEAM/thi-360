package vn.unicom.thi360.commerce.order.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamLearnerGate;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderHistory;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrder;
import vn.unicom.thi360.commerce.order.domain.PurchaseOrderItem;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderItemRepository;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.order.infrastructure.StorefrontProductLockRepository;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteRequest;
import vn.unicom.thi360.commerce.pricing.PriceQuote;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.ReserveRequest;
import vn.unicom.thi360.commerce.storefront.application.ProductDiscontinuedException;
import vn.unicom.thi360.commerce.storefront.application.StorefrontErrorCodes;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.MailContact;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Nghiệp vụ đơn hàng của học viên (TM 8.3, E12, E13, E14, E22, E25, E26).
 */
@Service
public class OrderService {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Duration VNPAY_PAYMENT_WINDOW = Duration.ofMinutes(15);
    private static final String DEFAULT_RESULT_VIEW = "FULL_SOLUTION";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ExamLearnerGate learnerGate;
    private final StorefrontProductLockRepository storefrontLockRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final PriceCalculator priceCalculator;
    private final PromotionRedemptionService promotionRedemptionService;
    private final FulfillmentService fulfillmentService;
    private final VnpayGateway vnpayGateway;
    private final OrderCodeGenerator codeGenerator;
    private final UserDirectory userDirectory;
    private final IdempotentCommand idempotentCommand;
    private final AuditRecorder auditRecorder;
    private final AppMessages messages;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final Clock clock;
    private final Duration orderExpireDuration;
    private final String webUrl;

    public OrderService(
            ExamLearnerGate learnerGate,
            StorefrontProductLockRepository storefrontLockRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            OrderHistoryRepository orderHistoryRepository,
            PaymentRepository paymentRepository,
            PriceCalculator priceCalculator,
            PromotionRedemptionService promotionRedemptionService,
            FulfillmentService fulfillmentService,
            VnpayGateway vnpayGateway,
            OrderCodeGenerator codeGenerator,
            UserDirectory userDirectory,
            IdempotentCommand idempotentCommand,
            AuditRecorder auditRecorder,
            AppMessages messages,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${thi360.commerce.order.expire-duration:PT15M}") Duration orderExpireDuration,
            @Value("${thi360.web-url:http://localhost:3000}") String webUrl) {
        this.learnerGate = Objects.requireNonNull(learnerGate);
        this.storefrontLockRepository = Objects.requireNonNull(storefrontLockRepository);
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.purchaseOrderItemRepository = Objects.requireNonNull(purchaseOrderItemRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.priceCalculator = Objects.requireNonNull(priceCalculator);
        this.promotionRedemptionService = Objects.requireNonNull(promotionRedemptionService);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.messages = Objects.requireNonNull(messages);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
        this.orderExpireDuration = Objects.requireNonNull(orderExpireDuration);
        this.webUrl = normalizeWebUrl(webUrl);
    }

    public CreateOrderResponse createOrder(
            String idempotencyKey,
            CreateOrderRequest request,
            String clientIp) {
        UUID userId = learnerGate.requireLearner();
        if (!userId.equals(request.expectedAccountId())) {
            throw new ConflictException("ACCOUNT_CHANGED", messages.get("ACCOUNT_CHANGED"));
        }

        String normalizedIp = normalizeIp(clientIp);
        AtomicBoolean freshlyCreated = new AtomicBoolean(false);
        CreateOrderOutcome outcome;
        try {
            outcome = idempotentCommand.execute(
                    idempotencyKey,
                    "order.create",
                    request,
                    CreateOrderOutcome.class,
                    () -> {
                        CreateOrderOutcome created = createOrderInTx(userId, request, normalizedIp);
                        freshlyCreated.set(true);
                        return created;
                    });
        } catch (AutoPromotionStaleException ex) {
            throw new OrderPriceChangedException(
                    recalculateFreshQuote(userId, ex.productCode, ex.voucherCode));
        }

        if (!outcome.freeOrder()) {
            return outcome.response();
        }

        // Đơn miễn phí (amount_due == 0): chỉ tự động gọi fulfill khi đơn vừa được tạo mới;
        // replay idempotency chỉ đọc lại trạng thái mới nhất để tính `next`.
        boolean fulfilled = freshlyCreated.get() && fulfillmentService.fulfill(outcome.orderId());
        OrderDetail fresh = txTemplate.execute(status -> purchaseOrderRepository.findById(outcome.orderId())
                .map(this::buildOrderDetail)
                .orElse(outcome.response().order()));
        String next = (fulfilled || (fresh != null && fresh.state() == OrderState.FULFILLED))
                ? "FULFILLED"
                : "CHECKING";
        return new CreateOrderResponse(fresh, next, null);
    }

    public PageResponse<OrderSummary> listMyOrders(PageQuery pageQuery) {
        UUID userId = learnerGate.requireLearner();
        Page<OrderSummary> page = txTemplate.execute(status -> {
            Page<PurchaseOrder> raw = purchaseOrderRepository.findByUserId(
                    userId, pageQuery.toPageable());
            return raw.map(OrderService::toSummary);
        });
        return PageResponse.from(page);
    }

    public OrderDetail getMyOrder(String code) {
        UUID userId = learnerGate.requireLearner();
        String normalizedCode = normalizeOrderCode(code);
        return txTemplate.execute(status -> {
            PurchaseOrder order = purchaseOrderRepository.findByCodeAndUserId(normalizedCode, userId)
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", messages.get("ORDER_NOT_FOUND")));
            return buildOrderDetail(order);
        });
    }

    public PayOrderResponse payOrder(String code, String idempotencyKey, String clientIp) {
        UUID userId = learnerGate.requireLearner();
        String normalizedCode = normalizeOrderCode(code);
        String normalizedIp = normalizeIp(clientIp);
        if (StringUtils.hasText(idempotencyKey)) {
            return idempotentCommand.execute(
                    idempotencyKey,
                    "order.pay",
                    normalizedCode,
                    Map.of("code", normalizedCode),
                    PayOrderResponse.class,
                    () -> payOrderInTx(userId, normalizedCode, normalizedIp));
        }
        return txTemplate.execute(status -> payOrderInTx(userId, normalizedCode, normalizedIp));
    }

    public OrderDetail cancelOrder(String code, String idempotencyKey) {
        UUID userId = learnerGate.requireLearner();
        String normalizedCode = normalizeOrderCode(code);
        if (StringUtils.hasText(idempotencyKey)) {
            return idempotentCommand.execute(
                    idempotencyKey,
                    "order.cancel",
                    normalizedCode,
                    Map.of("code", normalizedCode),
                    OrderDetail.class,
                    () -> cancelOrderInTx(userId, normalizedCode));
        }
        return txTemplate.execute(status -> cancelOrderInTx(userId, normalizedCode));
    }

    private CreateOrderOutcome createOrderInTx(UUID userId, CreateOrderRequest request, String clientIp) {
        Instant now = clock.instant();
        StoredProduct stored = requireOnSaleProduct(
                storefrontLockRepository.lockByNormalizedCode(StorefrontSearchText.normalize(request.code()))
                        .orElse(null));

        PurchaseOrder existingPending = purchaseOrderRepository
                .lockPendingByUserAndProduct(userId, stored.code())
                .orElse(null);
        UUID existingPendingId = existingPending == null ? null : existingPending.getId();

        String normalizedVoucher = trimToNull(request.voucherCode());
        PricedProduct pricedProduct = new PricedProduct(
                stored.examId(), stored.examKindItemId(), stored.listPriceVnd());
        QuoteCalculation calc = priceCalculator.calculateQuote(
                new QuoteRequest(pricedProduct, userId, normalizedVoucher, existingPendingId, now));

        if (!Integer.valueOf(stored.versionNo()).equals(request.expectedVersionNo())
                || !Long.valueOf(calc.finalPrice()).equals(request.expectedAmountDue())) {
            throw new OrderPriceChangedException(PriceQuote.from(stored, calc, true));
        }

        DiscountSnapshot discounts = resolveDiscounts(calc);

        if (existingPending != null && canReusePendingOrder(existingPending, stored, calc, discounts, now)) {
            Payment payment = createNextPayment(existingPending, clientIp, now);
            String paymentUrl = buildPaymentUrl(existingPending, payment, resolveUserLocale(userId), clientIp, now);
            orderHistoryRepository.save(new OrderHistory(
                    Ids.newId(),
                    existingPending.getId(),
                    OrderState.PENDING_PAYMENT,
                    OrderState.PENDING_PAYMENT,
                    "LEARNER",
                    "REUSE_PENDING_PAYMENT:" + payment.getTxnRef(),
                    now));
            auditRecorder.record(AuditEvent.builder("commerce", "ORDER_PAYMENT_CREATED")
                    .target("PURCHASE_ORDER", existingPending.getId().toString(), existingPending.getCode())
                    .after(Map.of("txnRef", payment.getTxnRef(), "attemptSeq", payment.getAttemptSeq(), "reused", true))
                    .build());
            OrderDetail detail = buildOrderDetail(existingPending);
            return new CreateOrderOutcome(
                    existingPending.getId(),
                    false,
                    new CreateOrderResponse(detail, "REDIRECT", paymentUrl));
        }

        // Khóa toàn bộ khuyến mãi cũ và mới theo thứ tự UUID tăng dần toàn cục trước khi thay thế đơn cũ
        // hoặc chèn purchase_order mới (FK promotion_id/voucher_id lấy FOR KEY SHARE trên promotion; khóa FOR UPDATE
        // trước tránh deadlock nâng khóa FOR KEY SHARE -> FOR UPDATE khi nhiều sản phẩm dùng chung khuyến mãi).
        Set<UUID> promotionIdsToLock = new LinkedHashSet<>();
        if (existingPending != null) {
            addIfNonNull(promotionIdsToLock, existingPending.getPromotionId());
            addIfNonNull(promotionIdsToLock, existingPending.getVoucherId());
        }
        addIfNonNull(promotionIdsToLock, discounts.promotionId());
        addIfNonNull(promotionIdsToLock, discounts.voucherId());
        if (!promotionIdsToLock.isEmpty()) {
            storefrontLockRepository.lockPromotionsInOrder(promotionIdsToLock);
        }

        if (existingPending != null) {
            // Bước 1: chuyển state sang REPLACED và flush trước khi chèn đơn mới để giải phóng unique index đơn chờ.
            existingPending.beginReplace(now);
            purchaseOrderRepository.saveAndFlush(existingPending);
            if (existingPending.getPromotionId() != null || existingPending.getVoucherId() != null) {
                promotionRedemptionService.releaseByOrder(existingPending.getId());
            }
        }

        UserSummary buyer = userDirectory.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated buyer missing in identity directory: " + userId));
        ProductDetailSnapshot detailSnapshot = parseDetailSnapshot(stored.detail());

        UUID newOrderId = Ids.newId();
        String newOrderCode = codeGenerator.nextOrderCode();
        boolean isFree = calc.finalPrice() == 0L;

        PurchaseOrder newOrder = isFree
                ? PurchaseOrder.createFreeExamOrder(
                        newOrderId,
                        newOrderCode,
                        userId,
                        buyer.username(),
                        buyer.fullName(),
                        stored.examId(),
                        stored.versionNo(),
                        detailSnapshot.optionCodes(),
                        stored.code(),
                        stored.name(),
                        stored.listPriceVnd(),
                        discounts.promotionId(),
                        discounts.promotionDiscountVnd(),
                        discounts.voucherId(),
                        discounts.voucherCode(),
                        discounts.voucherDiscountVnd(),
                        calc.discountApplied(),
                        stored.attemptsGranted(),
                        detailSnapshot.benefits(),
                        detailSnapshot.resultView(),
                        now)
                : PurchaseOrder.createPendingExamOrder(
                        newOrderId,
                        newOrderCode,
                        userId,
                        buyer.username(),
                        buyer.fullName(),
                        stored.examId(),
                        stored.versionNo(),
                        detailSnapshot.optionCodes(),
                        stored.code(),
                        stored.name(),
                        stored.listPriceVnd(),
                        discounts.promotionId(),
                        discounts.promotionDiscountVnd(),
                        discounts.voucherId(),
                        discounts.voucherCode(),
                        discounts.voucherDiscountVnd(),
                        calc.discountApplied(),
                        calc.finalPrice(),
                        stored.attemptsGranted(),
                        detailSnapshot.benefits(),
                        detailSnapshot.resultView(),
                        now.plus(orderExpireDuration),
                        now);

        purchaseOrderRepository.saveAndFlush(newOrder);

        if (existingPending != null) {
            existingPending.linkReplacement(newOrderId, now);
            purchaseOrderRepository.saveAndFlush(existingPending);
            orderHistoryRepository.save(new OrderHistory(
                    Ids.newId(),
                    existingPending.getId(),
                    OrderState.PENDING_PAYMENT,
                    OrderState.REPLACED,
                    "LEARNER",
                    "REPLACED_BY:" + newOrderCode,
                    now));
            auditRecorder.record(AuditEvent.builder("commerce", "ORDER_REPLACED")
                    .target("PURCHASE_ORDER", existingPending.getId().toString(), existingPending.getCode())
                    .after(Map.of("replacedByOrderCode", newOrderCode))
                    .build());
        }

        purchaseOrderItemRepository.saveAndFlush(new PurchaseOrderItem(
                Ids.newId(),
                newOrderId,
                stored.examId(),
                stored.versionNo(),
                stored.code(),
                stored.name(),
                detailSnapshot.optionCodes(),
                stored.attemptsGranted(),
                detailSnapshot.benefits(),
                detailSnapshot.resultView(),
                1));

        List<ReserveRequest> reserveRequests = buildReserveRequests(discounts);
        if (!reserveRequests.isEmpty()) {
            try {
                promotionRedemptionService.reserveForOrder(userId, newOrderId, newOrderCode, reserveRequests);
            } catch (BusinessRuleException ex) {
                if (discounts.voucherId() == null) {
                    throw new AutoPromotionStaleException(stored.code(), normalizedVoucher, ex);
                }
                throw ex;
            }
        }

        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                newOrderId,
                null,
                newOrder.getState(),
                "LEARNER",
                isFree ? "FREE_ORDER_PAID" : "ORDER_CREATED",
                now));

        auditRecorder.record(AuditEvent.builder("commerce", "ORDER_CREATED")
                .target("PURCHASE_ORDER", newOrderId.toString(), newOrderCode)
                .after(Map.of(
                        "productCode", stored.code(),
                        "amountDueVnd", newOrder.getAmountDueVnd(),
                        "paymentMethod", newOrder.getPaymentMethod().name(),
                        "state", newOrder.getState().name()))
                .build());

        if (isFree) {
            OrderDetail detail = buildOrderDetail(newOrder);
            return new CreateOrderOutcome(newOrderId, true, new CreateOrderResponse(detail, "CHECKING", null));
        }

        Payment payment = createNextPayment(newOrder, clientIp, now);
        String paymentUrl = buildPaymentUrl(newOrder, payment, resolveUserLocale(userId), clientIp, now);
        OrderDetail detail = buildOrderDetail(newOrder);
        return new CreateOrderOutcome(newOrderId, false, new CreateOrderResponse(detail, "REDIRECT", paymentUrl));
    }

    private PayOrderResponse payOrderInTx(UUID userId, String normalizedCode, String clientIp) {
        Instant now = clock.instant();
        PurchaseOrder order = purchaseOrderRepository.lockByCode(normalizedCode).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new NotFoundException("ORDER_NOT_FOUND", messages.get("ORDER_NOT_FOUND"));
        }
        if (order.getState() != OrderState.PENDING_PAYMENT || order.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ConflictException("ORDER_NOT_PENDING", messages.get("ORDER_NOT_PENDING"));
        }
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(now)) {
            throw new ConflictException("ORDER_EXPIRED", messages.get("ORDER_EXPIRED"));
        }
        requireOnSaleProduct(
                storefrontLockRepository.findByNormalizedCode(StorefrontSearchText.normalize(order.getProductCode()))
                        .orElse(null));

        // TM 8.3: Không tính lại giá và không kiểm tra lại hạn khuyến mãi trên đơn đã chốt.
        Payment payment = createNextPayment(order, clientIp, now);
        String paymentUrl = buildPaymentUrl(order, payment, resolveUserLocale(userId), clientIp, now);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                OrderState.PENDING_PAYMENT,
                OrderState.PENDING_PAYMENT,
                "LEARNER",
                "PAYMENT_ATTEMPT_CREATED:" + payment.getTxnRef(),
                now));
        auditRecorder.record(AuditEvent.builder("commerce", "ORDER_PAYMENT_CREATED")
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .after(Map.of("txnRef", payment.getTxnRef(), "attemptSeq", payment.getAttemptSeq(), "reused", false))
                .build());
        OrderDetail detail = buildOrderDetail(order);
        return new PayOrderResponse(detail, "REDIRECT", paymentUrl, payment.getTxnRef(), order.getExpiresAt());
    }

    private OrderDetail cancelOrderInTx(UUID userId, String normalizedCode) {
        Instant now = clock.instant();
        PurchaseOrder order = purchaseOrderRepository.lockByCode(normalizedCode).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new NotFoundException("ORDER_NOT_FOUND", messages.get("ORDER_NOT_FOUND"));
        }
        if (order.getState() != OrderState.PENDING_PAYMENT) {
            throw new ConflictException("ORDER_NOT_PENDING", messages.get("ORDER_NOT_PENDING"));
        }

        order.markCancelled(now);
        purchaseOrderRepository.save(order);
        orderHistoryRepository.save(new OrderHistory(
                Ids.newId(),
                order.getId(),
                OrderState.PENDING_PAYMENT,
                OrderState.CANCELLED,
                "LEARNER",
                "CANCELLED_BY_USER",
                now));
        if (order.getPromotionId() != null || order.getVoucherId() != null) {
            promotionRedemptionService.releaseByOrder(order.getId());
        }
        auditRecorder.record(AuditEvent.builder("commerce", "ORDER_CANCELLED")
                .target("PURCHASE_ORDER", order.getId().toString(), order.getCode())
                .after(Map.of("state", OrderState.CANCELLED.name()))
                .build());
        return buildOrderDetail(order);
    }

    private Payment createNextPayment(PurchaseOrder order, String clientIp, Instant now) {
        int nextSeq = paymentRepository.findMaxAttemptSeqByOrderId(order.getId()) + 1;
        if (nextSeq > OrderCodeGenerator.MAX_ATTEMPT_SEQ) {
            throw new BusinessRuleException("PAYMENT_ATTEMPTS_EXCEEDED");
        }
        String txnRef = codeGenerator.txnRef(order.getCode(), nextSeq);
        Instant payWindowEnd = now.plus(VNPAY_PAYMENT_WINDOW);
        Instant effectiveExpire = (order.getExpiresAt() != null && order.getExpiresAt().isBefore(payWindowEnd))
                ? order.getExpiresAt()
                : payWindowEnd;
        String createDate = VNP_TIME_FMT.format(now.atZone(GMT_PLUS_7));
        String expireDate = VNP_TIME_FMT.format(effectiveExpire.atZone(GMT_PLUS_7));
        Payment payment = new Payment(
                Ids.newId(),
                order.getId(),
                nextSeq,
                txnRef,
                createDate,
                expireDate,
                order.getAmountDueVnd(),
                now);
        return paymentRepository.saveAndFlush(payment);
    }

    private String buildPaymentUrl(
            PurchaseOrder order, Payment payment, String locale, String clientIp, Instant now) {
        return vnpayGateway.pay(new PayRequest(
                payment.getTxnRef(),
                order.getCode(),
                payment.getAmountVnd(),
                "Thanh toan don " + order.getCode(),
                locale,
                webUrl + "/checkout/result",
                clientIp,
                payment.getVnpCreateDate(),
                payment.getVnpExpireDate()));
    }

    private String resolveUserLocale(UUID userId) {
        return userDirectory.findMailContact(userId)
                .map(MailContact::preferredLocale)
                .filter(StringUtils::hasText)
                .map(locale -> "en".equalsIgnoreCase(locale.trim()) ? "en" : "vn")
                .orElse("vn");
    }

    private PriceQuote recalculateFreshQuote(UUID userId, String productCode, String voucherCode) {
        return txTemplate.execute(status -> {
            Instant now = clock.instant();
            String normalizedProductCode = StorefrontSearchText.normalize(productCode);
            StoredProduct stored = requireOnSaleProduct(
                    storefrontLockRepository.findByNormalizedCode(normalizedProductCode).orElse(null));
            UUID survivingPendingId = purchaseOrderRepository
                    .findPendingPaymentOrderId(userId, normalizedProductCode)
                    .orElse(null);
            PricedProduct pricedProduct = new PricedProduct(
                    stored.examId(), stored.examKindItemId(), stored.listPriceVnd());
            QuoteCalculation freshCalc = priceCalculator.calculateQuote(
                    new QuoteRequest(pricedProduct, userId, voucherCode, survivingPendingId, now));
            return PriceQuote.from(stored, freshCalc, true);
        });
    }

    private static void addIfNonNull(Set<UUID> target, UUID id) {
        if (id != null) {
            target.add(id);
        }
    }

    private static StoredProduct requireOnSaleProduct(StoredProduct stored) {
        if (stored == null || stored.saleState() == SaleState.UNAVAILABLE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        if (stored.saleState() == SaleState.DISCONTINUED) {
            throw new ProductDiscontinuedException(stored.code(), stored.name());
        }
        if (stored.saleState() != SaleState.ON_SALE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        return stored;
    }

    private static boolean canReusePendingOrder(
            PurchaseOrder existing,
            StoredProduct stored,
            QuoteCalculation calc,
            DiscountSnapshot discounts,
            Instant now) {
        if (existing.getState() != OrderState.PENDING_PAYMENT || existing.getPaymentMethod() != PaymentMethod.VNPAY) {
            return false;
        }
        if (existing.getExpiresAt() != null && !existing.getExpiresAt().isAfter(now)) {
            return false;
        }
        if (calc.finalPrice() <= 0L) {
            return false;
        }
        return Integer.valueOf(stored.versionNo()).equals(existing.getExamVersionNo())
                && existing.getAttemptsGranted() == stored.attemptsGranted()
                && existing.getListPriceVnd() == calc.listPrice()
                && existing.getAmountDueVnd() == calc.finalPrice()
                && existing.getDiscountApplied() == calc.discountApplied()
                && Objects.equals(existing.getPromotionId(), discounts.promotionId())
                && Objects.equals(existing.getVoucherId(), discounts.voucherId())
                && Objects.equals(normalizeUpper(existing.getVoucherCode()), normalizeUpper(discounts.voucherCode()));
    }

    private static DiscountSnapshot resolveDiscounts(QuoteCalculation calc) {
        return switch (calc.discountApplied()) {
            case NONE -> new DiscountSnapshot(null, 0L, null, null, 0L);
            case PROMOTION_ONLY -> new DiscountSnapshot(
                    calc.promotion().id(), calc.promotionDiscount(), null, null, 0L);
            case VOUCHER_ONLY -> new DiscountSnapshot(
                    null, 0L, calc.voucher().id(), calc.voucher().code(), calc.voucherDiscount());
            case COMBINED -> new DiscountSnapshot(
                    calc.promotion().id(),
                    calc.promotionDiscount(),
                    calc.voucher().id(),
                    calc.voucher().code(),
                    calc.voucherDiscount());
        };
    }

    private static List<ReserveRequest> buildReserveRequests(DiscountSnapshot discounts) {
        List<ReserveRequest> list = new ArrayList<>(2);
        if (discounts.promotionId() != null) {
            list.add(new ReserveRequest(discounts.promotionId(), discounts.promotionDiscountVnd()));
        }
        if (discounts.voucherId() != null) {
            list.add(new ReserveRequest(discounts.voucherId(), discounts.voucherDiscountVnd()));
        }
        return list;
    }

    private ProductDetailSnapshot parseDetailSnapshot(String rawDetailJson) {
        if (!StringUtils.hasText(rawDetailJson)) {
            return new ProductDetailSnapshot(List.of(), Map.of(), DEFAULT_RESULT_VIEW);
        }
        try {
            JsonNode root = objectMapper.readTree(rawDetailJson);
            List<String> optionCodes = new ArrayList<>();
            JsonNode choiceGroups = root.path("choices");
            if (choiceGroups.isArray()) {
                for (JsonNode group : choiceGroups) {
                    JsonNode options = group.path("options");
                    if (options.isArray()) {
                        for (JsonNode option : options) {
                            String code = option.path("code").asText(null);
                            if (StringUtils.hasText(code)) {
                                optionCodes.add(code);
                            }
                        }
                    }
                }
            }
            Map<String, Object> benefits = Map.of();
            JsonNode benefitsNode = root.get("benefits");
            if (benefitsNode != null && benefitsNode.isObject()) {
                benefits = PurchaseOrder.copyNonNullEntries(objectMapper.convertValue(benefitsNode, MAP_TYPE));
            }
            String resultView = root.path("resultView").asText(DEFAULT_RESULT_VIEW);
            if (!StringUtils.hasText(resultView)) {
                resultView = DEFAULT_RESULT_VIEW;
            }
            return new ProductDetailSnapshot(List.copyOf(optionCodes), benefits, resultView);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse storefront product detail JSON", ex);
        }
    }

    private OrderDetail buildOrderDetail(PurchaseOrder order) {
        List<OrderItemView> items = purchaseOrderItemRepository.findByOrderIdOrderByPositionAsc(order.getId()).stream()
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
        List<OrderPaymentView> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .map(p -> new OrderPaymentView(
                        p.getId(),
                        p.getAttemptSeq(),
                        p.getTxnRef(),
                        p.getAmountVnd(),
                        p.getState(),
                        p.isDuplicate(),
                        p.getVnpTransactionNo(),
                        p.getBankCode(),
                        p.getCardType(),
                        p.getPayDate(),
                        p.getResponseCode(),
                        p.getCreatedAt()))
                .toList();
        List<OrderHistoryView> history = orderHistoryRepository.findByOrderIdOrderByAtAsc(order.getId()).stream()
                .map(h -> new OrderHistoryView(h.getId(), h.getFromState(), h.getToState(), h.getActor(), h.getReason(), h.getAt()))
                .toList();
        String replacedByCode = order.getReplacedByOrderId() == null
                ? null
                : purchaseOrderRepository.findById(order.getReplacedByOrderId()).map(PurchaseOrder::getCode).orElse(null);

        return new OrderDetail(
                order.getId(),
                order.getCode(),
                order.getUserId(),
                order.getBuyerUsername(),
                order.getBuyerFullName(),
                order.getProductType(),
                order.getExamId(),
                order.getExamVersionNo(),
                order.getBundleId(),
                order.getBundleVersionNo(),
                order.getOptionCodes(),
                order.getProductCode(),
                order.getProductName(),
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
                order.getReplacedByOrderId(),
                replacedByCode,
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getFulfilledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                payments,
                history);
    }

    private static OrderSummary toSummary(PurchaseOrder order) {
        return new OrderSummary(
                order.getId(),
                order.getCode(),
                order.getProductType(),
                order.getExamId(),
                order.getBundleId(),
                order.getProductCode(),
                order.getProductName(),
                order.getListPriceVnd(),
                order.getPromotionDiscountVnd(),
                order.getVoucherDiscountVnd(),
                order.getDiscountApplied(),
                order.getAmountDueVnd(),
                order.getAttemptsGranted(),
                order.getPaymentMethod(),
                order.getState(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getFulfilledAt(),
                order.getCreatedAt());
    }

    private static String normalizeOrderCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeUpper(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeIp(String ip) {
        return StringUtils.hasText(ip) ? ip.trim() : "127.0.0.1";
    }

    private static String normalizeWebUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "http://localhost:3000";
        }
        String trimmed = raw.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private record DiscountSnapshot(
            UUID promotionId,
            long promotionDiscountVnd,
            UUID voucherId,
            String voucherCode,
            long voucherDiscountVnd) {
    }

    private record ProductDetailSnapshot(
            List<String> optionCodes,
            Map<String, Object> benefits,
            String resultView) {
    }

    private record CreateOrderOutcome(
            UUID orderId,
            boolean freeOrder,
            CreateOrderResponse response) {
    }

    private static final class AutoPromotionStaleException extends RuntimeException {
        private final String productCode;
        private final String voucherCode;

        private AutoPromotionStaleException(String productCode, String voucherCode, Throwable cause) {
            super(cause);
            this.productCode = productCode;
            this.voucherCode = voucherCode;
        }
    }

    public record CreateOrderRequest(
            @NotBlank(message = "{validation.order.code.notBlank}")
            @Size(max = 100, message = "{validation.order.code.size}")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,
            @Size(max = 100, message = "{validation.order.voucherCode.size}")
            String voucherCode,
            @NotNull(message = "{validation.order.expectedAccountId.notNull}")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID expectedAccountId,
            @NotNull(message = "{validation.order.expectedVersionNo.notNull}")
            @Min(value = 1, message = "{validation.order.expectedVersionNo.min}")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer expectedVersionNo,
            @NotNull(message = "{validation.order.expectedAmountDue.notNull}")
            @Min(value = 0, message = "{validation.order.expectedAmountDue.min}")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Long expectedAmountDue) {
    }

    public record CreateOrderResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderDetail order,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"REDIRECT", "FULFILLED", "CHECKING"})
            String next,
            String paymentUrl) {
    }

    public record PayOrderResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderDetail order,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "REDIRECT") String next,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String paymentUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String txnRef,
            Instant expiresAt) {
    }

    public record OrderSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
            UUID examId,
            UUID bundleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPriceVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promotionDiscountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voucherDiscountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountApplied discountApplied,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountDueVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentMethod paymentMethod,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderState state,
            Instant expiresAt,
            Instant paidAt,
            Instant fulfilledAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    }

    public record OrderDetail(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerUsername,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buyerFullName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
            UUID examId,
            Integer examVersionNo,
            UUID bundleId,
            Integer bundleVersionNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> optionCodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPriceVnd,
            UUID promotionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promotionDiscountVnd,
            UUID voucherId,
            String voucherCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voucherDiscountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DiscountApplied discountApplied,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountDueVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> benefits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultView,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentMethod paymentMethod,
            OrderState state,
            UUID replacedByOrderId,
            String replacedByOrderCode,
            Instant expiresAt,
            Instant paidAt,
            Instant fulfilledAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderItemView> items,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderPaymentView> payments,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderHistoryView> history) {
    }

    public record OrderItemView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int examVersionNo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> optionCodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsGranted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> benefits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultView,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position) {
    }

    public record OrderPaymentView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptSeq,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String txnRef,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountVnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentState state,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean duplicate,
            String vnpTransactionNo,
            String bankCode,
            String cardType,
            String payDate,
            String responseCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    }

    public record OrderHistoryView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            String fromState,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toState,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actor,
            String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at) {
    }
}
