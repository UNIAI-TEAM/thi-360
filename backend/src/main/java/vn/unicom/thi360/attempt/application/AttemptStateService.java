package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.Cloze;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.ClozeBlankView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.Essay;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.Matching;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.MultipleChoice;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.OptionView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.QuestionSet;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.ShortAnswer;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.SideView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.SingleChoice;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.StatementView;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody.TrueFalse;
import vn.unicom.thi360.attempt.api.AttemptStateResponse;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptItemView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptPartStateView;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Builds the student-facing {@link AttemptStateResponse} strictly via allowlist DTOs and rewritten
 * attempt asset URLs (LT §7.1, §7.3, AD-121 #4, AC-25, AC-26, AC-27).
 */
@Service
public class AttemptStateService {

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final QuestionSourcePort questionSourcePort;
    private final AttemptQuestionSanitizer sanitizer;
    private final Clock clock;

    public AttemptStateService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            QuestionSourcePort questionSourcePort,
            AttemptQuestionSanitizer sanitizer,
            Clock clock) {
        this.accessGate = accessGate;
        this.attemptGuard = attemptGuard;
        this.attemptPartRepository = attemptPartRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.questionSourcePort = questionSourcePort;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttemptStateResponse getState(UUID attemptId) {
        UUID userId = accessGate.requireLearner();
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);
        Instant now = clock.instant();

        if (attempt.getState() == AttemptState.STARTING) {
            return AttemptStateResponse.starting(attempt.getId(), now);
        }
        if (attempt.getState() == AttemptState.ABORTED) {
            return AttemptStateResponse.aborted(attempt.getId(), now, attempt.getAbortReason());
        }

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<UUID> visiblePartIds =
                parts.stream()
                        .filter(AttemptStateService::isPartVisible)
                        .map(AttemptPart::getId)
                        .toList();

        List<AttemptItem> visibleItems =
                visiblePartIds.isEmpty()
                        ? List.of()
                        : attemptItemRepository.findByAttemptIdAndPartIdInOrderByOrdAsc(
                                attemptId, visiblePartIds);

        Map<VersionKey, JsonNode> snapshots = loadSnapshots(visibleItems);
        Map<UUID, List<AttemptItemView>> itemsByPartId = new LinkedHashMap<>();
        for (AttemptItem item : visibleItems) {
            JsonNode snapshot = resolveSnapshot(item, snapshots);
            AttemptItemView view = toItemView(attemptId, item, snapshot);
            itemsByPartId.computeIfAbsent(item.getPartId(), k -> new ArrayList<>()).add(view);
        }

        List<AttemptPartStateView> partViews =
                parts.stream()
                        .map(
                                p ->
                                        new AttemptPartStateView(
                                                p.getId(),
                                                p.getPartCode(),
                                                p.getPartName(),
                                                p.getOrd(),
                                                p.getState(),
                                                p.getDurationMinutes(),
                                                p.getQuestionCountRequired(),
                                                p.getMaxPoints(),
                                                p.getStartedAt(),
                                                p.getDeadlineAt(),
                                                p.getClosedAt(),
                                                p.getChoice(),
                                                p.getChoiceDeadlineAt(),
                                                isPartVisible(p)
                                                        ? itemsByPartId.getOrDefault(p.getId(), List.of())
                                                        : List.of()))
                        .toList();

        return new AttemptStateResponse(
                now,
                attempt.getId(),
                attempt.getState(),
                null,
                null,
                attempt.getExamId(),
                attempt.getExamVersionNo(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getPartCode(),
                attempt.getTiming(),
                attempt.getNavigationMode(),
                attempt.getAttemptNo(),
                attempt.getChoices(),
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                attempt.getLastPosition(),
                attempt.getMaxScore(),
                partViews);
    }

    private static boolean isPartVisible(AttemptPart part) {
        return part.getState() == PartState.OPEN || part.getState() == PartState.CLOSED;
    }

    private Map<VersionKey, JsonNode> loadSnapshots(List<AttemptItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        Map<VersionKey, JsonNode> map = new LinkedHashMap<>();
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        map.putIfAbsent(
                                new VersionKey(UUID.fromString(idText), version.key().versionNo()),
                                child);
                    }
                }
            }
        }
        return map;
    }

    private static JsonNode resolveSnapshot(
            AttemptItem item, Map<VersionKey, JsonNode> snapshots) {
        JsonNode snapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        if (snapshot == null) {
            throw new IllegalStateException(
                    "Missing question snapshot for attempt item "
                            + item.getId()
                            + " question="
                            + item.getQuestionId()
                            + " v"
                            + item.getQuestionVersionNo());
        }
        return snapshot;
    }

    private AttemptItemView toItemView(UUID attemptId, AttemptItem item, JsonNode snapshot) {
        String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(snapshot, "content"));
        AttemptQuestionBody body = projectBody(attemptId, item, snapshot.path("body"));
        return new AttemptItemView(
                item.getId(),
                item.getParentItemId(),
                item.getDisplayNo(),
                item.getUnitCount(),
                item.getQuestionType(),
                item.getPointsMax(),
                item.getSectionId(),
                item.getComponentCode(),
                item.getBlockId(),
                item.getOptionOrder(),
                item.isFlagged(),
                item.getAnswerSeq(),
                item.getAnswer(),
                content,
                body);
    }

    private AttemptQuestionBody projectBody(UUID attemptId, AttemptItem item, JsonNode body) {
        QuestionType type = item.getQuestionType();
        return switch (type) {
            case SINGLE_CHOICE ->
                    new SingleChoice(
                            type, projectOrderedOptions(attemptId, body.path("options"), item.getOptionOrder()));
            case MULTIPLE_CHOICE ->
                    new MultipleChoice(
                            type,
                            projectOrderedOptions(attemptId, body.path("options"), item.getOptionOrder()),
                            textOrNull(body, "scoringRule"));
            case SHORT_ANSWER ->
                    new ShortAnswer(
                            type, textOrNull(body, "inputHint"), textOrNull(body, "answerFormat"));
            case CLOZE ->
                    new Cloze(
                            type,
                            sanitizer.rewriteAssetUrls(attemptId, textOrNull(body, "passage")),
                            projectBlanks(body.path("blanks")));
            case QUESTION_SET ->
                    new QuestionSet(
                            type,
                            sanitizer.rewriteAssetUrls(attemptId, textOrNull(body, "passage")),
                            intOrNull(body, "blankNumberStart"));
            case TRUE_FALSE ->
                    new TrueFalse(type, projectStatements(attemptId, body.path("statements")));
            case MATCHING ->
                    new Matching(
                            type,
                            projectSides(attemptId, body.path("left")),
                            projectOrderedSides(attemptId, body.path("right"), item.getOptionOrder()),
                            boolOrNull(body, "reuseRight"));
            case ESSAY -> new Essay(type, intOrNull(body, "wordLimit"));
        };
    }

    private List<OptionView> projectOrderedOptions(
            UUID attemptId, JsonNode arrayNode, List<String> order) {
        Map<String, OptionView> byId = new LinkedHashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"));
                if (id != null) {
                    byId.put(id, new OptionView(id, content));
                }
            }
        }
        return reorderById(byId, order);
    }

    private List<SideView> projectSides(UUID attemptId, JsonNode arrayNode) {
        List<SideView> sides = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"));
                if (id != null) {
                    sides.add(new SideView(id, content));
                }
            }
        }
        return sides;
    }

    private List<SideView> projectOrderedSides(
            UUID attemptId, JsonNode arrayNode, List<String> order) {
        Map<String, SideView> byId = new LinkedHashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String id = textOrNull(node, "id");
                String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"));
                if (id != null) {
                    byId.put(id, new SideView(id, content));
                }
            }
        }
        return reorderById(byId, order);
    }

    private List<StatementView> projectStatements(UUID attemptId, JsonNode arrayNode) {
        List<StatementView> statements = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                statements.add(
                        new StatementView(
                                textOrNull(node, "id"),
                                sanitizer.rewriteAssetUrls(attemptId, textOrNull(node, "content"))));
            }
        }
        return statements;
    }

    private static List<ClozeBlankView> projectBlanks(JsonNode arrayNode) {
        List<ClozeBlankView> blanks = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                blanks.add(new ClozeBlankView(textOrNull(node, "id")));
            }
        }
        return blanks;
    }

    private static <T> List<T> reorderById(Map<String, T> byId, List<String> order) {
        if (order == null || order.isEmpty()) {
            return List.copyOf(byId.values());
        }
        List<T> ordered = new ArrayList<>(byId.size());
        Set<String> added = new LinkedHashSet<>();
        for (String id : order) {
            T entry = byId.get(id);
            if (entry != null && added.add(id)) {
                ordered.add(entry);
            }
        }
        for (Map.Entry<String, T> entry : byId.entrySet()) {
            if (added.add(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asBoolean();
    }
}
