package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** EX-2 gate behaviour on published versions: determinism, snapshot source, exclusion, refusal, no side effects. */
class PaperGenerationIT extends PaperGenerationBaseIT {
    @Autowired StringRedisTemplate redis;
    @Autowired TransactionTemplate transactions;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoSpyBean QuestionSourcePort sourceSpy;

    @Test
    void sameSeedSameInputSamePaper() throws Exception {
        Subject subject = newSubject();
        singles(subject, 40);
        Published published = publishStructure(null, singleSection(subject, 5));

        GeneratedPaper first = port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 42L, Set.of());
        GeneratedPaper again = port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 42L, Set.of());

        assertThat(again).isEqualTo(first);
        assertThat(roots(first)).hasSize(5).doesNotHaveDuplicates();
        var section = first.parts().getFirst().sections().getFirst();
        assertThat(section.sectionId()).isEqualTo("s1");
        assertThat(section.subjectCode()).isEqualTo(subject.code());
        assertThat(section.questionCount()).isEqualTo(5);
        assertThat(section.groups().getFirst().groupId()).isEqualTo("b1");
        assertThat(first.dropped()).isEmpty();
        // The port is not a second bean of the solver limits type.
        assertThat(context.getBeansOfType(SolverLimits.class)).hasSize(1);
    }

    @Test
    void workingCopyEditDoesNotAffectVersion() throws Exception {
        Subject subject = newSubject();
        singles(subject, 40);
        Published published = publishStructure(null, singleSection(subject, 5));
        GeneratedPaper before = port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 9L, Set.of());

        jdbc.update("UPDATE exam.exam_structure SET parts = jsonb_set(jsonb_set(parts,"
                + "'{parts,0,sections,0,blocks,0,questionCount}','2'::jsonb),'{parts,0,sections,0,questionCount}','2'::jsonb), "
                + "structure_hash = 'edited' WHERE exam_id = ?", published.examId());
        jdbc.update("UPDATE exam.exam SET revision = revision + 1 WHERE id = ?", published.examId());
        entities.clear();

        GeneratedPaper after = port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 9L, Set.of());
        assertThat(after).isEqualTo(before);
        assertThat(roots(after)).hasSize(5);
    }

    @Test
    void excludedQuestionsAreNotPickedInPart() throws Exception {
        Subject subject = newSubject();
        List<UUID> pool = singles(subject, 12);
        Published published = insertVersion(singleSection(subject, 5), null);

        GeneratedPaper plain = port.generate(published.examId(), 1, List.of("p1"), List.of(), 3L, Set.of());
        assertThat(roots(plain)).hasSize(5);

        // Exclude what was picked plus two more: the replacement comes from the rest of the pool.
        Set<UUID> excluded = new java.util.LinkedHashSet<>(roots(plain));
        pool.stream().filter(id -> !excluded.contains(id)).limit(2).forEach(excluded::add);
        GeneratedPaper without = port.generate(published.examId(), 1, List.of("p1"), List.of(), 3L, excluded);
        assertThat(roots(without)).hasSize(5).doesNotHaveDuplicates().doesNotContainAnyElementsOf(excluded);

        // Boundary: exactly five left works and returns those five; one more excluded fails.
        Set<UUID> sevenOut = new java.util.LinkedHashSet<>(pool.subList(0, 7));
        GeneratedPaper exact = port.generate(published.examId(), 1, List.of("p1"), List.of(), 3L, sevenOut);
        assertThat(rootSet(exact)).containsExactlyInAnyOrderElementsOf(pool.subList(7, 12));
        Set<UUID> eightOut = new java.util.LinkedHashSet<>(pool.subList(0, 8));
        assertGenerationFails(published, eightOut, Reason.GENERATION_FAILED);
    }

    @Test
    void shortageFailsWithoutPartialPaper() throws Exception {
        Subject subject = newSubject();
        List<UUID> pool = singles(subject, 5);
        Published published = publishStructure(null, singleSection(subject, 5));
        assertThat(roots(port.generate(published.examId(), 1, List.of("p1"), List.of(), 1L, Set.of()))).hasSize(5);

        // One question leaves the source after publication: four of five, no paper at all.
        jdbc.update("UPDATE questionbank.question SET status = 'RETIRED' WHERE id = ?", pool.getFirst());
        assertGenerationFails(published, Set.of(), Reason.GENERATION_FAILED);
    }

    @Test
    void unknownVersionPartOrSelectionIsRefused() throws Exception {
        Subject subject = newSubject();
        singles(subject, 10);
        Published published = insertVersion(singleSection(subject, 3), null);
        UUID exam = published.examId();
        clearInvocations(sourceSpy);

        refused(() -> port.generate(exam, 2, List.of("p1"), List.of(), 1L, Set.of()));
        refused(() -> port.generate(UUID.randomUUID(), 1, List.of("p1"), List.of(), 1L, Set.of()));
        refused(() -> port.generate(exam, 1, List.of("nope"), List.of(), 1L, Set.of()));
        refused(() -> port.generate(exam, 1, List.of(), List.of(), 1L, Set.of()));
        refused(() -> port.generate(exam, 1, List.of("p1", "p1"), List.of(), 1L, Set.of()));
        // p1 is a part every student takes: it takes no selection, and a selection of an unrequested part is stray.
        refused(() -> port.generate(exam, 1, List.of("p1"), List.of(Selection.option("p1", "X")), 1L, Set.of()));
        refused(() -> port.generate(exam, 1, List.of("p1"), List.of(Selection.option("p9", "X")), 1L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());

        // The same calls reach the source once they are valid, so the spy really sees the solver's reads.
        assertThat(roots(port.generate(exam, 1, List.of("p1"), List.of(), 1L, Set.of()))).hasSize(3);
        verify(sourceSpy).findCandidates(any(), anyInt());
    }

    @Test
    void generationWritesNothing() throws Exception {
        Subject subject = newSubject();
        singles(subject, 20);
        Published published = publishStructure(null, singleSection(subject, 5));
        Map<String, Long> before = counts(published);

        port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 5L, Set.of());
        port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(), 6L, Set.of());

        assertThat(counts(published)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE exam_id = ?",
                Long.class, published.examId())).isEqualTo(before.get("usageOfExam"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCallsStayDeterministic() throws Exception {
        Subject[] subject = new Subject[1];
        Published[] published = new Published[1];
        List<UUID>[] pool = new List[1];
        try {
            transactions.executeWithoutResult(tx -> {
                subject[0] = newSubject();
                pool[0] = singles(subject[0], 60);
                published[0] = insertVersion(singleSection(subject[0], 8), null);
            });
            UUID exam = published[0].examId();
            GeneratedPaper expected = port.generate(exam, 1, List.of("p1"), List.of(), 77L, Set.of());
            ExecutorService executor = Executors.newFixedThreadPool(20);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<GeneratedPaper>> calls = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    calls.add(executor.submit(() -> {
                        start.await();
                        return port.generate(exam, 1, List.of("p1"), List.of(), 77L, Set.of());
                    }));
                }
                start.countDown();
                for (Future<GeneratedPaper> call : calls) {
                    assertThat(call.get(60, TimeUnit.SECONDS)).isEqualTo(expected);
                }
                // The shared state is intact: another call after the burst still gives the same paper.
                assertThat(port.generate(exam, 1, List.of("p1"), List.of(), 77L, Set.of())).isEqualTo(expected);
            } finally {
                executor.shutdownNow();
            }
            assertThat(roots(expected)).hasSize(8).doesNotHaveDuplicates();
        } finally {
            transactions.executeWithoutResult(tx -> {
                if (published[0] != null) {
                    jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", published[0].examId());
                    jdbc.update("DELETE FROM exam.exam WHERE id = ?", published[0].examId());
                }
                if (pool[0] != null) {
                    for (UUID id : pool[0]) {
                        jdbc.update("DELETE FROM questionbank.question_version WHERE question_id = ?", id);
                        jdbc.update("DELETE FROM questionbank.question WHERE id = ?", id);
                    }
                }
                if (subject[0] != null) jdbc.update("DELETE FROM catalog.catalog_item WHERE id = ?", subject[0].id());
            });
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private void assertGenerationFails(Published published, Set<UUID> excluded, Reason reason) {
        assertThatThrownBy(() -> port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(),
                1L, excluded)).isInstanceOfSatisfying(PaperGenerationException.class,
                ex -> assertThat(ex.reason()).isEqualTo(reason));
    }

    private static void refused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(PaperGenerationException.class,
                ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));
    }

    private Map<String, Long> counts(Published published) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ledger", count("SELECT count(*) FROM questionbank.question_usage_ledger"));
        counts.put("usageOfExam", count("SELECT count(*) FROM questionbank.question_usage_ledger WHERE exam_id = '"
                + published.examId() + "'"));
        counts.put("exams", count("SELECT count(*) FROM exam.exam"));
        counts.put("versions", count("SELECT count(*) FROM exam.exam_version"));
        counts.put("structures", count("SELECT count(*) FROM exam.exam_structure"));
        counts.put("checkRuns", count("SELECT count(*) FROM exam.exam_check_run"));
        counts.put("outbox", count("SELECT count(*) FROM shared.outbox_event"));
        counts.put("revision", count("SELECT revision FROM exam.exam WHERE id = '" + published.examId() + "'"));
        counts.put("previewKeys", (long) redis.keys("exam:preview:*").size());
        return counts;
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
