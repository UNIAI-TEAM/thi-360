package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;

/**
 * The no-witness Bộ câu hỏi row is an upper bound: min(eligible stock, authored count), summed over groups and
 * capped by the distinct children of every eligible set. It never depends on the check deadline.
 */
class ExamCheckSourcesTests {
    private static final Instant DEADLINE = Instant.parse("2026-11-19T12:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(DEADLINE.minusSeconds(1));
    private final Clock clock = new Clock() {
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

    /** Two disjoint three-child sets, a flexible group asking for five: min(6, 5) = 5, not the 3 a subset-sum gives. */
    @Test
    void flexibleGroupReportsTheStockUpperBoundNotWhatSetsExactlyCompose() {
        GroupQuota group = flexible("flex", 5);
        ExamCheckSources sources = sources(group, set("a", 3), set("b", 3));
        assertThat(sources.complete(List.of(group))).isTrue();

        assertThat(sources.setRowCapacity(List.of(group))).isEqualTo(5);
    }

    @Test
    void theValueIsTheSameBeforeAndAfterTheDeadline() {
        GroupQuota group = flexible("flex", 5);
        ExamCheckSources sources = sources(group, set("a", 3), set("b", 3));
        assertThat(sources.complete(List.of(group))).isTrue();
        long before = sources.setRowCapacity(List.of(group));

        now.set(DEADLINE);
        assertThat(sources.setRowCapacity(List.of(group))).isEqualTo(before).isEqualTo(5);
    }

    @Test
    void childrenSharedByOverlappingGroupsAreCountedOnce() {
        GroupQuota first = flexible("f1", 3);
        GroupQuota second = flexible("f2", 3);
        var candidates = mock(SolverInputBuilder.SolverCandidates.class);
        List<PathCandidate> shared = List.of(set("a", 3), set("big", 4));
        when(candidates.candidatesFor(first)).thenReturn(shared);
        when(candidates.candidatesFor(second)).thenReturn(shared);
        ExamCheckSources sources = new ExamCheckSources(candidates, clock, DEADLINE, 10);

        assertThat(sources.setRowCapacity(List.of(first, second))).isEqualTo(3);
    }

    @Test
    void aFixedGroupCountsOnlyExactSizeSetsCappedBySetCountTimesSetSize() {
        GroupQuota group = fixed("fixed", 1, 3);
        ExamCheckSources sources = sources(group, set("a", 3), set("b", 3), set("wrong", 4));
        assertThat(sources.setRowCapacity(List.of(group))).isEqualTo(3);

        GroupQuota none = fixed("none", 1, 3);
        assertThat(sources(none, set("wrong", 4)).setRowCapacity(List.of(none))).isZero();
    }

    @Test
    void maxAuthoredCountOverEmptyAndPopulatedStockIsOverflowSafeAndFast() {
        GroupQuota empty = flexible("empty", Integer.MAX_VALUE);
        assertThat(sources(empty).setRowCapacity(List.of(empty))).isZero();

        GroupQuota big = flexible("big", Integer.MAX_VALUE);
        GroupQuota huge = fixed("huge", Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<PathCandidate> sets = new ArrayList<>();
        for (int i = 0; i < 200; i++) sets.add(set("s" + i, 3));
        var candidates = mock(SolverInputBuilder.SolverCandidates.class);
        when(candidates.candidatesFor(big)).thenReturn(sets);
        when(candidates.candidatesFor(huge)).thenReturn(sets);
        ExamCheckSources sources = new ExamCheckSources(candidates, clock, DEADLINE, 1_000);

        long[] value = new long[1];
        assertThat(sources.complete(List.of(big, huge))).isTrue();
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> value[0] = sources.setRowCapacity(List.of(big, huge)));
        assertThat(value[0]).isEqualTo(600);
    }

    private ExamCheckSources sources(GroupQuota group, PathCandidate... sets) {
        var candidates = mock(SolverInputBuilder.SolverCandidates.class);
        when(candidates.candidatesFor(group)).thenReturn(List.of(sets));
        return new ExamCheckSources(candidates, clock, DEADLINE, 10);
    }

    private static GroupQuota flexible(String id, int questionCount) {
        return new GroupQuota(id, GroupQuota.GroupKind.FLEXIBLE_SET, questionCount, null, null, null,
                "SINGLE_CHOICE", null, List.of(), List.of(), null, null);
    }

    private static GroupQuota fixed(String id, int setCount, int setSize) {
        return new GroupQuota(id, GroupQuota.GroupKind.SET, null, null, setCount, setSize,
                "SINGLE_CHOICE", null, List.of(), List.of(), null, null);
    }

    private static PathCandidate set(String id, int size) {
        List<CountingUnit> children = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            children.add(new CountingUnit(id + "-c" + i, "SINGLE_CHOICE", null, null, List.of(), List.of(), null,
                    AnswerQuotaCounting.QUESTION_SET));
        }
        return new PathCandidate(id, 1, id, null, children);
    }
}
