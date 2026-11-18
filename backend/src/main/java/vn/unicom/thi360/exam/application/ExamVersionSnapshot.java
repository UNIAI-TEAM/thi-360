package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
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

    ObjectNode selectedCombinations(ObjectNode all, Set<String> selectedPaths) {
        Set<String> selected = new LinkedHashSet<>();
        for (String path : selectedPaths) selected.addAll(List.of(path.split("\\+")));
        ObjectNode result = mapper.createObjectNode();
        ArrayNode combinations = result.putArray("combinations");
        for (JsonNode item : all.path("combinations")) {
            if (selected.contains(item.path("partId").asText() + ":" + item.path("code").asText())) {
                combinations.add(item);
            }
        }
        return result;
    }

    ObjectNode parts(JsonNode document, JsonNode checkResult, JsonNode witnesses, Set<String> selectedPaths,
                     String timing, Integer durationMinutes) {
        ObjectNode snapshot = document.deepCopy();
        for (JsonNode part : snapshot.path("parts")) {
            Set<String> sectionIds = new LinkedHashSet<>();
            part.path("sections").forEach(section -> sectionIds.add(section.path("id").asText()));
            part.path("subjectPool").forEach(section -> sectionIds.add(section.path("id").asText()));
            for (JsonNode option : part.path("options")) {
                option.path("sections").forEach(section -> sectionIds.add(section.path("id").asText()));
                if (!option.path("blocks").isEmpty()) sectionIds.add(option.path("id").asText() + ":blocks");
            }
            ArrayNode paths = ((ObjectNode) part).putArray("paths");
            for (JsonNode checked : checkResult.path("paths")) {
                String pathId = checked.path("pathId").asText();
                if (!selectedPaths.contains(pathId)) continue;
                JsonNode proof = null;
                for (JsonNode witness : witnesses) {
                    List<String> choices = new ArrayList<>();
                    witness.path("path").path("chosen").forEach(choice -> choices.add(choice.path("optionId").asText()));
                    if (pathId.equals(choices.isEmpty() ? "default" : String.join("+", choices))) {
                        proof = witness.path("path");
                        break;
                    }
                }
                ObjectNode path = paths.addObject().put("pathId", pathId);
                Totals totals = new Totals();
                if (proof == null || !proof.path("sections").isArray()) {
                    totals.questions = null;
                    totals.points = null;
                } else {
                    for (JsonNode section : proof.path("sections")) {
                        if (sectionIds.contains(section.path("sectionId").asText())) totals.add(section, null);
                    }
                    for (JsonNode choices : proof.path("picks")) {
                        if (choices.isEmpty()) continue;
                        JsonNode pick = choices.get(0);
                        JsonNode option = null;
                        for (JsonNode candidate : part.path("options")) {
                            if (candidate.path("id").asText().equals(pick.path("optionId").asText())) option = candidate;
                        }
                        if (option == null) continue;
                        int i = 0;
                        for (JsonNode section : pick.path("sections")) {
                            JsonNode count = pick.path("counts").path(i++);
                            totals.add(section, count.isIntegralNumber() ? count.intValue() : null);
                        }
                        totals.trim(pick, option);
                    }
                }
                if (totals.questions == null) path.putNull("questionCount"); else path.put("questionCount", totals.questions);
                if (totals.points == null) path.putNull("maxPoints"); else path.put("maxPoints", totals.points);
                if ("PER_PART".equals(timing) && part.path("durationMinutes").isIntegralNumber()) {
                    path.put("durationMinutes", part.path("durationMinutes").intValue());
                } else if ("SHARED".equals(timing) && document.path("parts").size() == 1 && durationMinutes != null) {
                    path.put("durationMinutes", durationMinutes);
                } else path.putNull("durationMinutes");
            }
        }
        return snapshot;
    }

    private static final class Totals {
        Integer questions = 0;
        BigDecimal points = BigDecimal.ZERO;

        void add(JsonNode section, Integer selectedCount) {
            Integer sectionCount = selectedCount != null ? selectedCount
                    : section.path("questionCount").isIntegralNumber() ? section.path("questionCount").intValue() : null;
            int fixed = 0;
            for (JsonNode group : section.path("groups")) {
                if ("SET".equals(group.path("kind").asText())) fixed += group.path("setCount").asInt() * group.path("setSize").asInt();
                else if (group.path("questionCount").isIntegralNumber()) fixed += group.path("questionCount").intValue();
            }
            for (JsonNode group : section.path("groups")) {
                Integer count = "SET".equals(group.path("kind").asText())
                        ? group.path("setCount").asInt() * group.path("setSize").asInt()
                        : group.path("questionCount").isIntegralNumber() ? group.path("questionCount").intValue()
                        : sectionCount == null ? null : Math.max(0, sectionCount - fixed);
                if (count == null) {
                    questions = null;
                    points = null;
                } else {
                    if (questions != null) questions += count;
                    if (count > 0 && !group.path("requiredPointsPerUnit").isNumber()) points = null;
                    else if (points != null && count > 0) points = points.add(group.path("requiredPointsPerUnit").decimalValue().multiply(BigDecimal.valueOf(count)));
                }
            }
        }

        void trim(JsonNode pick, JsonNode option) {
            if (!option.path("countsByPick").isArray()) return;
            int actual = 0, target = 0;
            for (JsonNode count : pick.path("counts")) actual += count.asInt();
            for (JsonNode count : option.path("countsByPick")) target += count.asInt();
            int drop = actual - target;
            if (drop <= 0) return;
            if (questions != null) questions -= drop;
            int natural = 0, social = 0;
            for (JsonNode section : pick.path("sections")) {
                if ("NATURAL".equals(section.path("choiceGroup").asText())) natural++;
                if ("SOCIAL".equals(section.path("choiceGroup").asText())) social++;
            }
            String trimmed = natural == 0 || social == 0 ? null : natural < social ? "NATURAL" : "SOCIAL";
            Set<BigDecimal> values = new LinkedHashSet<>();
            for (JsonNode section : pick.path("sections")) {
                if (trimmed != null && !trimmed.equals(section.path("choiceGroup").asText())) continue;
                for (JsonNode group : section.path("groups")) {
                    if (!"SINGLE".equals(group.path("kind").asText())) continue;
                    if (!group.path("requiredPointsPerUnit").isNumber()) { points = null; return; }
                    values.add(group.path("requiredPointsPerUnit").decimalValue().stripTrailingZeros());
                }
            }
            if (values.size() != 1) points = null;
            else if (points != null) points = points.subtract(values.iterator().next().multiply(BigDecimal.valueOf(drop)));
        }
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
