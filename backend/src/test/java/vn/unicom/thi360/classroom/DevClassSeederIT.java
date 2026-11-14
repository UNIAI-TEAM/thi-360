package vn.unicom.thi360.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.classroom.application.DevClassSeeder;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.outbox.OutboxEventRepository;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"api", "worker", "test", "dev"})
@ExtendWith(OutputCaptureExtension.class)
class DevClassSeederIT extends AbstractKeycloakIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DevClassSeeder devClassSeeder;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private ClassMemberRepository classMemberRepository;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private AuditQuery auditQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void devProfile_seedsClassesIdempotently_concurrently_andSkipsInactiveClass(CapturedOutput output)
            throws Exception {
        OpsSession ops = loginAsOpsAdmin();
        assertSeededClassesViaApi(ops.accessCookie());

        // Sequential rerun must be idempotent
        Long classCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE deleted_at IS NULL", Long.class);
        Long memberCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member", Long.class);
        Long historyCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class);
        long outboxBefore = outboxEventRepository.count();

        devClassSeeder.seed();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE deleted_at IS NULL", Long.class))
                .isEqualTo(classCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member", Long.class))
                .isEqualTo(memberCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class))
                .isEqualTo(historyCountBefore);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBefore);

        while (outboxRelay.relayBatch() > 0) {
            // Drain outbox events
        }
        assertSeededClassAuditEvents();

        // B1-001: Concurrent runs from empty class state must succeed cleanly without duplicate rows or exceptions
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM classroom.class_member_history WHERE class_id IN (SELECT id FROM classroom.class_room WHERE code IN ('LOP-A', 'LOP-B'))");
            jdbcTemplate.update(
                    "DELETE FROM classroom.class_member WHERE class_id IN (SELECT id FROM classroom.class_room WHERE code IN ('LOP-A', 'LOP-B'))");
            jdbcTemplate.update(
                    "DELETE FROM classroom.class_room WHERE code IN ('LOP-A', 'LOP-B')");
        });

        long outboxBeforeConcurrent = outboxEventRepository.count();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    devClassSeeder.seed();
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE code IN ('LOP-A', 'LOP-B') AND deleted_at IS NULL",
                Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member", Long.class)).isEqualTo(4L);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBeforeConcurrent + 4L);

        // B1-002: Existing INACTIVE seed class must NOT receive members on re-seed (logged at INFO and skipped)
        ClassRoom lopA = classRoomRepository.findByCodeNormalizedAndDeletedAtIsNull("lop-a").orElseThrow();
        UUID student01Id = userDirectory.findActiveByUsername("student01").orElseThrow().id();

        mockMvc.perform(delete("/api/v1/admin/classes/{id}/members/{userId}", lopA.getId(), student01Id)
                        .cookie(ops.accessCookie(), ops.xsrfCookie())
                        .header("Origin", "http://localhost:3001")
                        .header("X-XSRF-TOKEN", ops.xsrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test_remove\"}"))
                .andExpect(status().isNoContent());

        ClassRoom lopABeforeDeactivate = classRoomRepository.findByIdAndDeletedAtIsNull(lopA.getId()).orElseThrow();
        MvcResult deactivateRes = mockMvc.perform(patch("/api/v1/admin/classes/{id}/status", lopA.getId())
                        .cookie(ops.accessCookie(), ops.xsrfCookie())
                        .header("Origin", "http://localhost:3001")
                        .header("X-XSRF-TOKEN", ops.xsrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"test_deactivate\",\"version\":"
                                + lopABeforeDeactivate.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn();
        long inactiveVersion = objectMapper.readTree(deactivateRes.getResponse().getContentAsString())
                .path("version").asLong();

        long outboxBeforeInactiveSeed = outboxEventRepository.count();
        long historyBeforeInactiveSeed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class);

        devClassSeeder.seed();

        ClassRoom reloadedLopA = classRoomRepository.findByIdAndDeletedAtIsNull(lopA.getId()).orElseThrow();
        assertThat(reloadedLopA.getStatus()).isEqualTo(ClassStatus.INACTIVE);
        assertThat(classMemberRepository.existsByIdClassIdAndIdUserId(lopA.getId(), student01Id)).isFalse();
        assertThat(classMemberRepository.findActiveUserIdsByClassId(lopA.getId())).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class))
                .isEqualTo(historyBeforeInactiveSeed);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBeforeInactiveSeed);
        assertThat(output.getOut()).contains("Skipping dev seed member assignment for inactive class");

        // Restore LOP-A active state and student01 membership so shared DB stays clean
        mockMvc.perform(patch("/api/v1/admin/classes/{id}/status", lopA.getId())
                        .cookie(ops.accessCookie(), ops.xsrfCookie())
                        .header("Origin", "http://localhost:3001")
                        .header("X-XSRF-TOKEN", ops.xsrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"version\":" + inactiveVersion + "}"))
                .andExpect(status().isOk());
        devClassSeeder.seed();
    }

    private record OpsSession(Cookie accessCookie, Cookie xsrfCookie) {}

    private OpsSession loginAsOpsAdmin() throws Exception {
        MvcResult opsLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.155")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"ops.admin\",\"password\":\"Thi360@dev\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie opsAccessCookie = opsLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie opsXsrfCookie = opsLogin.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(opsAccessCookie).isNotNull();
        assertThat(opsXsrfCookie).isNotNull();
        return new OpsSession(opsAccessCookie, opsXsrfCookie);
    }

    private void assertSeededClassesViaApi(Cookie opsAccessCookie) throws Exception {
        MvcResult classesResult = mockMvc.perform(get("/api/v1/admin/classes")
                        .param("size", "100")
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode classesBody = objectMapper.readTree(classesResult.getResponse().getContentAsString());
        Map<String, JsonNode> byCode = new HashMap<>();
        for (JsonNode item : classesBody.path("items")) {
            byCode.put(item.path("code").asText(), item);
        }

        assertThat(byCode).containsKeys("LOP-A", "LOP-B");
        JsonNode lopA = byCode.get("LOP-A");
        JsonNode lopB = byCode.get("LOP-B");
        assertThat(lopA.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(lopA.path("memberCount").asLong()).isEqualTo(2L);
        assertThat(lopA.path("ownerUsername").asText()).isEqualTo("ops.admin");
        assertThat(lopB.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(lopB.path("memberCount").asLong()).isEqualTo(2L);
        assertThat(lopB.path("ownerUsername").asText()).isEqualTo("ops.admin");

        assertClassMembers(opsAccessCookie, lopA.path("id").asText(), List.of("student01", "student02"));
        assertClassMembers(opsAccessCookie, lopB.path("id").asText(), List.of("student03", "student04"));

        UUID student01Id = userDirectory.findActiveByUsername("student01").orElseThrow().id();
        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", student01Id)
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentClasses[0].classCode").value("LOP-A"))
                .andExpect(jsonPath("$.history[0].classCode").value("LOP-A"))
                .andExpect(jsonPath("$.history[0].action").value("JOIN"))
                .andExpect(jsonPath("$.history[0].reason").value("dev_seed"));
    }

    private void assertClassMembers(Cookie opsAccessCookie, String classId, List<String> expectedUsernames)
            throws Exception {
        MvcResult membersResult = mockMvc.perform(get("/api/v1/admin/classes/{id}/members", classId)
                        .param("size", "100")
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode membersBody = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        List<String> usernames = new ArrayList<>();
        for (JsonNode item : membersBody.path("items")) {
            usernames.add(item.path("username").asText());
        }
        assertThat(usernames).containsExactlyInAnyOrderElementsOf(expectedUsernames);
    }

    private void assertSeededClassAuditEvents() {
        for (String code : List.of("LOP-A", "LOP-B")) {
            String classId = jdbcTemplate.queryForObject(
                    "SELECT id::text FROM classroom.class_room WHERE code = ? AND deleted_at IS NULL",
                    String.class,
                    code);
            assertThat(classId).isNotBlank();
            List<AuditEventView> events = auditQuery
                    .findByTarget("CLASS", classId, new PageQuery(0, 20, Sort.unsorted()))
                    .items();
            assertThat(events)
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("CLASSROOM");
                        assertThat(ev.action()).isEqualTo("CLASS_CREATED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.reason()).isEqualTo("dev_seed");
                    })
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("CLASSROOM");
                        assertThat(ev.action()).isEqualTo("CLASS_MEMBER_ADDED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.reason()).isEqualTo("dev_seed");
                    });
        }
    }
}
