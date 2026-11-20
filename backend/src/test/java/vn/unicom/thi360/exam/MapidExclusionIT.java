package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * UNI-2758 rule 4 (a MAPID only in one group) down to the SQL: the section is compiled by the real
 * {@link SolverInputBuilder}, the candidates come from the real serving source. A question is dropped from every
 * other group when it, or for a set any child (or the set itself), carries the node or one of its descendants.
 */
@Transactional
class MapidExclusionIT extends AbstractIntegrationTest {
    @Autowired QuestionSourcePort source;
    @Autowired JdbcTemplate jdbc;

    private final UUID subject = UUID.randomUUID();
    private final UUID node = UUID.randomUUID();
    private final UUID descendant = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void onlyInGroupDropsCarriersFromTheOtherGroupsAndKeepsThemInTheTarget() {
        UUID singleX = single(QuestionType.SINGLE_CHOICE, node);
        UUID singleDescendant = single(QuestionType.SINGLE_CHOICE, descendant);
        UUID singleOther = single(QuestionType.SINGLE_CHOICE, other);
        UUID singleClean = single(QuestionType.SINGLE_CHOICE);
        UUID answerX = single(QuestionType.SHORT_ANSWER, node);
        UUID answerClean = single(QuestionType.SHORT_ANSWER);
        UUID setClean = set(List.of(other), List.of());
        UUID setChildDescendant = set(List.of(), List.of(descendant));
        UUID setChildNode = set(List.of(node), List.of(other));
        UUID setRootTag = set(List.of(), List.of());
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", setRootTag, node);

        var input = build(new MapidRule("only", MapidRuleType.ONLY_IN_GROUP, "g1", List.of(node), null, null, null));
        var groups = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst().groups();

        assertThat(ids(input, groups.get(0))).containsExactlyInAnyOrder(answerX, answerClean);
        assertThat(ids(input, groups.get(1))).containsExactlyInAnyOrder(singleOther, singleClean);
        assertThat(ids(input, groups.get(2))).containsExactlyInAnyOrder(setClean);
        assertThat(List.of(singleX, singleDescendant, setChildDescendant, setChildNode, setRootTag)).doesNotContainAnyElementsOf(
                ids(input, groups.get(1)));
    }

    @Test
    void withoutTheRuleEveryGroupKeepsItsCarriers() {
        UUID singleX = single(QuestionType.SINGLE_CHOICE, node);
        UUID singleClean = single(QuestionType.SINGLE_CHOICE);
        UUID setChildNode = set(List.of(node), List.of(other));

        var input = build(new MapidRule("count", MapidRuleType.COUNT, null, List.of(node), MapidComparator.AT_LEAST, 1, null));
        var groups = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst().groups();

        assertThat(ids(input, groups.get(1))).containsExactlyInAnyOrder(singleX, singleClean);
        assertThat(ids(input, groups.get(2))).containsExactlyInAnyOrder(setChildNode);
    }

    private List<UUID> ids(SolverInputBuilder.SolverInput input, GroupQuota group) {
        return input.candidates().candidatesFor(group).stream().map(PathCandidate::questionId).map(UUID::fromString).toList();
    }

    /** g1 SHORT_ANSWER (target), g2 SINGLE_CHOICE, g3 one set of two children; all served by the real source. */
    private SolverInputBuilder.SolverInput build(MapidRule rule) {
        CatalogLookup catalog = mock(CatalogLookup.class);
        List<CatalogItemView> knowledge = List.of(item(node, "/node/"), item(descendant, "/node/child/"),
                item(other, "/other/"));
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(knowledge);
        CatalogItemView math = mock(CatalogItemView.class);
        when(math.id()).thenReturn(subject);
        when(catalog.findActiveItem("SUBJECT", "MATH")).thenReturn(Optional.of(math));
        var blocks = List.of(block("g1", QuestionType.SHORT_ANSWER, 1, null, null),
                block("g2", QuestionType.SINGLE_CHOICE, 1, null, null),
                block("g3", QuestionType.QUESTION_SET, null, 1, 2));
        var section = new ExamSection("s", "MATH", 4, null, null, null, null, blocks, List.of(), null,
                MapidMode.MATRIX, List.of(rule));
        var part = new ExamPart("p", 1, "Part", 20, 4, 4, 4, new PartParticipation(ParticipationMode.ALL, null), null,
                List.of(section), List.of());
        return new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
    }

    private static CatalogItemView item(UUID id, String path) {
        CatalogItemView item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(id);
        when(item.path()).thenReturn(path);
        return item;
    }

    private static QuestionBlock block(String id, QuestionType type, Integer count, Integer setCount, Integer setSize) {
        return new QuestionBlock(id, id, type, count, setCount, setSize, null, null, null, null, false, null,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
    }

    private UUID single(QuestionType type, UUID... nodes) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "values (?,?,?,'READY',?,1,1)", id, "Q-" + id, type.name(), subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", id);
        for (UUID knowledge : nodes) jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", id, knowledge);
        return id;
    }

    /** A two-child set; each child carries the given nodes. */
    private UUID set(List<UUID> firstChild, List<UUID> secondChild) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) "
                + "values (?,?,'QUESTION_SET','READY',?,1)", id, "SET-" + id, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", id);
        int position = 0;
        for (List<UUID> nodes : List.of(firstChild, secondChild)) {
            UUID child = UUID.randomUUID();
            jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "C-" + child, id, position++);
            for (UUID knowledge : nodes) {
                jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", child, knowledge);
            }
        }
        return id;
    }
}
