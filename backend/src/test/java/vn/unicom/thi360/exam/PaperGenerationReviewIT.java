package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;

/** Review exams (ADD H29): with {@code everyChildInScope} every child of a picked cluster is in the knowledge scope. */
class PaperGenerationReviewIT extends PaperGenerationBaseIT {
    private static final UUID SCOPE_NODE = UUID.fromString("00000000-0000-7000-8200-000000001201");

    @Test
    void everyChildStaysInScope() throws Exception {
        // The review templates ship with the flag on (ADD H29).
        assertThat(jdbc.queryForObject("SELECT structure #>> '{parts,0,sections,0,knowledgeScope,everyChildInScope}' "
                + "FROM exam.exam_template WHERE code = 'VACT_REVIEW'", String.class)).isEqualTo("true");

        UUID outside = jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g "
                + "ON g.id = i.group_id WHERE g.code = 'KNOWLEDGE' AND i.status = 1 AND i.id <> ? "
                + "AND i.path NOT LIKE (SELECT path FROM catalog.catalog_item WHERE id = ?) || '%' "
                + "AND (SELECT path FROM catalog.catalog_item WHERE id = ?) NOT LIKE i.path || '%' LIMIT 1",
                UUID.class, SCOPE_NODE, SCOPE_NODE, SCOPE_NODE);
        Subject subject = newSubject();
        // Five clusters wholly in the scope, five with one child outside it. Every cluster has a child inside.
        List<UUID> whole = sets(subject, 5, 3);
        List<UUID> mixed = sets(subject, 5, 3);
        whole.forEach(root -> children(root).forEach(child -> knowledge(child, SCOPE_NODE)));
        mixed.forEach(root -> {
            List<UUID> children = children(root);
            knowledge(children.get(0), SCOPE_NODE);
            knowledge(children.get(1), SCOPE_NODE);
            knowledge(children.get(2), outside);
        });

        // Control: without the flag a cluster with a child in the scope is a candidate, so mixed ones are drawn.
        Published loose = insertVersion(review(subject, false), null);
        Set<UUID> looseDrawn = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            looseDrawn.addAll(roots(port.generate(loose.examId(), 1, List.of("p1"), List.of(), seed, Set.of())));
        }
        assertThat(looseDrawn).containsAnyElementsOf(mixed);

        // With the flag, only whole-in-scope clusters, whatever the seed.
        Published strict = insertVersion(review(subject, true), null);
        for (long seed = 1; seed <= 20; seed++) {
            GeneratedPaper paper = port.generate(strict.examId(), 1, List.of("p1"), List.of(), seed, Set.of());
            List<UUID> roots = roots(paper);
            assertThat(roots).hasSize(2).doesNotHaveDuplicates().doesNotContainAnyElementsOf(mixed);
            for (UUID root : roots) {
                for (UUID child : children(root)) {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_knowledge "
                            + "WHERE question_id = ? AND knowledge_item_id = ?", Long.class, child, SCOPE_NODE))
                            .as("child %s of %s in scope", child, root).isEqualTo(1L);
                }
            }
        }
    }

    private List<UUID> children(UUID root) {
        return new ArrayList<>(jdbc.queryForList("SELECT id FROM questionbank.question WHERE parent_id = ? "
                + "ORDER BY position", UUID.class, root));
    }

    private void knowledge(UUID question, UUID node) {
        jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) "
                + "VALUES (?,?,true)", question, node);
    }

    /** A review of two clusters of three children each, in the knowledge scope of {@link #SCOPE_NODE}. */
    private static String review(Subject subject, boolean everyChildInScope) {
        return "{\"parts\":[{\"id\":\"p1\",\"order\":1,\"name\":\"Review\",\"questionCount\":6,"
                + "\"questionCountOnPaper\":6,\"questionCountRequired\":6,"
                + "\"participation\":{\"mode\":\"ALL\",\"choose\":null},\"sections\":[{\"id\":\"s1\",\"subjectCode\":\""
                + subject.code() + "\",\"questionCount\":6,\"knowledgeScope\":{\"nodeIds\":[\"" + SCOPE_NODE
                + "\"],\"competencyCodes\":[],\"everyChildInScope\":" + everyChildInScope + "},"
                + "\"blocks\":[{\"id\":\"b1\",\"questionType\":\"QUESTION_SET\",\"childAnswerType\":\"SINGLE_CHOICE\","
                + "\"setCount\":2,\"setSize\":3,\"questionCount\":6}]}],\"options\":[]}]}";
    }
}
