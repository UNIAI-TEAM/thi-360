package vn.unicom.thi360.commerce.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.storefront.api.AnswerTypeGroup;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.DetailBenefits;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceGroup;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceOption;
import vn.unicom.thi360.commerce.storefront.api.DetailPart;
import vn.unicom.thi360.commerce.storefront.api.DetailScoring;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;
import vn.unicom.thi360.shared.security.TestSecurity.TestSecuritySupport;

/**
 * Bộ dựng dữ liệu dùng chung cho test đơn hàng, thanh toán, cấp quyền (plan CM-3a, B1).
 * Mọi tài khoản, sản phẩm, mã khuyến mãi, đơn hàng đều sinh mã ngẫu nhiên, không giả định bảng rỗng.
 */
public class CommerceTestFixtures {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogLookup catalogLookup;
    private final PromotionRepository promotionRepository;
    private final TestSecuritySupport securitySupport;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> examIds = new ArrayList<>();
    private final List<UUID> promotionIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> classIds = new ArrayList<>();
    private final List<String> mailRecipients = new ArrayList<>();

    public CommerceTestFixtures(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            CatalogLookup catalogLookup,
            PromotionRepository promotionRepository,
            TestSecuritySupport securitySupport) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalogLookup = catalogLookup;
        this.promotionRepository = promotionRepository;
        this.securitySupport = securitySupport;
    }

    public LearnerAccount createLearner() {
        return createLearner(true, "vi", 2, false);
    }

    public LearnerAccount createLearner(boolean emailVerified, String preferredLocale) {
        return createLearner(emailVerified, preferredLocale, 2, false);
    }

    public LearnerAccount createLearner(
            boolean emailVerified, String preferredLocale, int statusCode, boolean deleted) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = "cm3a." + userId.toString().substring(0, 8);
        String email = username + "@test.local";
        String fullName = "Hoc Vien " + username;
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp verifiedAt = emailVerified ? now : null;
        Timestamp deletedAt = deleted ? now : null;
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     deleted_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, false, ?, ?, ?, ?, ?, 0)
                """,
                userId,
                String.format("U%06d", seq),
                username,
                username.toLowerCase(),
                fullName,
                email,
                email.toLowerCase(),
                verifiedAt,
                preferredLocale == null ? "vi" : preferredLocale,
                statusCode,
                deletedAt,
                now,
                userId,
                now,
                userId);
        userIds.add(userId);
        mailRecipients.add(email);
        return new LearnerAccount(userId, username, fullName, email);
    }

    public RequestPostProcessor asLearner(LearnerAccount account) {
        return securitySupport.apply(new SimulatedPrincipal(
                account.id(), account.username(), "STUDENT", false, List.of()));
    }

    public RequestPostProcessor asManager(UUID userId, String username, String... permissions) {
        return securitySupport.apply(new SimulatedPrincipal(
                userId, username, null, false, permissions == null ? List.of() : List.of(permissions)));
    }

    public UUID createClassRoom() {
        UUID classId = UUID.randomUUID();
        String code = "CLS" + shortToken();
        jdbc.update("""
                INSERT INTO classroom.class_room
                    (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), 0)
                """, classId, code, code.toLowerCase(), "Class " + code);
        classIds.add(classId);
        return classId;
    }

    public void addClassMember(UUID classId, UUID userId) {
        jdbc.update("""
                INSERT INTO classroom.class_member (class_id, user_id, joined_at, added_by)
                VALUES (?, ?, now(), ?)
                """, classId, userId, userId);
    }

    public StoredTestProduct insertOnSaleProduct(String kindCode, long listPriceVnd, int attemptsGranted) {
        return insertProduct(kindCode, "ON_SALE", listPriceVnd, attemptsGranted, 1, List.of("OPT-A", "OPT-B"));
    }

    public StoredTestProduct insertOnSaleProduct(
            String kindCode, long listPriceVnd, int attemptsGranted, int versionNo, List<String> optionCodes) {
        return insertProduct(kindCode, "ON_SALE", listPriceVnd, attemptsGranted, versionNo, optionCodes);
    }

    public StoredTestProduct insertProduct(
            String kindCode,
            String saleState,
            long listPriceVnd,
            int attemptsGranted,
            int versionNo,
            List<String> optionCodes) {
        UUID examId = UUID.randomUUID();
        UUID examKindItemId = catalogLookup.findActiveItem("EXAM", kindCode).orElseThrow().id();
        String code = "ORDP" + shortToken();
        String name = "Exam " + code;

        List<DetailChoiceOption> choiceOptions = optionCodes == null ? List.of() : optionCodes.stream()
                .map(opt -> new DetailChoiceOption(
                        "BRANCH", opt, "Option " + opt, null, null, null, List.of(), List.of(), List.of()))
                .toList();
        List<DetailChoiceGroup> choiceGroups = choiceOptions.isEmpty()
                ? List.of()
                : List.of(new DetailChoiceGroup("Phần tự chọn", choiceOptions));
        DetailBenefits benefits = new DetailBenefits(true, false, true, "Luyện tập nâng cao");

        ProductCard card = new ProductCard(
                code, name, "Tóm tắt " + code, null,
                new ExamKindRef(kindCode, kindCode, kindCode),
                "FULL", null, "STANDARD",
                new IntRange(50, 50), new IntRange(75, 75),
                attemptsGranted, listPriceVnd, listPriceVnd, null, versionNo);
        ProductDetail detail = new ProductDetail(
                code, name, "Tóm tắt " + code, null,
                new ExamKindRef(kindCode, kindCode, kindCode),
                "FULL", null, "STANDARD",
                new IntRange(50, 50), new IntRange(75, 75),
                new DecimalRange(BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
                attemptsGranted, listPriceVnd, listPriceVnd, null, versionNo,
                "Mô tả " + code, null, null,
                List.of(new DetailPart(1, "Phần 1", new IntRange(50, 50), new IntRange(75, 75),
                        new DecimalRange(BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
                        true, List.of(new AnswerTypeGroup("SINGLE_CHOICE", 50, null)))),
                choiceGroups,
                new DetailScoring("PER_PART", BigDecimal.valueOf(100), null, "LINEAR"),
                List.of(),
                benefits,
                "FULL_SOLUTION");

        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, exam_item_id,
                     kind_code, scope, question_count, question_count_max, duration_minutes, duration_minutes_max,
                     max_points, max_points_max, build_mode, list_price_vnd, attempts_granted,
                     card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FULL', 50, 50, 75, 75, 100.00, 100.00, 'STANDARD',
                        ?, ?, ?::jsonb, ?::jsonb, ?, now(), now())
                """,
                UUID.randomUUID(), examId, code, code.toLowerCase(), versionNo, saleState, name,
                (name + " " + code).toLowerCase(), examKindItemId, kindCode,
                listPriceVnd, attemptsGranted, toJson(card), toJson(detail), "hash-" + shortToken());
        examIds.add(examId);
        return new StoredTestProduct(examId, examKindItemId, code, name, versionNo, listPriceVnd, attemptsGranted);
    }

    public void updateStorefrontPriceAndVersion(
            UUID examId, long newListPriceVnd, int newVersionNo, String saleState) {
        String code = jdbc.queryForObject(
                "SELECT code FROM commerce.storefront_product WHERE exam_id = ?", String.class, examId);
        jdbc.update("""
                UPDATE commerce.storefront_product
                SET list_price_vnd = ?, version_no = ?, sale_state = ?, updated_at = now()
                WHERE exam_id = ?
                """, newListPriceVnd, newVersionNo, saleState, examId);
        if (code != null && !examIds.contains(examId)) {
            examIds.add(examId);
        }
    }

    public Promotion createVoucher(
            String prefix,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            Integer totalLimit,
            Integer perUserLimit,
            boolean combinable,
            PromotionScope scope,
            PromotionAudienceType audience) {
        Instant now = Instant.now();
        return createVoucher(
                prefix, discountType, discountValue, maxDiscountVnd,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                totalLimit, perUserLimit, combinable, scope, audience);
    }

    public Promotion createVoucher(
            String prefix,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            Instant startsAt,
            Instant endsAt,
            Integer totalLimit,
            Integer perUserLimit,
            boolean combinable,
            PromotionScope scope,
            PromotionAudienceType audience) {
        UUID id = Ids.newId();
        String code = prefix + shortToken();
        promotionIds.add(id);
        return promotionRepository.saveAndFlush(Promotion.voucher(
                id, "Voucher " + code, null, code, discountType, discountValue, maxDiscountVnd,
                startsAt, endsAt, totalLimit, perUserLimit, combinable, scope, audience,
                PromotionStatus.ACTIVE, Instant.now(), null, null));
    }

    public Promotion createAutoPromotion(
            String name, DiscountType discountType, long discountValue, Long maxDiscountVnd,
            Instant startsAt, Instant endsAt, Integer totalLimit, PromotionScope scope) {
        UUID id = Ids.newId();
        promotionIds.add(id);
        return promotionRepository.saveAndFlush(Promotion.auto(
                id, name, null, discountType, discountValue, maxDiscountVnd,
                startsAt, endsAt, totalLimit, scope, PromotionStatus.ACTIVE, Instant.now(), null, null));
    }

    public StubOrder insertStubOrder(
            LearnerAccount buyer,
            StoredTestProduct product,
            OrderState state,
            PaymentMethod paymentMethod,
            long amountDueVnd,
            Instant expiresAt,
            Instant paidAt) {
        UUID orderId = Ids.newId();
        String orderCode = "DH261121" + shortToken().substring(0, 6);
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp expiresTs = expiresAt == null ? null : Timestamp.from(expiresAt);
        Timestamp paidTs = paidAt == null ? null : Timestamp.from(paidAt);
        jdbc.update("""
                INSERT INTO commerce.purchase_order
                    (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                     option_codes, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                     attempts_granted, benefits, result_view, payment_method, state, expires_at, paid_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'EXAM', ?, ?, ARRAY['OPT-A']::text[], ?, ?, ?, 'NONE', ?, ?,
                        '{"aiExplanation":true}'::jsonb, 'FULL_SOLUTION', ?, ?, ?, ?, ?, ?, 0)
                """,
                orderId, orderCode, buyer.id(), buyer.username(), buyer.fullName(),
                product.examId(), product.versionNo(), product.code(), product.name(),
                product.listPriceVnd(), amountDueVnd, product.attemptsGranted(),
                paymentMethod.name(), state.name(), expiresTs, paidTs, now, now);
        jdbc.update("""
                INSERT INTO commerce.purchase_order_item
                    (id, order_id, exam_id, exam_version_no, exam_code, exam_name, option_codes,
                     attempts_granted, benefits, result_view, position)
                VALUES (?, ?, ?, ?, ?, ?, ARRAY['OPT-A']::text[], ?, '{"aiExplanation":true}'::jsonb, 'FULL_SOLUTION', 1)
                """,
                Ids.newId(), orderId, product.examId(), product.versionNo(), product.code(), product.name(),
                product.attemptsGranted());
        orderIds.add(orderId);
        return new StubOrder(orderId, orderCode);
    }

    public StubPayment insertStubPayment(StubOrder order, int attemptSeq, long amountVnd, PaymentState state) {
        UUID paymentId = Ids.newId();
        String txnRef = order.code() + String.format("%02d", attemptSeq);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO commerce.payment
                    (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd, state,
                     is_duplicate, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, '20261121100000', '20261121101500', ?, ?, false, ?, ?, 0)
                """, paymentId, order.id(), attemptSeq, txnRef, amountVnd, state.name(), now, now);
        return new StubPayment(paymentId, txnRef);
    }

    public GrantOrderResult.Line grantOrder(EntitlementGrantService grantService, UUID orderId, String orderCode,
            UUID userId, UUID examId, int attempts) {
        return grantService.grantForOrder(new GrantOrderCommand(
                orderId, orderCode, userId,
                List.of(new GrantLine(examId, 1, attempts, List.of(), Map.of("aiExplanation", true), "FULL_SOLUTION"))))
                .lines().getFirst();
    }

    public void trackOrderId(UUID orderId) {
        if (orderId != null && !orderIds.contains(orderId)) {
            orderIds.add(orderId);
        }
    }

    public void cleanup() {
        for (UUID userId : userIds) {
            jdbc.update("""
                    DELETE FROM commerce.payment_event
                    WHERE payment_id IN (
                        SELECT p.id FROM commerce.payment p
                        JOIN commerce.purchase_order o ON o.id = p.order_id
                        WHERE o.user_id = ?
                    )
                    """, userId);
            jdbc.update("""
                    DELETE FROM commerce.payment
                    WHERE order_id IN (SELECT id FROM commerce.purchase_order WHERE user_id = ?)
                    """, userId);
            jdbc.update("""
                    DELETE FROM commerce.order_history
                    WHERE order_id IN (SELECT id FROM commerce.purchase_order WHERE user_id = ?)
                    """, userId);
            jdbc.update("""
                    DELETE FROM commerce.purchase_order_item
                    WHERE order_id IN (SELECT id FROM commerce.purchase_order WHERE user_id = ?)
                    """, userId);
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE user_id = ?", userId);
            jdbc.update("""
                    DELETE FROM commerce.entitlement_usage
                    WHERE grant_id IN (
                        SELECT g.id FROM commerce.entitlement_grant g
                        JOIN commerce.entitlement e ON e.id = g.entitlement_id
                        WHERE e.user_id = ?
                    )
                    """, userId);
            jdbc.update("""
                    DELETE FROM commerce.entitlement_grant
                    WHERE entitlement_id IN (SELECT id FROM commerce.entitlement WHERE user_id = ?)
                    """, userId);
            jdbc.update("DELETE FROM commerce.entitlement WHERE user_id = ?", userId);
            jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = NULL WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM commerce.purchase_order WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
        }
        for (UUID orderId : orderIds) {
            jdbc.update("DELETE FROM commerce.payment_event WHERE payment_id IN (SELECT id FROM commerce.payment WHERE order_id = ?)", orderId);
            jdbc.update("DELETE FROM commerce.payment WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM commerce.order_history WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM commerce.purchase_order_item WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM commerce.entitlement_grant WHERE order_id = ?", orderId);
            jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = NULL WHERE id = ? OR replaced_by_order_id = ?", orderId, orderId);
            jdbc.update("DELETE FROM commerce.purchase_order WHERE id = ?", orderId);
        }
        for (String email : mailRecipients) {
            jdbc.update("DELETE FROM shared.email_message WHERE lower(to_email) = lower(?)", email);
        }
        for (UUID promoId : promotionIds) {
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion_audience WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion_scope_item WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion_change_log WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", promoId);
        }
        for (UUID examId : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        for (UUID classId : classIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        orderIds.clear();
        promotionIds.clear();
        examIds.clear();
        classIds.clear();
        mailRecipients.clear();
        userIds.clear();
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize storefront fixture JSON", ex);
        }
    }

    public static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public record LearnerAccount(UUID id, String username, String fullName, String email) {
    }

    public record StoredTestProduct(
            UUID examId,
            UUID examKindItemId,
            String code,
            String name,
            int versionNo,
            long listPriceVnd,
            int attemptsGranted) {
    }

    public record StubOrder(UUID id, String code) {
    }

    public record StubPayment(UUID id, String txnRef) {
    }
}
