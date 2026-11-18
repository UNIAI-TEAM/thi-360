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
}
