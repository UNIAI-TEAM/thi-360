package vn.unicom.thi360.exam.domain.solver.oracle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.unicom.thi360.exam.domain.solver.CellQuota;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.DimensionQuota;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.MatrixQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.QuotaUnit;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;

/**
 * Quy tắc chung của oracle độc lập (W-ORACLE), suy ra từ spec
 * {@code docs/specs/2026-11-16-be-pbi-03-04.md} mục 4.3, 5.2 (B9), 5.4 và các quyết định D22–D25 —
 * không đọc mã cài đặt của bộ giải.
 */
final class OracleSupport {

    private OracleSupport() {
    }

    // ------------------------------------------------------------ quy phần dư lớn nhất

    /**
     * Quy dãy tỉ lệ ra số câu nguyên theo phần dư lớn nhất (PBI-04 7.3), cùng quy tắc hòa của web
     * ({@code apportionByPercent} trong {@code apps/web-admin/src/features/exams/matrix.ts}):
     * giá trị lý thuyết làm tròn 1e-9, phần nguyên của {@code floor(t + 1e-9)}, phần thập phân
     * cũng làm tròn 1e-9 trước khi so — nên các phần dư bằng nhau về toán nhưng khác biểu diễn
     * nhị phân vẫn hòa và phân theo thứ tự khai báo.
     */
    static int[] apportion(List<Integer> percents, int total) {
        int n = percents.size();
        double[] theoretical = new double[n];
        int[] floors = new int[n];
        double theoSum = 0;
        int floorSum = 0;
        for (int i = 0; i < n; i++) {
            theoretical[i] = roundPrecision(percents.get(i) * total / 100.0);
            floors[i] = (int) Math.floor(theoretical[i] + 1e-9);
            theoSum += theoretical[i];
            floorSum += floors[i];
        }
        int target = (int) Math.round(theoSum);
        int missing = Math.max(0, target - floorSum);
        List<Integer> ranked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ranked.add(i);
        }
        ranked.sort((a, b) -> {
            double fa = roundPrecision(theoretical[a] - floors[a]);
            double fb = roundPrecision(theoretical[b] - floors[b]);
            if (fa != fb) {
                return Double.compare(fb, fa);
            }
            return Integer.compare(a, b);
        });
        int[] result = floors;
        for (int k = 0; k < Math.min(missing, n); k++) {
            result[ranked.get(k)]++;
        }
        return result;
    }

    /** {@code Math.round(v * 1e9) / 1e9} — chuẩn hóa số học thập phân của web trước khi so sánh. */
    private static double roundPrecision(double value) {
        return Math.round(value * 1e9) / 1e9;
    }

    // ------------------------------------------------------------ chỉ tiêu đã quy

    /**
     * Chỉ tiêu đã quy ra số câu nguyên (PERCENT → COUNT bằng {@link #apportion}). Ô hoặc mã không
     * khai là chỉ tiêu 0 (javadoc {@link MatrixQuota}: đơn vị khớp vào đó không thể được chọn).
     */
    record ResolvedQuota(
            Map<String, Integer> answerTypes,
            Map<String, Integer> grades,
            Map<String, Integer> difficulties,
            Map<String, Integer> cells) {

        static final ResolvedQuota EMPTY =
                new ResolvedQuota(Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Chỉ tiêu dạng trả lời của môn đã quy ({@link SubjectQuota.AnswerQuotas}); rỗng khi null. */
    static Map<String, Integer> resolveAnswerQuotas(SubjectQuota.AnswerQuotas quotas, int total) {
        if (quotas == null) {
            return Map.of();
        }
        return resolve(quotas.items(), quotas.unit(), total);
    }

    /** Ma trận đã quy: COMBINED → các ô; SEPARATE → hai chiều lớp và độ khó. */
    static ResolvedQuota resolveMatrix(MatrixQuota matrix, int total) {
        if (matrix == null) {
            return ResolvedQuota.EMPTY;
        }
        return switch (matrix) {
            case MatrixQuota.Combined c -> new ResolvedQuota(Map.of(), Map.of(), Map.of(),
                    resolveCells(c.cells(), c.unit(), total));
            case MatrixQuota.Separate s -> new ResolvedQuota(Map.of(),
                    resolve(s.grades(), s.unit(), total),
                    resolve(s.difficulties(), s.unit(), total), Map.of());
        };
    }

    private static Map<String, Integer> resolve(List<DimensionQuota> items, QuotaUnit unit,
            int total) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        int[] values;
        if (unit == QuotaUnit.PERCENT) {
            values = apportion(items.stream().map(DimensionQuota::quota).toList(), total);
        } else {
            values = items.stream().mapToInt(DimensionQuota::quota).toArray();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            out.put(items.get(i).code(), values[i]);
        }
        return out;
    }

    private static Map<String, Integer> resolveCells(List<CellQuota> cells, QuotaUnit unit,
            int total) {
        if (cells == null || cells.isEmpty()) {
            return Map.of();
        }
        int[] values;
        if (unit == QuotaUnit.PERCENT) {
            values = apportion(cells.stream().map(CellQuota::quota).toList(), total);
        } else {
            values = cells.stream().mapToInt(CellQuota::quota).toArray();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            CellQuota cell = cells.get(i);
            out.put(cellKey(cell.gradeCode(), cell.difficultyCode()), values[i]);
        }
        return out;
    }

    /**
     * Khóa một ô lớp × độ khó của chỉ tiêu Combined (dạng {@code "G10 NB"}): một mã hóa duy
     * nhất dùng chung bởi chỉ tiêu đã quy ({@link #resolveCells}), bộ lọc đơn vị
     * ({@link #admissibleInSubject}) và bộ đếm của {@code BruteForceOracle} lẫn
     * {@code WitnessValidator} — không thể tách thành hai dạng khác nhau (R3-3).
     */
    static String cellKey(String gradeCode, String difficultyCode) {
        return gradeCode + " " + difficultyCode;
    }

    /** Quy bộ chỉ tiêu cấp môn của một {@link SectionOnPath} theo số câu đã quyết của môn. */
    static ResolvedQuota resolveSubjectQuota(SubjectQuota quota, int subjectCount) {
        if (quota == null) {
            return ResolvedQuota.EMPTY;
        }
        ResolvedQuota matrix = resolveMatrix(quota.matrix(), subjectCount);
        return new ResolvedQuota(resolveAnswerQuotas(quota.answerQuotas(), subjectCount),
                matrix.grades(), matrix.difficulties(), matrix.cells());
    }

    /** Quy chỉ tiêu riêng của nhóm (ma trận nhóm) theo tổng đơn vị của nhóm. */
    static ResolvedQuota resolveGroupQuota(GroupQuota group, int groupUnits) {
        return resolveMatrix(group.matrix(), groupUnits);
    }

    /** Các id mà một ứng viên chiếm trên đường: id câu cộng mọi childId khác null (B9, spec 5.4). */
    static Set<String> occupiedIds(PathCandidate candidate) {
        Set<String> ids = new HashSet<>();
        ids.add(candidate.questionId());
        for (CountingUnit unit : candidate.units()) {
            if (unit.childId() != null) {
                ids.add(unit.childId());
            }
        }
        return ids;
    }

    /**
     * Ứng viên lặp định danh ngay trong chính nó (id câu trùng một childId, hoặc một childId
     * xuất hiện hai lần): "mỗi câu con chỉ dùng một lần trên cả đường" (spec 5.4) nên ứng viên
     * như vậy không bao giờ vào được đề — kiểm trước khi gom {@link #occupiedIds} thành Set.
     */
    static boolean hasInternalIdentityRepeat(PathCandidate candidate) {
        Set<String> ids = new HashSet<>();
        ids.add(candidate.questionId());
        for (CountingUnit unit : candidate.units()) {
            if (unit.childId() != null && !ids.add(unit.childId())) {
                return true;
            }
        }
        return false;
    }

    static int unitCount(PathCandidate candidate) {
        return candidate.units().size();
    }

    // ------------------------------------------------------------ điều kiện nguồn của nhóm

    /**
     * Ứng viên đủ điều kiện của nhóm theo spec 5.4/5.2: quy tắc chấm mong đợi, điểm từng đơn vị,
     * dạng trả lời (SINGLE) / dạng câu con và kích thước chùm (SET). Lọc phạm vi MAPID/dạng bài do
     * {@code CandidateSource} đảm nhận; oracle không lọc lại.
     */
    static boolean eligibleForGroup(GroupQuota group, PathCandidate candidate) {
        if (group.expectedScoringRule() != null
                && !group.expectedScoringRule().equals(candidate.scoringRule())) {
            return false;
        }
        if (group.requiredPointsPerUnit() != null) {
            for (CountingUnit unit : candidate.units()) {
                if (unit.points() == null
                        || unit.points().compareTo(group.requiredPointsPerUnit()) != 0) {
                    return false;
                }
            }
        }
        return switch (group.kind()) {
            case SINGLE -> {
                if (group.answerType() != null) {
                    for (CountingUnit unit : candidate.units()) {
                        if (!group.answerType().equals(unit.answerType())) {
                            yield false;
                        }
                    }
                }
                yield true;
            }
            case SET -> {
                // Chùm: đúng kích thước, mọi câu con đúng dạng khi nhóm giới hạn (spec 5.2).
                if (candidate.setId() == null || unitCount(candidate) != group.setSize()) {
                    yield false;
                }
                if (group.childAnswerType() != null) {
                    for (CountingUnit unit : candidate.units()) {
                        if (!group.childAnswerType().equals(unit.answerType())) {
                            yield false;
                        }
                    }
                }
                yield true;
            }
        };
    }

    /**
     * Đơn vị có được phép nằm trong môn không: khi môn khai chỉ tiêu dạng trả lời thì mọi đơn vị
     * phải mang dạng đã khai; khi khai ma trận thì mọi đơn vị phải rơi đúng ô/chỉ tiêu đã khai
     * (ô không khai = chỉ tiêu 0). Kiểm theo từng mục chỉ tiêu đã khai của các môn trong bài toán
     * con (môn có nhiều ExamSection thì mọi mục đều phải thỏa).
     */
    static boolean admissibleInSubject(List<ResolvedQuota> quotas, CountingUnit unit) {
        for (ResolvedQuota q : quotas) {
            if (!q.answerTypes().isEmpty() && !q.answerTypes().containsKey(unit.answerType())) {
                return false;
            }
            if (!q.grades().isEmpty()
                    && (unit.gradeCode() == null || !q.grades().containsKey(unit.gradeCode()))) {
                return false;
            }
            if (!q.difficulties().isEmpty() && (unit.difficultyCode() == null
                    || !q.difficulties().containsKey(unit.difficultyCode()))) {
                return false;
            }
            if (!q.cells().isEmpty() && (unit.gradeCode() == null || unit.difficultyCode() == null
                    || !q.cells().containsKey(cellKey(unit.gradeCode(),
                            unit.difficultyCode())))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ số câu nhóm / cách chọn

    /**
     * Số đơn vị đếm mà mỗi nhóm của một môn phải chiếm, theo {@link GroupQuota#resolvedUnitCount}:
     * nhóm "lấy phần còn lại" nhận {@code số câu môn - tổng cố định}.
     */
    static int[] groupUnitRequirements(SectionOnPath section) {
        if (section.questionCount() == null) {
            throw new IllegalArgumentException(
                    "section " + section.sectionId() + " has unresolved questionCount");
        }
        List<GroupQuota> groups = section.groups();
        int fixed = fixedUnits(section);
        int remainder = section.questionCount() - fixed;
        int[] req = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            req[i] = groups.get(i).resolvedUnitCount(remainder);
        }
        return req;
    }

    /** Số câu tổng cố định của một môn (phần không thuộc nhóm "lấy phần còn lại"). */
    static int fixedUnits(SectionOnPath section) {
        int fixed = 0;
        for (GroupQuota g : section.groups()) {
            fixed += g.resolvedUnitCount(0);
        }
        return fixed;
    }

    /**
     * Các phân bổ số câu cho một cách chọn môn, cài đặt độc lập từ B10/D25 và quyết định
     * "nhóm null tính một nhóm chung" (STATUS BE-5a:23), không dùng bộ gán của bộ giải:
     *
     * <ul>
     *   <li>{@code hi} = max của {@code countsByPick}, {@code lo} = min;
     *   <li>mọi môn được chọn thuộc một nhóm chung (toàn NATURAL, toàn SOCIAL, hoặc toàn
     *       null): {@code choose} phương án đối xứng với k bất kỳ — mỗi môn lần lượt nhận
     *       {@code lo}, còn lại {@code hi};
     *   <li>trộn NATURAL/SOCIAL: chia B10 — nhóm đông (nhiều môn được chọn hơn) nhận
     *       {@code hi} mỗi môn, nhóm lẻ nhận {@code lo}; hai nhóm ngang nhau thì không có
     *       nhóm đông, đầu vào vô nghĩa theo B10;
     *   <li>trộn null với non-null, hoặc tên nhóm ngoài miền spec (NATURAL/SOCIAL/null):
     *       đầu vào không hợp lệ → {@link IllegalArgumentException}.
     * </ul>
     */
    static List<List<Integer>> allocationsOf(SubjectChoice choice, List<SectionOnPath> picked) {
        int k = picked.size();
        int hi = Integer.MIN_VALUE;
        int lo = Integer.MAX_VALUE;
        for (int v : choice.countsByPick()) {
            hi = Math.max(hi, v);
            lo = Math.min(lo, v);
        }
        long nulls = 0;
        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        for (SectionOnPath s : picked) {
            String g = s.choiceGroup();
            if (g == null) {
                nulls++;
            } else if (!"NATURAL".equals(g) && !"SOCIAL".equals(g)) {
                throw new IllegalArgumentException("choiceGroup must be NATURAL or SOCIAL");
            } else {
                groupCounts.merge(g, 1, Integer::sum);
            }
        }
        if (nulls > 0 && nulls < k) {
            throw new IllegalArgumentException("choiceGroup must not mix null and non-null");
        }
        if (groupCounts.size() == 2) {
            // trộn hai nhóm: chia B10 — nhóm lẻ nhận lo, nhóm đông nhận hi
            String minority = null;
            int minSize = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> e : groupCounts.entrySet()) {
                if (e.getValue() < minSize) {
                    minSize = e.getValue();
                    minority = e.getKey();
                }
            }
            if (minSize * 2 == k) {
                throw new IllegalArgumentException("equal-size groups: no majority per B10");
            }
            List<Integer> counts = new ArrayList<>(k);
            for (SectionOnPath s : picked) {
                counts.add(s.choiceGroup().equals(minority) ? lo : hi);
            }
            return List.of(counts);
        }
        // một nhóm chung (toàn NATURAL, toàn SOCIAL, hoặc toàn null): mỗi môn một lượt nhận lo
        List<List<Integer>> out = new ArrayList<>(k);
        for (int low = 0; low < k; low++) {
            List<Integer> counts = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                counts.add(i == low ? lo : hi);
            }
            out.add(counts);
        }
        return out;
    }

    /**
     * Mọi cách chọn {@code choose} môn của phương án, theo thứ tự tổ hợp ổn định (từ điển trên
     * chỉ số theo thứ tự cấu trúc), mỗi cách chọn sinh một {@link SubjectPick} trên mỗi phân
     * bổ của {@link #allocationsOf} — cài lại độc lập từ spec, không dùng bộ gán của bộ giải.
     */
    static List<SubjectPick> picksOf(SubjectChoice choice) {
        List<SectionOnPath> sections = choice.sections();
        int n = sections.size();
        int k = choice.choose();
        List<SubjectPick> out = new ArrayList<>();
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) {
            idx[i] = i;
        }
        while (idx[0] <= n - k) {
            List<SectionOnPath> picked = new ArrayList<>(k);
            for (int i : idx) {
                picked.add(sections.get(i));
            }
            for (List<Integer> counts : allocationsOf(choice, picked)) {
                out.add(new SubjectPick(choice.optionId(), picked, counts));
            }
            int p = k - 1;
            while (p >= 0 && idx[p] == n - k + p) {
                p--;
            }
            if (p < 0) {
                break;
            }
            idx[p]++;
            for (int j = p + 1; j < k; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
        return out;
    }

    /** Khóa chuỗi của một cách chọn: {@code S1=c1+S2=c2}, đủ để đối chiếu selections. */
    static String pickKey(SubjectPick pick) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pick.sections().size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(pick.sections().get(i).subjectCode()).append('=').append(pick.counts().get(i));
        }
        return sb.toString();
    }
}
