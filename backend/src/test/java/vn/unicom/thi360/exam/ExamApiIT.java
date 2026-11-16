package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class ExamApiIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";

    @Test
    void permissionsAndMissingExam() throws Exception {
        mockMvc.perform(get(EXAM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("HSA-F-DEV-001"));
        mockMvc.perform(get("/api/v1/admin/exams/" + UUID.randomUUID())
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleRevisionIsRejected() throws Exception {
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":9223372036854775807}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("EXAM_REVISION_CONFLICT"));
    }
}
