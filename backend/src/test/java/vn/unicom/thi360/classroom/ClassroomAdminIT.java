package vn.unicom.thi360.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.classroom.api.AddClassMembersRequest;
import vn.unicom.thi360.classroom.api.TransferClassMemberRequest;
import vn.unicom.thi360.classroom.application.ClassService;
import vn.unicom.thi360.classroom.application.UserDeletedClassCleanupListener;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

class ClassroomAdminIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private ClassService classService;

    @Autowired
    private UserDeletedClassCleanupListener userDeletedClassCleanupListener;

    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdPlatformAdminIds = new ArrayList<>();
    @AfterEach
    void cleanup() {
        for (UUID classId : createdClassIds) {
            jdbcTemplate.update(
                    "DELETE FROM classroom.class_member_history WHERE class_id = ? OR related_class_id = ?",
                    classId,
                    classId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbcTemplate.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        createdClassIds.clear();

        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM classroom.class_member_history WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        createdUserIds.clear();
        for (UUID platformAdminId : createdPlatformAdminIds) {
            jdbcTemplate.update("DELETE FROM identity.platform_admin WHERE id = ?", platformAdminId);
        }
        createdPlatformAdminIds.clear();
    }

    @Test
    void everyEndpoint_returns403WhenMissingRequiredPermission() throws Exception {
        UUID anyClassId = Ids.newId();
        UUID anyUserId = Ids.newId();

        // Read endpoints require CLASS.VIEW
        mockMvc.perform(get("/api/v1/admin/classes").with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/classes/{id}", anyClassId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", anyClassId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", anyUserId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Write endpoints require CLASS.MODIFY (CLASS.VIEW alone must be rejected with 403)
        mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "FORBIDDEN-CLS",
                                  "name": "Forbidden Class"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/admin/classes/{id}", anyClassId)
                        .with(asUser(Permissions.CLASS_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/admin/classes/{id}/status", anyClassId)
                        .with(asUser(Permissions.CLASS_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/admin/classes/{id}", anyClassId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", anyClassId)
                        .with(asUser(Permissions.CLASS_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(anyUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/admin/classes/{id}/members/{userId}", anyClassId, anyUserId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members/{userId}/transfer", anyClassId, anyUserId)
                        .with(asUser(Permissions.CLASS_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetClassId\":\"%s\"}".formatted(Ids.newId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createAndUpdateClass_enforcesCodeRuleAndUniquenessAmongNonDeleted() throws Exception {
        String sfx = suffix();
        List<String> invalidCodes = List.of(
                "A",
                "-LOP",
                ".LOP",
                "LOP A",
                "LOP@A",
                "A123456789012345678901234567890" // 31 chars
        );
        for (String badCode : invalidCodes) {
            mockMvc.perform(post("/api/v1/admin/classes")
                            .with(asUser(Permissions.CLASS_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "Invalid Code Class"
                                    }
                                    """.formatted(badCode)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value("CLASS_CODE_INVALID"));
        }

        // Valid code with mixed case and allowed symbols (._-)
        String code = "Lop_" + sfx + ".A-1";
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "  %s  ",
                                  "name": "Lớp Toán %s",
                                  "description": "Mô tả ban đầu"
                                }
                                """.formatted(code, sfx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID classId = UUID.fromString(createdJson.get("id").asText());
        createdClassIds.add(classId);

        // Duplicate code (case-insensitive) rejected with 409 CLASS_CODE_TAKEN
        mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Trùng mã"
                                }
                                """.formatted(code.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_CODE_TAKEN"));

        // Stale version on PATCH -> 412 VERSION_CONFLICT
        mockMvc.perform(patch("/api/v1/admin/classes/{id}", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"version\":99}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Soft delete -> code becomes reusable
        mockMvc.perform(delete("/api/v1/admin/classes/{id}", classId)
                        .param("reason", "Hết khóa")
                        .with(asUser(Permissions.CLASS_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/classes/{id}", classId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isNotFound());

        String reusedCode = code.toLowerCase();
        MvcResult recreatedRes = mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Lớp tái sử dụng mã"
                                }
                                """.formatted(reusedCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(reusedCode))
                .andReturn();
        createdClassIds.add(UUID.fromString(
                objectMapper.readTree(recreatedRes.getResponse().getContentAsString()).get("id").asText()));
    }

    @Test
    void ac01_3_addRemoveTransferMembers_preservesHistoryOrderAndLeavesAccountsUntouched() throws Exception {
        String sfx = suffix();
        UUID ownerId = insertUser("owner." + sfx, "Phụ trách " + sfx);
        UUID student1 = insertUser("sv1." + sfx, "Học viên Một " + sfx);
        UUID student2 = insertUser("sv2." + sfx, "Học viên Hai " + sfx);

        UUID classA = createClassViaApi("LOP-A-" + sfx, "Lớp A " + sfx, ownerId);
        UUID classB = createClassViaApi("LOP-B-" + sfx, "Lớp B " + sfx, null);
        UUID classC = createClassViaApi("LOP-C-" + sfx, "Lớp C " + sfx, null);

        // Add both students to class A, and student1 also to class C (multi-class membership)
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classA)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\",\"%s\"]}".formatted(student1, student2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0));

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classC)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(1));

        // Transfer student1 from class A -> class B in one transaction
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members/{userId}/transfer", classA, student1)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetClassId": "%s",
                                  "reason": "Chuyển lịch học"
                                }
                                """.formatted(classB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceClassId").value(classA.toString()))
                .andExpect(jsonPath("$.targetClassId").value(classB.toString()))
                .andExpect(jsonPath("$.userId").value(student1.toString()));

        // Remove student2 from class A
        mockMvc.perform(delete("/api/v1/admin/classes/{id}/members/{userId}", classA, student2)
                        .param("reason", "Hoàn thành khóa")
                        .with(asUser(Permissions.CLASS_MODIFY)))
                .andExpect(status().isNoContent());

        // Check members of class A (empty), class B (student1), class C (student1)
        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", classA)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", classB)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(student1.toString()));

        // Verify GET /api/v1/admin/users/{id}/classes for student1:
        // currentClasses = [classB, classC] (multi-class), history newest-first:
        // TRANSFER_IN (B, related=A), TRANSFER_OUT (A, related=B), JOIN (C), JOIN (A)
        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", student1)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student1.toString()))
                .andExpect(jsonPath("$.currentClasses", hasSize(2)))
                .andExpect(jsonPath("$.currentClasses[*].classId",
                        containsInAnyOrder(classB.toString(), classC.toString())))
                .andExpect(jsonPath("$.history", hasSize(4)))
                .andExpect(jsonPath("$.history[0].action").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.history[0].classId").value(classB.toString()))
                .andExpect(jsonPath("$.history[0].relatedClassId").value(classA.toString()))
                .andExpect(jsonPath("$.history[0].reason").value("Chuyển lịch học"))
                .andExpect(jsonPath("$.history[1].action").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.history[1].classId").value(classA.toString()))
                .andExpect(jsonPath("$.history[1].relatedClassId").value(classB.toString()))
                .andExpect(jsonPath("$.history[1].reason").value("Chuyển lịch học"))
                .andExpect(jsonPath("$.history[2].action").value("JOIN"))
                .andExpect(jsonPath("$.history[2].classId").value(classC.toString()))
                .andExpect(jsonPath("$.history[3].action").value("JOIN"))
                .andExpect(jsonPath("$.history[3].classId").value(classA.toString()));

        // Verify GET /api/v1/admin/users/{id}/classes for student2:
        // currentClasses = [], history: [LEAVE (A), JOIN (A)]
        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", student2)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentClasses", hasSize(0)))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].action").value("LEAVE"))
                .andExpect(jsonPath("$.history[0].classId").value(classA.toString()))
                .andExpect(jsonPath("$.history[0].reason").value("Hoàn thành khóa"))
                .andExpect(jsonPath("$.history[1].action").value("JOIN"));

        // Soft-delete class B -> student1 account remains ACTIVE & untouched, currentClasses drops deleted class B
        mockMvc.perform(delete("/api/v1/admin/classes/{id}", classB)
                        .param("reason", "Đóng lớp B")
                        .with(asUser(Permissions.CLASS_MODIFY)))
                .andExpect(status().isNoContent());

        Short sv1Status = jdbcTemplate.queryForObject(
                "SELECT status FROM identity.user_account WHERE id = ? AND deleted_at IS NULL",
                Short.class,
                student1);
        assertThat(sv1Status).isEqualTo((short) 2);

        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", student1)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentClasses", hasSize(1)))
                .andExpect(jsonPath("$.currentClasses[0].classId").value(classC.toString()));

        // Drain outbox & verify audit trail includes class actions with reason
        while (outboxRelay.relayBatch() > 0) {
            // drain outbox
        }
        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM audit.audit_event WHERE target_id IN (?, ?, ?) ORDER BY occurred_at ASC",
                String.class,
                classA.toString(),
                classB.toString(),
                classC.toString());
        assertThat(actions).contains(
                "CLASS_CREATED",
                "CLASS_MEMBER_ADDED",
                "CLASS_MEMBER_TRANSFERRED",
                "CLASS_MEMBER_REMOVED",
                "CLASS_DELETED");
    }

    @Test
    void addMembers_skipsExistingAndInRequestDuplicatesWithoutError() throws Exception {
        String sfx = suffix();
        UUID student1 = insertUser("dup1." + sfx, "SV Một " + sfx);
        UUID student2 = insertUser("dup2." + sfx, "SV Hai " + sfx);
        UUID classId = createClassViaApi("DUP-" + sfx, "Lớp Trùng " + sfx, null);

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0));

        // Send [student1 (already member), student2, student2 (duplicate in same payload)]
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(student1, student2, student2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(1))
                .andExpect(jsonPath("$.addedUserIds[0]").value(student2.toString()))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.skippedUserIds", hasSize(2)));

        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void transferToInactiveClass_returns409ClassInactive_andLeavesOldClassUnchanged() throws Exception {
        String sfx = suffix();
        UUID student = insertUser("inact." + sfx, "SV Chuyển " + sfx);
        UUID activeClass = createClassViaApi("ACT-" + sfx, "Lớp Hoạt Động", null);
        UUID inactiveClass = createClassViaApi("INA-" + sfx, "Lớp Ngừng", null);

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", activeClass)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student)))
                .andExpect(status().isOk());

        // Deactivate target class
        mockMvc.perform(patch("/api/v1/admin/classes/{id}/status", inactiveClass)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INACTIVE",
                                  "reason": "Tạm dừng tuyển sinh",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Direct add into INACTIVE class blocked with 409 CLASS_INACTIVE
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", inactiveClass)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_INACTIVE"));

        // Transfer into INACTIVE class blocked with 409 CLASS_INACTIVE
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members/{userId}/transfer", activeClass, student)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetClassId": "%s",
                                  "reason": "Thử chuyển sang lớp ngừng"
                                }
                                """.formatted(inactiveClass)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_INACTIVE"));

        // Verify old class still has student, inactive class still empty, no TRANSFER rows written
        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", activeClass)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(student.toString()));

        mockMvc.perform(get("/api/v1/admin/classes/{id}/members", inactiveClass)
                        .with(asUser(Permissions.CLASS_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        Integer transferHistoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action IN (3, 4)",
                Integer.class,
                student);
        assertThat(transferHistoryCount).isZero();
    }

    @Test
    void userDeletedOutbox_removesMembershipsAndWritesLeaveHistoryIdempotently() throws Exception {
        String sfx = suffix();
        UUID student = insertUser("del." + sfx, "SV Xóa " + sfx);
        UUID actorId = insertUser("deleter." + sfx, "Admin Xóa " + sfx);
        jdbcTemplate.update(
                "UPDATE identity.user_account SET delete_reason = ? WHERE id = ?",
                "Người dùng yêu cầu xóa",
                student);
        UUID class1 = createClassViaApi("DEL1-" + sfx, "Lớp 1", null);
        UUID class2 = createClassViaApi("DEL2-" + sfx, "Lớp 2", null);

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", class1)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", class2)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(student)))
                .andExpect(status().isOk());

        // Drain any earlier outbox backlog first
        while (outboxRelay.relayBatch() > 0) {
            // drain
        }

        Instant deletedAt = Instant.now();
        UserDeleted deletedPayload = new UserDeleted(student, "del." + sfx, deletedAt, actorId);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> outboxPublisher.publish(UserDeleted.OUTBOX_TOPIC, deletedPayload));

        // Drain outbox backlog (UserDeleted + generated CLASS_MEMBER_REMOVED audit events)
        while (outboxRelay.relayBatch() > 0) {
            // drain
        }

        Integer remainingMemberships = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                Integer.class,
                student);
        assertThat(remainingMemberships).isZero();

        Integer leaveRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action = 2",
                Integer.class,
                student);
        assertThat(leaveRows).isEqualTo(2);

        List<String> removedReasons = jdbcTemplate.queryForList(
                "SELECT reason FROM audit.audit_event WHERE action = 'CLASS_MEMBER_REMOVED' AND target_id IN (?, ?) ORDER BY target_id ASC",
                String.class,
                class1.toString(),
                class2.toString());
        assertThat(removedReasons).containsExactly("Người dùng yêu cầu xóa", "Người dùng yêu cầu xóa");

        List<UUID> removedActors = jdbcTemplate.queryForList(
                "SELECT actor_id FROM audit.audit_event WHERE action = 'CLASS_MEMBER_REMOVED' AND target_id IN (?, ?)",
                UUID.class,
                class1.toString(),
                class2.toString());
        assertThat(removedActors).containsExactly(actorId, actorId);

        // Re-delivering the same UserDeleted event must be idempotent (no duplicate LEAVE or audit rows)
        eventPublisher.publishEvent(deletedPayload);
        while (outboxRelay.relayBatch() > 0) {
            // drain
        }
        Integer leaveRowsAfterReplay = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action = 2",
                Integer.class,
                student);
        assertThat(leaveRowsAfterReplay).isEqualTo(2);

        Integer auditRowsAfterReplay = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE action = 'CLASS_MEMBER_REMOVED' AND target_id IN (?, ?)",
                Integer.class,
                class1.toString(),
                class2.toString());
        assertThat(auditRowsAfterReplay).isEqualTo(2);
    }

    @Test
    void userDeletedCleanup_serializesWithAddAndTransferWithoutOrphanMembership() throws Exception {
        String sfx = suffix();
        UUID actorId = insertUser("conc.actor." + sfx, "Admin Conc");
        UUID studentA = insertUser("conc.a." + sfx, "SV Conc A");
        UUID studentB = insertUser("conc.b." + sfx, "SV Conc B");
        UUID studentC = insertUser("conc.c." + sfx, "SV Conc C");
        UUID class1 = createClassViaApi("CONC1-" + sfx, "Lớp Conc 1", null);
        UUID class2 = createClassViaApi("CONC2-" + sfx, "Lớp Conc 2", null);
        UUID class3 = createClassViaApi("CONC3-" + sfx, "Lớp Conc 3", null);

        while (outboxRelay.relayBatch() > 0) {
            // drain backlog
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Case A: addMembers holds advisory lock and inserts membership before commit while UserDeleted relay waits.
            CountDownLatch addInsertedLatch = new CountDownLatch(1);
            Future<?> addFuture = pool.submit(() -> {
                setTestSecurityContext(actorId, "conc.actor." + sfx, Permissions.CLASS_MODIFY);
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        classService.addMembers(class1, new AddClassMembersRequest(List.of(studentA)));
                        addInsertedLatch.countDown();
                        awaitUngrantedAdvisoryLock();
                    });
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            Future<?> relayFuture = pool.submit(() -> {
                try {
                    assertThat(addInsertedLatch.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
                Instant deletedAt = Instant.now();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbcTemplate.update(
                            "UPDATE identity.user_account SET deleted_at = ?, status = 3 WHERE id = ?",
                            java.sql.Timestamp.from(deletedAt),
                            studentA);
                    outboxPublisher.publish(
                            UserDeleted.OUTBOX_TOPIC,
                            new UserDeleted(studentA, "conc.a." + sfx, deletedAt, actorId));
                });
                while (outboxRelay.relayBatch() > 0) {
                    // relay UserDeleted + resulting audit events
                }
            });

            addFuture.get(15, TimeUnit.SECONDS);
            relayFuture.get(15, TimeUnit.SECONDS);
            while (outboxRelay.relayBatch() > 0) {
                // drain any remaining audit events
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                    Integer.class,
                    studentA)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action = 2",
                    Integer.class,
                    studentA)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE action = 'CLASS_MEMBER_REMOVED' AND target_id = ?",
                    Integer.class,
                    class1.toString())).isEqualTo(1);

            // Case B: UserDeleted cleanup holds advisory lock while addMembers waits; addMembers re-checks deleted status under lock and rejects.
            mockMvc.perform(post("/api/v1/admin/classes/{id}/members", class2)
                            .with(asUser(Permissions.CLASS_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userIds\":[\"%s\"]}".formatted(studentB)))
                    .andExpect(status().isOk());

            Instant deletedAtB = Instant.now();
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET deleted_at = ?, status = 3 WHERE id = ?",
                    java.sql.Timestamp.from(deletedAtB),
                    studentB);

            CountDownLatch cleanupLockedLatch = new CountDownLatch(1);
            UserDeleted deletedB = new UserDeleted(studentB, "conc.b." + sfx, deletedAtB, actorId);

            Future<?> cleanupFuture = pool.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        userDeletedClassCleanupListener.onUserDeleted(deletedB);
                        cleanupLockedLatch.countDown();
                        awaitUngrantedAdvisoryLock();
                    }));

            Future<Throwable> blockedAddFuture = pool.submit(() -> {
                try {
                    assertThat(cleanupLockedLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    setTestSecurityContext(actorId, "conc.actor." + sfx, Permissions.CLASS_MODIFY);
                    classService.addMembers(class3, new AddClassMembersRequest(List.of(studentB)));
                    return null;
                } catch (Throwable ex) {
                    return ex;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            cleanupFuture.get(15, TimeUnit.SECONDS);
            Throwable addError = blockedAddFuture.get(15, TimeUnit.SECONDS);
            assertThat(addError).isInstanceOf(NotFoundException.class);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                    Integer.class,
                    studentB)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action = 2",
                    Integer.class,
                    studentB)).isEqualTo(1);

            // Case C: UserDeleted cleanup holds advisory lock while transferMember waits; transferMember rejects after cleanup commits.
            mockMvc.perform(post("/api/v1/admin/classes/{id}/members", class2)
                            .with(asUser(Permissions.CLASS_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userIds\":[\"%s\"]}".formatted(studentC)))
                    .andExpect(status().isOk());

            Instant deletedAtC = Instant.now();
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET deleted_at = ?, status = 3 WHERE id = ?",
                    java.sql.Timestamp.from(deletedAtC),
                    studentC);

            CountDownLatch cleanupLockedCLatch = new CountDownLatch(1);
            UserDeleted deletedC = new UserDeleted(studentC, "conc.c." + sfx, deletedAtC, actorId);

            Future<?> cleanupCFuture = pool.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        userDeletedClassCleanupListener.onUserDeleted(deletedC);
                        cleanupLockedCLatch.countDown();
                        awaitUngrantedAdvisoryLock();
                    }));

            Future<Throwable> blockedTransferFuture = pool.submit(() -> {
                try {
                    assertThat(cleanupLockedCLatch.await(5, TimeUnit.SECONDS)).isTrue();
                    setTestSecurityContext(actorId, "conc.actor." + sfx, Permissions.CLASS_MODIFY);
                    classService.transferMember(
                            class2,
                            studentC,
                            new TransferClassMemberRequest(class3, "Chuyển lớp"));
                    return null;
                } catch (Throwable ex) {
                    return ex;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            cleanupCFuture.get(15, TimeUnit.SECONDS);
            Throwable transferError = blockedTransferFuture.get(15, TimeUnit.SECONDS);
            assertThat(transferError).isInstanceOf(NotFoundException.class);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                    Integer.class,
                    studentC)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM classroom.class_member_history WHERE user_id = ? AND action IN (3, 4)",
                    Integer.class,
                    studentC)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    private void awaitUngrantedAdvisoryLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Timed out waiting for concurrent advisory lock contention");
    }

    private static void setTestSecurityContext(UUID userId, String username, String... permissions) {
        CurrentUser currentUser = new CurrentUser(
                userId,
                username,
                Realm.APP,
                Access.MANAGE,
                PermissionSet.ofCodes(List.of(permissions)));
        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(currentUser, "test-sid"));
    }

    @Test
    void inactiveAndPlatformAdminUsers_cannotBeAddedAsMembersOrSetAsOwner() throws Exception {
        String sfx = suffix();
        UUID activeOwner = insertUser("act.own." + sfx, "Active Owner");
        UUID inactiveUser = insertUserWithStatus("ina.usr." + sfx, "Inactive User", (short) 3);
        UUID platformAdminId = insertPlatformAdmin("plat." + sfx);
        UUID classId = createClassViaApi("OWN-" + sfx, "Lớp kiểm tra user", activeOwner);

        // Add INACTIVE or PLATFORM_ADMIN user as member -> 404 NOT_FOUND
        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(inactiveUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/classes/{id}/members", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"%s\"]}".formatted(platformAdminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Create class with INACTIVE or PLATFORM_ADMIN owner -> 404 NOT_FOUND
        mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OWN-INA-%s",
                                  "name": "Inactive Owner Class",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(sfx, inactiveUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OWN-PLT-%s",
                                  "name": "Platform Owner Class",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(sfx, platformAdminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Update class owner to INACTIVE or PLATFORM_ADMIN -> 404 NOT_FOUND
        mockMvc.perform(patch("/api/v1/admin/classes/{id}", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": "%s",
                                  "version": 0
                                }
                                """.formatted(inactiveUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/admin/classes/{id}", classId)
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": "%s",
                                  "version": 0
                                }
                                """.formatted(platformAdminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private UUID createClassViaApi(String code, String name, UUID ownerUserId) throws Exception {
        String body = ownerUserId == null
                ? "{\"code\":\"%s\",\"name\":\"%s\"}".formatted(code, name)
                : "{\"code\":\"%s\",\"name\":\"%s\",\"ownerUserId\":\"%s\"}".formatted(code, name, ownerUserId);
        MvcResult result = mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(Permissions.CLASS_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdClassIds.add(id);
        return id;
    }

    private UUID insertUser(String username, String fullName) {
        return insertUserWithStatus(username, fullName, (short) 2);
    }

    private UUID insertUserWithStatus(String username, String fullName, short statusCode) {
        UUID id = Ids.newId();
        String code = "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbcTemplate.update(
                """
                INSERT INTO identity.user_account (
                    id, code, username, username_normalized, full_name, source, status, must_change_password
                ) VALUES (?, ?, ?, ?, ?, 1, ?, false)
                """,
                id,
                code,
                username,
                username.trim().toLowerCase(),
                fullName,
                statusCode);
        createdUserIds.add(id);
        return id;
    }

    private UUID insertPlatformAdmin(String username) {
        UUID id = Ids.newId();
        jdbcTemplate.update(
                "INSERT INTO identity.platform_admin (id, username) VALUES (?, ?)",
                id,
                username);
        createdPlatformAdminIds.add(id);
        return id;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
