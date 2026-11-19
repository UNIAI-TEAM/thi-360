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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.api.MyExamCard;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.domain.MyExamAction;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.EntitlementRow;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.GrantOptions;
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
 * {@code GET /me/exams} (TM 12): one card per purchase entitlement of the account, plus a "checking" card for every paid
 * unfulfilled order (also when the exam is already owned). Attempt counts come from the granted entitlement (never
 * from the product on sale), the open and the result attempt from {@link AttemptStatusProvider}, the sale state and
 * the names from the storefront projection, the question count and duration from the serving paths of
 * {@link PublishedExamPort}. Read-only.
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

    /**
     * Repeatable read: entitlements, grants, attempt status and pending orders of one request come from one snapshot, so
     * a fulfillment that commits between the reads (paid order becomes an entitlement) shows as one side of the commit,
     * never as neither.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MyExamCard> list(UUID userId) {
        List<EntitlementRow> entitlements = repository.findPurchaseEntitlements(userId);
        List<PendingOrder> pending = pendingOrders.findPaidUnfulfilled(userId);

        Set<UUID> examIds = new LinkedHashSet<>();
        entitlements.forEach(e -> examIds.add(e.examId()));
        pending.forEach(p -> examIds.add(p.examId()));
        Map<UUID, ProductRef> products = new HashMap<>();
        repository.findProductsByExamIds(examIds).forEach(p -> products.put(p.examId(), p));
        Map<UUID, List<GrantOptions>> grants = repository.findGrantOptions(entitlements.stream().map(EntitlementRow::id).toList());
        Map<UUID, String> pendingByExam = new HashMap<>();
        pending.forEach(p -> pendingByExam.putIfAbsent(p.examId(), p.orderCode()));

        Map<UUID, Optional<ServingExam>> servingByExam = new HashMap<>();
        Function<UUID, Optional<ServingExam>> serving = examId -> servingByExam.computeIfAbsent(examId, exams::findServing);
        List<MyExamCard> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PendingOrder order : pending) {
            if (!seen.add(order.orderCode() + "|" + order.examId())) {
                continue;
            }
            ProductRef product = products.get(order.examId());
            if (product == null) {
                log.warn("Paid order {} has no storefront product for exam {}; its checking card is left out",
                        order.orderCode(), order.examId());
                continue;
            }
            cards.add(card(product, null, null, null, null, order.orderCode(), serving));
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
            cards.add(card(product, entitlement, rangeScope(grants.getOrDefault(entitlement.id(), List.of())),
                    provider.findOpenAttempt(userId, entitlement.id()).orElse(null),
                    provider.findLatestResultAttempt(userId, entitlement.id()).orElse(null),
                    pendingByExam.get(entitlement.examId()), serving));
        }
        return cards;
    }

    private MyExamCard card(ProductRef product, EntitlementRow entitlement, List<Set<String>> grantOptions,
            OpenAttempt open, ResultAttempt result, String pendingOrderCode,
            Function<UUID, Optional<ServingExam>> serving) {
        ProductCard stored = read(product.card());
        boolean hasEntitlement = entitlement != null;
        int attemptsLeft = hasEntitlement ? entitlement.attemptsLeft() : 0;
        ExamAccessState state = ExamAccessState.of(hasEntitlement, attemptsLeft, open != null, pendingOrderCode != null);
        boolean hasHistory = open != null || result != null
                || (hasEntitlement && entitlement.attemptsUsed() + entitlement.attemptsReserved() > 0);
        boolean onSale = product.saleState() == SaleState.ON_SALE;

        IntRange questions = stored.questionCount();
        IntRange minutes = stored.durationMinutes();
        Optional<ServingExam> servingExam = serving.apply(product.examId());
        if (servingExam.isPresent()) {
            List<ServingPath> paths = eligiblePaths(servingExam.get().servingPaths(), grantOptions);
            // Nothing the buyer can be given (E25): no range, and not the public catalog range either.
            questions = paths.isEmpty() ? null : orElse(range(paths, ServingPath::questionCount), questions);
            minutes = paths.isEmpty() ? null : orElse(range(paths, ServingPath::durationMinutes), minutes);
        }

        return new MyExamCard(
                hasEntitlement ? entitlement.id() : null,
                product.code(),
                stored.name(),
                stored.examKind(),
                stored.format(),
                stored.scopeLabel(),
                stored.buildMode(),
                stored.coverUrl(),
                questions,
                minutes,
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
     * The grants whose serving paths the card shows (LT 5.1, E21): the grants that still have a usable attempt, as
     * {@code prepare} picks its FIFO grant from them; when none has one left, the newest grant (the next purchase of
     * attempts is the one that counts). Each entry is the option codes of one grant.
     */
    private static List<Set<String>> rangeScope(List<GrantOptions> grants) {
        List<Set<String>> usable = grants.stream().filter(GrantOptions::usable).map(GrantOptions::optionCodes).toList();
        if (!usable.isEmpty() || grants.isEmpty()) {
            return usable;
        }
        return List.of(grants.get(grants.size() - 1).optionCodes());
    }

    /**
     * The serving paths one of the given grants can be served (E25): a path with choices is eligible for a grant when
     * every choice is among that grant's option codes; a path with none is eligible for every grant. {@code null}
     * grants (a paid order with no entitlement yet) keep every serving path. Empty when nothing is eligible.
     */
    private static List<ServingPath> eligiblePaths(List<ServingPath> servingPaths, List<Set<String>> grantOptions) {
        if (grantOptions == null) {
            return servingPaths;
        }
        List<Set<String>> normalized = grantOptions.stream().map(MyExamsService::normalizeCodes).toList();
        return servingPaths.stream()
                .filter(path -> normalized.stream().anyMatch(purchased -> path.choices().stream()
                        .allMatch(choice -> choice.code() != null
                                && purchased.contains(choice.code().trim().toLowerCase(Locale.ROOT)))))
                .toList();
    }

    private static Set<String> normalizeCodes(Set<String> codes) {
        Set<String> normalized = new HashSet<>();
        codes.forEach(code -> normalized.add(code.trim().toLowerCase(Locale.ROOT)));
        return normalized;
    }

    private static IntRange orElse(IntRange range, IntRange fallback) {
        return range != null ? range : fallback;
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
