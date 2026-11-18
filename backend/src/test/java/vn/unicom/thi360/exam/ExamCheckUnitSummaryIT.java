package vn.unicom.thi360.exam;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

    @Test
    void freshReviewExamWithRemainderGroupReportsSectionCountForGroup() throws Exception {
        UUID id = insertExam("""
                {"parts":[{"id":"p","name":"Review","questionCount":10,"sections":[
                  {"id":"s","subjectCode":"TOAN","questionCount":10,"blocks":[
                    {"id":"b","questionType":"SINGLE_CHOICE","questionCount":null,
                     "requiredPointsPerQuestion":1}]}]}]}
                """);
        mockMvc.perform(get(summaryUrl(id)).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].allocated").value(10))
                .andExpect(jsonPath("$.parts[0].children[0].allocated").value(10))
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").value(10))
                .andExpect(jsonPath("$.parts[0].children[0].children[0].maxPoints").value(10));
    }

    @Test
    void createdVactReviewWithTenQuestionOverviewReportsTenForGroupAndSection() throws Exception {
        // Real VACT_REVIEW template: one untyped block, no questionCount and no rate, ids fixed by the migration.
        String body = "{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"buildMode\":\"REVIEW\","
                + "\"durationMinutes\":45,\"name\":\"Review\",\"code\":\"T-"
                + UUID.randomUUID().toString().substring(0, 8) + "\","
                + "\"overview\":[{\"partId\":\"e29ab2f3-6070-56e3-bbfd-933ee323d888\",\"questionCount\":10},"
                + "{\"partId\":\"e29ab2f3-6070-56e3-bbfd-933ee323d888\","
                + "\"sectionId\":\"029f5eaa-06f3-55df-bfbb-10ed773d1c26\",\"questionCount\":10}]}";
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateCode").value("VACT_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mockMvc.perform(get(summaryUrl(UUID.fromString(id))).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].allocated").value(10))
                .andExpect(jsonPath("$.parts[0].children[0].allocated").value(10))
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").value(10))
                // The template block has no requiredPointsPerQuestion, so the group has no maximum.
                .andExpect(jsonPath("$.parts[0].children[0].children[0].maxPoints").doesNotExist());
    }

    @Test
    void remainderGroupTakesSectionCountMinusFixedGroups() throws Exception {
        UUID id = insertExam(section(50, """
                {"id":"fixed","questionType":"SINGLE_CHOICE","questionCount":30,"requiredPointsPerQuestion":1},
                {"id":"rest","questionType":"SINGLE_CHOICE","questionCount":null,"requiredPointsPerQuestion":2}
                """));
        mockMvc.perform(get(summaryUrl(id)).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").value(30))
                .andExpect(jsonPath("$.parts[0].children[0].children[1].allocated").value(20))
                .andExpect(jsonPath("$.parts[0].children[0].children[1].maxPoints").value(40))
                .andExpect(jsonPath("$.parts[0].children[0].allocated").value(50));
    }

    @Test
    void twoRemainderGroupsInOneSectionAreUnknownNotAnError() throws Exception {
        UUID id = insertExam(section(50, """
                {"id":"a","questionType":"SINGLE_CHOICE","questionCount":null},
                {"id":"b","questionType":"SINGLE_CHOICE","questionCount":null}
                """));
        mockMvc.perform(get(summaryUrl(id)).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").doesNotExist())
                .andExpect(jsonPath("$.parts[0].children[0].children[1].allocated").doesNotExist())
                .andExpect(jsonPath("$.parts[0].children[0].allocated").doesNotExist());
    }

    @Test
    void fixedGroupsOverSectionCountLeaveRemainderUnknown() throws Exception {
        UUID id = insertExam(section(50, """
                {"id":"fixed","questionType":"SINGLE_CHOICE","questionCount":60},
                {"id":"rest","questionType":"SINGLE_CHOICE","questionCount":null}
                """));
        mockMvc.perform(get(summaryUrl(id)).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").value(60))
                .andExpect(jsonPath("$.parts[0].children[0].children[1].allocated").doesNotExist());
    }

    @Test
    void omittedGroupCountsZeroAndDoesNotShiftRemainder() throws Exception {
        UUID id = insertExam(section(50, """
                {"id":"skipped","questionType":"ESSAY","questionCount":7,"omitted":true},
                {"id":"fixed","questionType":"SINGLE_CHOICE","questionCount":30},
                {"id":"rest","questionType":"SINGLE_CHOICE","questionCount":null}
                """));
        mockMvc.perform(get(summaryUrl(id)).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].children[0].children[0].allocated").value(0))
                .andExpect(jsonPath("$.parts[0].children[0].children[1].allocated").value(30))
                .andExpect(jsonPath("$.parts[0].children[0].children[2].allocated").value(20))
                .andExpect(jsonPath("$.parts[0].children[0].allocated").value(50));
    }

    private static String section(int count, String blocks) {
        return "{\"parts\":[{\"id\":\"p\",\"name\":\"Part\",\"questionCount\":" + count + ",\"sections\":["
                + "{\"id\":\"s\",\"subjectCode\":\"TOAN\",\"questionCount\":" + count + ",\"blocks\":[" + blocks
                + "]}]}]}";
    }

    private UUID insertExam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Summary','FULL','SHARED')", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'test')",
                id, parts);
        return id;
    }

    private static String summaryUrl(UUID id) {
        return "/api/v1/admin/exams/" + id + "/structure/summary";
    }
}
