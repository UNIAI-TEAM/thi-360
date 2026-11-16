package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chuẩn bị một bài toán con theo môn (spec 5.4): dịch chỉ tiêu cấp môn ({@link SubjectQuota})
 * của từng {@link SectionOnPath} và chỉ tiêu của các nhóm ({@link GroupQuota}) thành mảng chỉ
 * tiêu nội bộ, quy chỉ tiêu tỉ lệ ra số câu bằng {@link QuotaApportioner}, và lọc ứng viên của
 * từng nhóm.
 *
 * <p>Nhiều {@link SectionOnPath} cùng {@code subjectCode} được gộp thành một bài toán con để
 * tìm kiếm chung (vùng gán chung, khóa duy nhất chung), nhưng <b>phạm vi chỉ tiêu không gộp</b>
 * (R2-3): chỉ tiêu cấp môn của một section chỉ tính trên các nhóm của section đó và tổng phải
 * bằng số câu của section đó; nhóm "lấy phần còn lại" nhận phần chênh của riêng section mình
 * (R2-4); tổng đơn vị của các nhóm mỗi section phải bằng đúng số câu của section (R2-5).
 *
 * <p>Bố cục {@code reqs}: theo thứ tự section — trước chỉ tiêu phía môn của section (dạng trả
 * lời, rồi ô COMBINED, rồi chiều lớp, rồi chiều độ khó — mỗi chiều khai báo là một phân hoạch),
 * rồi theo thứ tự nhóm của section: chỉ tiêu TỔNG của nhóm (quota = số đơn vị của nhóm) rồi chỉ
 * tiêu phụ (ma trận, năng lực, nhóm MAPID). {@code reqFrom[g]} / {@code reqTo[g]} là khoảng chỉ
 * tiêu của nhóm g (TỔNG ở reqFrom); {@code subjectBlocks[s]} là khoảng chỉ tiêu phía môn của
 * section s.
 *
 * <p>Mỗi chiều đã khai (dạng trả lời của section, từng chiều ma trận môn của section, từng chiều
 * ma trận nhóm) là một phân hoạch: mọi đơn vị của ứng viên phải khớp đúng một mục chỉ tiêu của
 * mỗi chiều áp dụng cho nhóm chứa nó, nên tổng chỉ tiêu của chiều phải bằng số đơn vị được kiểm
 * soát (số câu của section / của nhóm). Đơn vị khớp mục chỉ tiêu quota 0 thì ứng viên không
 * usable; đơn vị không khớp mục nào của một chiều áp dụng cũng vậy.
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

        static Requirement competency(String groupId, CompetencyQuota comp, int resolvedQuota) {
            return new Requirement(Kind.COMPETENCY, "comp:" + groupId + ":" + comp.competencyCode(),
                    resolvedQuota, u -> u.competencyCodes().contains(comp.competencyCode()));
        }

        static Requirement mapidGroup(String groupId, MapidGroupQuota group,
                Runnable deadlineCheck, int[] scans) {
            Set<String> nodes = Set.copyOf(group.nodeIds());
            return new Requirement(Kind.MAPID_GROUP, "mapid:" + groupId + ":" + group.groupKey(),
                    group.quota(), u -> {
                        for (String id : u.mapidIds()) {
                            if ((scans[0]++ & 0x3FF) == 0) {
                                deadlineCheck.run();
                            }
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
     * @param subjectCode   mã môn
     * @param count         số đơn vị của bài toán con (= tổng số câu các section)
     * @param sections      các section của bài toán con (theo thứ tự)
     * @param groups        các nhóm đã quyết đơn vị (gộp các section, theo thứ tự)
     * @param sectionOf     sectionOf[g] = chỉ số section chứa nhóm g
     * @param reqs          toàn bộ chỉ tiêu (theo section: phía môn của section, rồi các nhóm)
     * @param reqRefs       reqRefs[r] định danh {@link QuotaRef} của chỉ tiêu phía môn; null với
     *                      chỉ tiêu phía nhóm
     * @param subjectBlocks subjectBlocks[s] = {from, to} khoảng chỉ tiêu phía môn của section s
     * @param reqFrom       reqFrom[g] = chỉ số chỉ tiêu TỔNG của nhóm g
     * @param reqTo         reqTo[g] = chỉ số sau chỉ tiêu cuối của nhóm g
     * @param cands         cands[g] ứng viên của nhóm g (đã xáo nếu GENERATE)
     * @param contrib       contrib[g][i][r] đóng góp của ứng viên i vào chỉ tiêu r
     * @param units         units[g][i] số đơn vị của ứng viên i
     * @param keys          keys[g][i] khóa duy nhất (questionId + childIds)
     * @param usable        ứng viên i có thể được chọn cho nhóm g không
     * @param dims          các phân hoạch {from, to, owner}: owner &gt;= 0 là nhóm sở hữu; owner =
     *                      -(s+1) là chỉ tiêu phía môn của section s. Mọi đơn vị phải khớp một mục
     *                      dương của mỗi phân hoạch áp dụng cho nhóm chứa nó
     */
    record SubproblemContext(
            String subjectCode,
            int count,
            List<SectionOnPath> sections,
            List<ResolvedGroup> groups,
            int[] sectionOf,
            List<Requirement> reqs,
            List<QuotaRef> reqRefs,
            int[][] subjectBlocks,
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
     * Dựng {@link SubproblemContext}: dịch chỉ tiêu theo section, quy tỉ lệ, lọc ứng viên.
     *
     * @return context, hoặc null khi rõ ràng bất khả thi (tổng một chiều ≠ số đơn vị được kiểm
     *         soát, nhóm cố định vượt số câu của section, số đơn vị dương mà không có chỉ tiêu
     *         dương, tổng nhóm một section khác số câu của section); lý do cụ thể đặt vào
     *         {@code failReason}, nhóm vào {@code failGroup}
     */
    static SubproblemContext prepare(Subproblem problem,
            List<List<PathCandidate>> candidatesByGroup, QuotaApportioner apportioner,
            Runnable deadlineCheck, String[] failReason, String[] failGroup) {
        List<SectionOnPath> sections = problem.sections();
        int nS = sections.size();
        // nhóm gộp theo section + số đơn vị đã quyết: nhóm "lấy phần còn lại" nhận phần chênh
        // của riêng section mình (R2-4), không phải của môn đã gộp
        List<ResolvedGroup> groups = new ArrayList<>();
        List<GroupQuota> flat = new ArrayList<>();
        List<Integer> secIdx = new ArrayList<>();
        for (int s = 0; s < nS; s++) {
            deadlineCheck.run();
            SectionOnPath section = sections.get(s);
            int fixed = 0;
            for (GroupQuota g : section.groups()) {
                if (g.kind() != GroupQuota.GroupKind.SINGLE || g.questionCount() != null) {
                    fixed += g.resolvedUnitCount(0);
                }
            }
            int count = section.questionCount() == null ? 0 : section.questionCount();
            int remainder = count - fixed;
            if (remainder < 0) {
                failReason[0] = "fixed group units " + fixed + " exceed section count "
                        + count + " of section " + section.sectionId();
                return null;
            }
            int sum = 0;
            for (GroupQuota g : section.groups()) {
                int units = (g.kind() == GroupQuota.GroupKind.SINGLE
                        && g.questionCount() == null)
                                ? remainder
                                : g.resolvedUnitCount(0);
                groups.add(new ResolvedGroup(g, units));
                flat.add(g);
                secIdx.add(s);
                sum += units;
            }
            // tổng đơn vị các nhóm của một section phải bằng đúng số câu của nó (R2-5):
            // mọi đề VALID chứa đúng số đơn vị đó của section
            if (sum != count) {
                failReason[0] = "resolved group units " + sum + " != section count " + count
                        + " of section " + section.sectionId();
                return null;
            }
        }
        int[] sectionOf = new int[flat.size()];
        for (int g = 0; g < flat.size(); g++) {
            sectionOf[g] = secIdx.get(g);
        }
        if (candidatesByGroup.size() != groups.size()) {
            throw new IllegalArgumentException("candidatesByGroup must be parallel to groups");
        }

        // Bộ đếm dùng chung cho các matcher MAPID, kiểm hạn mỗi 1024 bước.
        int[] matcherScans = {0};

        List<Requirement> reqs = new ArrayList<>();
        List<QuotaRef> reqRefs = new ArrayList<>();
        List<int[]> dims = new ArrayList<>();
        String subjectCode = problem.subjectCode();
        int[][] subjectBlocks = new int[nS][];
        int[] reqFrom = new int[groups.size()];
        int[] reqTo = new int[groups.size()];

        for (int s = 0; s < nS; s++) {
            deadlineCheck.run();
            SectionOnPath section = sections.get(s);
            String sectionId = section.sectionId();
            int count = section.questionCount() == null ? 0 : section.questionCount();
            SubjectQuota quota = section.subjectQuota() == null
                    ? SubjectQuota.NONE
                    : section.subjectQuota();
            subjectBlocks[s] = new int[]{reqs.size(), reqs.size()};
            if (quota.answerQuotas() != null) {
                List<DimensionQuota> items = quota.answerQuotas().items();
                addDim(reqs, reqRefs, dims, items, quota.answerQuotas().unit(), count, apportioner,
                        i -> Requirement.subjectAnswer(items.get(i)),
                        i -> new QuotaRef(subjectCode, sectionId, "ANSWER", items.get(i).code()),
                        -(s + 1), failReason, "subject answer quotas of section " + sectionId);
            }
            if (quota.matrix() instanceof MatrixQuota.Combined combined) {
                addDim(reqs, reqRefs, dims, combined.cells().stream()
                        .map(c -> new DimensionQuota(
                                c.gradeCode() + "/" + c.difficultyCode(), c.quota()))
                        .toList(), combined.unit(), count, apportioner,
                        i -> Requirement.subjectCell(combined.cells().get(i)),
                        i -> new QuotaRef(subjectCode, sectionId, "CELL",
                                combined.cells().get(i).gradeCode() + "/"
                                        + combined.cells().get(i).difficultyCode()),
                        -(s + 1), failReason,
                        "subject combined matrix of section " + sectionId);
            } else if (quota.matrix() instanceof MatrixQuota.Separate separate) {
                if (!separate.grades().isEmpty()) {
                    addDim(reqs, reqRefs, dims, separate.grades(), separate.unit(), count,
                            apportioner,
                            i -> Requirement.subjectGrade(separate.grades().get(i)),
                            i -> new QuotaRef(subjectCode, sectionId, "GRADE",
                                    separate.grades().get(i).code()),
                            -(s + 1), failReason,
                            "subject grade dimension of section " + sectionId);
                }
                if (!separate.difficulties().isEmpty()) {
                    addDim(reqs, reqRefs, dims, separate.difficulties(), separate.unit(), count,
                            apportioner,
                            i -> Requirement.subjectDifficulty(separate.difficulties().get(i)),
                            i -> new QuotaRef(subjectCode, sectionId, "DIFFICULTY",
                                    separate.difficulties().get(i).code()),
                            -(s + 1), failReason,
                            "subject difficulty dimension of section " + sectionId);
                }
            }
            subjectBlocks[s][1] = reqs.size();
            if (failReason[0] != null) {
                return null;
            }

            // ---- chỉ tiêu phía nhóm của section s ----
            for (int g = 0; g < groups.size(); g++) {
                if (sectionOf[g] != s) {
                    continue;
                }
                deadlineCheck.run();
                ResolvedGroup rg = groups.get(g);
                GroupQuota group = rg.group();
                reqFrom[g] = reqs.size();
                reqs.add(Requirement.groupTotal(group.groupId(), rg.units()));
                reqRefs.add(null);
                if (group.matrix() instanceof MatrixQuota.Combined combined) {
                    addDim(reqs, reqRefs, dims, combined.cells().stream()
                            .map(c -> new DimensionQuota(
                                    c.gradeCode() + "/" + c.difficultyCode(), c.quota()))
                            .toList(), combined.unit(), rg.units(), apportioner,
                            i -> Requirement.groupCell(group.groupId(), combined.cells().get(i)),
                            i -> null, g,
                            failReason, "group " + group.groupId() + " combined matrix");
                } else if (group.matrix() instanceof MatrixQuota.Separate separate) {
                    if (!separate.grades().isEmpty()) {
                        addDim(reqs, reqRefs, dims, separate.grades(), separate.unit(), rg.units(),
                                apportioner,
                                i -> Requirement.groupGrade(group.groupId(),
                                        separate.grades().get(i)),
                                i -> null, g,
                                failReason, "group " + group.groupId() + " grade dimension");
                    }
                    if (!separate.difficulties().isEmpty()) {
                        addDim(reqs, reqRefs, dims, separate.difficulties(), separate.unit(),
                                rg.units(), apportioner,
                                i -> Requirement.groupDifficulty(group.groupId(),
                                        separate.difficulties().get(i)),
                                i -> null, g,
                                failReason,
                                "group " + group.groupId() + " difficulty dimension");
                    }
                }
                List<CompetencyQuota> competencies = group.competencies();
                int[] resolvedCompetencies = null;
                if (!competencies.isEmpty() && competencies.getFirst().unit() == QuotaUnit.PERCENT) {
                    resolvedCompetencies = apportioner.apportion(
                            competencies.stream().map(CompetencyQuota::quota).toList(), rg.units());
                }
                for (int i = 0; i < competencies.size(); i++) {
                    CompetencyQuota c = competencies.get(i);
                    reqs.add(Requirement.competency(group.groupId(), c,
                            resolvedCompetencies == null ? c.quota() : resolvedCompetencies[i]));
                    reqRefs.add(null);
                }
                for (MapidGroupQuota mg : group.mapidGroups()) {
                    reqs.add(Requirement.mapidGroup(group.groupId(), mg, deadlineCheck,
                            matcherScans));
                    reqRefs.add(null);
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
        }

        int[][] dimsArr = dims.toArray(int[][]::new);

        // ---- lọc ứng viên ----
        int nG = groups.size();
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
                usable[g][i] = usableFor(rg.group(), c, vec, reqs, subjectBlocks, dimsArr,
                        reqFrom[g], reqTo[g], sectionOf[g], deadlineCheck);
            }
        }
        return new SubproblemContext(problem.subjectCode(), problem.count(), sections, groups,
                sectionOf, reqs, reqRefs, subjectBlocks, reqFrom, reqTo, cands, contrib, units,
                keys, usable, dimsArr);
    }

    /**
     * Thêm một phân hoạch chỉ tiêu (dãy giá trị theo {@code unit}) vào {@code reqs}; ghi chiều vào
     * {@code dims} với chủ sở hữu {@code owner} (&gt;= 0: nhóm; &lt; 0: chỉ tiêu phía môn của
     * section {@code -owner-1}); kiểm tổng chiều = {@code expected} (spec 5.2). Sai tổng →
     * failReason.
     */
    private static void addDim(List<Requirement> reqs, List<QuotaRef> reqRefs, List<int[]> dims,
            List<DimensionQuota> items, QuotaUnit unit, int expected,
            QuotaApportioner apportioner,
            java.util.function.IntFunction<Requirement> make,
            java.util.function.IntFunction<QuotaRef> makeRef,
            int owner, String[] failReason, String label) {
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
            Requirement r = make.apply(i);
            // quota đã quy thay giá trị gốc (PERCENT → số câu)
            reqs.add(new Requirement(r.kind(), r.key(), values[i], r.matcher()));
            reqRefs.add(makeRef.apply(i));
        }
        dims.add(new int[]{from, from + items.size(), owner});
    }

    /**
     * Ứng viên có usable cho nhóm không: qua bộ lọc của nhóm (kích thước bộ, dạng câu con, dạng
     * trả lời, điểm, quy tắc chấm), không trùng khóa trong chính nó, mọi đơn vị khớp ít nhất một
     * mục chỉ tiêu dương trong mỗi phân hoạch áp dụng (chỉ tiêu phía môn của section chứa nhóm +
     * chiều của chính nhóm), và không đóng góp vào mục chỉ tiêu quota 0 trong phạm vi áp dụng đó.
     */
    private static boolean usableFor(GroupQuota group, PathCandidate c, int[] vec,
            List<Requirement> reqs, int[][] subjectBlocks, int[][] dims, int reqFrom, int reqTo,
            int sectionIdx, Runnable deadlineCheck) {
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
        // vector đóng góp + phủ các phân hoạch áp dụng cho nhóm này
        int rCount = reqs.size();
        int scans = 0;
        for (CountingUnit u : us) {
            for (int r = 0; r < rCount; r++) {
                if ((scans++ & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (reqs.get(r).matcher().test(u)) {
                    vec[r]++;
                }
            }
            for (int[] dim : dims) {
                if ((scans++ & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (!dimApplies(dim, reqFrom, reqTo, sectionIdx)) {
                    continue;
                }
                boolean covered = false;
                for (int r = dim[0]; r < dim[1]; r++) {
                    if ((scans++ & 0x3FF) == 0) {
                        deadlineCheck.run();
                    }
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
        // không đóng góp vào mục quota 0 trong phạm vi áp dụng (chỉ tiêu phía môn của section
        // chứa nhóm + chỉ tiêu của chính nhóm)
        int[] blk = subjectBlocks[sectionIdx];
        for (int r = 0; r < rCount; r++) {
            if (vec[r] > 0 && reqs.get(r).quota() == 0
                    && ((r >= blk[0] && r < blk[1]) || (r >= reqFrom && r < reqTo))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Phân hoạch {@code dim} có áp dụng cho ứng viên của một nhóm không: phân hoạch của chính
     * nhóm (owner = chỉ số nhóm nằm trong [reqFrom, reqTo) của nó) hoặc chỉ tiêu phía môn của
     * section chứa nhóm (owner = -(sectionIdx+1)).
     */
    private static boolean dimApplies(int[] dim, int reqFrom, int reqTo, int sectionIdx) {
        int owner = dim[2];
        if (owner >= 0) {
            return dim[0] >= reqFrom && dim[1] <= reqTo;
        }
        return owner == -(sectionIdx + 1);
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
