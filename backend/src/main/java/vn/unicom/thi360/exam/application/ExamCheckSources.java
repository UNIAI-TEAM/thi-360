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
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ScoringDetails;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Unit;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Source details used for display; the solver, not these counts, decides feasibility. */
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
    private final QuestionSourcePort source;
    private final Clock clock;
    private final Instant deadline;
    private final int maxCandidates;
    private final Map<GroupQuota, Pool> pools = new LinkedHashMap<>();
    private Map<VersionKey, Candidate> loaded;

    ExamCheckSources(SolverInputBuilder.SolverCandidates candidates, QuestionSourcePort source,
                     Clock clock, Instant deadline, int maxCandidates) {
        this.candidates = candidates;
        this.source = source;
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

    boolean complete(List<GroupQuota> groups) {
        return groups.stream().allMatch(group -> pool(group).complete());
    }

    /** Resolve only mismatched versions to stable question codes, in bounded batches. */
    Mismatches mismatchedCodes(GroupQuota group, BlockExpectedScoring expected) {
        if (expected == null) return new Mismatches(List.of(), true);
        Pool pool = pool(group);
        if (!pool.complete()) return new Mismatches(List.of(), false);
        if (loaded == null) loaded = candidates.loadedCandidates();
        Map<VersionKey, Set<UUID>> mismatches = new LinkedHashMap<>();
        for (PathCandidate item : pool.candidates()) {
            VersionKey key = new VersionKey(UUID.fromString(item.questionId()), item.versionNo());
            Candidate candidate = loaded.get(key);
            if (candidate == null) throw new IllegalStateException("Missing loaded candidate " + key);
            for (Unit unit : candidate.units()) {
                if (mismatch(expected, unit)) {
                    mismatches.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(unit.questionId());
                }
            }
        }
        if (mismatches.isEmpty()) return new Mismatches(List.of(), true);
        List<VersionKey> keys = new ArrayList<>(mismatches.keySet());
        Map<VersionKey, Version> versions = new LinkedHashMap<>();
        for (int start = 0; start < keys.size(); start += 200) {
            if (!clock.instant().isBefore(deadline)) return new Mismatches(List.of(), false);
            for (Version version : source.loadVersions(keys.subList(start, Math.min(start + 200, keys.size())))) {
                versions.put(version.key(), version);
            }
        }
        Set<String> codes = new LinkedHashSet<>();
        for (var entry : mismatches.entrySet()) {
            Version version = versions.get(entry.getKey());
            if (version == null || version.code() == null) throw new IllegalStateException("Missing serving version " + entry.getKey());
            for (UUID unitId : entry.getValue()) {
                codes.add(Objects.equals(unitId, entry.getKey().questionId())
                        ? version.code() : version.childCodes().getOrDefault(unitId, version.code()));
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
