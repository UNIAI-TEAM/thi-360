package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbeTests;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * IT của {@code GET /api/v1/admin/question-assets/{id}} (spec 4.5): phát nội dung kèm
 * {@code nosniff} + {@code Content-Disposition} inline (ảnh) / attachment (tệp), quyền
 * một-trong {@code QUESTION.VIEW|EXAM.VIEW} — 401 chưa đăng nhập, 403 khi thiếu cả hai,
 * 404 khi id không có hoặc object đã bị dọn.
 */
class QuestionAssetDownloadIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/question-assets";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectStorage objectStorage;

    private byte[] png;
    private UUID imageId;
    private UUID attachmentId;

    @BeforeEach
    void seed() throws Exception {
        png = QuestionAssetProbeTests.imageBytes("PNG", 4, 3);
        imageId = upload("IMAGE", "img.png", png, "image/png");
        byte[] pdf = "%PDF-1.7\nfake".getBytes();
        attachmentId = upload("ATTACHMENT", "spec.pdf", pdf, "application/pdf");
    }

    // ------------------------------------------------------------- phục vụ

    @Test
    void imageServedInlineWithNosniff() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(png));
    }

    @Test
    void attachmentServedAsAttachment() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", attachmentId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")));
    }

    // ------------------------------------------------------------- quyền

    @Test
    void examViewAloneCanFetch() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void questionViewAloneCanFetch() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", attachmentId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void neitherPermission403() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous401() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- 404

    @Test
    void unknownId404() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    void orphanRowWithoutObject404() throws Exception {
        String key = jdbc.queryForObject(
                "SELECT object_key FROM questionbank.question_asset WHERE id = ?",
                String.class, imageId);
        objectStorage.delete(key);
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- helpers

    private UUID upload(String kind, String fileName, byte[] content, String contentType) throws Exception {
        String body = mockMvc.perform(multipart(HttpMethod.POST, BASE)
                        .file(new MockMultipartFile("file", fileName, contentType, content))
                        .param("kind", kind)
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("id").asText());
    }
}
