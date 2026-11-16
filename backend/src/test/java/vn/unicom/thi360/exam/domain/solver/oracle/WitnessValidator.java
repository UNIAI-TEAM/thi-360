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
 *   <li>một câu / phiên bản / câu con chỉ xuất hiện một lần trong một bài toán con (đường con);
 *   <li>cách chọn môn của đường có {@link SubjectChoice} mang số câu theo thứ tự cấu trúc (D25).
 * </ul>
 *
 * <p>Đường có {@link SubjectChoice}: đề chứng minh lưu theo bài toán con (môn, số câu) — validator
 * suy ra mọi bài toán con mà các cách chọn sinh ra và khớp từng {@link SubjectWitness} với đúng
 * một bài toán con.
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
        Map<String, Instance> byKey = new LinkedHashMap<>();
        for (Instance inst : instances) {
            byKey.put(inst.subjectCode + "|" + inst.total, inst);
        }

        Set<String> usedKeys = new HashSet<>();
        Map<String, GroupWitness> witnessByGroup = new HashMap<>();
        for (SubjectWitness sw : result.witness()) {
            String key = sw.subjectCode() + "|" + sw.resolvedCount();
            Instance inst = byKey.get(key);
            if (inst == null) {
                out.add(new Violation("ORPHAN_WITNESS",
                        "no subproblem (subject, count) for " + key));
                continue;
            }
            if (!usedKeys.add(key)) {
                out.add(new Violation("DUPLICATE_WITNESS", "duplicate witness for " + key));
                continue;
            }
            out.addAll(checkInstance(inst, sw, source, witnessByGroup));
        }
        for (Map.Entry<String, Instance> e : byKey.entrySet()) {
            if (!e.getValue().groups.isEmpty() && !usedKeys.contains(e.getKey())) {
                out.add(new Violation("MISSING_WITNESS",
                        "subproblem " + e.getKey() + " has no witness"));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ bài toán con mong đợi

    /** Một bài toán con (môn, số câu) mà các cách chọn của đường sinh ra. */
    private static final class Instance {
        final String subjectCode;
        int total;
        final List<SectionOnPath> sections = new ArrayList<>();
        final List<GroupQuota> groups = new ArrayList<>();
        /** Số đơn vị mỗi nhóm phải chiếm, song song {@code groups}. */
        final List<Integer> required = new ArrayList<>();
        /** Chỉ tiêu cấp môn đã quy, một mục trên một mục {@code sections}. */
        final List<ResolvedQuota> quotas = new ArrayList<>();

        Instance(String subjectCode) {
            this.subjectCode = subjectCode;
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
                }
            }
            return inst;
        }
    }

    /** Mọi bài toán con (môn, số câu) mà các đường con của {@code path} sinh ra. */
    private List<Instance> instancesOf(vn.unicom.thi360.exam.domain.solver.SolvePath path) {
        List<Instance> out = new ArrayList<>();
        List<List<SubjectPick>> combos = oracle.combosOf(path);
        List<List<SectionOnPath>> sectionLists = new ArrayList<>();
        if (combos.isEmpty()) {
            sectionLists.add(path.sections());
        } else {
            for (List<SubjectPick> combo : combos) {
                sectionLists.add(oracle.resolvedSectionsOf(path, combo));
            }
        }
        Map<String, Instance> seen = new LinkedHashMap<>();
        for (List<SectionOnPath> sections : sectionLists) {
            Map<String, List<SectionOnPath>> bySubject = new LinkedHashMap<>();
            for (SectionOnPath s : sections) {
                bySubject.computeIfAbsent(s.subjectCode(), k -> new ArrayList<>()).add(s);
            }
            for (List<SectionOnPath> merged : bySubject.values()) {
                Instance inst = Instance.of(merged);
                seen.putIfAbsent(inst.subjectCode + "|" + inst.total, inst);
            }
        }
        out.addAll(seen.values());
        return out;
    }

    // ------------------------------------------------------------ số câu của cách chọn

    /** Mọi cách chọn môn trên đường phải nhận số câu theo thứ tự cấu trúc (D25). */
    private List<Violation> checkPickCounts(
            vn.unicom.thi360.exam.domain.solver.SolvePath path) {
        List<Violation> out = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (i >= path.picks().size()) {
                continue;
            }
            for (SubjectPick pick : path.picks().get(i)) {
                List<Integer> expected = expectedCounts(choice, pick);
                if (!expected.equals(pick.counts())) {
                    out.add(new Violation("PICK_COUNTS_MISMATCH",
                            "pick " + OracleSupport.pickKey(pick) + " expects " + expected));
                }
            }
        }
        return out;
    }

    private List<Integer> expectedCounts(SubjectChoice choice, SubjectPick pick) {
        List<SectionOnPath> ordered = new ArrayList<>();
        for (SectionOnPath s : choice.sections()) {
            if (pick.subjectCodes().contains(s.subjectCode())) {
                ordered.add(s);
            }
        }
        return OracleSupport.structureOrderPick(choice, ordered).counts();
    }

    // ------------------------------------------------------------ kiểm một bài toán con

    private List<Violation> checkInstance(Instance inst, SubjectWitness sw,
            CandidateSource source, Map<String, GroupWitness> witnessByGroup) {
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
        Map<String, Integer> subjectCounts = new HashMap<>();
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
            if (witnessByGroup.put(inst.subjectCode + "|" + inst.total + "|" + gw.groupId(),
                    gw) != null) {
                out.add(new Violation("GROUP_IN_TWO_WITNESSES", gw.groupId()));
            }
            SectionOnPath owner = ownerSection(inst, gw.groupId());
            out.addAll(checkGroup(owner, inst.groups.get(gi), inst.required.get(gi), gw, source,
                    occupied, subjectCounts));
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(seen);
        if (!missing.isEmpty()) {
            out.add(new Violation("GROUP_WITNESS_MISSING", "groups without picks: " + missing));
        }
        for (ResolvedQuota q : inst.quotas) {
            out.addAll(checkSubjectQuota(inst, q, subjectCounts));
        }
        return out;
    }

    private SectionOnPath ownerSection(Instance inst, String groupId) {
        for (SectionOnPath s : inst.sections) {
            for (GroupQuota g : s.groups()) {
                if (g.groupId().equals(groupId)) {
                    return s;
                }
            }
        }
        return inst.sections.get(0);
    }

    private List<Violation> checkGroup(SectionOnPath section, GroupQuota group, int required,
            GroupWitness gw, CandidateSource source, Set<String> occupied,
            Map<String, Integer> subjectCounts) {
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
            for (String id : OracleSupport.occupiedIds(c)) {
                if (!occupied.add(id)) {
                    out.add(new Violation("ID_REUSED",
                            id + " used more than once in subproblem " + section.subjectCode()));
                }
            }
        }
        int units = picked.stream().mapToInt(OracleSupport::unitCount).sum();
        switch (group.kind()) {
            case SINGLE -> {
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
                addUnit(subjectCounts, u);
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
            counts.merge("C:" + u.gradeCode() + "" + u.difficultyCode(), 1, Integer::sum);
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

    private List<Violation> checkSubjectQuota(Instance inst, ResolvedQuota q,
            Map<String, Integer> counts) {
        List<Violation> out = new ArrayList<>();
        String where = "subject " + inst.subjectCode;
        checkExact(out, "SUBJECT_ANSWER_QUOTA", q.answerTypes(), counts, "A:", where);
        checkExact(out, "SUBJECT_GRADE_QUOTA", q.grades(), counts, "G:", where);
        checkExact(out, "SUBJECT_DIFFICULTY_QUOTA", q.difficulties(), counts, "D:", where);
        checkExact(out, "SUBJECT_CELL_QUOTA", q.cells(), counts, "C:", where);
        return out;
    }
}
