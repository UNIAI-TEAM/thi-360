package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;

/** R3-2: a witnessed error belongs to a unit by its proof, whatever characters the saved block ids contain. */
class DefaultExamCheckerWitnessKeyTests {
    @Test
    void proofsWhoseGroupIdsContainTheOldDelimitersStayDistinct() {
        // Joined as subject|group,question:version... both proofs once became "MATH|a,q1:1,q2:1".
        var first = new SubjectWitness("MATH", 2, List.of(
                new GroupWitness("a", List.of(new QuestionPick("q1", 1), new QuestionPick("q2", 1)))));
        var second = new SubjectWitness("MATH", 1, List.of(
                new GroupWitness("a,q1:1", List.of(new QuestionPick("q2", 1)))));
        var third = new SubjectWitness("MATH", 1, List.of(
                new GroupWitness("a", List.of(new QuestionPick("q1", 1))), new GroupWitness("q2:1", List.of())));
        assertThat(DefaultExamChecker.key(first)).isNotEqualTo(DefaultExamChecker.key(second));
        assertThat(DefaultExamChecker.key(first)).isNotEqualTo(DefaultExamChecker.key(third));
        assertThat(DefaultExamChecker.key(second)).isNotEqualTo(DefaultExamChecker.key(third));
    }

    @Test
    void theSameProofIsTheSameKeyEvenWhenResolvedCountOrTopicsDiffer() {
        var first = new SubjectWitness("MATH", 1, List.of(new GroupWitness("g|1", List.of(new QuestionPick("q1", 2)))));
        var second = new SubjectWitness("MATH", 5, List.of(new GroupWitness("g|1", List.of(new QuestionPick("q1", 2)))));
        assertThat(DefaultExamChecker.key(first)).isEqualTo(DefaultExamChecker.key(second));
    }
}
