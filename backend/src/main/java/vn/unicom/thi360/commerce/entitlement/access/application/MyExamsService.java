package vn.unicom.thi360.commerce.entitlement.access.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.api.MyExamCard;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.domain.MyExamAction;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.EntitlementRow;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup.PendingOrder;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.ResultAttempt;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;

/**
 * {@code GET /me/exams} (TM 12): one card per purchase entitlement of the account, plus a "checking" card for each paid
 * order that has no entitlement yet. Attempt counts come from the granted entitlement (never from the product on sale),
 * the open and the result attempt from {@link AttemptStatusProvider}, the sale state and the names from the storefront
 * projection, the question count and duration from the serving paths of {@link PublishedExamPort}. Read-only.
 */
@Service
public class MyExamsService {

    private static final Logger log = LoggerFactory.getLogger(MyExamsService.class);

    private final ExamAccessReadRepository repository;
    private final PendingOrderLookup pendingOrders;
    private final AttemptStatusSource attempts;
    private final PublishedExamPort exams;
    private final ObjectMapper mapper;

    public MyExamsService(ExamAccessReadRepository repository, PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts, PublishedExamPort exams, ObjectMapper mapper) {
        this.repository = repository;
        this.pendingOrders = pendingOrders;
        this.attempts = attempts;
        this.exams = exams;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MyExamCard> list(UUID userId) {
        List<EntitlementRow> entitlements = repository.findPurchaseEntitlements(userId);
        List<PendingOrder> pending = pendingOrders.findPaidUnfulfilled(userId);

        Set<UUID> examIds = new LinkedHashSet<>();
        entitlements.forEach(e -> examIds.add(e.examId()));
        pending.forEach(p -> examIds.add(p.examId()));
        Map<UUID, ProductRef> products = new HashMap<>();
        repository.findProductsByExamIds(examIds).forEach(p -> products.put(p.examId(), p));
        Map<UUID, Set<String>> optionCodes = repository.findOptionCodes(entitlements.stream().map(EntitlementRow::id).toList());
        Map<UUID, String> pendingByExam = new HashMap<>();
        pending.forEach(p -> pendingByExam.putIfAbsent(p.examId(), p.orderCode()));
        Set<UUID> entitledExams = new HashSet<>();
        entitlements.forEach(e -> entitledExams.add(e.examId()));

        List<MyExamCard> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PendingOrder order : pending) {
            if (entitledExams.contains(order.examId()) || !seen.add(order.orderCode() + "|" + order.examId())) {
                continue;
            }
            ProductRef product = products.get(order.examId());
            if (product == null) {
                log.warn("Paid order {} has no storefront product for exam {}; its checking card is left out",
                        order.orderCode(), order.examId());
                continue;
            }
            cards.add(card(product, null, Set.of(), null, null, order.orderCode()));
        }
        AttemptStatusProvider provider = attempts.get();
        for (int i = entitlements.size() - 1; i >= 0; i--) {
            EntitlementRow entitlement = entitlements.get(i);
            ProductRef product = products.get(entitlement.examId());
            if (product == null) {
                log.warn("Entitlement {} has no storefront product for exam {}; its card is left out", entitlement.id(),
                        entitlement.examId());
                continue;
            }
            cards.add(card(product, entitlement, optionCodes.getOrDefault(entitlement.id(), Set.of()),
                    provider.findOpenAttempt(userId, entitlement.id()).orElse(null),
                    provider.findLatestResultAttempt(userId, entitlement.id()).orElse(null),
                    pendingByExam.get(entitlement.examId())));
        }
        return cards;
    }

    private MyExamCard card(ProductRef product, EntitlementRow entitlement, Set<String> purchasedOptions,
            OpenAttempt open, ResultAttempt result, String pendingOrderCode) {
        ProductCard stored = read(product.card());
        boolean hasEntitlement = entitlement != null;
        int attemptsLeft = hasEntitlement ? entitlement.attemptsLeft() : 0;
        ExamAccessState state = ExamAccessState.of(hasEntitlement, attemptsLeft, open != null, pendingOrderCode != null);
        boolean hasHistory = open != null || result != null
                || (hasEntitlement && entitlement.attemptsUsed() + entitlement.attemptsReserved() > 0);
        boolean onSale = product.saleState() == SaleState.ON_SALE;

        List<ServingPath> paths = exams.findServing(product.examId()).map(ServingExam::servingPaths)
                .map(all -> purchasedPaths(all, purchasedOptions)).orElse(List.of());
        IntRange questions = range(paths, ServingPath::questionCount);
        IntRange minutes = range(paths, ServingPath::durationMinutes);

        return new MyExamCard(
                hasEntitlement ? entitlement.id() : null,
                product.code(),
                stored.name(),
                stored.examKind(),
                stored.format(),
                stored.scopeLabel(),
                stored.buildMode(),
                stored.coverUrl(),
                questions != null ? questions : stored.questionCount(),
                minutes != null ? minutes : stored.durationMinutes(),
                attemptsLeft,
                hasEntitlement ? entitlement.attemptsTotal() - entitlement.attemptsRevoked() : 0,
                product.saleState(),
                state,
                open == null ? null : open.attemptId(),
                open == null ? null : open.state(),
                result == null ? null : result.attemptId(),
                pendingOrderCode != null,
                pendingOrderCode,
                MyExamAction.of(state, attemptsLeft, open != null, result != null, hasHistory, onSale));
    }

    /**
     * The serving paths the buyer can be given (E25): a path with choices counts when every choice is among the purchased
     * option codes; a path with none always counts. When no path matches (a code that is no longer served) every serving
     * path is kept, so the card still shows what the exam is.
     */
    private static List<ServingPath> purchasedPaths(List<ServingPath> servingPaths, Set<String> purchasedOptions) {
        Set<String> purchased = new HashSet<>();
        purchasedOptions.forEach(code -> purchased.add(code.trim().toLowerCase(Locale.ROOT)));
        List<ServingPath> matching = servingPaths.stream()
                .filter(path -> path.choices().stream()
                        .allMatch(choice -> choice.code() != null
                                && purchased.contains(choice.code().trim().toLowerCase(Locale.ROOT))))
                .toList();
        return matching.isEmpty() ? servingPaths : matching;
    }

    private static IntRange range(List<ServingPath> paths, Function<ServingPath, Integer> value) {
        Integer min = null;
        Integer max = null;
        for (ServingPath path : paths) {
            Integer v = value.apply(path);
            if (v == null) {
                continue;
            }
            min = min == null || v < min ? v : min;
            max = max == null || v > max ? v : max;
        }
        return min == null ? null : new IntRange(min, max);
    }

    private ProductCard read(String json) {
        try {
            return mapper.readValue(json, ProductCard.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored storefront card is not readable", e);
        }
    }
}
