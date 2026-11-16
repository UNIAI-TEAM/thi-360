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
        // đường đã giải xong giữ nguyên khi đường sau hết hạn (R2-7: INVALID > UNCONFIRMED > VALID)
        List<PathResult> results = new ArrayList<>();
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
            for (long i = 0; i < combos; i++) {
                SolvePath path = enumerator.pathAt(i);
                results.add(solvePath(path, memoSource, mode, memo, check, solved, memoHit));
            }
            return new SolveOutcome(aggregate(results), null, results,
                    new SolveStats(weighted, solved[0], memoHit[0]));
        } catch (DeadlineHit e) {
            // giữ kết quả các đường đã giải xong (R2-7): INVALID > UNCONFIRMED; đường đang giải
            // dở không có trong results
            return new SolveOutcome(aggregateAfterDeadline(results),
                    SolveReason.DEADLINE_EXCEEDED, results,
                    new SolveStats(weighted, solved[0], memoHit[0]));
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
        if (path.choices().isEmpty()) {
            return evalTuple(path, List.of(), source, mode, memo, check, solved, memoHit);
        }
        List<List<SubjectPick>> expanded = new ArrayList<>();
        long combinations = 1;
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (choice.sections().size() > limits.maxChoiceSubjects()) {
                return PathResult.invalid(path, SolveReason.CHOICE_SUBJECT_LIMIT_EXCEEDED, null, null);
            }
            List<SubjectPick> picks = i < path.picks().size() && !path.picks().get(i).isEmpty()
                    ? path.picks().get(i) : choice.picks(allocator);
            if (picks.size() > limits.maxPaths() / combinations) {
                return PathResult.unconfirmed(path, SolveReason.PATH_LIMIT_EXCEEDED, null, null);
            }
            combinations *= picks.size();
            expanded.add(picks);
        }
        List<List<SubjectPick>> tuples = new ArrayList<>();
        product(expanded, 0, new SubjectPick[expanded.size()], tuples, check);
        SolveStatus worst = SolveStatus.VALID;
        SolveReason reason = null;
        String subjectCode = null;
        String groupId = null;
        QuotaRef failingQuota = null;
        List<SubjectPick> failing = new ArrayList<>();
        Map<SubproblemKey, SubjectWitness> witnesses = new LinkedHashMap<>();
        for (List<SubjectPick> tuple : tuples) {
            PathResult r;
            try {
                check.run();
                r = evalTuple(path, tuple, source, mode, memo, check, solved, memoHit);
            } catch (DeadlineHit e) {
                if (worst == SolveStatus.INVALID) {
                    return PathResult.invalidPicks(path, reason, subjectCode, groupId, failing,
                            failingQuota);
                }
                return PathResult.unconfirmed(path, SolveReason.DEADLINE_EXCEEDED, null, null);
            }
            if (r.status() == SolveStatus.VALID) {
                List<SectionOnPath> sections = new ArrayList<>(path.sections());
                for (SubjectPick pick : tuple) {
                    sections.addAll(pick.resolvedSections());
                }
                for (SubjectWitness w : r.witness()) {
                    List<SectionOnPath> own = sections.stream()
                            .filter(s -> s.subjectCode().equals(w.subjectCode())).toList();
                    witnesses.put(new SubproblemKey(w.subjectCode(), own, w.resolvedCount()), w);
                }
                continue;
            }
            if (r.status() == SolveStatus.INVALID) {
                failing.addAll(tuple);
                if (worst != SolveStatus.INVALID) {
                    worst = SolveStatus.INVALID;
                    reason = r.reason();
                    subjectCode = r.subjectCode();
                    groupId = r.groupId();
                    failingQuota = r.failingQuota();
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
            return PathResult.invalidPicks(path, reason, subjectCode, groupId, failing, failingQuota);
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
                String[] limitGroup = new String[1];
                List<List<PathCandidate>> pools = fetchPools(problem, source, rng, check,
                        limitGroup);
                if (pools == null) {
                    // vượt giới hạn ứng viên: nguồn cạn, không phải bất khả thi → UNCONFIRMED
                    outcome = SubproblemSolver.Outcome.unconfirmed(
                            SolveReason.CANDIDATE_LIMIT_EXCEEDED, limitGroup[0]);
                } else {
                    outcome = solver.solve(problem, pools, apportioner);
                }
                memo.put(key, outcome);
            }
            if (outcome.status() != SolveStatus.VALID) {
                if (outcome.status() == SolveStatus.INVALID) {
                    return PathResult.invalid(path, outcome.reason(), e.getKey(),
                            outcome.groupId(), outcome.failingQuota());
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
     * ở chế độ GENERATE. Trả null khi một nhóm vượt giới hạn ứng viên (ghi {@code limitGroup[0]});
     * kiểm hạn trước và ngay sau mỗi lần gọi nguồn để không truy vấn thêm khi đã hết hạn (R2-8).
     */
    private List<List<PathCandidate>> fetchPools(Prep.Subproblem problem, CandidateSource source,
            Random rng, Runnable check, String[] limitGroup) {
        List<GroupQuota> flat = new ArrayList<>();
        for (SectionOnPath s : problem.sections()) {
            flat.addAll(s.groups());
            check.run();
        }
        List<List<PathCandidate>> pools = new ArrayList<>(flat.size());
        for (GroupQuota g : flat) {
            check.run();
            List<PathCandidate> raw = source.candidatesFor(g);
            check.run();
            if (raw == null) {
                raw = List.of();
            }
            if (raw.size() > limits.maxCandidatesPerBlock()) {
                limitGroup[0] = g.groupId();
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

    /**
     * Tổng hợp các đường đã giải xong trước khi hết hạn (R2-7): đường INVALID đã xác lập vẫn làm
     * cả yêu cầu INVALID; còn lại UNCONFIRMED vì phần chưa giải (đường dang dở và các đường sau)
     * không kết luận được.
     */
    private static SolveStatus aggregateAfterDeadline(List<PathResult> results) {
        for (PathResult r : results) {
            if (r.status() == SolveStatus.INVALID) {
                return SolveStatus.INVALID;
            }
        }
        return SolveStatus.UNCONFIRMED;
    }
}
