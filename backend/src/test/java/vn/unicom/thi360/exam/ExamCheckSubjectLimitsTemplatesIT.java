package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * CR-4 AC-6: every active standard template of HSA and QDA (FULL, PART, SUBJECT) loads into an exam that the two
 * R-12 rules accept. Sections sit in {@code sections}, {@code subjectPool} and {@code options[].sections}; the
 * review templates are not standard exams and are left out.
 */
class ExamCheckSubjectLimitsTemplatesIT extends AbstractSubjectLimitsIT {

    @Test
    void everyActiveStandardTemplateComplies() throws Exception {
        List<Map<String, Object>> templates = jdbc.queryForList("SELECT code, kind_code, scope, part_code, subject_code "
                + "FROM exam.exam_template WHERE kind_code IN ('HSA', 'QDA') AND active AND build_mode = 'STANDARD' "
                + "ORDER BY code");
        assertThat(templates).as("HSA and QDA standard templates").hasSizeGreaterThanOrEqualTo(18);
        List<String> failures = new ArrayList<>();
        boolean pool = false;
        boolean options = false;
        boolean sections = false;
        int scienceSections = 0;
        for (Map<String, Object> template : templates) {
            String code = (String) template.get("code");
            String base = create((String) template.get("kind_code"), (String) template.get("scope"),
                    (String) template.get("part_code"), (String) template.get("subject_code"), null);
            JsonNode before = structure(base);
            json(post(base + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.createObjectNode().put("templateCode", code).put("confirmReplace", true)
                            .put("expectedRevision", before.path("revision").asLong()).toString()), 200);
            JsonNode loaded = structure(base);
            for (JsonNode part : loaded.path("parts")) {
                sections |= part.path("sections").size() > 0;
                pool |= part.path("subjectPool").size() > 0;
                for (JsonNode option : part.path("options")) options |= option.path("sections").size() > 0;
            }
            scienceSections += countScience(loaded);
            JsonNode result = check(base);
            List<String> limited = codes(result).stream().filter(c -> SET_CODE.equals(c) || SHORT_CODE.equals(c)).toList();
            if (!limited.isEmpty()) {
                failures.add(code + " -> " + issues(result).stream()
                        .filter(item -> limited.contains(item.path("code").asText()))
                        .map(item -> item.path("code").asText() + "@" + item.path("subjectCode").asText()).toList());
            }
        }
        assertThat(failures).as("templates that break the R-12 rules").isEmpty();
        assertThat(scienceSections).as("science sections the rules looked at").isPositive();
        assertThat(sections && pool && options).as("sections, subjectPool and options[].sections were all exercised").isTrue();
    }

    private static int countScience(JsonNode node) {
        int count = 0;
        if (node.isObject()) {
            String subject = node.path("subjectCode").asText();
            if (node.has("blocks") && List.of("LY", "HOA", "SINH", "SU", "DIA").contains(subject)) count++;
            for (var it = node.elements(); it.hasNext();) count += countScience(it.next());
        } else if (node.isArray()) {
            for (JsonNode child : node) count += countScience(child);
        }
        return count;
    }
}
