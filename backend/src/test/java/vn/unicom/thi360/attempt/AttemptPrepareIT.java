package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/** Integration tests for {@code GET /api/v1/me/entitlements/{entitlementId}/prepare} (AC-11, AC-12). */
class AttemptPrepareIT extends AbstractAttemptDsIT {

    @org.springframework.beans.factory.annotation.Autowired
    private EntitlementPort entitlementPort;

    @Test
    void prepareReturnsStartingAttempt() throws Exception {
        UUID userId = createStudent("prep-start");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();

        UUID attemptId = Ids.newId();
        entitlementPort.reserve(userId, line.entitlementId(), attemptId);
        Attempt starting =
                Attempt.start(
                        attemptId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        12345L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "key-prep-start",
                        mutableClock.instant());
        attemptRepository.saveAndFlush(starting);

        MvcResult res = performPrepare(userId, line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        JsonNode body = json(res);
        assertThat(body.path("openAttempt").path("attemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(body.path("openAttempt").path("state").asText()).isEqualTo("STARTING");
        assertThat(body.path("attemptsLeft").asInt()).isEqualTo(1);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(body);
    }

    @Test
    void prepareMatchesPublishedPaths() throws Exception {
        UUID userId = createStudent("prep-paths");

        // 1. HSA FULL: non-blank frozen instructions (unchanged after draft edit), grantSource, parts from paths[], afterStartChoices
        GrantOrderResult.Line hsaLine = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());
        String originalDraftInstructions =
                jdbc.queryForObject(
                        "SELECT instructions FROM exam.exam WHERE id = ?",
                        String.class,
                        hsaLine.examId());
        String originalVersionConfig =
                jdbc.queryForObject(
                        "SELECT config::text FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                        String.class,
                        hsaLine.examId());
        try {
            jdbc.update(
                    "UPDATE exam.exam_version SET config = jsonb_set(config, '{instructions}', to_jsonb(?::text), true) "
                            + "WHERE exam_id = ? AND version_no = 1",
                    "<p>Frozen exam rules v1</p>",
                    hsaLine.examId());
            jdbc.update(
                    "UPDATE exam.exam SET instructions = ? WHERE id = ?",
                    "<p>DRAFT CHANGED SHOULD NOT AFFECT PREPARE</p>",
                    hsaLine.examId());

            MvcResult hsaRes = performPrepare(userId, hsaLine.entitlementId());
            assertThat(hsaRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode hsa = json(hsaRes);
            ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(hsa);

            ServingExam hsaServing = publishedExamPort.findServing(hsaLine.examId()).orElseThrow();
            ServingPath hsaPath = hsaServing.servingPaths().getFirst();

            assertThat(hsa.path("examCode").asText()).isEqualTo("DS-HSA-FULL-MINI");
            assertThat(hsa.path("examKind").path("code").asText()).isEqualTo("HSA");
            assertThat(hsa.path("grantSource").path("source").asText()).isEqualTo("PURCHASE");
            assertThat(hsa.path("grantSource").path("orderCode").asText()).startsWith("ORD-");
            assertThat(hsa.path("instructions").asText()).isEqualTo("<p>Frozen exam rules v1</p>");
            assertThat(hsa.path("instructions").asText()).doesNotContain("DRAFT CHANGED");
            assertThat(hsa.path("navigationMode").asText()).isEqualTo("SEQUENTIAL");
            assertThat(hsa.path("preChoice").isNull()).isTrue();
            assertThat(hsa.path("parts")).hasSize(hsaPath.parts().size());
            for (int i = 0; i < hsaPath.parts().size(); i++) {
                JsonNode partJson = hsa.path("parts").get(i);
                var expectedPart = hsaPath.parts().get(i);
                assertThat(partJson.path("partCode").asText()).isEqualTo(expectedPart.partId());
                assertThat(partJson.path("questionCount").asInt()).isEqualTo(expectedPart.questionCount());
                assertThat(new BigDecimal(partJson.path("maxPoints").asText()))
                        .isEqualByComparingTo(expectedPart.maxPoints());
            }
            assertThat(hsa.path("afterStartChoices")).hasSize(1);
            JsonNode p3Choice = hsa.path("afterStartChoices").get(0);
            assertThat(p3Choice.path("options").size()).isGreaterThanOrEqualTo(2);
            JsonNode scienceOpt = null;
            for (JsonNode opt : p3Choice.path("options")) {
                if ("SCIENCE".equals(opt.path("code").asText())) {
                    scienceOpt = opt;
                }
            }
            assertThat(scienceOpt).isNotNull();
            assertThat(scienceOpt.path("chooseSubjects").asInt()).isEqualTo(3);
            assertThat(scienceOpt.path("subjects")).hasSize(5);
            assertThat(scienceOpt.path("countsByPick")).hasSize(3);
        } finally {
            jdbc.update(
                    "UPDATE exam.exam_version SET config = ?::jsonb WHERE exam_id = ? AND version_no = 1",
                    originalVersionConfig,
                    hsaLine.examId());
            jdbc.update(
                    "UPDATE exam.exam SET instructions = ? WHERE id = ?",
                    originalDraftInstructions,
                    hsaLine.examId());
        }

        // 2. QDA FULL: afterStartChoices combinations ordered cleanly
        GrantOrderResult.Line qdaLine = grant(userId, "DS-QDA-FULL-MINI", 2, List.of());
        MvcResult qdaRes = performPrepare(userId, qdaLine.entitlementId());
        assertThat(qdaRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode qda = json(qdaRes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(qda);
        assertThat(qda.path("preChoice").isNull()).isTrue();
        assertThat(qda.path("afterStartChoices")).hasSize(1);
        assertThat(qda.path("afterStartChoices").get(0).path("options")).isNotEmpty();

        // 3. SPT Informatics with single granted branch -> preChoice preselectedCode = CS, filtered options, and omittedGroups
        GrantOrderResult.Line sptCsLine = grant(userId, "DS-SPT-INFORMATICS-MINI", 2, List.of("CS"));
        MvcResult sptRes = performPrepare(userId, sptCsLine.entitlementId());
        assertThat(sptRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode spt = json(sptRes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(spt);
        assertThat(spt.path("preChoice").path("preselectedCode").asText()).isEqualTo("CS");
        assertThat(spt.path("preChoice").path("options")).hasSize(1);
        assertThat(spt.path("preChoice").path("options").get(0).path("code").asText()).isEqualTo("CS");
        assertThat(spt.path("omittedGroups")).isNotEmpty();
        assertThat(spt.path("parts").get(1).path("questionCount").asInt()).isEqualTo(2);

        // 4. VSAT Van with omitted essay group (Viết, 30 pts) -> non-empty omittedGroups and 0-count omitted part
        GrantOrderResult.Line vsatVanLine = grant(userId, "DS-VSAT-VAN", 2, List.of());
        MvcResult vsatRes = performPrepare(userId, vsatVanLine.entitlementId());
        assertThat(vsatRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode vsat = json(vsatRes);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(vsat);
        assertThat(vsat.path("omittedGroups")).hasSize(1);
        assertThat(vsat.path("omittedGroups").get(0).path("name").asText()).isNotBlank();
        assertThat(new BigDecimal(vsat.path("omittedGroups").get(0).path("points").asText()))
                .isEqualByComparingTo("30");
        assertThat(vsat.path("parts")).hasSize(4);
        assertThat(vsat.path("parts").get(3).path("questionCount").asInt()).isZero();
    }

    @Test
    void prepareWithZeroAttemptsLeftReturnsNullGrantSourceAndHidesRestrictedOptions() throws Exception {
        UUID userId = createStudent("prep-zero-left");
        GrantOrderResult.Line line = grant(userId, "DS-SPT-INFORMATICS-MINI", 1, List.of("CS"));
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();

        UUID attemptId = Ids.newId();
        entitlementPort.reserve(userId, line.entitlementId(), attemptId);
        attemptRepository.saveAndFlush(
                Attempt.start(
                        attemptId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        9876L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "key-prep-zero",
                        mutableClock.instant()));

        MvcResult res = performPrepare(userId, line.entitlementId());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(res);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(body);
        assertThat(body.path("attemptsLeft").asInt()).isZero();
        assertThat(body.path("grantSource").isNull()).isTrue();
        assertThat(body.path("openAttempt").path("attemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(body.path("openAttempt").path("state").asText()).isEqualTo("STARTING");
        assertThat(body.path("preChoice").path("options")).isEmpty();
    }

    @Test
    void otherUsersEntitlementIs404() throws Exception {
        UUID owner = createStudent("prep-owner");
        UUID stranger = createStudent("prep-stranger");
        UUID manager = createManager("prep-manager");
        GrantOrderResult.Line line = grant(owner, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult strangerRes = performPrepare(stranger, line.entitlementId());
        assertThat(strangerRes.getResponse().getStatus()).isEqualTo(404);

        MvcResult anonRes =
                mockMvc.perform(get("/api/v1/me/entitlements/{entitlementId}/prepare", line.entitlementId()))
                        .andReturn();
        assertThat(anonRes.getResponse().getStatus()).isEqualTo(401);

        MvcResult manageRes =
                mockMvc.perform(
                                get("/api/v1/me/entitlements/{entitlementId}/prepare", line.entitlementId())
                                        .with(AttemptTestFixtures.asManageUser(manager, "manager")))
                        .andReturn();
        assertThat(manageRes.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(manageRes).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");
    }
}
