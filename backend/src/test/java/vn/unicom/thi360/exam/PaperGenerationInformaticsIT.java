package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;

/** Informatics orientation (2B K2): a BRANCH option chosen like any other; the paper holds only that path. */
class PaperGenerationInformaticsIT extends PaperGenerationBaseIT {

    @Test
    void sptEachBranchGeneratesTwentyTwo() throws Exception {
        verifyBranches(publishTemplate("SPT", "SUBJECT", null, "INFORMATICS", null), 22);
    }

    @Test
    void thptqgEachBranchGeneratesTwentyEight() throws Exception {
        verifyBranches(publishTemplate("THPTQG", "SUBJECT", null, "INFORMATICS", null), 28);
    }

    private void verifyBranches(Published published, int expected) throws Exception {
        List<String> parts = partIds(published);
        String choicePart = parts.getLast();
        Set<String> groupsOfCs = null;
        for (String branch : List.of("CS", "ICT")) {
            GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), parts,
                    List.of(Selection.option(choicePart, branch)), 31L, Set.of());
            int total = paper.parts().stream().flatMap(part -> part.sections().stream())
                    .mapToInt(section -> section.questionCount()).sum();
            assertThat(total).as("questions of branch %s", branch).isEqualTo(expected);
            assertThat(roots(paper)).hasSize(expected).doesNotHaveDuplicates();
            Set<String> groups = paper.parts().stream().flatMap(part -> part.sections().stream())
                    .flatMap(section -> section.groups().stream()).map(GeneratedGroup::groupId)
                    .collect(Collectors.toSet());
            // The two branches are two different paths: the groups of the other branch never appear.
            if (groupsOfCs == null) {
                groupsOfCs = groups;
            } else {
                assertThat(groups).isNotEqualTo(groupsOfCs);
            }
        }
    }
}
