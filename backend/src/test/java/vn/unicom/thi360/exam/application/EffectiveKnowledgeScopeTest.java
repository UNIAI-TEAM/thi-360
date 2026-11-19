package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.KnowledgeScope;

class EffectiveKnowledgeScopeTest {
    private static final UUID SECTION_NODE = UUID.randomUUID();
    private static final UUID BLOCK_NODE = UUID.randomUUID();
    private static final KnowledgeScope SECTION =
            new KnowledgeScope(List.of(SECTION_NODE), List.of("SEC"), true);

    @Test
    void nullBlockScopeIsTheSectionScope() {
        assertThat(EffectiveKnowledgeScope.of(null, SECTION)).isSameAs(SECTION);
    }

    @Test
    void emptyNodeIdsKeepTheSectionNodesAndTakeTheBlockCompetencies() {
        var effective = EffectiveKnowledgeScope.of(new KnowledgeScope(List.of(), List.of("BLK"), false), SECTION);
        assertThat(effective.nodeIds()).containsExactly(SECTION_NODE);
        assertThat(effective.everyChildInScope()).isTrue();
        assertThat(effective.competencyCodes()).containsExactly("BLK");
    }

    @Test
    void ownNodeIdsReplaceTheSectionNodesAndKeepTheirChildRule() {
        var effective = EffectiveKnowledgeScope.of(
                new KnowledgeScope(List.of(BLOCK_NODE), List.of("BLK"), false), SECTION);
        assertThat(effective.nodeIds()).containsExactly(BLOCK_NODE);
        assertThat(effective.everyChildInScope()).isFalse();
        assertThat(effective.competencyCodes()).containsExactly("BLK");
    }

    @Test
    void ownNodeIdsWithoutCompetenciesInheritTheSectionCompetencies() {
        var effective = EffectiveKnowledgeScope.of(new KnowledgeScope(List.of(BLOCK_NODE), List.of()), SECTION);
        assertThat(effective.nodeIds()).containsExactly(BLOCK_NODE);
        assertThat(effective.competencyCodes()).containsExactly("SEC");
    }

    @Test
    void bothEmptyBlockScopeIsTheSectionScope() {
        var effective = EffectiveKnowledgeScope.of(new KnowledgeScope(List.of(), List.of()), SECTION);
        assertThat(effective.nodeIds()).containsExactly(SECTION_NODE);
        assertThat(effective.competencyCodes()).containsExactly("SEC");
        assertThat(effective.everyChildInScope()).isTrue();
    }

    @Test
    void noSectionScopeLeavesTheBlockScopeAsIs() {
        var block = new KnowledgeScope(List.of(), List.of("BLK"));
        assertThat(EffectiveKnowledgeScope.of(block, null)).isSameAs(block);
        assertThat(EffectiveKnowledgeScope.of(null, null)).isNull();
    }

    @Test
    void nodesAreInheritedWhenTheBlockHasNoScopeOrNoNodeIds() {
        assertThat(EffectiveKnowledgeScope.inheritsNodes(null)).isTrue();
        assertThat(EffectiveKnowledgeScope.inheritsNodes(new KnowledgeScope(List.of(), List.of("BLK")))).isTrue();
        assertThat(EffectiveKnowledgeScope.inheritsNodes(new KnowledgeScope(List.of(BLOCK_NODE), List.of()))).isFalse();
    }
}
