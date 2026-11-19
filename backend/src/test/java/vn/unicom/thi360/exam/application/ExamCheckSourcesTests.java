package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;

/**
 * The presenter preloads each group's pool, then resolves the subject quota rows; a check deadline
 * that expires between those steps must not let the no-witness Bộ câu hỏi row publish the partial
 * result of a calculation that was never run (R5-1 / R5-2).
 */
class ExamCheckSourcesTests {

    /**
     * A flexible group asking for three children with one eligible three-child set in a complete pool.
     * When the deadline has passed after the pool was cached, the row still reports its three children —
     * the conservative upper bound — not the zero an abandoned subset-sum prefix would publish.
     */
    @Test
    void setRowCapacityAfterTheDeadlineReportsTheStockUpperBound() {
        GroupQuota group = new GroupQuota("flex", GroupQuota.GroupKind.FLEXIBLE_SET, 3, null, null, null,
                "SINGLE_CHOICE", null, List.of(), List.of(), null, null);
        List<CountingUnit> children = List.of(
                setChild("c1"), setChild("c2"), setChild("c3"));
        var candidates = mock(SolverInputBuilder.SolverCandidates.class);
        when(candidates.candidatesFor(group))
                .thenReturn(List.of(new PathCandidate("set-1", 1, "set-1", null, children)));

        Instant deadline = Instant.parse("2026-11-19T12:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(deadline.minusSeconds(1));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override public Instant instant() {
                return now.get();
            }
        };
        ExamCheckSources sources = new ExamCheckSources(candidates, clock, deadline, 10);
        assertThat(sources.complete(List.of(group))).isTrue();

        now.set(deadline);
        assertThat(sources.setRowCapacity(List.of(group))).isEqualTo(3);
    }

    private static CountingUnit setChild(String childId) {
        return new CountingUnit(childId, "SINGLE_CHOICE", null, null, List.of(), List.of(), null,
                AnswerQuotaCounting.QUESTION_SET);
    }
}
