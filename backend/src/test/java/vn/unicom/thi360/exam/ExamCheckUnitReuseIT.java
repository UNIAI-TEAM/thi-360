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
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private ExamCheckUnitEngine units;

    @Test
    void onlyChangedSubjectIsSolvedAgain() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Unit reuse','FULL','SHARED')", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                  {"id":"physics","subjectCode":"LY","questionCount":1,"blocks":[
                   {"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"chemistry","subjectCode":"HOA","questionCount":1,"blocks":[
                   {"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "bp", "p", "physics", PHYSICS);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "bc", "p", "chemistry", CHEMISTRY);
        String url = "/api/v1/admin/exams/" + id + "/check";
        String structureUrl = "/api/v1/admin/exams/" + id + "/structure";
        assertStates(structureUrl, "NEEDS_CHECK", "NEEDS_CHECK");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(2)).solveUnit(any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_unit WHERE exam_id=?", Integer.class, id)).isEqualTo(2);
        assertStates(structureUrl, "CHECKED", "CHECKED");
        assertStates(url, "CHECKED", "CHECKED");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        jdbc.update("UPDATE exam.exam_structure SET parts=jsonb_set(parts,'{parts,0,sections,0,blocks,0,requiredPointsPerQuestion}', '1'::jsonb), structure_hash='second' WHERE exam_id=?", id);
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", id);
        entities.clear();
        assertStates(structureUrl, "NEEDS_CHECK", "CHECKED");
        assertStates(url, "NEEDS_CHECK", "CHECKED");
        UUID previousRun = jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id=?", UUID.class, id);
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"physics\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id=?", UUID.class, id)).isEqualTo(previousRun);
        assertStates(structureUrl, "CHECKED", "CHECKED");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partId\":\"p\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(0)).solveUnit(any(), any(), any());
        jdbc.update("INSERT INTO questionbank.question_source_revision(subject_item_id,revision) VALUES (?,1) "
                + "ON CONFLICT (subject_item_id) DO UPDATE SET revision=questionbank.question_source_revision.revision+1", CHEMISTRY);
        assertStates(structureUrl, "CHECKED", "NEEDS_CHECK");
        clearInvocations(units);
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
        verify(units, times(1)).solveUnit(any(), any(), any());
    }

    private void assertStates(String url, String physics, String chemistry) throws Exception {
        String body = mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var states = mapper.readTree(body).path("sectionStates");
        assertThat(states).hasSize(2);
        assertThat(states.get(0).path("sectionId").asText()).isEqualTo("physics");
        assertThat(states.get(0).path("state").asText()).isEqualTo(physics);
        assertThat(states.get(1).path("sectionId").asText()).isEqualTo("chemistry");
        assertThat(states.get(1).path("state").asText()).isEqualTo(chemistry);
    }
}
