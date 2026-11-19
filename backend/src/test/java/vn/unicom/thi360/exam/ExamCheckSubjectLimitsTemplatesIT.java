package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    /**
     * R1-14: the rules are dispatched for the sections of {@code subjectPool} (QDA) and {@code options[].sections} (HSA),
     * not only for {@code sections}. A broken Lý pool section is a located error on the QDA paths that hold Lý and on
     * no other path; a broken Hóa option section is a located error on the HSA paths.
     */
    @Test
    void rulesReachSubjectPoolAndOptionSections() throws Exception {
        String qda = loadFullTemplate("QDA");
        JsonNode qdaBefore = structure(qda);
        ObjectNode qdaDoc = qdaBefore.deepCopy();
        ObjectNode pool = sectionIn(qdaDoc, "LY", "subjectPool");
        setShape(pool, 3, 3);
        save(qda, qdaBefore, qdaDoc);
        JsonNode qdaResult = check(qda);
        JsonNode setIssue = issue(qdaResult, SET_CODE);
        assertThat(setIssue.path("sectionId").asText()).isEqualTo(pool.path("id").asText());
        assertThat(setIssue.path("subjectCode").asText()).isEqualTo("LY");
        java.util.Set<String> holdingScience = new java.util.TreeSet<>(jdbc.queryForList(
                "SELECT i.code FROM catalog.catalog_item i JOIN catalog.combination_version v ON v.item_id = i.id AND v.is_current "
                        + "JOIN catalog.combination_subject cs ON cs.combination_version_id = v.id "
                        + "JOIN catalog.catalog_item s ON s.id = cs.subject_item_id JOIN catalog.catalog_item k ON k.id = v.exam_item_id "
                        + "WHERE s.code = 'LY' AND k.code = 'QDA'", String.class));
        assertThat(holdingScience).as("QDA combinations with Lý").isNotEmpty();
        java.util.Set<String> failing = new java.util.TreeSet<>();
        java.util.Set<String> others = new java.util.TreeSet<>();
        for (JsonNode path : qdaResult.path("paths")) {
            String code = path.path("choices").path(0).path("code").asText();
            boolean located = java.util.stream.StreamSupport.stream(path.path("issues").spliterator(), false)
                    .anyMatch(item -> SET_CODE.equals(item.path("code").asText())
                            && pool.path("id").asText().equals(item.path("sectionId").asText()));
            if (located) {
                failing.add(code);
                assertThat(path.path("result").asText()).as("path " + code).isEqualTo("INVALID");
            } else {
                others.add(code);
            }
        }
        assertThat(failing).as("QDA paths with the broken Lý pool section").isEqualTo(holdingScience);
        assertThat(others).as("QDA paths without Lý carry no such error").isNotEmpty().doesNotContainAnyElementsOf(holdingScience);

        String hsa = loadFullTemplate("HSA");
        JsonNode hsaBefore = structure(hsa);
        ObjectNode hsaDoc = hsaBefore.deepCopy();
        ObjectNode option = sectionIn(hsaDoc, "HOA", "options");
        dropShortAnswer(option);
        save(hsa, hsaBefore, hsaDoc);
        JsonNode hsaResult = check(hsa);
        JsonNode shortIssue = issue(hsaResult, SHORT_CODE);
        assertThat(shortIssue.path("sectionId").asText()).isEqualTo(option.path("id").asText());
        assertThat(shortIssue.path("subjectCode").asText()).isEqualTo("HOA");
        assertThat(java.util.stream.StreamSupport.stream(hsaResult.path("paths").spliterator(), false)
                .anyMatch(path -> path.path("issues").toString().contains(SHORT_CODE))).as("an HSA path carries the error").isTrue();
    }

    @Test
    void unusedPoolSectionIsReportedWithoutInvalidatingEnglishPath() throws Exception {
        String base = loadFullTemplate("QDA");
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode history = sectionIn(edited, "SU", "subjectPool");
        setShape(history, 0, 3);
        for (JsonNode part : edited.path("parts")) {
            if (part.path("codeSource").isObject()) ((ObjectNode) part.path("codeSource")).put("branch", "ENGLISH");
        }
        save(base, before, edited);
        JsonNode result = check(base);
        JsonNode found = issue(result, SET_CODE);
        assertThat(found.path("sectionId").asText()).isEqualTo(history.path("id").asText());
        assertThat(found.path("subjectCode").asText()).isEqualTo("SU");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        JsonNode english = java.util.stream.StreamSupport.stream(result.path("paths").spliterator(), false)
                .filter(path -> path.path("choices").toString().contains("QD05")).findFirst().orElseThrow();
        assertThat(english.path("result").asText()).isEqualTo("VALID");
        assertThat(english.path("issues").toString()).doesNotContain(SET_CODE);
    }

    /**
     * R2-3: a subject can be in an active combination without being on any evaluated path. Lý is kept in the pool
     * with three sets, but Hóa, Sinh, Sử and Địa are gone: every combination with Lý misses another subject, becomes a
     * placeholder path, and Lý is solved on no path. Its located R-12 error is still reported at the check, and the
     * English path stays valid.
     */
    @Test
    void poolSectionOfASubjectOnNoEvaluatedPathIsReported() throws Exception {
        String base = loadFullTemplate("QDA");
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode physics = sectionIn(edited, "LY", "subjectPool");
        setShape(physics, 3, 3);
        for (JsonNode part : edited.path("parts")) {
            if (!part.path("codeSource").isObject()) continue;
            com.fasterxml.jackson.databind.node.ArrayNode kept = mapper.createArrayNode();
            for (JsonNode section : part.path("subjectPool")) {
                if (List.of("LY", "ANH").contains(section.path("subjectCode").asText())) kept.add(section);
            }
            ((ObjectNode) part).set("subjectPool", kept);
        }
        save(base, before, edited);

        JsonNode result = check(base);
        JsonNode found = issue(result, SET_CODE);
        assertThat(found.path("sectionId").asText()).isEqualTo(physics.path("id").asText());
        assertThat(found.path("subjectCode").asText()).isEqualTo("LY");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : result.path("paths")) outcome.put(path.path("choices").path(0).path("code").asText(), path.path("result").asText());
        assertThat(outcome).containsEntry("QD05", "VALID").containsEntry("QD01", "INVALID").containsEntry("QD03", "INVALID");
        assertThat(java.util.stream.StreamSupport.stream(result.path("paths").spliterator(), false)
                .filter(path -> "VALID".equals(path.path("result").asText()))
                .map(path -> path.path("issues").toString()).toList()).noneMatch(issues -> issues.contains(SET_CODE));
    }

    /** A template's first standard FULL exam of the kind, loaded; returns its URL. */
    private String loadFullTemplate(String kind) throws Exception {
        String code = jdbc.queryForObject("SELECT code FROM exam.exam_template WHERE kind_code = ? AND scope = 'FULL' AND active "
                + "AND build_mode = 'STANDARD' ORDER BY code LIMIT 1", String.class, kind);
        String base = create(kind, "FULL", null, null, null);
        JsonNode before = structure(base);
        json(post(base + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode().put("templateCode", code).put("confirmReplace", true)
                        .put("expectedRevision", before.path("revision").asLong()).toString()), 200);
        return base;
    }

    /** The first section of the subject inside {@code parts[].<array>[]}, with an option's sections read as {@code options}. */
    private static ObjectNode sectionIn(JsonNode structure, String subjectCode, String array) {
        for (JsonNode part : structure.path("parts")) {
            if ("options".equals(array)) {
                for (JsonNode option : part.path("options")) {
                    for (JsonNode section : option.path("sections")) {
                        if (subjectCode.equals(section.path("subjectCode").asText())) return (ObjectNode) section;
                    }
                }
            } else {
                for (JsonNode section : part.path(array)) {
                    if (subjectCode.equals(section.path("subjectCode").asText())) return (ObjectNode) section;
                }
            }
        }
        throw new AssertionError("no " + subjectCode + " section in " + array);
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
