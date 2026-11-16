package vn.unicom.thi360.exam.domain.solver.oracle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.ChosenOption;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.MapidGroupQuota;
import vn.unicom.thi360.exam.domain.solver.CompetencyQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.oracle.OracleSupport.ResolvedQuota;

/**
 * Oracle độc lập của W-ORACLE: duyệt vét cạn mọi tổ hợp ứng viên cho các ca nhỏ (≤ 4 nhóm trên
 * ≤ 3 môn, ≤ 12 ứng viên mỗi nhóm, chùm 2–3 câu con, tối đa một {@link SubjectChoice}) và kết
 * luận VALID/INVALID theo đúng spec 5.4 — không có hạn thời gian nên không trả UNCONFIRMED.
 *
 * <p>Quy tắc suy ra từ spec (độc lập với cài đặt bộ giải):
 * <ul>
 *   <li>đường tách thành bài toán con theo mã môn, gộp các {@link SectionOnPath} cùng môn;
 *   <li>nhóm SINGLE lấy đúng {@code questionCount} đơn vị ({@code null} = phần còn lại); nhóm SET
 *       lấy đúng {@code setCount} ứng viên, mỗi ứng viên đúng {@code setSize} đơn vị;
 *   <li>một đơn vị đếm tính một lần vào tổng và đồng thời vào mọi chỉ tiêu của nhóm lẫn của môn
 *       (B9); chỉ tiêu đếm cả câu con của chùm;
 *   <li>mỗi câu / phiên bản / câu con dùng tối đa một lần trên cả đường;
 *   <li>đường có {@link SubjectChoice}: VALID khi mọi cách chọn môn VALID, INVALID khi có ít nhất
 *       một cách INVALID (kèm {@code failingPickKeys} đúng các cách chọn lỗi);
 *   <li>chỉ tiêu PERCENT quy bằng phần dư lớn nhất ({@link OracleSupport#apportion}).
 * </ul>
 */
public final class BruteForceOracle {

    /** Ngân sách duyệt: quá thì {@link IllegalStateException} (ca sinh ra phải nhỏ hơn ngưỡng). */
    private static final long MAX_NODES = 2_000_000;

    /** Oracle với quy tắc gán số câu theo thứ tự cấu trúc (D25, tự cài từ spec). */
    public BruteForceOracle() {
    }

    // ------------------------------------------------------------ kết quả

    /**
     * Kết luận của oracle cho một đường (tổng hợp) hoặc một đường con của cách chọn.
     *
     * @param status          VALID hoặc INVALID (oracle không có UNCONFIRMED)
     * @param failingPickKeys {@link OracleSupport#pickKey} của các cách chọn môn lỗi; rỗng khi
     *                        VALID hoặc khi đường không có SUBJECT_CHOICE
     */
    public record Eval(SolveStatus status, Set<String> failingPickKeys) {

        public boolean valid() {
            return status == SolveStatus.VALID;
        }
    }

    // ------------------------------------------------------------ API chính

    /**
     * Giải một đường con đã mở đầy đủ (mọi môn có {@code questionCount}): VALID khi tồn tại một
     * phép xếp ứng viên thỏa mọi ràng buộc trên toàn đường.
     */
    public boolean solveSubpath(List<SectionOnPath> resolvedSections, CandidateSource source) {
        Objects.requireNonNull(source, "source");
        return new Subpath(resolvedSections, source).search();
    }

    /**
     * Kết luận cho một {@link SolvePath} (đầu vào của {@code solvePath} hoặc một phần tử
     * {@code SolveOutcome.results}): phần {@code picks} rỗng nghĩa là "mọi cách chọn"; một cách
     * chọn mỗi phương án nghĩa là đường con đã mở.
     */
    public Eval evaluate(SolvePath path, CandidateSource source) {
        List<List<SubjectPick>> combos = combosOf(path);
        Set<String> failing = new HashSet<>();
        boolean allValid = true;
        for (List<SubjectPick> combo : combos) {
            if (!solveSubpath(resolvedSectionsOf(path, combo), source)) {
                allValid = false;
                for (SubjectPick pick : combo) {
                    failing.add(OracleSupport.pickKey(pick));
                }
            }
        }
        return new Eval(allValid ? SolveStatus.VALID : SolveStatus.INVALID, failing);
    }

    /**
     * Kết luận cho mọi đường mà {@code parts} liệt kê (mỗi cách chọn môn tính một đường), trả map
     * theo {@link #pathKey} của từng dạng đường: khóa đường tổng hợp ({@link #aggregateKey}) cho
     * mỗi đường liệt kê được, cộng khóa từng đường con của từng tổ hợp cách chọn.
     */
    public Map<String, Eval> evaluateAll(List<SolvePart> parts, CandidateSource source) {
        Map<String, Eval> out = new LinkedHashMap<>();
        for (SolvePath path : expand(parts)) {
            Eval agg = evaluate(path, source);
            out.put(pathKey(path), agg);
            List<List<SubjectPick>> combos = combosOf(path);
            if (!combos.isEmpty()) {
                for (List<SubjectPick> combo : combos) {
                    SolvePath sub = subPath(path, combo);
                    Eval e = out.get(pathKey(sub));
                    if (e == null) {
                        boolean valid = solveSubpath(resolvedSectionsOf(path, combo), source);
                        Set<String> failing = valid ? Set.of()
                                : new HashSet<>(combo.stream()
                                        .map(OracleSupport::pickKey).toList());
                        e = new Eval(valid ? SolveStatus.VALID : SolveStatus.INVALID, failing);
                        out.put(pathKey(sub), e);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ liệt kê đường / cách chọn

    /**
     * Mở {@code parts} thành các đường (tích các phương án của phần tự chọn), mỗi đường mang
     * {@code chosen} + môn cố định + phương án {@link SubjectChoice} đã chọn (chưa mở cách chọn).
     */
    public List<SolvePath> expand(List<SolvePart> parts) {
        List<Acc> acc = new ArrayList<>();
        acc.add(new Acc());
        for (SolvePart part : parts) {
            List<Acc> next = new ArrayList<>();
            switch (part) {
                case SolvePart.Fixed f -> {
                    for (Acc a : acc) {
                        Acc b = a.copy();
                        b.sections.addAll(f.sections());
                        next.add(b);
                    }
                }
                case SolvePart.Choice c -> {
                    for (Acc a : acc) {
                        for (SolveOption option : c.options()) {
                            Acc b = a.copy();
                            b.chosen.add(new ChosenOption(c.partId(), optionIdOf(option)));
                            b.sections.addAll(c.commonSections());
                            switch (option) {
                                case SolveOption.Sections s -> b.sections.addAll(s.sections());
                                case SubjectChoice sc -> b.choices.add(sc);
                            }
                            next.add(b);
                        }
                    }
                }
            }
            acc = next;
        }
        List<SolvePath> out = new ArrayList<>(acc.size());
        long idx = 0;
        for (Acc a : acc) {
            out.add(new SolvePath(idx++, a.chosen, a.sections, a.choices, List.of()));
        }
        return out;
    }

    /**
     * Các tổ hợp cách chọn môn của một đường: tích theo phần tử {@code choices}; phần tử
     * {@code picks} không rỗng thì dùng đúng các cách chọn đó (đường con đã mở hoặc tập con).
     */
    public List<List<SubjectPick>> combosOf(SolvePath path) {
        if (path.choices().isEmpty()) {
            // tích Đề-các của không cách chọn = một tổ hợp rỗng: đường không SUBJECT_CHOICE
            // vẫn là một đường con cần đánh giá (spec 5.4), không phải không đường con
            return List.of(List.of());
        }
        List<List<SubjectPick>> perChoice = new ArrayList<>(path.choices().size());
        for (int i = 0; i < path.choices().size(); i++) {
            List<SubjectPick> provided = i < path.picks().size() ? path.picks().get(i) : List.of();
            perChoice.add(provided.isEmpty()
                    ? OracleSupport.picksOf(path.choices().get(i))
                    : provided);
        }
        List<List<SubjectPick>> combos = new ArrayList<>();
        Deque<SubjectPick> stack = new ArrayDeque<>();
        cartesian(perChoice, 0, stack, combos);
        return combos;
    }

    private void cartesian(List<List<SubjectPick>> perChoice, int i, Deque<SubjectPick> stack,
            List<List<SubjectPick>> out) {
        if (i == perChoice.size()) {
            out.add(List.copyOf(stack));
            return;
        }
        for (SubjectPick pick : perChoice.get(i)) {
            stack.addLast(pick);
            cartesian(perChoice, i + 1, stack, out);
            stack.removeLast();
        }
    }

    /** Các môn của đường con ứng với một tổ hợp cách chọn, số câu đã quyết. */
    public List<SectionOnPath> resolvedSectionsOf(SolvePath path, List<SubjectPick> combo) {
        List<SectionOnPath> out = new ArrayList<>(path.sections());
        for (SubjectPick pick : combo) {
            out.addAll(pick.resolvedSections());
        }
        return out;
    }

    /** Đường con của một tổ hợp cách chọn (đúng một cách chọn mỗi phương án). */
    public SolvePath subPath(SolvePath path, List<SubjectPick> combo) {
        return new SolvePath(path.pathIndex(), path.chosen(), path.sections(), path.choices(),
                combo.stream().map(List::of).toList());
    }

    // ------------------------------------------------------------ khóa đường

    /**
     * Khóa định danh đường để so oracle với kết quả bộ giải: phương án đã chọn + các môn cố định
     * + phương án SUBJECT_CHOICE + chữ ký cách chọn. Phần tử {@code picks} rỗng — hoặc liệt kê
     * đủ mọi cách chọn (solver materialize đường tổng hợp) — mã hóa là {@code ALL}, một cách
     * chọn mã hóa khóa riêng của nó — nên khóa của đường tổng hợp và của đường con từng cách
     * chọn không trùng nhau.
     */
    public static String pathKey(SolvePath path) {
        StringBuilder sb = new StringBuilder(aggregateKey(path));
        sb.append("||");
        for (int i = 0; i < path.choices().size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            List<SubjectPick> picks = i < path.picks().size() ? path.picks().get(i) : List.of();
            // đường tổng hợp của solver liệt kê đủ mọi cách chọn → cùng khóa "ALL" với oracle
            if (picks.isEmpty()
                    || picks.size() == OracleSupport.picksOf(path.choices().get(i)).size()) {
                sb.append("ALL");
            } else if (picks.size() == 1) {
                sb.append(OracleSupport.pickKey(picks.get(0)));
            } else {
                sb.append('{');
                for (int j = 0; j < picks.size(); j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    sb.append(OracleSupport.pickKey(picks.get(j)));
                }
                sb.append('}');
            }
        }
        return sb.toString();
    }

    /** Khóa mức đường liệt kê (bỏ chi tiết cách chọn): chosen + môn cố định + các SUBJECT_CHOICE. */
    public static String aggregateKey(SolvePath path) {
        StringBuilder sb = new StringBuilder();
        for (ChosenOption c : path.chosen()) {
            sb.append(c.partId()).append(':').append(c.optionId()).append(';');
        }
        sb.append('|');
        for (SectionOnPath s : path.sections()) {
            sb.append(s.sectionId()).append(',');
        }
        sb.append('|');
        for (SubjectChoice c : path.choices()) {
            sb.append(c.optionId()).append(',');
        }
        return sb.toString();
    }

    private static String optionIdOf(SolveOption option) {
        return switch (option) {
            case SolveOption.Sections s -> s.optionId();
            case SubjectChoice sc -> sc.optionId();
        };
    }

    private static final class Acc {
        List<ChosenOption> chosen = new ArrayList<>();
        List<SectionOnPath> sections = new ArrayList<>();
        List<SubjectChoice> choices = new ArrayList<>();

        Acc copy() {
            Acc b = new Acc();
            b.chosen.addAll(chosen);
            b.sections.addAll(sections);
            b.choices.addAll(choices);
            return b;
        }
    }

    // ------------------------------------------------------------ giải một đường con

    /** Bài toán con của một môn: các nhóm đã gộp từ mọi mục cùng mã môn và chỉ tiêu từng mục. */
    private static final class SubjectProblem {
        final List<GroupQuota> groups = new ArrayList<>();
        final List<SectionOnPath> sections = new ArrayList<>();
        final List<ResolvedQuota> quotas = new ArrayList<>();
        /** Số đơn vị mỗi nhóm phải chiếm, song song {@code groups}. */
        final List<Integer> required = new ArrayList<>();
        int totalUnits;
    }

    private final class Subpath {

        private final CandidateSource source;
        private final Map<String, SubjectProblem> problems = new LinkedHashMap<>();
        private final List<GroupQuota> allGroups = new ArrayList<>();
        private final Map<GroupQuota, SubjectProblem> groupSubject = new HashMap<>();
        private final Map<GroupQuota, List<Assignment>> pickSets = new HashMap<>();
        private long nodes;

        Subpath(List<SectionOnPath> sections, CandidateSource source) {
            this.source = source;
            for (SectionOnPath section : sections) {
                SubjectProblem problem = problems.computeIfAbsent(section.subjectCode(),
                        k -> new SubjectProblem());
                problem.sections.add(section);
                problem.quotas.add(OracleSupport.resolveSubjectQuota(section.subjectQuota(),
                        section.questionCount()));
                problem.totalUnits += section.questionCount();
                int[] req = OracleSupport.groupUnitRequirements(section);
                for (int i = 0; i < section.groups().size(); i++) {
                    GroupQuota group = section.groups().get(i);
                    problem.groups.add(group);
                    problem.required.add(req[i]);
                    allGroups.add(group);
                    groupSubject.put(group, problem);
                }
            }
        }

        boolean search() {
            if (!uniqueGroupIds()) {
                return false;
            }
            for (GroupQuota group : allGroups) {
                List<Assignment> sets = enumeratePickSets(group);
                if (sets.isEmpty()) {
                    return false;
                }
                pickSets.put(group, sets);
            }
            Map<String, Map<String, Integer>> counters = new HashMap<>();
            for (String subject : problems.keySet()) {
                counters.put(subject, new HashMap<>());
            }
            return dfs(0, new HashSet<>(), counters);
        }

        private boolean uniqueGroupIds() {
            Set<String> seen = new HashSet<>();
            for (GroupQuota g : allGroups) {
                if (!seen.add(g.groupId())) {
                    return false;
                }
            }
            return true;
        }

        // ---- duyệt tập chọn của một nhóm

        private List<Assignment> enumeratePickSets(GroupQuota group) {
            SubjectProblem problem = groupSubject.get(group);
            int req = problem.required.get(problem.groups.indexOf(group));
            ResolvedQuota groupQuota = OracleSupport.resolveGroupQuota(group, req);
            List<PathCandidate> eligible = new ArrayList<>();
            for (PathCandidate c : source.candidatesFor(group)) {
                if (!OracleSupport.eligibleForGroup(group, c)) {
                    continue;
                }
                boolean admissible = true;
                for (CountingUnit u : c.units()) {
                    if (!OracleSupport.admissibleInSubject(problem.quotas, u)
                            || !OracleSupport.admissibleInSubject(List.of(groupQuota), u)) {
                        admissible = false;
                        break;
                    }
                }
                if (admissible) {
                    eligible.add(c);
                }
            }
            List<Assignment> out = new ArrayList<>();
            if (req == 0 && group.kind() == GroupQuota.GroupKind.SINGLE) {
                out.add(new Assignment(List.of()));
                return out;
            }
            enumerate(group, eligible, 0, req, 0, new HashSet<>(), new ArrayDeque<>(), out);
            return out;
        }

        private void enumerate(GroupQuota group, List<PathCandidate> eligible, int idx, int req,
                int taken, Set<String> usedIds, Deque<PathCandidate> chosen,
                List<Assignment> out) {
            if (out.size() > 65_536) {
                throw new IllegalStateException("pick-set enumeration over budget");
            }
            if (group.kind() == GroupQuota.GroupKind.SINGLE) {
                if (taken == req) {
                    Assignment a = new Assignment(List.copyOf(chosen));
                    if (satisfiesGroupQuotas(group, a)) {
                        out.add(a);
                    }
                    return;
                }
            } else if (taken == group.setCount()) {
                Assignment a = new Assignment(List.copyOf(chosen));
                if (satisfiesGroupQuotas(group, a)) {
                    out.add(a);
                }
                return;
            }
            if (idx >= eligible.size()) {
                return;
            }
            PathCandidate c = eligible.get(idx);
            int units = OracleSupport.unitCount(c);
            boolean fits = group.kind() == GroupQuota.GroupKind.SINGLE
                    ? taken + units <= req
                    : taken + 1 <= group.setCount();
            if (fits) {
                Set<String> ids = OracleSupport.occupiedIds(c);
                if (usedIds.stream().noneMatch(ids::contains)) {
                    usedIds.addAll(ids);
                    chosen.addLast(c);
                    enumerate(group, eligible, idx + 1, req,
                            taken + (group.kind() == GroupQuota.GroupKind.SINGLE ? units : 1),
                            usedIds, chosen, out);
                    chosen.removeLast();
                    ids.forEach(usedIds::remove);
                }
            }
            enumerate(group, eligible, idx + 1, req, taken, usedIds, chosen, out);
        }

        /** Chỉ tiêu riêng của nhóm phải khớp đúng trên tập chọn (spec 5.4, javadoc MatrixQuota). */
        private boolean satisfiesGroupQuotas(GroupQuota group, Assignment a) {
            SubjectProblem problem = groupSubject.get(group);
            int req = problem.required.get(problem.groups.indexOf(group));
            ResolvedQuota quota = OracleSupport.resolveGroupQuota(group, req);
            Map<String, Integer> counts = contribution(a, group);
            if (!exactCounts(quota.grades(), counts, "G:")) {
                return false;
            }
            if (!exactCounts(quota.difficulties(), counts, "D:")) {
                return false;
            }
            if (!exactCounts(quota.cells(), counts, "C:")) {
                return false;
            }
            for (CompetencyQuota q : group.competencies()) {
                if (counts.getOrDefault("P:" + q.competencyCode(), 0) != q.quota()) {
                    return false;
                }
            }
            for (MapidGroupQuota q : group.mapidGroups()) {
                int n = 0;
                for (PathCandidate c : a.candidates()) {
                    for (CountingUnit u : c.units()) {
                        if (u.mapidIds().stream().anyMatch(q.nodeIds()::contains)) {
                            n++;
                        }
                    }
                }
                if (n != q.quota()) {
                    return false;
                }
            }
            return true;
        }

        private boolean exactCounts(Map<String, Integer> target, Map<String, Integer> counts,
                String prefix) {
            for (Map.Entry<String, Integer> e : target.entrySet()) {
                if (counts.getOrDefault(prefix + e.getKey(), 0) != e.getValue()) {
                    return false;
                }
            }
            return true;
        }

        private Map<String, Integer> contribution(Assignment a, GroupQuota group) {
            Map<String, Integer> counts = new HashMap<>();
            for (PathCandidate c : a.candidates()) {
                for (CountingUnit u : c.units()) {
                    counts.merge("A:" + u.answerType(), 1, Integer::sum);
                    if (u.gradeCode() != null) {
                        counts.merge("G:" + u.gradeCode(), 1, Integer::sum);
                    }
                    if (u.difficultyCode() != null) {
                        counts.merge("D:" + u.difficultyCode(), 1, Integer::sum);
                    }
                    if (u.gradeCode() != null && u.difficultyCode() != null) {
                        counts.merge("C:" + u.gradeCode() + "" + u.difficultyCode(), 1,
                                Integer::sum);
                    }
                    for (String comp : u.competencyCodes()) {
                        counts.merge("P:" + comp, 1, Integer::sum);
                    }
                }
            }
            return counts;
        }

        // ---- duyệt chung trên mọi nhóm của mọi môn

        private boolean dfs(int groupIdx, Set<String> usedIds,
                Map<String, Map<String, Integer>> counters) {
            if (++nodes > MAX_NODES) {
                throw new IllegalStateException("oracle search over budget (" + MAX_NODES + ")");
            }
            if (groupIdx == allGroups.size()) {
                for (Map.Entry<String, SubjectProblem> e : problems.entrySet()) {
                    Map<String, Integer> counts = counters.get(e.getKey());
                    for (ResolvedQuota q : e.getValue().quotas) {
                        if (!countsMatch(q, counts)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            GroupQuota group = allGroups.get(groupIdx);
            SubjectProblem problem = groupSubject.get(group);
            Map<String, Integer> counts = counters.get(problem.sections.get(0).subjectCode());
            for (Assignment a : pickSets.get(group)) {
                Set<String> ids = new HashSet<>();
                for (PathCandidate c : a.candidates()) {
                    ids.addAll(OracleSupport.occupiedIds(c));
                }
                if (usedIds.stream().anyMatch(ids::contains)) {
                    continue;
                }
                Map<String, Integer> delta = contribution(a, group);
                delta.forEach((k, v) -> counts.merge(k, v, Integer::sum));
                if (!exceeds(problem.quotas, counts)) {
                    usedIds.addAll(ids);
                    if (dfs(groupIdx + 1, usedIds, counters)) {
                        return true;
                    }
                    ids.forEach(usedIds::remove);
                }
                delta.forEach((k, v) -> counts.merge(k, -v, Integer::sum));
            }
            return false;
        }

        /** Chỉ tiêu đã khai vượt số câu khi bộ đếm đang lớn hơn mục tiêu (cắt nhánh). */
        private boolean exceeds(List<ResolvedQuota> quotas, Map<String, Integer> counts) {
            for (ResolvedQuota q : quotas) {
                if (over(q.answerTypes(), counts, "A:") || over(q.grades(), counts, "G:")
                        || over(q.difficulties(), counts, "D:") || over(q.cells(), counts, "C:")) {
                    return true;
                }
            }
            return false;
        }

        private boolean over(Map<String, Integer> target, Map<String, Integer> counts,
                String prefix) {
            for (Map.Entry<String, Integer> e : target.entrySet()) {
                if (counts.getOrDefault(prefix + e.getKey(), 0) > e.getValue()) {
                    return true;
                }
            }
            return false;
        }

        private boolean countsMatch(ResolvedQuota q, Map<String, Integer> counts) {
            return exactCounts(q.answerTypes(), counts, "A:")
                    && exactCounts(q.grades(), counts, "G:")
                    && exactCounts(q.difficulties(), counts, "D:")
                    && exactCounts(q.cells(), counts, "C:");
        }
    }

    /** Một tập ứng viên đủ điều kiện của một nhóm (đã qua kiểm chỉ tiêu riêng của nhóm). */
    private record Assignment(List<PathCandidate> candidates) {
    }
}
