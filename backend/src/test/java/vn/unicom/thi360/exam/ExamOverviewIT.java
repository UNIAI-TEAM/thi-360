package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamOverviewIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void updatesByIdWithoutReplacingBlocksAndRejectsStaleRevision() throws Exception {
        String url = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
        var before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var part = before.path("parts").get(0);
        var blocks = part.path("sections").get(0).path("blocks");
        String body = mapper.writeValueAsString(Map.of("expectedRevision", before.path("revision").asLong(),
                "overview", List.of(Map.of("partId", part.path("id").asText(), "questionCount", 40))));
        var after = mapper.readTree(mockMvc.perform(put(url + "/overview").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("parts").get(0).path("questionCount").asInt()).isEqualTo(40);
        assertThat(after.path("parts").get(0).path("sections").get(0).path("questionCount").asInt()).isEqualTo(40);
        assertThat(after.path("parts").get(0).path("sections").get(0).path("blocks")).isEqualTo(blocks);
        mockMvc.perform(put(url + "/overview").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isPreconditionFailed());
        String second = mapper.writeValueAsString(Map.of("expectedRevision", after.path("revision").asLong(),
                "overview", List.of(Map.of("partId", part.path("id").asText(), "questionCount", 50))));
        var restored = mapper.readTree(mockMvc.perform(put(url + "/overview").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(restored.path("parts").get(0).path("questionCount").asInt()).isEqualTo(50);
        assertThat(restored.path("parts").get(0).path("sections").get(0).path("blocks")).isEqualTo(blocks);
    }

    @Test
    void createsSelectedSubjectPartAndRequiresScoringConfirmation() throws Exception {
        jdbc.update("""
                UPDATE exam.exam_template SET structure = CAST(? AS jsonb)
                WHERE code = 'SPT_DIA_2025'
                """, """
                {"parts":[
                  {"id":"part-one","order":1,"name":"I","questionCount":16,"maxPoints":4,
                   "sections":[{"id":"section-one","subjectCode":"DIA","questionCount":16,"blocks":[]}]},
                  {"id":"part-two","order":2,"name":"II","questionCount":4,"maxPoints":4,
                   "sections":[{"id":"section-two","subjectCode":"DIA","questionCount":4,
                   "blocks":[{"id":"block-two","name":"II","questionCount":4}]}]}
                ]}
                """);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String partExamCode = "SPT-P-" + suffix;
        String body = """
                {"kind":"SPT","scope":"PART","partCode":"P2","subjectCode":"DIA",
                 "code":"%s","name":"Part test"}
                """.formatted(partExamCode);
        var created = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.practiceScoring").value("RAW"))
                .andExpect(jsonPath("$.maxScore").value(4))
                .andExpect(jsonPath("$.parts[0].id").value("part-two"))
                .andExpect(jsonPath("$.parts[0].sections[0].blocks[0].id").value("block-two"))
                .andReturn().getResponse().getContentAsString());
        String url = "/api/v1/admin/exams/" + created.path("id").asText();
        String revision = created.path("revision").asText();
        String linear = "{\"expectedRevision\":" + revision + ",\"practiceScoring\":\"LINEAR\"}";
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(linear))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCORING_CONFIRM_REQUIRED"));
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(linear.replace("}", ",\"confirmScoring\":true}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referenceMax").value(10));
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("P2", "P9").replace(partExamCode, "SPT-M-" + suffix)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EXAM_PART_NOT_FOUND"));
        String subjectBody = body.replace("\"scope\":\"PART\",\"partCode\":\"P2\"",
                        "\"scope\":\"SUBJECT\"").replace(partExamCode, "SPT-S-" + suffix);
        var subject = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(subjectBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String change = "{\"scope\":\"PART\",\"partCode\":\"P2\",\"expectedRevision\":"
                + subject.path("revision").asLong() + "}";
        mockMvc.perform(patch("/api/v1/admin/exams/" + subject.path("id").asText())
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxScore").value(4))
                .andExpect(jsonPath("$.parts[0].id").value("part-two"))
                .andExpect(jsonPath("$.practiceScoring").value("RAW"));
    }

    @Test
    void templateVersionRequiresConfirmationAndKeepsEditedCounts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String templateCode = "HSA_VER_" + suffix;
        jdbc.update("""
                UPDATE exam.exam_template SET structure = jsonb_set(
                  jsonb_set(
                    jsonb_set(structure, '{parts,0,id}', '"old-part"'::jsonb),
                    '{parts,0,sections,0,id}', '"old-section"'::jsonb),
                  '{parts,0,order}', '1'::jsonb)
                WHERE code = 'HSA_FULL'
                """);
        jdbc.update("""
                INSERT INTO exam.exam_template
                  (code, kind_code, scope, timing, duration_minutes, max_score, structure, active, build_mode, is_default)
                SELECT ?, kind_code, scope, timing, duration_minutes, max_score,
                  jsonb_set(
                    jsonb_set(structure, '{parts,0,id}', '"new-part"'::jsonb),
                    '{parts,0,sections,0,id}', '"new-section"'::jsonb), true, build_mode, false
                FROM exam.exam_template WHERE code = 'HSA_FULL'
                """, templateCode);
        // UUIDs reused for different rows must not override the cross-version business keys.
        jdbc.update("""
                UPDATE exam.exam_template
                SET structure = jsonb_set(
                  jsonb_set(structure, '{parts,1,id}', '"old-part"'::jsonb),
                  '{parts,0,sections}', structure #> '{parts,0,sections}'
                    || '[{"id":"old-section","subjectCode":"VAN","questionCount":7,"blocks":[]}]'::jsonb)
                WHERE code = ?
                """, templateCode);
        String create = """
                {"kind":"HSA","scope":"FULL","code":"HSA-V-%s","name":"Version test",
                 "overview":[{"partId":"old-part","questionCount":40}]}
                """.formatted(suffix);
        var created = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parts[0].questionCount").value(40))
                .andReturn().getResponse().getContentAsString());
        String examUrl = "/api/v1/admin/exams/" + created.path("id").asText();
        String url = examUrl + "/structure/template";
        String partId = created.path("parts").get(0).path("id").asText();
        String sectionId = created.path("parts").get(0).path("sections").get(0).path("id").asText();
        var edited = mapper.readTree(mockMvc.perform(put(examUrl + "/overview")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "expectedRevision", created.path("revision").asLong(),
                                "overview", List.of(Map.of("partId", partId,
                                        "sectionId", sectionId, "questionCount", 30))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String revision = edited.path("revision").asText();
        String load = "{\"templateCode\":\"" + templateCode + "\",\"expectedRevision\":" + revision + "}";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(load))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(load))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(load))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_REPLACE_CONFIRM"));
        String confirmed = load.replace("}", ",\"confirmReplace\":true}");
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(confirmed))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_OVERVIEW_CONFIRM"));
        var kept = mapper.readTree(mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmed.replace("}", ",\"overviewPolicy\":\"KEEP_EDITED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].id").value("new-part"))
                .andExpect(jsonPath("$.parts[0].questionCount").value(40))
                .andExpect(jsonPath("$.parts[0].sections[0].id").value("new-section"))
                .andExpect(jsonPath("$.parts[0].sections[0].questionCount").value(30))
                .andReturn().getResponse().getContentAsString());
        String useTemplate = "{\"templateCode\":\"" + templateCode + "\",\"confirmReplace\":true,"
                + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":" + kept.path("revision").asLong() + "}";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(useTemplate))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parts[0].questionCount").value(50))
                .andExpect(jsonPath("$.parts[0].sections[0].questionCount").value(50));
    }

    @Test
    void createsSubjectFromDefaultAndReviewTemplate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String templateCode = "QDA_SUBJECT_HOA_" + suffix;
        jdbc.update("UPDATE exam.exam_template SET is_default=false WHERE kind_code='QDA' "
                + "AND scope='SUBJECT' AND subject_code='HOA' AND build_mode='STANDARD'");
        jdbc.update("""
                INSERT INTO exam.exam_template
                  (code, kind_code, scope, subject_code, timing, structure, active, build_mode, is_default)
                VALUES (?, 'QDA', 'SUBJECT', 'HOA', 'PER_PART',
                  '{"parts":[{"id":"chem-part","order":1,"name":"Chemistry","questionCount":25,
                    "sections":[{"id":"chem-section","subjectCode":"HOA","questionCount":25,"blocks":[]}]}]}'::jsonb,
                  true, 'STANDARD', true)
                """, templateCode);
        String create = "{\"kind\":\"QDA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"HOA\","
                + "\"code\":\"QDA-S-" + suffix + "\",\"name\":\"Chemistry\"}";
        mockMvc.perform(post("/api/v1/admin/exams").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateCode").value(templateCode))
                .andExpect(jsonPath("$.parts[0].sections[0].questionCount").value(25));
        String review = create.replace("QDA-S-", "QDA-R-").replace("}", ",\"buildMode\":\"REVIEW\"}");
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.buildMode").value("REVIEW"))
                .andExpect(jsonPath("$.templateCode").value("QDA_REVIEW"))
                .andExpect(jsonPath("$.parts[0].sections[0].subjectCode").value("HOA"));
    }

    @Test
    void rowIdBackfillIsIdempotentAndPreservesExistingValues() throws Exception {
        String code = "IDS_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO exam.exam_template
                  (code,kind_code,scope,timing,structure,active,build_mode,is_default)
                VALUES (?, 'HSA', 'FULL', 'PER_PART',
                  '{"parts":[{"id":null,"sections":[{"id":null},{"id":""}]}]}'::jsonb,
                  true, 'STANDARD', false)
                """, code);
        String sql = Files.readString(Path.of("src/main/resources/db/migration/exam/"
                + "V202611180455__exam_template_row_ids.sql"));
        jdbc.execute(sql);
        String first = jdbc.queryForObject("SELECT structure::text FROM exam.exam_template WHERE code=?", String.class, code);
        var structure = mapper.readTree(first);
        UUID.fromString(structure.path("parts").get(0).path("id").asText());
        UUID.fromString(structure.path("parts").get(0).path("sections").get(0).path("id").asText());
        assertThat(structure.path("parts").get(0).path("sections").get(1).path("id").asText()).isEmpty();
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("SELECT structure::text FROM exam.exam_template WHERE code=?", String.class, code))
                .isEqualTo(first);
    }
}
