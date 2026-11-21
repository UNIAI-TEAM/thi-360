package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptAttachmentView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptAudioPlayView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptItemView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptPartStateView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AudioPolicyView;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AudioPlayState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Builds the student-facing {@link AttemptStateResponse} strictly via allowlist DTOs, rewritten
 * attempt asset URLs, root question {@code attachments[]}, and reconciled audio play statuses (LT
 * §7.1, §7.3, BS §17.2, AD-121 #4, AC-18, AC-19, AC-25..27).
 */
@Service
public class AttemptStateService {

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptAudioPlayRepository attemptAudioPlayRepository;
    private final QuestionSourcePort questionSourcePort;
    private final QuestionAttachmentPort questionAttachmentPort;
    private final AttemptQuestionSanitizer sanitizer;
    private final EntityManager entityManager;
    private final Clock clock;

    public AttemptStateService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            QuestionSourcePort questionSourcePort,
            AttemptQuestionSanitizer sanitizer,
            Clock clock) {
        this(
                accessGate,
                attemptGuard,
                attemptPartRepository,
                attemptItemRepository,
                null,
                questionSourcePort,
                null,
                sanitizer,
                null,
                clock,
                false);
    }

    @Autowired
    public AttemptStateService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            QuestionSourcePort questionSourcePort,
            QuestionAttachmentPort questionAttachmentPort,
            AttemptQuestionSanitizer sanitizer,
            EntityManager entityManager,
            Clock clock) {
        this(
                accessGate,
                attemptGuard,
                attemptPartRepository,
                attemptItemRepository,
                attemptAudioPlayRepository,
                questionSourcePort,
                questionAttachmentPort,
                sanitizer,
                entityManager,
                clock,
                true);
    }

    private AttemptStateService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptAudioPlayRepository attemptAudioPlayRepository,
            QuestionSourcePort questionSourcePort,
            QuestionAttachmentPort questionAttachmentPort,
            AttemptQuestionSanitizer sanitizer,
            EntityManager entityManager,
            Clock clock,
            boolean requireAudioDeps) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptAudioPlayRepository =
                requireAudioDeps
                        ? Objects.requireNonNull(
                                attemptAudioPlayRepository,
                                "attemptAudioPlayRepository must not be null")
                        : attemptAudioPlayRepository;
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.questionAttachmentPort =
                requireAudioDeps
                        ? Objects.requireNonNull(
                                questionAttachmentPort, "questionAttachmentPort must not be null")
                        : questionAttachmentPort;
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.entityManager =
                requireAudioDeps
                        ? Objects.requireNonNull(entityManager, "entityManager must not be null")
                        : entityManager;
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
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
        List<AttemptAudioPlayView> audioPlayViews =
                loadAndReconcileAudioPlays(attempt, parts, visiblePartIds, now);
        AudioPolicyView audioPolicy =
                audioPlayViews.isEmpty()
                        ? null
                        : new AudioPolicyView(AttemptAudioPlay.MAX_PLAYS, false);

        List<AttemptItem> visibleItems =
                visiblePartIds.isEmpty()
                        ? List.of()
                        : attemptItemRepository.findByAttemptIdAndPartIdInOrderByPartIdAscOrdAsc(
                                attemptId, visiblePartIds);

        Map<VersionKey, JsonNode> snapshots = loadSnapshots(visibleItems);
        Map<VersionKey, List<Attachment>> attachmentsByVersion = loadAttachments(visibleItems);
        Map<UUID, List<AttemptItemView>> itemsByPartId = new LinkedHashMap<>();
        for (AttemptItem item : visibleItems) {
            JsonNode snapshot = resolveSnapshot(item, snapshots);
            AttemptItemView view = toItemView(attemptId, item, snapshot, attachmentsByVersion);
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
                audioPolicy,
                audioPlayViews,
                partViews);
    }

    private List<AttemptAudioPlayView> loadAndReconcileAudioPlays(
            Attempt attempt, List<AttemptPart> parts, List<UUID> visiblePartIds, Instant now) {
        if (attemptAudioPlayRepository == null) {
            return List.of();
        }
        List<AttemptAudioPlay> plays =
                attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attempt.getId());
        if (plays.isEmpty()) {
            return List.of();
        }
        Map<UUID, PartState> partStates = new LinkedHashMap<>();
        for (AttemptPart part : parts) {
            partStates.put(part.getId(), part.getState());
        }
        Set<UUID> partIdsToClose = new LinkedHashSet<>();
        for (AttemptAudioPlay play : plays) {
            if (play.getState() == AudioPlayState.PLAYING
                    && (attempt.getState() != AttemptState.IN_PROGRESS
                            || partStates.get(play.getPartId()) != PartState.OPEN)) {
                partIdsToClose.add(play.getPartId());
            }
        }
        if (!partIdsToClose.isEmpty()) {
            // Detach initially loaded entities so FOR UPDATE re-hydrates true committed DB rows & versions
            // instead of returning stale L1 cache instances (prevents 412 ObjectOptimisticLockingFailureException).
            if (entityManager != null) {
                plays.forEach(entityManager::detach);
            }
            List<AttemptAudioPlay> locked =
                    attemptAudioPlayRepository.findByAttemptIdAndPartIdInOrderByAssetIdAscForUpdate(
                            attempt.getId(), partIdsToClose);
            List<AttemptAudioPlay> changed = new ArrayList<>();
            for (AttemptAudioPlay play : locked) {
                if (play.getState() == AudioPlayState.PLAYING
                        && (attempt.getState() != AttemptState.IN_PROGRESS
                                || partStates.get(play.getPartId()) != PartState.OPEN)) {
                    play.markClosed(now);
                    changed.add(play);
                }
            }
            if (!changed.isEmpty()) {
                attemptAudioPlayRepository.saveAllAndFlush(changed);
            }
            plays = attemptAudioPlayRepository.findByAttemptIdOrderByAssetIdAsc(attempt.getId());
        }
        Set<UUID> visiblePartSet = Set.copyOf(visiblePartIds);
        return plays.stream()
                .filter(p -> visiblePartSet.contains(p.getPartId()))
                .map(
                        p ->
                                new AttemptAudioPlayView(
                                        p.getAssetId(),
                                        p.getPartId(),
                                        p.getDurationMs(),
                                        p.getPlayCount(),
                                        p.playsLeft(),
                                        p.getState(),
                                        p.getEndReason(),
                                        p.getPlayId(),
                                        p.getPositionMs()))
                .toList();
    }

    private Map<VersionKey, List<Attachment>> loadAttachments(List<AttemptItem> items) {
        if (questionAttachmentPort == null || items.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> rootKeys = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            if (item.getParentItemId() == null) {
                rootKeys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            }
        }
        if (rootKeys.isEmpty()) {
            return Map.of();
        }
        return questionAttachmentPort.attachments(rootKeys, false);
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

    private AttemptItemView toItemView(
            UUID attemptId,
            AttemptItem item,
            JsonNode snapshot,
            Map<VersionKey, List<Attachment>> attachmentsByVersion) {
        String content = sanitizer.rewriteAssetUrls(attemptId, textOrNull(snapshot, "content"));
        AttemptQuestionBody body = projectBody(attemptId, item, snapshot.path("body"));
        List<AttemptAttachmentView> itemAttachments = List.of();
        if (item.getParentItemId() == null && !attachmentsByVersion.isEmpty()) {
            List<Attachment> raw =
                    attachmentsByVersion.getOrDefault(
                            new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()),
                            List.of());
            if (!raw.isEmpty()) {
                itemAttachments =
                        raw.stream()
                                .map(
                                        att ->
                                                new AttemptAttachmentView(
                                                        att.id(),
                                                        att.name(),
                                                        att.mime(),
                                                        "/api/v1/me/attempts/"
                                                                + attemptId
                                                                + "/assets/"
                                                                + att.id()))
                                .toList();
            }
        }
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
                body,
                itemAttachments);
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
