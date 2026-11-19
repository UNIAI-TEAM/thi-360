package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.spi.PaperSolverPort.DroppedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;

/** HSA FULL on the 2B template: parts generated on request, and the 17 - 1 rule of the science choice (F4, 2B H3). */
class PaperGenerationHsaIT extends PaperGenerationBaseIT {
    private static final List<String> TRIPLE = List.of("LY", "HOA", "SINH");

    @Test
    void onlyRequestedPartsAreGenerated() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        List<String> parts = partIds(published);
        assertThat(parts).hasSize(3);

        GeneratedPaper paper = port.generate(published.examId(), published.versionNo(),
                List.of(parts.get(0), parts.get(1)), List.of(), 11L, Set.of());

        assertThat(paper.parts()).extracting(part -> part.partCode()).containsExactly(parts.get(0), parts.get(1));
        assertThat(paper.parts().get(0).sections()).extracting(GeneratedSection::subjectCode).containsExactly("TOAN");
        assertThat(paper.parts().get(1).sections()).extracting(GeneratedSection::subjectCode).containsExactly("VAN");
        assertThat(paper.parts()).allSatisfy(part -> assertThat(part.sections().getFirst().questionCount()).isEqualTo(50));
        assertThat(paper.dropped()).isEmpty();
        // Nothing of part 3 is in the paper, and its choice was not asked for.
        assertThat(paper.parts()).noneMatch(part -> part.partCode().equals(parts.get(2)));
    }

    @Test
    void literaturePassageBlockIsOneGroupOfItsAuthoredId() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        List<String> parts = partIds(published);

        GeneratedPaper paper = port.generate(published.examId(), published.versionNo(),
                List.of(parts.get(0), parts.get(1)), List.of(), 11L, Set.of());

        // The Literature passage block (five passages of five children) is one group, not five synthetic slot groups.
        assertGroupsAreAuthoredBlocks(published, paper);
        assertThat(slottedBlocks(published)).isNotEmpty();
    }

    @Test
    void scienceSubjectReceivesSixteenBySeed() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        String part = partIds(published).get(2);
        Selection science = Selection.subjects(part, "SCIENCE", TRIPLE);

        Set<String> subjectsThatGotSixteen = new HashSet<>();
        for (long seed = 1; seed <= 12; seed++) {
            GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), List.of(part),
                    List.of(science), seed, Set.of());
            Map<String, Integer> counts = countsBySubject(paper);
            assertThat(counts).containsOnlyKeys(TRIPLE.toArray(String[]::new));
            assertThat(counts.values()).containsExactlyInAnyOrder(17, 17, 16);
            assertThat(roots(paper)).doesNotHaveDuplicates();
            assertThat(roots(paper).stream().mapToInt(this::units).sum()).isEqualTo(50);
            counts.forEach((subject, count) -> {
                if (count == 16) subjectsThatGotSixteen.add(subject);
                assertThat(roots(section(paper, subject)).stream().mapToInt(this::units).sum()).isEqualTo(count);
            });
            // Same seed, same subject receives 16.
            GeneratedPaper again = port.generate(published.examId(), published.versionNo(), List.of(part),
                    List.of(science), seed, Set.of());
            assertThat(again).isEqualTo(paper);
        }
        // The seed moves the 16 between the chosen subjects.
        assertThat(subjectsThatGotSixteen.size()).isGreaterThan(1);
    }

    @Test
    void scienceReportsDroppedQuestion() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        String part = partIds(published).get(2);

        GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), List.of(part),
                List.of(Selection.subjects(part, "SCIENCE", TRIPLE)), 5L, Set.of());

        assertThat(paper.dropped()).hasSize(1);
        DroppedQuestion dropped = paper.dropped().getFirst();
        String sixteen = countsBySubject(paper).entrySet().stream().filter(entry -> entry.getValue() == 16)
                .findFirst().orElseThrow().getKey();
        assertThat(dropped.subjectCode()).isEqualTo(sixteen);
        assertThat(dropped.partCode()).isEqualTo(part);
        assertThat(roots(paper)).doesNotContain(dropped.question().questionId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE id = ? AND subject_item_id = "
                + "(SELECT id FROM catalog.catalog_item WHERE group_id = ? AND code = ?)", Long.class,
                dropped.question().questionId(), SUBJECT_GROUP, sixteen)).isEqualTo(1L);
    }

    @Test
    void excludedQuestionsAreNotPickedInScience() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        String part = partIds(published).get(2);
        Selection science = Selection.subjects(part, "SCIENCE", TRIPLE);
        GeneratedPaper first = port.generate(published.examId(), published.versionNo(), List.of(part),
                List.of(science), 4L, Set.of());
        Set<UUID> excluded = new LinkedHashSet<>(roots(first));
        // More source of the same shape, so the exclusion cannot starve the choice.
        cloneMany(excluded, 1);

        GeneratedPaper second = port.generate(published.examId(), published.versionNo(), List.of(part),
                List.of(science), 4L, excluded);

        assertThat(roots(second)).doesNotHaveDuplicates().doesNotContainAnyElementsOf(excluded);
        assertThat(roots(second).stream().mapToInt(this::units).sum()).isEqualTo(50);
        assertThat(countsBySubject(second).values()).containsExactlyInAnyOrder(17, 17, 16);
        assertThat(second.dropped().getFirst().question().questionId()).isNotIn(excluded);
    }

    private static Map<String, Integer> countsBySubject(GeneratedPaper paper) {
        Map<String, Integer> counts = new TreeMap<>();
        paper.parts().forEach(part -> part.sections().forEach(section ->
                counts.merge(section.subjectCode(), section.questionCount(), Integer::sum)));
        return counts;
    }
}
