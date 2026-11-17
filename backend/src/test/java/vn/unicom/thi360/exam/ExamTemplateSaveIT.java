package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
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
    // FU-BE1-6: BE-5 will remove this narrowly scoped fast-check exception.
    private static final Set<String> CODE_SOURCED_PARTS = Set.of(
            "BCA_FULL:3", "BCA_PART_TN_TU_CHON:1",
            "QDA_FULL:3", "QDA_PART_SCIENCE:1", "QDA_PART_ENGLISH:1");
    // FU-BE7-2: BE-5 will make the checker skip omitted blocks and remove this exception.
    private static final Set<String> OMITTED_BLOCK_TEMPLATES = Set.of(
            "SP2E_VAN_2026", "SP2E_VAN_OLD", "SPT_MATH_2025", "SPT_LY_2025",
            "SPT_HOA_2025", "SPT_SINH_2025", "SPT_SU_2025", "SPT_DIA_2025",
            "SPT_ECON_LAW_2025", "SPT_TECH_INDUSTRY_2025", "SPT_TECH_AGRICULTURE_2025",
            "SPT_VAN_2025", "SPT_ANH_2025", "SPT_INFORMATICS_2025");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void eachActiveTemplateCanBeLoadedSavedAndChecked() throws Exception {
        var templates = jdbc.queryForList("""
                select code, kind_code, scope, part_code, subject_code, build_mode, active,
                       structure::text as document
                from exam.exam_template order by code
                """);
        assertThat(templates).isNotEmpty();
        Set<String> actualCodeSourcedParts = new LinkedHashSet<>();
        Set<String> actualOmittedTemplates = new LinkedHashSet<>();
        Set<String> failures = new LinkedHashSet<>();
        for (var template : templates) {
            String code = (String) template.get("code");
            JsonNode parts = mapper.readTree((String) template.get("document")).path("parts");
            if (hasOmittedBlocks(parts)) actualOmittedTemplates.add(code);
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).hasNonNull("codeSource")) actualCodeSourcedParts.add(code + ":" + (i + 1));
            }
        }
        if (!actualCodeSourcedParts.equals(CODE_SOURCED_PARTS)) {
            failures.add("Code-source exception positions differ: actual " + actualCodeSourcedParts
                    + ", expected " + CODE_SOURCED_PARTS);
        }
        if (!actualOmittedTemplates.equals(OMITTED_BLOCK_TEMPLATES)) {
            failures.add("Omitted-block exception templates differ: actual " + actualOmittedTemplates
                    + ", expected " + OMITTED_BLOCK_TEMPLATES);
        }
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

    @Test
    void omittedBlockExceptionRequiresExactSectionOrUnambiguousPath() throws Exception {
        JsonNode parts = mapper.readTree("""
                [{"id":"p1","sections":[{"id":"s1","blocks":[
                    {"questionCount":4},{"questionCount":2,"omitted":true}
                ]}],"options":[]},{"id":"p2","sections":[{"id":"s2","blocks":[
                    {"questionCount":1,"omitted":true}
                ]}],"options":[]}]
                """);
        ObjectNode issue = mapper.createObjectNode().put("code", "SECTION_TOTAL_MISMATCH")
                .put("partId", "p1").put("sectionId", "s1").put("required", 4).put("available", 6);
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isTrue();
        assertThat(allowedOmittedBlockIssue("OTHER", parts, issue)).isFalse();
        issue.put("available", 7);
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isFalse();
        issue.put("available", 6).put("sectionId", "s2");
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isFalse();
        issue.put("code", "PATH_TOTAL_MISMATCH").putNull("partId").putNull("sectionId");
        issue.put("required", 4).put("available", 7);
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isTrue();
        issue.put("available", 6);
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isFalse();
        ((ObjectNode) parts.get(0)).putArray("options").addObject().putArray("sections")
                .addObject().putArray("blocks").addObject().put("omitted", true).put("questionCount", 1);
        issue.put("available", 7);
        assertThat(allowedOmittedBlockIssue("SPT_VAN_2025", parts, issue)).isFalse();
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
                        if (!allowedCodeSourceIssue(code, structure.path("parts"), issue)
                                && !allowedOmittedBlockIssue(code, structure.path("parts"), issue)) {
                            failures.add(code + " structural issue: " + issue);
                        }
                    }
                }
            }
    }

    private boolean allowedOmittedBlockIssue(String code, JsonNode parts, JsonNode issue) {
        if (!OMITTED_BLOCK_TEMPLATES.contains(code)
                || !issue.path("required").isIntegralNumber() || !issue.path("available").isIntegralNumber()) {
            return false;
        }
        long difference = issue.path("available").asLong() - issue.path("required").asLong();
        if (difference <= 0) return false;
        if ("SECTION_TOTAL_MISMATCH".equals(issue.path("code").asText())) {
            if (!issue.path("partId").isTextual() || !issue.path("sectionId").isTextual()
                    || issue.path("partId").asText().isBlank() || issue.path("sectionId").asText().isBlank()) return false;
            for (JsonNode part : parts) {
                if (!part.path("id").asText().equals(issue.path("partId").asText())) continue;
                for (JsonNode sections : part.findValues("sections")) {
                    for (JsonNode section : sections) {
                        if (section.path("id").asText().equals(issue.path("sectionId").asText())) {
                            return omittedQuestions(section.path("blocks")) == difference;
                        }
                    }
                }
            }
        } else if ("PATH_TOTAL_MISMATCH".equals(issue.path("code").asText())
                && issue.path("partId").isNull() && issue.path("sectionId").isNull()) {
            long omitted = 0;
            for (JsonNode part : parts) {
                // Branch-specific omissions need path identity; do not guess which option was selected.
                if (hasOmittedBlocks(part.path("options")) || hasOmittedBlocks(part.path("subjectPool"))) return false;
                for (JsonNode section : part.path("sections")) {
                    long count = omittedQuestions(section.path("blocks"));
                    if (count < 0) return false;
                    omitted += count;
                }
            }
            return omitted == difference;
        }
        return false;
    }

    private boolean hasOmittedBlocks(JsonNode node) {
        return node.findValues("omitted").stream().anyMatch(JsonNode::asBoolean);
    }

    private long omittedQuestions(JsonNode blocks) {
        long count = 0;
        for (JsonNode block : blocks) {
            if (!block.path("omitted").asBoolean()) continue;
            if (!block.path("questionCount").isIntegralNumber()
                    || block.path("questionCount").asLong() <= 0) return -1;
            count += block.path("questionCount").asLong();
        }
        return count;
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
