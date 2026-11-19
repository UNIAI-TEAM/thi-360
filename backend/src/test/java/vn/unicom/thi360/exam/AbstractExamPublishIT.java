package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * Builds exams the way the publish tests of EX-1 need them: a hand-written structure, a saved check with chosen
 * path results and a witness question per valid path, and the complete listing of {@link ExamListingFixture}.
 * Everything is rolled back with the test transaction.
 */
@Transactional
abstract class AbstractExamPublishIT extends AbstractIntegrationTest {
    protected static final UUID SUBJECT = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected ExamRepository exams;
    @Autowired protected ExamKindProfileRepository profiles;
    @Autowired protected ExamCheckResultStore results;
    @Autowired protected QuestionSourcePort source;
    @Autowired protected CatalogLookup catalog;
    @Autowired protected EntityManager entityManager;

    protected UUID kind(String code) {
        return jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id "
                + "WHERE g.code = 'EXAM' AND i.code = ?", UUID.class, code);
    }

    /** A FULL exam of the kind with a complete listing; the structure is the given {@code parts} array body. */
    protected UUID newExam(String kindCode, String partsArray) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, has_unpublished_changes) "
                + "VALUES (?, ?, ?, 'E24 test', ?, 'FULL', 'SHARED', true)", id, id.toString(), id.toString(), kind(kindCode));
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, ?::jsonb, 'publish-it')",
                id, "{\"parts\":" + partsArray + "}");
        ExamListingFixture.fill(jdbc, id);
        return id;
    }

    /** Saves a check whose paths have the given results ({@code "pathId" -> VALID|INVALID}); valid ones get a witness. */
    protected void saveCheck(UUID examId, Map<String, String> pathResults) {
        entityManager.flush();
        entityManager.clear();
        var exam = exams.findById(examId).orElseThrow();
        var profile = profiles.findById(exam.getKindItemId()).orElseThrow();
        var input = new ExamCheckResultStore.Input(examId, exam.getRevision(), "publish-it",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), profile.getRevision(), catalog.revision(),
                "test", mapper.valueToTree(source.sourceRevisions(List.of(SUBJECT))));
        ObjectNode outcome = mapper.createObjectNode();
        ArrayNode paths = outcome.putArray("paths");
        ArrayNode witnesses = mapper.createArrayNode();
        boolean allValid = true;
        for (var entry : pathResults.entrySet()) {
            paths.addObject().put("pathId", entry.getKey()).put("result", entry.getValue());
            if (!"VALID".equals(entry.getValue())) {
                allValid = false;
                continue;
            }
            UUID question = question();
            ObjectNode witness = witnesses.addObject();
            ArrayNode chosen = witness.putObject("path").putArray("chosen");
            if (!"default".equals(entry.getKey())) {
                for (String option : entry.getKey().split("\\+")) chosen.addObject().put("optionId", option);
            }
            witness.putArray("witness").addObject().putArray("groups").addObject().putArray("picks")
                    .addObject().put("questionId", question.toString()).put("versionNo", 1);
        }
        outcome.put("status", allValid ? "VALID" : "INVALID");
        assertThat(results.save(input, allValid ? "VALID" : "INVALID", 0, Instant.now(), 1, outcome, witnesses)).isTrue();
    }

    protected static Map<String, String> paths(String... idAndResult) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : idAndResult) map.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        return map;
    }

    protected UUID question() {
        UUID question = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "PUB-" + question, SUBJECT);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
        return question;
    }

    protected long revision(UUID examId) {
        return jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, examId);
    }

    protected ResultActions publish(UUID examId, String... pathIds) throws Exception {
        return publishWithKey(examId, UUID.randomUUID().toString(), pathIds);
    }

    protected ResultActions publishWithKey(UUID examId, String idempotencyKey, String... pathIds) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", revision(examId));
        ArrayNode selected = body.putArray("pathIds");
        for (String pathId : pathIds) selected.add(pathId);
        return mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }
}
