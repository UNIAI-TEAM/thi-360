package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

@Transactional
class ExamApiIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
    private static final UUID EXAM_ID = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamRepository exams;
    @Autowired private ExamKindProfileRepository profiles;
    @Autowired private ExamCheckResultStore results;
    @Autowired private QuestionSourcePort source;
    @Autowired private CatalogLookup catalog;

    @Test
    void createChecksPermissionConflictValidationAndAudit() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_VIEW))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String result = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(result).path("id").asText();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'module'='EXAM' AND payload->>'action'='EXAM.CREATE' "
                        + "AND payload->>'targetId'=?", Long.class, id)).isEqualTo(1L);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_CODE_TAKEN"));
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"FULL\"", "\"SUBJECT\"").replace(code, code + "X")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_NOT_ALLOWED"));
    }

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

    @Test
    void instructionsImg422() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code
                + "\",\"instructions\":\"<p>Doc ky</p><img src=\\\"x.png\\\">\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
        long revision = mapper.readTree(mockMvc.perform(get(EXAM)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("revision").asLong();
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"<img src=\\\"x.png\\\" onerror=\\\"pwn()\\\"/>\","
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
    }

    @Test
    void instructionsNonImageViolationRejectedOnCreateAndPatch() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code
                + "\",\"instructions\":\"<p onclick=\\\"alert(1)\\\">Doc ky</p>\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
        long revision = mapper.readTree(mockMvc.perform(get(EXAM)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("revision").asLong();
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"<p>Ok</p><script>alert(1)</script>\","
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
    }

    @Test
    void safeInstructionsAreStoredCleaned() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String html = "<P><STRONG>Doc ky</STRONG> <EM>de bai</EM></P><UL><LI>70 phut</LI></UL>";
        String cleaned = "<p><strong>Doc ky</strong> <em>de bai</em></p><ul><li>70 phut</li></ul>";
        String result = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\""
                                + code + "\",\"instructions\":\"" + html.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(result).path("id").asText();
        String detail = mockMvc.perform(get("/api/v1/admin/exams/" + id)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(detail).path("instructions").asText()).isEqualTo(cleaned);
    }

    @Test
    void auditCapturesMetadataAndStructureChanges() throws Exception {
        var before = mapper.readTree(mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated name\",\"expectedRevision\":" + before.path("revision").asLong() + "}"))
                .andExpect(status().isOk());
        entityManager.flush();
        String id = "00000000-0000-7000-8700-000000000001";
        var metadataAudit = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'action'='EXAM.UPDATE' AND payload->>'targetId'=?",
                String.class, id));
        assertThat(metadataAudit.path("before").path("name").asText()).isEqualTo(before.path("name").asText());
        assertThat(metadataAudit.path("after").path("name").asText()).isEqualTo("Updated name");

        var structure = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = structure.path("parts").deepCopy();
        var part = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0);
        String partId = part.path("id").asText();
        part.put("name", "Updated part");
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", structure.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        entityManager.flush();
        var structureAudit = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'action'='EXAM.STRUCTURE_UPDATE' AND payload->>'targetId'=?",
                String.class, id));
        assertThat(structureAudit.path("before").path("structureHash").asText()).isNotBlank();
        assertThat(structureAudit.path("after").path("structureHash").asText())
                .isNotBlank().isNotEqualTo(structureAudit.path("before").path("structureHash").asText());
        assertThat(structureAudit.path("before").path("changedPartIds").toString()).contains(partId);
        assertThat(structureAudit.path("after").path("changedPartIds").toString()).contains(partId);
    }

    @Test
    void listReturnsDetailFieldsAndFiltersServerSide() throws Exception {
        // Bản tóm tắt mang đủ trường của chi tiết (FE-2:2).
        String page = mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].timing").value("PER_PART"))
                .andExpect(jsonPath("$.items[0].practiceScoring").value("RAW"))
                .andExpect(jsonPath("$.items[0].revision").isNumber())
                .andReturn().getResponse().getContentAsString();
        var item = mapper.readTree(page).path("items").get(0);
        assertThat(item.path("partCode").isNull()).isTrue();
        assertThat(item.path("subjectCode").isNull()).isTrue();
        assertThat(item.path("referenceMax").isNull()).isTrue();
        long persistedRevision = jdbc.queryForObject(
                "SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM_ID);
        assertThat(item.path("revision").asLong())
                .as("list trả đúng revision đã lưu (0 là giá trị hợp lệ của seed mới)")
                .isEqualTo(persistedRevision);

        // Truyền revision qua list trên một bài tạo riêng: tạo -> PATCH -> revision tăng đúng.
        String isoCode = "ISO-REV-" + UUID.randomUUID().toString().substring(0, 8);
        String created = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"ISO\",\"code\":\""
                                + isoCode + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID isoId = UUID.fromString(mapper.readTree(created).path("id").asText());
        long isoRev = jdbc.queryForObject(
                "SELECT revision FROM exam.exam WHERE id = ?", Long.class, isoId);
        mockMvc.perform(get("/api/v1/admin/exams").param("q", isoCode)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(isoCode))
                .andExpect(jsonPath("$.items[0].revision").value((int) isoRev));
        mockMvc.perform(patch(EXAM.replace(EXAM_ID.toString(), isoId.toString()))
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ISO renamed\",\"expectedRevision\":" + isoRev + "}"))
                .andExpect(status().isOk());
        long isoRevAfter = jdbc.queryForObject(
                "SELECT revision FROM exam.exam WHERE id = ?", Long.class, isoId);
        assertThat(isoRevAfter).isGreaterThan(isoRev);
        mockMvc.perform(get("/api/v1/admin/exams").param("q", isoCode)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].revision").value((int) isoRevAfter));

        // hasUnpublishedChanges lọc ở máy chủ: bài dev đang true nên false rỗng.
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("hasUnpublishedChanges", "false").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = false WHERE id = ?", EXAM_ID);
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("hasUnpublishedChanges", "false").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value("HSA-F-DEV-001"));
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("hasUnpublishedChanges", "true").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", EXAM_ID);

        // checkStatus lọc theo trạng thái hiệu lực (đánh giá lại), không theo cột đã lưu.
        Exam exam = exams.findById(EXAM_ID).orElseThrow();
        Long profileRevision = profiles.findById(exam.getKindItemId()).orElseThrow().getRevision();
        String structureHash = jdbc.queryForObject(
                "SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, EXAM_ID);
        Map<UUID, Long> revisions = source.sourceRevisions(List.of(SUBJECT_ID));
        assertThat(results.save(new ExamCheckResultStore.Input(EXAM_ID, exam.getRevision(), structureHash,
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), profileRevision,
                catalog.revision(), "fingerprint", mapper.valueToTree(revisions)),
                "VALID", 0, Instant.now(), 1,
                mapper.createObjectNode().put("status", "VALID"), mapper.createObjectNode())).isTrue();

        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("checkStatus", "VALID").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].check.status").value("VALID"));
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("checkStatus", "NEEDS_RECHECK").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));

        jdbc.update("INSERT INTO questionbank.question_source_revision (subject_item_id, revision)"
                        + " VALUES (?, 1) ON CONFLICT (subject_item_id) DO UPDATE SET revision"
                        + " = questionbank.question_source_revision.revision + 1", SUBJECT_ID);
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("checkStatus", "NEEDS_RECHECK").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].check.status").value("NEEDS_RECHECK"));
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .param("checkStatus", "VALID").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void listCheckStatusFilterPagesAcrossMixedStatuses() throws Exception {
        // R1-2/R1-5: lọc checkStatus bằng vị từ DB — tổng chính xác qua nhiều trang,
        // trang sắp xếp và không chồng lấn. 3 UNCHECKED + 3 NEEDS_RECHECK + 1 VALID.
        String prefix = "PGMIX-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> uncheckedCodes = new ArrayList<>();
        List<String> recheckCodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            uncheckedCodes.add(createExam(prefix + "-U" + i));
            recheckCodes.add(createExam(prefix + "-R" + i));
        }
        UUID validId = UUID.fromString(
                mapper.readTree(createExamBody(prefix + "-V0")).path("id").asText());

        // 3 bài có check_status khác UNCHECKED mà chưa từng check -> NEEDS_RECHECK.
        jdbc.update("UPDATE exam.exam SET check_status = 'VALID'"
                        + " WHERE code_normalized LIKE ?",
                (prefix + "-r%").toLowerCase(java.util.Locale.ROOT));

        // 1 bài VALID: run đã lưu khớp mọi đầu vào hiện tại (hash cấu trúc, snapshot
        // cấu hình, revision profile và danh mục; không source nào nên source_revisions rỗng).
        Exam validExam = exams.findById(validId).orElseThrow();
        String validHash = "pgmix-" + prefix;
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = ? WHERE exam_id = ?",
                validHash, validId);
        Long profileRevision = profiles.findById(validExam.getKindItemId())
                .orElseThrow().getRevision();
        assertThat(results.save(new ExamCheckResultStore.Input(validId, validExam.getRevision(),
                validHash, mapper.valueToTree(ExamCheckFingerprint.Config.from(validExam)),
                profileRevision, catalog.revision(), "fingerprint",
                mapper.createObjectNode()), "VALID", 0, Instant.now(), 1,
                mapper.createObjectNode().put("status", "VALID"),
                mapper.createObjectNode())).isTrue();

        String q = prefix.toLowerCase(java.util.Locale.ROOT);
        assertStatusPage(q, "UNCHECKED", 2, 0, 3, uncheckedCodes);
        assertStatusPage(q, "UNCHECKED", 2, 1, 1, uncheckedCodes);
        assertStatusPage(q, "NEEDS_RECHECK", 2, 0, 3, recheckCodes);
        assertStatusPage(q, "NEEDS_RECHECK", 2, 1, 1, recheckCodes);
        assertStatusPage(q, "VALID", 20, 0, 1, null);
        assertStatusPage(q, "INVALID", 20, 0, 0, null);
    }

    private String createExam(String code) throws Exception {
        createExamBody(code);
        return code;
    }

    private String createExamBody(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"" + code
                                + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void assertStatusPage(String q, String checkStatus, int size, int page,
            int expectedCount, List<String> expectedUniverse) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/exams")
                        .param("q", q).param("checkStatus", checkStatus)
                        .param("size", String.valueOf(size)).param("page", String.valueOf(page))
                        .param("sort", "code,asc").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("items").size())
                .as("page %d của %s", page, checkStatus).isEqualTo(expectedCount);
        List<String> codes = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        root.path("items").forEach(item -> {
            codes.add(item.path("code").asText());
            statuses.add(item.path("check").path("status").asText());
        });
        assertThat(statuses).allMatch(checkStatus::equals);
        assertThat(codes).isSorted();
        if (expectedUniverse != null) {
            assertThat(codes).allMatch(expectedUniverse::contains);
            assertThat(root.path("totalItems").asInt()).isEqualTo(expectedUniverse.size());
        } else {
            assertThat(root.path("totalItems").asInt()).isEqualTo(expectedCount);
        }
        assertThat(root.path("page").asInt()).isEqualTo(page);
    }
}
