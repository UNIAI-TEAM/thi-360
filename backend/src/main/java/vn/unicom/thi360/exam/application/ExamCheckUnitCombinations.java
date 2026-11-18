package vn.unicom.thi360.exam.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

/** Maps the active catalog codes into the code-sourced parts that use them. */
final class ExamCheckUnitCombinations {
    private ExamCheckUnitCombinations() { }

    static Map<String, Map<String, List<String>>> forStructure(
            CatalogLookup catalog, UUID examKindId, ExamStructureDocument document) {
        if (document.parts().stream().noneMatch(part -> part.codeSource() != null)) return Map.of();
        var definitions = catalog.listCombinations(examKindId);
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        for (var part : document.parts()) {
            if (part.codeSource() == null) continue;
            String branch = part.codeSource().branch();
            Map<String, List<String>> codes = new LinkedHashMap<>();
            for (var definition : definitions) {
                if (branch != null && (definition.branch() == null || !branch.equals(definition.branch().name()))) continue;
                codes.put(definition.itemCode(), definition.subjectCodes());
            }
            result.put(part.id(), codes);
        }
        return result;
    }

    static Map<UUID, UUID> topicParents(CatalogLookup catalog, ExamStructureDocument document) {
        boolean hasPairs = document.parts().stream().anyMatch(part ->
                hasTopicPairs(part.sections()) || hasTopicPairs(part.subjectPool())
                || part.options().stream().anyMatch(option -> hasTopicPairs(option.sections())
                        || option.blocks().stream().anyMatch(block -> block.topicQuotas().size() > 1)));
        if (!hasPairs) return Map.of();
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        for (var node : catalog.listGroupItems("KNOWLEDGE")) {
            if (node.parentId() != null) parents.put(node.id(), node.parentId());
        }
        return parents;
    }

    private static boolean hasTopicPairs(List<vn.unicom.thi360.exam.domain.ExamSection> sections) {
        return sections.stream().anyMatch(section -> section.topicQuotas().size() > 1
                || section.blocks().stream().anyMatch(block -> block.topicQuotas().size() > 1));
    }
}
