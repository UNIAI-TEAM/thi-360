package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbeTests;

/**
 * IT của {@code POST /api/v1/admin/question-assets} (spec 4.5, 10): chữ ký byte chống đuôi
 * giả, cỡ tệp theo loại → 413, giới hạn pixel đọc từ header → 422, quyền 401/403, audit
 * {@code QUESTION.ASSET_UPLOAD}. {@code kind=IMAGE} mới bị giới hạn pixel (quyết định 8.7).
 */
class QuestionAssetUploadIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/question-assets";
    private static final UUID ACTOR = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbc;

    private byte[] png;
    private byte[] jpeg;
    private byte[] webp;

    @BeforeEach
    void cleanAndImages() throws Exception {
        jdbc.update("DELETE FROM questionbank.question_asset WHERE uploaded_by = ?", ACTOR);
        png = QuestionAssetProbeTests.imageBytes("PNG", 4, 3);
        jpeg = QuestionAssetProbeTests.imageBytes("JPEG", 5, 2);
        webp = QuestionAssetProbeTests.webpVp8xHeader(6, 6);
    }

    @Override
    protected org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... permissions) {
        return asUser(ACTOR, "asset-it", permissions);
    }

    // ------------------------------------------------------------- happy path

    @Test
    void uploadImageReturnsServingResponse() throws Exception {
        upload("IMAGE", "a.png", png, "image/png")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith(BASE + "/")))
                .andExpect(jsonPath("$.fileName").value("a.png"))
                .andExpect(jsonPath("$.size").value(png.length));
    }

    @Test
    void uploadWritesRowWithSniffedMimeAndDimensions() throws Exception {
        String body = upload("IMAGE", "photo.jpg", jpeg, "text/plain")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("id").asText());
        var row = jdbc.queryForMap(
                "SELECT kind, mime, size, width, height, octet_length(sha256) AS sha_len, object_key, uploaded_by "
                        + "FROM questionbank.question_asset WHERE id = ?", id);
        assertThat(row.get("kind")).isEqualTo("IMAGE");
        assertThat(row.get("mime")).isEqualTo("image/jpeg"); // chữ ký, không tin Content-Type
        assertThat(row.get("width")).isEqualTo(5);
        assertThat(row.get("height")).isEqualTo(2);
        assertThat(row.get("sha_len")).isEqualTo(32);
        assertThat((String) row.get("object_key")).startsWith("questions/");
        assertThat(row.get("uploaded_by")).isEqualTo(ACTOR);
    }

    @Test
    void attachmentAcceptedBySignature() throws Exception {
        upload("ATTACHMENT", "spec.pdf", "%PDF-1.7\nbody".getBytes(), "application/pdf")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("spec.pdf"));
    }

    @Test
    void attachmentImageAndDocxAccepted() throws Exception {
        upload("ATTACHMENT", "scan.webp", webp, "image/webp")
                .andExpect(status().isCreated());
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        upload("ATTACHMENT", "de.docx", zip, "application/octet-stream")
                .andExpect(status().isCreated());
        upload("ATTACHMENT", "de.zip", zip, "application/zip")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSET_TYPE_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------- từ chối

    @Test
    void htmlDisguisedAsPngRejected() throws Exception {
        upload("IMAGE", "fake.png", "<html><body>x</body></html>".getBytes(), "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSET_TYPE_NOT_ALLOWED"));
    }

    @Test
    void svgRejected() throws Exception {
        upload("IMAGE", "icon.svg",
                "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(),
                "image/svg+xml")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSET_TYPE_NOT_ALLOWED"));
    }

    @Test
    void imageOver5Mb413() throws Exception {
        byte[] big = java.util.Arrays.copyOf(png, 5 * 1024 * 1024 + 1);
        upload("IMAGE", "big.png", big, "image/png")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ASSET_TOO_LARGE"));
    }

    @Test
    void attachmentOver20Mb413() throws Exception {
        byte[] big = java.util.Arrays.copyOf("%PDF-1.7\n".getBytes(), 20 * 1024 * 1024 + 1);
        upload("ATTACHMENT", "big.pdf", big, "application/pdf")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ASSET_TOO_LARGE"));
    }

    @Test
    void pixelLimit422() throws Exception {
        byte[] headerOnly = QuestionAssetProbeTests.pngHeaderOnly(10_000, 10_000);
        upload("IMAGE", "huge.png", headerOnly, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSET_TOO_LARGE"));
        // ATTACHMENT không bị giới hạn pixel (quyết định 8.7) — cùng file header-only qua.
        upload("ATTACHMENT", "huge.png", headerOnly, "image/png")
                .andExpect(status().isCreated());
    }

    @Test
    void corruptPngRejected() throws Exception {
        byte[] corrupt = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        upload("IMAGE", "corrupt.png", corrupt, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSET_TYPE_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------- quyền + audit

    @Test
    void anonymous401() throws Exception {
        mockMvc.perform(multipart(HttpMethod.POST, BASE)
                        .file(file("a.png", png))
                        .header("Origin", "http://localhost:3001")
                        .param("kind", "IMAGE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewOnly403_modifyOnlyOk() throws Exception {
        upload("IMAGE", "a.png", png, "image/png", Permissions.QUESTION_VIEW)
                .andExpect(status().isForbidden());
        upload("IMAGE", "a.png", png, "image/png", Permissions.EXAM_VIEW)
                .andExpect(status().isForbidden());
        upload("IMAGE", "a.png", png, "image/png", Permissions.QUESTION_MODIFY)
                .andExpect(status().isCreated());
    }

    @Test
    void uploadAudited() throws Exception {
        String body = upload("IMAGE", "audit.png", png, "image/png")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM shared.outbox_event WHERE payload::text LIKE ? AND payload::text LIKE ?",
                Integer.class, "%QUESTION.ASSET_UPLOAD%", "%" + id + "%");
        assertThat(count).isEqualTo(1);
    }

    // ------------------------------------------------------------- helpers

    private ResultActions upload(String kind, String fileName, byte[] content, String contentType,
            String... permissions) throws Exception {
        var request = multipart(HttpMethod.POST, BASE)
                .file(file(fileName, content, contentType))
                .param("kind", kind);
        if (permissions.length > 0) {
            request.with(asUser(permissions));
        } else {
            request.with(asUser(Permissions.QUESTION_MODIFY));
        }
        return mockMvc.perform(request);
    }

    private ResultActions upload(String kind, String fileName, byte[] content, String contentType)
            throws Exception {
        return upload(kind, fileName, content, contentType, Permissions.QUESTION_MODIFY);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return file(name, content, "application/octet-stream");
    }

    private static MockMultipartFile file(String name, byte[] content, String contentType) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
