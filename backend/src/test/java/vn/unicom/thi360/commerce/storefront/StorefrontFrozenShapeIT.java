package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;

/**
 * CM-1 R1-9: one real frozen publish shape, end to end. The REAL {@code PublishedExamPort} reads a snapshot written in
 * the shape the exam module freezes for an HSA FULL exam (P1 fixed with a set that has no {@code questionCount}; P2 with
 * a remainder block and an omitted essay; P3 science 3 of 5 or English) and the storefront serves it over HTTP, next to a
 * PART exam of the same kind. The counts are exact: what a learner answers, never what the template holds in all.
 */
class StorefrontFrozenShapeIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/public/storefront";

    /** Frozen parts of the HSA FULL shape; every part has a path entry for each of the two serving paths. */
    private static final String HSA_FULL_PARTS = """
            {"parts":[
             {"id":"p1","order":1,"name":"Toan hoc va xu li so lieu","durationMinutes":75,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"ALL"},"maxPoints":50,
              "sections":[{"id":"toan","subjectCode":"TOAN","questionCount":50,"blocks":[
                {"id":"t1","questionType":"SINGLE_CHOICE","questionCount":32},
                {"id":"t2","questionType":"SHORT_ANSWER","questionCount":15},
                {"id":"t3","questionType":"QUESTION_SET","setCount":1,"setSize":3}]}],
              "paths":[{"pathId":"o-sci","questionCount":50,"maxPoints":50,"durationMinutes":75},
                       {"pathId":"o-eng","questionCount":50,"maxPoints":50,"durationMinutes":75}]},
             {"id":"p2","order":2,"name":"Ngon ngu - Van hoc","durationMinutes":60,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"ALL"},"maxPoints":50,
              "sections":[{"id":"van","subjectCode":"VAN","questionCount":50,"blocks":[
                {"id":"v1","questionType":"SINGLE_CHOICE"},
                {"id":"v2","questionType":"QUESTION_SET","setCount":5,"setSize":5,"childAnswerType":"SINGLE_CHOICE"},
                {"id":"v3","questionType":"ESSAY","questionCount":1,"omitted":true}]}],
              "paths":[{"pathId":"o-sci","questionCount":50,"maxPoints":50,"durationMinutes":60},
                       {"pathId":"o-eng","questionCount":50,"maxPoints":50,"durationMinutes":60}]},
             {"id":"p3","order":3,"name":"Khoa hoc hoac Tieng Anh","durationMinutes":60,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"CHOICE","choose":1},"maxPoints":50,
              "options":[
               {"id":"o-sci","kind":"SUBJECT_CHOICE","code":"SCIENCE","name":"Khoa hoc","chooseSubjects":3,"countsByPick":[17,17,16],
                "sections":[
                 {"id":"sci-ly","subjectCode":"LY","questionCount":17,"blocks":[
                   {"id":"ly1","questionType":"SINGLE_CHOICE","questionCount":13},
                   {"id":"ly2","questionType":"QUESTION_SET","setCount":1,"setSize":3},
                   {"id":"ly3","questionType":"SHORT_ANSWER","questionCount":1}]},
                 {"id":"sci-hoa","subjectCode":"HOA","questionCount":17,"blocks":[
                   {"id":"hoa1","questionType":"SINGLE_CHOICE","questionCount":13},
                   {"id":"hoa2","questionType":"QUESTION_SET","setCount":1,"setSize":3},
                   {"id":"hoa3","questionType":"SHORT_ANSWER","questionCount":1}]},
                 {"id":"sci-sinh","subjectCode":"SINH","questionCount":17,"blocks":[
                   {"id":"sinh1","questionType":"SINGLE_CHOICE","questionCount":13},
                   {"id":"sinh2","questionType":"QUESTION_SET","setCount":1,"setSize":3},
                   {"id":"sinh3","questionType":"SHORT_ANSWER","questionCount":1}]},
                 {"id":"sci-su","subjectCode":"SU","questionCount":17,"blocks":[
                   {"id":"su1","questionType":"SINGLE_CHOICE","questionCount":14},
                   {"id":"su2","questionType":"QUESTION_SET","setCount":1,"setSize":3}]},
                 {"id":"sci-dia","subjectCode":"DIA","questionCount":17,"blocks":[
                   {"id":"dia1","questionType":"SINGLE_CHOICE","questionCount":14},
                   {"id":"dia2","questionType":"QUESTION_SET","setCount":1,"setSize":3}]}]},
               {"id":"o-eng","kind":"SUBJECT","code":"ANH","name":"Tieng Anh",
                "sections":[{"id":"eng","subjectCode":"ANH","questionCount":50,"blocks":[
                   {"id":"e1","questionType":"SINGLE_CHOICE","questionCount":10},
                   {"id":"e2","questionType":"SINGLE_CHOICE","questionCount":2},
                   {"id":"e3","questionType":"SINGLE_CHOICE","questionCount":2},
                   {"id":"e4","questionType":"SINGLE_CHOICE","questionCount":4},
                   {"id":"e5","questionType":"SINGLE_CHOICE","questionCount":4},
                   {"id":"e6","questionType":"SINGLE_CHOICE","questionCount":4},
                   {"id":"e7","questionType":"SINGLE_CHOICE","questionCount":4},
                   {"id":"e8","questionType":"QUESTION_SET","setCount":1,"setSize":5},
                   {"id":"e9","questionType":"QUESTION_SET","setCount":1,"setSize":5},
                   {"id":"e10","questionType":"QUESTION_SET","setCount":1,"setSize":5},
                   {"id":"e11","questionType":"SINGLE_CHOICE","questionCount":5}]}]},
               {"id":"o-unserved","kind":"SUBJECT","code":"VAN","name":"Khong phuc vu",
                "sections":[{"id":"unserved","subjectCode":"VAN","questionCount":50,"blocks":[
                   {"id":"u1","questionType":"ESSAY","questionCount":50}]}]}],
              "paths":[{"pathId":"o-sci","questionCount":50,"maxPoints":50,"durationMinutes":60},
                       {"pathId":"o-eng","questionCount":50,"maxPoints":50,"durationMinutes":60}]}]}
            """;

    private static final String HSA_PART_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Toan hoc va xu li so lieu","durationMinutes":75,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"ALL"},"maxPoints":50,
              "sections":[{"id":"toan","subjectCode":"TOAN","questionCount":50,"blocks":[
                {"id":"t1","questionType":"SINGLE_CHOICE","questionCount":35},
                {"id":"t2","questionType":"SHORT_ANSWER","questionCount":15}]}],
              "paths":[{"pathId":"default","questionCount":50,"maxPoints":50,"durationMinutes":75}]}]}
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;
    @Autowired StorefrontProjectionService projection;

    private final List<UUID> examIds = new ArrayList<>();
    private final String tag = "fz" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @AfterEach
    void clean() {
        for (UUID id : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", id);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", id);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", id);
        }
    }

    @Test
    void frozenHsaFullShapeIsServedWithExactCountsAndAPartOnlyFacet() throws Exception {
        UUID full = exam("HSA", "FULL", null, HSA_FULL_PARTS, "{o-sci,o-eng}");
        UUID part = exam("HSA", "PART", "TOAN", HSA_PART_PARTS, "{default}");
        projection.project(full);
        projection.project(part);
        String fullCode = codeOf(full);
        String partCodeOfExam = codeOf(part);
        String ip = "198.18." + (int) (Math.random() * 250 + 1) + ".9";

        JsonNode detail = json(publicGet(BASE + "/products/" + fullCode, ip));
        JsonNode parts = detail.path("parts");
        assertThat(parts).hasSize(3);
        // P1: the fixed set without questionCount is setCount * setSize = 3.
        assertThat(rows(parts.get(0).path("answerTypes")))
                .containsExactly("SINGLE_CHOICE:32", "SHORT_ANSWER:15", "QUESTION_SET:3");
        // P2: the remainder block takes 50 - 25 (the set) = 25; the omitted essay is not an answer type.
        assertThat(rows(parts.get(1).path("answerTypes"))).containsExactly("SINGLE_CHOICE:25", "QUESTION_SET:25:SINGLE_CHOICE");
        // P3: nothing is fixed; a learner does ONE 50-question path.
        assertThat(rows(parts.get(2).path("answerTypes"))).isEmpty();
        assertThat(parts.get(2).path("questionCount").path("min").asInt()).isEqualTo(50);
        assertThat(parts.get(2).path("questionCount").path("max").asInt()).isEqualTo(50);

        JsonNode options = detail.path("choices").get(0).path("options");
        assertThat(detail.path("choices")).hasSize(1);
        assertThat(options).as("science and English; the unserved option adds nothing").hasSize(2);
        JsonNode science = options.get(0);
        assertThat(science.path("code").asText()).isEqualTo("SCIENCE");
        assertThat(science.path("subjectAnswerTypes")).hasSize(5);
        List<String> perSubject = new ArrayList<>();
        science.path("subjectAnswerTypes").forEach(s -> perSubject.add(s.path("subject").path("code").asText() + "="
                + String.join("+", rows(s.path("answerTypes")))));
        assertThat(perSubject).containsExactly(
                "LY=SINGLE_CHOICE:13+QUESTION_SET:3+SHORT_ANSWER:1", "HOA=SINGLE_CHOICE:13+QUESTION_SET:3+SHORT_ANSWER:1",
                "SINH=SINGLE_CHOICE:13+QUESTION_SET:3+SHORT_ANSWER:1", "SU=SINGLE_CHOICE:14+QUESTION_SET:3",
                "DIA=SINGLE_CHOICE:14+QUESTION_SET:3");
        JsonNode english = options.get(1);
        assertThat(english.path("code").asText()).isEqualTo("ANH");
        assertThat(english.path("subjectAnswerTypes")).hasSize(1);
        assertThat(rows(english.path("subjectAnswerTypes").get(0).path("answerTypes")))
                .as("English alone is the 50 questions of its path").containsExactly("SINGLE_CHOICE:35", "QUESTION_SET:15");
        assertThat(detail.toString()).as("no essay: omitted in P2, unserved in P3").doesNotContain("ESSAY");

        // The card says what the paths total: three parts of 50.
        JsonNode card = json(publicGet(BASE + "/products?q=" + fullCode, ip)).path("items").get(0);
        assertThat(card.path("questionCount").path("min").asInt()).isEqualTo(150);
        assertThat(card.path("questionCount").path("max").asInt()).isEqualTo(150);

        // The Phần facet: PART exams only. The FULL exam holds Toan hoc, but is found through its format.
        JsonNode facet = json(publicGet(BASE + "/filters", ip)).path("parts");
        JsonNode toan = null;
        List<String> keys = new ArrayList<>();
        for (JsonNode entry : facet) {
            keys.add(entry.path("kindCode").asText() + ":" + entry.path("code").asText());
            if (entry.path("kindCode").asText().equals("HSA") && entry.path("code").asText().equals("TOAN")) {
                toan = entry;
            }
        }
        assertThat(toan).as("the TOAN PART exam is in the facet").isNotNull();
        long onSaleToanParts = jdbc.queryForObject("SELECT count(*) FROM commerce.storefront_product WHERE kind_code = 'HSA' "
                + "AND sale_state = 'ON_SALE' AND scope = 'PART' AND part_codes @> ARRAY['TOAN']::text[]", Long.class);
        assertThat(toan.path("count").asLong()).isEqualTo(onSaleToanParts);
        assertThat(keys).as("the FULL exam adds no P1/P2/P3").doesNotContain("HSA:P1", "HSA:P2", "HSA:P3");
        assertThat(codes(json(publicGet(BASE + "/products?kind=HSA&part=TOAN&q=" + tag, ip)))).containsExactly(partCodeOfExam);
        assertThat(codes(json(publicGet(BASE + "/products?kind=HSA&format=FULL&part=TOAN&q=" + tag, ip)))).isEmpty();
        assertThat(codes(json(publicGet(BASE + "/products?kind=HSA&format=FULL&q=" + tag, ip)))).containsExactly(fullCode);
    }

    // ----------------------------------------------------------------- helpers

    private static List<String> rows(JsonNode types) {
        List<String> rows = new ArrayList<>();
        types.forEach(t -> rows.add(t.path("questionType").asText() + ":" + t.path("questionCount").asInt()
                + (t.path("childAnswerType").isNull() || t.path("childAnswerType").isMissingNode() ? ""
                        : ":" + t.path("childAnswerType").asText())));
        return rows;
    }

    private static List<String> codes(JsonNode page) {
        List<String> codes = new ArrayList<>();
        page.path("items").forEach(item -> codes.add(item.path("code").asText()));
        return codes;
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    private String publicGet(String path, String ip) throws Exception {
        return mockMvc.perform(get(path).header("X-Forwarded-For", ip)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private String codeOf(UUID examId) {
        return jdbc.queryForObject("SELECT code FROM exam.exam WHERE id = ?", String.class, examId);
    }

    /** An exam of the exam module with one serving version holding the frozen parts. */
    private UUID exam(String kindCode, String scope, String partCode, String parts, String servingPaths) throws Exception {
        UUID id = UUID.randomUUID();
        examIds.add(id);
        String code = (tag + "-" + examIds.size()).toUpperCase();
        UUID kind = catalog.findActiveItem("EXAM", kindCode).orElseThrow().id();
        String name = "Bai " + kindCode + " " + scope + " " + tag;
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, part_code, timing, "
                + "status, serving_version, build_mode) VALUES (?, ?, ?, ?, ?, ?, ?, 'PER_PART', 'PUBLISHED', 1, "
                + "'STANDARD')", id, code, code.toLowerCase(), name, kind, scope, partCode);
        var config = mapper.createObjectNode().put("code", code).put("name", name).put("kindCode", kindCode)
                .put("scope", scope).put("timing", "PER_PART").put("maxScore", 150).put("practiceScoring", "LINEAR")
                .put("buildMode", "STANDARD");
        config.put("kindItemId", kind.toString());
        if (partCode != null) {
            config.put("partCode", partCode);
        }
        config.putObject("listing").put("summary", "Tom tat").put("objectives", "Muc tieu").put("audience", "Doi tuong")
                .put("listPriceVnd", 150_000).put("attemptsGranted", 3).put("benefitAiExplanation", true)
                .put("benefitStrengthWeakness", false).put("benefitPractice", true).put("resultView", "SCORE_AND_ANSWERS");
        config.putArray("combinations");
        config.putArray("omittedGroups");
        jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, "
                        + "serving_path_ids, check_run_id, state, status, published_at, published_by) VALUES (?, ?, 1, "
                        + "?::jsonb, ?::jsonb, ?::text[], ?::text[], ?, 'SERVING', 'PUBLISHED', ?, ?)",
                UUID.randomUUID(), id, config.toString(), parts, servingPaths, servingPaths, UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")), UUID.randomUUID());
        return id;
    }
}
