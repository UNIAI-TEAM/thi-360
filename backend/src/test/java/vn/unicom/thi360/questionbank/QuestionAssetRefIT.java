package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import vn.unicom.thi360.questionbank.application.QuestionAssetCleanupJob;

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
    @Autowired private QuestionAssetCleanupJob cleanupJob;
    @Autowired private EntityManager entityManager;

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

    @Test
    void readySetWorkingChildRefsServingUntouchedAndAgedWorkingAssetSurvivesCleanup()
            throws Exception {
        // R1-5: sửa câu bộ đang READY — đổi ảnh một câu con, thêm câu con mới và bỏ một câu
        // con khỏi bản làm việc. Ref đi theo (id, version_no) của dòng câu con; hàng và ref
        // phục vụ (v1) không đổi; tài nguyên chỉ còn ở bản làm việc, dù đã quá hạn dọn, vẫn
        // sống — test này đỏ nếu gỡ saveWorkingChildren.
        UUID passageAsset = saveAsset(QuestionAssetKind.IMAGE);
        UUID a1 = saveAsset(QuestionAssetKind.IMAGE);
        UUID a2 = saveAsset(QuestionAssetKind.IMAGE);
        String body = """
                {"type":"QUESTION_SET","subjectCode":"%s","gradeCode":"%s","difficultyCode":"%s",
                 "body":{"type":"QUESTION_SET","passage":"<p>Đọc <img data-asset-id='%s'/></p>",
                  "children":[
                    {"type":"SINGLE_CHOICE","content":"<p>C1 <img data-asset-id='%s'/></p>",
                     "body":%s},
                    {"type":"SHORT_ANSWER","content":"<p>C2 <img data-asset-id='%s'/></p>",
                     "body":%s}]}}"""
                .formatted(QuestionTestSupport.SUBJECT_TOAN, QuestionTestSupport.GRADE_G10,
                        QuestionTestSupport.LEVEL_NB, passageAsset, a1,
                        QuestionTestSupport.singleChoiceBody(), a2,
                        QuestionTestSupport.shortAnswerBody());
        UUID setId = createId(body);
        List<UUID> childIds = jdbc.queryForList(
                "SELECT id FROM questionbank.question WHERE parent_id = ? ORDER BY position",
                UUID.class, setId);
        assertThat(childIds).hasSize(2);
        UUID c1 = childIds.get(0);
        UUID c2 = childIds.get(1);
        String c1ServingContent = contentOf(c1);
        entityManager.flush();
        entityManager.clear();
        markReady(setId); // v1 trở thành bản phục vụ (cha lẫn các câu con)
        entityManager.flush();
        entityManager.clear();

        // Bản làm việc v2: c1 đổi sang ảnh mới, c2 bị bỏ, thêm câu con mới với ảnh riêng.
        UUID a1b = saveAsset(QuestionAssetKind.IMAGE);
        UUID a3 = saveAsset(QuestionAssetKind.IMAGE);
        age(a3); // quá hạn orphan 24h — chỉ sống nhờ ref của bản làm việc
        String update = """
                {"expectedRevision":%d,"type":"QUESTION_SET","subjectCode":"%s",
                 "gradeCode":"%s","difficultyCode":"%s",
                 "body":{"type":"QUESTION_SET","passage":"<p>Đọc <img data-asset-id='%s'/></p>",
                  "children":[
                    {"id":"%s","type":"SINGLE_CHOICE",
                     "content":"<p>C1 <img data-asset-id='%s'/></p>","body":%s},
                    {"type":"SHORT_ANSWER",
                     "content":"<p>C3 <img data-asset-id='%s'/></p>","body":%s}]}}"""
                .formatted(revision(setId), QuestionTestSupport.SUBJECT_TOAN,
                        QuestionTestSupport.GRADE_G10, QuestionTestSupport.LEVEL_NB,
                        passageAsset, c1, a1b, QuestionTestSupport.singleChoiceBody(),
                        a3, QuestionTestSupport.shortAnswerBody());
        // First save includes c2 in the working version; the next save removes it.
        var firstUpdate = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(update);
        var children = (com.fasterxml.jackson.databind.node.ArrayNode) firstUpdate.at("/body/children");
        var retained = mapper.createObjectNode();
        retained.put("id", c2.toString());
        retained.put("type", "SHORT_ANSWER");
        retained.put("content", imgHtml(a2));
        retained.set("body", mapper.readTree(QuestionTestSupport.shortAnswerBody()));
        children.add(retained);
        update(setId, mapper.writeValueAsString(firstUpdate));
        assertThat(refsOf(c2, 2)).containsExactly(a2);
        UUID c3 = onlyChild(setId, c1);
        entityManager.flush();
        entityManager.clear();
        var finalUpdate = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(update);
        finalUpdate.put("expectedRevision", revision(setId));
        ((com.fasterxml.jackson.databind.node.ObjectNode) finalUpdate.at("/body/children/1"))
                .put("id", c3.toString());
        update(setId, mapper.writeValueAsString(finalUpdate));
        // Ref v1 (phục vụ) và hàng phục vụ giữ nguyên — kể cả câu con bị bỏ khỏi bản làm việc.
        assertThat(refsOf(setId, 1)).containsExactly(passageAsset);
        assertThat(refsOf(c1, 1)).containsExactly(a1);
        assertThat(refsOf(c2, 1)).containsExactly(a2);
        assertThat(refsOf(c3, 1)).isEmpty();
        assertThat(servingVersionNo(setId)).isEqualTo(1);
        assertThat(servingVersionNo(c1)).isEqualTo(1);
        assertThat(servingVersionNo(c2)).isEqualTo(1);
        assertThat(servingVersionNo(c3)).isNull();
        assertThat(contentOf(c1)).isEqualTo(c1ServingContent);
        // Ref v2 (làm việc): cha theo passage, c1 theo ảnh mới, c3 theo ảnh của nó; c2 không có.
        assertThat(refsOf(setId, 2)).containsExactly(passageAsset);
        assertThat(refsOf(c1, 2)).containsExactly(a1b);
        assertThat(refsOf(c2, 2)).isEmpty();
        assertThat(refsOf(c3, 2)).containsExactly(a3);
        // Quét mồ côi trong transaction của test: asset quá hạn nhưng chỉ có ở v2 vẫn giữ.
        cleanupJob.run();
        assertThat(assetExists(a3)).isTrue();
        assertThat(assetExists(a1)).isTrue();
        assertThat(assetExists(a1b)).isTrue();
        assertThat(assetExists(a2)).isTrue();
    }

    private void age(UUID assetId) {
        jdbc.update("UPDATE questionbank.question_asset SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(java.time.Duration.ofHours(30))),
                assetId);
    }

    private boolean assetExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*) > 0 FROM questionbank.question_asset WHERE id = ?",
                Boolean.class, id));
    }

    /** Câu con mới của bản làm việc: hàng tạo sẵn với deleted_at (chưa phục vụ). */
    private UUID onlyChild(UUID parentId, UUID knownId) {
        List<UUID> others = jdbc.queryForList(
                "SELECT id FROM questionbank.question WHERE parent_id = ? AND id <> ?"
                        + " AND deleted_at IS NOT NULL",
                UUID.class, parentId, knownId);
        assertThat(others).hasSize(1);
        return others.get(0);
    }

    private Integer servingVersionNo(UUID id) {
        return jdbc.queryForObject(
                "SELECT current_version_no FROM questionbank.question WHERE id = ?",
                Integer.class, id);
    }

    private String contentOf(UUID id) {
        return jdbc.queryForObject(
                "SELECT content_text FROM questionbank.question WHERE id = ?", String.class, id);
    }
}
