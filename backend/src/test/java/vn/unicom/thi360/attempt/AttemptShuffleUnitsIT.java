package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.ExamListingFixture;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Real-wiring integration test (zero solver mocks) verifying that {@link PaperSolverPort}
 * propagates per-{@code (sectionId, blockId)} {@code shuffleUnits} flags through to
 * {@code POST /api/v1/me/attempts} item ordering (R1-11, R1-42).
 */
class AttemptShuffleUnitsIT extends AbstractIntegrationTest {

    private static final UUID SUBJECT_GROUP =
            UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID HSA_KIND =
            UUID.fromString("00000000-0000-7000-8200-000000000101");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaperSolverPort paperSolverPort;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AttemptItemRepository attemptItemRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID userId : createdUserIds) {
            AttemptTestFixtures.cleanupUser(jdbc, userId);
        }
        createdUserIds.clear();
        for (UUID examId : createdExamIds) {
            jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE exam_id = ?", examId);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", examId);
            jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", examId);
            jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", examId);
            jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", examId);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
        }
        createdExamIds.clear();
        AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, createdQuestionIds);
        createdQuestionIds.clear();
        for (UUID subjectId : createdSubjectIds) {
            jdbc.update(
                    "DELETE FROM questionbank.question_source_revision WHERE subject_item_id = ?",
                    subjectId);
            jdbc.update("DELETE FROM catalog.catalog_item WHERE id = ?", subjectId);
        }
        createdSubjectIds.clear();
    }

    @Test
    void solverAndAttemptStartPreserveUnshuffledBlockAndShuffleUnitsEnabledBlock() throws Exception {
        String s1Code = createSubject();
        String s2Code = createSubject();
        UUID adminId = UUID.randomUUID();
        createReadySingles(adminId, s1Code, 6);
        createReadySingles(adminId, s2Code, 10);

        // Two sections in one part: one unshuffled block and one shuffled block (R1-11)
        String partsJson =
                """
                {
                  "parts": [
                    {
                      "id": "p1",
                      "order": 1,
                      "name": "P1",
                      "questionCount": 16,
                      "questionCountOnPaper": 16,
                      "questionCountRequired": 16,
                      "participation": {"mode": "ALL", "choose": null},
                      "sections": [
                        {
                          "id": "sec-fixed",
                          "subjectCode": "%s",
                          "questionCount": 6,
                          "blocks": [
                            {"id": "b_fixed", "questionType": "SINGLE_CHOICE", "questionCount": 6, "shuffleUnits": false, "shuffleEditable": true}
                          ]
                        },
                        {
                          "id": "sec-shuffled",
                          "subjectCode": "%s",
                          "questionCount": 10,
                          "blocks": [
                            {"id": "b_shuffled", "questionType": "SINGLE_CHOICE", "questionCount": 10, "shuffleUnits": true, "shuffleEditable": true}
                          ]
                        }
                      ],
                      "options": []
                    }
                  ]
                }
                """
                        .formatted(s1Code, s2Code);

        UUID examId = publishCustomExam(partsJson);

        // 1. Direct real PaperSolverPort check: per-block shuffleUnits flags match structure
        GeneratedPaper directPaper =
                paperSolverPort.generate(examId, 1, List.of("p1"), List.of(), 77L, Set.of());
        GeneratedSection fixedSec = findSection(directPaper, "sec-fixed");
        GeneratedSection shuffledSec = findSection(directPaper, "sec-shuffled");
        assertThat(fixedSec.groups()).hasSize(1);
        assertThat(fixedSec.groups().getFirst().groupId()).isEqualTo("b_fixed");
        assertThat(fixedSec.groups().getFirst().shuffleUnits()).isFalse();
        assertThat(shuffledSec.groups()).hasSize(1);
        assertThat(shuffledSec.groups().getFirst().groupId()).isEqualTo("b_shuffled");
        assertThat(shuffledSec.groups().getFirst().shuffleUnits()).isTrue();

        // 2. End-to-end POST /me/attempts check: unshuffled section keeps exact solver order, shuffled section permutes
        boolean observedShuffledDifference = false;
        for (int run = 0; run < 3; run++) {
            UUID userId = createStudent("shuffle-u-" + run);
            GrantOrderResult.Line line =
                    AttemptTestFixtures.grantExam(
                            grantService,
                            userId,
                            examId,
                            2,
                            List.of(),
                            "SCORE_AND_ANSWERS",
                            1);

            MvcResult startRes =
                    mockMvc.perform(
                                    post("/api/v1/me/attempts")
                                            .with(AttemptTestFixtures.asExamUser(userId, "learner"))
                                            .header("Idempotency-Key", "shuf-k-" + run)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    objectMapper.writeValueAsString(
                                                            new StartAttemptRequest(
                                                                    line.entitlementId(), null))))
                            .andExpect(status().isAccepted())
                            .andReturn();

            UUID attemptId =
                    UUID.fromString(
                            objectMapper
                                    .readTree(startRes.getResponse().getContentAsString())
                                    .path("attemptId")
                                    .asText());
            Attempt opened = awaitOpen(attemptId);

            // Re-run real solver with exact attempt seed to obtain pre-PaperOrdering pick sequence
            GeneratedPaper solverPickOrder =
                    paperSolverPort.generate(
                            examId, 1, List.of("p1"), List.of(), opened.getSeed(), Set.of());
            List<UUID> expectedFixedOrder =
                    groupQuestionIds(findSection(solverPickOrder, "sec-fixed"));
            List<UUID> pickedShuffledOrder =
                    groupQuestionIds(findSection(solverPickOrder, "sec-shuffled"));

            List<AttemptItem> persisted =
                    attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
            List<UUID> persistedFixedOrder =
                    persisted.stream()
                            .filter(it -> "sec-fixed".equals(it.getSectionId()))
                            .map(AttemptItem::getQuestionId)
                            .toList();
            List<UUID> persistedShuffledOrder =
                    persisted.stream()
                            .filter(it -> "sec-shuffled".equals(it.getSectionId()))
                            .map(AttemptItem::getQuestionId)
                            .toList();

            assertThat(persistedFixedOrder).isEqualTo(expectedFixedOrder);
            assertThat(persistedShuffledOrder)
                    .containsExactlyInAnyOrderElementsOf(pickedShuffledOrder);
            if (!persistedShuffledOrder.equals(pickedShuffledOrder)) {
                observedShuffledDifference = true;
                break;
            }
        }
        assertThat(observedShuffledDifference).isTrue();
    }

    private UUID createStudent(String label) {
        UUID id =
                AttemptTestFixtures.createStudentAccount(
                        jdbc, label + "-" + UUID.randomUUID().toString().substring(0, 6));
        createdUserIds.add(id);
        return id;
    }

    private String createSubject() {
        UUID id = UUID.randomUUID();
        String code = "SHF_" + id.toString().replace("-", "").substring(0, 12);
        jdbc.update(
                "INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,name_en,"
                        + "display_order,status,is_system,depth,path) VALUES (?,?,?,?,(SELECT coalesce(max(value),0)+1 "
                        + "FROM catalog.catalog_item WHERE group_id=?),'Shuffle Test','Shuffle Test',0,1,false,1,?)",
                id,
                SUBJECT_GROUP,
                code,
                code.toLowerCase(Locale.ROOT),
                SUBJECT_GROUP,
                "/" + id + "/");
        createdSubjectIds.add(id);
        return code;
    }

    private void createReadySingles(UUID adminId, String subjectCode, int count) throws Exception {
        List<UUID> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SaveQuestionRequest req =
                    new SaveQuestionRequest(
                            QuestionType.SINGLE_CHOICE,
                            subjectCode,
                            "G12",
                            "TH",
                            null,
                            null,
                            BigDecimal.ONE,
                            List.of(),
                            List.of(),
                            "<p>Q " + subjectCode + " " + i + "</p>",
                            null,
                            List.of(),
                            List.of(),
                            new QuestionBody.SingleChoiceBody(
                                    QuestionType.SINGLE_CHOICE,
                                    List.of(
                                            new QuestionBody.ChoiceOption("a", "<p>A</p>", true),
                                            new QuestionBody.ChoiceOption("b", "<p>B</p>", false)),
                                    false),
                            "SHF-Q-" + UUID.randomUUID().toString().substring(0, 8));
            MvcResult res =
                    mockMvc.perform(
                                    post("/api/v1/admin/questions")
                                            .with(
                                                    asUser(
                                                            adminId,
                                                            "shuf-admin",
                                                            Permissions.QUESTION_MODIFY))
                                            .header(
                                                    "Idempotency-Key",
                                                    "shf-q-" + UUID.randomUUID())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
            assertThat(res.getResponse().getStatus()).isBetween(200, 201);
            UUID qId =
                    UUID.fromString(
                            objectMapper
                                    .readTree(res.getResponse().getContentAsString())
                                    .path("summary")
                                    .path("id")
                                    .asText());
            batch.add(qId);
            createdQuestionIds.add(qId);
        }
        mockMvc.perform(
                        post("/api/v1/admin/questions/bulk/ready")
                                .with(asUser(adminId, "shuf-admin", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", "shf-rdy-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new BulkQuestionStatusRequest(batch))))
                .andExpect(status().isOk());
    }

    private UUID publishCustomExam(String partsJson) throws Exception {
        UUID examId = UUID.randomUUID();
        createdExamIds.add(examId);
        jdbc.update(
                "INSERT INTO exam.exam(id,code,code_normalized,name,kind_item_id,scope,timing,question_count,"
                        + "has_unpublished_changes,duration_minutes) VALUES (?,?,?,'Shuffle Units Test',?,'FULL','SHARED',"
                        + "(SELECT sum((p->>'questionCount')::integer) FROM jsonb_array_elements(?::jsonb->'parts') p),true,30)",
                examId,
                examId.toString(),
                examId.toString(),
                HSA_KIND,
                partsJson);
        jdbc.update(
                "INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'shuf-it')",
                examId,
                partsJson);

        JsonNode structure = objectMapper.readTree(partsJson);
        for (JsonNode part : structure.path("parts")) {
            String partId = part.path("id").asText();
            for (JsonNode section : part.path("sections")) {
                UUID subjectId =
                        jdbc.queryForObject(
                                "SELECT id FROM catalog.catalog_item WHERE group_id = ? AND code = ?",
                                UUID.class,
                                SUBJECT_GROUP,
                                section.path("subjectCode").asText());
                for (JsonNode block : section.path("blocks")) {
                    jdbc.update(
                            "INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                            UUID.randomUUID(),
                            examId,
                            block.path("id").asText(),
                            partId,
                            section.path("id").asText(),
                            subjectId);
                }
            }
        }

        ExamListingFixture.fill(jdbc, examId);
        MvcResult checkRes =
                mockMvc.perform(
                                post("/api/v1/admin/exams/{id}/check", examId)
                                        .with(asUser(Permissions.EXAM_MODIFY)))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode checked = objectMapper.readTree(checkRes.getResponse().getContentAsString());
        var paths = objectMapper.createArrayNode();
        checked.path("paths")
                .forEach(
                        p -> {
                            if ("VALID".equals(p.path("result").asText())) {
                                paths.add(p.path("pathId").asText());
                            }
                        });
        assertThat(paths).isNotEmpty();

        long revision =
                jdbc.queryForObject(
                        "SELECT revision FROM exam.exam WHERE id = ?", Long.class, examId);
        var pubReq = objectMapper.createObjectNode().put("expectedRevision", revision);
        pubReq.set("pathIds", paths);
        mockMvc.perform(
                        post("/api/v1/admin/exams/{id}/publish", examId)
                                .with(asUser(Permissions.EXAM_PUBLISH))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(pubReq.toString()))
                .andExpect(status().isOk());
        return examId;
    }

    private Attempt awaitOpen(UUID attemptId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            Attempt a = attemptRepository.findById(attemptId).orElse(null);
            if (a != null && a.getState() == AttemptState.IN_PROGRESS) {
                return a;
            }
            if (a != null && a.getState() == AttemptState.ABORTED) {
                throw new AssertionError("Attempt aborted unexpectedly: " + a.getAbortReason());
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out awaiting IN_PROGRESS for attempt " + attemptId);
    }

    private static GeneratedSection findSection(GeneratedPaper paper, String sectionId) {
        return paper.parts().stream()
                .flatMap(p -> p.sections().stream())
                .filter(s -> sectionId.equals(s.sectionId()))
                .findFirst()
                .orElseThrow();
    }

    private static List<UUID> groupQuestionIds(GeneratedSection section) {
        GeneratedGroup group = section.groups().getFirst();
        return group.questions().stream().map(GeneratedQuestion::questionId).toList();
    }
}
