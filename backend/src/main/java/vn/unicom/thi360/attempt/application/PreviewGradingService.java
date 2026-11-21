package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.ClozeBlankView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.OptionView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.SideView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.StatementView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.UnitStatsView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewGradingDetail;
import vn.unicom.thi360.attempt.api.PreviewGradeRequest;
import vn.unicom.thi360.attempt.api.PreviewGradeResponse;
import vn.unicom.thi360.attempt.api.PreviewGradeResponse.PreviewGradedItemView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperAttachmentView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperBlockView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperItemView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperPartView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperQuestionSetView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperQuestionView;
import vn.unicom.thi360.attempt.api.PreviewTrialPaperResponse.PreviewPaperSegmentView;
import vn.unicom.thi360.attempt.api.PreviewTrialResponse;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicies;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.attempt.domain.grading.ItemGradeResult;
import vn.unicom.thi360.attempt.domain.grading.ItemGrader;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.ItemInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartInput;
import vn.unicom.thi360.exam.spi.PreviewPaperPort;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Attachment;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Block;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Item;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Part;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewPaper;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Question;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.QuestionSet;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Segment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/**
 * Nghiệp vụ làm thử đề xem trước cho admin: gia hạn TTL xem trước, phát đề đã lọc sạch đáp án theo đúng
 * thứ tự và đính kèm, và chấm bài tạm trong bộ nhớ bằng {@link ItemGrader} &amp; {@link ScoreAggregator} mà
 * không ghi bất kỳ bản ghi nào vào {@code attempt.*} hay {@code entitlement} (AC-17..AC-21, AC-23..AC-24).
 */
@Service
public class PreviewGradingService {

    private final PreviewPaperPort previewPaperPort;
    private final QuestionSourcePort questionSourcePort;
    private final AnswerValueValidator answerValueValidator;

    public PreviewGradingService(
            PreviewPaperPort previewPaperPort,
            QuestionSourcePort questionSourcePort,
            AnswerValueValidator answerValueValidator) {
        this.previewPaperPort = Objects.requireNonNull(previewPaperPort);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.answerValueValidator = Objects.requireNonNull(answerValueValidator);
    }

    /** Gia hạn TTL phiên xem trước để admin làm thử đề như học viên (BS §9, AC-23). */
    public PreviewTrialResponse extendForTrial(UUID examId, UUID previewId) {
        Instant expiresAt = previewPaperPort.extendForTrial(examId, previewId);
        return new PreviewTrialResponse(expiresAt);
    }

    /**
     * Đọc đề xem trước từ Redis qua {@link PreviewPaperPort#load} và dựng bản phát hành dành cho làm thử
     * đã lọc bỏ hoàn toàn đáp án, hướng dẫn chấm và metadata ngân hàng câu hỏi (AC-18, AC-24).
     */
    @Transactional(readOnly = true)
    public PreviewTrialPaperResponse getPaper(UUID examId, UUID previewId) {
        PreviewPaper paper = previewPaperPort.load(examId, previewId);
        Map<VersionKey, Version> versionMap = loadAllVersions(paper, true);

        List<PreviewPaperPartView> parts = new ArrayList<>();
        if (paper.parts() != null) {
            for (Part part : paper.parts()) {
                List<PreviewPaperSegmentView> segments = mapSegments(part.displaySegments());
                List<PreviewPaperBlockView> blocks = new ArrayList<>();
                if (part.blocks() != null) {
                    for (Block block : part.blocks()) {
                        blocks.add(toBlockView(block, versionMap));
                    }
                }
                parts.add(new PreviewPaperPartView(
                        part.partId(),
                        part.name(),
                        part.order(),
                        part.durationMinutes(),
                        part.firstNumber(),
                        part.lastNumber(),
                        part.points(),
                        segments,
                        blocks));
            }
        }

        return new PreviewTrialPaperResponse(
                paper.examId(),
                previewId,
                paper.versionNo(),
                paper.kindCode(),
                paper.timing(),
                paper.durationMinutes(),
                parts);
    }

    /**
     * Chấm bài làm tạm trên đề xem trước: validate từng câu trả lời theo snapshot đã phát hành và dùng
     * {@link ItemGrader} + {@link GradingPolicies#forKind(String)} + {@link ScoreAggregator} không lưu DB
     * (AC-17..AC-20).
     */
    @Transactional(readOnly = true)
    public PreviewGradeResponse grade(UUID examId, UUID previewId, PreviewGradeRequest request) {
        PreviewPaper paper = previewPaperPort.load(examId, previewId);
        Map<UUID, AnswerValue> submittedAnswers =
                request != null && request.answers() != null ? request.answers() : Map.of();

        List<PartInput> partInputs = new ArrayList<>();
        List<LeafQuestionRef> leafQuestions = new ArrayList<>();
        Map<UUID, LeafQuestionRef> leafById = new LinkedHashMap<>();

        if (paper.parts() != null) {
            int ord = 1;
            for (Part part : paper.parts()) {
                UUID syntheticPartId = syntheticPartUuid(part.partId());
                int partOrd = part.order() != null ? part.order() : ord;
                partInputs.add(new PartInput(
                        syntheticPartId, part.partId(), part.name(), partOrd, PartState.CLOSED, part.points(), null));
                ord++;
                if (part.blocks() == null) {
                    continue;
                }
                for (Block block : part.blocks()) {
                    if (block.items() == null) {
                        continue;
                    }
                    for (Item item : block.items()) {
                        if (item.kind() == Item.Kind.QUESTION && item.question() != null) {
                            LeafQuestionRef ref = new LeafQuestionRef(syntheticPartId, item.question());
                            leafQuestions.add(ref);
                            leafById.put(item.question().questionId(), ref);
                        } else if (item.kind() == Item.Kind.SET
                                && item.set() != null
                                && item.set().children() != null) {
                            for (Question child : item.set().children()) {
                                LeafQuestionRef ref = new LeafQuestionRef(syntheticPartId, child);
                                leafQuestions.add(ref);
                                leafById.put(child.questionId(), ref);
                            }
                        }
                    }
                }
            }
        }

        for (UUID questionId : submittedAnswers.keySet()) {
            if (!leafById.containsKey(questionId)) {
                throw new BusinessRuleException("PREVIEW_UNKNOWN_QUESTION", null);
            }
        }

        Map<VersionKey, Version> versionMap = loadAllVersions(paper, false);
        GradingPolicy policy = GradingPolicies.forKind(paper.kindCode());

        List<ItemInput> itemInputs = new ArrayList<>(leafQuestions.size());
        List<PreviewGradedItemView> gradedItems = new ArrayList<>(leafQuestions.size());

        for (LeafQuestionRef ref : leafQuestions) {
            Question q = ref.question();
            VersionKey key = new VersionKey(q.questionId(), q.versionNo());
            Version version = requireVersion(versionMap, key);
            JsonNode snapshot = version.snapshot();
            QuestionType type = QuestionType.valueOf(q.type());

            AnswerValue submitted = submittedAnswers.get(q.questionId());
            AnswerValue normalized = null;
            if (submitted != null) {
                AnswerValueValidator.ValidationResult validation =
                        answerValueValidator.validate(type, snapshot, submitted);
                if (!validation.valid()) {
                    throw new BusinessRuleException("INVALID_ANSWER_FORMAT", null);
                }
                normalized = validation.normalized();
            }

            BigDecimal maxPoints = resolveQuestionPoints(type, snapshot, q.points());
            ItemGradeResult gradeResult = ItemGrader.grade(type, snapshot, normalized, maxPoints, policy);
            int unitCount = type == QuestionType.CLOZE
                    ? Math.max(1, snapshot.path("body").path("blanks").size())
                    : 1;

            itemInputs.add(new ItemInput(
                    q.questionId(),
                    ref.partUuid(),
                    null,
                    null,
                    null,
                    null,
                    type,
                    unitCount,
                    true,
                    gradeResult.pointsMax(),
                    gradeResult.earnedPoints(),
                    gradeResult.outcome(),
                    gradeResult.gradingDetail()));

            ReviewGradingDetail detailView =
                    AttemptResultService.projectGradingDetail(type, gradeResult.gradingDetail());
            gradedItems.add(new PreviewGradedItemView(
                    q.questionId(),
                    q.number(),
                    type,
                    gradeResult.outcome(),
                    gradeResult.earnedPoints(),
                    gradeResult.pointsMax(),
                    gradeResult.pendingReview(),
                    detailView));
        }

        AggregatedScore aggregated = ScoreAggregator.aggregate(
                new ScoreAggregator.Input(paper.kindCode(), "RAW", null, partInputs, itemInputs, List.of()));
        ScoreAggregator.UnitStats stats = aggregated.statistics();
        UnitStatsView statsView = new UnitStatsView(
                stats.correctCount(),
                stats.partialCount(),
                stats.wrongCount(),
                stats.blankCount(),
                stats.pendingReviewCount(),
                stats.totalCount());

        return new PreviewGradeResponse(
                paper.examId(),
                previewId,
                paper.kindCode(),
                aggregated.rawScore(),
                aggregated.rawMaxScore(),
                aggregated.pendingReview(),
                statsView,
                gradedItems);
    }

    private Map<VersionKey, Version> loadAllVersions(PreviewPaper paper, boolean includeSetRoots) {
        Set<VersionKey> keys = new LinkedHashSet<>();
        if (paper.parts() != null) {
            for (Part part : paper.parts()) {
                if (part.blocks() == null) {
                    continue;
                }
                for (Block block : part.blocks()) {
                    if (block.items() == null) {
                        continue;
                    }
                    for (Item item : block.items()) {
                        if (item.kind() == Item.Kind.QUESTION && item.question() != null) {
                            keys.add(new VersionKey(item.question().questionId(), item.question().versionNo()));
                        } else if (item.kind() == Item.Kind.SET && item.set() != null) {
                            if (includeSetRoots) {
                                keys.add(new VersionKey(item.set().questionId(), item.set().versionNo()));
                            }
                            if (item.set().children() != null) {
                                for (Question child : item.set().children()) {
                                    keys.add(new VersionKey(child.questionId(), child.versionNo()));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<Version> versions = questionSourcePort.loadVersions(List.copyOf(keys));
        Map<VersionKey, Version> map = new LinkedHashMap<>(versions.size());
        for (Version version : versions) {
            map.put(version.key(), version);
        }
        return map;
    }

    private static List<PreviewPaperSegmentView> mapSegments(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(s -> new PreviewPaperSegmentView(
                        s.code(),
                        s.name(),
                        s.fromPosition(),
                        s.toPosition(),
                        s.commandCount(),
                        s.maxPoints()))
                .toList();
    }

    private PreviewPaperBlockView toBlockView(Block block, Map<VersionKey, Version> versionMap) {
        List<PreviewPaperItemView> items = new ArrayList<>();
        if (block.items() != null) {
            for (Item item : block.items()) {
                if (item.kind() == Item.Kind.QUESTION && item.question() != null) {
                    items.add(new PreviewPaperItemView(
                            "QUESTION", toQuestionView(item.question(), versionMap), null));
                } else if (item.kind() == Item.Kind.SET && item.set() != null) {
                    items.add(new PreviewPaperItemView("SET", null, toSetView(item.set(), versionMap)));
                }
            }
        }
        return new PreviewPaperBlockView(
                block.blockId(),
                block.name(),
                block.subjectCode(),
                block.questionType(),
                block.manualGrading(),
                block.pointsEach(),
                block.firstNumber(),
                block.lastNumber(),
                block.questionCount(),
                items);
    }

    private static BigDecimal resolveQuestionPoints(
            QuestionType type, JsonNode snapshot, BigDecimal fallbackPoints) {
        if (type == QuestionType.CLOZE) {
            JsonNode blanks = snapshot.path("body").path("blanks");
            if (blanks.isArray() && !blanks.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (JsonNode blank : blanks) {
                    sum = sum.add(blank.hasNonNull("points")
                            ? blank.path("points").decimalValue()
                            : BigDecimal.ONE);
                }
                return sum;
            }
        }
        return fallbackPoints;
    }

    private PreviewPaperQuestionSetView toSetView(QuestionSet set, Map<VersionKey, Version> versionMap) {
        Version version = requireVersion(versionMap, new VersionKey(set.questionId(), set.versionNo()));
        JsonNode snapshot = version.snapshot();
        JsonNode body = snapshot.path("body");
        String content = AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(snapshot, "content"));
        String passage = AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(body, "passage"));
        Integer blankNumberStart = intOrNull(body, "blankNumberStart");
        List<PreviewPaperAttachmentView> attachments = mapAttachments(set.attachments());
        List<PreviewPaperQuestionView> children = set.children() == null
                ? List.of()
                : set.children().stream().map(c -> toQuestionView(c, versionMap)).toList();
        return new PreviewPaperQuestionSetView(
                set.questionId(),
                set.code(),
                set.versionNo(),
                content,
                passage,
                blankNumberStart,
                attachments,
                children);
    }

    private PreviewPaperQuestionView toQuestionView(Question q, Map<VersionKey, Version> versionMap) {
        Version version = requireVersion(versionMap, new VersionKey(q.questionId(), q.versionNo()));
        JsonNode snapshot = version.snapshot();
        QuestionType type = QuestionType.valueOf(q.type());
        String content = AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(snapshot, "content"));
        AttemptQuestionBody body = projectQuestionBody(type, snapshot, q.optionOrder());
        List<String> optionOrder = q.optionOrder() == null ? null : List.copyOf(q.optionOrder());
        List<PreviewPaperAttachmentView> attachments = mapAttachments(q.attachments());
        return new PreviewPaperQuestionView(
                q.number(),
                q.questionId(),
                q.code(),
                q.versionNo(),
                type,
                content,
                resolveQuestionPoints(type, snapshot, q.points()),
                optionOrder,
                attachments,
                body);
    }

    private static List<PreviewPaperAttachmentView> mapAttachments(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> new PreviewPaperAttachmentView(
                        a.id(), a.name(), a.mime(), AdminHtmlAssetHelper.ADMIN_ASSET_PREFIX + a.id()))
                .toList();
    }

    private static AttemptQuestionBody projectQuestionBody(
            QuestionType type, JsonNode snapshot, List<String> optionOrder) {
        JsonNode body = snapshot.path("body");
        return switch (type) {
            case SINGLE_CHOICE -> {
                List<OptionView> options = reorderById(readOptions(body.path("options")), optionOrder, OptionView::id);
                yield new AttemptQuestionBody.SingleChoice(QuestionType.SINGLE_CHOICE, options);
            }
            case MULTIPLE_CHOICE -> {
                List<OptionView> options = reorderById(readOptions(body.path("options")), optionOrder, OptionView::id);
                String scoringRule = textOrNull(body, "scoringRule");
                yield new AttemptQuestionBody.MultipleChoice(QuestionType.MULTIPLE_CHOICE, options, scoringRule);
            }
            case SHORT_ANSWER -> new AttemptQuestionBody.ShortAnswer(
                    QuestionType.SHORT_ANSWER,
                    textOrNull(body, "inputHint"),
                    textOrNull(body, "answerFormat"));
            case CLOZE -> {
                String passage = AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(body, "passage"));
                List<ClozeBlankView> blanks = new ArrayList<>();
                JsonNode rawBlanks = body.path("blanks");
                if (rawBlanks.isArray()) {
                    for (JsonNode b : rawBlanks) {
                        blanks.add(new ClozeBlankView(textOrNull(b, "id")));
                    }
                }
                yield new AttemptQuestionBody.Cloze(QuestionType.CLOZE, passage, blanks);
            }
            case QUESTION_SET -> new AttemptQuestionBody.QuestionSet(
                    QuestionType.QUESTION_SET,
                    AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(body, "passage")),
                    intOrNull(body, "blankNumberStart"));
            case TRUE_FALSE -> {
                List<StatementView> statements = new ArrayList<>();
                JsonNode rawStatements = body.path("statements");
                if (rawStatements.isArray()) {
                    for (JsonNode s : rawStatements) {
                        statements.add(new StatementView(
                                textOrNull(s, "id"),
                                AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(s, "content"))));
                    }
                }
                yield new AttemptQuestionBody.TrueFalse(QuestionType.TRUE_FALSE, statements);
            }
            case MATCHING -> {
                List<SideView> left = readSides(body.path("left"));
                List<SideView> right = reorderById(readSides(body.path("right")), optionOrder, SideView::id);
                Boolean reuseRight = body.has("reuseRight") && !body.get("reuseRight").isNull()
                        ? body.get("reuseRight").asBoolean()
                        : null;
                yield new AttemptQuestionBody.Matching(QuestionType.MATCHING, left, right, reuseRight);
            }
            case ESSAY -> new AttemptQuestionBody.Essay(QuestionType.ESSAY, intOrNull(body, "wordLimit"));
        };
    }

    private static List<OptionView> readOptions(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<OptionView> list = new ArrayList<>(arrayNode.size());
        for (JsonNode opt : arrayNode) {
            list.add(new OptionView(
                    textOrNull(opt, "id"), AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(opt, "content"))));
        }
        return list;
    }

    private static List<SideView> readSides(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<SideView> list = new ArrayList<>(arrayNode.size());
        for (JsonNode item : arrayNode) {
            list.add(new SideView(
                    textOrNull(item, "id"), AdminHtmlAssetHelper.ensureAdminImgSrc(textOrNull(item, "content"))));
        }
        return list;
    }

    private static <T> List<T> reorderById(List<T> items, List<String> order, Function<T, String> idFn) {
        if (items.isEmpty() || order == null || order.isEmpty()) {
            return items;
        }
        Map<String, T> byId = new LinkedHashMap<>(items.size());
        for (T item : items) {
            String id = idFn.apply(item);
            if (id != null) {
                byId.put(id, item);
            }
        }
        List<T> reordered = new ArrayList<>(items.size());
        for (String id : order) {
            T removed = byId.remove(id);
            if (removed != null) {
                reordered.add(removed);
            }
        }
        reordered.addAll(byId.values());
        return reordered;
    }

    private static Version requireVersion(Map<VersionKey, Version> versionMap, VersionKey key) {
        Version version = versionMap.get(key);
        if (version == null) {
            throw new IllegalStateException("Missing question version snapshot: " + key);
        }
        return version;
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
        return node.get(field).asInt();
    }

    private static UUID syntheticPartUuid(String partId) {
        String key = partId == null ? "part" : ("preview-part:" + partId);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private record LeafQuestionRef(UUID partUuid, Question question) {}
}
