package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Upper bound of the Bộ câu hỏi quota row without a witness paper: what the set groups can take, never the raw
     * child stock. A SET group takes at most {@code setCount} whole sets of exactly {@code setSize}; a FLEXIBLE_SET
     * group takes whole sets whose sizes add up to at most its {@code questionCount}. Ineligible candidates (wrong
     * or oversized sets) are filtered per group before counting. Groups can share candidates, so the sum is also
     * capped by the distinct counting children of every eligible set: the result is an upper bound of selectable
     * children, not a proof that a shared-pool assignment exists. The work never follows the authored count: an
     * empty pool adds nothing, and the subset-sum table is bounded by the eligible child stock.
     */
    long setRowCapacity(List<GroupQuota> groups) {
        Set<String> distinct = new LinkedHashSet<>();
        long sum = 0;
        for (GroupQuota group : groups) {
            boolean fixed = group.kind() == GroupQuota.GroupKind.SET;
            if (!fixed && group.kind() != GroupQuota.GroupKind.FLEXIBLE_SET) continue;
            List<Integer> weights = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            long groupUnits = 0;
            long eligibleSize = 0;
            for (PathCandidate candidate : pool(group).candidates()) {
                if (candidate.setId() == null
                        || (fixed ? candidate.units().size() != group.setSize()
                                : candidate.units().size() > group.questionCount())) continue;
                int weight = 0;
                for (CountingUnit unit : candidate.units()) {
                    if (!AnswerQuotaCounting.QUESTION_SET.equals(unit.quotaAnswerType())) continue;
                    weight++;
                    distinct.add(unit.childId() == null ? candidate.questionId() : unit.childId());
                }
                if (weight == 0) continue;
                weights.add(weight);
                sizes.add(candidate.units().size());
                groupUnits += weight;
                eligibleSize += candidate.units().size();
            }
            if (fixed) {
                weights.sort(java.util.Comparator.reverseOrder());
                for (int i = 0; i < Math.min(group.setCount(), weights.size()); i++) sum += weights.get(i);
            } else if (!sizes.isEmpty()) {
                // Bound the reachability table by the eligible child stock, never the authored count: questionCount
                // can be Integer.MAX_VALUE, where limit + 1 would overflow. The cap stays within the int range.
                int limit = (int) Math.min(group.questionCount().longValue(),
                        Math.min(eligibleSize, (long) Integer.MAX_VALUE - 1));
                sum += Math.min(groupUnits, composable(sizes, limit));
            }
        }
        return Math.min(sum, distinct.size());
    }

    /** Largest sum of whole set sizes that does not exceed {@code limit}; stops at the check deadline. */
    private int composable(List<Integer> sizes, int limit) {
        boolean[] reachable = new boolean[limit + 1];
        reachable[0] = true;
        for (int size : sizes) {
            if (!clock.instant().isBefore(deadline)) break;
            for (int total = limit; total >= size; total--) reachable[total] |= reachable[total - size];
        }
        for (int total = limit; total > 0; total--) if (reachable[total]) return total;
        return 0;
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
