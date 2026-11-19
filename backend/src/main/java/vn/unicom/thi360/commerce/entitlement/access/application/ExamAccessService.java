package vn.unicom.thi360.commerce.entitlement.access.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.api.ExamAccessItem;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.EntitlementRow;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;

/**
 * {@code POST /me/exam-access/query} (TM 10.2): the state of each product code for one account, read-only. A code is
 * mapped to its exam through the storefront projection; a code that is not in the projection, or is a bundle (CM-6), is
 * {@code NONE}.
 */
@Service
public class ExamAccessService {

    private final ExamAccessReadRepository repository;
    private final PendingOrderLookup pendingOrders;
    private final AttemptStatusSource attempts;

    public ExamAccessService(ExamAccessReadRepository repository, PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts) {
        this.repository = repository;
        this.pendingOrders = pendingOrders;
        this.attempts = attempts;
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

        List<ExamAccessItem> items = new ArrayList<>(requested.size());
        requested.forEach((normalized, code) -> {
            ProductRef product = products.get(normalized);
            if (product == null) {
                items.add(new ExamAccessItem(code, ExamAccessState.NONE, 0, null, null, false, null));
                return;
            }
            EntitlementRow entitlement = entitlements.get(product.examId());
            String pendingOrderCode = pendingByExam.get(product.examId());
            OpenAttempt open = entitlement == null ? null
                    : attempts.get().findOpenAttempt(userId, entitlement.id()).orElse(null);
            int attemptsLeft = entitlement == null ? 0 : entitlement.attemptsLeft();
            ExamAccessState state = ExamAccessState.of(
                    entitlement != null, attemptsLeft, open != null, pendingOrderCode != null);
            items.add(new ExamAccessItem(code, state, attemptsLeft,
                    entitlement == null ? null : entitlement.id(), open == null ? null : open.attemptId(),
                    pendingOrderCode != null, pendingOrderCode));
        });
        return items;
    }

    /** The way {@code storefront_product.code_normalized} is written: trimmed and lower case. */
    static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
