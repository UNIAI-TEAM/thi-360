package vn.unicom.thi360.commerce.entitlement.access.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.api.BundleAccessExamItem;
import vn.unicom.thi360.commerce.entitlement.access.api.ExamAccessItem;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.EntitlementRow;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;

/**
 * {@code POST /me/exam-access/query} (TM 10.2, 16.4): the state of each product code for one account, read-only. A
 * single exam code maps to its exam through the storefront projection; a bundle code returns {@code NONE} on the
 * bundle row along with per-exam states in {@code items}; an unknown code is {@code NONE}.
 */
@Service
public class ExamAccessService {

    private final ExamAccessReadRepository repository;
    private final PendingOrderLookup pendingOrders;
    private final AttemptStatusSource attempts;
    private final ObjectMapper mapper;

    public ExamAccessService(ExamAccessReadRepository repository, PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts) {
        this(repository, pendingOrders, attempts, new ObjectMapper());
    }

    @Autowired
    public ExamAccessService(ExamAccessReadRepository repository, PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts, ObjectMapper mapper) {
        this.repository = repository;
        this.pendingOrders = pendingOrders;
        this.attempts = attempts;
        this.mapper = mapper;
    }

    /**
     * Repeatable read: entitlements, attempt status and pending orders come from one snapshot (see
     * {@link MyExamsService#list}).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ExamAccessItem> query(UUID userId, List<String> codes) {
        Map<String, String> requested = new LinkedHashMap<>();
        for (String code : codes) {
            String trimmed = code.trim();
            requested.putIfAbsent(normalize(trimmed), trimmed);
        }
        Map<String, ProductRef> products = repository.findProductsByCodes(requested.keySet()).stream()
                .collect(Collectors.toMap(ProductRef::codeNormalized, p -> p));
        Map<UUID, EntitlementRow> entitlements = new HashMap<>();
        repository.findPurchaseEntitlements(userId).forEach(e -> entitlements.put(e.examId(), e));
        Map<UUID, String> pendingByExam = new HashMap<>();
        pendingOrders.findPaidUnfulfilled(userId).forEach(p -> pendingByExam.putIfAbsent(p.examId(), p.orderCode()));
        AttemptStatusProvider provider = attempts.get();

        List<ExamAccessItem> items = new ArrayList<>(requested.size());
        requested.forEach((normalized, code) -> {
            ProductRef product = products.get(normalized);
            if (product == null) {
                items.add(new ExamAccessItem(code, ExamAccessState.NONE, 0, null, null, false, null, null));
                return;
            }
            if (product.productType() == ProductType.BUNDLE) {
                List<BundleAccessExamItem> bundleItems = buildBundleAccessItems(
                        userId, product.detail(), entitlements, pendingByExam, provider);
                items.add(new ExamAccessItem(code, ExamAccessState.NONE, 0, null, null, false, null, bundleItems));
                return;
            }
            EntitlementRow entitlement = entitlements.get(product.examId());
            String pendingOrderCode = pendingByExam.get(product.examId());
            OpenAttempt open = entitlement == null ? null
                    : provider.findOpenAttempt(userId, entitlement.id()).orElse(null);
            int attemptsLeft = entitlement == null ? 0 : entitlement.attemptsLeft();
            ExamAccessState state = ExamAccessState.of(
                    entitlement != null, attemptsLeft, open != null, pendingOrderCode != null);
            items.add(new ExamAccessItem(code, state, attemptsLeft,
                    entitlement == null ? null : entitlement.id(), open == null ? null : open.attemptId(),
                    pendingOrderCode != null, pendingOrderCode, null));
        });
        return items;
    }

    private List<BundleAccessExamItem> buildBundleAccessItems(
            UUID userId,
            String detailJson,
            Map<UUID, EntitlementRow> entitlements,
            Map<UUID, String> pendingByExam,
            AttemptStatusProvider provider) {
        if (detailJson == null || detailJson.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = mapper.readTree(detailJson);
        } catch (Exception ignored) {
            return List.of();
        }
        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            return List.of();
        }
        List<BundleAccessExamItem> result = new ArrayList<>(itemsNode.size());
        for (JsonNode itemNode : itemsNode) {
            String rawExamId = itemNode.path("examId").asText(null);
            if (rawExamId == null || rawExamId.isBlank()) {
                continue;
            }
            UUID itemExamId;
            try {
                itemExamId = UUID.fromString(rawExamId);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String itemCode = itemNode.path("code").asText("");
            String itemName = itemNode.path("name").asText("");
            EntitlementRow entitlement = entitlements.get(itemExamId);
            String pendingOrderCode = pendingByExam.get(itemExamId);
            OpenAttempt open = entitlement == null ? null
                    : provider.findOpenAttempt(userId, entitlement.id()).orElse(null);
            int attemptsLeft = entitlement == null ? 0 : entitlement.attemptsLeft();
            ExamAccessState itemState = ExamAccessState.of(
                    entitlement != null, attemptsLeft, open != null, pendingOrderCode != null);
            result.add(new BundleAccessExamItem(
                    itemExamId,
                    itemCode,
                    itemName,
                    itemState,
                    attemptsLeft,
                    entitlement == null ? null : entitlement.id(),
                    open == null ? null : open.attemptId(),
                    pendingOrderCode != null,
                    pendingOrderCode));
        }
        return List.copyOf(result);
    }

    /** The way {@code storefront_product.code_normalized} is written: trimmed and lower case. */
    static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
