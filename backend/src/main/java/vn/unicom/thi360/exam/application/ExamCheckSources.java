package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.BlockExpectedScoring;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ScoringDetails;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Source details used for display; the solver, not these counts, decides feasibility. Counts this class reports
 * without a witness paper (e.g. {@link #setRowCapacity}) are upper bounds of selectable children, never a proof
 * that a shared-pool assignment exists.
 */
final class ExamCheckSources {
    record Pool(List<PathCandidate> candidates, boolean complete) {
        Pool { candidates = List.copyOf(candidates); }
        long units() { return candidates.stream().mapToLong(candidate -> candidate.units().size()).sum(); }
        long sets(int size) { return candidates.stream().filter(candidate -> candidate.setId() != null
                && candidate.units().size() == size).count(); }
    }

    record Mismatches(List<String> codes, boolean complete) {
        Mismatches { codes = List.copyOf(codes); }
    }

    private final SolverInputBuilder.SolverCandidates candidates;
    private final Clock clock;
    private final Instant deadline;
    private final int maxCandidates;
    private final Map<GroupQuota, Pool> pools = new LinkedHashMap<>();
    private Map<VersionKey, Candidate> loaded;

    ExamCheckSources(SolverInputBuilder.SolverCandidates candidates,
                     Clock clock, Instant deadline, int maxCandidates) {
        this.candidates = candidates;
        this.clock = clock;
        this.deadline = deadline;
        this.maxCandidates = maxCandidates;
    }

    /** Singles matching the group's source nodes (UNI-2758); -1 when unknown (deadline or truncated pool). */
    long singles(GroupQuota group) {
        if (!clock.instant().isBefore(deadline)) return -1;
        return candidates.singlesFor(group);
    }

    Pool pool(GroupQuota group) {
        return pools.computeIfAbsent(group, key -> {
            loaded = null;
            if (!clock.instant().isBefore(deadline)) return new Pool(List.of(), false);
            List<PathCandidate> found = candidates.candidatesFor(key);
            return new Pool(found, found.size() <= maxCandidates && clock.instant().isBefore(deadline));
        });
    }

    /** Union of units from each group's pool, counting a shared child only once. */
    List<CountingUnit> union(List<GroupQuota> groups) {
        Map<String, CountingUnit> distinct = new LinkedHashMap<>();
        for (GroupQuota group : groups) {
            for (PathCandidate candidate : pool(group).candidates()) {
                for (CountingUnit unit : candidate.units()) {
                    String key = unit.childId() == null ? candidate.questionId() : unit.childId();
                    distinct.putIfAbsent(key, unit);
                }
            }
        }
        return List.copyOf(distinct.values());
    }

    /**
     * Upper bound of the Bộ câu hỏi row without a witness paper: per set group min(distinct children of its eligible
     * sets, authored cap), summed and capped by the distinct children of all groups; not a proof that an assignment
     * exists. Eligible: exact {@code setSize} sets for a SET group (cap {@code setCount * setSize}), sets no larger
     * than {@code questionCount} for a FLEXIBLE_SET group (cap {@code questionCount}). O(candidate children), no
     * subset-sum and no clock read, so the value is the same before and after the check deadline.
     */
    long setRowCapacity(List<GroupQuota> groups) {
        Set<String> all = new LinkedHashSet<>();
        long sum = 0;
        for (GroupQuota group : groups) {
            boolean fixed = group.kind() == GroupQuota.GroupKind.SET;
            if (!fixed && group.kind() != GroupQuota.GroupKind.FLEXIBLE_SET) continue;
            Set<String> eligible = new HashSet<>();
            for (PathCandidate candidate : pool(group).candidates()) {
                if (candidate.setId() == null) continue;
                int size = candidate.units().size();
                if (fixed ? group.setSize() == null || size != group.setSize()
                        : group.questionCount() != null && size > group.questionCount()) continue;
                for (CountingUnit unit : candidate.units()) {
                    if (!AnswerQuotaCounting.QUESTION_SET.equals(unit.quotaAnswerType())) continue;
                    eligible.add(unit.childId() == null ? candidate.questionId() : unit.childId());
                }
            }
            long cap = fixed ? (group.setCount() == null || group.setSize() == null ? 0
                    : (long) group.setCount() * group.setSize())
                    : group.questionCount() == null ? Long.MAX_VALUE : group.questionCount();
            sum = Math.min(Integer.MAX_VALUE, sum + Math.min(eligible.size(), cap));
            all.addAll(eligible);
        }
        return Math.min(sum, all.size());
    }

    boolean complete(List<GroupQuota> groups) {
        return groups.stream().allMatch(group -> pool(group).complete());
    }

    /** Served candidates of this solve, keyed by version (for witness lookups). */
    Map<VersionKey, Candidate> loadedCandidates() {
        return candidates.loadedCandidates();
    }

    /** Read stable codes from the same compact serving projection as the scoring fields. */
    Mismatches mismatchedCodes(GroupQuota group, BlockExpectedScoring expected) {
        if (expected == null) return new Mismatches(List.of(), true);
        Pool pool = pool(group);
        if (!pool.complete()) return new Mismatches(List.of(), false);
        if (loaded == null) loaded = candidates.loadedCandidates();
        Set<String> codes = new LinkedHashSet<>();
        for (PathCandidate item : pool.candidates()) {
            VersionKey key = new VersionKey(UUID.fromString(item.questionId()), item.versionNo());
            Candidate candidate = loaded.get(key);
            if (candidate == null) throw new IllegalStateException("Missing loaded candidate " + key);
            for (Unit unit : candidate.units()) {
                if (mismatch(expected, unit)) {
                    String code = unit.code() == null ? candidate.code() : unit.code();
                    if (code == null) throw new IllegalStateException("Missing serving question code " + key);
                    codes.add(code);
                }
            }
        }
        return new Mismatches(List.copyOf(codes), clock.instant().isBefore(deadline));
    }

    static boolean mismatch(BlockExpectedScoring expected, Unit unit) {
        ScoringDetails actual = unit.scoring();
        if (unit.answerType() == QuestionType.TRUE_FALSE) {
            if (expected.trueFalseMethod() != null
                    && !Objects.equals(expected.trueFalseMethod(), actual == null ? null : actual.trueFalseMethod())) return true;
            if (expected.trueFalseTable() != null
                    && !sameTable(expected.trueFalseTable(), actual == null ? null : actual.trueFalseTable())) return true;
        }
        return unit.answerType() == QuestionType.MULTIPLE_CHOICE && expected.multipleChoiceRule() != null
                && !Objects.equals(expected.multipleChoiceRule(), actual == null ? null : actual.multipleChoiceRule());
    }

    private static boolean sameTable(List<BigDecimal> expected, List<BigDecimal> actual) {
        if (actual == null || expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).compareTo(actual.get(i)) != 0) return false;
        }
        return true;
    }
}
