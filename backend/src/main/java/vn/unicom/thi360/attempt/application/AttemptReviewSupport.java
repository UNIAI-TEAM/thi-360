package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Shared helpers for review and solution-asset authorization, parent/child visibility filtering,
 * and issued version snapshot loading.
 */
final class AttemptReviewSupport {

    private AttemptReviewSupport() {}

    static String resolveEffectiveResultView(EntitlementPort entitlementPort, Attempt attempt) {
        try {
            EntitlementBenefits benefits = entitlementPort.benefitsOf(attempt.getId());
            if (benefits != null && benefits.resultView() != null && !benefits.resultView().isBlank()) {
                return benefits.resultView();
            }
        } catch (NotFoundException ignored) {
            // Fall back to persisted resultView when synthetic test rows have no entitlement_usage row
        }
        return attempt.getResultView();
    }

    /**
     * Filters items matching {@code AttemptAssetService#isItemStudentVisible}: excludes out-of-scope
     * items, children whose parent {@code QUESTION_SET} is out of scope, and {@code QUESTION_SET}
     * parents whose children are all out of scope.
     */
    static List<AttemptItem> filterStudentVisibleItems(List<AttemptItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<UUID, AttemptItem> byId = new HashMap<>(items.size() * 2);
        Set<UUID> parentsWithChildren = new HashSet<>();
        Set<UUID> parentsWithInScopeChildren = new HashSet<>();
        for (AttemptItem item : items) {
            byId.put(item.getId(), item);
            if (item.getParentItemId() != null) {
                parentsWithChildren.add(item.getParentItemId());
                if (item.isInScope()) {
                    parentsWithInScopeChildren.add(item.getParentItemId());
                }
            }
        }
        List<AttemptItem> visible = new ArrayList<>(items.size());
        for (AttemptItem item : items) {
            if (!item.isInScope()) {
                continue;
            }
            if (item.getParentItemId() != null) {
                AttemptItem parent = byId.get(item.getParentItemId());
                if (parent != null && !parent.isInScope()) {
                    continue;
                }
            }
            if (item.getQuestionType() == QuestionType.QUESTION_SET
                    && parentsWithChildren.contains(item.getId())
                    && !parentsWithInScopeChildren.contains(item.getId())) {
                continue;
            }
            visible.add(item);
        }
        return visible;
    }

    static Map<VersionKey, JsonNode> loadSnapshots(
            List<AttemptItem> visibleItems,
            Map<UUID, AttemptItem> allItemsById,
            AttemptItemRepository attemptItemRepository,
            QuestionSourcePort questionSourcePort) {
        if (visibleItems == null || visibleItems.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : visibleItems) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        Map<VersionKey, JsonNode> map = new LinkedHashMap<>(keys.size() * 2);
        loadVersionsInto(keys, map, questionSourcePort);

        Set<UUID> missingParentItemIds = new LinkedHashSet<>();
        Set<VersionKey> parentKeys = new LinkedHashSet<>();
        for (AttemptItem item : visibleItems) {
            VersionKey key = new VersionKey(item.getQuestionId(), item.getQuestionVersionNo());
            if (!map.containsKey(key) && item.getParentItemId() != null) {
                AttemptItem parent = allItemsById != null ? allItemsById.get(item.getParentItemId()) : null;
                if (parent != null) {
                    parentKeys.add(new VersionKey(parent.getQuestionId(), parent.getQuestionVersionNo()));
                } else {
                    missingParentItemIds.add(item.getParentItemId());
                }
            }
        }
        if (!missingParentItemIds.isEmpty()) {
            for (AttemptItem parent : attemptItemRepository.findAllById(missingParentItemIds)) {
                parentKeys.add(new VersionKey(parent.getQuestionId(), parent.getQuestionVersionNo()));
            }
        }
        if (!parentKeys.isEmpty()) {
            loadVersionsInto(parentKeys, map, questionSourcePort);
        }
        return map;
    }

    private static void loadVersionsInto(
            Set<VersionKey> keys, Map<VersionKey, JsonNode> map, QuestionSourcePort questionSourcePort) {
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            if (version == null || version.snapshot() == null) {
                continue;
            }
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        try {
                            map.putIfAbsent(
                                    new VersionKey(UUID.fromString(idText.trim()), version.key().versionNo()),
                                    child);
                        } catch (IllegalArgumentException ignored) {
                            // Ignore malformed child UUID
                        }
                    }
                }
            }
        }
    }
}
