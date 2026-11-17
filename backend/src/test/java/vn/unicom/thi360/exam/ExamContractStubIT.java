package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class ExamContractStubIT extends AbstractIntegrationTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void summaryRequiresViewAndReportsNotImplemented() throws Exception {
        String url = "/api/v1/admin/exams/" + ID + "/structure/summary";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void overviewRequiresModifyAndReportsNotImplemented() throws Exception {
        String url = "/api/v1/admin/exams/" + ID + "/overview";
        String body = "{\"overview\":[],\"expectedRevision\":0}";
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void scopedCheckDoesNotSilentlyRunWholeExam() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams/" + ID + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"section\"}"))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }
}
