package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.application.ExamCheckResultQuery;
import vn.unicom.thi360.exam.application.ExamCheckValidity;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

@Transactional
class ExamCheckValidityIT extends AbstractIntegrationTest {
    private static final UUID EXAM_ID = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamRepository exams;
    @Autowired private ExamKindProfileRepository profiles;
    @Autowired private ExamCheckResultStore results;
    @Autowired private ExamCheckValidity validity;
    @Autowired private ExamCheckResultQuery query;
    @Autowired private QuestionSourcePort source;
    @Autowired private CatalogLookup catalog;

    @Test
    void sourceRevisionChangeNeedsRecheckInList() throws Exception {
        Exam exam = exams.findById(EXAM_ID).orElseThrow();
        Long profileRevision = profiles.findById(exam.getKindItemId()).orElseThrow().getRevision();
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'validity-hash' WHERE exam_id = ?", EXAM_ID);
        Map<UUID, Long> revisions = source.sourceRevisions(List.of(SUBJECT_ID));
        assertThat(query.latest(EXAM_ID)).isNull();
        var input = new ExamCheckResultStore.Input(EXAM_ID, exam.getRevision(), "validity-hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), profileRevision, catalog.revision(),
                "fingerprint", mapper.valueToTree(revisions));
        assertThat(results.save(input, "VALID", 0, Instant.now(), 1,
                mapper.createObjectNode().put("status", "VALID"), mapper.createObjectNode()))
                .isTrue();

        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].check.status").value("VALID"));
        jdbc.update("UPDATE exam.exam SET check_status = 'NEEDS_RECHECK' WHERE id = ?", EXAM_ID);
        assertThat(validity.assess(List.of(EXAM_ID)).get(EXAM_ID).status()).isEqualTo("VALID");
        assertThat(query.latest(EXAM_ID).path("invalidReason").isNull()).isTrue();
        jdbc.update("INSERT INTO questionbank.question_source_revision (subject_item_id, revision) VALUES (?, 1) "
                + "ON CONFLICT (subject_item_id) DO UPDATE SET revision = questionbank.question_source_revision.revision + 1",
                SUBJECT_ID);
        mockMvc.perform(get("/api/v1/admin/exams").param("q", "HSA-F-DEV-001")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].check.status").value("NEEDS_RECHECK"));
        assertThat(validity.assess(List.of(EXAM_ID)).get(EXAM_ID).invalidReason()).isEqualTo("SOURCE_CHANGED");
        assertThat(query.latest(EXAM_ID).path("invalidReason").asText()).isEqualTo("SOURCE_CHANGED");
        jdbc.update("UPDATE questionbank.question_source_revision SET revision = ? WHERE subject_item_id = ?",
                revisions.get(SUBJECT_ID), SUBJECT_ID);
        jdbc.update("UPDATE exam.exam SET duration_minutes = duration_minutes + 1 WHERE id = ?", EXAM_ID);
        assertThat(validity.assess(List.of(EXAM_ID)).get(EXAM_ID).invalidReason()).isEqualTo("CONFIG_CHANGED");
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'changed' WHERE exam_id = ?", EXAM_ID);
        assertThat(validity.assess(List.of(EXAM_ID)).get(EXAM_ID).invalidReason()).isEqualTo("STRUCTURE_CHANGED");
    }
}
