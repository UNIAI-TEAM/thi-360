package vn.unicom.thi360.commerce.classassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.storefront.FakePublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PartPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

@Import(ClassExamAssignmentIT.FakeExamPortConfig.class)
class ClassExamAssignmentIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakeExamPortConfig {
        @Bean
        @Primary
        FakePublishedExamPort fakePublishedExamPort() {
            return new FakePublishedExamPort();
        }
    }

    @Autowired private FakePublishedExamPort examPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalog;
    @Autowired private EntitlementPort entitlementPort;

    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID classId : createdClassIds) {
            jdbc.update(
                    """
                    DELETE FROM commerce.entitlement_usage
                    WHERE grant_id IN (
                        SELECT id FROM commerce.entitlement_grant
                        WHERE class_assignment_id IN (
                            SELECT id FROM commerce.class_exam_assignment WHERE class_id = ?
                        )
                    )
                    """,
                    classId);
            jdbc.update(
                    """
                    DELETE FROM commerce.entitlement_grant
                    WHERE class_assignment_id IN (
                        SELECT id FROM commerce.class_exam_assignment WHERE class_id = ?
                    )
                    """,
                    classId);
            jdbc.update(
                    "DELETE FROM commerce.entitlement WHERE source = 'CLASS' AND source_ref = ?",
                    classId);
            jdbc.update("DELETE FROM commerce.class_exam_assignment WHERE class_id = ?", classId);
            jdbc.update(
                    "DELETE FROM classroom.class_member_history WHERE class_id = ? OR related_class_id = ?",
                    classId,
                    classId);
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        createdClassIds.clear();

        for (UUID userId : createdUserIds) {
            jdbc.update(
                    """
                    DELETE FROM commerce.entitlement_usage
                    WHERE grant_id IN (
                        SELECT id FROM commerce.entitlement_grant
                        WHERE entitlement_id IN (SELECT id FROM commerce.entitlement WHERE user_id = ?)
                    )
                    """,
                    userId);
            jdbc.update(
                    """
                    DELETE FROM commerce.entitlement_grant
                    WHERE entitlement_id IN (SELECT id FROM commerce.entitlement WHERE user_id = ?)
                    """,
                    userId);
            jdbc.update("DELETE FROM commerce.entitlement WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM classroom.class_member_history WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        createdUserIds.clear();

        for (UUID examId : createdExamIds) {
            examPort.withdraw(examId);
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        createdExamIds.clear();
    }

    @Test
    void permissionAndIdempotencyAndValidationRulesAreEnforced() throws Exception {
        String sfx = suffix();
        UUID classId = createClass("CLS-PERM-" + sfx, "Lớp kiểm quyền");
        UUID publishedExamId = serveExam("EX-PERM-" + sfx, "Đề kiểm quyền", "PUBLISHED", true);
        UUID discontinuedExamId = serveExam("EX-DISC-" + sfx, "Đề đã ngừng", "DISCONTINUED", true);

        // Missing CLASS.VIEW -> 403
        mockMvc.perform(get("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());

        // CLASS.VIEW can list assignments, but cannot assign or list assignable exams
        mockMvc.perform(get("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/classes/{classId}/assignable-exams", classId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isForbidden());

        // Missing Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examIds": ["%s"],
                                  "attemptsGranted": 2,
                                  "resultView": "FULL_SOLUTION"
                                }
                                """.formatted(publishedExamId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        // Invalid resultView -> 400 CLASS_EXAM_RESULT_VIEW_INVALID
        mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examIds": ["%s"],
                                  "attemptsGranted": 2,
                                  "resultView": "INVALID_VIEW"
                                }
                                """.formatted(publishedExamId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLASS_EXAM_RESULT_VIEW_INVALID"));

        // Discontinued exam -> 409 EXAM_NOT_PUBLISHED
        mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examIds": ["%s"],
                                  "attemptsGranted": 2,
                                  "resultView": "SCORE"
                                }
                                """.formatted(discontinuedExamId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_PUBLISHED"));

        // Idempotent replay with same key & body returns same assignment without duplicating
        UUID adminId = Ids.newId();
        String idemKey = UUID.randomUUID().toString();
        String payload = """
                {
                  "examIds": ["%s"],
                  "attemptsGranted": 2,
                  "resultView": "FULL_SOLUTION",
                  "note": "Đợt luyện tập 1"
                }
                """.formatted(publishedExamId);

        MvcResult first = mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(adminId, "admin." + sfx, Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAssignmentId = mapper.readTree(first.getResponse().getContentAsString())
                .path("assignments")
                .get(0)
                .path("id")
                .asText();

        MvcResult replay = mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(adminId, "admin." + sfx, Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String replayAssignmentId = mapper.readTree(replay.getResponse().getContentAsString())
                .path("assignments")
                .get(0)
                .path("id")
                .asText();
        assertThat(replayAssignmentId).isEqualTo(firstAssignmentId);

        // Same Idempotency-Key with different body -> 409 IDEMPOTENCY_KEY_REUSED
        mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(adminId, "admin." + sfx, Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examIds": ["%s"],
                                  "attemptsGranted": 5,
                                  "resultView": "SCORE"
                                }
                                """.formatted(publishedExamId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void assignAutoGrantTransferAndRevokePreserveUsedAttemptsUnderC15() throws Exception {
        String sfx = suffix();
        UUID sourceClassId = createClass("CLS-SRC-" + sfx, "Lớp HSA Mùa Xuân");
        UUID targetClassId = createClass("CLS-TGT-" + sfx, "Lớp HSA Chuyên Sâu");

        UUID student1 = createStudent("st1." + sfx);
        UUID student2 = createStudent("st2." + sfx);
        UUID student3 = createStudent("st3." + sfx);

        // Enroll student1 initially in sourceClass
        addMembers(sourceClassId, List.of(student1));

        // Publish an exam WITHOUT commercial storefront listing (listing == null) for sourceClass,
        // and an exam WITH listing for targetClass
        UUID nonStorefrontExamId = serveExam("EX-CLS-" + sfx, "Đề kiểm tra nội bộ lớp", "PUBLISHED", false);
        UUID targetExamId = serveExam("EX-TGT-" + sfx, "Đề chuyên sâu lớp đích", "PUBLISHED", true);

        // Assign targetExam to targetClass (autoGrantNewMembers = true)
        assignExams(targetClassId, List.of(targetExamId), 2, "SCORE_AND_ANSWERS", true, "Bài lớp đích");

        // Assign nonStorefrontExam to sourceClass (3 attempts, FULL_SOLUTION, autoGrantNewMembers = true)
        JsonNode assignRes = assignExams(
                sourceClassId, List.of(nonStorefrontExamId), 3, "FULL_SOLUTION", true, "Bài kiểm tra tuần 1");
        assertThat(assignRes.path("totalGrantsCreated").asInt()).isEqualTo(1);
        UUID assignmentId = UUID.fromString(assignRes.path("assignments").get(0).path("id").asText());

        // Student1 sees the CLASS card in /api/v1/me/exams with className & no commercial buy buttons,
        // and /api/v1/me/exam-entitlements/{id}/prepare reports grantSource.source = CLASS
        JsonNode s1Cards = myExams(student1);
        assertThat(s1Cards).hasSize(1);
        JsonNode s1Card = s1Cards.get(0);
        UUID s1EntitlementId = UUID.fromString(s1Card.path("entitlementId").asText());
        assertThat(s1Card.path("entitlementSource").asText()).isEqualTo("CLASS");
        assertThat(s1Card.path("classId").asText()).isEqualTo(sourceClassId.toString());
        assertThat(s1Card.path("className").asText()).isEqualTo("Lớp HSA Mùa Xuân");
        assertThat(s1Card.path("attemptsLeft").asInt()).isEqualTo(3);
        assertThat(s1Card.path("attemptsTotal").asInt()).isEqualTo(3);
        assertThat(s1Card.path("actions")).extracting(JsonNode::asText).containsExactly("START");

        mockMvc.perform(get("/api/v1/me/entitlements/{id}/prepare", s1EntitlementId)
                        .with(learner(student1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsLeft").value(3))
                .andExpect(jsonPath("$.grantSource.source").value("CLASS"));

        // Add student2 while autoGrantNewMembers = true -> automatically receives 3 attempts
        addMembers(sourceClassId, List.of(student2));
        JsonNode s2Cards = myExams(student2);
        assertThat(s2Cards).hasSize(1);
        assertThat(s2Cards.get(0).path("attemptsLeft").asInt()).isEqualTo(3);

        // Turn off autoGrantNewMembers via PATCH -> student3 added afterwards does NOT get granted
        mockMvc.perform(patch(
                                "/api/v1/admin/classes/{classId}/exam-assignments/{assignmentId}",
                                sourceClassId,
                                assignmentId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoGrantNewMembers\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoGrantNewMembers").value(false));

        addMembers(sourceClassId, List.of(student3));
        assertThat(myExams(student3)).isEmpty();

        // Student1 uses 1 attempt (reserve + confirmUse -> C15 must preserve this used attempt!)
        UUID attemptId = Ids.newId();
        entitlementPort.reserve(student1, s1EntitlementId, attemptId);
        entitlementPort.confirmUse(attemptId);

        // Transfer student2 from sourceClass -> targetClass:
        // unused attempts in sourceClass are revoked (empty 0/0 card omitted) AND targetClass exam is auto-granted!
        mockMvc.perform(post(
                                "/api/v1/admin/classes/{classId}/members/{userId}/transfer",
                                sourceClassId,
                                student2)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetClassId": "%s",
                                  "reason": "Chuyển sang lớp chuyên sâu"
                                }
                                """.formatted(targetClassId)))
                .andExpect(status().isOk());

        JsonNode s2AfterTransfer = myExams(student2);
        assertThat(s2AfterTransfer).hasSize(1);
        assertThat(s2AfterTransfer.get(0).path("classId").asText()).isEqualTo(targetClassId.toString());
        assertThat(s2AfterTransfer.get(0).path("className").asText()).isEqualTo("Lớp HSA Chuyên Sâu");
        assertThat(s2AfterTransfer.get(0).path("attemptsLeft").asInt()).isEqualTo(2);

        // Revoke assignment on sourceClass: student1 had 2 unused + 1 used -> 2 revoked, 1 used preserved!
        MvcResult revokeRes = mockMvc.perform(post(
                                "/api/v1/admin/classes/{classId}/exam-assignments/{assignmentId}/revoke",
                                sourceClassId,
                                assignmentId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Kết thúc đợt kiểm tra tuần 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsRevoked").value(2))
                .andExpect(jsonPath("$.assignment.status").value("REVOKED"))
                .andExpect(jsonPath("$.assignment.totalAttemptsUsed").value(1))
                .andExpect(jsonPath("$.assignment.totalAttemptsLeft").value(0))
                .andExpect(jsonPath("$.assignment.totalAttemptsRevoked").value(5))
                .andReturn();
        assertThat(revokeRes.getResponse().getStatus()).isEqualTo(200);

        // Student1 still sees their card (because they used 1 attempt -> netTotal = 1, hasHistory = true) with 0 left
        JsonNode s1AfterRevoke = myExams(student1);
        assertThat(s1AfterRevoke).hasSize(1);
        assertThat(s1AfterRevoke.get(0).path("attemptsLeft").asInt()).isZero();
        assertThat(s1AfterRevoke.get(0).path("attemptsTotal").asInt()).isEqualTo(1);
        assertThat(s1AfterRevoke.get(0).path("state").asText()).isEqualTo("EXHAUSTED");

        // Verify audit events were recorded in shared.outbox_event
        Integer assignedAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'action' = 'CLASS_EXAM_ASSIGNED' AND payload ->> 'targetId' = ?",
                Integer.class,
                assignmentId.toString());
        Integer updatedAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'action' = 'CLASS_EXAM_ASSIGNMENT_UPDATED' AND payload ->> 'targetId' = ?",
                Integer.class,
                assignmentId.toString());
        Integer revokedAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'action' = 'CLASS_EXAM_ASSIGNMENT_REVOKED' AND payload ->> 'targetId' = ?",
                Integer.class,
                assignmentId.toString());
        assertThat(assignedAudits).isEqualTo(1);
        assertThat(updatedAudits).isEqualTo(1);
        assertThat(revokedAudits).isEqualTo(1);
    }

    private UUID createClass(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\",\"name\":\"%s\"}".formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
        createdClassIds.add(id);
        return id;
    }

    private void addMembers(UUID classId, List<UUID> userIds) throws Exception {
        String idsJson = String.join(",", userIds.stream().map(id -> "\"" + id + "\"").toList());
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[%s]}".formatted(idsJson)))
                .andExpect(status().isOk());
    }

    private JsonNode assignExams(
            UUID classId,
            List<UUID> examIds,
            int attemptsGranted,
            String resultView,
            boolean autoGrant,
            String note)
            throws Exception {
        String idsJson = String.join(",", examIds.stream().map(id -> "\"" + id + "\"").toList());
        MvcResult res = mockMvc.perform(post("/api/v1/admin/classes/{classId}/exam-assignments", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examIds": [%s],
                                  "attemptsGranted": %d,
                                  "resultView": "%s",
                                  "autoGrantNewMembers": %s,
                                  "note": "%s"
                                }
                                """.formatted(idsJson, attemptsGranted, resultView, autoGrant, note)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode myExams(UUID userId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/me/exams").with(learner(userId)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).path("items");
    }

    private UUID createStudent(String username) {
        UUID userId = Ids.newId();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId,
                String.format("U%06d", seq),
                username,
                username.toLowerCase(),
                "Học viên " + username,
                username + "@test.local",
                username.toLowerCase() + "@test.local",
                now,
                userId,
                now,
                userId);
        createdUserIds.add(userId);
        return userId;
    }

    private RequestPostProcessor learner(UUID userId) {
        return testSecuritySupport.apply(
                new SimulatedPrincipal(userId, "learner." + userId, "STUDENT", false, List.of()));
    }

    private UUID serveExam(String code, String name, String status, boolean withListing) {
        UUID examId = Ids.newId();
        UUID kindItemId = catalog.listGroupItems("EXAM").stream()
                .filter(i -> "HSA".equalsIgnoreCase(i.code()))
                .findFirst()
                .orElseThrow()
                .id();
        Listing listing = withListing
                ? new Listing(
                        "Tóm tắt " + name,
                        null,
                        null,
                        50_000L,
                        2,
                        true,
                        true,
                        false,
                        null,
                        "SCORE_AND_ANSWERS",
                        "Mô tả",
                        null)
                : null;
        Part part = new Part(
                "part-1",
                1,
                "Định lượng",
                45,
                30,
                30,
                30,
                "ALL",
                null,
                BigDecimal.valueOf(50),
                List.of(),
                List.of(new Section("sec-1", "TOAN", 30, "FORBIDDEN")),
                List.of(),
                List.of(),
                List.of(new PartPath("path-1", 30, BigDecimal.valueOf(50), 45)),
                List.of());
        PublishedVersion version = new PublishedVersion(
                examId,
                1,
                "PUBLISHED",
                status,
                Instant.now(),
                code,
                name,
                kindItemId,
                "HSA",
                "PART",
                "P1",
                null,
                "STANDARD",
                "PER_PART",
                45,
                BigDecimal.valueOf(50),
                null,
                null,
                listing,
                List.of(part),
                List.of(),
                List.of(),
                List.of("path-1"),
                List.of("path-1"),
                "Làm bài nghiêm túc.");
        ServingPath servingPath = new ServingPath(
                "path-1",
                1,
                List.of(),
                30,
                BigDecimal.valueOf(50),
                45,
                List.of(new ServingPathPart("part-1", "Định lượng", 30, BigDecimal.valueOf(50), 45)),
                List.of());
        examPort.serve(new ServingExam(examId, status, version, List.of(servingPath)));
        createdExamIds.add(examId);
        return examId;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
