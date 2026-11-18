package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.Map;
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
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void eachStandardTemplateCanBeLoadedSavedAndChecked() throws Exception {
        var templates = jdbc.queryForList("""
                select code, kind_code, scope, part_code, subject_code, build_mode, active, publishable,
                       structure::text as document
                from exam.exam_template where build_mode = 'STANDARD' order by code
                """);
        assertThat(templates).isNotEmpty();
        Set<String> failures = new LinkedHashSet<>();
        for (var template : templates) {
            String code = (String) template.get("code");
            try {
                checkTemplate(template, failures);
            } catch (AssertionError | Exception failure) {
                failures.add(code + ": " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        assertThat(failures).as("template save/check failures").isEmpty();
    }


    private void checkTemplate(Map<String, Object> template, Set<String> failures) throws Exception {
            String code = (String) template.get("code");
            assertThat(template.get("active")).as("%s must be loadable", code).isEqualTo(true);
            ObjectNode create = mapper.createObjectNode()
                    .put("kind", (String) template.get("kind_code"))
                    .put("scope", (String) template.get("scope"))
                    .put("name", "Template check")
                    .put("code", "T-" + UUID.randomUUID().toString().substring(0, 8))
                    .put("durationMinutes", 60);
            create.put("buildMode", (String) template.get("build_mode"));
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
            String readBack = mockMvc.perform(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode saved = mapper.readTree(readBack);
            assertThat(omittedIds(saved.path("parts"))).as("%s omitted block flags", code)
                    .containsExactlyInAnyOrderElementsOf(omittedIds(structure.path("parts")));
            String checked = mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode result = mapper.readTree(checked);
            for (JsonNode issueList : result.findValues("issues")) {
                for (JsonNode issue : issueList) {
                    if ("POINTS_TOTAL_MISMATCH".equals(issue.path("code").asText())) {
                        failures.add(code + " points issue: " + issue);
                    }
                    // SET_SHORTAGE reports bank availability, not set configuration.
                    if ("SET_SHORTAGE".equals(issue.path("code").asText())) continue;
                    // TEMPLATE_NOT_PUBLISHABLE is the publish policy of the template, not structure configuration (D11).
                    if ("TEMPLATE_NOT_PUBLISHABLE".equals(issue.path("code").asText())) {
                        if (!Boolean.FALSE.equals(template.get("publishable"))) {
                            failures.add(code + " reports TEMPLATE_NOT_PUBLISHABLE but publishable is not false");
                        }
                        continue;
                    }
                    if (Set.of("STRUCTURE", "QUESTION_SET").contains(issue.path("group").asText())
                            && "ERROR".equals(issue.path("severity").asText())) {
                        failures.add(code + " configuration issue: " + issue);
                    }
                }
            }
    }

    private Set<String> omittedIds(JsonNode node) {
        Set<String> ids = new LinkedHashSet<>();
        collectOmittedIds(node, ids);
        return ids;
    }

    private void collectOmittedIds(JsonNode node, Set<String> ids) {
        if (node.isObject() && node.path("omitted").asBoolean()) {
            assertThat(node.path("id").asText()).isNotBlank();
            ids.add(node.path("id").asText());
        }
        node.forEach(child -> collectOmittedIds(child, ids));
    }

}
