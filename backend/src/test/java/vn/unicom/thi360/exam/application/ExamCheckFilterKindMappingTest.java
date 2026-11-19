package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.application.SolverInputBuilder.SolverCandidates.FilterDimension;
import vn.unicom.thi360.exam.application.ExamCheckRules.FilterKind;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

/** BE-16 row 4 (enum part): the two filter enums keep identical names, and AUDIO, POINTS map to their own codes. */
class ExamCheckFilterKindMappingTest {
    @Test
    void everySolverDimensionHasAFilterKindOfTheSameName() {
        assertThat(Arrays.stream(FilterDimension.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(FilterKind.values()).map(Enum::name).toList());
        assertThat(FilterDimension.values()).extracting(Enum::name).contains("AUDIO", "POINTS");
        for (FilterDimension dimension : FilterDimension.values()) {
            assertThat(FilterKind.valueOf(dimension.name()).name()).isEqualTo(dimension.name());
        }
    }

    @Test
    void audioAndPointsShortagesReportRequiredAndAvailableAtTheirLocation() {
        var rules = new ExamCheckRules(new ObjectMapper(), null, new ExamStructureDocument(List.of()));
        var location = new ExamCheckRules.EvidenceLocation("p", "s", "ANH", "b", null, null, null, List.of());
        var issues = rules.filterIssues(List.of(
                new ExamCheckRules.FilterEvidence(location, FilterKind.AUDIO, 3, 4, 2),
                new ExamCheckRules.FilterEvidence(location, FilterKind.POINTS, 6, 12, 0),
                new ExamCheckRules.FilterEvidence(location, FilterKind.AUDIO, 3, 2, 1),
                new ExamCheckRules.FilterEvidence(location, FilterKind.POINTS, 6, 12, 7)));

        assertThat(issues).extracting(issue -> issue.path("code").asText())
                .containsExactly("AUDIO_SHORTAGE", "POINTS_SHORTAGE");
        assertThat(issues.getFirst().path("required").asInt()).isEqualTo(3);
        assertThat(issues.getFirst().path("available").asInt()).isEqualTo(2);
        assertThat(issues.getFirst().path("blockId").asText()).isEqualTo("b");
        assertThat(issues.get(1).path("required").asInt()).isEqualTo(6);
        assertThat(issues.get(1).path("available").asInt()).isZero();
    }
}
