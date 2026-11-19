package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.CompiledMapidRules.Cap;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.TopicQuota;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class MapidRuleSolverInputTest {
    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID LEGACY = UUID.randomUUID();
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final QuestionSourcePort source = mock(QuestionSourcePort.class);

    @Test
    void matrixRulesReplaceLegacyScopeAndQuotasInTheSolverInput() {
        var input = build(section(MapidMode.MATRIX, List.of(
                new MapidRule("src", MapidRuleType.SOURCE, "g", List.of(ROOT), null, null, null),
                new MapidRule("cnt", MapidRuleType.COUNT, null, List.of(ROOT), MapidComparator.AT_LEAST, 2, null))));
        var section = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst();
        GroupQuota group = section.groups().getFirst();
        assertThat(group.topicQuotas()).isEmpty();
        assertThat(section.subjectQuota().topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.id()).isEqualTo("cnt");
            assertThat(quota.count()).isEqualTo(2);
            assertThat(quota.nodeIds()).containsExactlyInAnyOrder(ROOT.toString(), CHILD.toString());
        });
        input.candidates().candidatesFor(group);
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).findCandidates(filter.capture(), anyInt());
        assertThat(filter.getValue().knowledgeGroups()).singleElement()
                .satisfies(nodes -> assertThat(nodes).containsExactlyInAnyOrder(ROOT, CHILD));
        assertThat(filter.getValue().everyChildInScope()).isNull();
    }

    @Test
    void allModeReadsTheWholeSubject() {
        var input = build(section(MapidMode.ALL, List.of(
                new MapidRule("src", MapidRuleType.SOURCE, null, List.of(ROOT), null, null, null))));
        var section = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst();
        assertThat(section.subjectQuota().topicQuotas()).isEmpty();
        input.candidates().candidatesFor(section.groups().getFirst());
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).findCandidates(filter.capture(), anyInt());
        assertThat(filter.getValue().knowledgeGroups()).isEmpty();
        assertThat(input.mapid().get("s").caps()).isEmpty();
    }

    @Test
    void sectionsWithoutAModeKeepTheLegacyScopeAndCarryNoCompiledRules() {
        var input = build(section(null, List.of()));
        var section = ((SolvePart.Fixed) input.parts().getFirst()).sections().getFirst();
        assertThat(input.mapid()).isEmpty();
        assertThat(section.subjectQuota().topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.id()).isEqualTo("legacySection"));
        assertThat(section.groups().getFirst().topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.id()).isEqualTo("legacyBlock"));
        input.candidates().candidatesFor(section.groups().getFirst());
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).findCandidates(filter.capture(), anyInt());
        assertThat(filter.getValue().knowledgeGroups()).singleElement()
                .satisfies(nodes -> assertThat(nodes).containsExactly(LEGACY));
    }

    @Test
    void capsCarriedNotYetEnforced() {
        var input = build(section(MapidMode.MATRIX, List.of(
                new MapidRule("exact", MapidRuleType.COUNT, null, List.of(ROOT), MapidComparator.EXACT, 3, null),
                new MapidRule("only", MapidRuleType.ONLY_IN_GROUP, "g", List.of(CHILD), null, null, null))));
        var compiled = input.mapid().get("s");
        assertThat(compiled.caps()).containsExactly(new Cap("exact", Cap.Scope.SUBJECT, null, List.of(ROOT), 3));
        assertThat(compiled.exclusions()).isEmpty();
        assertThat(input.withParts(input.parts()).mapid()).isEqualTo(input.mapid());
    }

    private SolverInputBuilder.SolverInput build(ExamSection section) {
        CatalogItemView root = mock(CatalogItemView.class);
        CatalogItemView child = mock(CatalogItemView.class);
        CatalogItemView legacy = mock(CatalogItemView.class);
        CatalogItemView subject = mock(CatalogItemView.class);
        when(root.id()).thenReturn(ROOT);
        when(root.path()).thenReturn("/root/");
        when(child.id()).thenReturn(CHILD);
        when(child.path()).thenReturn("/root/child/");
        when(legacy.id()).thenReturn(LEGACY);
        when(legacy.path()).thenReturn("/legacy/");
        when(subject.id()).thenReturn(UUID.randomUUID());
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(root, child, legacy));
        when(catalog.findActiveItem("SUBJECT", "MATH")).thenReturn(Optional.of(subject));
        when(source.findCandidates(any(), anyInt())).thenReturn(List.of());
        var part = new ExamPart("p", 1, "Part", 20, 4, 4, 4, new PartParticipation(ParticipationMode.ALL, null), null,
                List.of(section), List.of());
        return new SolverInputBuilder(catalog, source, SolverLimits.defaults())
                .build(UUID.randomUUID(), new ExamStructureDocument(List.of(part)));
    }

    /** One group of 4 single questions; legacy scope and a topic quota on the section and on the group. */
    private static ExamSection section(MapidMode mode, List<MapidRule> rules) {
        var block = new QuestionBlock("g", "group", QuestionType.SHORT_ANSWER, 4, null, null,
                new KnowledgeScope(List.of(LEGACY), List.of()), null, null, null, false, BigDecimal.ONE,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false,
                List.of(quota("legacyBlock")), null);
        return new ExamSection("s", "MATH", 4, new KnowledgeScope(List.of(LEGACY), List.of()), null, null, null,
                List.of(block), List.of(quota("legacySection")), null, mode, rules);
    }

    private static TopicQuota quota(String id) {
        return new TopicQuota(id, id, List.of(LEGACY), 1, QuotaBasis.QUESTION, QuotaEnforcement.REQUIRED,
                List.of(), List.of(), List.of());
    }
}
