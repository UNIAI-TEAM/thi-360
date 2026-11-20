package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;

/**
 * UNI-2758 on the EX-2 path (forward merge gate): a published version whose section carries MAPID rules is generated
 * through {@code PaperSolverPort} (the real {@code PaperGenerationService}, its own limits, the serving source), and
 * every rule is honoured by the paper. The BA example: the subject takes exactly 3 questions of A and the first group
 * at least 2 of them; B may only appear in the last group; the middle group takes questions of C only. The stock is
 * mixed so that each rule is the only thing keeping the paper right: A and B are the bulk of the bank.
 */
class PaperGenerationMapidIT extends PaperGenerationBaseIT {
    private static final int SEEDS = 4;

    @Autowired CatalogLookup catalog;

    @Test
    void exactSubjectCountGroupMinimumOnlyInGroupAndSourceHoldThroughThePort() throws Exception {
        Subject subject = newSubject();
        List<UUID> nodes = unrelatedKnowledgeNodes(3);
        UUID a = nodes.get(0);
        UUID b = nodes.get(1);
        UUID c = nodes.get(2);
        Set<UUID> carriersOfA = tagged(singles(subject, 30), a);
        Set<UUID> carriersOfB = tagged(singles(subject, 30), b);
        Set<UUID> carriersOfC = tagged(singles(subject, 8), c);
        singles(subject, 12);

        Published published = publishStructure(null, section(subject, a, b, c));

        // The published snapshot keeps the declared mode and every rule (EX-1 publish, EX-2 reads this snapshot).
        JsonNode section = mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = ?", String.class, published.examId(), published.versionNo()))
                .path("parts").get(0).path("sections").get(0);
        assertThat(section.path("mapidMode").asText()).isEqualTo("MATRIX");
        assertThat(section.path("mapidRules")).hasSize(4);

        for (long seed = 1; seed <= SEEDS; seed++) {
            GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), List.of("p1"), List.of(),
                    seed, Set.of());

            List<UUID> all = roots(paper);
            assertThat(all).as("seed %d: 12 distinct questions", seed).hasSize(12).doesNotHaveDuplicates();
            assertThat(count(all, carriersOfA)).as("seed %d: A exactly 3 in the subject", seed).isEqualTo(3);
            assertThat(count(group(paper, "g1"), carriersOfA)).as("seed %d: A at least 2 in g1", seed)
                    .isGreaterThanOrEqualTo(2);
            assertThat(count(group(paper, "g1"), carriersOfB) + count(group(paper, "g2"), carriersOfB))
                    .as("seed %d: B only in g3", seed).isZero();
            assertThat(group(paper, "g2")).as("seed %d: g2 only from C", seed).hasSize(4)
                    .allSatisfy(question -> assertThat(carriersOfC).contains(question));
            assertThat(paper.dropped()).isEmpty();
        }
    }

    private static List<UUID> group(GeneratedPaper paper, String groupId) {
        return paper.parts().stream().flatMap(part -> part.sections().stream())
                .flatMap(section -> section.groups().stream()).filter(group -> group.groupId().equals(groupId))
                .map(GeneratedGroup::questions).findFirst().orElseThrow().stream()
                .map(question -> question.questionId()).toList();
    }

    private static long count(List<UUID> questions, Set<UUID> carriers) {
        return questions.stream().filter(carriers::contains).count();
    }

    /** Marks every question with the knowledge node; returns the marked ids. */
    private Set<UUID> tagged(List<UUID> questions, UUID node) {
        for (UUID question : questions) {
            jdbc.update("INSERT INTO questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) "
                    + "VALUES (?,?,true)", question, node);
        }
        return new HashSet<>(questions);
    }

    /** Knowledge nodes of the seed that share no subtree, so a carrier of one never counts for another. */
    private List<UUID> unrelatedKnowledgeNodes(int wanted) {
        List<UUID> picked = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (CatalogItemView item : catalog.listActiveGroupItems("KNOWLEDGE")) {
            boolean disjoint = paths.stream()
                    .noneMatch(path -> path.startsWith(item.path()) || item.path().startsWith(path));
            if (!disjoint) continue;
            picked.add(item.id());
            paths.add(item.path());
            if (picked.size() == wanted) break;
        }
        assertThat(picked).as("%d unrelated knowledge nodes in the seed", wanted).hasSize(wanted);
        return picked;
    }

    /** One part, one section of 12 single-choice questions in g1 (5), g2 (4) and g3 (3), MATRIX mode with four rules. */
    private static String section(Subject subject, UUID a, UUID b, UUID c) {
        String rules = "["
                + "{\"id\":\"r1\",\"type\":\"COUNT\",\"comparator\":\"EXACT\",\"count\":3,\"nodeIds\":[\"" + a + "\"]},"
                + "{\"id\":\"r2\",\"type\":\"COUNT\",\"groupId\":\"g1\",\"comparator\":\"AT_LEAST\",\"count\":2,"
                + "\"nodeIds\":[\"" + a + "\"]},"
                + "{\"id\":\"r3\",\"type\":\"ONLY_IN_GROUP\",\"groupId\":\"g3\",\"nodeIds\":[\"" + b + "\"]},"
                + "{\"id\":\"r4\",\"type\":\"SOURCE\",\"groupId\":\"g2\",\"nodeIds\":[\"" + c + "\"]}]";
        return "{\"parts\":[{\"id\":\"p1\",\"order\":1,\"name\":\"P1\",\"questionCount\":12,"
                + "\"questionCountOnPaper\":12,\"questionCountRequired\":12,"
                + "\"participation\":{\"mode\":\"ALL\",\"choose\":null},\"sections\":[{\"id\":\"s1\","
                + "\"subjectCode\":\"" + subject.code() + "\",\"questionCount\":12,\"mapidMode\":\"MATRIX\","
                + "\"mapidRules\":" + rules + ",\"blocks\":[" + block("g1", 5) + "," + block("g2", 4) + ","
                + block("g3", 3) + "]}],\"options\":[]}]}";
    }

    private static String block(String id, int count) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"questionType\":\"SINGLE_CHOICE\","
                + "\"questionCount\":" + count + "}";
    }
}
