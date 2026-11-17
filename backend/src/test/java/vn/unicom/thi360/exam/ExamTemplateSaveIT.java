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
    // FU-BE1-6: BE-5 will remove this narrowly scoped fast-check exception.
    private static final Set<String> CODE_SOURCED_PARTS = Set.of(
            "BCA_FULL:3", "BCA_PART_TN_TU_CHON:1",
            "QDA_FULL:3", "QDA_PART_SCIENCE:1", "QDA_PART_ENGLISH:1");
    // FU-BE7-3: H3 retires the legacy variable-count checker rules.
    private static final Set<String> HSA_SCIENCE_TEMPLATES = Set.of("HSA_FULL", "HSA_PART_SCIENCE");
    private static final Map<String, Integer> CODE_SOURCE_COUNTS = Map.of(
            "BCA_FULL:3", 15, "BCA_PART_TN_TU_CHON:1", 15,
            "QDA_FULL:3", 50, "QDA_PART_SCIENCE:1", 50, "QDA_PART_ENGLISH:1", 50);
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

    @Test
    void codeSourceExceptionRejectsChangedDeclaredCountAndAvailability() throws Exception {
        JsonNode parts = mapper.readTree("""
                [{},{},{"id":"p3","questionCount":15,"questionCountRequired":15,
                         "codeSource":{"source":"GLOBAL_LIST"}}]
                """);
        ObjectNode issue = mapper.createObjectNode().put("code", "PART_TOTAL_MISMATCH")
                .put("partId", "p3").put("required", 15).put("available", 0);
        assertThat(allowedCodeSourceIssue("BCA_FULL", parts, issue)).isTrue();
        issue.put("available", 1);
        assertThat(allowedCodeSourceIssue("BCA_FULL", parts, issue)).isFalse();
        issue.put("available", 0);
        ((ObjectNode) parts.get(2)).put("questionCount", 16);
        assertThat(allowedCodeSourceIssue("BCA_FULL", parts, issue)).isFalse();
        issue.put("code", "PATH_TOTAL_MISMATCH").putNull("partId").put("required", 82).put("available", 66);
        assertThat(allowedCodeSourceIssue("BCA_FULL", parts, issue)).isFalse();
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
                    // SET_SHORTAGE reports bank availability, not set configuration.
                    if ("SET_SHORTAGE".equals(issue.path("code").asText())) continue;
                    if (Set.of("STRUCTURE", "QUESTION_SET").contains(issue.path("group").asText())
                            && "ERROR".equals(issue.path("severity").asText())) {
                        if (!allowedCodeSourceIssue(code, saved.path("parts"), issue)
                                && !allowedOmittedBlockIssue(code, saved.path("parts"), issue)
                                && !allowedHsaScienceIssue(code, saved.path("parts"), issue)) {
                            failures.add(code + " configuration issue: " + issue);
                        }
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

    private boolean allowedHsaScienceIssue(String code, JsonNode parts, JsonNode issue) {
        if (!HSA_SCIENCE_TEMPLATES.contains(code)
                || !Set.of("REMAINDER_GROUP_INVALID", "SUBJECT_QUOTA_COUNT_VARIES").contains(issue.path("code").asText())) return false;
        for (JsonNode part : parts) {
            if (!part.path("id").asText().equals(issue.path("partId").asText())) continue;
            for (JsonNode option : part.path("options")) {
                if (!"SUBJECT_CHOICE".equals(option.path("kind").asText())
                        || !option.path("id").asText().equals(issue.path("optionId").asText())) continue;
                for (JsonNode section : option.path("sections")) {
                    if (section.path("id").asText().equals(issue.path("sectionId").asText())
                            && Set.of("LY", "HOA", "SINH", "SU", "DIA").contains(section.path("subjectCode").asText())) return true;
                }
            }
        }
        return false;
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
                Integer expected = CODE_SOURCE_COUNTS.get(code + ":" + (i + 1));
                if (expected != null) {
                    JsonNode part = parts.get(i);
                    if (!part.hasNonNull("codeSource") || part.path("questionCount").asInt(-1) != expected
                            || part.path("questionCountRequired").asInt(-1) != expected) return false;
                    missing += expected;
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
                Integer expected = CODE_SOURCE_COUNTS.get(code + ":" + (i + 1));
                return expected != null && part.hasNonNull("codeSource")
                        && part.path("questionCount").asInt(-1) == expected
                        && part.path("questionCountRequired").asInt(-1) == expected
                        && (!"PART_TOTAL_MISMATCH".equals(issueCode)
                                || (issue.path("required").asInt(-1) == expected && issue.path("available").asInt(-1) == 0))
                        && (!"OPTION_EMPTY".equals(issueCode)
                                || "CHOICE".equals(part.path("participation").path("mode").asText()));
            }
        }
        return false;
    }
}
