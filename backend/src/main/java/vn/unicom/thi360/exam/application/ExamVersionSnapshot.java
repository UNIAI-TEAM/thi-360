package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vn.unicom.thi360.exam.domain.solver.LargestRemainderApportioner;

/** Records integer quota allocations alongside the unchanged structure snapshot. */
final class ExamVersionSnapshot {
    private final ObjectMapper mapper;
    private final LargestRemainderApportioner apportioner = new LargestRemainderApportioner();

    ExamVersionSnapshot(ObjectMapper mapper) { this.mapper = mapper; }

    ObjectNode resolvedQuotas(JsonNode document) {
        ObjectNode resolved = mapper.createObjectNode();
        for (JsonNode part : document.path("parts")) {
            sections(part.path("sections"), Set.of(), resolved);
            for (JsonNode option : part.path("options")) {
                Set<Integer> counts = new LinkedHashSet<>();
                for (JsonNode count : option.path("countsByPick")) counts.add(count.intValue());
                sections(option.path("sections"), counts, resolved);
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
