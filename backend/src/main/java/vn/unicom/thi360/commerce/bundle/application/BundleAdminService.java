package vn.unicom.thi360.commerce.bundle.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.commerce.bundle.api.BundleBenefitsView;
import vn.unicom.thi360.commerce.bundle.api.BundleCandidateResponse;
import vn.unicom.thi360.commerce.bundle.api.BundleDetailResponse;
import vn.unicom.thi360.commerce.bundle.api.BundleItemView;
import vn.unicom.thi360.commerce.bundle.api.BundleRevisionRequest;
import vn.unicom.thi360.commerce.bundle.api.BundleSummaryResponse;
import vn.unicom.thi360.commerce.bundle.api.CreateBundleRequest;
import vn.unicom.thi360.commerce.bundle.api.UpdateBundleRequest;
import vn.unicom.thi360.commerce.bundle.application.BundleExamSnapshotSupport.SubjectCatalog;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleCodeNormalizer;
import vn.unicom.thi360.commerce.bundle.domain.BundleItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleResultView;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.BundleBenefits;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.SnapshotItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleItemRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleVersionRepository;
import vn.unicom.thi360.commerce.pricing.CommercePricingProperties;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Admin use cases for exam bundles: listing, candidates, create, detail, update, publish, discontinue
 * (TM 16.2, 16.4, BS §2).
 */
@Service
public class BundleAdminService {

    private static final int MIN_ITEMS = 2;
    private static final int MAX_ITEMS = 12;
    private static final int MIN_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "code", "name", "status", "listPriceVnd", "updatedAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    private static final String AUDIT_MODULE = "COMMERCE";
    private static final String TARGET_TYPE = "bundle";

    private final BundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;
    private final BundleVersionRepository bundleVersionRepository;
    private final StorefrontProductRepository storefrontRepository;
    private final PublishedExamPort publishedExamPort;
    private final BundleExamSnapshotSupport snapshotSupport;
    private final BundleStorefrontProjector storefrontProjector;
    private final CommercePricingProperties pricingProperties;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final AppMessages messages;
    private final Clock clock;

    public BundleAdminService(
            BundleRepository bundleRepository,
            BundleItemRepository bundleItemRepository,
            BundleVersionRepository bundleVersionRepository,
            StorefrontProductRepository storefrontRepository,
            PublishedExamPort publishedExamPort,
            BundleExamSnapshotSupport snapshotSupport,
            BundleStorefrontProjector storefrontProjector,
            CommercePricingProperties pricingProperties,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            AppMessages messages,
            Clock clock) {
        this.bundleRepository = bundleRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.bundleVersionRepository = bundleVersionRepository;
        this.storefrontRepository = storefrontRepository;
        this.publishedExamPort = publishedExamPort;
        this.snapshotSupport = snapshotSupport;
        this.storefrontProjector = storefrontProjector;
        this.pricingProperties = pricingProperties;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<BundleSummaryResponse> list(
            String q,
            String examKind,
            BundleStatus status,
            Integer page,
            Integer size,
            String sort) {
        PageQuery pageQuery = PageQuery.of(page, size, sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT);
        UUID filterKindId = null;
        if (examKind != null && !examKind.isBlank()) {
            CatalogItemView resolvedKind = snapshotSupport.resolveExamKind(examKind).orElse(null);
            if (resolvedKind == null) {
                return new PageResponse<>(List.of(), pageQuery.page(), pageQuery.size(), 0, 0);
            }
            filterKindId = resolvedKind.id();
        }
        Specification<Bundle> spec = buildListSpec(q, filterKindId, status);
        Page<Bundle> pageResult = bundleRepository.findAll(spec, pageQuery.toPageable());
        List<Bundle> content = pageResult.getContent();
        if (content.isEmpty()) {
            return PageResponse.from(pageResult, b -> null);
        }
        List<UUID> ids = content.stream().map(Bundle::getId).toList();
        Map<UUID, Integer> itemCounts = new HashMap<>(ids.size());
        for (BundleItem item : bundleItemRepository.findByBundleIdInOrderByBundleIdAscPositionAsc(ids)) {
            itemCounts.merge(item.getBundleId(), 1, Integer::sum);
        }
        Map<UUID, SaleState> saleStates = storefrontRepository.findSaleStatesByBundleIds(ids);
        Map<UUID, CatalogItemView> kindsById = snapshotSupport.examKindsById();

        return PageResponse.from(pageResult, b -> {
            CatalogItemView kind = kindsById.get(b.getExamKindItemId());
            String kindCode = kind == null ? b.getExamKindItemId().toString() : kind.code();
            String kindNameVi = kind == null ? kindCode : kind.nameVi();
            String kindNameEn = kind == null ? kindCode : kind.nameEn();
            return new BundleSummaryResponse(
                    b.getId(),
                    b.getCode(),
                    b.getName(),
                    b.getSummary(),
                    b.getExamKindItemId(),
                    kindCode,
                    kindNameVi,
                    kindNameEn,
                    b.getListPriceVnd(),
                    b.getAttemptsPerExam(),
                    b.getStatus(),
                    saleStates.get(b.getId()),
                    b.isHasUnpublishedChanges(),
                    b.getServingVersion(),
                    b.getRevision(),
                    itemCounts.getOrDefault(b.getId(), 0),
                    b.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public List<BundleCandidateResponse> candidates(String examKind) {
        CatalogItemView kindItem = snapshotSupport.resolveExamKind(examKind)
                .orElseThrow(() -> new BusinessRuleException(BundleErrorCodes.BUNDLE_EXAM_KIND_INVALID));
        SubjectCatalog subjects = snapshotSupport.subjectCatalog();
        List<BundleCandidateResponse> candidates = new ArrayList<>();
        for (UUID examId : publishedExamPort.listPublishedExamIds()) {
            ServingExam serving = publishedExamPort.findServing(examId).orElse(null);
            if (!snapshotSupport.isOnSale(serving)) {
                continue;
            }
            PublishedVersion v = serving.version();
            if (!snapshotSupport.matchesExamKind(v, kindItem)) {
                continue;
            }
            if (!snapshotSupport.isAllowedScope(v)) {
                continue;
            }
            candidates.add(snapshotSupport.toCandidate(serving, subjects));
        }
        candidates.sort(Comparator.comparing(BundleCandidateResponse::code, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(candidates);
    }

    @Transactional(readOnly = true)
    public BundleDetailResponse get(UUID id) {
        Bundle bundle = bundleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(BundleErrorCodes.BUNDLE_NOT_FOUND, null));
        List<BundleItem> items = bundleItemRepository.findByBundleIdOrderByPositionAsc(id);
        return buildDetailResponse(bundle, items);
    }

    @Transactional
    public BundleDetailResponse create(CreateBundleRequest request) {
        CatalogItemView kindItem = snapshotSupport.resolveExamKind(request.examKindItemId().toString())
                .orElseThrow(() -> new BusinessRuleException(BundleErrorCodes.BUNDLE_EXAM_KIND_INVALID));

        String trimmedCode = request.code().trim();
        String codeNormalized = BundleCodeNormalizer.normalize(trimmedCode);
        ensureCodeAvailable(codeNormalized, null);

        validateDraftFields(request.listPriceVnd(), request.attemptsPerExam());
        BundleResultView resultView = parseResultView(request.resultView());
        Map<UUID, ServingExam> verifiedExams = validateMemberExams(request.examIds(), kindItem);

        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        UUID bundleId = Ids.newId();

        Bundle bundle = Bundle.createDraft(
                bundleId,
                trimmedCode,
                request.name(),
                trimToNull(request.summary()),
                trimToNull(request.description()),
                kindItem.id(),
                request.listPriceVnd(),
                request.attemptsPerExam(),
                Boolean.TRUE.equals(request.benefitAiExplanation()),
                Boolean.TRUE.equals(request.benefitStrengthWeakness()),
                Boolean.TRUE.equals(request.benefitPractice()),
                trimToNull(request.benefitNote()),
                resultView,
                now,
                actor.id(),
                actor.username());
        bundle = saveBundleHandlingCodeConflict(bundle);
        List<BundleItem> savedItems = replaceItems(bundleId, request.examIds(), false);

        auditRecorder.record(AuditEvent.builder(AUDIT_MODULE, "BUNDLE_CREATED")
                .target(TARGET_TYPE, bundle.getId().toString(), bundle.getCode())
                .after(auditState(bundle, request.examIds()))
                .build());

        return buildDetailResponse(bundle, savedItems, verifiedExams, kindItem, null);
    }

    @Transactional
    public BundleDetailResponse update(UUID id, UpdateBundleRequest request) {
        Bundle bundle = bundleRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException(BundleErrorCodes.BUNDLE_NOT_FOUND, null));
        if (!Objects.equals(request.expectedRevision(), bundle.getRevision())) {
            throw new VersionConflictException();
        }
        if (bundle.getStatus() == BundleStatus.DISCONTINUED) {
            throw new ConflictException(BundleErrorCodes.BUNDLE_ALREADY_DISCONTINUED);
        }

        UUID targetKindId = request.examKindItemId() != null ? request.examKindItemId() : bundle.getExamKindItemId();
        CatalogItemView kindItem = snapshotSupport.resolveExamKind(targetKindId.toString())
                .orElseThrow(() -> new BusinessRuleException(BundleErrorCodes.BUNDLE_EXAM_KIND_INVALID));

        String trimmedCode = request.code().trim();
        String codeNormalized = BundleCodeNormalizer.normalize(trimmedCode);
        ensureCodeAvailable(codeNormalized, bundle.getId());

        validateDraftFields(request.listPriceVnd(), request.attemptsPerExam());
        BundleResultView resultView = request.resultView() == null
                ? bundle.getResultView()
                : parseResultView(request.resultView());
        Map<UUID, ServingExam> verifiedExams = validateMemberExams(request.examIds(), kindItem);

        List<BundleItem> oldItems = bundleItemRepository.findByBundleIdOrderByPositionAsc(id);
        Map<String, Object> beforeState = auditState(bundle, oldItems.stream().map(BundleItem::getExamId).toList());

        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        bundle.updateWorkingCopy(
                trimmedCode,
                request.name(),
                trimToNull(request.summary()),
                trimToNull(request.description()),
                kindItem.id(),
                request.listPriceVnd(),
                request.attemptsPerExam(),
                Boolean.TRUE.equals(request.benefitAiExplanation()),
                Boolean.TRUE.equals(request.benefitStrengthWeakness()),
                Boolean.TRUE.equals(request.benefitPractice()),
                trimToNull(request.benefitNote()),
                resultView,
                now,
                actor.id(),
                actor.username());
        bundle = saveBundleHandlingCodeConflict(bundle);
        List<BundleItem> savedItems = replaceItems(id, request.examIds(), true);

        auditRecorder.record(AuditEvent.builder(AUDIT_MODULE, "BUNDLE_UPDATED")
                .target(TARGET_TYPE, bundle.getId().toString(), bundle.getCode())
                .before(beforeState)
                .after(auditState(bundle, request.examIds()))
                .build());

        SaleState saleState = storefrontRepository.findByBundleId(id).map(s -> s.saleState()).orElse(null);
        return buildDetailResponse(bundle, savedItems, verifiedExams, kindItem, saleState);
    }

    @Transactional
    public BundleDetailResponse publish(UUID id, BundleRevisionRequest request) {
        Bundle bundle = bundleRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException(BundleErrorCodes.BUNDLE_NOT_FOUND, null));
        if (request != null && !Objects.equals(request.expectedRevision(), bundle.getRevision())) {
            throw new VersionConflictException();
        }
        if (bundle.getStatus() == BundleStatus.DISCONTINUED) {
            throw new ConflictException(BundleErrorCodes.BUNDLE_ALREADY_DISCONTINUED);
        }

        // All publish validations MUST run before creating a bundle_version row (AC-7).
        Long price = bundle.getListPriceVnd();
        if (price == null) {
            throw new BusinessRuleException(BundleErrorCodes.LISTING_PRICE_MISSING);
        }
        if (price == 0L) {
            throw new BusinessRuleException(BundleErrorCodes.LISTING_PRICE_ZERO);
        }
        long minAmount = pricingProperties.vnpayMinAmount();
        if (price < minAmount) {
            throw new BusinessRuleException(
                    BundleErrorCodes.LISTING_PRICE_BELOW_MIN,
                    messages.get(BundleErrorCodes.LISTING_PRICE_BELOW_MIN, minAmount));
        }
        if (bundle.getDescription() == null || bundle.getDescription().isBlank()) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_DESCRIPTION_REQUIRED);
        }
        Integer attempts = bundle.getAttemptsPerExam();
        if (attempts == null || attempts < MIN_ATTEMPTS || attempts > MAX_ATTEMPTS) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ATTEMPTS_INVALID);
        }

        CatalogItemView kindItem = snapshotSupport.resolveExamKind(bundle.getExamKindItemId().toString())
                .orElseThrow(() -> new BusinessRuleException(BundleErrorCodes.BUNDLE_EXAM_KIND_INVALID));
        List<BundleItem> items = bundleItemRepository.findByBundleIdOrderByPositionAsc(id);
        List<UUID> examIds = items.stream().map(BundleItem::getExamId).toList();
        Map<UUID, ServingExam> servingExams = validateMemberExams(examIds, kindItem);

        ensureCodeAvailable(bundle.getCodeNormalized(), bundle.getId());

        int nextVersion = bundle.getServingVersion() == null ? 1 : bundle.getServingVersion() + 1;
        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();

        SubjectCatalog subjects = snapshotSupport.subjectCatalog();
        List<SnapshotItem> snapshotItems = new ArrayList<>(items.size());
        for (BundleItem item : items) {
            snapshotItems.add(snapshotSupport.toSnapshotItem(
                    servingExams.get(item.getExamId()), item.getPosition(), subjects));
        }
        BundleSnapshot snapshot = new BundleSnapshot(
                bundle.getName(),
                bundle.getSummary(),
                bundle.getDescription(),
                price,
                attempts,
                new BundleBenefits(
                        bundle.isBenefitAiExplanation(),
                        bundle.isBenefitStrengthWeakness(),
                        bundle.isBenefitPractice(),
                        bundle.getBenefitNote()),
                bundle.getResultView().name(),
                snapshotItems);

        BundleVersion version = new BundleVersion(
                Ids.newId(),
                bundle.getId(),
                nextVersion,
                snapshot,
                now,
                actor.id(),
                actor.username());
        bundleVersionRepository.saveAndFlush(version);

        bundle.markPublished(nextVersion, now, actor.id(), actor.username());
        bundle = bundleRepository.saveAndFlush(bundle);

        try {
            storefrontProjector.project(bundle, version, SaleState.ON_SALE, servingExams, now);
        } catch (DataIntegrityViolationException ex) {
            if (isCodeConstraintViolation(ex)) {
                throw new ConflictException(BundleErrorCodes.BUNDLE_CODE_TAKEN);
            }
            throw ex;
        }

        auditRecorder.record(AuditEvent.builder(AUDIT_MODULE, "BUNDLE_PUBLISHED")
                .target(TARGET_TYPE, bundle.getId().toString(), bundle.getCode())
                .after(Map.of(
                        "code", bundle.getCode(),
                        "versionNo", nextVersion,
                        "listPriceVnd", price,
                        "attemptsPerExam", attempts,
                        "itemCount", items.size()))
                .build());

        return buildDetailResponse(bundle, items, servingExams, kindItem, SaleState.ON_SALE);
    }

    @Transactional
    public BundleDetailResponse discontinue(UUID id, BundleRevisionRequest request) {
        Bundle bundle = bundleRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException(BundleErrorCodes.BUNDLE_NOT_FOUND, null));
        if (request != null && !Objects.equals(request.expectedRevision(), bundle.getRevision())) {
            throw new VersionConflictException();
        }
        if (bundle.getStatus() == BundleStatus.DRAFT) {
            throw new ConflictException(BundleErrorCodes.BUNDLE_NOT_PUBLISHED);
        }
        if (bundle.getStatus() == BundleStatus.DISCONTINUED) {
            throw new ConflictException(BundleErrorCodes.BUNDLE_ALREADY_DISCONTINUED);
        }

        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        bundle.markDiscontinued(now, actor.id(), actor.username());
        bundle = bundleRepository.saveAndFlush(bundle);
        storefrontRepository.markBundleDiscontinued(bundle.getId(), now);

        auditRecorder.record(AuditEvent.builder(AUDIT_MODULE, "BUNDLE_DISCONTINUED")
                .target(TARGET_TYPE, bundle.getId().toString(), bundle.getCode())
                .after(Map.of(
                        "code", bundle.getCode(),
                        "status", BundleStatus.DISCONTINUED.name(),
                        "servingVersion", bundle.getServingVersion() == null ? 0 : bundle.getServingVersion()))
                .build());

        List<BundleItem> items = bundleItemRepository.findByBundleIdOrderByPositionAsc(id);
        return buildDetailResponse(bundle, items);
    }

    // ------------------------------------------------------------------ validation helpers

    private void ensureCodeAvailable(String codeNormalized, UUID selfBundleId) {
        boolean takenInBundle = selfBundleId == null
                ? bundleRepository.existsByCodeNormalized(codeNormalized)
                : bundleRepository.existsByCodeNormalizedAndIdNot(codeNormalized, selfBundleId);
        if (takenInBundle || storefrontRepository.existsCodeNormalizedConflict(codeNormalized, selfBundleId)) {
            throw new ConflictException(BundleErrorCodes.BUNDLE_CODE_TAKEN);
        }
        for (UUID examId : publishedExamPort.listPublishedExamIds()) {
            ServingExam serving = publishedExamPort.findServing(examId).orElse(null);
            if (serving != null && serving.version() != null && serving.version().code() != null
                    && codeNormalized.equals(BundleCodeNormalizer.normalize(serving.version().code()))) {
                throw new ConflictException(BundleErrorCodes.BUNDLE_CODE_TAKEN);
            }
        }
    }

    private void validateDraftFields(Long listPriceVnd, Integer attemptsPerExam) {
        if (listPriceVnd != null && listPriceVnd < 0) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_LIST_PRICE_NEGATIVE);
        }
        if (attemptsPerExam != null && (attemptsPerExam < MIN_ATTEMPTS || attemptsPerExam > MAX_ATTEMPTS)) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ATTEMPTS_INVALID);
        }
    }

    private static BundleResultView parseResultView(String raw) {
        if (raw == null || raw.isBlank()) {
            return BundleResultView.SCORE_AND_ANSWERS;
        }
        try {
            return BundleResultView.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_RESULT_VIEW_INVALID);
        }
    }

    private Map<UUID, ServingExam> validateMemberExams(List<UUID> examIds, CatalogItemView kindItem) {
        if (examIds == null || examIds.size() < MIN_ITEMS || examIds.size() > MAX_ITEMS) {
            throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEMS_COUNT_INVALID);
        }
        Set<UUID> seen = new HashSet<>(examIds.size());
        Map<UUID, ServingExam> resolved = new LinkedHashMap<>(examIds.size());
        for (UUID examId : examIds) {
            if (examId == null) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_INVALID_EXAM);
            }
            if (!seen.add(examId)) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_DUPLICATE);
            }
            ServingExam serving = publishedExamPort.findServing(examId).orElse(null);
            if (serving == null || serving.version() == null) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_INVALID_EXAM);
            }
            if (!snapshotSupport.isOnSale(serving)) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_NOT_ON_SALE);
            }
            if (!snapshotSupport.isAllowedScope(serving.version())) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED);
            }
            if (!snapshotSupport.matchesExamKind(serving.version(), kindItem)) {
                throw new BusinessRuleException(BundleErrorCodes.BUNDLE_ITEM_KIND_MISMATCH);
            }
            resolved.put(examId, serving);
        }
        return resolved;
    }

    private Bundle saveBundleHandlingCodeConflict(Bundle bundle) {
        try {
            return bundleRepository.saveAndFlush(bundle);
        } catch (DataIntegrityViolationException ex) {
            if (isCodeConstraintViolation(ex)) {
                throw new ConflictException(BundleErrorCodes.BUNDLE_CODE_TAKEN);
            }
            throw ex;
        }
    }

    private static boolean isCodeConstraintViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("uq_bundle_code_normalized") || lower.contains("uq_storefront_product_code")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private List<BundleItem> replaceItems(UUID bundleId, List<UUID> examIds, boolean deleteFirst) {
        if (deleteFirst) {
            bundleItemRepository.deleteByBundleId(bundleId);
            bundleItemRepository.flush();
        }
        List<BundleItem> rows = new ArrayList<>(examIds.size());
        for (int i = 0; i < examIds.size(); i++) {
            rows.add(new BundleItem(Ids.newId(), bundleId, examIds.get(i), i + 1));
        }
        return bundleItemRepository.saveAllAndFlush(rows);
    }

    // ------------------------------------------------------------------ query & mapping helpers

    private Specification<Bundle> buildListSpec(String q, UUID examKindItemId, BundleStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (examKindItemId != null) {
                predicates.add(cb.equal(root.get("examKindItemId"), examKindItemId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = likeContainsPattern(q.trim().toLowerCase(Locale.ROOT));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(root.get("codeNormalized"), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String likeContainsPattern(String normalized) {
        return "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private BundleDetailResponse buildDetailResponse(Bundle bundle, List<BundleItem> items) {
        CatalogItemView kindItem = snapshotSupport.resolveExamKind(bundle.getExamKindItemId().toString()).orElse(null);
        SaleState saleState = storefrontRepository.findByBundleId(bundle.getId())
                .map(s -> s.saleState())
                .orElse(null);
        Map<UUID, ServingExam> servings = new LinkedHashMap<>(items.size());
        for (BundleItem item : items) {
            publishedExamPort.findServing(item.getExamId()).ifPresent(s -> servings.put(item.getExamId(), s));
        }
        return buildDetailResponse(bundle, items, servings, kindItem, saleState);
    }

    private BundleDetailResponse buildDetailResponse(
            Bundle bundle,
            List<BundleItem> items,
            Map<UUID, ServingExam> servings,
            CatalogItemView kindItem,
            SaleState saleState) {
        SubjectCatalog subjects = snapshotSupport.subjectCatalog();
        Map<UUID, SnapshotItem> snapshotFallbacks = Map.of();
        if (bundle.getServingVersion() != null) {
            BundleVersion version = bundleVersionRepository
                    .findByBundleIdAndVersionNo(bundle.getId(), bundle.getServingVersion())
                    .orElse(null);
            if (version != null) {
                Map<UUID, SnapshotItem> map = new HashMap<>();
                version.getSnapshot().items().forEach(si -> map.put(si.examId(), si));
                snapshotFallbacks = map;
            }
        }
        List<BundleItemView> itemViews = new ArrayList<>(items.size());
        for (BundleItem item : items) {
            itemViews.add(snapshotSupport.toItemView(
                    item.getExamId(),
                    item.getPosition(),
                    servings.get(item.getExamId()),
                    snapshotFallbacks.get(item.getExamId()),
                    subjects));
        }

        String kindCode = kindItem == null ? bundle.getExamKindItemId().toString() : kindItem.code();
        String kindNameVi = kindItem == null ? kindCode : kindItem.nameVi();
        String kindNameEn = kindItem == null ? kindCode : kindItem.nameEn();

        return new BundleDetailResponse(
                bundle.getId(),
                bundle.getCode(),
                bundle.getName(),
                bundle.getSummary(),
                bundle.getDescription(),
                bundle.getExamKindItemId(),
                kindCode,
                kindNameVi,
                kindNameEn,
                bundle.getListPriceVnd(),
                bundle.getAttemptsPerExam(),
                new BundleBenefitsView(
                        bundle.isBenefitAiExplanation(),
                        bundle.isBenefitStrengthWeakness(),
                        bundle.isBenefitPractice(),
                        bundle.getBenefitNote()),
                bundle.getResultView(),
                bundle.getStatus(),
                saleState,
                bundle.isHasUnpublishedChanges(),
                bundle.getServingVersion(),
                bundle.getRevision(),
                itemViews,
                bundle.getCreatedAt(),
                bundle.getCreatedBy(),
                bundle.getCreatedByUsername(),
                bundle.getUpdatedAt(),
                bundle.getUpdatedBy(),
                bundle.getUpdatedByUsername(),
                bundle.getDiscontinuedAt(),
                bundle.getDiscontinuedBy());
    }

    private static Map<String, Object> auditState(Bundle bundle, List<UUID> examIds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", bundle.getCode());
        map.put("name", bundle.getName());
        map.put("examKindItemId", bundle.getExamKindItemId().toString());
        map.put("listPriceVnd", bundle.getListPriceVnd());
        map.put("attemptsPerExam", bundle.getAttemptsPerExam());
        map.put("resultView", bundle.getResultView().name());
        map.put("status", bundle.getStatus().name());
        map.put("examIds", examIds.stream().map(UUID::toString).toList());
        return map;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
