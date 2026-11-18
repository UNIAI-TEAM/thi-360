package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.domain.ExamQuota;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;

/** Stable identity and canonical solver inputs for one jointly solved subject. */
@Component
public class ExamCheckUnitFingerprint {
    private final ObjectMapper mapper;
    private final ExamCheckFingerprint fingerprints;

    public ExamCheckUnitFingerprint(ObjectMapper mapper, ExamCheckFingerprint fingerprints) {
        this.mapper = mapper;
        this.fingerprints = fingerprints;
    }

    public record Identity(String key, List<String> sectionIds, String fingerprint) { }

    public Identity calculate(String subject, List<ExamSection> sections, List<ExamQuota> quotas,
            ExamCheckFingerprint.Config config, Long profileRevision, long catalogRevision) {
        return calculate(subject, sections, quotas, config, profileRevision, catalogRevision, List.of());
    }

    Identity calculate(String subject, List<ExamSection> sections, List<ExamQuota> quotas,
            ExamCheckFingerprint.Config config, Long profileRevision, long catalogRevision,
            List<SectionOnPath> resolvedSections) {
        List<ExamSection> ordered = sections.stream().sorted(Comparator.comparing(ExamSection::id)).toList();
        List<String> ids = ordered.stream().map(ExamSection::id).toList();
        var input = mapper.createObjectNode();
        input.put("subjectCode", subject);
        input.set("sections", mapper.valueToTree(ordered));
        if (!resolvedSections.isEmpty()) {
            var resolvedCounts = mapper.createObjectNode();
            for (SectionOnPath section : resolvedSections) {
                if (section.questionCount() != null) resolvedCounts.put(section.sectionId(), section.questionCount());
            }
            input.set("resolvedCounts", resolvedCounts);
        }
        List<ExamQuota> required = new ArrayList<>();
        for (ExamQuota quota : quotas) {
            if (quota.enforcement() == QuotaEnforcement.REQUIRED && quota.subjectCodes().size() == 1
                    && subject.equals(quota.subjectCodes().getFirst())) required.add(quota);
        }
        input.set("examQuotas", mapper.valueToTree(required));
        removeDisplayNames(input);
        String key;
        try { key = mapper.writeValueAsString(List.of(subject, ids)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        return new Identity(key, ids, fingerprints.calculate(input, config, profileRevision, catalogRevision));
    }

    private static void removeDisplayNames(JsonNode node) {
        if (node.isObject()) {
            ((ObjectNode) node).remove("name");
            node.elements().forEachRemaining(ExamCheckUnitFingerprint::removeDisplayNames);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(ExamCheckUnitFingerprint::removeDisplayNames);
        }
    }
}
