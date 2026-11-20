package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Shared base for AT-1 DS integration tests using one PostgreSQL DS database and mutable clock per
 * Spring context.
 */
@Import(AttemptTestFixtures.MutableClockConfig.class)
abstract class AbstractAttemptDsIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dsDatabase(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired protected ApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected MutableClock mutableClock;
    @Autowired protected EntitlementGrantService grantService;
    @Autowired protected PublishedExamPort publishedExamPort;
    @Autowired protected AttemptRepository attemptRepository;
    @Autowired protected AttemptPartRepository attemptPartRepository;
    @Autowired protected AttemptItemRepository attemptItemRepository;
    @Autowired protected AttemptEventRepository attemptEventRepository;

    protected final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void initDsAndClock() {
        AttemptTestFixtures.ensureDsBuilt(context);
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
    }

    @AfterEach
    void cleanupCreatedUsers() {
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
    }

    protected UUID createStudent(String label) {
        UUID id = AttemptTestFixtures.createStudentAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    protected UUID createManager(String label) {
        UUID id = AttemptTestFixtures.createManageAccount(jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    protected GrantOrderResult.Line grant(UUID userId, String examCode, int attempts, List<String> optionCodes) {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        return AttemptTestFixtures.grantExam(
                grantService,
                userId,
                examId,
                attempts,
                optionCodes,
                "SCORE_AND_ANSWERS",
                serving.version().versionNo());
    }

    protected MvcResult performPrepare(UUID userId, UUID entitlementId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/me/entitlements/{entitlementId}/prepare", entitlementId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    protected MvcResult performStart(
            UUID userId, String idempotencyKey, UUID entitlementId, PreChoiceInput preChoice)
            throws Exception {
        StartAttemptRequest payload = new StartAttemptRequest(entitlementId, preChoice);
        var req =
                post("/api/v1/me/attempts")
                        .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload));
        if (idempotencyKey != null) {
            req = req.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(req).andReturn();
    }

    protected MvcResult performGetState(UUID userId, UUID attemptId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/me/attempts/{attemptId}", attemptId)
                                .with(AttemptTestFixtures.asExamUser(userId, "learner")))
                .andReturn();
    }

    protected Attempt awaitTerminalStartState(UUID attemptId, AttemptState... targets)
            throws InterruptedException {
        Set<AttemptState> targetSet = EnumSet.noneOf(AttemptState.class);
        targetSet.addAll(List.of(targets));
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Attempt current = attemptRepository.findById(attemptId).orElse(null);
            if (current != null && targetSet.contains(current.getState())) {
                return current;
            }
            Thread.sleep(25);
        }
        Attempt latest = attemptRepository.findById(attemptId).orElse(null);
        throw new AssertionError(
                "Timed out waiting for attempt "
                        + attemptId
                        + " to reach "
                        + targetSet
                        + ", actual="
                        + (latest == null ? "MISSING" : latest.getState()));
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected int countEntitlementUsage(UUID attemptId, String state) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commerce.entitlement_usage WHERE attempt_id = ? AND state = ?",
                        Integer.class,
                        attemptId,
                        state);
        assertThat(count).isNotNull();
        return count;
    }

    protected int countEntitlementUsageForGrant(UUID grantId, String state) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commerce.entitlement_usage WHERE grant_id = ? AND state = ?",
                        Integer.class,
                        grantId,
                        state);
        assertThat(count).isNotNull();
        return count;
    }
}
