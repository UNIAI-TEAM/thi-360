package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.oracle.BruteForceOracle;

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
    @DisplayName("năng lực và ô lớp × độ khó riêng lẻ đủ nhưng không xếp được cùng nhau → INVALID")
    void competencyTotalsButCellsMismatch() {
        // nhóm cần 2 câu: ô (G10,NB)=1 và (G11,TH)=1, năng lực P=1.
        // Nguồn đủ từng chỉ tiêu riêng (có ô (G10,NB), có ô (G11,TH), có một câu P) nhưng câu
        // duy nhất mang P lại là ô (G10,TH) → không có phép xếp thỏa đồng thời.
        GroupQuota g = new GroupQuota("g1", GroupQuota.GroupKind.SINGLE, 2, null, null, null,
                null, new MatrixQuota.Combined(List.of(
                        new CellQuota("G10", "NB", 1), new CellQuota("G11", "TH", 1))),
                List.of(new CompetencyQuota("P", 1)), List.of(), null, null);
        SectionOnPath section = section("S", 2, List.of(g));
        PathCandidate q1 = unit("q1", "G10", "NB"); // ô đúng, không P
        PathCandidate q2 = unit("q2", "G11", "TH"); // ô đúng, không P
        PathCandidate q3 = new PathCandidate("q3", 1, null, null,
                List.of(new CountingUnit(null, "SINGLE_CHOICE", "G10", "TH", List.of(),
                        List.of("P"), BigDecimal.ONE))); // có P nhưng ô lệch
        PathResult result = solver.solvePath(path(List.of(section)),
                g2 -> List.of(q1, q2, q3), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);

        // ca đối chứng khả thi: thêm một câu (G11,TH) có P → lấy q1 + q4
        PathCandidate q4 = new PathCandidate("q4", 1, null, null,
                List.of(new CountingUnit(null, "SINGLE_CHOICE", "G11", "TH", List.of(),
                        List.of("P"), BigDecimal.ONE)));
        PathResult feasible = solver.solvePath(path(List.of(section)),
                g2 -> List.of(q1, q2, q3, q4), SolveMode.check());
        assertThat(feasible.status()).isEqualTo(SolveStatus.VALID);
        for (GroupWitness gw : feasible.witness().get(0).groups()) {
            assertThat(gw.picks()).containsExactlyInAnyOrder(
                    new QuestionPick("q1", 1), new QuestionPick("q4", 1));
        }
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
        PathCandidate set = new PathCandidate("set1", 1, "set1", null,
                List.of(child("c1"), child("c2")));
        PathCandidate standaloneC1 = single("c1");
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> g == setGroup ? List.of(set) : List.of(standaloneC1), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("hết hạn trong chuẩn bị ứng viên sau khi nạp nguồn")
    void deadlineDuringPrep() {
        GroupQuota group = GroupQuota.single("g", 1);
        SectionOnPath section = section("S", 1, List.of(group));
        PhaseClock clock = new PhaseClock("Prep", "prepare");
        List<String> fetched = new ArrayList<>();
        CandidateSource source = g -> {
            fetched.add(g.groupId());
            return List.of(single("q"));
        };
        PaperSolver limited = new PaperSolver(
                new SolverLimits(200, 20_000, Duration.ofSeconds(30), 8), clock);
        PathResult result = limited.solvePath(path(List.of(section)), source, SolveMode.check());
        assertThat(fetched).containsExactly("g");
        assertThat(clock.expiredReads).isPositive();
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("hết hạn trong quay lui với chùm và nhiều phân hoạch")
    void deadlineDuringSearch() {
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 3), new DimensionQuota("G11", 3)),
                List.of(new DimensionQuota("NB", 3), new DimensionQuota("TH", 3))));
        GroupQuota setGroup = GroupQuota.set("gs", 1, 2, null);
        GroupQuota rest = GroupQuota.remainder("gr");
        SectionOnPath section = new SectionOnPath("S", "sec", 6, quota,
                List.of(setGroup, rest));
        // Điều chỉnh kích cỡ nguồn để một lần kiểm định kỳ rơi trong vòng quay lui.
        // Đồng hồ chỉ hết hạn trong search, nên không thể nhầm với pha nạp hoặc dựng lớp.
        PhaseClock clock = null;
        PathResult result = null;
        for (int n = 1; n <= 1024; n++) {
            List<PathCandidate> singles = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                singles.add(unit("q" + i, i % 2 == 0 ? "G10" : "G11",
                        (i / 2) % 2 == 0 ? "NB" : "TH"));
            }
            PhaseClock attemptClock = new PhaseClock("PathSearch", "search");
            List<String> fetched = new ArrayList<>();
            CandidateSource source = g -> {
                fetched.add(g.groupId());
                return g == setGroup
                        ? List.of(setPair("s1", "G10", "NB"), setPair("s2", "G11", "TH"))
                        : singles;
            };
            PaperSolver limited = new PaperSolver(
                    new SolverLimits(200, 20_000, Duration.ofSeconds(30), 8), attemptClock);
            PathResult attempt = limited.solvePath(path(List.of(section)), source, SolveMode.check());
            assertThat(fetched).containsExactlyInAnyOrder("gs", "gr");
            if (attemptClock.expiredReads > 0) {
                clock = attemptClock;
                result = attempt;
                break;
            }
        }
        assertThat(clock).as("phải kiểm hạn trong vòng quay lui").isNotNull();
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
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
    @DisplayName("R1-1: cầu nối U/q không dùng được không được gộp định danh hai nhóm")
    void unusableBridgeCandidate() {
        // Cầu nối không dùng được mang cả hai khóa U và q; hai câu độc lập vẫn được chọn.
        GroupQuota ga = new GroupQuota("gA", GroupQuota.GroupKind.SINGLE, 1, "U", null, null,
                null, null, List.of(), List.of(), null, null);
        GroupQuota gb = new GroupQuota("gB", GroupQuota.GroupKind.SINGLE, 1, "Q", null, null,
                null, null, List.of(), List.of(), null, null);
        SectionOnPath section = section("S", 2, List.of(ga, gb));
        PathCandidate bridge = new PathCandidate("U", 1, null, null, List.of(
                new CountingUnit(null, "U", "G10", "NB", List.of(), List.of(),
                        BigDecimal.ONE),
                new CountingUnit("q", "Q", "G10", "NB", List.of(), List.of(),
                        BigDecimal.ONE)));
        PathCandidate u1 = PathCandidate.standalone("U", 1, "U", "G10", "NB", BigDecimal.ONE);
        PathCandidate q1 = PathCandidate.standalone("q", 1, "Q", "G10", "NB", BigDecimal.ONE);
        PathResult result = solver.solvePath(path(List.of(section)),
                g -> g == ga ? List.of(bridge, u1) : List.of(bridge, q1), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        // đường VALID phải thành bằng hai ứng viên dùng được, không nhờ cầu nối
        for (GroupWitness gw : result.witness().get(0).groups()) {
            if (gw.groupId().equals("gA")) {
                assertThat(gw.picks()).containsExactly(new QuestionPick("U", 1));
            } else {
                assertThat(gw.picks()).containsExactly(new QuestionPick("q", 1));
            }
        }
    }

    @Test
    @DisplayName("R1-2: số câu dương không có yêu cầu nào phủ → không được trả witness rỗng VALID")
    void positiveCountNoRequirement() {
        GroupQuota g = GroupQuota.single("g1", 2);
        SectionOnPath section = section("S", 2, List.of(g));
        PathResult result = solver.solvePath(path(List.of(section)), x -> List.of(),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.witness()).isEmpty();

        // nguồn đủ nhưng yêu cầu rỗng: mục 2 câu có nhóm khai 0 câu
        SectionOnPath zero = new SectionOnPath("S", "sec-zero", 2, SubjectQuota.NONE,
                List.of(GroupQuota.single("g0", 0)));
        PathResult under = solver.solvePath(path(List.of(zero)),
                x -> List.of(single("q1"), single("q2")), SolveMode.check());
        assertThat(under.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(under.witness()).isEmpty();
    }

    @Test
    @DisplayName("R2-5: tổng nhóm của mục phải bằng đúng số câu mục: thiếu nhóm lấp → INVALID")
    void sectionTotalMustBeFilled() {
        // mục 2 câu chỉ khai một nhóm cố định 1 câu, không nhóm "lấy phần còn lại"
        SectionOnPath short_ = new SectionOnPath("S", "sec-short", 2, SubjectQuota.NONE,
                List.of(GroupQuota.single("g1", 1)));
        PathResult r = solver.solvePath(path(List.of(short_)),
                x -> List.of(single("q1"), single("q2")), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.witness()).isEmpty();

        // mục 1 câu không khai nhóm nào
        SectionOnPath noGroups = new SectionOnPath("S", "sec-ng", 1, SubjectQuota.NONE,
                List.of());
        PathResult r2 = solver.solvePath(path(List.of(noGroups)),
                x -> List.of(single("q1")), SolveMode.check());
        assertThat(r2.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r2.witness()).isEmpty();
    }

    @Test
    @DisplayName("R1-3: cùng groupId khác giá trị trên hai đường loại trừ nhau → mỗi đường VALID với nguồn riêng")
    void sameGroupIdDifferentValueAcrossPaths() {
        // hai phương án loại trừ nhau mang nhóm cùng id "g" nhưng khác chỉ tiêu dạng trả lời;
        // nguồn phân theo giá trị nhóm → nhớ nguồn theo id trần sẽ lẫn hai đường
        GroupQuota gSc = new GroupQuota("g", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE",
                null, null, null, null, List.of(), List.of(), null, null);
        GroupQuota gSa = new GroupQuota("g", GroupQuota.GroupKind.SINGLE, 1, "SHORT_ANSWER",
                null, null, null, null, List.of(), List.of(), null, null);
        SolvePart part = SolvePart.Choice.of("p1", List.of(
                new SolveOption.Sections("opt-a", List.of(new SectionOnPath("MATH", "sa", 1,
                        SubjectQuota.NONE, List.of(gSc)))),
                new SolveOption.Sections("opt-b", List.of(new SectionOnPath("MATH", "sb", 1,
                        SubjectQuota.NONE, List.of(gSa))))));
        PathCandidate sc1 = PathCandidate.standalone("sc1", 1, "SINGLE_CHOICE", "G10", "NB",
                BigDecimal.ONE);
        PathCandidate sa1 = PathCandidate.standalone("sa1", 1, "SHORT_ANSWER", "G10", "NB",
                BigDecimal.ONE);
        CandidateSource source = g -> "SINGLE_CHOICE".equals(g.answerType())
                ? List.of(sc1) : List.of(sa1);
        SolveOutcome outcome = solver.solveAll(List.of(part), source, SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.VALID);
        assertThat(outcome.results()).hasSize(2);
        for (PathResult r : outcome.results()) {
            assertThat(r.status()).isEqualTo(SolveStatus.VALID);
            GroupWitness gw = r.witness().get(0).groups().get(0);
            String optionId = r.path().chosen().get(0).optionId();
            if (optionId.equals("opt-a")) {
                assertThat(gw.picks()).containsExactly(new QuestionPick("sc1", 1));
            } else {
                assertThat(gw.picks()).containsExactly(new QuestionPick("sa1", 1));
            }
        }
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

    @Test
    @DisplayName("R2-6: solvePath trên đường SUBJECT_CHOICE chưa mở phải giải mọi cách chọn")
    void unopenedSubjectChoiceSolves() {
        // SUBJECT_CHOICE 3 môn chọn 2; CS2 chỉ đủ 1 câu → cách chọn có CS2 nhận 1 mới khả thi
        List<SectionOnPath> sections = new ArrayList<>();
        List<GroupQuota> groupOf = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            GroupQuota g = GroupQuota.remainder("g-cs" + s);
            groupOf.add(g);
            sections.add(new SectionOnPath("CS" + s, "cs" + s, null, SubjectQuota.NONE,
                    List.of(g)));
        }
        SubjectChoice choice = new SubjectChoice("opt-sc", sections, 2, List.of(2, 1));
        CandidateSource starving = g -> g == groupOf.get(2)
                ? List.of(single("cs2-q1"))
                : List.of(single(g.groupId() + "-q1"), single(g.groupId() + "-q2"),
                        single(g.groupId() + "-q3"));
        SolvePath unopened = new SolvePath(0,
                List.of(new ChosenOption("p1", "opt-sc")), List.of(), List.of(choice),
                List.of());
        PathResult r = solver.solvePath(unopened, starving, SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.failingSelections()).isNotEmpty();

        // danh sách cách chọn rỗng trong cũng là dạng "chưa mở"
        SolvePath emptyInner = new SolvePath(0,
                List.of(new ChosenOption("p1", "opt-sc")), List.of(), List.of(choice),
                List.of(List.of()));
        PathResult r2 = solver.solvePath(emptyInner, starving, SolveMode.check());
        assertThat(r2.status()).isEqualTo(SolveStatus.INVALID);

        // nguồn đủ mọi môn → đường chưa mở VALID và witness phủ mọi bài toán con
        CandidateSource full = g -> List.of(single(g.groupId() + "-q1"),
                single(g.groupId() + "-q2"), single(g.groupId() + "-q3"));
        PathResult ok = solver.solvePath(unopened, full, SolveMode.check());
        assertThat(ok.status()).isEqualTo(SolveStatus.VALID);
        assertThat(ok.witness()).isNotEmpty();

        // cách chọn đã vật liệu hóa một phần: giải đúng cách chọn đó
        List<SectionOnPath> picked = List.of(sections.get(0), sections.get(1));
        SubjectPick only = new SubjectPick("opt-sc", picked, List.of(2, 1));
        SolvePath partial = new SolvePath(0,
                List.of(new ChosenOption("p1", "opt-sc")), List.of(), List.of(choice),
                List.of(List.of(only)));
        PathResult r3 = solver.solvePath(partial, starving, SolveMode.check());
        assertThat(r3.status()).isEqualTo(SolveStatus.VALID);
    }

    @Test
    @DisplayName("R2-1: chỉ tiêu tỉ lệ hòa phần dư thập phân phân theo thứ tự khai báo (quy tắc web)")
    void percentTieDeclarationOrder() {
        // [3,13,23,61] × 10: phần lý thuyết 0.3;1.3;2.3;6.1 — ba phần dư 0.3 bằng nhau về toán
        // nhưng khác biểu diễn nhị phân; quy tắc web (làm tròn 1e-9) cho [1,1,2,6]
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Combined(List.of(
                new CellQuota("G10", "NB", 3), new CellQuota("G10", "TH", 13),
                new CellQuota("G11", "NB", 23), new CellQuota("G11", "TH", 61)),
                QuotaUnit.PERCENT));
        GroupQuota rest = GroupQuota.remainder("g-rest");
        SectionOnPath section = new SectionOnPath("MATH", "sec", 10, quota, List.of(rest));
        List<PathCandidate> pool = new ArrayList<>();
        pool.add(unit("a1", "G10", "NB"));
        pool.add(unit("b1", "G10", "TH"));
        pool.add(unit("c1", "G11", "NB"));
        pool.add(unit("c2", "G11", "NB"));
        for (int i = 0; i < 6; i++) {
            pool.add(unit("d" + i, "G11", "TH"));
        }
        CandidateSource source = g -> pool;
        // oracle độc lập phải kết luận ca này khả thi theo phân bổ của web
        assertThat(new BruteForceOracle().solveSubpath(List.of(section), source)).isTrue();
        PathResult result = solver.solvePath(path(List.of(section)), source, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().get(0).groups().get(0).picks()).hasSize(10);
    }

    @Test
    @DisplayName("R2-17: quy tắc chấm và điểm yêu cầu của nhóm lọc nguồn")
    void scoringRuleAndPointsFilters() {
        GroupQuota g = new GroupQuota("g1", GroupQuota.GroupKind.SINGLE, 2, null, null, null,
                null, null, List.of(), List.of(), BigDecimal.valueOf(2), "PER_ITEM");
        SectionOnPath section = section("S", 2, List.of(g));
        PathCandidate ok1 = scored("ok1", "PER_ITEM", BigDecimal.valueOf(2));
        PathCandidate ok2 = scored("ok2", "PER_ITEM", BigDecimal.valueOf(2));
        PathCandidate wrongRule = scored("wr", "ALL_OR_NOTHING", BigDecimal.valueOf(2));
        PathCandidate wrongPoints = scored("wp", "PER_ITEM", BigDecimal.valueOf(3));
        PathCandidate noPoints = scored("np", "PER_ITEM", null);
        PathResult result = solver.solvePath(path(List.of(section)),
                x -> List.of(ok1, ok2, wrongRule, wrongPoints, noPoints), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        for (GroupWitness gw : result.witness().get(0).groups()) {
            assertThat(gw.picks()).containsExactlyInAnyOrder(
                    new QuestionPick("ok1", 1), new QuestionPick("ok2", 1));
        }
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

    private static PathCandidate scored(String id, String rule, BigDecimal points) {
        return new PathCandidate(id, 1, null, rule,
                List.of(new CountingUnit(null, "SINGLE_CHOICE", "G10", "NB", List.of(),
                        List.of(), points)));
    }

    /** Chùm 2 câu con cùng khối lớp và độ khó, dạng SINGLE_CHOICE. */
    private static PathCandidate setPair(String id, String grade, String difficulty) {
        List<CountingUnit> units = List.of(
                new CountingUnit(id + "-c1", "SINGLE_CHOICE", grade, difficulty, List.of(),
                        List.of(), BigDecimal.ONE),
                new CountingUnit(id + "-c2", "SINGLE_CHOICE", grade, difficulty, List.of(),
                        List.of(), BigDecimal.ONE));
        return new PathCandidate(id, 1, id, null, units);
    }

    /** Đồng hồ chỉ hết hạn khi kiểm tra trong pha được chọn, không phụ thuộc thời gian nguồn. */
    private static final class PhaseClock extends Clock {
        private final String className;
        private final String methodName;
        private int expiredReads;

        PhaseClock(String className, String methodName) {
            this.className = PaperSolver.class.getPackageName() + "." + className;
            this.methodName = methodName;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            boolean inside = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(className)
                            && frame.getMethodName().equals(methodName)));
            if (inside) {
                expiredReads++;
                return Instant.EPOCH.plusSeconds(60);
            }
            return Instant.EPOCH;
        }
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
        return new PathCandidate(setId, 1, setId, null, units);
    }

    private static PathCandidate cloze(String id, int blanks) {
        List<CountingUnit> units = new ArrayList<>(blanks);
        for (int i = 0; i < blanks; i++) {
            units.add(new CountingUnit(null, "CLOZE", "G10", "NB", List.of(), List.of(),
                    BigDecimal.ONE));
        }
        return new PathCandidate(id, 1, id, null, units);
    }
}
