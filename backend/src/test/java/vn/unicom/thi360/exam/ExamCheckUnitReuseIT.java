package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckUnitEngine;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCheckUnitReuseIT extends AbstractIntegrationTest {
    private static final UUID PHYSICS = UUID.fromString("00000000-0000-7000-8200-000000000204");
    private static final UUID CHEMISTRY = UUID.fromString("00000000-0000-7000-8200-000000000205");
    private static final UUID BIOLOGY = UUID.fromString("00000000-0000-7000-8200-000000000206");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private ExamCheckUnitEngine units;

    @Test
    void onlyChangedSubjectIsSolvedAgain() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Unit reuse','FULL','SHARED',3)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":3,"sections":[
                  {"id":"physics","subjectCode":"LY","questionCount":1,"blocks":[
                   {"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"chemistry","subjectCode":"HOA","questionCount":1,"blocks":[
                   {"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"biology","subjectCode":"SINH","questionCount":1,"blocks":[
                   {"id":"bb","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "bp", "p", "physics", PHYSICS);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "bc", "p", "chemistry", CHEMISTRY);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "bb", "p", "biology", BIOLOGY);
        String url = "/api/v1/admin/exams/" + id + "/check";
        String structureUrl = "/api/v1/admin/exams/" + id + "/structure";
        mockMvc.perform(post(url).header("Origin", "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"SECTION\",\"sectionId\":\"physics\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"SECTION\",\"sectionId\":\"physics\"}"))
                .andExpect(status().isForbidden());
        assertStates(structureUrl, "NEEDS_CHECK", "NEEDS_CHECK", "NEEDS_CHECK");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(3)).solveUnit(any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_unit WHERE exam_id=?", Integer.class, id)).isEqualTo(3);
        assertStates(structureUrl, "CHECKED", "CHECKED", "CHECKED");
        assertStates(url, "CHECKED", "CHECKED", "CHECKED");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        jdbc.update("UPDATE exam.exam_structure SET parts=jsonb_set(parts,'{parts,0,sections,0,blocks,0,requiredPointsPerQuestion}', '1'::jsonb), structure_hash='second' WHERE exam_id=?", id);
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", id);
        entities.clear();
        assertStates(structureUrl, "NEEDS_CHECK", "CHECKED", "CHECKED");
        assertStates(url, "NEEDS_CHECK", "CHECKED", "CHECKED");
        UUID previousRun = jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id=?", UUID.class, id);
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"physics\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id=?", UUID.class, id)).isEqualTo(previousRun);
        assertStates(structureUrl, "CHECKED", "CHECKED", "CHECKED");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partId\":\"p\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        UUID questionId = jdbc.queryForObject("SELECT id FROM questionbank.question WHERE code='SEED-SINH-G10-NB-001'", UUID.class);
        long revision = jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id=?", Long.class, questionId);
        mockMvc.perform(post("/api/v1/admin/questions/" + questionId + "/retire")
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        entities.clear();
        assertStates(structureUrl, "CHECKED", "CHECKED", "NEEDS_CHECK");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
    }

    @Test
    void mathSectionsAcrossThreePartsShareOneUnit() throws Exception {
        UUID id = UUID.randomUUID();
        UUID math = UUID.fromString("00000000-0000-7000-8200-000000000201");
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Three-part math','FULL','SHARED',3)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[
                  {"id":"i","order":1,"name":"I","questionCount":1,"sections":[{"id":"math-i","subjectCode":"TOAN","questionCount":1,"blocks":[{"id":"bi","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                  {"id":"ii","order":2,"name":"II","questionCount":1,"sections":[{"id":"math-ii","subjectCode":"TOAN","questionCount":1,"blocks":[{"id":"bii","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                  {"id":"iii","order":3,"name":"III","questionCount":1,"sections":[{"id":"math-iii","subjectCode":"TOAN","questionCount":1,"blocks":[{"id":"biii","questionType":"SINGLE_CHOICE","questionCount":1}]}]}
                ]}
                """);
        for (String[] ref : new String[][] {{"i", "math-i", "bi"}, {"ii", "math-ii", "bii"}, {"iii", "math-iii", "biii"}}) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), id, ref[2], ref[0], ref[1], math);
        }
        String check = "/api/v1/admin/exams/" + id + "/check";
        clearInvocations(units);
        mockMvc.perform(post(check).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT cardinality(section_ids) FROM exam.exam_check_unit WHERE exam_id=?",
                Integer.class, id)).isEqualTo(3);
        jdbc.update("UPDATE exam.exam_structure SET parts=jsonb_set(parts,'{parts,1,sections,0,blocks,0,requiredPointsPerQuestion}', '1'::jsonb), structure_hash='second' WHERE exam_id=?", id);
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", id);
        entities.clear();
        var before = mockMvc.perform(get("/api/v1/admin/exams/" + id + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var state : mapper.readTree(before).path("sectionStates")) {
            assertThat(state.path("state").asText()).isEqualTo("NEEDS_CHECK");
        }
        clearInvocations(units);
        mockMvc.perform(post(check).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partId\":\"ii\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
        var after = mockMvc.perform(get("/api/v1/admin/exams/" + id + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var state : mapper.readTree(after).path("sectionStates")) {
            assertThat(state.path("state").asText()).isEqualTo("CHECKED");
        }
    }

    @Test
    void codesBecomeStaleOnlyWhenTheirSubjectChanges() throws Exception {
        UUID id = UUID.randomUUID();
        UUID qda = UUID.fromString("00000000-0000-7000-8200-000000000102");
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,kind_item_id,scope,timing,question_count) "
                + "VALUES (?,?,?,'Codes',?,'FULL','SHARED',2)", id, id.toString(), id.toString(), qda);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"codes","name":"Codes","codeSource":{"source":"GLOBAL_LIST"},
                  "subjectPool":[
                   {"id":"ly","subjectCode":"LY","questionCount":1,"blocks":[{"id":"bly","questionType":"SINGLE_CHOICE","questionCount":1}]},
                   {"id":"hoa","subjectCode":"HOA","questionCount":1,"blocks":[{"id":"bhoa","questionType":"SINGLE_CHOICE","questionCount":1}]},
                   {"id":"sinh","subjectCode":"SINH","questionCount":1,"blocks":[{"id":"bsinh","questionType":"SINGLE_CHOICE","questionCount":1}]},
                   {"id":"su","subjectCode":"SU","questionCount":1,"blocks":[{"id":"bsu","questionType":"SINGLE_CHOICE","questionCount":1}]},
                   {"id":"dia","subjectCode":"DIA","questionCount":1,"blocks":[{"id":"bdia","questionType":"SINGLE_CHOICE","questionCount":1}]},
                   {"id":"anh","subjectCode":"ANH","questionCount":2,"blocks":[{"id":"banh","questionType":"SINGLE_CHOICE","questionCount":2}]}
                  ]}]}
                """);
        for (String[] ref : new String[][] {{"ly", "204"}, {"hoa", "205"}, {"sinh", "206"},
                {"su", "207"}, {"dia", "208"}, {"anh", "203"}}) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), id, "b" + ref[0], "codes", ref[0],
                    UUID.fromString("00000000-0000-7000-8200-000000000" + ref[1]));
        }
        String url = "/api/v1/admin/exams/" + id;
        clearInvocations(units);
        mockMvc.perform(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(6)).solveUnit(any(), any(), any());
        jdbc.update("UPDATE exam.exam_structure SET parts=jsonb_set(parts,'{parts,0,subjectPool,0,blocks,0,requiredPointsPerQuestion}', '1'::jsonb), structure_hash='second' WHERE exam_id=?", id);
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", id);
        entities.clear();
        var structure = mapper.readTree(mockMvc.perform(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var staleSubjects = new java.util.HashSet<String>();
        for (var state : structure.path("sectionStates")) {
            if ("NEEDS_CHECK".equals(state.path("state").asText())) staleSubjects.add(state.path("subjectCode").asText());
        }
        assertThat(staleSubjects).containsExactly("LY");
        var staleCodes = new java.util.HashSet<String>();
        for (var code : structure.path("combinations")) {
            for (var subject : code.path("subjectCodes")) {
                if (staleSubjects.contains(subject.asText())) staleCodes.add(code.path("code").asText());
            }
        }
        assertThat(staleCodes).containsExactlyInAnyOrder("QD01", "QD03");
        clearInvocations(units);
        mockMvc.perform(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
        UUID biologyQuestion = jdbc.queryForObject("SELECT id FROM questionbank.question WHERE code='SEED-SINH-G10-NB-001'", UUID.class);
        long biologyRevision = jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id=?", Long.class, biologyQuestion);
        mockMvc.perform(post("/api/v1/admin/questions/" + biologyQuestion + "/retire")
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + biologyRevision + "}"))
                .andExpect(status().isOk());
        entities.clear();
        structure = mapper.readTree(mockMvc.perform(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        staleSubjects.clear();
        for (var state : structure.path("sectionStates")) {
            if ("NEEDS_CHECK".equals(state.path("state").asText())) staleSubjects.add(state.path("subjectCode").asText());
        }
        assertThat(staleSubjects).containsExactly("SINH");
        staleCodes.clear();
        for (var code : structure.path("combinations")) {
            for (var subject : code.path("subjectCodes")) {
                if (staleSubjects.contains(subject.asText())) staleCodes.add(code.path("code").asText());
            }
        }
        assertThat(staleCodes).containsExactlyInAnyOrder("QD02", "QD03");
    }

    @Test
    void templateReloadInvalidatesSavedUnitsAndRejectsStaleRevision() throws Exception {
        UUID id = UUID.randomUUID();
        UUID hsa = UUID.fromString("00000000-0000-7000-8200-000000000101");
        UUID math = UUID.fromString("00000000-0000-7000-8200-000000000201");
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,kind_item_id,scope,timing,question_count) "
                + "VALUES (?,?,?,'Reload',?,'FULL','SHARED',1)",
                id, id.toString(), id.toString(), hsa);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":1,"sections":[
                  {"id":"old-math","subjectCode":"TOAN","questionCount":1,"blocks":[
                    {"id":"old-block","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "old-block", "p", "old-math", math);
        String url = "/api/v1/admin/exams/" + id;
        mockMvc.perform(post(url + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        var before = mapper.readTree(mockMvc.perform(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(before.path("sectionStates").get(0).path("state").asText()).isEqualTo("CHECKED");
        long revision = before.path("revision").asLong();
        String request = "{\"templateCode\":\"HSA_FULL\",\"confirmReplace\":true,\"expectedRevision\":";
        mockMvc.perform(post(url + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request + (revision + 1) + "}"))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(post(url + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request + revision + "}"))
                .andExpect(status().isOk());
        entities.clear();
        var after = mapper.readTree(mockMvc.perform(get(url + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("sectionStates")).isNotEmpty();
        for (var state : after.path("sectionStates")) {
            assertThat(state.path("state").asText()).isEqualTo("NEEDS_CHECK");
        }
    }

    @Test
    void checkingEmptyPartReturnsLocatedIssueWithoutWholeExamRun() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Empty part','FULL','SHARED',1)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')",
                id, "{\"parts\":[{\"id\":\"empty\",\"name\":\"Empty\",\"questionCount\":1}]}");
        var result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partId\":\"empty\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"))
                .andReturn().getResponse().getContentAsString());
        var structure = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "PART_EMPTY".equals(issue.path("code").asText())).toList();
        assertThat(structure).singleElement().satisfies(issue ->
                assertThat(issue.path("location").path("partId").asText()).isEqualTo("empty"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id=?",
                Integer.class, id)).isZero();
    }

    @Test
    void invalidScopedSectionPersistsItsJointUnit() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Incomplete unit','FULL','SHARED',2)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                  {"id":"a","subjectCode":"LY","questionCount":1,"blocks":[
                    {"id":"ba","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"b","subjectCode":"LY","questionCount":1,"blocks":[
                    {"id":"bb","questionType":"SINGLE_CHOICE","questionCount":1,
                     "childConditions":[{"to":1}]}]}]}]}
                """);
        String base = "/api/v1/admin/exams/" + id;
        mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"a\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"));
        assertThat(jdbc.queryForObject("SELECT outcome FROM exam.exam_check_unit WHERE exam_id=?",
                String.class, id)).isEqualTo("INVALID");
        assertThat(jdbc.queryForObject("SELECT cardinality(section_ids) FROM exam.exam_check_unit WHERE exam_id=?",
                Integer.class, id)).isEqualTo(2);
        var result = mapper.readTree(mockMvc.perform(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("sectionStates")).allSatisfy(state ->
                assertThat(state.path("state").asText()).isEqualTo("INCOMPLETE"));
    }

    @Test
    void malformedMutuallyExclusiveOptionDoesNotInvalidateCheckedAlternative() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Exclusive options','FULL','SHARED',1)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":1,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"good","kind":"SUBJECT","code":"LY","sections":[
                      {"id":"good-section","subjectCode":"LY","questionCount":1,"blocks":[
                        {"id":"good-block","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                    {"id":"bad","kind":"SUBJECT_CHOICE","code":"LY","sections":[
                      {"id":"bad-section","subjectCode":"LY","questionCount":1,"blocks":[
                        {"id":"bad-block","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]}
                """);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "good-block", "p", "good-section", PHYSICS);
        String base = "/api/v1/admin/exams/" + id;
        mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"good-section\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"bad-section\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"));
        var result = mapper.readTree(mockMvc.perform(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("sectionStates").get(0).path("state").asText()).isEqualTo("CHECKED");
        assertThat(result.path("sectionStates").get(1).path("state").asText()).isEqualTo("INCOMPLETE");
    }

    private void assertStates(String url, String physics, String chemistry, String biology) throws Exception {
        String body = mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var states = mapper.readTree(body).path("sectionStates");
        assertThat(states).hasSize(3);
        assertThat(states.get(0).path("sectionId").asText()).isEqualTo("physics");
        assertThat(states.get(0).path("state").asText()).isEqualTo(physics);
        assertThat(states.get(1).path("sectionId").asText()).isEqualTo("chemistry");
        assertThat(states.get(1).path("state").asText()).isEqualTo(chemistry);
        assertThat(states.get(2).path("sectionId").asText()).isEqualTo("biology");
        assertThat(states.get(2).path("state").asText()).isEqualTo(biology);
    }
}
