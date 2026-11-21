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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptResultResponse;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.PartResultView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.SubjectResultView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.UnitStatsView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactComponentView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactSubsectionView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewAttachmentView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeBlankView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeKey;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewEssayCriterionScore;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewEssayFeedback;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewExpectedAnswer;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewItemView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingKey;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingSideView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewOptionView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewPartView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewQuestionBody;
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
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.SubjectBreakdown;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.UnitStats;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactComponentBreakdown;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactSubsectionBreakdown;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
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

    private static final Pattern ADMIN_ASSET_PATH =
            Pattern.compile("/api/v1/admin/question-assets/([0-9a-fA-F-]{36})(?:/content)?(?:[/?#].*)?");
    private static final String ADMIN_ASSET_PREFIX = "/api/v1/admin/question-assets/";

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final QuestionSourcePort questionSourcePort;
    private final PublishedExamPort publishedExamPort;
    private final CatalogLookup catalogLookup;
    private final AttemptQuestionSanitizer sanitizer;
    private final MessageSource messageSource;

    public AttemptResultService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            QuestionSourcePort questionSourcePort,
            PublishedExamPort publishedExamPort,
            CatalogLookup catalogLookup,
            AttemptQuestionSanitizer sanitizer,
            MessageSource messageSource) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.catalogLookup = Objects.requireNonNull(catalogLookup);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.messageSource = Objects.requireNonNull(messageSource);
    }

    @Transactional
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

        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> subjectNamesByCode = loadSubjectNames(locale);
        String subjectCode = resolveSubjectCode(attempt.getSubjectItemId(), subjectNamesByCode);

        boolean compensated =
                attemptEventRepository.existsByAttemptIdAndType(attemptId, AttemptEventType.COMPENSATED);
        String faultMessage = resolveFaultMessage(attempt, compensated, locale);
        String effectiveResultView = attempt.getResultView();
        boolean reviewAvailable = !"SCORE".equals(effectiveResultView)
                && (attempt.getState() == AttemptState.SCORED || attempt.getState() == AttemptState.PENDING_REVIEW);

        if (attempt.getState() == AttemptState.GRADING || attempt.getState() == AttemptState.GRADING_ERROR) {
            String statusMessage = attempt.getState() == AttemptState.GRADING_ERROR
                    ? msg("attempt.result.grading_error.status", "Hệ thống đang xử lý kết quả bài làm của bạn")
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
                    attempt.getState() == AttemptState.GRADING_ERROR ? attempt.getGradingError() : null,
                    attempt.getPracticeScoring(),
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

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
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
                ? msg("attempt.result.regrading.label", "Đang chấm lại")
                : null;
        String pendingReviewLabel = (pendingReview && !regrading)
                ? msg("attempt.result.pending_review.label", "Chờ chấm")
                : null;
        String scoreLabel = "VACT".equals(attempt.getExamKindCode())
                ? msg("attempt.result.vact.practice_score.label", "Điểm luyện tập")
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

        List<SubjectResultView> subjectViews = aggregated.subjectBreakdowns().stream()
                .map(s -> new SubjectResultView(
                        s.code(),
                        resolveComponentOrSubjectLabel(s.code(), subjectNamesByCode, locale),
                        s.pendingReview(),
                        s.score(),
                        s.maxScore(),
                        toStatsView(s.stats())))
                .toList();

        List<VactComponentView> vactViews = null;
        if (aggregated.vactComponents() != null) {
            vactViews = aggregated.vactComponents().stream()
                    .map(c -> toVactComponentView(c, subjectNamesByCode, locale))
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
                null,
                aggregated.practiceScoring(),
                rawScore,
                displayScore,
                aggregated.rawMaxScore(),
                aggregated.scaleMax(),
                scoreLabel,
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

    @Transactional
    public AttemptReviewResponse getReview(UUID attemptId, String partFilter) {
        UUID userId = accessGate.requireLearner();
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);

        String effectiveResultView = attempt.getResultView();
        if ("SCORE".equals(effectiveResultView)) {
            throw new ForbiddenException(
                    "REVIEW_NOT_INCLUDED",
                    msg("REVIEW_NOT_INCLUDED", "Gói lượt thi này không bao gồm quyền xem lại bài làm"));
        }
        if (attempt.getState() != AttemptState.SCORED && attempt.getState() != AttemptState.PENDING_REVIEW) {
            throw new ConflictException(
                    "REVIEW_NOT_AVAILABLE",
                    msg("REVIEW_NOT_AVAILABLE", "Chưa thể xem lại bài làm ở trạng thái hiện tại"));
        }

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        AttemptPart selectedPart = resolvePartFilter(parts, partFilter);
        String selectedPartCode = selectedPart != null ? selectedPart.getPartCode() : null;

        List<AttemptItem> allItems = loadItemsInPaperOrder(attemptId, parts);
        Map<UUID, AttemptPart> partById = new HashMap<>(parts.size());
        for (AttemptPart p : parts) {
            partById.put(p.getId(), p);
        }

        List<AttemptItem> inScopeItems = allItems.stream()
                .filter(AttemptItem::isInScope)
                .filter(i -> selectedPart == null || Objects.equals(i.getPartId(), selectedPart.getId()))
                .toList();

        boolean partialPending = attempt.getState() == AttemptState.PENDING_REVIEW;
        // LT §10.2: during PENDING_REVIEW only return auto-graded / non-pending items
        List<AttemptItem> visibleCandidates = partialPending
                ? filterAutoGradedForPendingReview(inScopeItems)
                : inScopeItems;

        Map<VersionKey, JsonNode> snapshots = loadSnapshots(visibleCandidates);
        boolean fullSolution = "FULL_SOLUTION".equals(effectiveResultView);

        List<ReviewItemView> itemViews = new ArrayList<>(visibleCandidates.size());
        for (AttemptItem item : visibleCandidates) {
            JsonNode snapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            if (snapshot == null) {
                continue;
            }
            AttemptPart part = partById.get(item.getPartId());
            itemViews.add(toReviewItemView(attempt, part, item, snapshot, fullSolution));
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

    private List<AttemptItem> filterAutoGradedForPendingReview(List<AttemptItem> items) {
        Set<UUID> nonPendingParents = new HashSet<>();
        for (AttemptItem item : items) {
            if (item.getParentItemId() != null && item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
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
            if (item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
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
            boolean fullSolution) {
        UUID attemptId = attempt.getId();
        String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(snapshot, "content"));
        List<ReviewAttachmentView> attachments = projectAttachments(attemptId, snapshot);
        JsonNode body = snapshot.path("body");
        QuestionType type = item.getQuestionType();

        ReviewQuestionBody reviewBody = projectReviewBody(attemptId, item, body);
        ReviewExpectedAnswer expectedAnswer = projectExpectedAnswer(type, body);

        String solution = null;
        ReviewEssayFeedback essayFeedback = null;
        if (fullSolution && item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
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
                item.getEarnedPoints(),
                item.getOutcome(),
                item.isFlagged(),
                item.getGradingDetail(),
                expectedAnswer,
                solution,
                essayFeedback);
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

    /** Rewrites admin/placeholder image sources inside solution HTML to solution-assets endpoint (BS §15). */
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
        for (Element img : doc.select("img")) {
            String rawSrc = img.attr("src").trim();
            if (!rawSrc.isEmpty()) {
                Matcher matcher = ADMIN_ASSET_PATH.matcher(rawSrc);
                if (matcher.matches()) {
                    img.attr("src", targetPrefix + matcher.group(1));
                }
            } else if (img.hasAttr("data-asset-id")) {
                UUID assetId = parseUuidOrNull(img.attr("data-asset-id").trim());
                if (assetId != null) {
                    img.attr("src", targetPrefix + assetId);
                }
            }
        }
        return doc.body().html();
    }

    private List<ReviewAttachmentView> projectAttachments(UUID attemptId, JsonNode snapshot) {
        JsonNode atts = snapshot.path("attachments");
        if (!atts.isArray() || atts.isEmpty()) {
            return List.of();
        }
        List<ReviewAttachmentView> list = new ArrayList<>(atts.size());
        for (JsonNode node : atts) {
            UUID assetId = parseUuidOrNull(textOrNull(node, "id"));
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

    private Map<VersionKey, JsonNode> loadSnapshots(List<AttemptItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        List<Version> versions = questionSourcePort.loadVersions(List.copyOf(keys));
        Map<VersionKey, JsonNode> map = new HashMap<>(versions.size() * 2);
        for (Version version : versions) {
            if (version == null || version.snapshot() == null) {
                continue;
            }
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    UUID childId = parseUuidOrNull(textOrNull(child, "id"));
                    if (childId != null) {
                        map.putIfAbsent(new VersionKey(childId, version.key().versionNo()), child);
                    }
                }
            }
        }
        return map;
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

    private Map<String, String> loadSubjectNames(Locale locale) {
        Map<String, String> map = new HashMap<>();
        boolean en = "en".equalsIgnoreCase(locale.getLanguage());
        for (CatalogItemView item : catalogLookup.listGroupItems("SUBJECT")) {
            map.put(item.code(), en && item.nameEn() != null ? item.nameEn() : item.nameVi());
        }
        return map;
    }

    private String resolveSubjectCode(UUID subjectItemId, Map<String, String> ignored) {
        if (subjectItemId == null) {
            return null;
        }
        return catalogLookup.findItem(subjectItemId).map(CatalogItemView::code).orElse(null);
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

    private VactComponentView toVactComponentView(
            VactComponentBreakdown component,
            Map<String, String> subjectNames,
            Locale locale) {
        String name = resolveComponentOrSubjectLabel(component.componentCode(), subjectNames, locale);
        List<VactSubsectionView> subViews = null;
        if (component.subsections() != null) {
            subViews = new ArrayList<>(component.subsections().size());
            int index = 0;
            for (VactSubsectionBreakdown sub : component.subsections()) {
                String label;
                if (sub.questionCount() == 12 || (component.subsections().size() == 2 && index == 0)) {
                    label = msg("attempt.result.vact.subsection.logic_data", "Logic và phân tích số liệu");
                } else {
                    label = msg("attempt.result.vact.subsection.scientific_reasoning", "Suy luận khoa học");
                }
                subViews.add(new VactSubsectionView(
                        sub.sectionId(),
                        label,
                        sub.questionCount(),
                        sub.correctCount(),
                        sub.partialCount(),
                        sub.wrongCount(),
                        sub.blankCount(),
                        sub.totalCount()));
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
        return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
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
