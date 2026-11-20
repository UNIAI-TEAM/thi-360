package vn.unicom.thi360.commerce.promotion.application;

import jakarta.persistence.criteria.Predicate;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PriceQuote;
import vn.unicom.thi360.commerce.promotion.api.CapWarning;
import vn.unicom.thi360.commerce.promotion.api.CreatePromotionRequest;
import vn.unicom.thi360.commerce.promotion.api.PromotionAudienceClassView;
import vn.unicom.thi360.commerce.promotion.api.PromotionAudienceUserView;
import vn.unicom.thi360.commerce.promotion.api.PromotionChangeLogView;
import vn.unicom.thi360.commerce.promotion.api.PromotionDetail;
import vn.unicom.thi360.commerce.promotion.api.PromotionPricePreviewRequest;
import vn.unicom.thi360.commerce.promotion.api.PromotionRedemptionView;
import vn.unicom.thi360.commerce.promotion.api.PromotionScopeItemView;
import vn.unicom.thi360.commerce.promotion.api.PromotionSummary;
import vn.unicom.thi360.commerce.promotion.api.SuggestedCodeResponse;
import vn.unicom.thi360.commerce.promotion.api.UpdatePromotionRequest;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionChangeAction;
import vn.unicom.thi360.commerce.promotion.domain.PromotionChangeLog;
import vn.unicom.thi360.commerce.promotion.domain.PromotionCodeNormalizer;
import vn.unicom.thi360.commerce.promotion.domain.PromotionFieldChange;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionRedemption;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.PromotionTimeState;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionChangeLogRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRedemptionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.storefront.application.ProductDiscontinuedException;
import vn.unicom.thi360.commerce.storefront.application.StorefrontErrorCodes;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.ExamRef;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.identity.spi.ClassMembershipPort.ClassRef;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Admin promotion/voucher management use cases: listing, detail, create (idempotent), update (in-use field locks and
 * optimistic revision), stop/resume, soft-delete, code suggestion, and price preview (TM 7.3, BS §10).
 */
@Service
public class PromotionAdminService {

    private static final String AUDIT_MODULE = "COMMERCE";
    private static final String TARGET_TYPE = "promotion";
    private static final String EXAM_CATALOG_GROUP = "EXAM";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SUGGESTED_CODE_LENGTH = 8;
    private static final int MIN_CODE_LENGTH = 3;
    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final Sort DEFAULT_LIST_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PromotionRepository promotionRepository;
    private final PromotionScopeItemRepository scopeItemRepository;
    private final PromotionAudienceRepository audienceRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final PromotionChangeLogRepository changeLogRepository;
    private final StorefrontProductRepository storefrontProductRepository;
    private final PromotionCapWarningService capWarningService;
    private final PriceCalculator priceCalculator;
    private final CatalogLookup catalogLookup;
    private final PublishedExamPort publishedExamPort;
    private final ClassMembershipPort classMembershipPort;
    private final UserDirectory userDirectory;
    private final IdempotentCommand idempotentCommand;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final AppMessages messages;
    private final Clock clock;

    public PromotionAdminService(
            PromotionRepository promotionRepository,
            PromotionScopeItemRepository scopeItemRepository,
            PromotionAudienceRepository audienceRepository,
            PromotionRedemptionRepository redemptionRepository,
            PromotionChangeLogRepository changeLogRepository,
            StorefrontProductRepository storefrontProductRepository,
            PromotionCapWarningService capWarningService,
            PriceCalculator priceCalculator,
            CatalogLookup catalogLookup,
            PublishedExamPort publishedExamPort,
            ClassMembershipPort classMembershipPort,
            UserDirectory userDirectory,
            IdempotentCommand idempotentCommand,
            AuditRecorder auditRecorder,
            CurrentUserProvider currentUserProvider,
            AppMessages messages,
            Clock clock) {
        this.promotionRepository = promotionRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.audienceRepository = audienceRepository;
        this.redemptionRepository = redemptionRepository;
        this.changeLogRepository = changeLogRepository;
        this.storefrontProductRepository = storefrontProductRepository;
        this.capWarningService = capWarningService;
        this.priceCalculator = priceCalculator;
        this.catalogLookup = catalogLookup;
        this.publishedExamPort = publishedExamPort;
        this.classMembershipPort = classMembershipPort;
        this.userDirectory = userDirectory;
        this.idempotentCommand = idempotentCommand;
        this.auditRecorder = auditRecorder;
        this.currentUserProvider = currentUserProvider;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionSummary> listPromotions(
            PromotionKind kind,
            PromotionStatus status,
            PromotionTimeState state,
            String q,
            Integer page,
            Integer size) {
        PageQuery pageQuery = PageQuery.of(page, size, null, Set.of(), DEFAULT_LIST_SORT);
        Instant now = clock.instant();
        Specification<Promotion> spec = buildListSpec(kind, status, state, q, now);
        Page<Promotion> result = promotionRepository.findAll(spec, pageQuery.toPageable());
        Map<UUID, List<CapWarning>> warningsByPromo = capWarningService.computeForPage(result.getContent());
        return PageResponse.from(result, p -> toSummary(p, now, warningsByPromo.getOrDefault(p.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PromotionDetail getPromotion(
            UUID id,
            Integer redemptionPage,
            Integer redemptionSize,
            RedemptionState redemptionState,
            String redemptionQ) {
        Promotion promo = promotionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        PageQuery redemptionQuery = PageQuery.of(redemptionPage, redemptionSize, null, Set.of());
        return buildDetail(promo, redemptionQuery, redemptionState, redemptionQ);
    }

    public PromotionDetail createPromotion(String idempotencyKey, CreatePromotionRequest request) {
        return idempotentCommand.execute(
                idempotencyKey,
                "PROMOTION_CREATE",
                request,
                PromotionDetail.class,
                () -> doCreatePromotion(request));
    }

    protected PromotionDetail doCreatePromotion(CreatePromotionRequest request) {
        ValidatedSpec spec = validateCreate(request);
        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        UUID id = Ids.newId();

        Promotion promo = spec.kind() == PromotionKind.AUTO
                ? Promotion.auto(id, spec.name(), spec.description(), spec.discountType(), spec.discountValue(),
                        spec.maxDiscountVnd(), spec.startsAt(), spec.endsAt(), spec.totalLimit(), spec.scope(),
                        spec.status(), now, actor.id(), actor.username())
                : Promotion.voucher(id, spec.name(), spec.description(), spec.code(), spec.discountType(),
                        spec.discountValue(), spec.maxDiscountVnd(), spec.startsAt(), spec.endsAt(),
                        spec.totalLimit(), spec.perUserLimit(), Boolean.TRUE.equals(spec.combinable()), spec.scope(),
                        spec.audience(), spec.status(), now, actor.id(), actor.username());

        if (spec.kind() == PromotionKind.VOUCHER) {
            String normalized = PromotionCodeNormalizer.normalize(spec.code());
            if (promotionRepository.existsByCodeNormalized(normalized)) {
                throw new ConflictException("VOUCHER_CODE_TAKEN");
            }
        }
        promo = savePromotionHandlingCodeRace(promo);

        List<PromotionScopeItem> scopeItems = replaceScopeItems(id, spec.scope(), spec.examKindIds(), spec.examIds(),
                false);
        List<PromotionAudience> audiences = replaceAudienceTargets(id, spec.audience(), spec.audienceClassIds(),
                spec.audienceUserIds(), false);

        Map<String, Object> afterSnapshot = snapshot(promo, spec.examKindIds(), spec.examIds(),
                spec.audienceClassIds(), spec.audienceUserIds());
        List<PromotionFieldChange> changes = creationChanges(afterSnapshot);
        recordChangeAndAudit(promo, PromotionChangeAction.CREATED, "PROMOTION_CREATED",
                actor, now, changes, null, afterSnapshot);

        return buildDetail(promo, scopeItems, audiences, PageQuery.of(0, PageQuery.DEFAULT_SIZE, null, Set.of()),
                null, null);
    }

    @Transactional
    public PromotionDetail updatePromotion(UUID id, UpdatePromotionRequest request) {
        Promotion promo = promotionRepository.lockActiveById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        if (!Objects.equals(request.expectedRevision(), promo.getRevision())) {
            throw new VersionConflictException();
        }

        List<PromotionScopeItem> existingScopeItems = scopeItemRepository.findByPromotionId(id);
        List<PromotionAudience> existingAudiences = audienceRepository.findByPromotionId(id);
        TargetIds currentTargets = TargetIds.from(existingScopeItems, existingAudiences);
        Map<String, Object> beforeSnapshot = snapshot(promo, currentTargets.examKindIds(), currentTargets.examIds(),
                currentTargets.classIds(), currentTargets.userIds());

        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();

        List<PromotionScopeItem> finalScopeItems = existingScopeItems;
        List<PromotionAudience> finalAudiences = existingAudiences;
        TargetIds finalTargets = currentTargets;

        if (promo.getUsedCount() > 0) {
            applyInUseUpdate(promo, request, currentTargets, now, actor);
            promotionRepository.saveAndFlush(promo);
        } else {
            ValidatedSpec spec = mergeAndValidateUnusedUpdate(promo, request, currentTargets, now);
            String previousNormalized = promo.getCodeNormalized();
            String nextNormalized = spec.code() == null ? null : PromotionCodeNormalizer.normalize(spec.code());
            if (spec.kind() == PromotionKind.VOUCHER && !Objects.equals(previousNormalized, nextNormalized)
                    && promotionRepository.existsByCodeNormalizedAndIdNot(nextNormalized, promo.getId())) {
                throw new ConflictException("VOUCHER_CODE_TAKEN");
            }
            promo.updateAllFields(
                    spec.name(), spec.description(), spec.code(), spec.discountType(), spec.discountValue(),
                    spec.maxDiscountVnd(), spec.startsAt(), spec.endsAt(), spec.totalLimit(), spec.perUserLimit(),
                    spec.combinable(), spec.scope(), spec.audience(), spec.status(), now, actor.id(),
                    actor.username());
            promo = savePromotionHandlingCodeRace(promo);

            finalScopeItems = replaceScopeItems(id, spec.scope(), spec.examKindIds(), spec.examIds(), true);
            finalAudiences = replaceAudienceTargets(id, spec.audience(), spec.audienceClassIds(),
                    spec.audienceUserIds(), true);
            finalTargets = new TargetIds(spec.examKindIds(), spec.examIds(), spec.audienceClassIds(),
                    spec.audienceUserIds());
        }

        Map<String, Object> afterSnapshot = snapshot(promo, finalTargets.examKindIds(), finalTargets.examIds(),
                finalTargets.classIds(), finalTargets.userIds());
        List<PromotionFieldChange> changes = diffSnapshots(beforeSnapshot, afterSnapshot);
        recordChangeAndAudit(promo, PromotionChangeAction.UPDATED, "PROMOTION_UPDATED",
                actor, now, changes, beforeSnapshot, afterSnapshot);

        return buildDetail(promo, finalScopeItems, finalAudiences,
                PageQuery.of(0, PageQuery.DEFAULT_SIZE, null, Set.of()), null, null);
    }

    @Transactional
    public PromotionDetail stopPromotion(UUID id, Long expectedRevision) {
        Promotion promo = promotionRepository.lockActiveById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        if (!Objects.equals(expectedRevision, promo.getRevision())) {
            throw new VersionConflictException();
        }
        PromotionStatus oldStatus = promo.getStatus();
        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        promo.stop(now, actor.id(), actor.username());
        promotionRepository.saveAndFlush(promo);

        List<PromotionFieldChange> changes = List.of(
                new PromotionFieldChange("status", oldStatus.name(), PromotionStatus.STOPPED.name()));
        recordChangeAndAudit(promo, PromotionChangeAction.STOPPED, "PROMOTION_STOPPED",
                actor, now, changes, Map.of("status", oldStatus.name()), Map.of("status", PromotionStatus.STOPPED.name()));

        return buildDetail(promo, PageQuery.of(0, PageQuery.DEFAULT_SIZE, null, Set.of()), null, null);
    }

    @Transactional
    public PromotionDetail resumePromotion(UUID id, Long expectedRevision) {
        Promotion promo = promotionRepository.lockActiveById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        if (!Objects.equals(expectedRevision, promo.getRevision())) {
            throw new VersionConflictException();
        }
        Instant now = clock.instant();
        if (!now.isBefore(promo.getEndsAt())) {
            throw new ConflictException("PROMOTION_ENDED");
        }
        PromotionStatus oldStatus = promo.getStatus();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        promo.resume(now, actor.id(), actor.username());
        promotionRepository.saveAndFlush(promo);

        List<PromotionFieldChange> changes = List.of(
                new PromotionFieldChange("status", oldStatus.name(), PromotionStatus.ACTIVE.name()));
        recordChangeAndAudit(promo, PromotionChangeAction.RESUMED, "PROMOTION_RESUMED",
                actor, now, changes, Map.of("status", oldStatus.name()), Map.of("status", PromotionStatus.ACTIVE.name()));

        return buildDetail(promo, PageQuery.of(0, PageQuery.DEFAULT_SIZE, null, Set.of()), null, null);
    }

    @Transactional
    public void deletePromotion(UUID id) {
        Promotion promo = promotionRepository.lockActiveById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));
        if (promo.getUsedCount() > 0) {
            throw new ConflictException("PROMOTION_IN_USE");
        }
        Instant now = clock.instant();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        promo.softDelete(now, actor.id(), actor.username());
        promotionRepository.saveAndFlush(promo);

        List<PromotionFieldChange> changes = List.of(
                new PromotionFieldChange("deletedAt", null, now.toString()));
        recordChangeAndAudit(promo, PromotionChangeAction.DELETED, "PROMOTION_DELETED",
                actor, now, changes, Map.of("deletedAt", ""), Map.of("deletedAt", now.toString()));
    }

    @Transactional(readOnly = true)
    public SuggestedCodeResponse suggestCode() {
        for (int attempt = 0; attempt < 25; attempt++) {
            String candidate = generateCode();
            if (!promotionRepository.existsByCodeNormalized(PromotionCodeNormalizer.normalize(candidate))) {
                return new SuggestedCodeResponse(candidate);
            }
        }
        throw new IllegalStateException("Failed to generate a unique suggested voucher code");
    }

    @Transactional(readOnly = true)
    public PriceQuote pricePreview(PromotionPricePreviewRequest request) {
        StoredProduct stored = storefrontProductRepository.findByExamId(request.examId()).orElse(null);
        if (stored == null || stored.saleState() == SaleState.UNAVAILABLE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        if (stored.saleState() == SaleState.DISCONTINUED) {
            throw new ProductDiscontinuedException(stored.code(), stored.name());
        }
        if (stored.saleState() != SaleState.ON_SALE) {
            throw new NotFoundException(StorefrontErrorCodes.PRODUCT_NOT_FOUND, null);
        }
        promotionRepository.findByIdAndDeletedAtIsNull(request.promotionId())
                .orElseThrow(() -> new NotFoundException("PROMOTION_NOT_FOUND", null));

        PricedProduct product = new PricedProduct(
                stored.examId(), stored.examKindItemId(), stored.listPriceVnd());
        QuoteCalculation calc = priceCalculator.previewPromotion(product, request.promotionId());
        return PriceQuote.from(stored, calc, false);
    }

    // ------------------------------------------------------------------ validation

    private ValidatedSpec validateCreate(CreatePromotionRequest req) {
        String name = normalizeName(req.name());
        String description = normalizeDescription(req.description(), false, null);
        PromotionStatus status = req.status() == null ? PromotionStatus.ACTIVE : req.status();
        return validateSpec(
                req.kind(),
                name,
                description,
                req.code(),
                req.discountType(),
                req.discountValue(),
                req.maxDiscountVnd(),
                req.startsAt(),
                req.endsAt(),
                req.totalLimit(),
                req.perUserLimit(),
                req.combinable(),
                req.scope(),
                req.examKindIds(),
                req.examIds(),
                req.audience(),
                req.audienceClassIds(),
                req.audienceUserIds(),
                status);
    }

    private ValidatedSpec mergeAndValidateUnusedUpdate(
            Promotion promo, UpdatePromotionRequest req, TargetIds currentTargets, Instant now) {
        if (req.kind() != null && req.kind() != promo.getKind()) {
            throw fieldValidation("kind", "INVALID", "validation.promotion.kind.immutable");
        }
        String name = req.name() != null ? normalizeName(req.name()) : promo.getName();
        String description = normalizeDescription(req.description(), Boolean.TRUE.equals(req.clearDescription()),
                promo.getDescription());
        String code = req.code() != null ? req.code() : promo.getCode();
        DiscountType discountType = req.discountType() != null ? req.discountType() : promo.getDiscountType();
        long discountValue = req.discountValue() != null ? req.discountValue() : promo.getDiscountValue();

        Long maxDiscountVnd;
        if (Boolean.TRUE.equals(req.clearMaxDiscountVnd())) {
            if (req.maxDiscountVnd() != null) {
                throw fieldValidation("maxDiscountVnd", "INVALID", "validation.promotion.maxDiscountVnd.invalid");
            }
            maxDiscountVnd = null;
        } else if (req.maxDiscountVnd() != null) {
            maxDiscountVnd = req.maxDiscountVnd();
        } else if (discountType == DiscountType.AMOUNT) {
            maxDiscountVnd = null;
        } else {
            maxDiscountVnd = promo.getMaxDiscountVnd();
        }

        Instant startsAt = req.startsAt() != null ? req.startsAt() : promo.getStartsAt();
        Instant endsAt = req.endsAt() != null ? req.endsAt() : promo.getEndsAt();

        Integer totalLimit;
        if (Boolean.TRUE.equals(req.clearTotalLimit())) {
            if (req.totalLimit() != null) {
                throw fieldValidation("totalLimit", "INVALID", "validation.promotion.totalLimit.invalid");
            }
            totalLimit = null;
        } else {
            totalLimit = req.totalLimit() != null ? req.totalLimit() : promo.getTotalLimit();
        }

        Integer perUserLimit;
        if (Boolean.TRUE.equals(req.clearPerUserLimit())) {
            if (req.perUserLimit() != null) {
                throw fieldValidation("perUserLimit", "INVALID", "validation.promotion.perUserLimit.invalid");
            }
            perUserLimit = null;
        } else {
            perUserLimit = req.perUserLimit() != null ? req.perUserLimit() : promo.getPerUserLimit();
        }

        Boolean combinable = req.combinable() != null ? req.combinable() : promo.getCombinable();
        PromotionScope scope = req.scope() != null ? req.scope() : promo.getScope();
        List<UUID> examKindIds = req.examKindIds() != null
                ? req.examKindIds()
                : (scope == promo.getScope() ? currentTargets.examKindIds() : List.of());
        List<UUID> examIds = req.examIds() != null
                ? req.examIds()
                : (scope == promo.getScope() ? currentTargets.examIds() : List.of());

        PromotionAudienceType audience = req.audience() != null ? req.audience() : promo.getAudience();
        List<UUID> classIds = req.audienceClassIds() != null
                ? req.audienceClassIds()
                : (audience == promo.getAudience() ? currentTargets.classIds() : List.of());
        List<UUID> userIds = req.audienceUserIds() != null
                ? req.audienceUserIds()
                : (audience == promo.getAudience() ? currentTargets.userIds() : List.of());

        PromotionStatus status = req.status() != null ? req.status() : promo.getStatus();
        ValidatedSpec spec = validateSpec(
                promo.getKind(), name, description, code, discountType, discountValue, maxDiscountVnd,
                startsAt, endsAt, totalLimit, perUserLimit, combinable, scope, examKindIds, examIds,
                audience, classIds, userIds, status);

        if (spec.status() == PromotionStatus.ACTIVE && promo.getStatus() != PromotionStatus.ACTIVE
                && !now.isBefore(spec.endsAt())) {
            throw new ConflictException("PROMOTION_ENDED");
        }
        return spec;
    }

    private void applyInUseUpdate(
            Promotion promo, UpdatePromotionRequest req, TargetIds currentTargets, Instant now, CurrentUser actor) {
        if (hasLockedFieldChange(promo, req, currentTargets)) {
            throw new ConflictException("PROMOTION_IN_USE");
        }
        String newName = req.name() != null ? normalizeName(req.name()) : promo.getName();
        String newDescription = normalizeDescription(
                req.description(), Boolean.TRUE.equals(req.clearDescription()), promo.getDescription());

        Instant newEndsAt = promo.getEndsAt();
        if (req.endsAt() != null) {
            if (req.endsAt().isBefore(now) || !req.endsAt().isAfter(promo.getStartsAt())) {
                throw new BusinessRuleException("PROMOTION_ENDS_AT_PAST");
            }
            newEndsAt = req.endsAt();
        }

        Integer newTotalLimit;
        if (Boolean.TRUE.equals(req.clearTotalLimit())) {
            if (req.totalLimit() != null) {
                throw fieldValidation("totalLimit", "INVALID", "validation.promotion.totalLimit.invalid");
            }
            newTotalLimit = null;
        } else if (req.totalLimit() != null) {
            if (req.totalLimit() < 0) {
                throw fieldValidation("totalLimit", "INVALID", "validation.promotion.totalLimit.invalid");
            }
            if (req.totalLimit() < promo.getUsedCount()) {
                throw new BusinessRuleException("PROMOTION_TOTAL_LIMIT_BELOW_USED");
            }
            newTotalLimit = req.totalLimit();
        } else {
            newTotalLimit = promo.getTotalLimit();
        }

        PromotionStatus newStatus = req.status() != null ? req.status() : promo.getStatus();
        if (newStatus == PromotionStatus.ACTIVE && promo.getStatus() != PromotionStatus.ACTIVE
                && !now.isBefore(newEndsAt)) {
            throw new ConflictException("PROMOTION_ENDED");
        }

        promo.updateMetadata(newName, newDescription, newEndsAt, newTotalLimit, newStatus, now,
                actor.id(), actor.username());
    }

    private static boolean hasLockedFieldChange(
            Promotion promo, UpdatePromotionRequest req, TargetIds currentTargets) {
        if (req.kind() != null && req.kind() != promo.getKind()) {
            return true;
        }
        if (req.code() != null && !Objects.equals(req.code().trim(), promo.getCode())) {
            return true;
        }
        if (req.discountType() != null && req.discountType() != promo.getDiscountType()) {
            return true;
        }
        if (req.discountValue() != null && req.discountValue().longValue() != promo.getDiscountValue()) {
            return true;
        }
        if (Boolean.TRUE.equals(req.clearMaxDiscountVnd()) && promo.getMaxDiscountVnd() != null) {
            return true;
        }
        if (req.maxDiscountVnd() != null && !Objects.equals(req.maxDiscountVnd(), promo.getMaxDiscountVnd())) {
            return true;
        }
        if (req.startsAt() != null && !req.startsAt().equals(promo.getStartsAt())) {
            return true;
        }
        if (Boolean.TRUE.equals(req.clearPerUserLimit()) && promo.getPerUserLimit() != null) {
            return true;
        }
        if (req.perUserLimit() != null && !Objects.equals(req.perUserLimit(), promo.getPerUserLimit())) {
            return true;
        }
        if (req.combinable() != null && !Objects.equals(req.combinable(), promo.getCombinable())) {
            return true;
        }
        if (req.scope() != null && req.scope() != promo.getScope()) {
            return true;
        }
        if (req.examKindIds() != null && !Set.copyOf(req.examKindIds()).equals(Set.copyOf(currentTargets.examKindIds()))) {
            return true;
        }
        if (req.examIds() != null && !Set.copyOf(req.examIds()).equals(Set.copyOf(currentTargets.examIds()))) {
            return true;
        }
        if (req.audience() != null && req.audience() != promo.getAudience()) {
            return true;
        }
        if (req.audienceClassIds() != null
                && !Set.copyOf(req.audienceClassIds()).equals(Set.copyOf(currentTargets.classIds()))) {
            return true;
        }
        if (req.audienceUserIds() != null
                && !Set.copyOf(req.audienceUserIds()).equals(Set.copyOf(currentTargets.userIds()))) {
            return true;
        }
        return false;
    }

    private ValidatedSpec validateSpec(
            PromotionKind kind,
            String name,
            String description,
            String rawCode,
            DiscountType discountType,
            Long discountValue,
            Long maxDiscountVnd,
            Instant startsAt,
            Instant endsAt,
            Integer totalLimit,
            Integer perUserLimit,
            Boolean combinable,
            PromotionScope scope,
            List<UUID> rawExamKindIds,
            List<UUID> rawExamIds,
            PromotionAudienceType rawAudience,
            List<UUID> rawClassIds,
            List<UUID> rawUserIds,
            PromotionStatus status) {

        if (scope == PromotionScope.BUNDLE) {
            throw fieldValidation("scope", "INVALID", "validation.promotion.scope.invalid");
        }
        if (discountValue == null || discountValue <= 0) {
            throw fieldValidation("discountValue", "INVALID", "validation.promotion.discountValue.invalid");
        }
        if (discountType == DiscountType.PERCENT) {
            if (discountValue < 1 || discountValue > 100) {
                throw fieldValidation("discountValue", "INVALID", "validation.promotion.discountValue.invalid");
            }
            if (maxDiscountVnd != null && maxDiscountVnd <= 0) {
                throw fieldValidation("maxDiscountVnd", "INVALID", "validation.promotion.maxDiscountVnd.invalid");
            }
        } else if (maxDiscountVnd != null) {
            throw fieldValidation("maxDiscountVnd", "NOT_ALLOWED", "validation.promotion.maxDiscountVnd.notAllowed");
        }

        if (!endsAt.isAfter(startsAt)) {
            throw fieldValidation("endsAt", "INVALID", "validation.promotion.endsAt.invalid");
        }
        if (totalLimit != null && totalLimit < 1) {
            throw fieldValidation("totalLimit", "INVALID", "validation.promotion.totalLimit.invalid");
        }

        List<UUID> classIds = deduplicateIds(rawClassIds, "audienceClassIds");
        List<UUID> userIds = deduplicateIds(rawUserIds, "audienceUserIds");

        String trimmedCode = null;
        PromotionAudienceType audience = null;
        Boolean resolvedCombinable = null;

        if (kind == PromotionKind.AUTO) {
            if (rawCode != null) {
                throw fieldValidation("code", "NOT_ALLOWED", "validation.promotion.code.notAllowed");
            }
            if (perUserLimit != null) {
                throw fieldValidation("perUserLimit", "NOT_ALLOWED", "validation.promotion.perUserLimit.notAllowed");
            }
            if (rawAudience != null) {
                throw fieldValidation("audience", "NOT_ALLOWED", "validation.promotion.audience.invalid");
            }
            if (combinable != null) {
                throw fieldValidation("combinable", "NOT_ALLOWED", "validation.promotion.combinable.notAllowed");
            }
            if (!classIds.isEmpty() || !userIds.isEmpty()) {
                throw fieldValidation("audienceClassIds", "NOT_ALLOWED",
                        "validation.promotion.audienceTargets.notAllowed");
            }
        } else {
            if (rawCode == null || rawCode.isBlank()) {
                throw fieldValidation("code", "REQUIRED", "validation.promotion.code.required");
            }
            trimmedCode = rawCode.trim();
            if (trimmedCode.length() < MIN_CODE_LENGTH || trimmedCode.length() > MAX_CODE_LENGTH) {
                throw fieldValidation("code", "INVALID", "validation.promotion.code.invalid");
            }
            if (perUserLimit != null && perUserLimit < 1) {
                throw fieldValidation("perUserLimit", "INVALID", "validation.promotion.perUserLimit.invalid");
            }
            audience = rawAudience == null ? PromotionAudienceType.ALL : rawAudience;
            resolvedCombinable = combinable != null && combinable;
            if (audience == PromotionAudienceType.SELECTED) {
                if (classIds.isEmpty() && userIds.isEmpty()) {
                    throw fieldValidation("audienceClassIds", "EMPTY", "validation.promotion.audienceTargets.empty");
                }
            } else if (!classIds.isEmpty() || !userIds.isEmpty()) {
                throw fieldValidation("audienceClassIds", "NOT_ALLOWED",
                        "validation.promotion.audienceTargets.notAllowed");
            }
        }

        List<UUID> examKindIds = deduplicateIds(rawExamKindIds, "examKindIds");
        List<UUID> examIds = deduplicateIds(rawExamIds, "examIds");
        validateScopeTargets(scope, examKindIds, examIds);

        return new ValidatedSpec(
                kind, name, description, trimmedCode, discountType, discountValue, maxDiscountVnd,
                startsAt, endsAt, totalLimit, perUserLimit, resolvedCombinable, scope,
                examKindIds, examIds, audience, classIds, userIds,
                status == null ? PromotionStatus.ACTIVE : status);
    }

    private void validateScopeTargets(PromotionScope scope, List<UUID> examKindIds, List<UUID> examIds) {
        switch (scope) {
            case ALL -> {
                if (!examKindIds.isEmpty()) {
                    throw fieldValidation("examKindIds", "NOT_ALLOWED", "validation.promotion.scopeItems.notAllowed");
                }
                if (!examIds.isEmpty()) {
                    throw fieldValidation("examIds", "NOT_ALLOWED", "validation.promotion.scopeItems.notAllowed");
                }
            }
            case EXAM_KIND -> {
                if (!examIds.isEmpty()) {
                    throw fieldValidation("examIds", "NOT_ALLOWED", "validation.promotion.scopeItems.notAllowed");
                }
                if (examKindIds.isEmpty()) {
                    throw fieldValidation("examKindIds", "EMPTY", "validation.promotion.scopeItems.empty");
                }
                for (UUID examKindId : examKindIds) {
                    CatalogItemView item = catalogLookup.findItem(examKindId).orElse(null);
                    if (item == null || !EXAM_CATALOG_GROUP.equals(item.groupCode())) {
                        throw new BusinessRuleException("PROMOTION_SCOPE_INVALID");
                    }
                }
            }
            case EXAM -> {
                if (!examKindIds.isEmpty()) {
                    throw fieldValidation("examKindIds", "NOT_ALLOWED", "validation.promotion.scopeItems.notAllowed");
                }
                if (examIds.isEmpty()) {
                    throw fieldValidation("examIds", "EMPTY", "validation.promotion.scopeItems.empty");
                }
                for (UUID examId : examIds) {
                    if (!isPublishedExam(examId)) {
                        throw new BusinessRuleException("PROMOTION_SCOPE_INVALID");
                    }
                }
            }
            case BUNDLE -> throw fieldValidation("scope", "INVALID", "validation.promotion.scope.invalid");
        }
    }

    private boolean isPublishedExam(UUID examId) {
        if (storefrontProductRepository.existsOnSaleByExamId(examId)) {
            return true;
        }
        return publishedExamPort.findServing(examId)
                .map(serving -> "PUBLISHED".equals(serving.status()))
                .orElse(false);
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw fieldValidation("name", "REQUIRED", "validation.promotion.name.notBlank");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw fieldValidation("name", "SIZE", "validation.promotion.name.size");
        }
        return trimmed;
    }

    private String normalizeDescription(String rawDescription, boolean clear, String fallback) {
        if (clear) {
            if (rawDescription != null) {
                throw fieldValidation("description", "INVALID", "validation.promotion.description.size");
            }
            return null;
        }
        if (rawDescription == null) {
            return fallback;
        }
        String trimmed = rawDescription.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw fieldValidation("description", "SIZE", "validation.promotion.description.size");
        }
        return trimmed;
    }

    private List<UUID> deduplicateIds(List<UUID> ids, String field) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>(ids.size());
        for (UUID id : ids) {
            if (id == null) {
                throw fieldValidation(field, "INVALID", "validation.promotion.scopeItems.notAllowed");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
    }

    private ValidationException fieldValidation(String field, String code, String messageKey) {
        String msg = messages.get(messageKey);
        return new ValidationException(List.of(new FieldError(field, code, msg)));
    }

    // ------------------------------------------------------------------ persistence helpers

    private Promotion savePromotionHandlingCodeRace(Promotion promo) {
        try {
            return promotionRepository.saveAndFlush(promo);
        } catch (DataIntegrityViolationException ex) {
            if (isVoucherCodeConstraintViolation(ex)) {
                throw new ConflictException("VOUCHER_CODE_TAKEN");
            }
            throw ex;
        }
    }

    private static boolean isVoucherCodeConstraintViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("uq_promotion_code_normalized")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<PromotionScopeItem> replaceScopeItems(
            UUID promotionId, PromotionScope scope, List<UUID> examKindIds, List<UUID> examIds, boolean deleteFirst) {
        if (deleteFirst) {
            scopeItemRepository.deleteByPromotionId(promotionId);
            scopeItemRepository.flush();
        }
        List<PromotionScopeItem> rows = new ArrayList<>();
        if (scope == PromotionScope.EXAM_KIND) {
            for (UUID kindId : examKindIds) {
                rows.add(PromotionScopeItem.forExamKind(Ids.newId(), promotionId, kindId));
            }
        } else if (scope == PromotionScope.EXAM) {
            for (UUID examId : examIds) {
                rows.add(PromotionScopeItem.forExam(Ids.newId(), promotionId, examId));
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        return scopeItemRepository.saveAllAndFlush(rows);
    }

    private List<PromotionAudience> replaceAudienceTargets(
            UUID promotionId, PromotionAudienceType audience, List<UUID> classIds, List<UUID> userIds,
            boolean deleteFirst) {
        if (deleteFirst) {
            audienceRepository.deleteByPromotionId(promotionId);
            audienceRepository.flush();
        }
        if (audience != PromotionAudienceType.SELECTED) {
            return List.of();
        }
        List<PromotionAudience> rows = new ArrayList<>(classIds.size() + userIds.size());
        for (UUID classId : classIds) {
            rows.add(PromotionAudience.forClass(Ids.newId(), promotionId, classId));
        }
        for (UUID userId : userIds) {
            rows.add(PromotionAudience.forUser(Ids.newId(), promotionId, userId));
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        return audienceRepository.saveAllAndFlush(rows);
    }

    // ------------------------------------------------------------------ audit & change log

    private void recordChangeAndAudit(
            Promotion promo,
            PromotionChangeAction action,
            String auditAction,
            CurrentUser actor,
            Instant occurredAt,
            List<PromotionFieldChange> changes,
            Map<String, Object> before,
            Map<String, Object> after) {
        changeLogRepository.saveAndFlush(new PromotionChangeLog(
                Ids.newId(), promo.getId(), action, actor.id(), actor.username(), changes, occurredAt));

        String label = promo.getCode() != null ? promo.getCode() : promo.getName();
        auditRecorder.record(AuditEvent.builder(AUDIT_MODULE, auditAction)
                .target(TARGET_TYPE, promo.getId().toString(), label)
                .before(before)
                .after(after)
                .build());
    }

    private static Map<String, Object> snapshot(
            Promotion p, List<UUID> examKindIds, List<UUID> examIds, List<UUID> classIds, List<UUID> userIds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", p.getKind().name());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("code", p.getCode());
        map.put("discountType", p.getDiscountType().name());
        map.put("discountValue", p.getDiscountValue());
        map.put("maxDiscountVnd", p.getMaxDiscountVnd());
        map.put("startsAt", p.getStartsAt().toString());
        map.put("endsAt", p.getEndsAt().toString());
        map.put("totalLimit", p.getTotalLimit());
        map.put("perUserLimit", p.getPerUserLimit());
        map.put("combinable", p.getCombinable());
        map.put("scope", p.getScope().name());
        map.put("examKindIds", sortedUuidStrings(examKindIds));
        map.put("examIds", sortedUuidStrings(examIds));
        map.put("audience", p.getAudience() == null ? null : p.getAudience().name());
        map.put("audienceClassIds", sortedUuidStrings(classIds));
        map.put("audienceUserIds", sortedUuidStrings(userIds));
        map.put("status", p.getStatus().name());
        return map;
    }

    private static List<String> sortedUuidStrings(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(UUID::toString).sorted().toList();
    }

    private static List<PromotionFieldChange> creationChanges(Map<String, Object> after) {
        List<PromotionFieldChange> changes = new ArrayList<>();
        after.forEach((field, val) -> {
            if (val != null && !(val instanceof Collection<?> col && col.isEmpty())) {
                changes.add(new PromotionFieldChange(field, null, val));
            }
        });
        return changes;
    }

    private static List<PromotionFieldChange> diffSnapshots(Map<String, Object> before, Map<String, Object> after) {
        List<PromotionFieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String field = entry.getKey();
            Object oldVal = before.get(field);
            Object newVal = entry.getValue();
            if (!Objects.equals(oldVal, newVal)) {
                changes.add(new PromotionFieldChange(field, oldVal, newVal));
            }
        }
        return changes;
    }

    // ------------------------------------------------------------------ query & view assembly

    private Specification<Promotion> buildListSpec(
            PromotionKind kind, PromotionStatus status, PromotionTimeState state, String q, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (state != null) {
                switch (state) {
                    case UPCOMING -> predicates.add(cb.greaterThan(root.get("startsAt"), now));
                    case ENDED -> predicates.add(cb.lessThanOrEqualTo(root.get("endsAt"), now));
                    case RUNNING -> {
                        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), now));
                        predicates.add(cb.greaterThan(root.get("endsAt"), now));
                    }
                }
            }
            if (q != null && !q.isBlank()) {
                String pattern = likeContainsPattern(q.trim().toLowerCase(Locale.ROOT));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("code")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String likeContainsPattern(String normalized) {
        return "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private PromotionSummary toSummary(Promotion p, Instant now, List<CapWarning> capWarnings) {
        return new PromotionSummary(
                p.getId(),
                p.getKind(),
                p.getName(),
                p.getDescription(),
                p.getCode(),
                p.getDiscountType(),
                p.getDiscountValue(),
                p.getMaxDiscountVnd(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getTotalLimit(),
                p.getPerUserLimit(),
                p.getCombinable(),
                p.getScope(),
                p.getAudience(),
                p.getStatus(),
                PromotionTimeState.at(now, p.getStartsAt(), p.getEndsAt()),
                p.getUsedCount(),
                p.getOverLimitCount(),
                p.getRevision(),
                capWarnings,
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private PromotionDetail buildDetail(
            Promotion promo, PageQuery redemptionQuery, RedemptionState redemptionState, String redemptionQ) {
        List<PromotionScopeItem> scopeItems = scopeItemRepository.findByPromotionId(promo.getId());
        List<PromotionAudience> audiences = audienceRepository.findByPromotionId(promo.getId());
        return buildDetail(promo, scopeItems, audiences, redemptionQuery, redemptionState, redemptionQ);
    }

    private PromotionDetail buildDetail(
            Promotion promo,
            List<PromotionScopeItem> scopeItems,
            List<PromotionAudience> audiences,
            PageQuery redemptionQuery,
            RedemptionState redemptionState,
            String redemptionQ) {
        Instant now = clock.instant();
        List<PromotionScopeItemView> resolvedScopes = resolveScopeItems(promo.getScope(), scopeItems);
        List<PromotionAudienceClassView> resolvedClasses = resolveAudienceClasses(audiences);
        List<PromotionAudienceUserView> resolvedUsers = resolveAudienceUsers(audiences);
        long confirmedCount = redemptionRepository.countByPromotionIdAndState(promo.getId(), RedemptionState.CONFIRMED);
        long reservedCount = redemptionRepository.countByPromotionIdAndState(promo.getId(), RedemptionState.RESERVED);
        long totalDiscountPaid = redemptionRepository.sumConfirmedDiscountByPromotionId(promo.getId());
        List<CapWarning> capWarnings = capWarningService.computeForOne(promo, scopeItems);
        PageResponse<PromotionRedemptionView> redemptions = loadRedemptions(
                promo.getId(), redemptionQuery, redemptionState, redemptionQ);
        List<PromotionChangeLogView> history = changeLogRepository
                .findByPromotionIdOrderByOccurredAtDescIdDesc(promo.getId())
                .stream()
                .map(entry -> new PromotionChangeLogView(
                        entry.getId(),
                        entry.getAction(),
                        entry.getActorId(),
                        entry.getActorUsername(),
                        entry.getOccurredAt(),
                        entry.getChanges()))
                .toList();

        return new PromotionDetail(
                promo.getId(),
                promo.getKind(),
                promo.getName(),
                promo.getDescription(),
                promo.getCode(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getMaxDiscountVnd(),
                promo.getStartsAt(),
                promo.getEndsAt(),
                promo.getTotalLimit(),
                promo.getPerUserLimit(),
                promo.getCombinable(),
                promo.getScope(),
                promo.getAudience(),
                promo.getStatus(),
                PromotionTimeState.at(now, promo.getStartsAt(), promo.getEndsAt()),
                promo.getUsedCount(),
                promo.getOverLimitCount(),
                promo.getRevision(),
                capWarnings,
                promo.getCreatedAt(),
                promo.getUpdatedAt(),
                resolvedScopes,
                resolvedClasses,
                resolvedUsers,
                confirmedCount,
                reservedCount,
                promo.getCreatedBy(),
                promo.getCreatedByUsername(),
                promo.getUpdatedBy(),
                promo.getUpdatedByUsername(),
                totalDiscountPaid,
                redemptions,
                history);
    }

    private List<PromotionScopeItemView> resolveScopeItems(PromotionScope scope, List<PromotionScopeItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (scope == PromotionScope.EXAM_KIND) {
            return items.stream()
                    .map(PromotionScopeItem::getExamItemId)
                    .filter(Objects::nonNull)
                    .map(id -> {
                        CatalogItemView view = catalogLookup.findItem(id).orElse(null);
                        return view != null
                                ? new PromotionScopeItemView("EXAM_KIND", id, view.code(), view.nameVi(), view.nameEn())
                                : new PromotionScopeItemView("EXAM_KIND", id, null, null, null);
                    })
                    .toList();
        }
        if (scope == PromotionScope.EXAM) {
            List<UUID> examIds = items.stream()
                    .map(PromotionScopeItem::getExamId)
                    .filter(Objects::nonNull)
                    .toList();
            Map<UUID, ExamRef> refs = storefrontProductRepository.findExamRefsByExamIds(examIds);
            return examIds.stream().map(examId -> {
                ExamRef ref = refs.get(examId);
                if (ref != null) {
                    return new PromotionScopeItemView("EXAM", examId, ref.code(), ref.name(), null);
                }
                return publishedExamPort.findServing(examId)
                        .map(s -> new PromotionScopeItemView("EXAM", examId, s.version().code(), s.version().name(), null))
                        .orElseGet(() -> new PromotionScopeItemView("EXAM", examId, null, null, null));
            }).toList();
        }
        return items.stream()
                .map(PromotionScopeItem::getBundleId)
                .filter(Objects::nonNull)
                .map(id -> new PromotionScopeItemView("BUNDLE", id, null, null, null))
                .toList();
    }

    private List<PromotionAudienceClassView> resolveAudienceClasses(List<PromotionAudience> audiences) {
        List<UUID> classIds = audiences.stream()
                .map(PromotionAudience::getClassId)
                .filter(Objects::nonNull)
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> sampleUserByClass = new HashMap<>(classIds.size());
        Set<UUID> sampleUsers = new HashSet<>();
        for (UUID classId : classIds) {
            Set<UUID> members = classMembershipPort.findUserIdsInClass(classId);
            if (!members.isEmpty()) {
                UUID sample = members.iterator().next();
                sampleUserByClass.put(classId, sample);
                sampleUsers.add(sample);
            }
        }
        Map<UUID, ClassRef> resolvedRefs = new HashMap<>(classIds.size());
        if (!sampleUsers.isEmpty()) {
            Map<UUID, List<ClassRef>> byUser = classMembershipPort.findCurrentClassesByUserIds(sampleUsers);
            byUser.values().forEach(list -> list.forEach(ref -> resolvedRefs.putIfAbsent(ref.id(), ref)));
        }
        return classIds.stream().map(id -> {
            ClassRef ref = resolvedRefs.get(id);
            return ref != null
                    ? new PromotionAudienceClassView(id, ref.code(), ref.name())
                    : new PromotionAudienceClassView(id, null, null);
        }).toList();
    }

    private List<PromotionAudienceUserView> resolveAudienceUsers(List<PromotionAudience> audiences) {
        List<UUID> userIds = audiences.stream()
                .map(PromotionAudience::getUserId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserSummary> users = userDirectory.findByIds(userIds);
        return userIds.stream().map(id -> {
            UserSummary user = users.get(id);
            return user != null
                    ? new PromotionAudienceUserView(id, user.username(), user.fullName())
                    : new PromotionAudienceUserView(id, null, null);
        }).toList();
    }

    private PageResponse<PromotionRedemptionView> loadRedemptions(
            UUID promotionId, PageQuery pageQuery, RedemptionState state, String q) {
        if (q == null || q.isBlank()) {
            Page<PromotionRedemption> page = state == null
                    ? redemptionRepository.findByPromotionIdOrderByCreatedAtDescIdDesc(
                            promotionId, pageQuery.toPageable())
                    : redemptionRepository.findByPromotionIdAndStateOrderByCreatedAtDescIdDesc(
                            promotionId, state, pageQuery.toPageable());
            Set<UUID> userIds = page.getContent().stream()
                    .map(PromotionRedemption::getUserId)
                    .collect(java.util.stream.Collectors.toSet());
            Map<UUID, UserSummary> users = userIds.isEmpty() ? Map.of() : userDirectory.findByIds(userIds);
            return PageResponse.from(page, r -> toRedemptionView(r, users.get(r.getUserId())));
        }

        List<PromotionRedemption> all = state == null
                ? redemptionRepository.findByPromotionIdOrderByCreatedAtDescIdDesc(promotionId)
                : redemptionRepository.findByPromotionIdAndStateOrderByCreatedAtDescIdDesc(promotionId, state);
        if (all.isEmpty()) {
            return new PageResponse<>(List.of(), pageQuery.page(), pageQuery.size(), 0L, 0);
        }
        Set<UUID> userIds = all.stream()
                .map(PromotionRedemption::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, UserSummary> users = userDirectory.findByIds(userIds);
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<PromotionRedemptionView> matched = all.stream()
                .map(r -> toRedemptionView(r, users.get(r.getUserId())))
                .filter(v -> matchesRedemptionQuery(v, needle))
                .toList();

        long total = matched.size();
        int from = (int) Math.min((long) pageQuery.page() * pageQuery.size(), total);
        int to = (int) Math.min((long) from + pageQuery.size(), total);
        int totalPages = total == 0 ? 0 : (int) ((total + pageQuery.size() - 1) / pageQuery.size());
        return new PageResponse<>(matched.subList(from, to), pageQuery.page(), pageQuery.size(), total, totalPages);
    }

    private static PromotionRedemptionView toRedemptionView(PromotionRedemption r, UserSummary user) {
        return new PromotionRedemptionView(
                r.getId(),
                r.getOrderId(),
                r.getOrderCode(),
                r.getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.fullName(),
                r.getDiscountVnd(),
                r.getState(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private static boolean matchesRedemptionQuery(PromotionRedemptionView view, String needle) {
        return containsIgnoreCase(view.orderCode(), needle)
                || containsIgnoreCase(view.username(), needle)
                || containsIgnoreCase(view.fullName(), needle)
                || view.userId().toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(SUGGESTED_CODE_LENGTH);
        for (int i = 0; i < SUGGESTED_CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private record ValidatedSpec(
            PromotionKind kind,
            String name,
            String description,
            String code,
            DiscountType discountType,
            long discountValue,
            Long maxDiscountVnd,
            Instant startsAt,
            Instant endsAt,
            Integer totalLimit,
            Integer perUserLimit,
            Boolean combinable,
            PromotionScope scope,
            List<UUID> examKindIds,
            List<UUID> examIds,
            PromotionAudienceType audience,
            List<UUID> audienceClassIds,
            List<UUID> audienceUserIds,
            PromotionStatus status
    ) {
    }

    private record TargetIds(
            List<UUID> examKindIds,
            List<UUID> examIds,
            List<UUID> classIds,
            List<UUID> userIds
    ) {
        static TargetIds from(List<PromotionScopeItem> scopeItems, List<PromotionAudience> audiences) {
            List<UUID> kinds = scopeItems.stream()
                    .map(PromotionScopeItem::getExamItemId)
                    .filter(Objects::nonNull)
                    .toList();
            List<UUID> exams = scopeItems.stream()
                    .map(PromotionScopeItem::getExamId)
                    .filter(Objects::nonNull)
                    .toList();
            List<UUID> classes = audiences.stream()
                    .map(PromotionAudience::getClassId)
                    .filter(Objects::nonNull)
                    .toList();
            List<UUID> users = audiences.stream()
                    .map(PromotionAudience::getUserId)
                    .filter(Objects::nonNull)
                    .toList();
            return new TargetIds(kinds, exams, classes, users);
        }
    }
}
