package vn.unicom.thi360.exam.domain.solver;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Bộ giải sinh đề của module exam (spec 5.4, B9, D15): dùng chung cho kiểm tra cấu hình, xem trước
 * và sinh đề.
 *
 * <p>Đầu vào là một đường lựa chọn dưới dạng danh sách <b>môn</b> ({@link SectionOnPath}: chỉ tiêu
 * cấp môn và các nhóm của nó), không phải từng nhóm riêng. Phương án {@link SubjectChoice} được mở
 * thành từng cách chọn môn ({@link SubjectPick}); mỗi cách tính một đường cho giới hạn 200 đường.
 * Đường được tách thành các bài toán con theo mã môn (gộp các môn cùng {@code subjectCode}), kết
 * quả nhớ theo {@link SubproblemKey} trong một lời gọi.
 *
 * <p>Quy tắc đếm của B9: một đơn vị đếm tính một lần vào tổng và đóng góp cùng lúc vào mọi chỉ
 * tiêu của nhóm và của môn; chùm lấy nguyên, câu con trừ vào từng chỉ tiêu theo phân loại của nó;
 * một câu, một phiên bản, một câu con chỉ dùng một lần trên cả đường.
 *
 * <p>Thuật toán: bài toán con đủ điều kiện spec 5.4 bước 1 giải chính xác bằng mạng luồng hai lớp
 * (phía nhóm → câu sức chứa 1 → phía môn); phần còn lại bằng quay lui có cắt nhánh trên lớp đóng
 * góp (chùm trước, số theo lớp, rồi câu cụ thể). Thứ tự duyệt cố định ở chế độ
 * {@link SolveMode.Check}; xáo theo hạt giống ở {@link SolveMode.Generate}.
 *
 * <p>Thuần Java: không Spring, không DB. Hạn thời gian tính cho cả lời gọi (đọc ứng viên, liệt kê
 * đường, giải) và được kiểm qua {@link Clock} truyền vào để test được.
 */
public final class PaperSolver {

    private final SolverLimits limits;
    private final Clock clock;
    private final SubjectCountAllocator allocator;
    private final QuotaApportioner apportioner;

    /** Bộ giải với giới hạn mặc định của spec (200 đường, 20.000 ứng viên/nhóm, 30 giây, 8 môn). */
    public PaperSolver() {
        this(SolverLimits.defaults(), Clock.systemUTC(), new StructureOrderAllocator(),
                new LargestRemainderApportioner());
    }

    /** Bộ giải với giới hạn và đồng hồ cho trước (test dùng đồng hồ điều khiển được). */
    public PaperSolver(SolverLimits limits, Clock clock) {
        this(limits, clock, new StructureOrderAllocator(), new LargestRemainderApportioner());
    }

    /**
     * Bộ giải với quy tắc tạm thay thế được (spec 12): gán số câu cho môn được chọn (D25) và quy
     * chỉ tiêu tỉ lệ ra số câu (PBI-04 7.3).
     */
    public PaperSolver(SolverLimits limits, Clock clock, SubjectCountAllocator allocator,
            QuotaApportioner apportioner) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.allocator = Objects.requireNonNull(allocator, "allocator must not be null");
        this.apportioner = Objects.requireNonNull(apportioner, "apportioner must not be null");
    }

    /** Giới hạn đang dùng. */
    public SolverLimits limits() {
        return limits;
    }

    /** Quy tắc tạm gán số câu cho môn được chọn (D25). */
    public SubjectCountAllocator allocator() {
        return allocator;
    }

    /** Quy tắc tạm quy chỉ tiêu tỉ lệ ra số câu (PBI-04 7.3). */
    public QuotaApportioner apportioner() {
        return apportioner;
    }

    /**
     * Giải mọi đường lựa chọn của cấu trúc (liệt kê kiểu lặp; mỗi cách chọn môn của một
     * {@link SubjectChoice} tính một đường cho giới hạn {@code limits.maxPaths}).
     *
     * @param parts  các phần của cấu trúc (FIXED + CHOICE), theo thứ tự cấu trúc
     * @param source nguồn ứng viên theo nhóm (gọi một lần mỗi giá trị {@link GroupQuota})
     * @param mode   CHECK (thứ tự cố định) hoặc GENERATE (xáo theo hạt giống)
     * @return kết quả tổng hợp và kết quả từng đường
     */
    public SolveOutcome solveAll(List<SolvePart> parts, CandidateSource source, SolveMode mode) {
        Objects.requireNonNull(parts, "parts must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Instant deadline = clock.instant().plus(limits.timeout());
        Runnable check = deadlineCheck(deadline);
        long[] solved = {0};
        long[] memoHit = {0};
        long weighted = 0;
        try {
            check.run();
            validateChoiceSubjectLimits(parts);
            PathEnumerator enumerator = new PathEnumerator(parts, allocator, check);
            weighted = enumerator.total();
            long combos = enumerator.combos();
            if (combos == 0) {
                return new SolveOutcome(SolveStatus.INVALID, null, List.of(),
                        new SolveStats(weighted, 0, 0));
            }
            if (weighted > limits.maxPaths()) {
                return new SolveOutcome(SolveStatus.UNCONFIRMED,
                        SolveReason.PATH_LIMIT_EXCEEDED, List.of(),
                        new SolveStats(weighted, 0, 0));
            }
            CandidateSource memoSource = memoized(source);
            Map<SubproblemKey, SubproblemSolver.Outcome> memo = new HashMap<>();
            List<PathResult> results = new ArrayList<>((int) Math.min(combos, 4096));
            for (long i = 0; i < combos; i++) {
                SolvePath path = enumerator.pathAt(i);
                results.add(solvePath(path, memoSource, mode, memo, check, solved, memoHit));
            }
            return new SolveOutcome(aggregate(results), null, results,
                    new SolveStats(weighted, solved[0], memoHit[0]));
        } catch (DeadlineHit e) {
            return new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED,
                    List.of(), new SolveStats(weighted, solved[0], memoHit[0]));
        } catch (ChoiceLimitHit e) {
            return new SolveOutcome(SolveStatus.INVALID,
                    SolveReason.CHOICE_SUBJECT_LIMIT_EXCEEDED, List.of(), SolveStats.EMPTY);
        }
    }

    /**
     * Giải một đường đã dựng sẵn (ví dụ một đường được lấy từ phép liệt kê của solveAll, hoặc dựng
     * tay cho test).
     */
    public PathResult solvePath(SolvePath path, CandidateSource source, SolveMode mode) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Instant deadline = clock.instant().plus(limits.timeout());
        Runnable check = deadlineCheck(deadline);
        try {
            return solvePath(path, memoized(source), mode, new HashMap<>(), check,
                    new long[1], new long[1]);
        } catch (DeadlineHit e) {
            return PathResult.unconfirmed(path, SolveReason.DEADLINE_EXCEEDED, null, null);
        }
    }

    /**
     * Chế độ xem trước: giải đúng một cách chọn môn của một {@link SubjectChoice} — các môn trong
     * {@code subjectCodes} (tập con đúng {@code choice.choose} môn, số câu gán theo
     * {@link SubjectCountAllocator} theo thứ tự cấu trúc, không theo thứ tự bấm chọn) — với hạt
     * giống {@code seed}: cùng hạt giống và cùng đầu vào cho cùng một đề (spec 5.4, D25).
     *
     * @return kết quả của đường con (một SubjectPick); INVALID khi không xếp được
     */
    public PathResult solvePick(SubjectChoice choice, List<String> subjectCodes, long seed,
            CandidateSource source) {
        Objects.requireNonNull(choice, "choice must not be null");
        Objects.requireNonNull(subjectCodes, "subjectCodes must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Instant deadline = clock.instant().plus(limits.timeout());
        Runnable check = deadlineCheck(deadline);
        try {
            if (choice.sections().size() > limits.maxChoiceSubjects()) {
                return PathResult.invalid(
                        new SolvePath(0, List.of(), List.of(), List.of(choice), List.of()),
                        SolveReason.CHOICE_SUBJECT_LIMIT_EXCEEDED, null, null);
            }
            Set<String> wanted = new HashSet<>(subjectCodes);
            if (wanted.size() != choice.choose()) {
                throw new IllegalArgumentException(
                        "subjectCodes must contain exactly choose=" + choice.choose()
                                + " distinct subjects");
            }
            List<SectionOnPath> picked = new ArrayList<>(choice.choose());
            for (SectionOnPath s : choice.sections()) {
                if (wanted.contains(s.subjectCode())) {
                    picked.add(s);
                }
            }
            if (picked.size() != choice.choose()) {
                throw new IllegalArgumentException(
                        "subjectCodes must be a subset of the choice's sections");
            }
            int[] counts = allocator.allocate(choice,
                    picked.stream().map(SectionOnPath::subjectCode).toList());
            SubjectPick pick = new SubjectPick(choice.optionId(), picked,
                    toList(counts));
            SolvePath path = new SolvePath(0,
                    List.of(new ChosenOption(null, choice.optionId())), List.of(),
                    List.of(choice), List.of(List.of(pick)));
            PathResult single = evalTuple(path, List.of(pick), memoized(source),
                    SolveMode.generate(seed), new HashMap<>(), check, new long[1], new long[1]);
            return single;
        } catch (DeadlineHit e) {
            return PathResult.unconfirmed(
                    new SolvePath(0, List.of(), List.of(), List.of(choice), List.of()),
                    SolveReason.DEADLINE_EXCEEDED, null, null);
        }
    }

    // ---------------------------------------------------------------- nội bộ

    /**
     * Giải một đường-kết-quả: VALID khi mọi cách chọn môn (tích các picks) VALID; INVALID khi ít
     * nhất một cách INVALID (failingSelections chỉ đúng các cách lỗi); còn lại UNCONFIRMED.
     */
    private PathResult solvePath(SolvePath path, CandidateSource source, SolveMode mode,
            Map<SubproblemKey, SubproblemSolver.Outcome> memo, Runnable check,
            long[] solved, long[] memoHit) {
        check.run();
        if (path.choices().isEmpty() || path.picks().isEmpty()) {
            return evalTuple(path, List.of(), source, mode, memo, check, solved, memoHit);
        }
        // tích các cách chọn môn của các phương án SUBJECT_CHOICE trên đường
        List<List<SubjectPick>> tuples = new ArrayList<>();
        product(path.picks(), 0, new SubjectPick[path.picks().size()], tuples, check);
        SolveStatus worst = SolveStatus.VALID;
        SolveReason reason = null;
        String subjectCode = null;
        String groupId = null;
        List<SubjectPick> failing = new ArrayList<>();
        Map<SubproblemKey, SubjectWitness> witnesses = new LinkedHashMap<>();
        for (List<SubjectPick> tuple : tuples) {
            check.run();
            PathResult r = evalTuple(path, tuple, source, mode, memo, check, solved, memoHit);
            if (r.status() == SolveStatus.VALID) {
                for (SubjectWitness w : r.witness()) {
                    witnesses.put(new SubproblemKey(w.subjectCode(), List.of(), w.resolvedCount()),
                            w);
                }
                continue;
            }
            failing.addAll(tuple);
            if (r.status() == SolveStatus.INVALID) {
                if (worst != SolveStatus.INVALID) {
                    worst = SolveStatus.INVALID;
                    reason = r.reason();
                    subjectCode = r.subjectCode();
                    groupId = r.groupId();
                }
            } else {
                if (worst == SolveStatus.VALID) {
                    worst = SolveStatus.UNCONFIRMED;
                    reason = r.reason();
                    subjectCode = r.subjectCode();
                    groupId = r.groupId();
                }
            }
        }
        if (worst == SolveStatus.VALID) {
            return PathResult.valid(path, new ArrayList<>(witnesses.values()));
        }
        if (worst == SolveStatus.INVALID) {
            return PathResult.invalidPicks(path, reason, subjectCode, groupId, failing);
        }
        return PathResult.unconfirmed(path, reason, subjectCode, groupId);
    }

    /**
     * Giải một đường con (các môn cố định + một tổ hợp cách chọn môn): tách bài toán con theo môn,
     * nhớ theo {@link SubproblemKey}. Trả PathResult của đường con (không failingSelections).
     */
    private PathResult evalTuple(SolvePath path, List<SubjectPick> tuple, CandidateSource source,
            SolveMode mode, Map<SubproblemKey, SubproblemSolver.Outcome> memo, Runnable check,
            long[] solved, long[] memoHit) {
        List<SectionOnPath> sections = new ArrayList<>(path.sections());
        for (SubjectPick pick : tuple) {
            sections.addAll(pick.resolvedSections());
        }
        // hai nhóm trên đường con trùng groupId → lỗi cấu hình (R1-3); chỉ các môn được chọn của
        // tổ hợp này nằm trên đường
        Set<String> seen = new HashSet<>();
        for (SectionOnPath s : sections) {
            for (GroupQuota g : s.groups()) {
                if (!seen.add(g.groupId())) {
                    return PathResult.invalid(path, SolveReason.DUPLICATE_GROUP_ID,
                            s.subjectCode(), g.groupId());
                }
            }
        }
        // gộp các môn cùng mã theo thứ tự xuất hiện
        Map<String, List<SectionOnPath>> bySubject = new LinkedHashMap<>();
        for (SectionOnPath s : sections) {
            bySubject.computeIfAbsent(s.subjectCode(), k -> new ArrayList<>()).add(s);
        }
        Random rng = (mode instanceof SolveMode.Generate g) ? new Random(g.seed()) : null;
        SubproblemSolver solver = new SubproblemSolver(check);
        List<SubjectWitness> witnesses = new ArrayList<>(bySubject.size());
        for (Map.Entry<String, List<SectionOnPath>> e : bySubject.entrySet()) {
            check.run();
            List<SectionOnPath> sub = e.getValue();
            int count = 0;
            for (SectionOnPath s : sub) {
                count += s.questionCount() == null ? 0 : s.questionCount();
            }
            Prep.Subproblem problem = new Prep.Subproblem(e.getKey(), count, sub);
            SubproblemKey key = problem.key();
            SubproblemSolver.Outcome outcome = memo.get(key);
            if (outcome != null) {
                memoHit[0]++;
            } else {
                solved[0]++;
                List<List<PathCandidate>> pools = fetchPools(problem, source, rng, check);
                if (pools == null) {
                    outcome = SubproblemSolver.Outcome.invalid(
                            SolveReason.CANDIDATE_LIMIT_EXCEEDED, null);
                } else {
                    outcome = solver.solve(problem, pools, apportioner);
                }
                memo.put(key, outcome);
            }
            if (outcome.status() != SolveStatus.VALID) {
                if (outcome.status() == SolveStatus.INVALID) {
                    return PathResult.invalid(path, outcome.reason(), e.getKey(),
                            outcome.groupId());
                }
                return PathResult.unconfirmed(path, outcome.reason(), e.getKey(),
                        outcome.groupId());
            }
            witnesses.add(new SubjectWitness(e.getKey(), count, outcome.witness()));
        }
        return PathResult.valid(path, witnesses);
    }

    /**
     * Nguồn của từng nhóm của bài toán con (đã nhớ theo {@link GroupQuota}), xáo theo hạt giống
     * ở chế độ GENERATE. Trả null khi một nhóm vượt giới hạn ứng viên.
     */
    private List<List<PathCandidate>> fetchPools(Prep.Subproblem problem, CandidateSource source,
            Random rng, Runnable check) {
        List<GroupQuota> flat = new ArrayList<>();
        for (SectionOnPath s : problem.sections()) {
            flat.addAll(s.groups());
            check.run();
        }
        List<List<PathCandidate>> pools = new ArrayList<>(flat.size());
        for (GroupQuota g : flat) {
            List<PathCandidate> raw = source.candidatesFor(g);
            if (raw == null) {
                raw = List.of();
            }
            if (raw.size() > limits.maxCandidatesPerBlock()) {
                return null;
            }
            if (rng != null) {
                List<PathCandidate> copy = new ArrayList<>(raw);
                Collections.shuffle(copy, rng);
                raw = copy;
            }
            pools.add(raw);
        }
        return pools;
    }

    /** Phương án SUBJECT_CHOICE có nhiều hơn {@code maxChoiceSubjects} môn → ném lỗi nghiệp vụ. */
    private void validateChoiceSubjectLimits(List<SolvePart> parts) {
        for (SolvePart p : parts) {
            if (p instanceof SolvePart.Choice c) {
                for (SolveOption o : c.options()) {
                    if (o instanceof SubjectChoice sc
                            && sc.sections().size() > limits.maxChoiceSubjects()) {
                        throw new ChoiceLimitHit();
                    }
                }
            }
        }
    }

    /** Ném khi một SubjectChoice vượt maxChoiceSubjects; bắt ở solveAll thành INVALID. */
    private static final class ChoiceLimitHit extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Ném khi hai nhóm trên đường trùng groupId; bắt thành INVALID(DUPLICATE_GROUP_ID). */
    private static final class DuplicateGroupHit extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DuplicateGroupHit(String groupId) {
            super(groupId);
        }
    }
    /** Nhớ nguồn theo toàn bộ giá trị {@link GroupQuota} (R1-3), không theo riêng groupId. */
    private static CandidateSource memoized(CandidateSource source) {
        Map<GroupQuota, List<PathCandidate>> cache = new HashMap<>();
        return g -> cache.computeIfAbsent(g, source::candidatesFor);
    }

    private Runnable deadlineCheck(Instant deadline) {
        return () -> {
            if (!clock.instant().isBefore(deadline)) {
                throw new DeadlineHit();
            }
        };
    }

    private static void product(List<List<SubjectPick>> options, int i, SubjectPick[] cur,
            List<List<SubjectPick>> out, Runnable check) {
        if (i == options.size()) {
            out.add(List.of(cur.clone()));
            return;
        }
        for (SubjectPick p : options.get(i)) {
            check.run();
            cur[i] = p;
            product(options, i + 1, cur, out, check);
        }
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> out = new ArrayList<>(values.length);
        for (int v : values) {
            out.add(v);
        }
        return out;
    }

    private static SolveStatus aggregate(List<PathResult> results) {
        SolveStatus worst = SolveStatus.VALID;
        for (PathResult r : results) {
            switch (r.status()) {
                case INVALID -> {
                    return SolveStatus.INVALID;
                }
                case UNCONFIRMED -> worst = SolveStatus.UNCONFIRMED;
                case VALID -> {
                }
            }
        }
        return worst;
    }
}
