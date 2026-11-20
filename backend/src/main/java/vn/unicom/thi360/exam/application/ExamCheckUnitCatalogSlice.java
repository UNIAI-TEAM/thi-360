package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;

/**
 * The catalog data one subject unit can read while it is solved, as a stable JSON slice that goes into the unit
 * fingerprint instead of the revision counter of the whole catalog (FU-BE9-1): an edit of a catalog item changes the
 * slice of the units that use that item and of no other unit, so only those subjects are solved again.
 * <p>
 * A unit reads the catalog in four places: its subject (the question source filters by the subject item), the MAPID
 * nodes of its scopes and topic quotas (the solver expands every node to its active descendants, and the rules compare
 * a node with its ancestors), and the items that group, level, competency, tag and task-type codes of its quotas and
 * filters name (the source resolves a code to an item). Names are not read, so a rename changes no slice. Combinations
 * are not part of a unit: they decide which units a path uses, never how one unit is solved.
 * <p>
 * An instance caches the catalog lists it reads, so create one per check run, never keep it.
 */
final class ExamCheckUnitCatalogSlice {
    private static final List<String> CODE_GROUPS = List.of("GRADE", "QUESTION_LEVEL", "COMPETENCY", "QUESTION_TAG", "TASK_TYPE");

    private final CatalogLookup catalog;
    private final ObjectMapper mapper;
    private Map<UUID, CatalogItemView> knowledge;
    private Map<String, List<CatalogItemView>> itemsByCode;
    private final Map<UUID, List<UUID>> descendants = new HashMap<>();
    private final Map<String, UUID> subjects = new HashMap<>();

    ExamCheckUnitCatalogSlice(CatalogLookup catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    /** The slice of the catalog that a unit of {@code subjectCode} with this input reads. */
    JsonNode of(String subjectCode, JsonNode unitInput) {
        ObjectNode slice = mapper.createObjectNode();
        UUID subject = subjects.computeIfAbsent(subjectCode,
                code -> catalog.findActiveItem("SUBJECT", code).map(CatalogItemView::id).orElse(null));
        slice.put("subjectItemId", subject == null ? null : subject.toString());
        Map<String, JsonNode> nodes = new TreeMap<>();
        Map<String, JsonNode> items = new TreeMap<>();
        collect(unitInput, nodes, items);
        ArrayNode knowledgeNodes = slice.putArray("knowledge");
        nodes.values().forEach(knowledgeNodes::add);
        ArrayNode codedItems = slice.putArray("items");
        items.values().forEach(codedItems::add);
        return slice;
    }

    private void collect(JsonNode node, Map<String, JsonNode> nodes, Map<String, JsonNode> items) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if ("nodeIds".equals(field.getKey()) && field.getValue().isArray()) {
                    field.getValue().forEach(id -> {
                        if (id.isTextual()) knowledgeNode(id.asText(), nodes);
                    });
                } else {
                    collect(field.getValue(), nodes, items);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, nodes, items));
        } else if (node.isTextual()) {
            codedItems(node.asText(), items);
        }
    }

    private void knowledgeNode(String text, Map<String, JsonNode> nodes) {
        UUID id;
        try {
            id = UUID.fromString(text);
        } catch (IllegalArgumentException notAnId) {
            return;
        }
        Map<UUID, CatalogItemView> byId = knowledge();
        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", id.toString());
        CatalogItemView root = byId.get(id);
        entry.put("active", root != null);
        if (root != null) {
            ArrayNode ancestors = entry.putArray("ancestors");
            for (UUID parent = root.parentId(); parent != null && byId.containsKey(parent);
                 parent = byId.get(parent).parentId()) {
                ancestors.add(parent.toString());
            }
            List<String> below = descendants.computeIfAbsent(id, key -> byId.values().stream()
                    .filter(candidate -> candidate.path().startsWith(root.path())).map(CatalogItemView::id).toList())
                    .stream().map(UUID::toString).sorted().toList();
            ArrayNode ids = entry.putArray("descendants");
            below.forEach(ids::add);
        }
        nodes.put(id.toString(), entry);
    }

    private void codedItems(String text, Map<String, JsonNode> items) {
        if (text.isBlank() || text.length() > 64) return;
        List<CatalogItemView> matches = itemsByCode().get(text.strip().toLowerCase(Locale.ROOT));
        if (matches == null) return;
        for (CatalogItemView item : matches) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("group", item.groupCode());
            entry.put("id", item.id().toString());
            entry.put("code", item.code());
            entry.put("status", item.effectiveStatus() == null ? null : item.effectiveStatus().name());
            items.put(item.groupCode() + "|" + item.id(), entry);
        }
    }

    private Map<UUID, CatalogItemView> knowledge() {
        if (knowledge == null) {
            knowledge = new HashMap<>();
            for (CatalogItemView item : catalog.listActiveGroupItems("KNOWLEDGE")) knowledge.put(item.id(), item);
        }
        return knowledge;
    }

    /** Items of the groups whose codes a quota or a filter names, by normalized code, whatever their status. */
    private Map<String, List<CatalogItemView>> itemsByCode() {
        if (itemsByCode == null) {
            itemsByCode = new HashMap<>();
            for (String group : CODE_GROUPS) {
                for (CatalogItemView item : catalog.listGroupItems(group)) {
                    itemsByCode.computeIfAbsent(item.codeNormalized() == null ? item.code().toLowerCase(Locale.ROOT)
                            : item.codeNormalized(), key -> new ArrayList<>()).add(item);
                }
            }
        }
        return itemsByCode;
    }
}
