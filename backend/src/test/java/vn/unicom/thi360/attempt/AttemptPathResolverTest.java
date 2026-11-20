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
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.PreparePartView;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.application.AttemptPathResolver;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.PrepareStructure;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.ResolvedStartPlan;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
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
        assertThat(prepare.preChoice().options().getFirst().code()).isEqualTo("CS");
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
