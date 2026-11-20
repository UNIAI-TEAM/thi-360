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
import vn.unicom.thi360.exam.domain.KnowledgeScope;
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
 * UNI-2758 BA 5 "Chọn 1 chùm từ 3 MAPID": under a SOURCE rule a set qualifies only when ALL its children carry the
 * SAME code of the rule (one node, descendants allowed), not a mix of the rule's codes. The legacy
 * {@code everyChildInScope} keeps its union meaning. Sections are compiled by the real {@link SolverInputBuilder}
 * and served by the real source (SQL).
 */
@Transactional
class MapidSourceSameNodeIT extends AbstractIntegrationTest {
    @Autowired QuestionSourcePort source;
    @Autowired JdbcTemplate jdbc;

    private final UUID subject = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID aChild = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void aGroupSourceKeepsASetOnlyWhenEveryChildCarriesTheSameCode() {
        Sets sets = sets();
        var input = build(MapidMode.MATRIX, List.of(new MapidRule("source", MapidRuleType.SOURCE, "g1",
                List.of(a, b, c), null, null, null)), null);
        assertThat(ids(input, group(input, 0))).containsExactlyInAnyOrder(sets.bbb, sets.aWithDescendant,
                sets.aa, sets.multiTagged);
    }

    @Test
    void aSubjectSourceAppliesTheSameRuleToEverySetGroup() {
        Sets sets = sets();
        var input = build(MapidMode.MATRIX, List.of(new MapidRule("source", MapidRuleType.SOURCE, null,
                List.of(a, b, c), null, null, null)), null);
        assertThat(ids(input, group(input, 0))).containsExactlyInAnyOrder(sets.bbb, sets.aWithDescendant,
                sets.aa, sets.multiTagged);
    }

    @Test
    void theLegacyEveryChildInScopeKeepsItsUnionMeaning() {
        Sets sets = sets();
        var input = build(null, List.of(), new KnowledgeScope(List.of(a, b, c), List.of(), true));
        assertThat(ids(input, group(input, 0))).contains(sets.mixed, sets.bbb, sets.aa)
                .doesNotContain(sets.halfClean);
    }

    private record Sets(UUID mixed, UUID halfClean, UUID bbb, UUID aWithDescendant, UUID aa, UUID multiTagged) { }

    private Sets sets() {
        return new Sets(
                set(List.of(b), List.of(c)),
                set(List.of(b), List.of()),
                set(List.of(b), List.of(b), List.of(b)),
                set(List.of(aChild), List.of(a)),
                set(List.of(a), List.of(a)),
                set(List.of(b, c), List.of(b)));
    }

    private GroupQuota group(SolverInputBuilder.SolverInput input, int index) {
        return ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst().groups().get(index);
    }

    private List<UUID> ids(SolverInputBuilder.SolverInput input, GroupQuota group) {
        return input.candidates().candidatesFor(group).stream().map(PathCandidate::questionId).map(UUID::fromString).toList();
    }

    private SolverInputBuilder.SolverInput build(MapidMode mode, List<MapidRule> rules, KnowledgeScope legacyScope) {
        CatalogLookup catalog = mock(CatalogLookup.class);
        List<CatalogItemView> knowledge = List.of(item(a, "/a/"), item(aChild, "/a/child/"), item(b, "/b/"),
                item(c, "/c/"));
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(knowledge);
        CatalogItemView math = mock(CatalogItemView.class);
        when(math.id()).thenReturn(subject);
        when(catalog.findActiveItem("SUBJECT", "MATH")).thenReturn(Optional.of(math));
        var blocks = List.of(block("g1", legacyScope), block("g2", legacyScope));
        var section = new ExamSection("s", "MATH", 6, null, null, null, null, blocks, List.of(), null, mode, rules);
        var part = new ExamPart("p", 1, "Part", 20, 6, 6, 6, new PartParticipation(ParticipationMode.ALL, null), null,
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

    private static QuestionBlock block(String id, KnowledgeScope scope) {
        return new QuestionBlock(id, id, QuestionType.QUESTION_SET, null, 1, 2, scope, null, null, null, false, null,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
    }

    /** A set whose child {@code i} carries the nodes of {@code children[i]} (the first is the primary one). */
    @SafeVarargs
    private UUID set(List<UUID>... children) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) "
                + "values (?,?,'QUESTION_SET','READY',?,1)", id, "SET-" + id, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", id);
        int position = 0;
        for (List<UUID> nodes : children) {
            UUID child = UUID.randomUUID();
            jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "C-" + child, id, position++);
            boolean primary = true;
            for (UUID node : nodes) {
                jdbc.update("insert into questionbank.question_knowledge values (?,?,?)", child, node, primary);
                primary = false;
            }
        }
        return id;
    }
}
