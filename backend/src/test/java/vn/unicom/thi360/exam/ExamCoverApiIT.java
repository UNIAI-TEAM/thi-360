package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * EX-1 AC-27 to AC-29. The upload commits in its own transaction (the object is stored before the row, plan B2), so
 * these tests run without a surrounding transaction. Each test uploads to exams it creates itself and removes only
 * those: the tables are not assumed empty. The object store is a spy, so "nothing stored" is asserted on the store.
 */
class ExamCoverApiIT extends AbstractIntegrationTest {
    private static final long TWO_MB = 2L * 1024 * 1024;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean ObjectStorage storage;
    private final List<UUID> exams = new ArrayList<>();
    private UUID exam;

    @BeforeEach
    void ownExam() {
        Mockito.reset(storage);
        exam = newExam();
    }

    @AfterEach
    void removeUploads() {
        Mockito.reset(storage);
        for (UUID owned : exams) {
            for (String key : jdbc.queryForList("SELECT object_key FROM exam.exam_cover_asset WHERE exam_id = ?", String.class, owned)) {
                storage.delete(key);
            }
            jdbc.update("UPDATE exam.exam SET cover_asset_id = NULL WHERE id = ?", owned);
            jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", owned);
            jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", owned);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", owned);
        }
    }

    private UUID newExam() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) VALUES (?, ?, ?, 'Cover API test', 'FULL', 'SHARED')",
                id, id.toString(), id.toString());
        exams.add(id);
        return id;
    }

    static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    private org.springframework.test.web.servlet.ResultActions upload(UUID target, String name, String type, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/admin/exams/" + target + "/cover")
                .file(new MockMultipartFile("file", name, type, bytes)).with(asUser(Permissions.EXAM_MODIFY)));
    }

    private long covers() {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_cover_asset WHERE exam_id = ?", Long.class, exam);
    }

    @Test
    void acceptsEachSupportedFormat() throws Exception {
        byte[] webp = new ClassPathResource("exam/cover-600x500.webp").getContentAsByteArray();
        long audits = jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action' = 'EXAM_COVER_UPLOADED' AND payload->>'targetId' = ?", Long.class, exam.toString());
        int n = 0;
        for (var sample : List.of(
                new Object[] {"cover.png", "image/png", image("png", 600, 600), "image/png"},
                new Object[] {"cover.jpg", "image/jpeg", image("jpg", 480, 800), "image/jpeg"},
                new Object[] {"cover.webp", "image/webp", webp, "image/webp"},
                // The signature decides, not the extension or the declared type.
                new Object[] {"renamed.bin", "application/octet-stream", image("png", 500, 500), "image/png"})) {
            JsonNode created = mapper.readTree(upload(exam, (String) sample[0], (String) sample[1], (byte[]) sample[2])
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            UUID asset = UUID.fromString(created.path("assetId").asText());
            assertThat(created.path("url").asText()).isEqualTo("/api/v1/admin/exams/" + exam + "/cover/" + asset);
            var served = mockMvc.perform(get(created.path("url").asText()).with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertThat(served.getContentType()).isEqualTo(sample[3]);
            assertThat(served.getContentAsByteArray()).isEqualTo(sample[2]);
            assertThat(storage.exists("exam-covers/" + asset)).isTrue();
            n++;
        }
        assertThat(covers()).isEqualTo(n);
        // The upload only creates the image; the exam keeps its cover until PUT listing attaches one.
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action' = 'EXAM_COVER_UPLOADED' AND payload->>'targetId' = ?", Long.class, exam.toString()))
                .isEqualTo(audits + n);
        mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/cover/" + UUID.randomUUID()).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidImages() throws Exception {
        byte[] jpeg = image("jpg", 600, 600);
        byte[] atLimit = Arrays.copyOf(jpeg, (int) TWO_MB);
        byte[] overLimit = Arrays.copyOf(jpeg, (int) TWO_MB + 1);
        byte[] text = "not an image at all".getBytes();
        byte[] png = image("png", 600, 600);
        byte[] webp = new ClassPathResource("exam/cover-600x500.webp").getContentAsByteArray();
        var cases = List.of(
                new Object[] {"text with an image extension", "cover.jpg", "image/jpeg", text, "UNSUPPORTED_TYPE"},
                new Object[] {"gif renamed to png", "cover.png", "image/png", image("gif", 600, 600), "UNSUPPORTED_TYPE"},
                new Object[] {"empty file", "cover.png", "image/png", new byte[0], "EMPTY"},
                new Object[] {"2 MB + 1 byte", "cover.jpg", "image/jpeg", overLimit, "TOO_LARGE"},
                new Object[] {"width 4097", "cover.png", "image/png", image("png", 4097, 480), "DIMENSIONS_TOO_LARGE"},
                new Object[] {"height 4097", "cover.png", "image/png", image("png", 480, 4097), "DIMENSIONS_TOO_LARGE"},
                new Object[] {"short side 479", "cover.png", "image/png", image("png", 479, 900), "DIMENSIONS_TOO_SMALL"},
                // R1-13: a valid header (signature, IHDR with 600 x 600) over a payload that stops early.
                new Object[] {"png cut after its header", "cover.png", "image/png", Arrays.copyOf(png, 60), "CORRUPT"},
                new Object[] {"png cut in the middle of its pixels", "cover.png", "image/png", Arrays.copyOf(png, png.length / 2), "CORRUPT"},
                new Object[] {"jpeg cut in the middle of its pixels", "cover.jpg", "image/jpeg", Arrays.copyOf(jpeg, jpeg.length / 2), "CORRUPT"},
                new Object[] {"webp cut after its header", "cover.webp", "image/webp", Arrays.copyOf(webp, webp.length - 12), "CORRUPT"});
        for (Object[] sample : cases) {
            upload(exam, (String) sample[1], (String) sample[2], (byte[]) sample[3])
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_LISTING_INVALID"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value((String) sample[4]));
        }
        assertThat(covers()).as("no row for a rejected image").isZero();
        verify(storage, never()).put(anyString(), any(), anyString());
        // Exactly 2 MB, 4.096 px and 480 px are accepted.
        upload(exam, "limit.jpg", "image/jpeg", atLimit).andExpect(status().isCreated());
        upload(exam, "wide.png", "image/png", image("png", 4096, 480)).andExpect(status().isCreated());
        upload(exam, "short.png", "image/png", image("png", 480, 480)).andExpect(status().isCreated());
        assertThat(covers()).isEqualTo(3);
        verify(storage, Mockito.times(3)).put(anyString(), any(), anyString());
        mockMvc.perform(multipart("/api/v1/admin/exams/" + UUID.randomUUID() + "/cover")
                .file(new MockMultipartFile("file", "a.png", "image/png", image("png", 600, 600)))
                .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isNotFound());
        verify(storage, Mockito.times(3)).put(anyString(), any(), anyString());
    }

    @Test
    void coverOfAnotherExamIsRejected() throws Exception {
        UUID other = newExam();
        JsonNode created = mapper.readTree(upload(other, "cover.png", "image/png", image("png", 600, 600))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String asset = created.path("assetId").asText();
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
        String body = "{\"summary\":\"x\",\"coverAssetId\":\"" + asset + "\",\"expectedRevision\":" + revision + "}";
        mockMvc.perform(put("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EXAM_LISTING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coverAssetId"));
        assertThat(jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam)).isEqualTo(revision);
        // The cover is not readable through another exam's route either.
        mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/cover/" + asset).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_listing WHERE exam_id = ?", Long.class, exam)).isZero();
    }
}
