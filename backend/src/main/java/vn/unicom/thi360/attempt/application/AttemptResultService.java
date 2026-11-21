package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AttemptChoiceSummary;
import vn.unicom.thi360.attempt.api.AttemptResultResponse;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.PartResultView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.SubjectResultView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.UnitStatsView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactComponentView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactSubsectionView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewAttachmentView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeBlankResult;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeBlankView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeKey;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewEssayCriterionScore;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewEssayFeedback;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewExpectedAnswer;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewGradingDetail;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewItemView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingKey;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingPairResult;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingSideView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewOptionView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewPartView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewQuestionBody;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewStatementResult;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewStatementView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewTrueFalseKey;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.ItemInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartBreakdown;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.SectionInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.UnitStats;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactComponentBreakdown;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactSubsectionBreakdown;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Queries attempt results and review payloads for the attempt owner (LT §10.1, §10.2, BS §3).
 */
@Service
public class AttemptResultService {

    private static final Pattern ADMIN_ASSET_PATH = Pattern.compile(
            "(?:https?://[^/\"'\\s<>]+)?/api/v1/admin/question-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/content)?",
            Pattern.CASE_INSENSITIVE);
    private static final String ADMIN_ASSET_PREFIX = "/api/v1/admin/question-assets/";

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final EntitlementPort entitlementPort;
    private final QuestionSourcePort questionSourcePort;
    private final QuestionAttachmentPort questionAttachmentPort;
    private final PublishedExamPort publishedExamPort;
    private final CatalogLookup catalogLookup;
    private final AttemptQuestionSanitizer sanitizer;
    private final MessageSource messageSource;
    private final TransactionTemplate readOnlyTx;

    private record SubjectCatalog(
            Map<String, String> namesByCode, Map<UUID, CatalogItemView> itemsById) {}

    public AttemptResultService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            EntitlementPort entitlementPort,
            QuestionSourcePort questionSourcePort,
            QuestionAttachmentPort questionAttachmentPort,
            PublishedExamPort publishedExamPort,
            CatalogLookup catalogLookup,
            AttemptQuestionSanitizer sanitizer,
            MessageSource messageSource,
            PlatformTransactionManager transactionManager) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.questionAttachmentPort = Objects.requireNonNull(questionAttachmentPort);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.catalogLookup = Objects.requireNonNull(catalogLookup);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.messageSource = Objects.requireNonNull(messageSource);
        this.readOnlyTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.readOnlyTx.setReadOnly(true);
    }

    public AttemptResultResponse getResult(UUID attemptId) {
        UUID userId = accessGate.requireLearner();
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);

        if (attempt.getState() == AttemptState.STARTING
                || attempt.getState() == AttemptState.IN_PROGRESS
                || attempt.getState() == AttemptState.ABORTED) {
            throw new ConflictException(
                    "ATTEMPT_NOT_FINISHED",
                    msg("ATTEMPT_NOT_FINISHED", "Lượt thi chưa kết thúc nên chưa có kết quả"));
        }

        String effectiveResultView = AttemptReviewSupport.resolveEffectiveResultView(entitlementPort, attempt);
        Locale locale = LocaleContextHolder.getLocale();

        return readOnlyTx.execute(status -> buildResultResponse(attempt, effectiveResultView, locale));
    }

    private AttemptResultResponse buildResultResponse(
            Attempt attempt, String effectiveResultView, Locale locale) {
        UUID attemptId = attempt.getId();
        SubjectCatalog subjects = loadSubjectCatalog(locale);
        String subjectCode = resolveSubjectCode(attempt.getSubjectItemId(), subjects);

        boolean compensated =
                attemptEventRepository.existsByAttemptIdAndType(attemptId, AttemptEventType.COMPENSATED);
        String faultMessage = resolveFaultMessage(attempt, compensated, locale);
        boolean reviewAvailable = allowsReview(effectiveResultView)
                && (attempt.getState() == AttemptState.SCORED || attempt.getState() == AttemptState.PENDING_REVIEW);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        Map<String, AttemptChoiceSummary> choices = AttemptChoiceViews.merge(attempt, parts);

        if (attempt.getState() == AttemptState.GRADING || attempt.getState() == AttemptState.GRADING_ERROR) {
            String statusMessage = attempt.getState() == AttemptState.GRADING_ERROR
                    ? msg(
                            "attempt.result.grading_error.status",
                            "Bài làm đã được ghi nhận, kết quả đang được xử lý",
                            locale)
                    : null;
            return new AttemptResultResponse(
                    attempt.getId(),
                    attempt.getExamId(),
                    attempt.getExamVersionNo(),
                    attempt.getExamName(),
                    attempt.getExamKindCode(),
                    attempt.getScope(),
                    attempt.getPartCode(),
                    subjectCode,
                    choices,
                    attempt.getAttemptNo(),
                    attempt.getState(),
                    attempt.isRegrading(),
                    null,
                    null,
                    statusMessage,
                    attempt.getEndReason(),
                    attempt.getStartedAt(),
                    attempt.getFinishedAt(),
                    null,
                    attempt.getPracticeScoring(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    effectiveResultView,
                    false,
                    attempt.getFaultFlag(),
                    attempt.getFaultReason(),
                    compensated,
                    faultMessage,
                    null,
                    List.of(),
                    List.of(),
                    null);
        }

        List<AttemptItem> items = loadItemsInPaperOrder(attemptId, parts);
        List<SectionInput> sections = loadSectionMetadata(attempt);

        AggregatedScore aggregated = ScoreAggregator.aggregate(toScoreInput(attempt, parts, items, sections));
        boolean pendingReview = attempt.getState() == AttemptState.PENDING_REVIEW;
        boolean regrading = attempt.isRegrading();

        BigDecimal rawScore = pendingReview ? (regrading ? attempt.getRawScore() : null) : attempt.getRawScore();
        BigDecimal displayScore =
                pendingReview ? (regrading ? attempt.getDisplayScore() : null) : attempt.getDisplayScore();
        if (rawScore == null && !pendingReview) {
            rawScore = aggregated.rawScore();
        }
        if (displayScore == null && !pendingReview) {
            displayScore = aggregated.displayScore();
        }

        String regradingLabel = regrading
                ? msg("attempt.result.regrading.label", "Đang chấm lại", locale)
                : null;
        String pendingReviewLabel = (pendingReview && !regrading)
                ? msg("attempt.result.pending_review.label", "Chờ chấm", locale)
                : null;
        boolean isVact = "VACT".equals(attempt.getExamKindCode());
        String scoreLabel = isVact
                ? msg("attempt.result.vact.practice_score.label", "Điểm luyện tập", locale)
                : null;
        String practiceScoreNote = isVact
                ? msg("attempt.result.vact.practice_score.note", "Không phải điểm V-ACT chính thức", locale)
                : null;

        List<PartResultView> partViews = aggregated.partBreakdowns().stream()
                .map(p -> new PartResultView(
                        p.partId(),
                        p.partCode(),
                        p.partName(),
                        p.ord(),
                        p.skipped(),
                        p.pendingReview(),
                        p.score(),
                        p.maxScore(),
                        toStatsView(p.stats())))
                .toList();

        // D1 (BS §3 / LT F15 / AC-16): V-ACT must NEVER expose per-component numeric scores in subjects[]
        List<SubjectResultView> subjectViews = isVact
                ? List.of()
                : aggregated.subjectBreakdowns().stream()
                        .map(s -> toSubjectResultView(s, subjects, locale))
                        .toList();

        List<VactComponentView> vactViews = null;
        if (aggregated.vactComponents() != null) {
            vactViews = aggregated.vactComponents().stream()
                    .map(c -> toVactComponentView(c, subjects.namesByCode(), locale))
                    .toList();
        }

        return new AttemptResultResponse(
                attempt.getId(),
                attempt.getExamId(),
                attempt.getExamVersionNo(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getPartCode(),
                subjectCode,
                choices,
                attempt.getAttemptNo(),
                attempt.getState(),
                regrading,
                regradingLabel,
                pendingReviewLabel,
                null,
                attempt.getEndReason(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getGradedAt(),
                aggregated.practiceScoring(),
                rawScore,
                displayScore,
                aggregated.rawMaxScore(),
                aggregated.scaleMax(),
                scoreLabel,
                practiceScoreNote,
                effectiveResultView,
                reviewAvailable,
                attempt.getFaultFlag(),
                attempt.getFaultReason(),
                compensated,
                faultMessage,
                toStatsView(aggregated.statistics()),
                partViews,
                subjectViews,
                vactViews);
    }

    public AttemptReviewResponse getReview(UUID attemptId, String partFilter) {
        UUID userId = accessGate.requireLearner();
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);

        String effectiveResultView = AttemptReviewSupport.resolveEffectiveResultView(entitlementPort, attempt);
        if (!allowsReview(effectiveResultView)) {
            throw new ForbiddenException(
                    "REVIEW_NOT_INCLUDED",
                    msg("REVIEW_NOT_INCLUDED", "Gói lượt thi này không bao gồm quyền xem lại bài làm"));
        }
        if (attempt.getState() != AttemptState.SCORED && attempt.getState() != AttemptState.PENDING_REVIEW) {
            throw new ConflictException(
                    "REVIEW_NOT_AVAILABLE",
                    msg("REVIEW_NOT_AVAILABLE", "Chưa thể xem lại bài làm ở trạng thái hiện tại"));
        }

        return readOnlyTx.execute(status -> buildReviewResponse(attempt, effectiveResultView, partFilter));
    }

    private AttemptReviewResponse buildReviewResponse(
            Attempt attempt, String effectiveResultView, String partFilter) {
        UUID attemptId = attempt.getId();
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        AttemptPart selectedPart = resolvePartFilter(parts, partFilter);
        String selectedPartCode = selectedPart != null ? selectedPart.getPartCode() : null;

        List<AttemptItem> allItems = loadItemsInPaperOrder(attemptId, parts);
        Map<UUID, AttemptItem> allItemsById = new HashMap<>(allItems.size() * 2);
        for (AttemptItem item : allItems) {
            allItemsById.put(item.getId(), item);
        }
        Map<UUID, AttemptPart> partById = new HashMap<>(parts.size());
        for (AttemptPart p : parts) {
            partById.put(p.getId(), p);
        }

        List<AttemptItem> inScopeItems = AttemptReviewSupport.filterStudentVisibleItems(allItems).stream()
                .filter(i -> selectedPart == null || Objects.equals(i.getPartId(), selectedPart.getId()))
                .toList();

        boolean partialPending = attempt.getState() == AttemptState.PENDING_REVIEW;
        // LT §10.2: during PENDING_REVIEW only return auto-graded / non-pending items
        List<AttemptItem> visibleCandidates = partialPending
                ? filterAutoGradedForPendingReview(inScopeItems)
                : inScopeItems;

        Map<VersionKey, JsonNode> snapshots = AttemptReviewSupport.loadSnapshots(
                visibleCandidates, allItemsById, attemptItemRepository, questionSourcePort);

        Set<VersionKey> attachmentKeys = new LinkedHashSet<>();
        for (AttemptItem item : visibleCandidates) {
            attachmentKeys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        Map<VersionKey, List<Attachment>> attachmentsByVersion = attachmentKeys.isEmpty()
                ? Map.of()
                : questionAttachmentPort.attachments(attachmentKeys, false);

        boolean fullSolution = "FULL_SOLUTION".equals(effectiveResultView);

        List<ReviewItemView> itemViews = new ArrayList<>(visibleCandidates.size());
        for (AttemptItem item : visibleCandidates) {
            VersionKey key = new VersionKey(item.getQuestionId(), item.getQuestionVersionNo());
            JsonNode snapshot = snapshots.get(key);
            if (snapshot == null) {
                throw new IllegalStateException(
                        "Missing issued question snapshot for attempt item "
                                + item.getId()
                                + " question="
                                + item.getQuestionId()
                                + " v"
                                + item.getQuestionVersionNo());
            }
            AttemptPart part = partById.get(item.getPartId());
            itemViews.add(toReviewItemView(
                    attempt, part, item, snapshot, attachmentsByVersion.get(key), fullSolution, partialPending));
        }

        AggregatedScore agg = ScoreAggregator.aggregate(
                toScoreInput(attempt, parts, allItems, List.of()));
        Map<UUID, PartBreakdown> breakdownByPartId = new HashMap<>();
        for (PartBreakdown pb : agg.partBreakdowns()) {
            breakdownByPartId.put(pb.partId(), pb);
        }
        List<ReviewPartView> partViews = parts.stream()
                .filter(p -> selectedPart == null || Objects.equals(p.getId(), selectedPart.getId()))
                .map(p -> {
                    PartBreakdown pb = breakdownByPartId.get(p.getId());
                    return new ReviewPartView(
                            p.getId(),
                            p.getPartCode(),
                            p.getPartName(),
                            p.getOrd(),
                            p.getState().name(),
                            pb != null && pb.pendingReview(),
                            pb != null ? pb.score() : null,
                            pb != null ? pb.maxScore() : p.getMaxPoints());
                })
                .toList();

        return new AttemptReviewResponse(
                attempt.getId(),
                attempt.getState(),
                effectiveResultView,
                selectedPartCode,
                partViews,
                itemViews);
    }

    static boolean allowsReview(String resultView) {
        return "SCORE_AND_ANSWERS".equals(resultView) || "FULL_SOLUTION".equals(resultView);
    }

    private List<AttemptItem> filterAutoGradedForPendingReview(List<AttemptItem> items) {
        Set<UUID> nonPendingParents = new HashSet<>();
        for (AttemptItem item : items) {
            if (item.getParentItemId() != null
                    && item.getOutcome() != null
                    && item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
                nonPendingParents.add(item.getParentItemId());
            }
        }
        List<AttemptItem> filtered = new ArrayList<>(items.size());
        for (AttemptItem item : items) {
            if (item.getQuestionType() == QuestionType.QUESTION_SET) {
                if (nonPendingParents.contains(item.getId())) {
                    filtered.add(item);
                }
                continue;
            }
            if (item.getOutcome() != null && item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private AttemptPart resolvePartFilter(List<AttemptPart> parts, String partFilter) {
        if (partFilter == null || partFilter.isBlank()) {
            return null;
        }
        String trimmed = partFilter.trim();
        for (AttemptPart part : parts) {
            if (trimmed.equals(part.getPartCode()) || trimmed.equals(part.getId().toString())) {
                return part;
            }
        }
        throw new NotFoundException("NOT_FOUND", null);
    }

    private ReviewItemView toReviewItemView(
            Attempt attempt,
            AttemptPart part,
            AttemptItem item,
            JsonNode snapshot,
            List<Attachment> resolvedAttachments,
            boolean fullSolution,
            boolean partialPending) {
        UUID attemptId = attempt.getId();
        String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(snapshot, "content"));
        List<ReviewAttachmentView> attachments = projectAttachments(attemptId, snapshot, resolvedAttachments);
        JsonNode body = snapshot.path("body");
        QuestionType type = item.getQuestionType();

        ReviewQuestionBody reviewBody = projectReviewBody(attemptId, item, body);
        ReviewExpectedAnswer expectedAnswer = projectExpectedAnswer(type, body);

        boolean partialSetContainer = partialPending
                && type == QuestionType.QUESTION_SET
                && (item.getOutcome() == null || item.getOutcome() == ItemOutcome.PENDING_REVIEW);

        BigDecimal earnedPoints = partialSetContainer ? null : item.getEarnedPoints();
        ItemOutcome outcome = partialSetContainer ? null : item.getOutcome();
        ReviewGradingDetail gradingDetail = partialSetContainer ? null : projectGradingDetail(item);

        String solution = null;
        ReviewEssayFeedback essayFeedback = null;
        if (fullSolution && !partialSetContainer && item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
            String rawSolution = textOrNull(snapshot, "solution");
            if (rawSolution != null && !rawSolution.isBlank()) {
                solution = rewriteSolutionAssetUrls(attemptId, rawSolution);
            }
            if (type == QuestionType.ESSAY && item.getOutcome() != ItemOutcome.BLANK) {
                essayFeedback = projectEssayFeedback(item);
            }
        }

        return new ReviewItemView(
                item.getId(),
                item.getPartId(),
                part != null ? part.getPartCode() : null,
                item.getParentItemId(),
                item.getDisplayNo(),
                item.getOrd(),
                item.getUnitCount(),
                item.getSectionId(),
                item.getComponentCode(),
                type,
                content,
                attachments,
                reviewBody,
                item.getAnswer(),
                item.getOptionOrder(),
                item.getPointsMax(),
                earnedPoints,
                outcome,
                item.isFlagged(),
                gradingDetail,
                expectedAnswer,
                solution,
                essayFeedback);
    }

    private static ReviewGradingDetail projectGradingDetail(AttemptItem item) {
        Map<String, Object> raw = item.getGradingDetail();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        QuestionType type = item.getQuestionType();
        if (type == QuestionType.ESSAY) {
            Boolean blankEssay = boolVal(raw.get("blankEssay"));
            Integer wordCount = intVal(raw.get("wordCount"));
            if (blankEssay == null && wordCount == null) {
                return null;
            }
            return new ReviewGradingDetail(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, blankEssay, wordCount);
        }

        List<ReviewClozeBlankResult> blankResults = null;
        if (raw.get("blankResults") instanceof List<?> rawBlanks) {
            List<ReviewClozeBlankResult> parsed = new ArrayList<>(rawBlanks.size());
            for (Object entry : rawBlanks) {
                if (entry instanceof Map<?, ?> m) {
                    ItemOutcome blankOutcome = parseOutcome(m.get("outcome"));
                    parsed.add(new ReviewClozeBlankResult(
                            strVal(m.get("id")),
                            blankOutcome,
                            toBigDecimalOrNull(m.get("pointsEarned")),
                            toBigDecimalOrNull(m.get("pointsMax"))));
                }
            }
            blankResults = parsed;
        }

        List<ReviewStatementResult> statementResults = null;
        if (raw.get("statementResults") instanceof List<?> rawStmts) {
            List<ReviewStatementResult> parsed = new ArrayList<>(rawStmts.size());
            for (Object entry : rawStmts) {
                if (entry instanceof Map<?, ?> m) {
                    parsed.add(new ReviewStatementResult(
                            strVal(m.get("id")),
                            Boolean.TRUE.equals(boolVal(m.get("answered"))),
                            Boolean.TRUE.equals(boolVal(m.get("matched")))));
                }
            }
            statementResults = parsed;
        }

        List<ReviewMatchingPairResult> pairResults = null;
        if (raw.get("pairResults") instanceof List<?> rawPairs) {
            List<ReviewMatchingPairResult> parsed = new ArrayList<>(rawPairs.size());
            for (Object entry : rawPairs) {
                if (entry instanceof Map<?, ?> m) {
                    parsed.add(new ReviewMatchingPairResult(
                            strVal(m.get("leftId")),
                            Boolean.TRUE.equals(boolVal(m.get("matched"))),
                            toBigDecimalOrNull(m.get("pointsEarned"))));
                }
            }
            pairResults = parsed;
        }

        return new ReviewGradingDetail(
                strVal(raw.get("itemType")),
                intVal(raw.get("childCount")),
                strVal(raw.get("selectedOptionId")),
                strVal(raw.get("scoringRule")),
                intVal(raw.get("selectedCount")),
                intVal(raw.get("correctSelectedCount")),
                intVal(raw.get("wrongSelectedCount")),
                intVal(raw.get("totalExpectedCount")),
                boolVal(raw.get("normalizedMatch")),
                intVal(raw.get("correctUnitCount")),
                intVal(raw.get("wrongUnitCount")),
                intVal(raw.get("blankUnitCount")),
                blankResults,
                strVal(raw.get("scoringMethod")),
                intVal(raw.get("matchedCount")),
                intVal(raw.get("statementCount")),
                statementResults,
                boolVal(raw.get("reuseRight")),
                intVal(raw.get("pairCount")),
                pairResults,
                null,
                null);
    }

    private ReviewQuestionBody projectReviewBody(UUID attemptId, AttemptItem item, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        return switch (item.getQuestionType()) {
            case SINGLE_CHOICE -> new ReviewQuestionBody(
                    null, null,
                    projectOrderedOptions(attemptId, body.path("options"), item.getOptionOrder()),
                    null, null, null, null, null, null, null, null, null);
            case MULTIPLE_CHOICE -> new ReviewQuestionBody(
                    null, null,
                    projectOrderedOptions(attemptId, body.path("options"), item.getOptionOrder()),
                    textOrNull(body, "scoringRule"), null, null, null, null, null, null, null, null);
            case SHORT_ANSWER -> new ReviewQuestionBody(
                    null, null, null, null,
                    textOrNull(body, "inputHint"),
                    textOrNull(body, "answerFormat"),
                    null, null, null, null, null, null);
            case CLOZE -> new ReviewQuestionBody(
                    sanitizer.rewriteAssetUrls(attemptId, textOrNull(body, "passage")),
                    null, null, null, null, null,
                    projectBlanks(body.path("blanks")),
                    null, null, null, null, null);
            case QUESTION_SET -> new ReviewQuestionBody(
                    sanitizer.rewriteAssetUrls(attemptId, textOrNull(body, "passage")),
                    intOrNull(body, "blankNumberStart"),
                    null, null, null, null, null, null, null, null, null, null);
            case TRUE_FALSE -> new ReviewQuestionBody(
                    null, null, null, null, null, null, null,
                    projectStatements(attemptId, body.path("statements")),
                    null, null, null, null);
            case MATCHING -> new ReviewQuestionBody(
                    null, null, null, null, null, null, null, null,
                    projectSides(attemptId, body.path("left")),
                    projectOrderedSides(attemptId, body.path("right"), item.getOptionOrder()),
                    boolOrNull(body, "reuseRight"),
                    null);
            case ESSAY -> new ReviewQuestionBody(
                    null, null, null, null, null, null, null, null, null, null, null,
                    intOrNull(body, "wordLimit"));
        };
    }

    private ReviewExpectedAnswer projectExpectedAnswer(QuestionType type, JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                List<String> expectedIds = new ArrayList<>();
                JsonNode options = body.path("options");
                if (options.isArray()) {
                    for (JsonNode opt : options) {
                        if (opt.path("correct").asBoolean(false)) {
                            String id = textOrNull(opt, "id");
                            if (id != null) {
                                expectedIds.add(id);
                            }
                        }
                    }
                }
                yield new ReviewExpectedAnswer(expectedIds, null, null, null, null);
            }
            case SHORT_ANSWER -> new ReviewExpectedAnswer(null, readStringList(body.path("answers")), null, null, null);
            case CLOZE -> {
                List<ReviewClozeKey> blanks = new ArrayList<>();
                JsonNode blanksNode = body.path("blanks");
                if (blanksNode.isArray()) {
                    for (JsonNode b : blanksNode) {
                        blanks.add(new ReviewClozeKey(textOrNull(b, "id"), readStringList(b.path("answers"))));
                    }
                }
                yield new ReviewExpectedAnswer(null, null, blanks, null, null);
            }
            case TRUE_FALSE -> {
                List<ReviewTrueFalseKey> stmts = new ArrayList<>();
                JsonNode stmtsNode = body.path("statements");
                if (stmtsNode.isArray()) {
                    for (JsonNode s : stmtsNode) {
                        stmts.add(new ReviewTrueFalseKey(textOrNull(s, "id"), boolOrNull(s, "answer")));
                    }
                }
                yield new ReviewExpectedAnswer(null, null, null, stmts, null);
            }
            case MATCHING -> {
                List<ReviewMatchingKey> pairs = new ArrayList<>();
                JsonNode pairsNode = body.path("pairs");
                if (pairsNode.isArray()) {
                    for (JsonNode p : pairsNode) {
                        pairs.add(new ReviewMatchingKey(textOrNull(p, "leftId"), textOrNull(p, "rightId")));
                    }
                }
                yield new ReviewExpectedAnswer(null, null, null, null, pairs);
            }
            case ESSAY, QUESTION_SET -> null;
        };
    }

    private ReviewEssayFeedback projectEssayFeedback(AttemptItem item) {
        if (item.getEarnedPoints() == null) {
            return null;
        }
        Map<String, Object> detail = item.getGradingDetail();
        String comment = null;
        List<ReviewEssayCriterionScore> criteria = List.of();
        if (detail != null) {
            if (detail.get("comment") instanceof String c) {
                comment = c;
            }
            if (detail.get("criteria") instanceof List<?> rawList) {
                List<ReviewEssayCriterionScore> parsed = new ArrayList<>(rawList.size());
                for (Object o : rawList) {
                    if (o instanceof Map<?, ?> m) {
                        String id = m.get("id") != null ? m.get("id").toString() : null;
                        BigDecimal pts = toBigDecimalOrNull(m.get("points"));
                        String critComment = m.get("comment") != null ? m.get("comment").toString() : null;
                        parsed.add(new ReviewEssayCriterionScore(id, pts, critComment));
                    }
                }
                criteria = parsed;
            }
        }
        return new ReviewEssayFeedback(item.getEarnedPoints(), comment, criteria);
    }

    /** Rewrites admin/placeholder asset URLs inside solution HTML to solution-assets endpoint (BS §15). */
    static String rewriteSolutionAssetUrls(UUID attemptId, String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("<img") && !lower.contains(ADMIN_ASSET_PREFIX)) {
            return html;
        }
        String targetPrefix = "/api/v1/me/attempts/" + attemptId + "/solution-assets/";
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        for (Element img : doc.select("img[data-asset-id]")) {
            UUID assetId = parseUuidOrNull(img.attr("data-asset-id").trim());
            if (assetId == null) {
                continue;
            }
            String rawSrc = img.attr("src").trim();
            if (rawSrc.isEmpty() || !rawSrc.startsWith(targetPrefix)) {
                if (!rawSrc.isEmpty() && rawSrc.toLowerCase(Locale.ROOT).contains(ADMIN_ASSET_PREFIX)) {
                    String rewritten = ADMIN_ASSET_PATH.matcher(rawSrc)
                            .replaceAll(mr -> Matcher.quoteReplacement(targetPrefix + mr.group(1)));
                    img.attr("src", rewritten);
                } else {
                    img.attr("src", targetPrefix + assetId);
                }
            }
        }

        for (Element element : doc.body().getAllElements()) {
            for (Attribute attr : element.attributes()) {
                String trimmed = attr.getValue().trim();
                if (trimmed.toLowerCase(Locale.ROOT).contains(ADMIN_ASSET_PREFIX)) {
                    String rewritten = ADMIN_ASSET_PATH.matcher(trimmed)
                            .replaceAll(mr -> Matcher.quoteReplacement(targetPrefix + mr.group(1)));
                    if (!rewritten.equals(trimmed)) {
                        element.attr(attr.getKey(), rewritten);
                    }
                }
            }
        }
        return doc.body().html();
    }

    private List<ReviewAttachmentView> projectAttachments(
            UUID attemptId, JsonNode snapshot, List<Attachment> resolvedAttachments) {
        if (resolvedAttachments != null && !resolvedAttachments.isEmpty()) {
            List<ReviewAttachmentView> list = new ArrayList<>(resolvedAttachments.size());
            for (Attachment att : resolvedAttachments) {
                if (att == null || att.id() == null) {
                    continue;
                }
                list.add(new ReviewAttachmentView(
                        att.id(),
                        att.name(),
                        att.mime(),
                        "/api/v1/me/attempts/" + attemptId + "/assets/" + att.id()));
            }
            if (!list.isEmpty()) {
                return list;
            }
        }

        JsonNode attachmentIds = snapshot.path("attachmentIds");
        if (!attachmentIds.isArray() || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<ReviewAttachmentView> list = new ArrayList<>(attachmentIds.size());
        for (JsonNode node : attachmentIds) {
            UUID assetId = parseUuidOrNull(node.isTextual() ? node.asText() : textOrNull(node, "id"));
            if (assetId == null) {
                continue;
            }
            list.add(new ReviewAttachmentView(
                    assetId,
                    textOrNull(node, "name"),
                    textOrNull(node, "mime"),
                    "/api/v1/me/attempts/" + attemptId + "/assets/" + assetId));
        }
        return list;
    }

    private List<ReviewOptionView> projectOrderedOptions(
            UUID attemptId, JsonNode arrayNode, List<String> order) {
        Map<String, ReviewOptionView> byId = new LinkedHashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                if (id != null) {
                    byId.put(
                            id,
                            new ReviewOptionView(
                                    id, sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"))));
                }
            }
        }
        return reorderById(byId, order);
    }

    private List<ReviewMatchingSideView> projectSides(UUID attemptId, JsonNode arrayNode) {
        List<ReviewMatchingSideView> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                if (id != null) {
                    list.add(new ReviewMatchingSideView(
                            id, sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"))));
                }
            }
        }
        return list;
    }

    private List<ReviewMatchingSideView> projectOrderedSides(
            UUID attemptId, JsonNode arrayNode, List<String> order) {
        Map<String, ReviewMatchingSideView> byId = new LinkedHashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                if (id != null) {
                    byId.put(
                            id,
                            new ReviewMatchingSideView(
                                    id, sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"))));
                }
            }
        }
        return reorderById(byId, order);
    }

    private List<ReviewStatementView> projectStatements(UUID attemptId, JsonNode arrayNode) {
        List<ReviewStatementView> statements = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                statements.add(new ReviewStatementView(
                        textOrNull(node, "id"),
                        sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"))));
            }
        }
        return statements;
    }

    private static List<ReviewClozeBlankView> projectBlanks(JsonNode arrayNode) {
        List<ReviewClozeBlankView> blanks = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                blanks.add(new ReviewClozeBlankView(textOrNull(node, "id")));
            }
        }
        return blanks;
    }

    private static <T> List<T> reorderById(Map<String, T> byId, List<String> order) {
        if (byId.isEmpty()) {
            return List.of();
        }
        if (order == null || order.isEmpty()) {
            return List.copyOf(byId.values());
        }
        List<T> ordered = new ArrayList<>(byId.size());
        Set<String> seen = new HashSet<>(byId.size());
        for (String id : order) {
            T val = byId.get(id);
            if (val != null && seen.add(id)) {
                ordered.add(val);
            }
        }
        for (Map.Entry<String, T> entry : byId.entrySet()) {
            if (seen.add(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    private List<AttemptItem> loadItemsInPaperOrder(UUID attemptId, List<AttemptPart> parts) {
        List<AttemptItem> items = new ArrayList<>(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId));
        Map<UUID, Integer> partOrdById = new HashMap<>(parts.size());
        for (AttemptPart p : parts) {
            partOrdById.put(p.getId(), p.getOrd());
        }
        items.sort(Comparator
                .comparingInt((AttemptItem i) -> partOrdById.getOrDefault(i.getPartId(), Integer.MAX_VALUE))
                .thenComparingInt(AttemptItem::getOrd));
        return items;
    }

    private List<SectionInput> loadSectionMetadata(Attempt attempt) {
        if (!"VACT".equals(attempt.getExamKindCode())) {
            return List.of();
        }
        Optional<PublishedVersion> published =
                publishedExamPort.loadVersion(attempt.getExamId(), attempt.getExamVersionNo());
        if (published.isEmpty() || published.get().parts() == null) {
            return List.of();
        }
        List<SectionInput> sections = new ArrayList<>();
        for (PublishedExamPort.Part part : published.get().parts()) {
            if (part.sections() == null) {
                continue;
            }
            for (PublishedExamPort.Section sec : part.sections()) {
                sections.add(new SectionInput(sec.id(), sec.subjectCode(), sec.questionCount()));
            }
        }
        return sections;
    }

    private SubjectCatalog loadSubjectCatalog(Locale locale) {
        Map<String, String> namesByCode = new HashMap<>();
        Map<UUID, CatalogItemView> itemsById = new HashMap<>();
        boolean en = "en".equalsIgnoreCase(locale.getLanguage());
        for (CatalogItemView item : catalogLookup.listGroupItems("SUBJECT")) {
            itemsById.put(item.id(), item);
            if (item.code() != null) {
                namesByCode.put(item.code(), en && item.nameEn() != null ? item.nameEn() : item.nameVi());
            }
        }
        return new SubjectCatalog(namesByCode, itemsById);
    }

    private String resolveSubjectCode(UUID subjectItemId, SubjectCatalog subjects) {
        if (subjectItemId == null) {
            return null;
        }
        CatalogItemView cached = subjects.itemsById().get(subjectItemId);
        if (cached != null) {
            return cached.code();
        }
        return catalogLookup.findItem(subjectItemId).map(CatalogItemView::code).orElse(null);
    }

    private SubjectResultView toSubjectResultView(
            ScoreAggregator.SubjectBreakdown breakdown, SubjectCatalog subjects, Locale locale) {
        CatalogItemView byId = breakdown.subjectItemId() != null
                ? subjects.itemsById().get(breakdown.subjectItemId())
                : null;
        String resolvedCode = (byId != null && byId.code() != null) ? byId.code() : breakdown.code();
        String label = resolveComponentOrSubjectLabel(resolvedCode, subjects.namesByCode(), locale);
        if ((label == null || label.equals(resolvedCode)) && byId != null) {
            boolean en = "en".equalsIgnoreCase(locale.getLanguage());
            label = en && byId.nameEn() != null ? byId.nameEn() : byId.nameVi();
        }
        return new SubjectResultView(
                resolvedCode,
                label,
                breakdown.pendingReview(),
                breakdown.score(),
                breakdown.maxScore(),
                toStatsView(breakdown.stats()));
    }

    private String resolveComponentOrSubjectLabel(
            String code, Map<String, String> subjectNames, Locale locale) {
        if (code == null) {
            return null;
        }
        String bundleKey = "attempt.result.vact.component." + code;
        String fromBundle = messageSource.getMessage(bundleKey, null, null, locale);
        if (fromBundle != null) {
            return fromBundle;
        }
        return subjectNames.getOrDefault(code, code);
    }

    VactComponentView toVactComponentView(
            VactComponentBreakdown component,
            Map<String, String> subjectNames,
            Locale locale) {
        String name = resolveComponentOrSubjectLabel(component.componentCode(), subjectNames, locale);
        List<VactSubsectionView> subViews = null;
        if (component.subsections() != null) {
            subViews = new ArrayList<>(component.subsections().size());
            int index = 0;
            for (VactSubsectionBreakdown sub : component.subsections()) {
                boolean isLogicData = sub.questionCount() == 12
                        || (sub.questionCount() != 18 && component.subsections().size() == 2 && index == 0);
                String label = isLogicData
                        ? msg("attempt.result.vact.subsection.logic_data", "Logic và phân tích số liệu", locale)
                        : msg("attempt.result.vact.subsection.scientific_reasoning", "Suy luận khoa học", locale);
                subViews.add(new VactSubsectionView(
                        sub.sectionId(),
                        label,
                        sub.questionCount(),
                        sub.correctCount(),
                        sub.partialCount(),
                        sub.wrongCount(),
                        sub.blankCount(),
                        sub.totalCount(),
                        sub.correctRate()));
                index++;
            }
        }
        return new VactComponentView(
                component.componentCode(),
                name,
                component.correctCount(),
                component.partialCount(),
                component.wrongCount(),
                component.blankCount(),
                component.totalCount(),
                component.correctRate(),
                subViews);
    }

    private String resolveFaultMessage(Attempt attempt, boolean compensated, Locale locale) {
        if (compensated) {
            return messageSource.getMessage(
                    "attempt.result.fault.compensated",
                    null,
                    "Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt",
                    locale);
        }
        if ("SYSTEM_FAULT".equals(attempt.getFaultFlag())
                || attempt.getEndReason() == EndReason.FAULT_CLOSED) {
            return messageSource.getMessage(
                    "attempt.result.fault.notice",
                    null,
                    "Lượt kết thúc do lỗi hệ thống",
                    locale);
        }
        return null;
    }

    private String msg(String code, String defaultMessage) {
        return msg(code, defaultMessage, LocaleContextHolder.getLocale());
    }

    private String msg(String code, String defaultMessage, Locale locale) {
        return messageSource.getMessage(code, null, defaultMessage, locale);
    }

    private static UnitStatsView toStatsView(UnitStats stats) {
        if (stats == null) {
            return null;
        }
        return new UnitStatsView(
                stats.correctCount(),
                stats.partialCount(),
                stats.wrongCount(),
                stats.blankCount(),
                stats.pendingReviewCount(),
                stats.totalCount());
    }

    private static ScoreAggregator.Input toScoreInput(
            Attempt attempt,
            List<AttemptPart> parts,
            List<AttemptItem> items,
            List<SectionInput> sections) {
        List<PartInput> partInputs = parts.stream()
                .map(p -> new PartInput(
                        p.getId(),
                        p.getPartCode(),
                        p.getPartName(),
                        p.getOrd(),
                        p.getState(),
                        p.getMaxPoints(),
                        p.getQuestionCountRequired()))
                .toList();
        List<ItemInput> itemInputs = items.stream()
                .map(i -> new ItemInput(
                        i.getId(),
                        i.getPartId(),
                        i.getParentItemId(),
                        i.getSubjectItemId(),
                        i.getSectionId(),
                        i.getComponentCode(),
                        i.getQuestionType(),
                        i.getUnitCount(),
                        i.isInScope(),
                        i.getPointsMax(),
                        i.getEarnedPoints(),
                        i.getOutcome(),
                        i.getGradingDetail()))
                .toList();
        return new ScoreAggregator.Input(
                attempt.getExamKindCode(),
                attempt.getPracticeScoring(),
                attempt.getReferenceMax(),
                partInputs,
                itemInputs,
                sections);
    }

    private static List<String> readStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(arrayNode.size());
        for (JsonNode n : arrayNode) {
            if (n.isTextual()) {
                list.add(n.asText());
            }
        }
        return list;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).isNumber() ? node.get(field).asInt() : null;
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asBoolean();
    }

    private static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String strVal(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private static Integer intVal(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        if (obj != null) {
            try {
                return Integer.parseInt(obj.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean boolVal(Object obj) {
        if (obj instanceof Boolean b) {
            return b;
        }
        if (obj != null) {
            return Boolean.parseBoolean(obj.toString());
        }
        return null;
    }

    private static ItemOutcome parseOutcome(Object obj) {
        if (obj instanceof ItemOutcome io) {
            return io;
        }
        if (obj instanceof String s) {
            try {
                return ItemOutcome.valueOf(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimalOrNull(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        if (obj instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
