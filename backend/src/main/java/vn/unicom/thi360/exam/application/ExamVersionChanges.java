package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.TreeSet;
import vn.unicom.thi360.exam.api.ExamVersionChange;

/** Field-level differences use values from the two immutable snapshots. */
public final class ExamVersionChanges {
    private ExamVersionChanges() { }

    public static List<ExamVersionChange> between(JsonNode previousConfig, JsonNode previousParts,
                                                  JsonNode config, JsonNode parts) {
        List<ExamVersionChange> changes = new ArrayList<>();
        compare(previousConfig, config, "config", null, changes);
        compare(previousParts, parts, "parts", null, changes);
        return List.copyOf(changes);
    }

    private static void compare(JsonNode before, JsonNode after, String path, String blockId, List<ExamVersionChange> out) {
        if (java.util.Objects.equals(before, after)) return;
        if (before != null && after != null && before.isObject() && after.isObject()) {
            TreeSet<String> names = new TreeSet<>();
            before.fieldNames().forEachRemaining(names::add);
            after.fieldNames().forEachRemaining(names::add);
            if (after.hasNonNull("id") && (after.has("questionType") || after.has("setCount"))) {
                blockId = after.path("id").asText();
            }
            for (String name : names) {
                if ("resolvedQuotas".equals(name)) continue;
                compare(before.get(name), after.get(name), path + "/" + name, blockId, out);
            }
            return;
        }
        String collection = path.substring(path.lastIndexOf('/') + 1);
        if (List.of("parts", "sections", "blocks", "options").contains(collection)) {
            if (before == null && after != null && after.isArray())
                before = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            if (after == null && before != null && before.isArray())
                after = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        if (before != null && after != null && before.isArray() && after.isArray()) {
            if (List.of("parts", "sections", "blocks", "options").contains(collection)
                    && identified(before) && identified(after)) {
                Map<String, JsonNode> oldItems = byId(before);
                Map<String, JsonNode> newItems = byId(after);
                List<String> oldOrder = oldItems.keySet().stream().filter(newItems::containsKey).toList();
                List<String> newOrder = newItems.keySet().stream().filter(oldItems::containsKey).toList();
                if (!oldOrder.equals(newOrder)) {
                    out.add(new ExamVersionChange(path, "ORDER_CHANGED", path,
                            oldOrder.toString(), newOrder.toString(), blockId, null));
                }
                for (var entry : oldItems.entrySet()) {
                    String id = entry.getKey();
                    JsonNode next = newItems.get(id);
                    if (next == null) entityChange(entry.getValue(), null, path + "/" + id, collection, blockId, out);
                    else compare(entry.getValue(), next, path + "/" + id,
                            "blocks".equals(collection) ? id : blockId, out);
                }
                for (var entry : newItems.entrySet()) {
                    if (!oldItems.containsKey(entry.getKey()))
                        entityChange(null, entry.getValue(), path + "/" + entry.getKey(), collection, blockId, out);
                }
                return;
            }
            int common = Math.min(before.size(), after.size());
            for (int i = 0; i < common; i++) compare(before.get(i), after.get(i), path + "/" + i, blockId, out);
            for (int i = common; i < before.size(); i++) compare(before.get(i), null, path + "/" + i, blockId, out);
            for (int i = common; i < after.size(); i++) compare(null, after.get(i), path + "/" + i, blockId, out);
            return;
        }
        String kind = path.contains("matrix") || path.contains("answerQuotas") ? "MATRIX_CHANGED" : "BLOCK_CHANGED";
        out.add(new ExamVersionChange(path, kind, path, text(before), text(after), blockId, null));
    }

    private static boolean identified(JsonNode array) {
        var ids = new java.util.HashSet<String>();
        for (JsonNode item : array) {
            if (!item.hasNonNull("id") || !ids.add(item.path("id").asText())) return false;
        }
        return true;
    }

    private static Map<String, JsonNode> byId(JsonNode array) {
        Map<String, JsonNode> items = new LinkedHashMap<>();
        for (JsonNode item : array) items.put(item.path("id").asText(), item);
        return items;
    }

    private static void entityChange(JsonNode before, JsonNode after, String path, String collection,
                                     String blockId, List<ExamVersionChange> out) {
        String kind = switch (collection) {
            case "blocks" -> before == null ? "BLOCK_ADDED" : "BLOCK_REMOVED";
            case "options" -> before == null ? "OPTION_ADDED" : "OPTION_REMOVED";
            default -> "BLOCK_CHANGED";
        };
        if ("blocks".equals(collection)) blockId = Objects.requireNonNullElse(before, after).path("id").asText();
        out.add(new ExamVersionChange(path, kind, path, text(before), text(after), blockId, null));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.textValue() : node.toString();
    }
}
