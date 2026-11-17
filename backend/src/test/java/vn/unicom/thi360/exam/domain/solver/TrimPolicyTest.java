package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrimPolicyTest {
    @Test
    void naturalSubjectsAreSolvedAtSeventeenThenTrimmedDeterministically() {
        List<SectionOnPath> sections = new ArrayList<>();
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        for (String subject : List.of("PHY", "CHEM", "BIO")) {
            GroupQuota singles = GroupQuota.single(subject + "-singles", 14);
            GroupQuota sets = GroupQuota.set(subject + "-sets", 1, 3, null);
            var answerQuotas = new SubjectQuota.AnswerQuotas(List.of(
                    new DimensionQuota("SINGLE_CHOICE", 16),
                    new DimensionQuota("SHORT_ANSWER", 1)), QuotaUnit.COUNT);
            sections.add(new SectionOnPath(subject, subject, null,
                    new SubjectQuota(answerQuotas, null), List.of(singles, sets), "NATURAL"));
            List<PathCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                candidates.add(PathCandidate.standalone(subject + "-q" + i, 1,
                        "SINGLE_CHOICE", null, null, null));
            }
            candidates.add(PathCandidate.standalone(subject + "-fill", 1,
                    "SHORT_ANSWER", null, null, null));
            pools.put(singles.groupId(), candidates);
            pools.put(sets.groupId(), List.of(new PathCandidate(subject + "-set", 1,
                    subject + "-set", null, List.of(
                    new CountingUnit(subject + "-child1", "SINGLE_CHOICE", null, null, List.of(), List.of(), null),
                    new CountingUnit(subject + "-child2", "SINGLE_CHOICE", null, null, List.of(), List.of(), null),
                    new CountingUnit(subject + "-child3", "SINGLE_CHOICE", null, null, List.of(), List.of(), null)))));
        }
        SubjectChoice choice = new SubjectChoice("science", sections, 3, List.of(17, 17, 16));
        PaperSolver solver = new PaperSolver();
        CandidateSource source = group -> pools.get(group.groupId());
        Set<String> trimmedSubjects = new java.util.HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            PathResult result = solver.solvePick(choice, List.of("PHY", "CHEM", "BIO"), seed,
                    source, Set.of("PHY-q0"));
            PathResult repeated = solver.solvePick(choice, List.of("PHY", "CHEM", "BIO"), seed,
                    source, Set.of("PHY-q0"));
            assertThat(result.status()).isEqualTo(SolveStatus.VALID);
            assertThat(result.droppedQuestion()).isEqualTo(repeated.droppedQuestion()).isNotNull();
            assertThat(result.droppedQuestion().questionId()).doesNotContain("-fill", "-set");
            assertThat(result.witness()).filteredOn(w -> w.resolvedCount() == 16).hasSize(1);
            trimmedSubjects.add(result.witness().stream().filter(w -> w.resolvedCount() == 16)
                    .findFirst().orElseThrow().subjectCode());
            for (SubjectWitness witness : result.witness()) {
                List<QuestionPick> picks = witness.groups().stream().flatMap(g -> g.picks().stream()).toList();
                assertThat(picks).extracting(QuestionPick::questionId)
                        .contains(witness.subjectCode() + "-set", witness.subjectCode() + "-fill")
                        .doesNotContain("PHY-q0", result.droppedQuestion().questionId());
                assertThat(picks).hasSize(witness.resolvedCount() - 2);
            }
        }
        assertThat(trimmedSubjects).containsExactlyInAnyOrder("PHY", "CHEM", "BIO");
    }
    @Test
    void excludingSetChildRejectsTheWholeSet() {
        GroupQuota sets = GroupQuota.set("sets", 1, 2, null);
        var section = new SectionOnPath("PHY", "phy", null, null, List.of(sets), "NATURAL");
        var other = new SectionOnPath("CHEM", "chem", null, null, List.of(sets), "NATURAL");
        var choice = new SubjectChoice("science", List.of(section, other), 1, List.of(2));
        var blocked = new PathCandidate("blocked", 1, "blocked", null, List.of(
                new CountingUnit("used-child", "SINGLE_CHOICE", null, null, List.of(), List.of(), null),
                new CountingUnit("other-child", "SINGLE_CHOICE", null, null, List.of(), List.of(), null)));
        var alternative = new PathCandidate("alternative", 1, "alternative", null, List.of(
                new CountingUnit("alt-1", "SINGLE_CHOICE", null, null, List.of(), List.of(), null),
                new CountingUnit("alt-2", "SINGLE_CHOICE", null, null, List.of(), List.of(), null)));
        var result = new PaperSolver().solvePick(choice, List.of("PHY"), 1,
                group -> List.of(blocked, alternative), Set.of("used-child"));
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().getFirst().groups().getFirst().picks())
                .extracting(QuestionPick::questionId).containsExactly("alternative");
    }
}
