package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;

/**
 * Names the MAPID rule behind a shortage (UNI-2758): the compiled topic quotas and caps carry the rule id as their
 * id, so a failure that points at one is a failure of that rule. Only a section in {@code MATRIX} mode compiles its
 * rules; a quota id that merely equals a rule id of an {@code ALL} section or of a legacy quota names nothing.
 */
final class MapidRuleNaming {
    private MapidRuleNaming() { }

    /** Sets {@code ruleId} and the declared {@code nodeIds} of the rule when {@code section} compiles that rule. */
    static boolean name(ObjectNode issue, ExamSection section, String ruleId) {
        if (section == null || ruleId == null || section.mapidMode() != MapidMode.MATRIX) return false;
        for (MapidRule rule : section.mapidRules()) {
            if (!ruleId.equals(rule.id())) continue;
            issue.put("ruleId", rule.id());
            ArrayNode nodes = issue.putArray("nodeIds");
            rule.nodeIds().forEach(node -> nodes.add(node.toString()));
            return true;
        }
        return false;
    }

    static ExamSection section(ExamStructureDocument document, String sectionId) {
        if (sectionId == null) return null;
        for (ExamPart part : document.parts()) {
            for (ExamSection section : part.sections()) if (sectionId.equals(section.id())) return section;
            for (ExamSection section : part.subjectPool()) if (sectionId.equals(section.id())) return section;
            for (ExamPartOption option : part.options()) {
                for (ExamSection section : option.sections()) if (sectionId.equals(section.id())) return section;
            }
        }
        return null;
    }
}
