package vn.unicom.thi360.exam.domain.solver.oracle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import vn.unicom.thi360.exam.domain.solver.CellQuota;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.DimensionQuota;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.MatrixQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.QuotaUnit;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.exam.domain.solver.oracle.OracleCase.Category;

/**
 * Sinh ca ngẫu nhiên có hạt giống cho {@code PaperSolverOracleTest} (contract W-ORACLE):
 * ≥ 500 ca, trong đó ≥ 200 ca có chỉ tiêu cấp môn trải trên ≥ 2 nhóm và có chùm, ≥ 100 ca có
 * {@link SubjectChoice}. Mọi ca nằm trong miền duyệt vét cạn của {@link BruteForceOracle}
 * (≤ 4 nhóm, ≤ 3 môn, ≤ 12 ứng viên mỗi nhóm, chùm 2–3 câu con, choice 3 môn chọn 2).
 *
 * <p>Mỗi nhóm ca có nhánh "bất khả thi": cấm một giá trị đã khai chỉ tiêu khỏi toàn bộ nguồn, hoặc
 * cắt nguồn xuống dưới số câu cần, hoặc bắt hai nhóm tranh một ứng viên — để phân bố VALID/INVALID
 * của oracle không lệch hẳn.
 */
public final class RandomCaseGenerator {

    private static final String[] ANSWER_TYPES =
            {"SINGLE_CHOICE", "SHORT_ANSWER", "TRUE_FALSE", "MULTIPLE_CHOICE"};
    private static final String[] GRADES = {"G10", "G11", "G12"};
    private static final String[] DIFFICULTIES = {"NB", "TH", "VD"};

    private final Random random;
    private int nextId;

    public RandomCaseGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Sinh {@code count} ca: {@code minQuotaSets} ca đầu là {@link Category#SUBJECT_QUOTA_SETS},
     * {@code minChoice} ca tiếp theo là {@link Category#SUBJECT_CHOICE}, phần còn lại rải ngẫu
     * nhiên trên mọi nhóm.
     */
    public List<OracleCase> generate(int count, int minQuotaSets, int minChoice) {
        List<OracleCase> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Category category;
            if (i < minQuotaSets) {
                category = Category.SUBJECT_QUOTA_SETS;
            } else if (i < minQuotaSets + minChoice) {
                category = Category.SUBJECT_CHOICE;
            } else {
                int r = random.nextInt(10);
                category = switch (r) {
                    case 0, 1 -> Category.BASIC_SINGLE;
                    case 2, 3 -> Category.SUBJECT_QUOTA;
                    case 4 -> Category.SETS;
                    case 5, 6 -> Category.SUBJECT_QUOTA_SETS;
                    case 7, 8 -> Category.SUBJECT_CHOICE;
                    default -> Category.SHARED_SOURCE;
                };
            }
            out.add(generate(category));
        }
        return out;
    }

    /** Sinh một ca của một nhóm. */
    public OracleCase generate(Category category) {
        return switch (category) {
            case BASIC_SINGLE -> basicSingle();
            case SUBJECT_QUOTA -> subjectQuota();
            case SETS -> sets();
            case SUBJECT_QUOTA_SETS -> subjectQuotaSets();
            case SUBJECT_CHOICE -> subjectChoice();
            case SHARED_SOURCE -> sharedSource();
        };
    }

    // ------------------------------------------------------------ các nhóm ca

    /** 1–3 môn × 1 nhóm câu đơn, không chỉ tiêu cấp môn; nguồn đủ hoặc thiếu. */
    private OracleCase basicSingle() {
        int subjects = pickWeighted(new int[] {50, 35, 15}, 3);
        List<SectionOnPath> sections = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        int q = 0;
        for (int s = 0; s < subjects; s++) {
            int count = 1 + random.nextInt(3); // 1..3 câu
            GroupQuota group = GroupQuota.single("g-" + nextId + "-" + s, count);
            int poolSize = count + random.nextInt(6);
            List<PathCandidate> pool = new ArrayList<>();
            String answerType = pick(ANSWER_TYPES);
            for (int i = 0; i < poolSize; i++) {
                pool.add(standalone("q" + (q++), answerType));
            }
            if (random.nextInt(4) == 0) {
                // nguồn thiếu rõ ràng: còn ít hơn số câu cần
                int leave = Math.max(0, count - 1);
                while (pool.size() > leave) {
                    pool.remove(pool.size() - 1);
                }
            }
            sources.put(group, pool);
            sections.add(section("S" + s, count, SubjectQuota.NONE, List.of(group)));
        }
        return new OracleCase(nextId++, Category.BASIC_SINGLE,
                List.of(new SolvePart.Fixed("p1", sections)), sources);
    }

    /** Một môn có chỉ tiêu dạng trả lời hoặc lớp × độ khó, 1–2 nhóm câu đơn. */
    private OracleCase subjectQuota() {
        int count = 3 + random.nextInt(4); // 3..6 câu
        int groups = random.nextBoolean() ? 1 : 2;
        List<GroupQuota> groupList = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        int remaining = count;
        for (int g = 0; g < groups; g++) {
            int take = g == groups - 1 ? remaining : 1 + random.nextInt(Math.max(1, remaining - 1));
            remaining -= take;
            groupList.add(GroupQuota.single("g-" + nextId + "-" + g, take));
        }
        SubjectQuota quota = randomSubjectQuota(count, false);
        String banned = random.nextInt(3) == 0 ? pickBannedValue(quota) : null;
        List<PathCandidate> pool = new ArrayList<>();
        for (int i = 0; i < count + 4; i++) {
            pool.add(candidateForQuota("p" + nextId + "-" + i, quota, banned));
        }
        for (int g = 0; g < groupList.size(); g++) {
            List<PathCandidate> src = new ArrayList<>(pool);
            if (g > 0 && random.nextBoolean()) {
                src.removeIf(c -> random.nextInt(4) == 0);
            }
            cap(src);
            sources.put(groupList.get(g), src);
        }
        SectionOnPath section = section("MATH", count, quota, groupList);
        return new OracleCase(nextId++, Category.SUBJECT_QUOTA,
                List.of(SolvePart.Fixed.of("p1", section)), sources);
    }

    /** Một môn gồm nhóm chùm (+ có thể một nhóm câu đơn), không chỉ tiêu cấp môn. */
    private OracleCase sets() {
        int setSize = 2 + random.nextInt(2);
        int setCount = 1 + random.nextInt(2);
        List<GroupQuota> groups = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        GroupQuota setGroup = GroupQuota.set("gs-" + nextId, setCount, setSize,
                random.nextInt(3) == 0 ? pick(ANSWER_TYPES) : null);
        groups.add(setGroup);
        int total = setCount * setSize;
        int singles = 0;
        if (random.nextBoolean()) {
            singles = 1 + random.nextInt(2);
            total += singles;
            groups.add(GroupQuota.single("g-" + nextId, singles));
        }
        String childType = setGroup.childAnswerType();
        List<PathCandidate> pool = new ArrayList<>();
        boolean shortfall = random.nextInt(4) == 0;
        int n = shortfall ? Math.max(0, setCount - 1) : setCount + random.nextInt(4);
        for (int i = 0; i < n; i++) {
            pool.add(setCandidate("set" + nextId + "-" + i, setSize, childType));
        }
        if (random.nextInt(4) == 0 && !pool.isEmpty()) {
            // chùm không đúng kích thước/dạng con → không dùng được
            pool.add(setCandidate("bad" + nextId, setSize == 2 ? 3 : 2, null));
        }
        sources.put(setGroup, pool);
        if (singles > 0) {
            List<PathCandidate> singles0 = new ArrayList<>();
            boolean singleShort = random.nextInt(4) == 0;
            int ns = singleShort ? Math.max(0, singles - 1) : singles + random.nextInt(3);
            for (int i = 0; i < ns; i++) {
                singles0.add(standalone("s" + nextId + "-" + i, pick(ANSWER_TYPES)));
            }
            sources.put(groups.get(1), singles0);
        }
        return new OracleCase(nextId++, Category.SETS,
                List.of(SolvePart.Fixed.of("p1",
                        section("ENG", total, SubjectQuota.NONE, groups))), sources);
    }

    /**
     * Chỉ tiêu cấp môn (dạng trả lời và/hoặc lớp × độ khó, COUNT hoặc PERCENT) trải trên một
     * nhóm chùm + một nhóm câu đơn — trọng tâm của contract (≥ 200 ca).
     */
    private OracleCase subjectQuotaSets() {
        int setSize = 2 + random.nextInt(2);
        int setCount = 1 + random.nextInt(2);
        int singles = 1 + random.nextInt(3);
        int total = setCount * setSize + singles;
        List<GroupQuota> groups = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        GroupQuota setGroup = GroupQuota.set("gs-" + nextId, setCount, setSize, null);
        GroupQuota singleGroup = random.nextInt(4) == 0
                ? GroupQuota.remainder("gr-" + nextId)
                : GroupQuota.single("g-" + nextId, singles);
        groups.add(setGroup);
        groups.add(singleGroup);
        boolean percent = random.nextInt(4) == 0;
        SubjectQuota quota = randomSubjectQuota(total, percent);
        String banned = random.nextInt(3) == 0 ? pickBannedValue(quota) : null;

        int usableSets = setCount + random.nextInt(3);
        List<PathCandidate> setPool = new ArrayList<>();
        for (int i = 0; i < usableSets; i++) {
            setPool.add(setCandidateForQuota("set" + nextId + "-" + i, setSize, quota, banned));
        }
        if (random.nextInt(3) == 0) {
            // một chùm lệch chỉ tiêu: lấy thì vượt chỉ tiêu môn
            setPool.add(setCandidate("bad" + nextId, setSize, null));
        }
        sources.put(setGroup, setPool);

        List<PathCandidate> singlePool = new ArrayList<>();
        for (int i = 0; i < singles + random.nextInt(5); i++) {
            singlePool.add(candidateForQuota("q" + nextId + "-" + i, quota, banned));
        }
        cap(singlePool);
        sources.put(singleGroup, singlePool);
        return new OracleCase(nextId++, Category.SUBJECT_QUOTA_SETS,
                List.of(SolvePart.Fixed.of("p1", section("MATH", total, quota, groups))),
                sources);
    }

    /**
     * {@code SUBJECT_CHOICE} 3 môn chọn 2, countsByPick bằng nhau hoặc lệch (3,2); các môn có
     * nhóm câu đơn (đôi khi có chùm) và có thể kèm chỉ tiêu cấp môn theo tỉ lệ.
     */
    private OracleCase subjectChoice() {
        boolean uneven = random.nextInt(3) == 0;
        int needed = uneven ? 3 : 4;
        List<Integer> countsByPick = uneven ? List.of(3, 2) : List.of(4, 4);
        List<SectionOnPath> sections = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        for (int s = 0; s < 3; s++) {
            boolean hasSet = random.nextInt(4) == 0;
            List<GroupQuota> groups = new ArrayList<>();
            SubjectQuota quota = SubjectQuota.NONE;
            if (random.nextInt(4) == 0) {
                quota = randomSubjectQuota(needed, true);
            }
            String banned = random.nextInt(5) == 0 ? pickBannedValue(quota) : null;
            if (hasSet) {
                GroupQuota sg = GroupQuota.set("gs-" + nextId + "-" + s, 1, 2, null);
                GroupQuota rg = GroupQuota.remainder("gr-" + nextId + "-" + s);
                groups.add(sg);
                groups.add(rg);
                List<PathCandidate> setPool = new ArrayList<>();
                for (int i = 0; i < 2 + random.nextInt(2); i++) {
                    setPool.add(setCandidateForQuota("set" + nextId + "-" + s + "-" + i, 2,
                            quota, banned));
                }
                sources.put(sg, setPool);
                List<PathCandidate> pool = new ArrayList<>();
                int shortfall = random.nextInt(4) == 0 ? 1 + random.nextInt(2) : 0;
                for (int i = 0; i < needed - 2 - shortfall + 3; i++) {
                    pool.add(candidateForQuota("q" + nextId + "-" + s + "-" + i, quota, banned));
                }
                cap(pool);
                sources.put(rg, pool);
            } else {
                GroupQuota g = GroupQuota.remainder("g-" + nextId + "-" + s);
                groups.add(g);
                List<PathCandidate> pool = new ArrayList<>();
                int shortfall = random.nextInt(4) == 0 ? 1 + random.nextInt(2) : 0;
                for (int i = 0; i < needed - shortfall + 2; i++) {
                    pool.add(candidateForQuota("q" + nextId + "-" + s + "-" + i, quota, banned));
                }
                cap(pool);
                sources.put(g, pool);
            }
            sections.add(new SectionOnPath("CS" + s, "sec-cs" + s, null, quota, groups));
        }
        SubjectChoice choice = new SubjectChoice("opt-sc", sections, 2, countsByPick);
        List<SolvePart> parts = List.of(SolvePart.Choice.of("p1", List.of(choice)));
        return new OracleCase(nextId++, Category.SUBJECT_CHOICE, parts, sources);
    }

    /** Hai nhóm câu đơn tranh nhau cùng một nhóm ứng viên — tính duy nhất trên đường. */
    private OracleCase sharedSource() {
        int countA = 1 + random.nextInt(2);
        int countB = 1 + random.nextInt(2);
        GroupQuota a = GroupQuota.single("ga-" + nextId, countA);
        GroupQuota b = GroupQuota.single("gb-" + nextId, countB);
        boolean starve = random.nextInt(3) == 0; // ~1/3 ca: tổng nguồn < tổng câu cần
        int poolSize = starve ? Math.max(1, countA + countB - 1) : countA + countB + 1;
        List<PathCandidate> shared = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            shared.add(standalone("sh" + nextId + "-" + i, pick(ANSWER_TYPES)));
        }
        List<PathCandidate> srcA = new ArrayList<>(shared);
        List<PathCandidate> srcB = new ArrayList<>(shared);
        if (random.nextBoolean() && srcA.size() > 1) {
            srcA.remove(0);
        }
        if (random.nextBoolean() && srcB.size() > 1) {
            srcB.remove(srcB.size() - 1);
        }
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        sources.put(a, srcA);
        sources.put(b, srcB);
        SectionOnPath section = section("PHY", countA + countB, SubjectQuota.NONE,
                List.of(a, b));
        return new OracleCase(nextId++, Category.SHARED_SOURCE,
                List.of(SolvePart.Fixed.of("p1", section)), sources);
    }

    // ------------------------------------------------------------ bộ phận sinh

    private SectionOnPath section(String subject, int count, SubjectQuota quota,
            List<GroupQuota> groups) {
        return new SectionOnPath(subject, "sec-" + subject + "-" + nextId, count, quota, groups);
    }

    private PathCandidate standalone(String id, String answerType) {
        return new PathCandidate(id, 1, null, null, null,
                List.of(new CountingUnit(null, answerType, pick(GRADES), pick(DIFFICULTIES),
                        List.of(), List.of(), BigDecimal.ONE)));
    }

    private PathCandidate setCandidate(String setId, int size, String childType) {
        List<CountingUnit> units = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            units.add(new CountingUnit(setId + "-c" + i,
                    childType != null ? childType : pick(ANSWER_TYPES), pick(GRADES),
                    pick(DIFFICULTIES), List.of(), List.of(), BigDecimal.ONE));
        }
        return new PathCandidate(setId, 1, setId, null, null, units);
    }

    /** Chỉ tiêu cấp môn ngẫu nhiên: dạng trả lời và/hoặc hai chiều lớp, độ khó (COUNT/PERCENT). */
    private SubjectQuota randomSubjectQuota(int total, boolean percent) {
        QuotaUnit unit = percent ? QuotaUnit.PERCENT : QuotaUnit.COUNT;
        SubjectQuota.AnswerQuotas answerQuotas = null;
        MatrixQuota matrix = null;
        if (random.nextBoolean()) {
            String major = pick(ANSWER_TYPES);
            String minor = pick(ANSWER_TYPES);
            while (minor.equals(major)) {
                minor = pick(ANSWER_TYPES);
            }
            int small = 1 + random.nextInt(Math.max(1, total / 3));
            List<DimensionQuota> items = percent
                    ? List.of(new DimensionQuota(major, (total - small) * 100 / total),
                            new DimensionQuota(minor, small * 100 / total))
                    : List.of(new DimensionQuota(major, total - small),
                            new DimensionQuota(minor, small));
            answerQuotas = new SubjectQuota.AnswerQuotas(items, unit);
        }
        if (random.nextBoolean()) {
            List<DimensionQuota> grades = split(total, 2, unit);
            List<DimensionQuota> diffs = split(total, 2 + random.nextInt(2), unit);
            matrix = new MatrixQuota.Separate(
                    assignCodes(grades, GRADES), assignCodes(diffs, DIFFICULTIES), unit);
        }
        if (answerQuotas == null && matrix == null) {
            answerQuotas = new SubjectQuota.AnswerQuotas(
                    List.of(new DimensionQuota(ANSWER_TYPES[0], percent ? 100 : total)), unit);
        }
        return new SubjectQuota(answerQuotas, matrix);
    }

    private List<DimensionQuota> split(int total, int parts, QuotaUnit unit) {
        List<DimensionQuota> out = new ArrayList<>(parts);
        int left = total;
        for (int i = 0; i < parts; i++) {
            int v = i == parts - 1 ? left : Math.max(0, random.nextInt(left + 1));
            left -= v;
            out.add(new DimensionQuota("_" + i, unit == QuotaUnit.PERCENT ? v * 100 / total : v));
        }
        return out;
    }

    private List<DimensionQuota> assignCodes(List<DimensionQuota> quotas, String[] codes) {
        List<DimensionQuota> out = new ArrayList<>(quotas.size());
        for (int i = 0; i < quotas.size(); i++) {
            out.add(new DimensionQuota(codes[i % codes.length], quotas.get(i).quota()));
        }
        return out;
    }

    /**
     * Một giá trị đã khai chỉ tiêu của môn để cấm khỏi toàn bộ nguồn (ca bất khả thi):
     * một dạng trả lời, một khối lớp hay một độ khó đã khai.
     */
    private String pickBannedValue(SubjectQuota quota) {
        List<String> declared = new ArrayList<>();
        if (quota.answerQuotas() != null) {
            quota.answerQuotas().items().forEach(d -> declared.add(d.code()));
        }
        if (quota.matrix() instanceof MatrixQuota.Separate s) {
            s.grades().forEach(d -> declared.add(d.code()));
            s.difficulties().forEach(d -> declared.add(d.code()));
        } else if (quota.matrix() instanceof MatrixQuota.Combined c) {
            c.cells().forEach(cell -> declared.add(cell.gradeCode()));
        }
        if (declared.isEmpty()) {
            return null;
        }
        return declared.get(random.nextInt(declared.size()));
    }

    /**
     * Ứng viên có phân loại lấy từ đúng miền chỉ tiêu đã khai để ca khả thi có thật;
     * {@code banned} là một giá trị bị cấm hoàn toàn khỏi nguồn (khiến chỉ tiêu không đạt được).
     */
    private PathCandidate candidateForQuota(String id, SubjectQuota quota, String banned) {
        String type = pick(ANSWER_TYPES);
        if (quota.answerQuotas() != null && random.nextInt(4) > 0) {
            List<String> items = quota.answerQuotas().items().stream()
                    .map(DimensionQuota::code).filter(t -> !t.equals(banned)).toList();
            if (!items.isEmpty()) {
                type = items.get(random.nextInt(items.size()));
            }
        }
        if (type.equals(banned)) {
            type = otherValue(banned, ANSWER_TYPES);
        }
        String grade = pick(GRADES);
        String diff = pick(DIFFICULTIES);
        if (quota.matrix() instanceof MatrixQuota.Separate s) {
            if (!s.grades().isEmpty() && random.nextInt(4) > 0) {
                List<String> gs = s.grades().stream().map(DimensionQuota::code)
                        .filter(v -> !v.equals(banned)).toList();
                if (!gs.isEmpty()) {
                    grade = gs.get(random.nextInt(gs.size()));
                }
            }
            if (!s.difficulties().isEmpty() && random.nextInt(4) > 0) {
                List<String> ds = s.difficulties().stream().map(DimensionQuota::code)
                        .filter(v -> !v.equals(banned)).toList();
                if (!ds.isEmpty()) {
                    diff = ds.get(random.nextInt(ds.size()));
                }
            }
        } else if (quota.matrix() instanceof MatrixQuota.Combined c && !c.cells().isEmpty()
                && random.nextInt(4) > 0) {
            List<CellQuota> cells = c.cells().stream()
                    .filter(cell -> !cell.gradeCode().equals(banned)
                            && !cell.difficultyCode().equals(banned))
                    .toList();
            if (!cells.isEmpty()) {
                CellQuota cell = cells.get(random.nextInt(cells.size()));
                grade = cell.gradeCode();
                diff = cell.difficultyCode();
            }
        }
        if (grade.equals(banned)) {
            grade = otherValue(banned, GRADES);
        }
        if (diff.equals(banned)) {
            diff = otherValue(banned, DIFFICULTIES);
        }
        return new PathCandidate(id, 1, null, null, null,
                List.of(new CountingUnit(null, type, grade, diff, List.of(), List.of(),
                        BigDecimal.ONE)));
    }

    private PathCandidate setCandidateForQuota(String setId, int size, SubjectQuota quota,
            String banned) {
        List<CountingUnit> units = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PathCandidate c = candidateForQuota(setId + "-q" + i, quota, banned);
            CountingUnit u = c.units().get(0);
            units.add(new CountingUnit(setId + "-c" + i, u.answerType(), u.gradeCode(),
                    u.difficultyCode(), List.of(), List.of(), u.points()));
        }
        return new PathCandidate(setId, 1, setId, null, null, units);
    }

    private String otherValue(String banned, String[] pool) {
        for (String v : pool) {
            if (!v.equals(banned)) {
                return v;
            }
        }
        return pool[0];
    }

    private void cap(List<PathCandidate> pool) {
        while (pool.size() > 12) {
            pool.remove(pool.size() - 1);
        }
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private int pickWeighted(int[] weights, int max) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = random.nextInt(total);
        int acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (r < acc) {
                return Math.min(i + 1, max);
            }
        }
        return max;
    }
}
