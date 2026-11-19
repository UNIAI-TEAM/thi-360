package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.exam.application.ExamPreviewStore;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;

/**
 * AC-23: HSA FULL, the Math and Literature parts, with 2,000 candidates in every block. The measured time goes to the
 * lane report; over 30 seconds is reported to the Advisor, the deadline is not changed here.
 */
@Tag("perf")
class PaperGenerationPerfIT extends PaperGenerationBaseIT {
    private static final int PER_BLOCK = 2_000;

    @Autowired ExamPreviewStore store;
    @Autowired SolverInputBuilder builder;

    @Test
    void hsaMathAndLiteratureWithTwoThousandCandidatesPerBlockGenerateWithinTheDeadline() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        List<String> parts = partIds(published);
        List<String> requested = List.of(parts.get(0), parts.get(1));

        ExamPreviewStore.Snapshot snapshot = store.loadVersion(published.examId(), published.versionNo());
        ExamStructureDocument all = new ExamStructureCodec(mapper).read(snapshot.parts());
        ExamStructureDocument document = new ExamStructureDocument(
                all.parts().stream().filter(part -> requested.contains(part.id())).toList(), all.examQuotas());

        // Bring every block of the two parts to 2,000 candidates with copies of what the seed already supplies.
        int blocks = 0;
        for (SolvePart part : builder.build(snapshot.exam().getKindItemId(), document).parts()) {
            for (GroupQuota group : groups(part)) {
                blocks++;
                List<UUID> sources = candidates(document, snapshot, group).stream()
                        .map(candidate -> UUID.fromString(candidate.questionId())).toList();
                assertThat(sources).as("seed source of block %s", group.groupId()).isNotEmpty();
                int copies = (PER_BLOCK - sources.size() + sources.size() - 1) / sources.size();
                if (copies > 0) cloneMany(sources, copies);
            }
        }
        for (SolvePart part : builder.build(snapshot.exam().getKindItemId(), document).parts()) {
            for (GroupQuota group : groups(part)) {
                assertThat(candidates(document, snapshot, group).size()).as("candidates of block %s", group.groupId())
                        .isGreaterThanOrEqualTo(PER_BLOCK);
            }
        }

        long worst = 0;
        GeneratedPaper paper = null;
        for (long seed = 1; seed <= 3; seed++) {
            long start = System.nanoTime();
            paper = port.generate(published.examId(), published.versionNo(), requested, List.of(), seed, Set.of());
            long ms = (System.nanoTime() - start) / 1_000_000;
            worst = Math.max(worst, ms);
            System.out.println("PERF PaperGeneration HSA FULL Toan+Van blocks=" + blocks + " candidates>=" + PER_BLOCK
                    + " seed=" + seed + " ms=" + ms);
        }
        System.out.println("PERF PaperGeneration HSA FULL Toan+Van worst ms=" + worst);
        assertThat(paper.parts()).hasSize(2);
        assertThat(paper.parts()).allSatisfy(part -> assertThat(part.sections().getFirst().questionCount()).isEqualTo(50));
        assertThat(worst).isLessThan(30_000);
    }

    private List<PathCandidate> candidates(ExamStructureDocument document, ExamPreviewStore.Snapshot snapshot,
            GroupQuota group) {
        // A fresh input per read: the source caches what it has already fetched. Groups compare by value, so a group
        // taken from one input is answered by another.
        return builder.build(snapshot.exam().getKindItemId(), document).candidates().candidatesFor(group);
    }

    private static List<GroupQuota> groups(SolvePart part) {
        List<SectionOnPath> sections = new ArrayList<>();
        if (part instanceof SolvePart.Fixed fixed) {
            sections.addAll(fixed.sections());
        } else if (part instanceof SolvePart.Choice choice) {
            sections.addAll(choice.commonSections());
            choice.options().forEach(option -> {
                if (option instanceof SolveOption.Sections normal) sections.addAll(normal.sections());
            });
        }
        List<GroupQuota> groups = new ArrayList<>();
        sections.forEach(section -> groups.addAll(section.groups()));
        return groups;
    }
}
