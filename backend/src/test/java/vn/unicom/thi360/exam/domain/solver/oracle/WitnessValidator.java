package vn.unicom.thi360.exam.domain.solver.oracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.CompetencyQuota;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.MapidGroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.exam.domain.solver.oracle.OracleSupport.ResolvedQuota;

/**
 * Kiểm tra lại một đề chứng minh ({@code witness}) mà bộ giải trả kèm đường VALID, độc lập với
 * thuật toán sinh ra nó — quy tắc suy từ spec 5.4 và B9:
 *
 * <ul>
 *   <li>từng {@link QuestionPick} thuộc đúng nguồn {@code CandidateSource} của nhóm (membership)
 *       và đủ điều kiện nhóm: quy tắc chấm, điểm mỗi đơn vị, dạng trả lời / dạng câu con, kích
 *       thước chùm; phạm vi MAPID do nguồn cung cấp;
 *   <li>nhóm SINGLE có đúng số đơn vị (nhóm "lấy phần còn lại" đúng phần còn lại); nhóm SET có
 *       đúng {@code setCount} ứng viên, mỗi ứng viên đúng {@code setSize} đơn vị;
 *   <li>mọi chỉ tiêu của nhóm và của môn khớp đúng (đếm cả câu con của chùm, theo từng đơn vị);
 *       chỉ tiêu của một mục chỉ tính trên các nhóm của chính mục đó (môn trên nhiều mục giữ
 *       phạm vi chỉ tiêu riêng);
 *   <li>mọi mục ({@link SectionOnPath}) nhận đúng số câu của nó: tổng đơn vị trên các nhóm của
 *       mục bằng {@code questionCount}, kể cả khi mục không có nhóm nào;
 *   <li>một câu / phiên bản / câu con chỉ xuất hiện một lần trong một bài toán con (đường con),
 *       kể cả lặp định danh ngay bên trong một ứng viên;
 *   <li>cách chọn môn của đường có {@link SubjectChoice} mang số câu theo phân bổ nhóm
 *       {@code choiceGroup} (B10/D25).
 * </ul>
 *
 * <p>Đường có {@link SubjectChoice}: đề chứng minh lưu theo bài toán con — validator suy ra mọi
 * bài toán con mà các cách chọn sinh ra và khóa theo (môn, số câu, tập nhóm của các mục), không
 * gộp theo (môn, số câu) để hai bài toán con khác mục không nuốt witness của nhau (R2-9).
 */
public final class WitnessValidator {

    private final BruteForceOracle oracle = new BruteForceOracle();

    /** Lỗi của một bước kiểm tra. */
    public record Violation(String code, String detail) {
        @Override
        public String toString() {
            return code + "(" + detail + ")";
        }
    }

    /**
     * Kiểm tra {@code result}: nếu {@code status} là VALID thì mọi ràng buộc của spec phải thỏa
     * trên {@code witness}; trả danh sách vi phạm (rỗng = chứng minh hợp lệ). Kết quả
     * INVALID/UNCONFIRMED chỉ được kiểm hình thức (không kèm chứng minh).
     */
    public List<Violation> validate(PathResult result, CandidateSource source) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(source, "source");
        List<Violation> out = new ArrayList<>();
        if (result.status() != SolveStatus.VALID) {
            if (!result.witness().isEmpty()) {
                out.add(new Violation("WITNESS_ON_NONVALID",
                        "witness must be empty when status is " + result.status()));
            }
            if (result.reason() == null) {
                out.add(new Violation("MISSING_REASON",
                        "non-VALID result must carry a reason"));
            }
            return out;
        }
        if (result.reason() != null) {
            out.add(new Violation("REASON_ON_VALID", "VALID result must not carry a reason"));
        }
        out.addAll(checkPickCounts(result.path()));

        List<Instance> instances = instancesOf(result.path());
        Set<Instance> usedInstances = new HashSet<>();
        Set<String> usedGroupKeys = new HashSet<>();
        for (SubjectWitness sw : result.witness()) {
            List<Instance> candidates = matchingInstances(instances, sw);
            if (candidates.isEmpty()) {
                out.add(new Violation("ORPHAN_WITNESS", "no subproblem matching " + sw.subjectCode()
                        + "|" + sw.resolvedCount() + " for witness groups "
                        + groupIdSet(sw.groups())));
                continue;
            }
            if (candidates.size() > 1) {
                out.add(new Violation("AMBIGUOUS_WITNESS", sw.subjectCode() + "|"
                        + sw.resolvedCount() + " matches " + candidates.size() + " subproblems"));
                continue;
            }
            Instance inst = candidates.get(0);
            if (!usedInstances.add(inst)) {
                out.add(new Violation("DUPLICATE_WITNESS", "duplicate witness for "
                        + inst.key()));
                continue;
            }
            out.addAll(checkInstance(inst, sw, source, usedGroupKeys));
        }
        for (Instance inst : instances) {
            // mọi bài toán con mong đợi cần witness, kể cả khi mục không có nhóm nào: mục
            // số câu dương không nhóm được oracle kết luận INVALID nên một đề VALID khai báo
            // không witness là giả (R2-21); witness của bài toán con rỗng nhóm được kiểm qua
            // SECTION_TOTAL_MISMATCH của chính nó
            if (!usedInstances.contains(inst)) {
                out.add(new Violation("MISSING_WITNESS",
                        "subproblem " + inst.key() + " has no witness"));
            }
        }
        return out;
    }

    /**
     * Các bài toán con khớp một {@link SubjectWitness}: cùng môn và số câu, và tập nhóm của
     * witness nằm trong tập nhóm của bài toán con (witness thiếu nhóm đã bị
     * {@code GROUP_WITNESS_MISSING} xử lý; ứng viên chỉ còn một thì bắt chắc). Khóa theo tập
     * nhóm — vết của "các mục thật" của bài toán con (groupId duy nhất trên đường).
     */
    private List<Instance> matchingInstances(List<Instance> instances, SubjectWitness sw) {
        List<Instance> out = new ArrayList<>();
        Set<String> witnessGroups = groupIdSet(sw.groups());
        for (Instance inst : instances) {
            if (!inst.subjectCode.equals(sw.subjectCode())
                    || inst.total != sw.resolvedCount()) {
                continue;
            }
            if (inst.groupIds().containsAll(witnessGroups)
                    && (witnessGroups.isEmpty() || inst.groupIds().equals(witnessGroups)
                            || onlySubsetMatch(instances, sw, inst))) {
                out.add(inst);
            }
        }
        return out;
    }

    /** Witness chứa một tập con nhóm chỉ được chấp nhận khi nó chọn đúng một bài toán con. */
    private boolean onlySubsetMatch(List<Instance> instances, SubjectWitness sw, Instance cand) {
        Set<String> witnessGroups = groupIdSet(sw.groups());
        int containing = 0;
        for (Instance inst : instances) {
            if (inst.subjectCode.equals(sw.subjectCode()) && inst.total == sw.resolvedCount()
                    && inst.groupIds().containsAll(witnessGroups)) {
                containing++;
            }
        }
        return containing == 1;
    }

    private static Set<String> groupIdSet(List<GroupWitness> groups) {
        Set<String> out = new LinkedHashSet<>();
        for (GroupWitness gw : groups) {
            out.add(gw.groupId());
        }
        return out;
    }

    // ------------------------------------------------------------ bài toán con mong đợi

    /** Một bài toán con (môn, số câu, các mục thật) mà các cách chọn của đường sinh ra. */
    private static final class Instance {
        final String subjectCode;
        int total;
        /** Các mục cùng mã môn, mỗi mục giữ chỉ tiêu và nhóm của riêng nó. */
        final List<SectionOnPath> sections = new ArrayList<>();
        final List<ResolvedQuota> quotas = new ArrayList<>();
        final List<GroupQuota> groups = new ArrayList<>();
        /** Số đơn vị mỗi nhóm phải chiếm, song song {@code groups}. */
        final List<Integer> required = new ArrayList<>();
        /** Mục sở hữu của từng nhóm, song song {@code groups}. */
        final List<SectionOnPath> owner = new ArrayList<>();

        Instance(String subjectCode) {
            this.subjectCode = subjectCode;
        }

        Set<String> groupIds() {
            Set<String> out = new TreeSet<>();
            for (GroupQuota g : groups) {
                out.add(g.groupId());
            }
            return out;
        }

        /** Khóa đầy đủ của bài toán con: môn, số câu và tập nhóm (đứng cho các mục). */
        String key() {
            return subjectCode + "|" + total + "|" + groupIds();
        }

        static Instance of(List<SectionOnPath> merged) {
            Instance inst = new Instance(merged.get(0).subjectCode());
            for (SectionOnPath s : merged) {
                inst.sections.add(s);
                inst.total += s.questionCount();
                inst.quotas.add(OracleSupport.resolveSubjectQuota(s.subjectQuota(),
                        s.questionCount()));
                int[] req = OracleSupport.groupUnitRequirements(s);
                for (int i = 0; i < s.groups().size(); i++) {
                    inst.groups.add(s.groups().get(i));
                    inst.required.add(req[i]);
                    inst.owner.add(s);
                }
            }
            return inst;
        }
    }

    /** Mọi bài toán con (môn, số câu, các mục) mà các đường con của {@code path} sinh ra. */
    private List<Instance> instancesOf(vn.unicom.thi360.exam.domain.solver.SolvePath path) {
        List<List<SectionOnPath>> sectionLists = new ArrayList<>();
        List<List<SubjectPick>> combos = oracle.combosOf(path);
        for (List<SubjectPick> combo : combos) {
            sectionLists.add(oracle.resolvedSectionsOf(path, combo));
        }
        Map<String, Instance> seen = new LinkedHashMap<>();
        for (List<SectionOnPath> sections : sectionLists) {
            Map<String, List<SectionOnPath>> bySubject = new LinkedHashMap<>();
            for (SectionOnPath s : sections) {
                bySubject.computeIfAbsent(s.subjectCode(), k -> new ArrayList<>()).add(s);
            }
            for (List<SectionOnPath> merged : bySubject.values()) {
                Instance inst = Instance.of(merged);
                seen.putIfAbsent(inst.key(), inst);
            }
        }
        return new ArrayList<>(seen.values());
    }

    // ------------------------------------------------------------ số câu của cách chọn

    /** Mọi cách chọn môn trên đường phải nhận một phân bổ hợp lệ (B10/D25). */
    private List<Violation> checkPickCounts(
            vn.unicom.thi360.exam.domain.solver.SolvePath path) {
        List<Violation> out = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (i >= path.picks().size()) {
                continue;
            }
            for (SubjectPick pick : path.picks().get(i)) {
                boolean allowed = false;
                for (List<Integer> counts : OracleSupport.solveAllocationsOf(choice,
                        pick.sections())) {
                    if (counts.equals(pick.counts())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    out.add(new Violation("PICK_COUNTS_MISMATCH",
                            "pick " + OracleSupport.pickKey(pick)
                                    + " is not an allowed allocation"));
                }
            }
        }
        return out;
    }
    // ------------------------------------------------------------ kiểm một bài toán con

    private List<Violation> checkInstance(Instance inst, SubjectWitness sw,
            CandidateSource source, Set<String> usedGroupKeys) {
        List<Violation> out = new ArrayList<>();
        Map<String, Integer> groupIndex = new HashMap<>();
        for (int i = 0; i < inst.groups.size(); i++) {
            if (groupIndex.put(inst.groups.get(i).groupId(), i) != null) {
                out.add(new Violation("DUPLICATE_GROUP_ID",
                        "duplicate groupId " + inst.groups.get(i).groupId()
                                + " in subproblem " + inst.subjectCode));
            }
        }
        Set<String> expected = new LinkedHashSet<>(groupIndex.keySet());
        Set<String> seen = new LinkedHashSet<>();
        Set<String> occupied = new HashSet<>();
        Map<SectionOnPath, Map<String, Integer>> sectionCounts = new LinkedHashMap<>();
        Map<SectionOnPath, Integer> sectionUnits = new LinkedHashMap<>();
        for (SectionOnPath s : inst.sections) {
            sectionCounts.put(s, new HashMap<>());
            sectionUnits.put(s, 0);
        }
        for (GroupWitness gw : sw.groups()) {
            Integer gi = groupIndex.get(gw.groupId());
            if (gi == null) {
                out.add(new Violation("UNKNOWN_GROUP",
                        "witness for group " + gw.groupId() + " not in subproblem"));
                continue;
            }
            if (!seen.add(gw.groupId())) {
                out.add(new Violation("DUPLICATE_GROUP_WITNESS", gw.groupId()));
                continue;
            }
            if (!usedGroupKeys.add(inst.key() + "|" + gw.groupId())) {
                out.add(new Violation("GROUP_IN_TWO_WITNESSES", gw.groupId()));
            }
            SectionOnPath owner = inst.owner.get(gi);
            out.addAll(checkGroup(inst, owner, inst.groups.get(gi), inst.required.get(gi), gw,
                    source, occupied, sectionCounts.get(owner), sectionUnits));
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(seen);
        if (!missing.isEmpty()) {
            out.add(new Violation("GROUP_WITNESS_MISSING", "groups without picks: " + missing));
        }
        // mọi mục phải được lấp đúng số câu của nó: tổng đơn vị trên các nhóm của mục bằng
        // questionCount, kể cả mục không có nhóm (R2-21)
        for (int i = 0; i < inst.sections.size(); i++) {
            SectionOnPath s = inst.sections.get(i);
            int actual = sectionUnits.get(s);
            if (actual != s.questionCount()) {
                out.add(new Violation("SECTION_TOTAL_MISMATCH", "section " + s.sectionId()
                        + " has " + actual + " units, requires " + s.questionCount()));
            }
            out.addAll(checkSubjectQuota(inst, s, inst.quotas.get(i), sectionCounts.get(s)));
        }
        return out;
    }

    private List<Violation> checkGroup(Instance inst, SectionOnPath section, GroupQuota group,
            int required, GroupWitness gw, CandidateSource source, Set<String> occupied,
            Map<String, Integer> sectionCounts, Map<SectionOnPath, Integer> sectionUnits) {
        List<Violation> out = new ArrayList<>();
        List<PathCandidate> pool = source.candidatesFor(group);
        Map<String, PathCandidate> byPickKey = new HashMap<>();
        for (PathCandidate c : pool) {
            byPickKey.put(c.questionId() + "@" + c.versionNo(), c);
        }
        List<PathCandidate> picked = new ArrayList<>();
        for (QuestionPick pick : gw.picks()) {
            PathCandidate c = byPickKey.get(pick.questionId() + "@" + pick.versionNo());
            if (c == null) {
                out.add(new Violation("NOT_IN_SOURCE",
                        pick.questionId() + " v" + pick.versionNo() + " not in source of "
                                + group.groupId()));
                continue;
            }
            picked.add(c);
            // lặp định danh ngay bên trong ứng viên: kiểm trước khi gom id thành Set (R2-18)
            if (OracleSupport.hasInternalIdentityRepeat(c)) {
                out.add(new Violation("INTERNAL_ID_REUSED", c.questionId()
                        + " repeats an identity inside itself in subproblem "
                        + inst.subjectCode));
            }
            for (String id : OracleSupport.occupiedIds(c)) {
                if (!occupied.add(id)) {
                    out.add(new Violation("ID_REUSED",
                            id + " used more than once in subproblem " + section.subjectCode()));
                }
            }
        }
        int units = picked.stream().mapToInt(OracleSupport::unitCount).sum();
        sectionUnits.merge(section, units, Integer::sum);
        switch (group.kind()) {
            case SINGLE, FLEXIBLE_SET -> {
                if (units != required) {
                    out.add(new Violation("GROUP_COUNT_MISMATCH", group.groupId() + " has "
                            + units + " units, requires " + required));
                }
            }
            case SET -> {
                if (picked.size() != group.setCount()) {
                    out.add(new Violation("SET_COUNT_MISMATCH", group.groupId() + " picked "
                            + picked.size() + " sets, requires " + group.setCount()));
                }
                for (PathCandidate c : picked) {
                    if (OracleSupport.unitCount(c) != group.setSize()) {
                        out.add(new Violation("SET_SIZE_MISMATCH", c.questionId()
                                + " has " + OracleSupport.unitCount(c) + " units, requires "
                                + group.setSize()));
                    }
                }
            }
        }
        for (PathCandidate c : picked) {
            if (!OracleSupport.eligibleForGroup(group, c)) {
                out.add(new Violation("INELIGIBLE_PICK",
                        c.questionId() + " violates group constraints of " + group.groupId()
                                + " (scoring rule / points / answer type / set size / child type)"));
            }
        }
        // Chỉ tiêu riêng của nhóm: khớp đúng trên các đơn vị đã chọn (spec 5.4).
        ResolvedQuota groupQuota = OracleSupport.resolveGroupQuota(group, required);
        Map<String, Integer> counts = new HashMap<>();
        for (PathCandidate c : picked) {
            for (CountingUnit u : c.units()) {
                addUnit(counts, u);
                addUnit(sectionCounts, u);
            }
        }
        checkExact(out, "GROUP_GRADE_QUOTA", groupQuota.grades(), counts, "G:", group.groupId());
        checkExact(out, "GROUP_DIFFICULTY_QUOTA", groupQuota.difficulties(), counts, "D:",
                group.groupId());
        checkExact(out, "GROUP_CELL_QUOTA", groupQuota.cells(), counts, "C:", group.groupId());
        for (CompetencyQuota q : group.competencies()) {
            int actual = counts.getOrDefault("P:" + q.competencyCode(), 0);
            if (actual != q.quota()) {
                out.add(new Violation("GROUP_COMPETENCY_QUOTA", group.groupId() + " competency "
                        + q.competencyCode() + " is " + actual + ", requires " + q.quota()));
            }
        }
        for (MapidGroupQuota q : group.mapidGroups()) {
            int actual = 0;
            for (PathCandidate c : picked) {
                for (CountingUnit u : c.units()) {
                    if (u.mapidIds().stream().anyMatch(q.nodeIds()::contains)) {
                        actual++;
                    }
                }
            }
            if (actual != q.quota()) {
                out.add(new Violation("GROUP_MAPID_QUOTA", group.groupId() + " mapid group "
                        + q.groupKey() + " is " + actual + ", requires " + q.quota()));
            }
        }
        return out;
    }

    private void addUnit(Map<String, Integer> counts, CountingUnit u) {
        counts.merge("A:" + u.answerType(), 1, Integer::sum);
        if (u.gradeCode() != null) {
            counts.merge("G:" + u.gradeCode(), 1, Integer::sum);
        }
        if (u.difficultyCode() != null) {
            counts.merge("D:" + u.difficultyCode(), 1, Integer::sum);
        }
        if (u.gradeCode() != null && u.difficultyCode() != null) {
            counts.merge("C:" + OracleSupport.cellKey(u.gradeCode(), u.difficultyCode()), 1,
                    Integer::sum);
        }
        for (String comp : u.competencyCodes()) {
            counts.merge("P:" + comp, 1, Integer::sum);
        }
    }

    private void checkExact(List<Violation> out, String code, Map<String, Integer> target,
            Map<String, Integer> counts, String prefix, String where) {
        for (Map.Entry<String, Integer> e : target.entrySet()) {
            int actual = counts.getOrDefault(prefix + e.getKey(), 0);
            if (actual != e.getValue()) {
                out.add(new Violation(code,
                        where + " quota " + e.getKey() + " is " + actual + ", requires "
                                + e.getValue()));
            }
        }
    }

    /**
     * Chỉ tiêu cấp môn của một mục phải khớp đúng trên các đơn vị của các nhóm thuộc chính mục
     * đó: vừa không thiếu từng mục đã khai, vừa không chứa giá trị ngoài phạm vi đã khai
     * (mục không khai = chỉ tiêu 0).
     */
    private List<Violation> checkSubjectQuota(Instance inst, SectionOnPath section,
            ResolvedQuota q, Map<String, Integer> counts) {
        List<Violation> out = new ArrayList<>();
        String where = "subject " + inst.subjectCode + " section " + section.sectionId();
        checkExact(out, "SUBJECT_ANSWER_QUOTA", q.answerTypes(), counts, "A:", where);
        checkExact(out, "SUBJECT_GRADE_QUOTA", q.grades(), counts, "G:", where);
        checkExact(out, "SUBJECT_DIFFICULTY_QUOTA", q.difficulties(), counts, "D:", where);
        checkExact(out, "SUBJECT_CELL_QUOTA", q.cells(), counts, "C:", where);
        checkOutOfScope(out, "SUBJECT_ANSWER_UNDECLARED", q.answerTypes(), counts, "A:", where);
        checkOutOfScope(out, "SUBJECT_GRADE_UNDECLARED", q.grades(), counts, "G:", where);
        checkOutOfScope(out, "SUBJECT_DIFFICULTY_UNDECLARED", q.difficulties(), counts, "D:",
                where);
        checkOutOfScope(out, "SUBJECT_CELL_UNDECLARED", q.cells(), counts, "C:", where);
        return out;
    }

    private void checkOutOfScope(List<Violation> out, String code, Map<String, Integer> declared,
            Map<String, Integer> counts, String prefix, String where) {
        if (declared.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getKey().startsWith(prefix) && !declared.containsKey(e.getKey()
                    .substring(prefix.length()))) {
                out.add(new Violation(code, where + " picked " + e.getValue() + " unit(s) of "
                        + e.getKey().substring(prefix.length()) + " outside declared quotas"));
            }
        }
    }
}
