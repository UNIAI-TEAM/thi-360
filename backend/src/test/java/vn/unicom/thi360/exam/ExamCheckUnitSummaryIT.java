package vn.unicom.thi360.exam;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamChecker;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCheckUnitSummaryIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ExamChecker checker;

    @Test
    void summaryEnforcesPermissionsAndReportsOverviewOverageWithoutChecking() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Summary','FULL','SHARED')", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'test')", id, """
                {"parts":[{"id":"p","name":"Math","questionCount":40,"sections":[
                  {"id":"s","subjectCode":"TOAN","questionCount":50,"blocks":[
                    {"id":"b","questionType":"SINGLE_CHOICE","questionCount":50,
                     "requiredPointsPerQuestion":1}]}]}]}
                """);
        String url = "/api/v1/admin/exams/" + id + "/structure/summary";
        clearInvocations(checker);
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].target").value(40))
                .andExpect(jsonPath("$.parts[0].allocated").value(50))
                .andExpect(jsonPath("$.parts[0].difference").value(10));
        verifyNoInteractions(checker);
    }
}
