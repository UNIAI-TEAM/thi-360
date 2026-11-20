package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * Integration tests for {@code GET /api/v1/me/attempts/{attemptId}} covering strict allowlist
 * sanitization across all 8 question types, short forms, hidden unopened parts, and attempt asset
 * URL rewriting (AC-25, AC-26, AC-27).
 */
class AttemptStateIT extends AbstractAttemptDsIT {

    @Test
    void forbiddenKeysAbsent() throws Exception {
        UUID userId = createStudent("state-forbid");
        GrantOrderResult.Line hsaLine = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        GrantOrderResult.Line sptLine = grant(userId, "DS-SPT-INFORMATICS-MINI", 2, List.of());

        // 1. Scan /prepare responses
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                json(performPrepare(userId, hsaLine.entitlementId())));
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(
                json(performPrepare(userId, sptLine.entitlementId())));

        // 2. Scan POST /me/attempts responses
        MvcResult startRes = performStart(userId, "forbid-s1", hsaLine.entitlementId(), null);
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json(startRes));

        // 3. Create 8 REAL READY questions (all 8 QuestionTypes) carrying sentinel secret values
        UUID adminId = UUID.randomUUID();
        List<UUID> createdIds = new java.util.ArrayList<>();
        UUID scId = createAndReadyQuestion(adminId, QuestionType.SINGLE_CHOICE, null, "<p>SC prompt</p>",
                "<p>SENTINEL_SOLUTION_SC</p>",
                new QuestionBody.SingleChoiceBody(
                        QuestionType.SINGLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("o1", "<p>A</p>", true),
                                new QuestionBody.ChoiceOption("o2", "<p>B</p>", false)),
                        true));
        createdIds.add(scId);

        UUID mcId = createAndReadyQuestion(adminId, QuestionType.MULTIPLE_CHOICE, null, "<p>MC prompt</p>",
                "<p>SENTINEL_SOLUTION_MC</p>",
                new QuestionBody.MultipleChoiceBody(
                        QuestionType.MULTIPLE_CHOICE,
                        List.of(
                                new QuestionBody.ChoiceOption("m1", "<p>M1</p>", true),
                                new QuestionBody.ChoiceOption("m2", "<p>M2</p>", false),
                                new QuestionBody.ChoiceOption("m3", "<p>M3</p>", true)),
                        QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING,
                        true));
        createdIds.add(mcId);

        UUID saId = createAndReadyQuestion(adminId, QuestionType.SHORT_ANSWER, null, "<p>SA prompt</p>",
                "<p>SENTINEL_SOLUTION_SA</p>",
                new QuestionBody.ShortAnswerBody(
                        QuestionType.SHORT_ANSWER,
                        List.of("SENTINEL_SA_ANSWER_42"),
                        "Nhap so",
                        null));
        createdIds.add(saId);

        UUID clozeId = createAndReadyQuestion(adminId, QuestionType.CLOZE, BigDecimal.ONE, "<p>Cloze prompt</p>",
                "<p>SENTINEL_SOLUTION_CLOZE</p>",
                new QuestionBody.ClozeBody(
                        QuestionType.CLOZE,
                        "<p>Fill [[b1]] here</p>",
                        List.of(new QuestionBody.ClozeBlank("b1", List.of("SENTINEL_CLOZE_ANSWER"), BigDecimal.ONE))));
        createdIds.add(clozeId);

        UUID tfId = createAndReadyQuestion(adminId, QuestionType.TRUE_FALSE, BigDecimal.ONE, "<p>TF prompt</p>",
                "<p>SENTINEL_SOLUTION_TF</p>",
                new QuestionBody.TrueFalseBody(
                        QuestionType.TRUE_FALSE,
                        List.of(
                                new QuestionBody.TrueFalseStatement("s1", "<p>Stmt 1</p>", true, new BigDecimal("0.5")),
                                new QuestionBody.TrueFalseStatement("s2", "<p>Stmt 2</p>", false, new BigDecimal("0.5"))),
                        QuestionBody.TrueFalseScoringMethod.PER_STATEMENT,
                        null));
        createdIds.add(tfId);

        UUID matchId = createAndReadyQuestion(adminId, QuestionType.MATCHING, BigDecimal.ONE, "<p>Match prompt</p>",
                "<p>SENTINEL_SOLUTION_MATCH</p>",
                new QuestionBody.MatchingBody(
                        QuestionType.MATCHING,
                        List.of(
                                new QuestionBody.MatchingSide("l1", "<p>Left 1</p>"),
                                new QuestionBody.MatchingSide("l2", "<p>Left 2</p>")),
                        List.of(
                                new QuestionBody.MatchingSide("r1", "<p>Right 1</p>"),
                                new QuestionBody.MatchingSide("r2", "<p>Right 2</p>")),
                        List.of(
                                new QuestionBody.MatchingPair("l1", "r1", new BigDecimal("0.5")),
                                new QuestionBody.MatchingPair("l2", "r2", new BigDecimal("0.5"))),
                        false));
        createdIds.add(matchId);

        UUID essayId = createAndReadyQuestion(adminId, QuestionType.ESSAY, BigDecimal.ONE, "<p>Essay prompt</p>",
                "<p>SENTINEL_SOLUTION_ESSAY</p>",
                new QuestionBody.EssayBody(
                        QuestionType.ESSAY,
                        "<p>SENTINEL_ESSAY_GUIDE</p>",
                        List.of(new QuestionBody.EssayCriterion("c1", "SENTINEL_RUBRIC_CRIT", BigDecimal.ONE)),
                        "<p>SENTINEL_SAMPLE_ANSWER</p>",
                        300));
        createdIds.add(essayId);

        UUID child1Id = UUID.randomUUID();
        UUID child2Id = UUID.randomUUID();
        UUID setId = createAndReadyQuestion(adminId, QuestionType.QUESTION_SET, new BigDecimal("2"),
                "<p>Set prompt</p>", "<p>SENTINEL_SOLUTION_SET</p>",
                new QuestionBody.QuestionSetBody(
                        QuestionType.QUESTION_SET,
                        "<p>Set passage</p>",
                        1,
                        List.of(
                                new QuestionBody.ChildQuestion(
                                        QuestionType.SINGLE_CHOICE,
                                        "<p>Child 1</p>",
                                        new QuestionBody.SingleChoiceBody(
                                                QuestionType.SINGLE_CHOICE,
                                                List.of(
                                                        new QuestionBody.ChoiceOption("c1a", "<p>CA</p>", true),
                                                        new QuestionBody.ChoiceOption("c1b", "<p>CB</p>", false)),
                                                true),
                                        "<p>SENTINEL_CHILD1_SOLUTION</p>",
                                        BigDecimal.ONE,
                                        null,
                                        null,
                                        List.of(),
                                        child1Id),
                                new QuestionBody.ChildQuestion(
                                        QuestionType.SHORT_ANSWER,
                                        "<p>Child 2</p>",
                                        new QuestionBody.ShortAnswerBody(
                                                QuestionType.SHORT_ANSWER,
                                                List.of("SENTINEL_CHILD2_SA_ANS"),
                                                "text",
                                                null),
                                        "<p>SENTINEL_CHILD2_SOLUTION</p>",
                                        BigDecimal.ONE,
                                        null,
                                        null,
                                        List.of(),
                                        child2Id))));
        createdIds.add(setId);

        // Attach an IN_PROGRESS attempt holding all 8 types on its OPEN part
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId).getFirst();

        // Replace part0 items with 1 of each of the 8 question types + set children
        jdbc.update("DELETE FROM attempt.attempt_item WHERE part_id = ?", part0.getId());
        int ord = 0;
        UUID setItemId = Ids.newId();
        List<AttemptItem> all8 =
                List.of(
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 1, 1, scId, 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of("o2", "o1")),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 2, 1, mcId, 1, QuestionType.MULTIPLE_CHOICE, BigDecimal.ONE, List.of("m3", "m1", "m2")),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 3, 1, saId, 1, QuestionType.SHORT_ANSWER, BigDecimal.ONE, null),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 4, 1, clozeId, 1, QuestionType.CLOZE, BigDecimal.ONE, null),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 5, 1, tfId, 1, QuestionType.TRUE_FALSE, BigDecimal.ONE, null),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 6, 1, matchId, 1, QuestionType.MATCHING, BigDecimal.ONE, List.of("r2", "r1")),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, 7, 1, essayId, 1, QuestionType.ESSAY, BigDecimal.ONE, null),
                        AttemptItem.create(setItemId, attemptId, part0.getId(), null, "SEC", "TOAN", "B", null, ord++, null, 2, setId, 1, QuestionType.QUESTION_SET, new BigDecimal("2"), null));
        attemptItemRepository.saveAllAndFlush(all8);
        attemptItemRepository.saveAllAndFlush(
                List.of(
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", setItemId, ord++, 8, 1, child1Id, 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of("c1b", "c1a")),
                        AttemptItem.create(Ids.newId(), attemptId, part0.getId(), null, "SEC", "TOAN", "B", setItemId, ord, 9, 1, child2Id, 1, QuestionType.SHORT_ANSWER, BigDecimal.ONE, null)));

        MvcResult stateRes = performGetState(userId, attemptId);
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        String rawJson = stateRes.getResponse().getContentAsString();
        JsonNode stateJson = objectMapper.readTree(rawJson);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(stateJson);
        assertThat(rawJson).doesNotContain("SENTINEL_");

        Set<String> seenTypes = new LinkedHashSet<>();
        for (JsonNode item : stateJson.path("parts").get(0).path("items")) {
            String qt = item.path("questionType").asText();
            seenTypes.add(qt);
            if ("TRUE_FALSE".equals(qt)) {
                for (JsonNode st : item.path("questionBody").path("statements")) {
                    assertThat(st.has("answer")).isFalse();
                    assertThat(st.has("points")).isFalse();
                    assertThat(st.has("id")).isTrue();
                    assertThat(st.has("content")).isTrue();
                }
            }
            if ("CLOZE".equals(qt)) {
                for (JsonNode bl : item.path("questionBody").path("blanks")) {
                    assertThat(bl.has("answers")).isFalse();
                    assertThat(bl.has("points")).isFalse();
                    assertThat(bl.has("id")).isTrue();
                }
            }
        }
        assertThat(seenTypes)
                .containsExactlyInAnyOrder(
                        "SINGLE_CHOICE",
                        "MULTIPLE_CHOICE",
                        "SHORT_ANSWER",
                        "CLOZE",
                        "TRUE_FALSE",
                        "MATCHING",
                        "ESSAY",
                        "QUESTION_SET");
    }

    @Test
    void unopenedPartsHiddenAndShortFormsReturnedForStartingAndAborted() throws Exception {
        UUID userId = createStudent("state-short");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 3, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();

        // 1. STARTING short form -> only attemptId, state, serverNow (no parts/items/deadlines)
        UUID startingId = Ids.newId();
        attemptRepository.saveAndFlush(
                Attempt.start(
                        startingId,
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
                        12L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "short-starting",
                        mutableClock.instant()));

        JsonNode startingJson = json(performGetState(userId, startingId));
        assertThat(startingJson.path("state").asText()).isEqualTo("STARTING");
        assertThat(startingJson.hasNonNull("serverNow")).isTrue();
        assertThat(startingJson.has("parts")).isFalse();
        assertThat(startingJson.has("startedAt")).isFalse();
        assertThat(startingJson.has("deadlineAt")).isFalse();

        // 2. ABORTED short form -> abortReason & retryable = true, no parts
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'ABORTED', abort_reason = ? WHERE id = ?",
                AbortReason.GENERATION_TIMEOUT.name(),
                startingId);
        JsonNode abortedJson = json(performGetState(userId, startingId));
        assertThat(abortedJson.path("state").asText()).isEqualTo("ABORTED");
        assertThat(abortedJson.path("abortReason").asText()).isEqualTo("GENERATION_TIMEOUT");
        assertThat(abortedJson.path("retryable").asBoolean()).isTrue();
        assertThat(abortedJson.has("parts")).isFalse();

        // 3. Full IN_PROGRESS form -> Part 0 OPEN with items, Part 1 & 2 PENDING with empty items[]
        MvcResult startRes = performStart(userId, "short-real-open", line.entitlementId(), null);
        UUID inProgId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(inProgId, AttemptState.IN_PROGRESS);

        JsonNode openJson = json(performGetState(userId, inProgId));
        assertThat(openJson.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(openJson.path("parts")).hasSize(3);
        assertThat(openJson.path("parts").get(0).path("state").asText()).isEqualTo("OPEN");
        assertThat(openJson.path("parts").get(0).path("items")).isNotEmpty();
        assertThat(openJson.path("parts").get(1).path("state").asText()).isEqualTo("PENDING");
        assertThat(openJson.path("parts").get(1).path("items")).isEmpty();
        assertThat(openJson.path("parts").get(2).path("state").asText()).isEqualTo("PENDING");
        assertThat(openJson.path("parts").get(2).path("items")).isEmpty();
    }

    @Test
    void imageSourcesPointToAttemptAssets() throws Exception {
        UUID imageQuestionId =
                jdbc.queryForObject(
                        "SELECT id FROM questionbank.question WHERE source_code = 'DS-Q-IMAGE-001' AND parent_id IS NULL",
                        UUID.class);
        Integer qVer =
                jdbc.queryForObject(
                        "SELECT current_version_no FROM questionbank.question WHERE id = ?",
                        Integer.class,
                        imageQuestionId);

        UUID userId = createStudent("state-img");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-SUBJECT-TOAN-MINI", 2, List.of());
        MvcResult startRes = performStart(userId, "img-start", line.entitlementId(), null);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        AttemptPart part0 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId).getFirst();
        jdbc.update("DELETE FROM attempt.attempt_item WHERE part_id = ?", part0.getId());
        attemptItemRepository.saveAndFlush(
                AttemptItem.create(
                        Ids.newId(),
                        attemptId,
                        part0.getId(),
                        null,
                        "SEC",
                        "TOAN",
                        "B",
                        null,
                        0,
                        1,
                        1,
                        imageQuestionId,
                        qVer,
                        QuestionType.SINGLE_CHOICE,
                        BigDecimal.ONE,
                        List.of("a", "b", "c", "d")));

        MvcResult stateRes = performGetState(userId, attemptId);
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        String rawJson = stateRes.getResponse().getContentAsString();
        JsonNode stateJson = objectMapper.readTree(rawJson);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(stateJson);
        assertThat(rawJson).doesNotContain("/api/v1/admin/");

        String expectedPrefix = "/api/v1/me/attempts/" + attemptId + "/assets/";
        JsonNode item = stateJson.path("parts").get(0).path("items").get(0);
        assertThat(item.path("content").asText()).contains("src=\"" + expectedPrefix);
        assertThat(item.path("questionBody").path("options").get(0).path("content").asText())
                .contains("src=\"" + expectedPrefix);
    }

    private UUID createAndReadyQuestion(
            UUID adminId,
            QuestionType type,
            BigDecimal points,
            String content,
            String solution,
            QuestionBody body)
            throws Exception {
        SaveQuestionRequest req =
                new SaveQuestionRequest(
                        type,
                        "TOAN",
                        "G12",
                        "TH",
                        null,
                        null,
                        points,
                        List.of(),
                        List.of(),
                        content,
                        solution,
                        List.of(),
                        List.of(),
                        body,
                        "AT1-8T-" + UUID.randomUUID().toString().substring(0, 8));

        MvcResult createRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "at1-8t-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
        assertThat(createRes.getResponse().getStatus()).isBetween(200, 201);
        UUID id = UUID.fromString(json(createRes).path("summary").path("id").asText());

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-admin", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "ready-8t-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new BulkQuestionStatusRequest(List.of(id)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);
        return id;
    }
}
