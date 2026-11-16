package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;

/**
 * Ref {@code question_asset_ref} theo phiên bản, cùng transaction lưu (model note 3):
 * lưu Nháp tại chỗ thay ref của phiên bản đó; sửa câu READY tạo phiên bản làm việc mới
 * kèm ref riêng, ref của bản phục vụ giữ nguyên; duplicate/restore cũng ghi ref; tài nguyên
 * trong nội dung câu con ghi ref trên dòng câu con (quyết định 8.4).
 */
@Transactional
class QuestionAssetRefIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";
    private final UUID actor = UUID.randomUUID();

    @Autowired private QuestionAssetRepository assetRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Override
    protected org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... permissions) {
        return asUser(actor, "asset-ref-it", permissions);
    }

    private UUID saveAsset(QuestionAssetKind kind) {
        UUID id = UUID.randomUUID();
        assetRepository.saveAndFlush(new QuestionAsset(
                id, kind, "questions/" + id, "a.bin", "image/png", 10, new byte[32],
                kind == QuestionAssetKind.IMAGE ? 4 : null,
                kind == QuestionAssetKind.IMAGE ? 4 : null, actor, Instant.now()));
        return id;
    }

    private List<UUID> refsOf(UUID questionId, int versionNo) {
        return jdbc.queryForList(
                "SELECT asset_id FROM questionbank.question_asset_ref"
                        + " WHERE question_id = ? AND version_no = ? ORDER BY asset_id",
                UUID.class, questionId, versionNo);
    }

    private UUID createId(String body) throws Exception {
        var created = mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(
                mapper.readTree(created.getResponse().getContentAsString()).at("/summary/id").asText());
    }

    private long revision(UUID id) {
        return jdbc.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
    }

    private void update(UUID id, String body) throws Exception {
        mockMvc.perform(put(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void markReady(UUID id) throws Exception {
        mockMvc.perform(post(BASE + "/{id}/ready", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision(id) + "}"))
                .andExpect(status().isOk());
    }

    private static String imgHtml(UUID assetId) {
        return "<p><img data-asset-id='" + assetId + "'/></p>";
    }

    private static String createBody(UUID assetId) {
        return "{\"type\":\"SINGLE_CHOICE\",\"subjectCode\":\"TOAN\",\"gradeCode\":\"G10\","
                + "\"difficultyCode\":\"NB\",\"content\":\"" + imgHtml(assetId)
                + "\",\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
    }

    private static String updateBody(UUID assetId, long revision) {
        return "{\"expectedRevision\":" + revision + ",\"type\":\"SINGLE_CHOICE\","
                + "\"subjectCode\":\"TOAN\",\"gradeCode\":\"G10\",\"difficultyCode\":\"NB\",\"content\":\""
                + imgHtml(assetId) + "\",\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
    }

    @Test
    void createWritesRefs() throws Exception {
        UUID asset = saveAsset(QuestionAssetKind.IMAGE);
        UUID attachment = saveAsset(QuestionAssetKind.ATTACHMENT);
        UUID id = createId("{\"type\":\"SINGLE_CHOICE\",\"content\":\"" + imgHtml(asset)
                + "\",\"attachmentIds\":[\"" + attachment + "\"],\"body\":"
                + QuestionTestSupport.singleChoiceBody() + "}");
        assertThat(refsOf(id, 1)).containsExactlyInAnyOrder(asset, attachment);
    }

    @Test
    void inPlaceDraftSaveReplacesRefs() throws Exception {
        UUID first = saveAsset(QuestionAssetKind.IMAGE);
        UUID second = saveAsset(QuestionAssetKind.IMAGE);
        UUID id = createId(createBody(first));
        assertThat(refsOf(id, 1)).containsExactly(first);
        update(id, updateBody(second, revision(id)));
        // Lưu tại chỗ cùng version_no=1: ref cũ bị thay, không còn ref tới asset đầu.
        assertThat(refsOf(id, 1)).containsExactly(second);
    }

    @Test
    void readyThenEditKeepsServingRefs() throws Exception {
        UUID serving = saveAsset(QuestionAssetKind.IMAGE);
        UUID working = saveAsset(QuestionAssetKind.IMAGE);
        UUID id = createId(createBody(serving));
        markReady(id); // version 1 trở thành bản phục vụ
        update(id, updateBody(working, revision(id))); // tạo working version 2
        assertThat(refsOf(id, 1)).containsExactly(serving); // bản phục vụ giữ ref
        assertThat(refsOf(id, 2)).containsExactly(working); // bản làm việc có ref riêng
    }

    @Test
    void duplicateWritesRefsForCopy() throws Exception {
        UUID asset = saveAsset(QuestionAssetKind.IMAGE);
        UUID id = createId(createBody(asset));
        var duplicated = mockMvc.perform(post(BASE + "/{id}/duplicate", id)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        UUID copyId = UUID.fromString(
                mapper.readTree(duplicated.getResponse().getContentAsString())
                        .at("/summary/id").asText());
        assertThat(refsOf(copyId, 1)).containsExactly(asset);
    }

    @Test
    void restoreWritesRefsForWorkingVersion() throws Exception {
        UUID first = saveAsset(QuestionAssetKind.IMAGE);
        UUID second = saveAsset(QuestionAssetKind.IMAGE);
        UUID id = createId(createBody(first));
        markReady(id); // v1 = bản phục vụ có asset `first`
        update(id, updateBody(second, revision(id))); // v2 làm việc có asset `second`
        // Khôi phục nội dung v1 vào bản làm việc v2 → ref v2 trỏ lại asset `first`.
        mockMvc.perform(post(BASE + "/{id}/versions/{v}/restore", id, 1)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision(id) + "}"))
                .andExpect(status().isOk());
        assertThat(refsOf(id, 1)).containsExactly(first);
        assertThat(refsOf(id, 2)).containsExactly(first);
    }

    @Test
    void setChildrenRefsPerRow() throws Exception {
        // Quyết định 8.4: ref trên dòng có HTML dùng tài nguyên — passage của bộ → dòng bộ,
        // nội dung câu con → dòng câu con.
        UUID passageAsset = saveAsset(QuestionAssetKind.IMAGE);
        UUID childAsset = saveAsset(QuestionAssetKind.IMAGE);
        String body = """
                {"type":"QUESTION_SET","subjectCode":"%s","gradeCode":"%s","difficultyCode":"%s",
                 "body":{"type":"QUESTION_SET","passage":"<p>Đọc <img data-asset-id='%s'/></p>",
                  "children":[
                    {"type":"SINGLE_CHOICE","content":"<p>C1 <img data-asset-id='%s'/></p>",
                     "body":%s},
                    {"type":"SHORT_ANSWER","content":"<p>C2</p>","body":%s}]}}"""
                .formatted(QuestionTestSupport.SUBJECT_TOAN, QuestionTestSupport.GRADE_G10,
                        QuestionTestSupport.LEVEL_NB, passageAsset, childAsset,
                        QuestionTestSupport.singleChoiceBody(), QuestionTestSupport.shortAnswerBody());
        UUID id = createId(body);
        List<UUID> childIds = jdbc.queryForList(
                "SELECT id FROM questionbank.question WHERE parent_id = ? ORDER BY position",
                UUID.class, id);
        assertThat(childIds).hasSize(2);
        assertThat(refsOf(id, 1)).containsExactly(passageAsset);
        assertThat(refsOf(childIds.get(0), 1)).containsExactly(childAsset);
        assertThat(refsOf(childIds.get(1), 1)).isEmpty();
    }
}
