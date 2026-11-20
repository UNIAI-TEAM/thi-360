package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckUnitEngine;
import vn.unicom.thi360.exam.application.ExamTemplateService;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.identity.spi.Permissions;

/** FU-BE15-R1-5 and FU-BE9-1: what a check reads of a template, and which units a catalog edit makes it solve again. */
@Transactional
class ExamCheckPerformanceIT extends AbstractIntegrationTest {
    private static final UUID PHYSICS = UUID.fromString("00000000-0000-7000-8200-000000000204");
    private static final UUID CHEMISTRY = UUID.fromString("00000000-0000-7000-8200-000000000205");
    private static final UUID BIOLOGY = UUID.fromString("00000000-0000-7000-8200-000000000206");
    /** Vat li lesson LY1-1 (leaves LY1-1-1, LY1-1-2) and lesson LY2-1 of the dev MAPID tree. */
    private static final String LESSON_REFERENCED = "00000000-0000-7000-8200-000000002101";
    private static final String LESSON_UNREFERENCED = "00000000-0000-7000-8200-000000002103";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean private ExamTemplateService templates;
    @MockitoSpyBean private ExamCheckUnitEngine units;

    private static String optionBlocks(int count) {
        return """
                {"parts":[{"id":"p","order":1,"name":"P","questionCount":5,"participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"o1","kind":"SUBJECT","code":"EN","blocks":[
                    {"id":"b","name":"B","questionType":"SINGLE_CHOICE","questionCount":%d}]}]}]}
                """.formatted(count);
    }

    private void template(String code, boolean fixedCounts) {
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, timing, structure, fixed_counts) "
                + "VALUES (?, 'HSA', 'FULL', 'PER_PART', cast(? as jsonb), ?)", code, optionBlocks(5), fixedCounts);
    }

    private UUID exam(String templateCode, String structure) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,template_code) "
                + "VALUES (?,?,?,'Perf','FULL','PER_PART',?)", id, id.toString(), id.toString(), templateCode);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,cast(? as jsonb),'perf')",
                id, structure);
        entityManager.clear();
        return id;
    }

    private JsonNode check(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * The check reads the flag columns of the templates it needs, never a whole template row: no template entity is
     * loaded, and the structure JSON is read only for a template that has a lock turned on.
     */
    @Test
    void checkReadsKeyedTemplateProjection() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            String unlocked = "FU-PERF-" + UUID.randomUUID().toString().substring(0, 8);
            template(unlocked, false);
            UUID plain = exam(unlocked, optionBlocks(5));
            statistics.clear();
            clearInvocations(templates);
            check(plain);
            assertThat(statistics.getEntityStatistics(ExamTemplate.class.getName()).getLoadCount())
                    .as("template rows loaded by a check of an exam without a template lock").isZero();
            verify(templates, never()).readStructure(any());

            String locked = "FU-PERF-" + UUID.randomUUID().toString().substring(0, 8);
            template(locked, true);
            UUID changed = exam(locked, optionBlocks(6));
            statistics.clear();
            clearInvocations(templates);
            JsonNode result = check(changed);
            assertThat(result.findValuesAsText("code")).as("the lock still reaches the rules").contains("STRUCTURE_COUNT_LOCKED");
            // One read per facts call (the check rules, then the section states), only for the locked template.
            verify(templates, org.mockito.Mockito.atLeastOnce()).readStructure(locked);
            verify(templates, org.mockito.Mockito.atMost(2)).readStructure(any());
            assertThat(statistics.getEntityStatistics(ExamTemplate.class.getName()).getLoadCount())
                    .as("template rows loaded by a check of an exam with a template lock").isZero();
        } finally {
            statistics.setStatisticsEnabled(previous);
        }
    }

    private UUID subjectsExam() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'Catalog edit','FULL','SHARED',3,60)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'first')", id, """
                {"parts":[{"id":"p","name":"Part","questionCount":3,"sections":[
                  {"id":"physics","subjectCode":"LY","questionCount":1,"knowledgeScope":{"nodeIds":["%s"]},"blocks":[
                   {"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"chemistry","subjectCode":"HOA","questionCount":1,"blocks":[
                   {"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"biology","subjectCode":"SINH","questionCount":1,"blocks":[
                   {"id":"bb","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """.formatted(LESSON_REFERENCED));
        for (Object[] ref : new Object[][] {{"bp", "physics", PHYSICS}, {"bc", "chemistry", CHEMISTRY},
                {"bb", "biology", BIOLOGY}}) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) "
                    + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), id, ref[0], "p", ref[1], ref[2]);
        }
        entityManager.clear();
        return id;
    }

    private void leafUnder(String lesson, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog.catalog_item(id, group_id, code, code_normalized, value, name_vi, status, is_system,
                                                 parent_id, depth, path)
                SELECT ?, p.group_id, ?, lower(?), (SELECT max(value) + 1 FROM catalog.catalog_item WHERE group_id = p.group_id),
                       ?, 1, false, p.id, 3, p.path || ?::text || '/'
                FROM catalog.catalog_item p WHERE p.id = ?::uuid
                """, id, code, code, code, id.toString(), lesson);
        entityManager.clear();
    }

    private void checkSolving(UUID exam, int expected, List<String> subjects) throws Exception {
        clearInvocations(units);
        JsonNode result = check(exam);
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("VALID");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SectionOnPath>> solved = ArgumentCaptor.forClass(List.class);
        verify(units, times(expected)).solveUnit(solved.capture(), any(), any());
        List<String> actual = new ArrayList<>();
        solved.getAllValues().forEach(sections -> actual.add(sections.getFirst().subjectCode()));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(subjects);
    }

    /**
     * A catalog edit makes a check solve again only the units whose subject, MAPID scope or code items it touches: a
     * rename, an item no unit uses and a node of another subject change no fingerprint.
     */
    @Test
    void catalogEditResolvesAffectedSubjectsOnly() throws Exception {
        UUID exam = subjectsExam();
        checkSolving(exam, 3, List.of("LY", "HOA", "SINH"));
        checkSolving(exam, 0, List.of());

        // Renames and items that none of the three units reads: the catalog revision moves, no unit is solved again.
        jdbc.update("UPDATE catalog.catalog_item SET name_vi = name_vi || ' (doi ten)' WHERE id IN (?::uuid, ?::uuid, ?)",
                LESSON_REFERENCED, LESSON_UNREFERENCED, CHEMISTRY);
        leafUnder(LESSON_UNREFERENCED, "FU-PERF-OTHER");
        checkSolving(exam, 0, List.of());

        // A new leaf below the lesson that Vat li scopes to: that unit alone is solved again.
        leafUnder(LESSON_REFERENCED, "FU-PERF-BELOW");
        checkSolving(exam, 1, List.of("LY"));
        checkSolving(exam, 0, List.of());
    }
}
