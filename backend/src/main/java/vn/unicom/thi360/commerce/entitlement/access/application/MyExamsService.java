package vn.unicom.thi360.commerce.entitlement.access.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.classroom.spi.ClassroomInfo;
import vn.unicom.thi360.classroom.spi.ClassroomLookupPort;
import vn.unicom.thi360.commerce.entitlement.access.api.MyExamBundleRef;
import vn.unicom.thi360.commerce.entitlement.access.api.MyExamCard;
import vn.unicom.thi360.commerce.entitlement.access.application.PendingOrderLookup.PendingOrder;
import vn.unicom.thi360.commerce.entitlement.access.domain.ExamAccessState;
import vn.unicom.thi360.commerce.entitlement.access.domain.MyExamAction;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.EntitlementRow;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.GrantOptions;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.ResultAttempt;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjector;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;

/**
 * {@code GET /me/exams} (TM 12, UNI-2774): one card per purchase or class entitlement of the account, plus a
 * "checking" card for every paid unfulfilled order (also when the exam is already owned). Attempt counts come from the
 * granted entitlement (never from the product on sale), the open and the result attempt from
 * {@link AttemptStatusProvider}, the sale state and names from the storefront projection (falling back to
 * {@link PublishedExamPort} for class-assigned exams without a commercial listing), and question count / duration from
 * the serving paths of {@link PublishedExamPort}. Read-only.
 */
@Service
public class MyExamsService {

    private static final Logger log = LoggerFactory.getLogger(MyExamsService.class);
    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";

    private final ExamAccessReadRepository repository;
    private final PendingOrderLookup pendingOrders;
    private final AttemptStatusSource attempts;
    private final PublishedExamPort exams;
    private final ObjectMapper mapper;
    private final ClassroomLookupPort classrooms;
    private final CatalogLookup catalog;

    public MyExamsService(
            ExamAccessReadRepository repository,
            PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts,
            PublishedExamPort exams,
            ObjectMapper mapper) {
        this(repository, pendingOrders, attempts, exams, mapper, EmptyClassroomLookup.INSTANCE, null);
    }

    @Autowired
    public MyExamsService(
            ExamAccessReadRepository repository,
            PendingOrderLookup pendingOrders,
            AttemptStatusSource attempts,
            PublishedExamPort exams,
            ObjectMapper mapper,
            ClassroomLookupPort classrooms,
            CatalogLookup catalog) {
        this.repository = repository;
        this.pendingOrders = pendingOrders;
        this.attempts = attempts;
        this.exams = exams;
        this.mapper = mapper;
        this.classrooms = classrooms;
        this.catalog = catalog;
    }

    /**
     * Repeatable read: entitlements, grants, attempt status and pending orders of one request come from one snapshot, so
     * a fulfillment that commits between the reads (paid order becomes an entitlement) shows as one side of the commit,
     * never as neither.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MyExamCard> list(UUID userId) {
        List<EntitlementRow> entitlements = repository.findEntitlements(userId);
        List<PendingOrder> pending = pendingOrders.findPaidUnfulfilled(userId);

        Set<UUID> examIds = new LinkedHashSet<>();
        Set<UUID> classIds = new LinkedHashSet<>();
        for (EntitlementRow e : entitlements) {
            examIds.add(e.examId());
            if ("CLASS".equals(e.source()) && e.sourceRef() != null) {
                classIds.add(e.sourceRef());
            }
        }
        pending.forEach(p -> examIds.add(p.examId()));

        Map<UUID, ProductRef> products = new HashMap<>();
        repository.findProductsByExamIds(examIds).forEach(p -> products.put(p.examId(), p));
        Map<UUID, List<GrantOptions>> grants =
                repository.findGrantOptions(entitlements.stream().map(EntitlementRow::id).toList());
        Map<UUID, String> pendingByExam = new HashMap<>();
        pending.forEach(p -> pendingByExam.putIfAbsent(p.examId(), p.orderCode()));
        Map<UUID, List<MyExamBundleRef>> bundlesByExam = repository.findPurchasedBundlesByUser(userId);
        Map<UUID, ClassroomInfo> classroomsById =
                classIds.isEmpty() ? Map.of() : classrooms.findByIds(classIds);

        Map<UUID, Optional<ServingExam>> servingByExam = new HashMap<>();
        Function<UUID, Optional<ServingExam>> serving =
                examId -> servingByExam.computeIfAbsent(examId, exams::findServing);
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
            CardMeta meta = metaFromProduct(product);
            cards.add(card(meta, null, null, null, null, order.orderCode(),
                    bundlesByExam.getOrDefault(product.examId(), List.of()), null, serving));
        }
        AttemptStatusProvider provider = attempts.get();
        for (int i = entitlements.size() - 1; i >= 0; i--) {
            EntitlementRow entitlement = entitlements.get(i);
            boolean isClass = "CLASS".equals(entitlement.source());
            OpenAttempt open = provider.findOpenAttempt(userId, entitlement.id()).orElse(null);
            ResultAttempt result = provider.findLatestResultAttempt(userId, entitlement.id()).orElse(null);
            boolean hasHistory = open != null || result != null
                    || (entitlement.attemptsUsed() + entitlement.attemptsReserved() > 0);
            int netTotal = entitlement.attemptsTotal() - entitlement.attemptsRevoked();
            if (isClass && netTotal <= 0 && !hasHistory) {
                // Class assignment was revoked before the student started any attempt: leave out the empty 0/0 card.
                continue;
            }

            ProductRef product = products.get(entitlement.examId());
            CardMeta meta = product != null
                    ? metaFromProduct(product)
                    : metaFromServing(entitlement.examId(), serving.apply(entitlement.examId()));
            if (meta == null) {
                log.warn("Entitlement {} has neither storefront product nor serving exam {}; its card is left out",
                        entitlement.id(), entitlement.examId());
                continue;
            }

            String pendingOrderCode = isClass ? null : pendingByExam.get(entitlement.examId());
            List<MyExamBundleRef> bundles =
                    isClass ? List.of() : bundlesByExam.getOrDefault(entitlement.examId(), List.of());
            ClassroomInfo classroom =
                    isClass && entitlement.sourceRef() != null ? classroomsById.get(entitlement.sourceRef()) : null;
            cards.add(card(
                    meta,
                    entitlement,
                    rangeGrant(grants.getOrDefault(entitlement.id(), List.of())),
                    open,
                    result,
                    pendingOrderCode,
                    bundles,
                    classroom,
                    serving));
        }
        return cards;
    }

    private MyExamCard card(
            CardMeta meta,
            EntitlementRow entitlement,
            Set<String> grantOptionCodes,
            OpenAttempt open,
            ResultAttempt result,
            String pendingOrderCode,
            List<MyExamBundleRef> bundles,
            ClassroomInfo classroom,
            Function<UUID, Optional<ServingExam>> serving) {
        boolean hasEntitlement = entitlement != null;
        boolean isClass = hasEntitlement && "CLASS".equals(entitlement.source());
        int attemptsLeft = hasEntitlement ? entitlement.attemptsLeft() : 0;
        ExamAccessState state = ExamAccessState.of(hasEntitlement, attemptsLeft, open != null,
                pendingOrderCode != null && !hasEntitlement);
        boolean hasHistory = open != null || result != null
                || (hasEntitlement && entitlement.attemptsUsed() + entitlement.attemptsReserved() > 0);
        boolean onSale = !isClass && meta.saleState() == SaleState.ON_SALE;

        IntRange questions = meta.questionCount();
        IntRange minutes = meta.durationMinutes();
        Optional<ServingExam> servingExam = serving.apply(meta.examId());
        if (servingExam.isPresent()) {
            List<ServingPath> paths = eligiblePaths(servingExam.get().servingPaths(), grantOptionCodes);
            // Nothing the buyer can be given (E25): no range, and not the public catalog range either.
            questions = paths.isEmpty() ? null : orElse(range(paths, ServingPath::questionCount), questions);
            minutes = paths.isEmpty() ? null : orElse(range(paths, ServingPath::durationMinutes), minutes);
        }

        String source = hasEntitlement ? entitlement.source() : null;
        UUID classId = isClass ? entitlement.sourceRef() : null;
        String className = classroom != null ? classroom.name() : null;

        return new MyExamCard(
                hasEntitlement ? entitlement.id() : null,
                meta.code(),
                meta.name(),
                meta.examKind(),
                meta.format(),
                meta.scopeLabel(),
                meta.buildMode(),
                meta.coverUrl(),
                questions,
                minutes,
                attemptsLeft,
                hasEntitlement ? entitlement.attemptsTotal() - entitlement.attemptsRevoked() : 0,
                meta.saleState(),
                state,
                open == null ? null : open.attemptId(),
                open == null ? null : open.state(),
                result == null ? null : result.attemptId(),
                pendingOrderCode != null,
                pendingOrderCode,
                MyExamAction.of(state, attemptsLeft, open != null, result != null, hasHistory, onSale,
                        pendingOrderCode != null),
                bundles,
                source,
                classId,
                className);
    }

    private CardMeta metaFromProduct(ProductRef product) {
        ProductCard stored = read(product.card());
        return new CardMeta(
                product.examId(),
                product.code(),
                stored.name(),
                stored.examKind(),
                stored.format(),
                stored.scopeLabel(),
                stored.buildMode(),
                stored.coverUrl(),
                stored.questionCount(),
                stored.durationMinutes(),
                product.saleState());
    }

    private CardMeta metaFromServing(UUID examId, Optional<ServingExam> servingOpt) {
        if (servingOpt.isEmpty() || servingOpt.get().version() == null) {
            return null;
        }
        ServingExam servingExam = servingOpt.get();
        PublishedVersion version = servingExam.version();
        if (version.code() == null || version.name() == null) {
            return null;
        }
        ExamKindRef kind = resolveKindRef(version);
        LocalizedName scopeLabel = resolveScopeLabel(version);
        String format = version.scope() != null ? version.scope() : "FULL";
        String buildMode = "REVIEW".equals(version.buildMode()) ? "REVIEW" : "STANDARD";
        Listing listing = version.listing();
        String coverUrl = listing != null && listing.coverAssetId() != null
                ? StorefrontProjector.COVER_URL_PREFIX + listing.coverAssetId()
                : null;
        IntRange questions = range(servingExam.servingPaths(), ServingPath::questionCount);
        IntRange minutes = range(servingExam.servingPaths(), ServingPath::durationMinutes);
        return new CardMeta(
                examId,
                version.code(),
                version.name(),
                kind,
                format,
                scopeLabel,
                buildMode,
                coverUrl,
                questions,
                minutes,
                SaleState.DISCONTINUED);
    }

    private ExamKindRef resolveKindRef(PublishedVersion version) {
        if (catalog != null && version.kindItemId() != null) {
            Optional<CatalogItemView> item = catalog.findItem(version.kindItemId());
            if (item.isPresent()) {
                return new ExamKindRef(version.kindCode(), item.get().nameVi(), item.get().nameEn());
            }
        }
        if (version.kindCode() != null) {
            if (catalog != null) {
                for (CatalogItemView item : catalog.listGroupItems(EXAM_GROUP)) {
                    if (item.code().trim().equalsIgnoreCase(version.kindCode().trim())) {
                        return new ExamKindRef(version.kindCode(), item.nameVi(), item.nameEn());
                    }
                }
            }
            return new ExamKindRef(version.kindCode(), version.kindCode(), version.kindCode());
        }
        return new ExamKindRef("EXAM", "EXAM", "EXAM");
    }

    private LocalizedName resolveScopeLabel(PublishedVersion version) {
        if ("PART".equals(version.scope())) {
            List<Part> parts = version.parts();
            String name = (parts == null || parts.isEmpty() || parts.getFirst().name() == null)
                    ? version.partCode()
                    : parts.getFirst().name();
            return name == null ? null : new LocalizedName(name, name);
        }
        if (catalog != null && "SUBJECT".equals(version.scope()) && version.subjectItemId() != null) {
            for (CatalogItemView item : catalog.listGroupItems(SUBJECT_GROUP)) {
                if (item.id().equals(version.subjectItemId())) {
                    return new LocalizedName(item.nameVi(), item.nameEn());
                }
            }
        }
        return null;
    }

    /**
     * The option codes of the grant whose serving paths the card shows (LT 5.1, E21): the oldest grant that still has
     * a usable attempt — the FIFO grant {@code prepare} takes the next attempt from, never a union of the usable
     * grants; when none has one left, the newest grant (the last purchase is what a top-up adds to). A grant without
     * option codes serves every path; an entitlement without grants serves none.
     */
    private static Set<String> rangeGrant(List<GrantOptions> grants) {
        for (GrantOptions grant : grants) {
            if (grant.usable()) {
                return grant.optionCodes();
            }
        }
        return grants.isEmpty() ? Set.of() : grants.get(grants.size() - 1).optionCodes();
    }

    /**
     * The serving paths the one given grant can be served (E25): a path with choices is eligible when every choice is
     * among the grant's purchased option codes; a path with none is eligible for every grant. {@code null} option
     * codes (a paid order with no entitlement yet) keep every serving path. Empty when nothing is eligible.
     */
    private static List<ServingPath> eligiblePaths(List<ServingPath> servingPaths, Set<String> purchasedOptionCodes) {
        if (purchasedOptionCodes == null) {
            return servingPaths;
        }
        Set<String> purchased = normalizeCodes(purchasedOptionCodes);
        return servingPaths.stream()
                .filter(path -> path.choices().stream().allMatch(choice -> choice.code() != null
                        && purchased.contains(choice.code().trim().toLowerCase(Locale.ROOT))))
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

    private record CardMeta(
            UUID examId,
            String code,
            String name,
            ExamKindRef examKind,
            String format,
            LocalizedName scopeLabel,
            String buildMode,
            String coverUrl,
            IntRange questionCount,
            IntRange durationMinutes,
            SaleState saleState) {}

    private enum EmptyClassroomLookup implements ClassroomLookupPort {
        INSTANCE;

        @Override
        public Optional<ClassroomInfo> findById(UUID classId) {
            return Optional.empty();
        }

        @Override
        public Optional<ClassroomInfo> lockActiveOrInactiveById(UUID classId) {
            return Optional.empty();
        }

        @Override
        public Map<UUID, ClassroomInfo> findByIds(Collection<UUID> classIds) {
            return Map.of();
        }

        @Override
        public List<UUID> findActiveMemberUserIds(UUID classId) {
            return List.of();
        }
    }
}
