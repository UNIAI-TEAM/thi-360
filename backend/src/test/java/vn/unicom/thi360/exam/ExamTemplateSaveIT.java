package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamTemplateSaveIT extends AbstractIntegrationTest {
    // FU-BE1-6: BE-5 will remove this narrowly scoped fast-check exception.
    private static final Set<String> CODE_SOURCED_PARTS = Set.of("BCA_FULL:3", "QDA_FULL:3");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void eachActiveTemplateCanBeLoadedSavedAndChecked() throws Exception {
        var templates = jdbc.queryForList("""
                select code, kind_code, scope, part_code, subject_code, build_mode, active
                from exam.exam_template order by code
                """);
        assertThat(templates).isNotEmpty();
        for (var template : templates) {
            String code = (String) template.get("code");
            assertThat(template.get("active")).as("%s must be loadable", code).isEqualTo(true);
            ObjectNode create = mapper.createObjectNode()
                    .put("kind", (String) template.get("kind_code"))
                    .put("scope", (String) template.get("scope"))
                    .put("name", "Template check")
                    .put("code", "T-" + UUID.randomUUID().toString().substring(0, 8))
                    .put("durationMinutes", 60);
            if (template.get("part_code") != null) create.put("partCode", (String) template.get("part_code"));
            if (template.get("subject_code") != null) create.put("subjectCode", (String) template.get("subject_code"));
            if (template.get("subject_code") == null && "SUBJECT".equals(template.get("scope"))) create.put("subjectCode", "TOAN");
            String created = mockMvc.perform(post("/api/v1/admin/exams")
                            .with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(create.toString()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode exam = mapper.readTree(created);
            String base = "/api/v1/admin/exams/" + exam.path("id").asText();
            ObjectNode load = mapper.createObjectNode().put("templateCode", code)
                    .put("confirmReplace", true).put("expectedRevision", exam.path("revision").asLong());
            String loaded = mockMvc.perform(post(base + "/structure/template")
                            .with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON).content(load.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode structure = mapper.readTree(loaded);
            ObjectNode update = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
            update.set("parts", structure.path("parts"));
            update.set("examQuotas", structure.path("examQuotas"));
            mockMvc.perform(put(base + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                    .andExpect(status().isOk());
            String checked = mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode result = mapper.readTree(checked);
            for (JsonNode issueList : result.findValues("issues")) {
                for (JsonNode issue : issueList) {
                    if ("STRUCTURE".equals(issue.path("group").asText())
                            && "ERROR".equals(issue.path("severity").asText())) {
                        assertThat(allowedCodeSourceIssue(code, structure.path("parts"), issue))
                                .as("%s structural issue: %s", code, issue).isTrue();
                    }
                }
            }
        }
    }

    private boolean allowedCodeSourceIssue(String code, JsonNode parts, JsonNode issue) {
        String issueCode = issue.path("code").asText();
        if ("PATH_TOTAL_MISMATCH".equals(issueCode)) {
            if (!issue.path("partId").isNull() || !issue.path("required").isIntegralNumber()
                    || !issue.path("available").isIntegralNumber()) return false;
            long missing = 0;
            boolean listed = false;
            for (int i = 0; i < parts.size(); i++) {
                if (CODE_SOURCED_PARTS.contains(code + ":" + (i + 1))) {
                    JsonNode part = parts.get(i);
                    if (!part.hasNonNull("codeSource") || !part.path("questionCount").isIntegralNumber()) return false;
                    missing += part.path("questionCount").asLong();
                    listed = true;
                }
            }
            return listed && missing > 0
                    && issue.path("required").asLong() - issue.path("available").asLong() == missing;
        }
        if (!Set.of("OPTION_EMPTY", "PART_EMPTY", "PART_TOTAL_MISMATCH").contains(issueCode)) return false;
        for (int i = 0; i < parts.size(); i++) {
            JsonNode part = parts.get(i);
            if (part.path("id").asText().equals(issue.path("partId").asText())) {
                return CODE_SOURCED_PARTS.contains(code + ":" + (i + 1))
                        && part.hasNonNull("codeSource")
                        && (!"OPTION_EMPTY".equals(issueCode)
                                || "CHOICE".equals(part.path("participation").path("mode").asText()));
            }
        }
        return false;
    }
}
