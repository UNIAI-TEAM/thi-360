package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chuẩn bị một bài toán con theo môn (spec 5.4): dịch chỉ tiêu cấp môn ({@link SubjectQuota}) và
 * chỉ tiêu của các nhóm ({@link GroupQuota}) thành mảng chỉ tiêu nội bộ, quy chỉ tiêu tỉ lệ ra số
 * câu bằng {@link QuotaApportioner}, và lọc ứng viên của từng nhóm.
 *
 * <p>Bố cục {@code reqs}: chỉ tiêu phía môn trước (dạng trả lời, rồi ô COMBINED, rồi chiều lớp,
 * rồi chiều độ khó — mỗi chiều khai báo là một phân hoạch trên đơn vị), sau đó theo thứ tự nhóm:
 * chỉ tiêu TỔNG của nhóm (quota = số đơn vị của nhóm) rồi chỉ tiêu phụ (ma trận, năng lực, nhóm
 * MAPID). {@code reqFrom[g]} / {@code reqTo[g]} là khoảng chỉ tiêu của nhóm g (TỔNG ở reqFrom).
 *
 * <p>Mỗi chiều đã khai (dạng trả lời của môn, từng chiều ma trận môn, từng chiều ma trận nhóm) là
 * một phân hoạch: mọi đơn vị của ứng viên phải khớp đúng một mục chỉ tiêu của mỗi chiều, nên tổng
 * chỉ tiêu của chiều phải bằng số đơn vị được kiểm soát (số câu của môn / của nhóm). Đơn vị khớp
 * mục chỉ tiêu quota 0 thì ứng viên không usable; đơn vị không khớp mục nào của một chiều cũng vậy
 * (không có chỗ để đếm mà không vi phạm).
 *
 * <p>Chỉ tiêu phụ không phân hoạch của nhóm (năng lực, nhóm MAPID) là chỉ tiêu tối thiểu độc lập:
 * đơn vị có gắn thì đếm, tổng không nhất thiết bằng số đơn vị của nhóm.
 */
final class Prep {

    private Prep() {
    }

    // ---------------------------------------------------------------- chỉ tiêu nội bộ

    /** Một chỉ tiêu nội bộ đã dịch (phía môn hoặc phía nhóm). */
    record Requirement(Kind kind, String key, int quota, Predicate<CountingUnit> matcher) {

        enum Kind {
            SUBJECT_ANSWER, SUBJECT_CELL, SUBJECT_GRADE, SUBJECT_DIFFICULTY,
            GROUP_TOTAL, GROUP_CELL, GROUP_GRADE, GROUP_DIFFICULTY, COMPETENCY, MAPID_GROUP
        }

        /** Chỉ tiêu dạng trả lời của môn (D22). */
        static Requirement subjectAnswer(DimensionQuota item) {
            return new Requirement(Kind.SUBJECT_ANSWER, "ans:" + item.code(), item.quota(),
                    u -> item.code().equals(u.answerType()));
        }

        static Requirement subjectCell(CellQuota cell) {
            return new Requirement(Kind.SUBJECT_CELL,
                    "scell:" + cell.gradeCode() + "/" + cell.difficultyCode(), cell.quota(),
                    u -> cell.gradeCode().equals(u.gradeCode())
                            && cell.difficultyCode().equals(u.difficultyCode()));
        }

        static Requirement subjectGrade(DimensionQuota dim) {
            return new Requirement(Kind.SUBJECT_GRADE, "sgrade:" + dim.code(), dim.quota(),
                    u -> dim.code().equals(u.gradeCode()));
        }

        static Requirement subjectDifficulty(DimensionQuota dim) {
            return new Requirement(Kind.SUBJECT_DIFFICULTY, "sdiff:" + dim.code(), dim.quota(),
                    u -> dim.code().equals(u.difficultyCode()));
        }

        /** Tổng số đơn vị của một nhóm (mọi đơn vị của ứng viên đều khớp). */
        static Requirement groupTotal(String groupId, int units) {
            return new Requirement(Kind.GROUP_TOTAL, "total:" + groupId, units, u -> true);
        }

        static Requirement groupCell(String groupId, CellQuota cell) {
            return new Requirement(Kind.GROUP_CELL,
                    "gcell:" + groupId + ":" + cell.gradeCode() + "/" + cell.difficultyCode(),
                    cell.quota(),
                    u -> cell.gradeCode().equals(u.gradeCode())
                            && cell.difficultyCode().equals(u.difficultyCode()));
        }

        static Requirement groupGrade(String groupId, DimensionQuota dim) {
            return new Requirement(Kind.GROUP_GRADE, "ggrade:" + groupId + ":" + dim.code(),
                    dim.quota(), u -> dim.code().equals(u.gradeCode()));
        }

        static Requirement groupDifficulty(String groupId, DimensionQuota dim) {
            return new Requirement(Kind.GROUP_DIFFICULTY,
                    "gdiff:" + groupId + ":" + dim.code(), dim.quota(),
                    u -> dim.code().equals(u.difficultyCode()));
        }

        static Requirement competency(String groupId, CompetencyQuota comp) {
            return new Requirement(Kind.COMPETENCY, "comp:" + groupId + ":" + comp.competencyCode(),
                    comp.quota(), u -> u.competencyCodes().contains(comp.competencyCode()));
        }

        static Requirement mapidGroup(String groupId, MapidGroupQuota group) {
            Set<String> nodes = Set.copyOf(group.nodeIds());
            return new Requirement(Kind.MAPID_GROUP, "mapid:" + groupId + ":" + group.groupKey(),
                    group.quota(), u -> {
                        for (String id : u.mapidIds()) {
                            if (nodes.contains(id)) {
                                return true;
                            }
                        }
                        return false;
                    });
        }
    }

    /**
     * Bài toán con một môn đã gộp (spec 5.4): các môn cùng {@code subjectCode} trên đường con,
     * tổng số câu {@code count}. Nhóm lấy từ mọi {@link SectionOnPath} theo thứ tự.
     */
    record Subproblem(String subjectCode, int count, List<SectionOnPath> sections) {

        Subproblem {
            sections = List.copyOf(sections);
        }

        SubproblemKey key() {
            return new SubproblemKey(subjectCode, sections, count);
        }
    }

    /** Nhóm đã quyết số đơn vị (SINGLE cố định / phần còn lại, SET = setCount × setSize). */
    record ResolvedGroup(GroupQuota group, int units) {
    }

    /**
     * Bài toán con đã chuẩn bị: chỉ tiêu nội bộ và ứng viên đã lọc của từng nhóm.
     *
     * @param subjectCode mã môn
     * @param count       số đơn vị của bài toán con (= số câu của môn)
     * @param groups      các nhóm đã quyết đơn vị
     * @param reqs        toàn bộ chỉ tiêu (phía môn trước, rồi theo nhóm)
     * @param subjectReqs số chỉ tiêu phía môn (đầu mảng reqs)
     * @param reqFrom     reqFrom[g] = chỉ số chỉ tiêu TỔNG của nhóm g
     * @param reqTo       reqTo[g] = chỉ số sau chỉ tiêu cuối của nhóm g
     * @param cands       cands[g] ứng viên của nhóm g (đã xáo nếu GENERATE)
     * @param contrib     contrib[g][i][r] đóng góp của ứng viên i vào chỉ tiêu r
     * @param units       units[g][i] số đơn vị của ứng viên i
     * @param keys        keys[g][i] khóa duy nhất (questionId + childIds)
     * @param usable      ứng viên i có thể được chọn cho nhóm g không
     * @param dims        các phân hoạch {from, to}: mọi đơn vị phải khớp một mục dương của mỗi phân
     *                    hoạch áp dụng cho nó (phía môn + phía nhóm của chính nhóm)
     */
    record SubproblemContext(
            String subjectCode,
            int count,
            List<ResolvedGroup> groups,
            List<Requirement> reqs,
            int subjectReqs,
            int[] reqFrom,
            int[] reqTo,
            List<List<PathCandidate>> cands,
            int[][][] contrib,
            int[][] units,
            String[][][] keys,
            boolean[][] usable,
            int[][] dims) {
    }

    // ---------------------------------------------------------------- chuẩn bị

    /**
     * Dựng {@link SubproblemContext}: dịch chỉ tiêu, quy tỉ lệ, lọc ứng viên.
     *
     * @return context, hoặc null khi rõ ràng bất khả thi (tổng một chiều ≠ số đơn vị được kiểm
     *         soát, tổng cố định vượt số câu của môn, số đơn vị dương mà không có chỉ tiêu dương);
     *         lý do cụ thể đặt vào {@code failReason}, nhóm vào {@code failGroup}
     */
    static SubproblemContext prepare(Subproblem problem,
            List<List<PathCandidate>> candidatesByGroup, QuotaApportioner apportioner,
            Runnable deadlineCheck, String[] failReason, String[] failGroup) {
        List<GroupQuota> flat = new ArrayList<>();
        for (SectionOnPath section : problem.sections()) {
            flat.addAll(section.groups());
            deadlineCheck.run();
        }
        // số đơn vị từng nhóm: SINGLE cố định, SET = setCount × setSize, nhóm "lấy phần còn lại"
        // nhận phần chênh giữa số câu của môn và tổng cố định
        List<ResolvedGroup> groups = new ArrayList<>(flat.size());
        int fixedUnits = 0;
        for (GroupQuota g : flat) {
            if (g.kind() != GroupQuota.GroupKind.SINGLE || g.questionCount() != null) {
                fixedUnits += g.resolvedUnitCount(0);
            }
        }
        int remainder = problem.count() - fixedUnits;
        if (remainder < 0) {
            failReason[0] = "fixed group units " + fixedUnits + " exceed subject count "
                    + problem.count();
            return null;
        }
        for (GroupQuota g : flat) {
            int units = (g.kind() == GroupQuota.GroupKind.SINGLE && g.questionCount() == null)
                    ? remainder
                    : g.resolvedUnitCount(0);
            groups.add(new ResolvedGroup(g, units));
        }
        if (candidatesByGroup.size() != groups.size()) {
            throw new IllegalArgumentException("candidatesByGroup must be parallel to groups");
        }

        List<Requirement> reqs = new ArrayList<>();
        List<int[]> dims = new ArrayList<>();

        // ---- chỉ tiêu phía môn ----
        SubjectQuota quota = mergedSubjectQuota(problem.sections());
        if (quota.answerQuotas() != null) {
            List<DimensionQuota> items = quota.answerQuotas().items();
            addDim(reqs, dims, items, quota.answerQuotas().unit(), problem.count(), apportioner,
                    i -> Requirement.subjectAnswer(items.get(i)),
                    failReason, "subject answer quotas");
        }
        if (quota.matrix() instanceof MatrixQuota.Combined combined) {
            addDim(reqs, dims, combined.cells().stream()
                    .map(c -> new DimensionQuota(c.gradeCode() + "/" + c.difficultyCode(), c.quota()))
                    .toList(), combined.unit(), problem.count(), apportioner,
                    i -> Requirement.subjectCell(combined.cells().get(i)),
                    failReason, "subject combined matrix");
        } else if (quota.matrix() instanceof MatrixQuota.Separate separate) {
            if (!separate.grades().isEmpty()) {
                addDim(reqs, dims, separate.grades(), separate.unit(), problem.count(), apportioner,
                        i -> Requirement.subjectGrade(separate.grades().get(i)),
                        failReason, "subject grade dimension");
            }
            if (!separate.difficulties().isEmpty()) {
                addDim(reqs, dims, separate.difficulties(), separate.unit(), problem.count(),
                        apportioner, i -> Requirement.subjectDifficulty(
                                separate.difficulties().get(i)),
                        failReason, "subject difficulty dimension");
            }
        }
        int subjectReqs = reqs.size();
        if (failReason[0] != null) {
            return null;
        }

        // ---- chỉ tiêu phía nhóm ----
        int nG = groups.size();
        int[] reqFrom = new int[nG];
        int[] reqTo = new int[nG];
        for (int g = 0; g < nG; g++) {
            deadlineCheck.run();
            ResolvedGroup rg = groups.get(g);
            GroupQuota group = rg.group();
            reqFrom[g] = reqs.size();
            reqs.add(Requirement.groupTotal(group.groupId(), rg.units()));
            if (group.matrix() instanceof MatrixQuota.Combined combined) {
                addDim(reqs, dims, combined.cells().stream()
                        .map(c -> new DimensionQuota(
                                c.gradeCode() + "/" + c.difficultyCode(), c.quota()))
                        .toList(), combined.unit(), rg.units(), apportioner,
                        i -> Requirement.groupCell(group.groupId(), combined.cells().get(i)),
                        failReason, "group " + group.groupId() + " combined matrix");
            } else if (group.matrix() instanceof MatrixQuota.Separate separate) {
                if (!separate.grades().isEmpty()) {
                    addDim(reqs, dims, separate.grades(), separate.unit(), rg.units(), apportioner,
                            i -> Requirement.groupGrade(group.groupId(), separate.grades().get(i)),
                            failReason, "group " + group.groupId() + " grade dimension");
                }
                if (!separate.difficulties().isEmpty()) {
                    addDim(reqs, dims, separate.difficulties(), separate.unit(), rg.units(),
                            apportioner, i -> Requirement.groupDifficulty(group.groupId(),
                                    separate.difficulties().get(i)),
                            failReason, "group " + group.groupId() + " difficulty dimension");
                }
            }
            for (CompetencyQuota c : group.competencies()) {
                reqs.add(Requirement.competency(group.groupId(), c));
            }
            for (MapidGroupQuota mg : group.mapidGroups()) {
                reqs.add(Requirement.mapidGroup(group.groupId(), mg));
            }
            reqTo[g] = reqs.size();
            if (failReason[0] != null) {
                failGroup[0] = group.groupId();
                return null;
            }
            // nhóm có đơn vị dương mà không có chỉ tiêu dương nào (TỔNG + phụ): vô lý → INVALID
            if (rg.units() > 0 && reqs.get(reqFrom[g]).quota() <= 0) {
                failReason[0] = "group " + group.groupId() + " needs " + rg.units()
                        + " units with non-positive total";
                failGroup[0] = group.groupId();
                return null;
            }
        }
        if (failReason[0] != null) {
            return null;
        }

        int[][] dimsArr = dims.toArray(int[][]::new);

        // ---- lọc ứng viên ----
        List<List<PathCandidate>> cands = new ArrayList<>(nG);
        int[][][] contrib = new int[nG][][];
        int[][] units = new int[nG][];
        String[][][] keys = new String[nG][][];
        boolean[][] usable = new boolean[nG][];
        int rCount = reqs.size();
        for (int g = 0; g < nG; g++) {
            ResolvedGroup rg = groups.get(g);
            List<PathCandidate> pool = candidatesByGroup.get(g);
            cands.add(pool);
            int n = pool.size();
            contrib[g] = new int[n][rCount];
            units[g] = new int[n];
            keys[g] = new String[n][];
            usable[g] = new boolean[n];
            for (int i = 0; i < n; i++) {
                if ((i & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                PathCandidate c = pool.get(i);
                keys[g][i] = keysOf(c);
                units[g][i] = c.units().size();
                int[] vec = contrib[g][i];
                usable[g][i] = usableFor(rg.group(), c, vec, reqs, subjectReqs, dimsArr,
                        reqFrom[g], reqTo[g]);
            }
        }
        return new SubproblemContext(problem.subjectCode(), problem.count(), groups, reqs,
                subjectReqs, reqFrom, reqTo, cands, contrib, units, keys, usable, dimsArr);
    }

    /**
     * Thêm một phân hoạch chỉ tiêu (dãy giá trị theo {@code unit}) vào {@code reqs}; ghi chiều vào
     * {@code dims}; kiểm tổng chiều = {@code expected} (spec 5.2). Sai tổng → failReason.
     */
    private static void addDim(List<Requirement> reqs, List<int[]> dims,
            List<DimensionQuota> items, QuotaUnit unit, int expected,
            QuotaApportioner apportioner,
            java.util.function.IntFunction<Requirement> make, String[] failReason, String label) {
        int[] values = new int[items.size()];
        if (unit == QuotaUnit.PERCENT) {
            values = apportioner.apportion(
                    items.stream().map(DimensionQuota::quota).toList(), expected);
        } else {
            for (int i = 0; i < items.size(); i++) {
                values[i] = items.get(i).quota();
            }
        }
        int sum = 0;
        for (int v : values) {
            sum += v;
        }
        if (sum != expected) {
            failReason[0] = label + " total " + sum + " != " + expected;
            return;
        }
        int from = reqs.size();
        for (int i = 0; i < items.size(); i++) {
            reqs.add(make.apply(i));
        }
        // ghi quota đã quy: các Requirement được dựng từ giá trị gốc → thay bằng giá trị đã quy
        // (đơn giản: dựng lại matcher từ make nhưng quota phải là values[i])
        for (int i = 0; i < items.size(); i++) {
            Requirement r = reqs.get(from + i);
            reqs.set(from + i, new Requirement(r.kind(), r.key(), values[i], r.matcher()));
        }
        dims.add(new int[]{from, from + items.size()});
    }

    /** Chỉ tiêu cấp môn có hiệu lực của bài toán con: mọi môn cùng mã dùng chung chỉ tiêu (B9). */
    private static SubjectQuota mergedSubjectQuota(List<SectionOnPath> sections) {
        for (SectionOnPath s : sections) {
            if (s.subjectQuota() != null && s.subjectQuota() != SubjectQuota.NONE
                    && (s.subjectQuota().answerQuotas() != null
                            || s.subjectQuota().matrix() != null)) {
                return s.subjectQuota();
            }
        }
        return SubjectQuota.NONE;
    }

    /**
     * Ứng viên có usable cho nhóm không: qua bộ lọc của nhóm (kích thước bộ, dạng câu con, dạng
     * trả lời, điểm, quy tắc chấm), không trùng khóa trong chính nó, mọi đơn vị khớp ít nhất một
     * mục chỉ tiêu dương trong mỗi phân hoạch áp dụng (phía môn + ma trận của chính nhóm), và
     * không đóng góp vào mục chỉ tiêu quota 0 trong phạm vi áp dụng đó.
     */
    private static boolean usableFor(GroupQuota group, PathCandidate c, int[] vec,
            List<Requirement> reqs, int subjectReqs, int[][] dims, int reqFrom, int reqTo) {
        List<CountingUnit> us = c.units();
        if (us.isEmpty()) {
            return false;
        }
        if (group.kind() == GroupQuota.GroupKind.SET) {
            if (us.size() != group.setSize()) {
                return false;
            }
            if (group.childAnswerType() != null) {
                for (CountingUnit u : us) {
                    if (!group.childAnswerType().equals(u.answerType())) {
                        return false;
                    }
                }
            }
        }
        if (group.answerType() != null) {
            for (CountingUnit u : us) {
                if (!group.answerType().equals(u.answerType())) {
                    return false;
                }
            }
        }
        if (group.expectedScoringRule() != null
                && !group.expectedScoringRule().equals(c.scoringRule())) {
            return false;
        }
        BigDecimal requiredPoints = group.requiredPointsPerUnit();
        if (requiredPoints != null) {
            for (CountingUnit u : us) {
                if (u.points() == null || u.points().compareTo(requiredPoints) != 0) {
                    return false;
                }
            }
        }
        // khóa duy nhất bên trong ứng viên: questionId trùng childId, hoặc hai childId giống nhau
        Set<String> seen = new HashSet<>();
        seen.add(c.questionId());
        for (CountingUnit u : us) {
            if (u.childId() != null && !seen.add(u.childId())) {
                return false;
            }
        }
        // vector đóng góp + phủ các phân hoạch áp dụng (phía môn + chiều của chính nhóm)
        int rCount = reqs.size();
        for (CountingUnit u : us) {
            for (int r = 0; r < rCount; r++) {
                if (reqs.get(r).matcher().test(u)) {
                    vec[r]++;
                }
            }
            for (int[] dim : dims) {
                if (!dimApplies(dim, subjectReqs, reqFrom, reqTo)) {
                    continue;
                }
                boolean covered = false;
                for (int r = dim[0]; r < dim[1]; r++) {
                    if (reqs.get(r).quota() > 0 && reqs.get(r).matcher().test(u)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            }
        }
        // không đóng góp vào mục quota 0 trong phạm vi áp dụng (phía môn + chỉ tiêu chính nhóm)
        for (int r = 0; r < rCount; r++) {
            if (vec[r] > 0 && reqs.get(r).quota() == 0
                    && (r < subjectReqs || (r >= reqFrom && r < reqTo))) {
                return false;
            }
        }
        return true;
    }

    /** Phân hoạch {@code dim} có áp dụng cho ứng viên của nhóm [reqFrom, reqTo) không. */
    private static boolean dimApplies(int[] dim, int subjectReqs, int reqFrom, int reqTo) {
        return dim[1] <= subjectReqs || (dim[0] >= reqFrom && dim[1] <= reqTo);
    }

    /** Khóa duy nhất của ứng viên: questionId + mọi childId khác null của các đơn vị. */
    static String[] keysOf(PathCandidate c) {
        List<String> out = new ArrayList<>(c.units().size() + 1);
        out.add(c.questionId());
        for (CountingUnit u : c.units()) {
            if (u.childId() != null) {
                out.add(u.childId());
            }
        }
        return out.toArray(String[]::new);
    }
}
