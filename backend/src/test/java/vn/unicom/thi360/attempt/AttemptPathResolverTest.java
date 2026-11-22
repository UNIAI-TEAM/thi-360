package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ChoiceOptionView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.PreparePartView;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.application.AttemptPathResolver;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.PrepareStructure;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.ResolvedStartPlan;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CombinationSubject;
import vn.unicom.thi360.exam.spi.PublishedExamPort.OmittedGroup;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PartPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;

/**
 * Unit tests for {@link AttemptPathResolver} covering whitespace scope fail-closed (R1-22),
 * multi-version prepare representative (R1-23/24), partial maxPoints & SHARED duration fallback
 * (R1-25/26/27), SUBJECT_CHOICE & unexpected preChoice validation (R1-37), and null part order
 * sorting (R1-47).
 */
class AttemptPathResolverTest {

    private PublishedExamPort publishedExamPort;
    private CatalogLookup catalogLookup;
    private AttemptPathResolver resolver;

    @BeforeEach
    void setUp() {
        publishedExamPort = mock(PublishedExamPort.class);
        catalogLookup = mock(CatalogLookup.class);
        when(catalogLookup.listGroupItems("SUBJECT")).thenReturn(List.of());
        resolver = new AttemptPathResolver(publishedExamPort, catalogLookup);
    }

    @Test
    void whitespaceOnlyGrantedOptionCodesFailsClosed() {
        UUID examId = UUID.randomUUID();
        ServingExam serving = branchServingExam(examId);

        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of("   ", "\t"));
        assertThat(prepare.preChoice()).isNotNull();
        assertThat(prepare.preChoice().options()).isEmpty();

        assertThatThrownBy(() -> resolver.resolveStartPlan(serving, List.of("   "), new PreChoiceInput("CS", null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "NO_SERVING_OPTION");
    }

    @Test
    void buildPrepareStructureUsesRepresentativeVersionForAllowedOlderPaths() {
        UUID examId = UUID.randomUUID();
        Part p1v1 = new Part("p1", 1, "Part 1 V1", 40, 20, 20, 20, "ALL", null, new BigDecimal("5"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("o-cs", 20, new BigDecimal("5"), 40)), List.of());
        Option csOpt = new Option("o-cs", "BRANCH", "CS", "Khoa học máy tính", null, null, List.of());
        Part p2v1 = new Part("p2", 2, "Part 2 V1", 20, 2, 3, 2, "CHOICE", 1, new BigDecimal("2"),
                List.of(), List.of(), List.of(), List.of(csOpt),
                List.of(new PartPath("o-cs", 2, new BigDecimal("2"), 20)), List.of());
        PublishedVersion v1 = version(examId, 1, "Exam V1", "PER_PART", null, new BigDecimal("7"),
                "<p>V1 instructions</p>", List.of(new OmittedGroup("Essay V1", BigDecimal.ONE)),
                List.of(p1v1, p2v1), List.of("o-cs"));

        Option ictOpt = new Option("o-ict", "BRANCH", "ICT", "Tin học ứng dụng", null, null, List.of());
        Part p1v2 = new Part("p1", 1, "Part 1 V2", 50, 24, 24, 24, "ALL", null, new BigDecimal("6"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("o-ict", 24, new BigDecimal("6"), 50)), List.of());
        Part p2v2 = new Part("p2", 2, "Part 2 V2", 25, 4, 4, 4, "CHOICE", 1, new BigDecimal("4"),
                List.of(), List.of(), List.of(), List.of(ictOpt),
                List.of(new PartPath("o-ict", 4, new BigDecimal("4"), 25)), List.of());
        PublishedVersion v2 = version(examId, 2, "Exam V2", "PER_PART", null, new BigDecimal("10"),
                "<p>V2 instructions</p>", List.of(new OmittedGroup("Essay V2", BigDecimal.TEN)),
                List.of(p1v2, p2v2), List.of("o-ict"));

        ServingPath csPath = new ServingPath("o-cs", 1,
                List.of(new PathChoice("p2", "o-cs", "BRANCH", "CS", "Khoa học máy tính")),
                22, new BigDecimal("7"), 60,
                List.of(new ServingPathPart("p1", "Part 1 V1", 20, new BigDecimal("5"), 40),
                        new ServingPathPart("p2", "Part 2 V1", 2, new BigDecimal("2"), 20)),
                List.of());
        ServingPath ictPath = new ServingPath("o-ict", 2,
                List.of(new PathChoice("p2", "o-ict", "BRANCH", "ICT", "Tin học ứng dụng")),
                28, new BigDecimal("10"), 75,
                List.of(new ServingPathPart("p1", "Part 1 V2", 24, new BigDecimal("6"), 50),
                        new ServingPathPart("p2", "Part 2 V2", 4, new BigDecimal("4"), 25)),
                List.of());
        ServingExam serving = new ServingExam(examId, "PUBLISHED", v2, List.of(ictPath, csPath));
        when(publishedExamPort.loadVersion(examId, 1)).thenReturn(Optional.of(v1));

        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of("CS"));

        assertThat(prepare.representativeVersion().versionNo()).isEqualTo(1);
        assertThat(prepare.representativeVersion().name()).isEqualTo("Exam V1");
        assertThat(prepare.representativeVersion().instructions()).isEqualTo("<p>V1 instructions</p>");
        assertThat(prepare.representativeVersion().omittedGroups())
                .containsExactly(new OmittedGroup("Essay V1", BigDecimal.ONE));
        assertThat(prepare.parts()).extracting(PreparePartView::name).containsExactly("Part 1 V1", "Part 2 V1");
        assertThat(prepare.preChoice().options()).hasSize(1);
        ChoiceOptionView csOption = prepare.preChoice().options().getFirst();
        assertThat(csOption.code()).isEqualTo("CS");
        assertThat(csOption.questionCount()).isEqualTo(22);
        assertThat(csOption.durationMinutes()).isEqualTo(60);
        assertThat(csOption.maxPoints()).isEqualByComparingTo("7");
    }

    @Test
    void partialMaxPointsFallsBackAndSharedDurationSumsParts() {
        UUID examId = UUID.randomUUID();
        Part p1 = new Part("p1", 1, "Part 1", 30, 10, 10, 10, "ALL", null, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 10, new BigDecimal("50"), 30)), List.of());
        Part p2 = new Part("p2", 2, "Part 2", 45, 10, 10, 10, "ALL", null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 10, null, 45)), List.of());
        PublishedVersion v = version(examId, 1, "Partial Max SHARED", "SHARED", null, new BigDecimal("100"),
                null, List.of(), List.of(p1, p2), List.of("default"));

        // 1. Both path maxPoints & durationMinutes null -> totalMaxScore falls back to version.maxScore (100), duration falls back to sum (75)
        ServingPath pathWithoutTotals = new ServingPath("default", 1, List.of(), 20, null, null,
                List.of(new ServingPathPart("p1", "Part 1", 10, new BigDecimal("50"), 30),
                        new ServingPathPart("p2", "Part 2", 10, null, 45)),
                List.of());
        ServingExam serving1 = new ServingExam(examId, "PUBLISHED", v, List.of(pathWithoutTotals));

        ResolvedStartPlan plan1 = resolver.resolveStartPlan(serving1, List.of(), null);
        assertThat(plan1.totalMaxScore()).isEqualByComparingTo("100");
        assertThat(plan1.examDurationMinutes()).isEqualTo(75);
        assertThat(resolver.buildPrepareStructure(serving1, List.of()).durationMinutes()).isEqualTo(75);

        // 2. Path has maxPoints = 80 -> totalMaxScore falls back to chosenPath.maxPoints (80), not partial sum 50
        ServingPath pathWithMax = new ServingPath("default", 1, List.of(), 20, new BigDecimal("80"), null,
                pathWithoutTotals.parts(), List.of());
        ServingExam serving2 = new ServingExam(examId, "PUBLISHED", v, List.of(pathWithMax));
        ResolvedStartPlan plan2 = resolver.resolveStartPlan(serving2, List.of(), null);
        assertThat(plan2.totalMaxScore()).isEqualByComparingTo("80");
    }

    @Test
    void partsWithNullOrderSortLast() {
        UUID examId = UUID.randomUUID();
        Part pNull = new Part("p-null", null, "Null Order", 10, 5, 5, 5, "ALL", null, BigDecimal.TEN,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 5, BigDecimal.TEN, 10)), List.of());
        Part p2 = new Part("p2", 2, "Second", 10, 5, 5, 5, "ALL", null, BigDecimal.TEN,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 5, BigDecimal.TEN, 10)), List.of());
        Part p1 = new Part("p1", 1, "First", 10, 5, 5, 5, "ALL", null, BigDecimal.TEN,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 5, BigDecimal.TEN, 10)), List.of());
        PublishedVersion v = version(examId, 1, "Null Order Exam", "PER_PART", null, new BigDecimal("30"),
                null, List.of(), List.of(pNull, p2, p1), List.of("default"));
        ServingPath path = new ServingPath("default", 1, List.of(), 15, new BigDecimal("30"), 30,
                List.of(new ServingPathPart("p1", "First", 5, BigDecimal.TEN, 10),
                        new ServingPathPart("p2", "Second", 5, BigDecimal.TEN, 10),
                        new ServingPathPart("p-null", "Null Order", 5, BigDecimal.TEN, 10)),
                List.of());
        ServingExam serving = new ServingExam(examId, "PUBLISHED", v, List.of(path));

        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of());
        assertThat(prepare.parts()).extracting(PreparePartView::partCode).containsExactly("p1", "p2", "p-null");
    }

    @Test
    void subjectChoiceValidationAndUnexpectedPreChoiceRejected() {
        UUID examId = UUID.randomUUID();
        Option scienceOpt = new Option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học", 3, List.of(17, 17, 16),
                List.of(
                        new Section("s-ly", "LY", 17, "ANY"),
                        new Section("s-hoa", "HOA", 17, "ANY"),
                        new Section("s-sinh", "SINH", 17, "ANY"),
                        new Section("s-su", "SU", 17, "ANY"),
                        new Section("s-dia", "DIA", 17, "ANY")));
        Part pChoice = new Part("p3", 1, "Khoa học", 60, 50, 50, 50, "CHOICE", 1, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(scienceOpt),
                List.of(new PartPath("o-sci", 50, new BigDecimal("50"), 60)), List.of());
        PublishedVersion choiceVersion = version(examId, 1, "HSA Science Part", "PER_PART", null,
                new BigDecimal("50"), null, List.of(), List.of(pChoice), List.of("o-sci"));
        ServingPath sciPath = new ServingPath("o-sci", 1,
                List.of(new PathChoice("p3", "o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học")),
                50, new BigDecimal("50"), 60,
                List.of(new ServingPathPart("p3", "Khoa học", 50, new BigDecimal("50"), 60)),
                List.of());
        ServingExam sciServing = new ServingExam(examId, "PUBLISHED", choiceVersion, List.of(sciPath));

        // Valid 3 subjects -> canonicalized
        ResolvedStartPlan valid = resolver.resolveStartPlan(
                sciServing, List.of(), new PreChoiceInput("science", List.of("ly", "hoa", "sinh")));
        assertThat(valid.selections()).containsExactly(
                PaperSolverPort.Selection.subjects("p3", "SCIENCE", List.of("LY", "HOA", "SINH")));
        assertThat(valid.choices()).containsKey("p3");

        // Wrong count (2), duplicate (ly/LY), unknown (ART) -> 422 SUBJECTS_INVALID
        for (List<String> invalidSubjects : List.of(
                List.of("LY", "HOA"),
                List.of("LY", "ly", "HOA"),
                List.of("LY", "HOA", "ART"))) {
            assertThatThrownBy(() -> resolver.resolveStartPlan(
                    sciServing, List.of(), new PreChoiceInput("SCIENCE", invalidSubjects)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "SUBJECTS_INVALID");
        }

        // Unexpected preChoice on fixed exam -> 422 CHOICE_NOT_ALLOWED
        Part fixedPart = new Part("p1", 1, "Toán", 75, 50, 50, 50, "ALL", null, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("default", 50, new BigDecimal("50"), 75)), List.of());
        PublishedVersion fixedVersion = version(examId, 1, "Fixed Exam", "PER_PART", null,
                new BigDecimal("50"), null, List.of(), List.of(fixedPart), List.of("default"));
        ServingExam fixedServing = new ServingExam(examId, "PUBLISHED", fixedVersion,
                List.of(new ServingPath("default", 1, List.of(), 50, new BigDecimal("50"), 75,
                        List.of(new ServingPathPart("p1", "Toán", 50, new BigDecimal("50"), 75)), List.of())));

        assertThatThrownBy(() -> resolver.resolveStartPlan(
                fixedServing, List.of(), new PreChoiceInput("CS", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CHOICE_NOT_ALLOWED");
        assertThatThrownBy(() -> resolver.resolveStartPlan(
                fixedServing, List.of(), new PreChoiceInput(null, List.of("TOAN"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CHOICE_NOT_ALLOWED");
    }

    @Test
    void choiceOptionsExposeDistinctPerPathMetricsAcrossOptionsCombinationsAndAfterStart() {
        UUID examId = UUID.randomUUID();

        // 1. PRE_START Option-based (CS vs ICT with different questionCount, durationMinutes, maxPoints)
        Option csOpt = new Option("o-cs", "ORIENTATION", "CS", "Khoa học máy tính", null, null, List.of());
        Option ictOpt = new Option("o-ict", "ORIENTATION", "ICT", "Tin học ứng dụng", null, null, List.of());
        Part p1Opt = new Part("p1", 1, "Tin học", 50, 28, 28, 28, "CHOICE", 1, new BigDecimal("10"),
                List.of(), List.of(), List.of(), List.of(csOpt, ictOpt),
                List.of(
                        new PartPath("o-cs", 28, new BigDecimal("10"), 50),
                        new PartPath("o-ict", 30, new BigDecimal("12"), 55)),
                List.of());
        PublishedVersion optVersion = version(examId, 1, "Informatics", "PER_PART", null,
                new BigDecimal("12"), null, List.of(), List.of(p1Opt), List.of("o-cs", "o-ict"));
        ServingExam optServing = new ServingExam(examId, "PUBLISHED", optVersion, List.of(
                new ServingPath("o-cs", 1, List.of(new PathChoice("p1", "o-cs", "ORIENTATION", "CS", "Khoa học máy tính")),
                        28, new BigDecimal("10"), 50,
                        List.of(new ServingPathPart("p1", "Tin học", 28, new BigDecimal("10"), 50)), List.of()),
                new ServingPath("o-ict", 1, List.of(new PathChoice("p1", "o-ict", "ORIENTATION", "ICT", "Tin học ứng dụng")),
                        30, new BigDecimal("12"), 55,
                        List.of(new ServingPathPart("p1", "Tin học", 30, new BigDecimal("12"), 55)), List.of())));

        PrepareStructure optPrepare = resolver.buildPrepareStructure(optServing, List.of());
        assertThat(optPrepare.preChoice()).isNotNull();
        assertThat(optPrepare.preChoice().options()).hasSize(2);
        ChoiceOptionView csView = optPrepare.preChoice().options().get(0);
        ChoiceOptionView ictView = optPrepare.preChoice().options().get(1);
        assertThat(csView.code()).isEqualTo("CS");
        assertThat(csView.questionCount()).isEqualTo(28);
        assertThat(csView.durationMinutes()).isEqualTo(50);
        assertThat(csView.maxPoints()).isEqualByComparingTo("10");
        assertThat(ictView.code()).isEqualTo("ICT");
        assertThat(ictView.questionCount()).isEqualTo(30);
        assertThat(ictView.durationMinutes()).isEqualTo(55);
        assertThat(ictView.maxPoints()).isEqualByComparingTo("12");

        // 2. PRE_START Combination-based (BCA CA1 vs CA2 with fallback from parts/version when path totals are null)
        Part bcaP1 = new Part("p1", 1, "Phần 1", null, 30, 30, 30, "ALL", null, new BigDecimal("40"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("p2:CA1", 30, new BigDecimal("40"), null),
                        new PartPath("p2:CA2", 30, new BigDecimal("40"), null)),
                List.of());
        Part bcaP2 = new Part("p2", 2, "Tổ hợp", null, null, null, null, "CHOICE", 1, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PartPath("p2:CA1", 20, new BigDecimal("60"), null),
                        new PartPath("p2:CA2", 25, new BigDecimal("70"), null)),
                List.of());
        Combination ca1 = new Combination("p2", "CA1", "Tổ hợp CA1", null, 1,
                List.of(new CombinationSubject("TOAN", "Toán", 0), new CombinationSubject("LY", "Vật lí", 1)));
        Combination ca2 = new Combination("p2", "CA2", "Tổ hợp CA2", null, 1,
                List.of(new CombinationSubject("TOAN", "Toán", 0), new CombinationSubject("HOA", "Hóa học", 1)));
        PublishedVersion bcaVersion = new PublishedVersion(
                examId, 1, "SERVING", "PUBLISHED", Instant.parse("2026-11-20T00:00:00Z"),
                "BCA-CODE", "BCA Exam", UUID.randomUUID(), "BCA", "FULL", null, null,
                "STANDARD", "SHARED", 180, new BigDecimal("100"), null, "RAW", null,
                List.of(bcaP1, bcaP2), List.of(ca1, ca2), List.of(),
                List.of("p2:CA1", "p2:CA2"), List.of("p2:CA1", "p2:CA2"), null);
        ServingPath ca1Path = new ServingPath("p2:CA1", 1,
                List.of(new PathChoice("p2", "p2:CA1", "COMBINATION", "CA1", "Tổ hợp CA1")),
                null, null, null,
                List.of(new ServingPathPart("p1", "Phần 1", 30, new BigDecimal("40"), null),
                        new ServingPathPart("p2", "Tổ hợp", 20, new BigDecimal("60"), null)),
                List.of(ca1));
        ServingPath ca2Path = new ServingPath("p2:CA2", 1,
                List.of(new PathChoice("p2", "p2:CA2", "COMBINATION", "CA2", "Tổ hợp CA2")),
                55, new BigDecimal("110"), 195,
                List.of(new ServingPathPart("p1", "Phần 1", 30, new BigDecimal("40"), null),
                        new ServingPathPart("p2", "Tổ hợp", 25, new BigDecimal("70"), null)),
                List.of(ca2));
        ServingExam bcaServing = new ServingExam(examId, "PUBLISHED", bcaVersion, List.of(ca1Path, ca2Path));

        PrepareStructure bcaPrepare = resolver.buildPrepareStructure(bcaServing, List.of());
        assertThat(bcaPrepare.preChoice()).isNotNull();
        assertThat(bcaPrepare.preChoice().options()).hasSize(2);
        ChoiceOptionView ca1View = bcaPrepare.preChoice().options().get(0);
        ChoiceOptionView ca2View = bcaPrepare.preChoice().options().get(1);
        assertThat(ca1View.code()).isEqualTo("CA1");
        assertThat(ca1View.questionCount()).isEqualTo(50);
        assertThat(ca1View.durationMinutes()).isEqualTo(180);
        assertThat(ca1View.maxPoints()).isEqualByComparingTo("100");
        assertThat(ca2View.code()).isEqualTo("CA2");
        assertThat(ca2View.questionCount()).isEqualTo(55);
        assertThat(ca2View.durationMinutes()).isEqualTo(195);
        assertThat(ca2View.maxPoints()).isEqualByComparingTo("110");

        // 3. AFTER_START HSA FULL options also populate path metrics
        Option sciOpt = new Option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học", 3, List.of(17, 17, 16), List.of());
        Option anhOpt = new Option("o-anh", "FIXED", "ANH", "Tiếng Anh", null, null, List.of());
        Part hsaP1 = new Part("p1", 1, "Toán", 75, 50, 50, 50, "ALL", null, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Part hsaP3 = new Part("p3", 2, "Tự chọn", 60, 50, 50, 50, "CHOICE", 1, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(sciOpt, anhOpt), List.of(), List.of());
        PublishedVersion hsaVersion = new PublishedVersion(
                examId, 1, "SERVING", "PUBLISHED", Instant.parse("2026-11-20T00:00:00Z"),
                "HSA-FULL", "HSA Full", UUID.randomUUID(), "HSA", "FULL", null, null,
                "STANDARD", "PER_PART", null, new BigDecimal("100"), null, "RAW", null,
                List.of(hsaP1, hsaP3), List.of(), List.of(),
                List.of("o-sci", "o-anh"), List.of("o-sci", "o-anh"), null);
        ServingExam hsaServing = new ServingExam(examId, "PUBLISHED", hsaVersion, List.of(
                new ServingPath("o-sci", 1, List.of(new PathChoice("p3", "o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học")),
                        100, new BigDecimal("100"), 135,
                        List.of(new ServingPathPart("p1", "Toán", 50, new BigDecimal("50"), 75),
                                new ServingPathPart("p3", "Khoa học", 50, new BigDecimal("50"), 60)),
                        List.of()),
                new ServingPath("o-anh", 1, List.of(new PathChoice("p3", "o-anh", "FIXED", "ANH", "Tiếng Anh")),
                        90, new BigDecimal("90"), 120,
                        List.of(new ServingPathPart("p1", "Toán", 50, new BigDecimal("50"), 75),
                                new ServingPathPart("p3", "Tiếng Anh", 40, new BigDecimal("40"), 45)),
                        List.of())));

        PrepareStructure hsaPrepare = resolver.buildPrepareStructure(hsaServing, List.of());
        assertThat(hsaPrepare.preChoice()).isNull();
        assertThat(hsaPrepare.afterStartChoices()).hasSize(1);
        List<ChoiceOptionView> hsaOptions = hsaPrepare.afterStartChoices().getFirst().options();
        assertThat(hsaOptions).hasSize(2);
        assertThat(hsaOptions.get(0).code()).isEqualTo("SCIENCE");
        assertThat(hsaOptions.get(0).questionCount()).isEqualTo(100);
        assertThat(hsaOptions.get(0).durationMinutes()).isEqualTo(135);
        assertThat(hsaOptions.get(0).maxPoints()).isEqualByComparingTo("100");
        assertThat(hsaOptions.get(1).code()).isEqualTo("ANH");
        assertThat(hsaOptions.get(1).questionCount()).isEqualTo(90);
        assertThat(hsaOptions.get(1).durationMinutes()).isEqualTo(120);
        assertThat(hsaOptions.get(1).maxPoints()).isEqualByComparingTo("90");
    }

    @Test
    void multiPathOptionMetricsRequireConsensusAndCompareBigDecimalByValue() {
        UUID examId = UUID.randomUUID();
        Option optA = new Option("o-a", "BRANCH", "OPT_A", "Option A", null, null, List.of());
        Option optB = new Option("o-b", "BRANCH", "OPT_B", "Option B", null, null, List.of());
        Part p1 = new Part("p1", 1, "Part 1", 90, 25, 25, 25, "CHOICE", 1, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(optA, optB), List.of(), List.of());
        Part p2 = new Part("p2", 2, "Part 2", 90, 25, 25, 25, "ALL", null, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        PublishedVersion v = version(examId, 1, "Multi-path Consensus", "PER_PART", null,
                new BigDecimal("100"), null, List.of(), List.of(p1, p2), List.of("a-1", "a-2", "b-1", "b-2"));

        // OPT_A matches two paths: agree on duration (180) and maxPoints (100 vs 100.0), disagree on questionCount (50 vs 60)
        ServingPath a1 = new ServingPath("a-1", 1, List.of(new PathChoice("p1", "o-a", "BRANCH", "OPT_A", "Option A")),
                50, new BigDecimal("100"), 180,
                List.of(new ServingPathPart("p1", "Part 1", 25, new BigDecimal("50"), 90),
                        new ServingPathPart("p2", "Part 2", 25, new BigDecimal("50"), 90)),
                List.of());
        ServingPath a2 = new ServingPath("a-2", 1, List.of(new PathChoice("p1", "o-a", "BRANCH", "OPT_A", "Option A")),
                60, new BigDecimal("100.0"), 180,
                List.of(new ServingPathPart("p1", "Part 1", 30, new BigDecimal("50.0"), 90),
                        new ServingPathPart("p2", "Part 2", 30, new BigDecimal("50.0"), 90)),
                List.of());

        // OPT_B matches two paths: agree on questionCount (40), disagree on duration (120 vs 150) and maxPoints (80 vs 90)
        ServingPath b1 = new ServingPath("b-1", 1, List.of(new PathChoice("p1", "o-b", "BRANCH", "OPT_B", "Option B")),
                40, new BigDecimal("80"), 120, List.of(), List.of());
        ServingPath b2 = new ServingPath("b-2", 1, List.of(new PathChoice("p1", "o-b", "BRANCH", "OPT_B", "Option B")),
                40, new BigDecimal("90"), 150, List.of(), List.of());

        ServingExam serving = new ServingExam(examId, "PUBLISHED", v, List.of(a1, a2, b1, b2));
        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of());

        ChoiceOptionView viewA = prepare.preChoice().options().get(0);
        assertThat(viewA.code()).isEqualTo("OPT_A");
        assertThat(viewA.questionCount()).isNull();
        assertThat(viewA.durationMinutes()).isEqualTo(180);
        assertThat(viewA.maxPoints()).isEqualByComparingTo("100");

        ChoiceOptionView viewB = prepare.preChoice().options().get(1);
        assertThat(viewB.code()).isEqualTo("OPT_B");
        assertThat(viewB.questionCount()).isEqualTo(40);
        assertThat(viewB.durationMinutes()).isNull();
        assertThat(viewB.maxPoints()).isNull();
    }

    @Test
    void optionMetricsWithNullOrIncompleteMetricFailClosedToNull() {
        UUID examId = UUID.randomUUID();
        Option partialMulti = new Option("o-pm", "BRANCH", "PARTIAL", "Partial", null, null, List.of());
        Option incompleteSingle = new Option("o-inc", "BRANCH", "INCOMPLETE", "Incomplete", null, null, List.of());
        Part p1 = new Part("p1", 1, "Part 1", null, null, null, null, "CHOICE", 1, null,
                List.of(), List.of(), List.of(), List.of(partialMulti, incompleteSingle), List.of(), List.of());
        Part p2 = new Part("p2", 2, "Part 2", null, null, null, null, "ALL", null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        PublishedVersion v = version(examId, 1, "Null Metric Fail-Closed", "PER_PART", null,
                new BigDecimal("100"), null, List.of(), List.of(p1, p2), List.of("pm-1", "pm-2", "inc-1"));

        // PARTIAL matches 2 paths: pm-1 has (50, 90, 100); pm-2 has null questionCount and null maxPoints -> both fail closed to null even with version.maxScore=100, duration stays 90
        ServingPath pm1 = new ServingPath("pm-1", 1, List.of(new PathChoice("p1", "o-pm", "BRANCH", "PARTIAL", "Partial")),
                50, new BigDecimal("100"), 90, List.of(), List.of());
        ServingPath pm2 = new ServingPath("pm-2", 1, List.of(new PathChoice("p1", "o-pm", "BRANCH", "PARTIAL", "Partial")),
                null, null, 90,
                List.of(new ServingPathPart("p1", "Part 1", 25, new BigDecimal("50"), 45),
                        new ServingPathPart("p2", "Part 2", null, null, 45)),
                List.of());

        // INCOMPLETE single path has null totals and one part missing questionCount & durationMinutes -> never partial sum
        ServingPath inc1 = new ServingPath("inc-1", 1, List.of(new PathChoice("p1", "o-inc", "BRANCH", "INCOMPLETE", "Incomplete")),
                null, null, null,
                List.of(new ServingPathPart("p1", "Part 1", 20, new BigDecimal("50"), 30),
                        new ServingPathPart("p2", "Part 2", null, new BigDecimal("50"), null)),
                List.of());

        ServingExam serving = new ServingExam(examId, "PUBLISHED", v, List.of(pm1, pm2, inc1));
        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of());

        ChoiceOptionView partialView = prepare.preChoice().options().get(0);
        assertThat(partialView.code()).isEqualTo("PARTIAL");
        assertThat(partialView.questionCount()).isNull();
        assertThat(partialView.durationMinutes()).isEqualTo(90);
        assertThat(partialView.maxPoints()).isNull();

        ChoiceOptionView incompleteView = prepare.preChoice().options().get(1);
        assertThat(incompleteView.code()).isEqualTo("INCOMPLETE");
        assertThat(incompleteView.questionCount()).isNull();
        assertThat(incompleteView.durationMinutes()).isNull();
        assertThat(incompleteView.maxPoints()).isEqualByComparingTo("100");

        // BE-R1-5: Reverse path order (leading null path pm2 before non-null pm1) also fails closed without NPE
        ServingExam reversedServing = new ServingExam(examId, "PUBLISHED", v, List.of(pm2, pm1));
        PrepareStructure reversedPrepare = resolver.buildPrepareStructure(reversedServing, List.of());
        ChoiceOptionView reversedView = reversedPrepare.preChoice().options().getFirst();
        assertThat(reversedView.code()).isEqualTo("PARTIAL");
        assertThat(reversedView.questionCount()).isNull();
        assertThat(reversedView.durationMinutes()).isEqualTo(90);
        assertThat(reversedView.maxPoints()).isNull();
    }

    @Test
    void incompletePathOmittingVersionPartAndOmittedGroupsFailClosedToNull() {
        UUID examId = UUID.randomUUID();
        Option missingPartOpt = new Option("o-miss", "BRANCH", "MISSING_PART", "Missing Part", null, null, List.of());
        Option emptyWithOmittedOpt = new Option("o-omit", "BRANCH", "OMIT_EMPTY", "Omit Empty", null, null, List.of());
        Part p1 = new Part("p1", 1, "Part 1", 30, 20, 20, 20, "CHOICE", 1, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(missingPartOpt, emptyWithOmittedOpt), List.of(), List.of());
        Part p2 = new Part("p2", 2, "Part 2", 45, 30, 30, 30, "ALL", null, new BigDecimal("50"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        // BE-R1-1: PublishedExamReader !complete path omits p2 from path.parts() -> must NOT return partial sum for p1 or version.maxScore
        PublishedVersion v = version(examId, 1, "Incomplete Part Sum", "PER_PART", null,
                new BigDecimal("100"), null, List.of(), List.of(p1, p2), List.of("miss-1"));
        ServingPath missingP2 = new ServingPath("miss-1", 1,
                List.of(new PathChoice("p1", "o-miss", "BRANCH", "MISSING_PART", "Missing Part")),
                null, null, null,
                List.of(new ServingPathPart("p1", "Part 1", 20, new BigDecimal("50"), 30)),
                List.of());
        PrepareStructure missPrepare = resolver.buildPrepareStructure(
                new ServingExam(examId, "PUBLISHED", v, List.of(missingP2)), List.of());
        ChoiceOptionView missView = missPrepare.preChoice().options().getFirst();
        assertThat(missView.questionCount()).isNull();
        assertThat(missView.durationMinutes()).isNull();
        assertThat(missView.maxPoints()).isNull();

        // BE-R1-2: Empty path.parts() with omittedGroups non-empty does NOT fall back to version.maxScore (100)
        PublishedVersion omittedVersion = version(examId, 1, "Omitted Groups Exam", "SHARED", 90,
                new BigDecimal("100"), null, List.of(new OmittedGroup("Essay", new BigDecimal("20"))),
                List.of(p1, p2), List.of("omit-1"));
        ServingPath emptyWithOmitted = new ServingPath("omit-1", 1,
                List.of(new PathChoice("p1", "o-omit", "BRANCH", "OMIT_EMPTY", "Omit Empty")),
                50, null, null, List.of(), List.of());
        PrepareStructure omitPrepare = resolver.buildPrepareStructure(
                new ServingExam(examId, "PUBLISHED", omittedVersion, List.of(emptyWithOmitted)), List.of());
        ChoiceOptionView omitView = omitPrepare.preChoice().options().getFirst();
        assertThat(omitView.questionCount()).isEqualTo(50);
        assertThat(omitView.durationMinutes()).isEqualTo(90);
        assertThat(omitView.maxPoints()).isNull();
    }

    @Test
    void sameOptionCodeAcrossVersionsUsesPerPathVersionFallbackAndConsensus() {
        UUID examId = UUID.randomUUID();
        Option sharedOptV1 = new Option("o-shared", "BRANCH", "SHARED_OPT", "Shared Option", null, null, List.of());
        Option diffOptV1 = new Option("o-diff", "BRANCH", "DIFF_OPT", "Diff Option", null, null, List.of());
        Part p1v1 = new Part("p1", 1, "Part 1 V1", null, 25, 25, 25, "CHOICE", 1, null,
                List.of(), List.of(), List.of(), List.of(sharedOptV1, diffOptV1), List.of(), List.of());
        PublishedVersion v1 = version(examId, 1, "Multi-Version V1", "SHARED", 60,
                new BigDecimal("10"), null, List.of(), List.of(p1v1), List.of("s-v1", "d-v1"));

        Option sharedOptV2 = new Option("o-shared", "BRANCH", "SHARED_OPT", "Shared Option", null, null, List.of());
        Option diffOptV2 = new Option("o-diff", "BRANCH", "DIFF_OPT", "Diff Option", null, null, List.of());
        Part p1v2 = new Part("p1", 1, "Part 1 V2", null, 25, 25, 25, "CHOICE", 1, null,
                List.of(), List.of(), List.of(), List.of(sharedOptV2, diffOptV2), List.of(), List.of());
        PublishedVersion v2 = version(examId, 2, "Multi-Version V2", "SHARED", 90,
                new BigDecimal("12"), null, List.of(), List.of(p1v2), List.of("s-v2", "d-v2"));

        // SHARED_OPT agrees across v2 and v1 (questionCount=25, explicit duration=75, maxPoints=10.0 vs 10)
        ServingPath sharedV2 = new ServingPath("s-v2", 2,
                List.of(new PathChoice("p1", "o-shared", "BRANCH", "SHARED_OPT", "Shared Option")),
                25, new BigDecimal("10.0"), 75, List.of(), List.of());
        ServingPath sharedV1 = new ServingPath("s-v1", 1,
                List.of(new PathChoice("p1", "o-shared", "BRANCH", "SHARED_OPT", "Shared Option")),
                25, new BigDecimal("10"), 75, List.of(), List.of());

        // DIFF_OPT uses empty path.parts() + null path duration/maxPoints -> must fall back to v2 (90, 12) vs v1 (60, 10) and fail closed to null
        ServingPath diffV2 = new ServingPath("d-v2", 2,
                List.of(new PathChoice("p1", "o-diff", "BRANCH", "DIFF_OPT", "Diff Option")),
                25, null, null, List.of(), List.of());
        ServingPath diffV1 = new ServingPath("d-v1", 1,
                List.of(new PathChoice("p1", "o-diff", "BRANCH", "DIFF_OPT", "Diff Option")),
                25, null, null, List.of(), List.of());

        ServingExam serving = new ServingExam(examId, "PUBLISHED", v2, List.of(sharedV2, sharedV1, diffV2, diffV1));
        when(publishedExamPort.loadVersion(examId, 1)).thenReturn(Optional.of(v1));

        PrepareStructure prepare = resolver.buildPrepareStructure(serving, List.of());
        assertThat(prepare.preChoice().options()).hasSize(2);

        ChoiceOptionView sharedView = prepare.preChoice().options().get(0);
        assertThat(sharedView.code()).isEqualTo("SHARED_OPT");
        assertThat(sharedView.questionCount()).isEqualTo(25);
        assertThat(sharedView.durationMinutes()).isEqualTo(75);
        assertThat(sharedView.maxPoints()).isEqualByComparingTo("10");

        ChoiceOptionView diffView = prepare.preChoice().options().get(1);
        assertThat(diffView.code()).isEqualTo("DIFF_OPT");
        assertThat(diffView.questionCount()).isEqualTo(25);
        assertThat(diffView.durationMinutes()).isNull();
        assertThat(diffView.maxPoints()).isNull();
    }

    private ServingExam branchServingExam(UUID examId) {
        Option cs = new Option("o-cs", "BRANCH", "CS", "CS", null, null, List.of());
        Option ict = new Option("o-ict", "BRANCH", "ICT", "ICT", null, null, List.of());
        Part p1 = new Part("p1", 1, "Part 1", 45, 22, 22, 22, "CHOICE", 1, new BigDecimal("10"),
                List.of(), List.of(), List.of(), List.of(cs, ict),
                List.of(new PartPath("o-cs", 22, new BigDecimal("10"), 45),
                        new PartPath("o-ict", 22, new BigDecimal("10"), 45)),
                List.of());
        PublishedVersion v = version(examId, 1, "Informatics", "PER_PART", null, new BigDecimal("10"),
                null, List.of(), List.of(p1), List.of("o-cs", "o-ict"));
        return new ServingExam(examId, "PUBLISHED", v, List.of(
                new ServingPath("o-cs", 1, List.of(new PathChoice("p1", "o-cs", "BRANCH", "CS", "CS")),
                        22, BigDecimal.TEN, 45, List.of(new ServingPathPart("p1", "Part 1", 22, BigDecimal.TEN, 45)), List.of()),
                new ServingPath("o-ict", 1, List.of(new PathChoice("p1", "o-ict", "BRANCH", "ICT", "ICT")),
                        22, BigDecimal.TEN, 45, List.of(new ServingPathPart("p1", "Part 1", 22, BigDecimal.TEN, 45)), List.of())));
    }

    private static PublishedVersion version(
            UUID examId,
            int versionNo,
            String name,
            String timing,
            Integer durationMinutes,
            BigDecimal maxScore,
            String instructions,
            List<OmittedGroup> omittedGroups,
            List<Part> parts,
            List<String> pathIds) {
        return new PublishedVersion(
                examId,
                versionNo,
                "SERVING",
                "PUBLISHED",
                Instant.parse("2026-11-20T00:00:00Z"),
                "TEST-CODE",
                name,
                UUID.randomUUID(),
                "SPT",
                "SUBJECT",
                null,
                null,
                "STANDARD",
                timing,
                durationMinutes,
                maxScore,
                null,
                "RAW",
                null,
                parts,
                List.of(),
                omittedGroups,
                pathIds,
                pathIds,
                instructions);
    }
}
