package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Resolves and authorizes question asset downloads scoped to an owned attempt, visible parts
 * ({@code OPEN} or {@code CLOSED}), in-scope items, and strictly student-visible fields (LT §7.3,
 * F12, AC-14, AC-15). Object storage I/O happens outside any DB transaction.
 */
@Service
public class AttemptAssetService {

    private static final Pattern ADMIN_ASSET_URL_PATTERN =
            Pattern.compile(
                    "/api/v1/admin/question-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final QuestionSourcePort questionSourcePort;
    private final QuestionAssetContentPort questionAssetContentPort;
    private final TransactionTemplate readOnlyTx;

    public AttemptAssetService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            QuestionSourcePort questionSourcePort,
            QuestionAssetContentPort questionAssetContentPort,
            PlatformTransactionManager transactionManager) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.questionAssetContentPort = Objects.requireNonNull(questionAssetContentPort);
        this.readOnlyTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.readOnlyTx.setReadOnly(true);
    }

    /**
     * Authorizes and loads the requested asset for the authenticated learner's attempt. Throws 404
     * if the attempt does not exist, belongs to another user, is {@code STARTING}/{@code ABORTED},
     * or if the asset is not referenced in any student-visible field of an opened/closed in-scope
     * item.
     */
    public AssetContent loadAttemptAsset(UUID attemptId, UUID assetId) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || assetId == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);
        if (attempt.getState() == AttemptState.STARTING
                || attempt.getState() == AttemptState.ABORTED) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        boolean allowed =
                Boolean.TRUE.equals(
                        readOnlyTx.execute(status -> isAssetAllowed(attemptId, assetId)));
        if (!allowed) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        // Load binary bytes from object storage outside any database transaction.
        return questionAssetContentPort
                .loadContent(assetId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    private boolean isAssetAllowed(UUID attemptId, UUID assetId) {
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<UUID> visiblePartIds =
                parts.stream()
                        .filter(
                                part ->
                                        part.getState() == PartState.OPEN
                                                || part.getState() == PartState.CLOSED)
                        .map(AttemptPart::getId)
                        .toList();
        if (visiblePartIds.isEmpty()) {
            return false;
        }

        List<AttemptItem> partItems =
                attemptItemRepository.findByAttemptIdAndPartIdInOrderByPartIdAscOrdAsc(
                        attemptId, visiblePartIds);
        if (partItems.isEmpty()) {
            return false;
        }

        Map<UUID, AttemptItem> itemsById = new HashMap<>(partItems.size() * 2);
        Set<UUID> parentsWithChildren = new HashSet<>();
        Set<UUID> parentsWithInScopeChildren = new HashSet<>();
        for (AttemptItem item : partItems) {
            itemsById.put(item.getId(), item);
            if (item.getParentItemId() != null) {
                parentsWithChildren.add(item.getParentItemId());
                if (item.isInScope()) {
                    parentsWithInScopeChildren.add(item.getParentItemId());
                }
            }
        }

        List<AttemptItem> visibleItems =
                partItems.stream()
                        .filter(
                                item ->
                                        isItemStudentVisible(
                                                item,
                                                itemsById,
                                                parentsWithChildren,
                                                parentsWithInScopeChildren))
                        .toList();
        if (visibleItems.isEmpty()) {
            return false;
        }

        Map<VersionKey, JsonNode> snapshots = loadSnapshots(visibleItems, itemsById);
        Set<UUID> allowedIds = new HashSet<>();
        for (AttemptItem item : visibleItems) {
            JsonNode snapshot =
                    snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            if (snapshot == null) {
                continue;
            }
            collectStudentVisibleAssets(item.getQuestionType(), snapshot, allowedIds);
            if (allowedIds.contains(assetId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isItemStudentVisible(
            AttemptItem item,
            Map<UUID, AttemptItem> itemsById,
            Set<UUID> parentsWithChildren,
            Set<UUID> parentsWithInScopeChildren) {
        if (!item.isInScope()) {
            return false;
        }
        if (item.getParentItemId() != null) {
            AttemptItem parent = itemsById.get(item.getParentItemId());
            if (parent != null && !parent.isInScope()) {
                return false;
            }
        }
        if (item.getQuestionType() == QuestionType.QUESTION_SET
                && parentsWithChildren.contains(item.getId())
                && !parentsWithInScopeChildren.contains(item.getId())) {
            return false;
        }
        return true;
    }

    private Map<VersionKey, JsonNode> loadSnapshots(
            List<AttemptItem> visibleItems, Map<UUID, AttemptItem> itemsById) {
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : visibleItems) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }

        Map<VersionKey, JsonNode> map = new LinkedHashMap<>();
        loadVersionsInto(keys, map);

        Set<VersionKey> parentKeys = new LinkedHashSet<>();
        for (AttemptItem item : visibleItems) {
            VersionKey key = new VersionKey(item.getQuestionId(), item.getQuestionVersionNo());
            if (!map.containsKey(key) && item.getParentItemId() != null) {
                AttemptItem parent = itemsById.get(item.getParentItemId());
                if (parent == null) {
                    parent = attemptItemRepository.findById(item.getParentItemId()).orElse(null);
                }
                if (parent != null) {
                    parentKeys.add(
                            new VersionKey(parent.getQuestionId(), parent.getQuestionVersionNo()));
                }
            }
        }
        if (!parentKeys.isEmpty()) {
            loadVersionsInto(parentKeys, map);
        }
        return map;
    }

    private void loadVersionsInto(Set<VersionKey> keys, Map<VersionKey, JsonNode> map) {
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        try {
                            map.putIfAbsent(
                                    new VersionKey(
                                            UUID.fromString(idText), version.key().versionNo()),
                                    child);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        }
    }

    private static void collectStudentVisibleAssets(
            QuestionType questionType, JsonNode snapshot, Set<UUID> out) {
        extractHtmlAssetIds(snapshot.path("content").asText(null), out);

        JsonNode attachments = snapshot.path("attachmentIds");
        if (attachments.isArray()) {
            for (JsonNode att : attachments) {
                addUuid(att.asText(null), out);
            }
        }

        JsonNode body = snapshot.path("body");
        if (body.isMissingNode() || body.isNull() || questionType == null) {
            return;
        }
        // Only student-visible body fields: never solution, gradingGuide, rubric, sampleAnswer,
        // commonMistakes, blanks[].answers, or children[].solution.
        switch (questionType) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> collectArrayFieldHtml(body, "options", "content", out);
            case SHORT_ANSWER -> extractHtmlAssetIds(body.path("inputHint").asText(null), out);
            case CLOZE, QUESTION_SET -> extractHtmlAssetIds(body.path("passage").asText(null), out);
            case TRUE_FALSE -> collectArrayFieldHtml(body, "statements", "content", out);
            case MATCHING -> {
                collectArrayFieldHtml(body, "left", "content", out);
                collectArrayFieldHtml(body, "right", "content", out);
            }
            case ESSAY -> {
                // Intentionally empty: gradingGuide, rubric, and sampleAnswer are confidential.
            }
        }
    }

    private static void collectArrayFieldHtml(
            JsonNode body, String arrayField, String htmlField, Set<UUID> out) {
        JsonNode array = body.path(arrayField);
        if (!array.isArray()) {
            return;
        }
        for (JsonNode elem : array) {
            extractHtmlAssetIds(elem.path(htmlField).asText(null), out);
        }
    }

    private static void extractHtmlAssetIds(String html, Set<UUID> out) {
        if (html == null || html.isBlank()) {
            return;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.select("[data-asset-id]")) {
            addUuid(el.attr("data-asset-id"), out);
        }
        Matcher matcher = ADMIN_ASSET_URL_PATTERN.matcher(html);
        while (matcher.find()) {
            addUuid(matcher.group(1), out);
        }
    }

    private static void addUuid(String raw, Set<UUID> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            out.add(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
        }
    }
}
