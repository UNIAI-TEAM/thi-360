package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.oracle.SteppingClock;

/**
 * Các ca bắt buộc của {@link PaperSolver} theo spec mục 10 (danh sách bộ giải) và các hồi quy
 * R1-1…R1-7 của contract BE-5a-r2 §6. Mọi ca dựng từ spec, không phụ thuộc cài đặt bộ giải.
 */
class PaperSolverSpecCasesTest {

    private final PaperSolver solver = new PaperSolver();

    // ------------------------------------------------------------ spec 10: ca bắt buộc

    @Test
    @DisplayName("hai nhóm cùng chỉ có q1 làm nguồn: từng nhóm đủ nhưng cả đường INVALID")
    void twoGroupsShareOnlyQ1() {
        GroupQuota g1 = GroupQuota.single("g1", 1);
        GroupQuota g2 = GroupQuota.single("g2", 1);
        SectionOnPath section = section("S", 2, List.of(g1, g2));
        PathCandidate q1 = single("q1");
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> List.of(q1), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.reason()).isNotNull();
    }

    @Test
    @DisplayName("tổng năng lực đủ nhưng ô lớp × độ khó của nhóm không khớp → INVALID")
    void competencyTotalsButCellsMismatch() {
        // nhóm cần 2 câu với ô (G10,NB)=1 và (G11,TH)=1; nguồn toàn (G10,TH): tổng đủ, ô không
        GroupQuota g = new GroupQuota("g1", GroupQuota.GroupKind.SINGLE, 2, null, null, null,
                null, new MatrixQuota.Combined(List.of(
                        new CellQuota("G10", "NB", 1), new CellQuota("G11", "TH", 1))),
                List.of(), List.of(), null, null);
        SectionOnPath section = section("S", 2, List.of(g));
        List<PathCandidate> pool = List.of(
                unit("q1", "G10", "TH"), unit("q2", "G10", "TH"), unit("q3", "G10", "TH"));
        PathResult result = solver.solvePath(path(List.of(section)), g2 -> pool,
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("ô đủ số đếm nhưng không ghép được bộ nguyên vẹn (cần 3NB+2TH, chỉ có chùm 4NB+1TH) → INVALID")
    void setCannotPackWhole() {
        // môn 5 câu, chỉ tiêu độ khó NB=3 TH=2; một nhóm chùm 1×5, nguồn toàn chùm 4NB+1TH
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(List.of(),
                List.of(new DimensionQuota("NB", 3), new DimensionQuota("TH", 2))));
        GroupQuota setGroup = GroupQuota.set("gs", 1, 5, null);
        SectionOnPath section = new SectionOnPath("S", "sec", 5, quota, List.of(setGroup));
        PathCandidate wrong = set("set1", 5, 4, 1); // 4 NB + 1 TH
        PathResult result = solver.solvePath(path(List.of(section)), g -> List.of(wrong),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("Điền khuyết lấy nguyên vẹn: ứng viên CLOZE nhiều đơn vị vào nhóm câu đơn")
    void clozeTakenWhole() {
        GroupQuota g = GroupQuota.single("g1", 3);
        SectionOnPath section = section("S", 3, List.of(g));
        PathCandidate cloze = cloze("clz1", 3);
        PathResult result = solver.solvePath(path(List.of(section)), x -> List.of(cloze),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness()).hasSize(1);
        assertThat(result.witness().get(0).groups().get(0).picks())
                .containsExactly(new QuestionPick("clz1", 1));
    }

    @Test
    @DisplayName("bộ câu hỏi hợp lệ được lấy nguyên (R1-5: đăng ký setPackedWhole)")
    void setPackedWhole() {
        GroupQuota setGroup = GroupQuota.set("gs", 1, 2, null);
        SectionOnPath section = section("S", 2, List.of(setGroup));
        PathCandidate set = set("set1", 2, 1, 1);
        PathResult result = solver.solvePath(path(List.of(section)), g -> List.of(set),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().get(0).groups().get(0).picks())
                .containsExactly(new QuestionPick("set1", 1));
    }

    @Test
    @DisplayName("câu con vừa trong bộ vừa có bản độc lập: chỉ dùng một lần trên cả đường")
    void childInSetAndStandaloneUsedOnce() {
        // chùm {c1,c2} bắt buộc cho nhóm chùm; nhóm đơn chỉ có câu độc lập mang id c1 → xung đột
        GroupQuota setGroup = GroupQuota.set("gs", 1, 2, null);
        GroupQuota singleGroup = GroupQuota.single("g2", 1);
        SectionOnPath section = section("S", 3, List.of(setGroup, singleGroup));
        PathCandidate set = new PathCandidate("set1", 1, "set1", null, null,
                List.of(child("c1"), child("c2")));
        PathCandidate standaloneC1 = single("c1");
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> g == setGroup ? List.of(set) : List.of(standaloneC1), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("hết hạn trong prep (đồng hồ vượt hạn ngay) → UNCONFIRMED, R1-4")
    void deadlineDuringPrep() {
        GroupQuota g = GroupQuota.single("g1", 2);
        SectionOnPath section = section("S", 2, List.of(g));
        List<PathCandidate> pool = List.of(single("q1"), single("q2"), single("q3"));
        // đồng hồ nhảy 1s mỗi lần đọc; hạn 1 ms → hết hạn ngay khi bắt đầu
        PaperSolver expired = new PaperSolver(
                new SolverLimits(200, 20_000, Duration.ofMillis(1), 8),
                new SteppingClock(1_000));
        PathResult result = expired.solvePath(path(List.of(section)), x -> pool,
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
    }

    @Test
    @DisplayName("hết hạn trong khi tìm kiếm → UNCONFIRMED, R1-4")
    void deadlineDuringSearch() {
        // ca cần duyệt tổ hợp: 4 nhóm cùng tranh nguồn, một nhóm không xếp được
        List<GroupQuota> groups = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            groups.add(GroupQuota.single("g" + i, 2));
        }
        SectionOnPath section = section("S", 8, groups);
        List<PathCandidate> pool = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            pool.add(single("q" + i));
        }
        // đồng hồ nhảy chậm để qua prep, hết hạn giữa duyệt
        PaperSolver limited = new PaperSolver(
                new SolverLimits(200, 20_000, Duration.ofMillis(30), 8),
                new SteppingClock(1));
        PathResult result = limited.solvePath(path(List.of(section)), x -> pool,
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
    }

    @Test
    @DisplayName("vượt 200 đường: mỗi cách chọn môn của SUBJECT_CHOICE tính một đường")
    void pathLimitCountsSubjectPicks() {
        // SUBJECT_CHOICE 8 môn chọn 2 = 28 cách; phần thứ hai có 8 phương án thường
        // → tích 28 × 8 = 224 đường > 200 (mỗi cách chọn môn tính một đường)
        List<SectionOnPath> choiceSections = new ArrayList<>();
        for (int s = 0; s < 8; s++) {
            GroupQuota g = GroupQuota.single("gc" + s, 1);
            choiceSections.add(new SectionOnPath("CS" + s, "cs" + s, null,
                    SubjectQuota.NONE, List.of(g)));
        }
        SubjectChoice sc = new SubjectChoice("opt-sc", choiceSections, 2, List.of(1, 1));
        List<SolveOption> branchOptions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            GroupQuota g = GroupQuota.single("go" + i, 1);
            branchOptions.add(new SolveOption.Sections("opt-" + i,
                    List.of(new SectionOnPath("OS" + i, "os" + i, 1, SubjectQuota.NONE,
                            List.of(g)))));
        }
        SolvePart part1 = SolvePart.Choice.of("p1", List.of(sc));
        SolvePart part2 = SolvePart.Choice.of("p2", branchOptions);
        SolveOutcome outcome = solver.solveAll(List.of(part1, part2), g -> List.of(single("q")),
                SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(outcome.reason()).isEqualTo(SolveReason.PATH_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("solveAll: một đường INVALID một đường VALID → tổng hợp INVALID, giữ cả hai kết quả (R1-7)")
    void solveAllMixedAggregation() {
        GroupQuota gOk = GroupQuota.single("g-ok", 1);
        GroupQuota gBad = GroupQuota.single("g-bad", 1);
        SectionOnPath secOk = new SectionOnPath("OK", "sec-ok", 1, SubjectQuota.NONE,
                List.of(gOk));
        SectionOnPath secBad = new SectionOnPath("BAD", "sec-bad", 1, SubjectQuota.NONE,
                List.of(gBad));
        SolvePart part = SolvePart.Choice.of("p1", List.of(
                new SolveOption.Sections("opt-ok", List.of(secOk)),
                new SolveOption.Sections("opt-bad", List.of(secBad))));
        SolveOutcome outcome = solver.solveAll(List.of(part),
                g -> g == gBad ? List.of() : List.of(single("q1")), SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(outcome.results()).hasSize(2);
        assertThat(outcome.results().stream().map(PathResult::status))
                .containsExactlyInAnyOrder(SolveStatus.VALID, SolveStatus.INVALID);
    }

    // ------------------------------------------------------------ hồi quy R1-x

    @Test
    @DisplayName("R1-1: chỉ được dùng ứng viên của đúng nhóm (câu cầu nối không sang nhóm khác)")
    void unusableBridgeCandidate() {
        // gA chỉ có q1; gB có q1,q2 → q1 phải về gA, gB lấy q2; solver lấy q2 cho gA là sai
        GroupQuota ga = GroupQuota.single("gA", 1);
        GroupQuota gb = GroupQuota.single("gB", 1);
        SectionOnPath section = section("S", 2, List.of(ga, gb));
        PathCandidate q1 = single("q1");
        PathCandidate q2 = single("q2");
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> g == ga ? List.of(q1) : List.of(q1, q2), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        // gA chỉ được chứa q1
        for (SubjectWitness sw : result.witness()) {
            for (GroupWitness gw : sw.groups()) {
                if (gw.groupId().equals("gA")) {
                    assertThat(gw.picks()).containsExactly(new QuestionPick("q1", 1));
                }
            }
        }
    }

    @Test
    @DisplayName("R1-2: nhóm cần câu dương mà không có yêu cầu nào thỏa → không được trả witness rỗng VALID")
    void positiveCountNoRequirement() {
        GroupQuota g = GroupQuota.single("g1", 2);
        SectionOnPath section = section("S", 2, List.of(g));
        PathResult result = solver.solvePath(path(List.of(section)), x -> List.of(),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.witness()).isEmpty();
    }

    @Test
    @DisplayName("R1-3: hai nhóm trùng groupId trên một đường → INVALID DUPLICATE_GROUP_ID")
    void duplicateGroupIdRejected() {
        GroupQuota g1 = GroupQuota.single("dup", 1);
        GroupQuota g2 = GroupQuota.single("dup", 1);
        SectionOnPath section = section("S", 2, List.of(g1, g2));
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> List.of(single("q1"), single("q2")), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.reason()).isEqualTo(SolveReason.DUPLICATE_GROUP_ID);
    }

    @Test
    @DisplayName("xem trước: cùng hạt giống cho cùng đề (preview một cách chọn)")
    void previewSeedDeterministic() {
        List<SectionOnPath> sections = new ArrayList<>();
        List<PathCandidate> pools = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            GroupQuota g = GroupQuota.remainder("g" + s);
            List<PathCandidate> pool = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                PathCandidate c = single("s" + s + "q" + i);
                pool.add(c);
                pools.add(c);
            }
            sections.add(new SectionOnPath("CS" + s, "sec" + s, null, SubjectQuota.NONE,
                    List.of(g)));
        }
        SubjectChoice choice = new SubjectChoice("opt-sc", sections, 2, List.of(3, 2));
        CandidateSource source = g -> pools;
        PathResult a = solver.solvePick(choice, List.of("CS0", "CS1"), 42L, source);
        PathResult b = solver.solvePick(choice, List.of("CS0", "CS1"), 42L, source);
        assertThat(a.status()).isEqualTo(SolveStatus.VALID);
        assertThat(b.status()).isEqualTo(SolveStatus.VALID);
        assertThat(b.witness()).isEqualTo(a.witness());
    }

    // ------------------------------------------------------------ bộ phận dựng

    private static SolvePath path(List<SectionOnPath> sections) {
        return SolvePath.of(0, sections);
    }

    private static SectionOnPath section(String subject, int count, List<GroupQuota> groups) {
        return new SectionOnPath(subject, "sec-" + subject, count, SubjectQuota.NONE, groups);
    }

    private static PathCandidate single(String id) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", "G10", "NB", BigDecimal.ONE);
    }

    private static PathCandidate unit(String id, String grade, String difficulty) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", grade, difficulty,
                BigDecimal.ONE);
    }

    private static CountingUnit child(String childId) {
        return new CountingUnit(childId, "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                BigDecimal.ONE);
    }

    /** Chùm {@code size} câu con với {@code nb} câu NB còn lại TH. */
    private static PathCandidate set(String setId, int size, int nb, int th) {
        List<CountingUnit> units = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String diff = i < nb ? "NB" : "TH";
            units.add(new CountingUnit(setId + "-c" + i, "SINGLE_CHOICE", "G10", diff,
                    List.of(), List.of(), BigDecimal.ONE));
        }
        return new PathCandidate(setId, 1, setId, null, null, units);
    }

    private static PathCandidate cloze(String id, int blanks) {
        List<CountingUnit> units = new ArrayList<>(blanks);
        for (int i = 0; i < blanks; i++) {
            units.add(new CountingUnit(null, "CLOZE", "G10", "NB", List.of(), List.of(),
                    BigDecimal.ONE));
        }
        return new PathCandidate(id, 1, id, null, null, units);
    }
}
