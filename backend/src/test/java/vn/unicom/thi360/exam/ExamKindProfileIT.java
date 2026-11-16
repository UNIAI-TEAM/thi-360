package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamKindProfileIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void profileUpdateRequiresCatalogPermissionAndCurrentRevision() throws Exception {
        String id = "00000000-0000-7000-8200-000000000101";
        long revision = jdbc.queryForObject(
                "SELECT revision FROM exam.exam_kind_profile WHERE exam_item_id=?::uuid", Long.class, id);
        String url = "/api/v1/admin/exam-kinds/" + id + "/profile";
        String body = """
                {"scopes":["FULL","PART"],"templateBy":"KIND","timing":"PER_PART",
                 "maxScore":150,"practiceScoring":"RAW","expectedRevision":%d}
                """.formatted(revision);
        mockMvc.perform(put(url).header("Origin", "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put(url).with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("EXAM_REVISION_CONFLICT"));
    }
}
