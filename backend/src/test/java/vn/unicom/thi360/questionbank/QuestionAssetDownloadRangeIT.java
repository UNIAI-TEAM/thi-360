package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbeTests;

/**
 * IT byte-range của {@code GET /api/v1/admin/question-assets/{id}} (UNI-2698): nội dung
 * trả về là {@code Resource} nên Spring MVC tự phục vụ range — 200 kèm
 * {@code Accept-Ranges: bytes} và {@code Content-Length} đầy đủ khi không có {@code Range};
 * 206 kèm {@code Content-Range}/{@code Content-Length} của vùng byte khi range hợp lệ;
 * 416 kèm {@code Content-Range: bytes *\/total} và body rỗng khi range vượt cuối hoặc
 * không parse được.
 * Quyền giữ nguyên: 401 chưa đăng nhập, 403 khi thiếu cả {@code QUESTION.VIEW|EXAM.VIEW},
 * {@code EXAM.VIEW} một mình vẫn tải được (206 khi có range), 404 khi id không có.
 */
class QuestionAssetDownloadRangeIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/question-assets";

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

    // ------------------------------------------------------------- hàng 1: 200 đầy đủ

    @Test
    void fullContentWithAcceptRanges() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", String.valueOf(png.length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(png));

        mockMvc.perform(get(BASE + "/{id}", attachmentId).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")));
    }

    // ------------------------------------------------------------- hàng 2-3: 206

    @Test
    void rangeBytesStartEnd206() throws Exception {
        int total = png.length;
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=2-5")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/" + total))
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(Arrays.copyOfRange(png, 2, 6)));
    }

    @Test
    void rangeBytesOpenEndAndSuffix206() throws Exception {
        int total = png.length;
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=" + (total - 3) + "-")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range",
                        "bytes " + (total - 3) + "-" + (total - 1) + "/" + total))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(Arrays.copyOfRange(png, total - 3, total)));

        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=-4")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range",
                        "bytes " + (total - 4) + "-" + (total - 1) + "/" + total))
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(Arrays.copyOfRange(png, total - 4, total)));
    }

    // ------------------------------------------------------------- hàng 4: 416

    @Test
    void rangeStartPastEnd416() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=" + (png.length + 100) + "-")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + png.length))
                .andExpect(result -> {
                    String contentLength = result.getResponse().getHeader("Content-Length");
                    assertThat(contentLength == null ? "0" : contentLength).isEqualTo("0");
                    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
                });
    }

    @Test
    void malformedRange416() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "garbage-not-a-range")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + png.length))
                .andExpect(result -> {
                    String contentLength = result.getResponse().getHeader("Content-Length");
                    assertThat(contentLength == null ? "0" : contentLength).isEqualTo("0");
                    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
                });
    }

    // ------------------------------------------------------------- hàng 5: quyền với Range

    @Test
    void anonymousWithRange401() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId).header("Range", "bytes=0-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void neitherPermissionWithRange403() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=0-1")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
    }

    @Test
    void examViewAloneWithRange206() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", imageId)
                        .header("Range", "bytes=0-1")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-1/" + png.length));
    }

    @Test
    void unknownIdWithRange404() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                        .header("Range", "bytes=0-1")
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("ASSET_NOT_FOUND"));
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
