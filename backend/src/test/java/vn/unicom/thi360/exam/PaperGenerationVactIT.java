package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;

/** V-ACT: a set group of flexible size takes whole clusters whose children add up to the requested count. */
class PaperGenerationVactIT extends PaperGenerationBaseIT {

    @Test
    void mathTakesWholeClusters() throws Exception {
        Published published = publishTemplate("VACT", "SUBJECT", null, "TOAN", null);
        String part = partIds(published).getFirst();

        GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), List.of(part), List.of(), 13L,
                Set.of());

        GeneratedSection math = paper.parts().getFirst().sections().getFirst();
        assertThat(math.subjectCode()).isEqualTo("TOAN");
        assertThat(math.questionCount()).isEqualTo(30);
        GeneratedGroup singles = math.groups().get(0);
        GeneratedGroup clusters = math.groups().get(1);
        assertThat(singles.questions()).hasSize(12).allSatisfy(question -> assertThat(units(question.questionId())).isEqualTo(1));
        // 18 children, every cluster whole: the roots' children add up to exactly 18.
        assertThat(clusters.questions()).isNotEmpty().allSatisfy(question ->
                assertThat(units(question.questionId())).isGreaterThan(1));
        assertThat(clusters.questions().stream().mapToInt(question -> units(question.questionId())).sum()).isEqualTo(18);
        assertThat(roots(paper)).doesNotHaveDuplicates();
    }

    @Test
    void flexibleClustersKeepTotalAndStayWhole() throws Exception {
        Subject subject = newSubject();
        // Clusters of 5, 4, 3 and 2 children: 12 can be made of 5+4+3, 5+3+2+2 ... and never needs a split.
        List<UUID> pool = new ArrayList<>();
        for (int size : new int[] {5, 5, 4, 4, 3, 3, 2, 2}) pool.addAll(sets(subject, 1, size));
        Published published = insertVersion(flexible(subject, 12), null);

        for (long seed = 1; seed <= 10; seed++) {
            GeneratedPaper paper = port.generate(published.examId(), 1, List.of("p1"), List.of(), seed, Set.of());
            GeneratedSection section = paper.parts().getFirst().sections().getFirst();
            assertThat(section.questionCount()).isEqualTo(12);
            List<UUID> roots = roots(section);
            assertThat(roots).doesNotHaveDuplicates().allSatisfy(root -> assertThat(pool).contains(root));
            assertThat(roots.stream().mapToInt(this::units).sum()).isEqualTo(12);
            // Same seed, same clusters.
            assertThat(port.generate(published.examId(), 1, List.of("p1"), List.of(), seed, Set.of())).isEqualTo(paper);
        }

        // Boundary: the pool holds exactly the clusters that make the count, so all of them are taken.
        Subject exact = newSubject();
        List<UUID> three = sets(exact, 3, 4);
        Published exactVersion = insertVersion(flexible(exact, 12), null);
        assertThat(rootSet(port.generate(exactVersion.examId(), 1, List.of("p1"), List.of(), 2L, Set.of())))
                .containsExactlyInAnyOrderElementsOf(three);

        // Clusters of 5 cannot make 12 without splitting one: refused, never a paper of 10 or 15.
        Subject odd = newSubject();
        sets(odd, 4, 5);
        Published oddVersion = insertVersion(flexible(odd, 12), null);
        assertThatThrownBy(() -> port.generate(oddVersion.examId(), 1, List.of("p1"), List.of(), 2L, Set.of()))
                .isInstanceOfSatisfying(PaperGenerationException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));
    }

    /** One part, one section, one question-set group with no cluster size: {@code children} counting units. */
    private static String flexible(Subject subject, int children) {
        return "{\"parts\":[{\"id\":\"p1\",\"order\":1,\"name\":\"P1\",\"questionCount\":" + children
                + ",\"questionCountOnPaper\":" + children + ",\"questionCountRequired\":" + children
                + ",\"participation\":{\"mode\":\"ALL\",\"choose\":null},\"sections\":[{\"id\":\"s1\",\"subjectCode\":\""
                + subject.code() + "\",\"questionCount\":" + children
                + ",\"blocks\":[{\"id\":\"b1\",\"questionType\":\"QUESTION_SET\",\"childAnswerType\":\"SINGLE_CHOICE\","
                + "\"questionCount\":" + children + "}]}],\"options\":[]}]}";
    }
}
