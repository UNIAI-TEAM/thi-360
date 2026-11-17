package vn.unicom.thi360.questionbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionAssetService;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;

/**
 * BE-2b W-WIRE (UNI-2614): resolver thật nối vào đường lưu câu. {@code img} có
 * {@code data-asset-id} của asset {@code IMAGE} được viết lại {@code src} theo đường phục
 * vụ; id không có, id sai loại, hoặc {@code attachmentIds} sai loại đều 422. Ảnh một mình
 * làm nội dung không rỗng (P1-22) nên câu ảnh-duy-nhất đi tới Sẵn sàng được.
 */
@Transactional
class QuestionAssetApiIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";
    private final UUID actor = UUID.randomUUID();

    @Autowired private QuestionAssetRepository assetRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Override
    protected org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... permissions) {
        return asUser(actor, "asset-api-it", permissions);
    }

    private UUID saveAsset(QuestionAssetKind kind) {
        UUID id = UUID.randomUUID();
        assetRepository.saveAndFlush(new QuestionAsset(
                id, kind, "questions/" + id, "a-" + id + ".bin",
                kind == QuestionAssetKind.IMAGE ? "image/png" : "application/pdf",
                10, new byte[32], kind == QuestionAssetKind.IMAGE ? 4 : null,
                kind == QuestionAssetKind.IMAGE ? 4 : null, actor, Instant.now()));
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void imgSrcRewritten() throws Exception {
        UUID assetId = saveAsset(QuestionAssetKind.IMAGE);
        String body = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p>Xem <img data-asset-id='"
                + assetId + "'/></p>\",\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content")
                        .value("<p>Xem <img data-asset-id=\"" + assetId + "\" src=\""
                                + QuestionAssetService.servingPathOf(assetId) + "\"></p>"));
    }

    @Test
    void imgUnknownId422() throws Exception {
        String body = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p><img data-asset-id='"
                + UUID.randomUUID() + "' src='x'></p>\",\"body\":"
                + QuestionTestSupport.singleChoiceBody() + "}";
        create(body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_CONTENT_UNSAFE"));
    }

    @Test
    void imgWrongKind422() throws Exception {
        // Quyết định 8.6: ATTACHMENT trong <img> bị coi như id không có.
        UUID assetId = saveAsset(QuestionAssetKind.ATTACHMENT);
        String body = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p><img data-asset-id='"
                + assetId + "'></p>\",\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_CONTENT_UNSAFE"));
    }

    @Test
    void attachmentIdsAccepted() throws Exception {
        UUID assetId = saveAsset(QuestionAssetKind.ATTACHMENT);
        String body = "{\"type\":\"SINGLE_CHOICE\",\"attachmentIds\":[\"" + assetId
                + "\"],\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body).andExpect(status().isCreated());
    }

    @Test
    void attachmentWrongKind422() throws Exception {
        // Quyết định 8.6: IMAGE trong attachmentIds bị coi như id không có.
        UUID assetId = saveAsset(QuestionAssetKind.IMAGE);
        String body = "{\"type\":\"SINGLE_CHOICE\",\"attachmentIds\":[\"" + assetId
                + "\"],\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void attachmentUnknownId422() throws Exception {
        String body = "{\"type\":\"SINGLE_CHOICE\",\"attachmentIds\":[\"" + UUID.randomUUID()
                + "\"],\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void imageOnlyQuestionReady() throws Exception {
        // P1-22 + quyết định 8.8: img hợp lệ một mình làm content không rỗng → Sẵn sàng được.
        UUID assetId = saveAsset(QuestionAssetKind.IMAGE);
        var created = create("""
                {"type":"SINGLE_CHOICE","subjectCode":"%s","gradeCode":"%s","difficultyCode":"%s",
                 "content":"<p><img data-asset-id='%s'/></p>","body":%s}"""
                .formatted(QuestionTestSupport.SUBJECT_TOAN, QuestionTestSupport.GRADE_G10,
                        QuestionTestSupport.LEVEL_NB, assetId, QuestionTestSupport.singleChoiceBody()))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(
                mapper.readTree(created.getResponse().getContentAsString()).at("/summary/id").asText());
        long revision = jdbc.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
        mockMvc.perform(post(BASE + "/{id}/ready", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }
}
