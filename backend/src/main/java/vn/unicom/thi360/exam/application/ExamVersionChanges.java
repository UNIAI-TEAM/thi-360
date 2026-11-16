package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
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
        if (before != null && after != null && before.isArray() && after.isArray()) {
            int common = Math.min(before.size(), after.size());
            for (int i = 0; i < common; i++) compare(before.get(i), after.get(i), path + "/" + i, blockId, out);
            for (int i = common; i < before.size(); i++) compare(before.get(i), null, path + "/" + i, blockId, out);
            for (int i = common; i < after.size(); i++) compare(null, after.get(i), path + "/" + i, blockId, out);
            return;
        }
        String kind = path.contains("matrix") || path.contains("answerQuotas") ? "MATRIX_CHANGED"
                : path.contains("groupOrder") || path.contains("shuffle") ? "ORDER_RULE_CHANGED"
                : before == null && path.contains("options/") ? "OPTION_ADDED"
                : after == null && path.contains("options/") ? "OPTION_REMOVED"
                : before == null && path.contains("blocks/") ? "BLOCK_ADDED"
                : after == null && path.contains("blocks/") ? "BLOCK_REMOVED" : "BLOCK_CHANGED";
        out.add(new ExamVersionChange(path, kind, path, text(before), text(after), blockId, null));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.textValue() : node.toString();
    }
}
