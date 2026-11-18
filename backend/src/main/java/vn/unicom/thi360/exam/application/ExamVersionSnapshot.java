package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.solver.LargestRemainderApportioner;

/** Records integer quota allocations alongside the unchanged structure snapshot. */
final class ExamVersionSnapshot {
    private final ObjectMapper mapper;
    private final LargestRemainderApportioner apportioner = new LargestRemainderApportioner();

    ExamVersionSnapshot(ObjectMapper mapper) { this.mapper = mapper; }

    ObjectNode combinations(JsonNode document, Set<String> selectedPaths, UUID kindId, CatalogLookup catalog) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode combinations = result.putArray("combinations");
        Set<String> selected = new LinkedHashSet<>();
        for (String path : selectedPaths) selected.addAll(List.of(path.split("\\+")));
        boolean hasCodeSource = false;
        for (JsonNode part : document.path("parts")) hasCodeSource |= part.hasNonNull("codeSource");
        if (!hasCodeSource) return result;
        var definitions = catalog.listCombinations(kindId);
        for (JsonNode part : document.path("parts")) {
            if (!part.hasNonNull("codeSource")) continue;
            String partId = part.path("id").asText();
            String branch = part.path("codeSource").path("branch").asText(null);
            for (var definition : definitions) {
                if (!selected.contains(partId + ":" + definition.itemCode())) continue;
                if (branch != null && (definition.branch() == null || !branch.equals(definition.branch().name()))) continue;
                ObjectNode item = combinations.addObject();
                item.put("partId", partId);
                item.put("code", definition.itemCode());
                item.put("name", definition.itemNameVi());
                if (definition.branch() == null) item.putNull("branch"); else item.put("branch", definition.branch().name());
                item.put("versionNo", definition.versionNo());
                ArrayNode subjects = item.putArray("subjects");
                int position = 0;
                for (var subject : definition.subjects()) {
                    ObjectNode row = subjects.addObject();
                    row.put("code", subject.code());
                    row.put("name", subject.nameVi());
                    row.put("position", position++);
                }
            }
        }
        return result;
    }

    ObjectNode parts(JsonNode document, JsonNode checkResult, Set<String> selectedPaths) {
        ObjectNode snapshot = document.deepCopy();
        for (JsonNode part : snapshot.path("parts")) {
            ArrayNode paths = ((ObjectNode) part).putArray("paths");
            for (JsonNode checked : checkResult.path("paths")) {
                String pathId = checked.path("pathId").asText();
                if (!selectedPaths.contains(pathId)) continue;
                ObjectNode path = paths.addObject();
                path.put("pathId", pathId);
                path.set("questionCount", checked.path("totalQuestions").isMissingNode()
                        ? mapper.nullNode() : checked.path("totalQuestions"));
                path.set("maxPoints", checked.path("totalPoints").isMissingNode()
                        ? mapper.nullNode() : checked.path("totalPoints"));
                path.set("durationMinutes", checked.path("totalMinutes").isMissingNode()
                        ? mapper.nullNode() : checked.path("totalMinutes"));
            }
        }
        return snapshot;
    }

    ArrayNode omittedGroups(JsonNode document) {
        ArrayNode groups = mapper.createArrayNode();
        collectOmitted(document, groups);
        return groups;
    }

    private void collectOmitted(JsonNode node, ArrayNode groups) {
        if (node.isArray()) {
            node.forEach(child -> collectOmitted(child, groups));
        } else if (node.isObject()) {
            if (node.path("omitted").asBoolean(false)) {
                ObjectNode group = groups.addObject();
                group.set("name", node.path("name"));
                int count = node.path("questionCount").asInt();
                if (count == 0) count = node.path("setCount").asInt() * node.path("setSize").asInt();
                if (node.path("requiredPointsPerQuestion").isNumber()) {
                    group.put("points", node.path("requiredPointsPerQuestion").decimalValue()
                            .multiply(java.math.BigDecimal.valueOf(count)));
                } else {
                    group.putNull("points");
                }
            } else {
                node.elements().forEachRemaining(child -> collectOmitted(child, groups));
            }
        }
    }

    ObjectNode resolvedQuotas(JsonNode document, Set<String> selectedPaths) {
        Set<String> selectedOptions = new LinkedHashSet<>();
        for (String path : selectedPaths) {
            if (!"default".equals(path)) selectedOptions.addAll(List.of(path.split("\\+")));
        }
        ObjectNode resolved = mapper.createObjectNode();
        for (JsonNode part : document.path("parts")) {
            sections(part.path("sections"), Set.of(), resolved);
            for (JsonNode option : part.path("options")) {
                if (!selectedOptions.contains(option.path("id").asText())) continue;
                Set<Integer> counts = new LinkedHashSet<>();
                for (JsonNode count : option.path("countsByPick")) counts.add(count.intValue());
                sections(option.path("sections"), counts, resolved);
                if (option.path("blocks").isArray() && !option.path("blocks").isEmpty()) {
                    int total = 0;
                    for (JsonNode block : option.path("blocks")) {
                        if (block.path("questionCount").isIntegralNumber()) total += block.path("questionCount").intValue();
                        else if (block.path("setCount").isIntegralNumber() && block.path("setSize").isIntegralNumber())
                            total += block.path("setCount").intValue() * block.path("setSize").intValue();
                    }
                    ObjectNode synthetic = mapper.createObjectNode();
                    synthetic.put("id", option.path("id").asText() + ":blocks");
                    synthetic.put("questionCount", total);
                    synthetic.set("blocks", option.path("blocks"));
                    sections(mapper.createArrayNode().add(synthetic), Set.of(), resolved);
                }
            }
        }
        return resolved;
    }

    private void sections(JsonNode sections, Set<Integer> choiceCounts, ObjectNode output) {
        for (JsonNode section : sections) {
            Set<Integer> totals = new LinkedHashSet<>();
            if (section.path("questionCount").isIntegralNumber()) totals.add(section.path("questionCount").intValue());
            else totals.addAll(choiceCounts);
            ObjectNode byCount = output.putObject(section.path("id").asText());
            for (int total : totals) {
                ObjectNode row = byCount.putObject(Integer.toString(total));
                row.set("answerQuotas", quota(section.path("answerQuotas"), "items", total));
                row.set("matrix", matrix(section.path("matrix"), total));
                int fixed = 0;
                for (JsonNode block : section.path("blocks")) {
                    if (block.path("questionCount").isIntegralNumber()) fixed += block.path("questionCount").intValue();
                    else if (block.path("setCount").isIntegralNumber() && block.path("setSize").isIntegralNumber())
                        fixed += block.path("setCount").intValue() * block.path("setSize").intValue();
                }
                ObjectNode blocks = row.putObject("blocks");
                for (JsonNode block : section.path("blocks")) {
                    int count = block.path("questionCount").isIntegralNumber() ? block.path("questionCount").intValue()
                            : block.path("setCount").isIntegralNumber() && block.path("setSize").isIntegralNumber()
                            ? block.path("setCount").intValue() * block.path("setSize").intValue() : total - fixed;
                    blocks.set(block.path("id").asText(), matrix(block.path("matrix"), count));
                }
            }
        }
    }

    private JsonNode matrix(JsonNode matrix, int total) {
        if (!matrix.isObject()) return com.fasterxml.jackson.databind.node.NullNode.instance;
        ObjectNode result = mapper.createObjectNode();
        for (String key : List.of("cells", "byGrade", "byDifficulty", "byCompetency")) {
            result.set(key, quota(matrix, key, total));
        }
        return result;
    }

    private JsonNode quota(JsonNode source, String key, int total) {
        if (!source.isObject()) return com.fasterxml.jackson.databind.node.NullNode.instance;
        ArrayNode out = mapper.createArrayNode();
        JsonNode rows = source.path(key);
        if (!rows.isArray() || rows.isEmpty()) return out;
        List<Integer> values = new ArrayList<>();
        for (JsonNode row : rows) values.add(row.path("value").decimalValue().intValueExact());
        int[] counts = "PERCENT".equals(source.path("unit").asText())
                ? apportioner.apportion(values, total) : values.stream().mapToInt(Integer::intValue).toArray();
        for (int i = 0; i < rows.size(); i++) {
            ObjectNode row = rows.get(i).deepCopy();
            row.put("value", counts[i]);
            out.add(row);
        }
        return out;
    }
}
