package vn.unicom.thi360.questionbank.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.questionbank.api.AvailableQuestionCountController.KnowledgeScope;
import vn.unicom.thi360.questionbank.api.AvailableQuestionCountController.Query;
import vn.unicom.thi360.questionbank.api.AvailableQuestionCountController.SubjectQuery;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * UNI-2758: the structure screen counts a group with the same exclusions generation applies (ONLY_IN_GROUP of
 * another group), so its source counts match what the solver can pick.
 */
class AvailableQuestionCountServiceTest {
    private static final UUID SUBJECT = UUID.randomUUID();
    private static final UUID SCOPE = UUID.randomUUID();
    private static final UUID EXCLUDED = UUID.randomUUID();
    private static final UUID EXCLUDED_CHILD = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final CatalogLookup catalog = mock(CatalogLookup.class);
    private final AvailableQuestionCountService service = new AvailableQuestionCountService(source, catalog);

    AvailableQuestionCountServiceTest() {
        var math = mock(CatalogItemView.class);
        when(math.id()).thenReturn(SUBJECT);
        when(catalog.findActiveItem("SUBJECT", "MATH")).thenReturn(Optional.of(math));
        var knowledge = List.of(item(SCOPE, "/s/"), item(EXCLUDED, "/x/"), item(EXCLUDED_CHILD, "/x/child/"),
                item(OTHER, "/o/"));
        when(catalog.listActiveGroupItems("KNOWLEDGE")).thenReturn(knowledge);
        when(source.count(any())).thenReturn(new QuestionSourcePort.Counts(0, List.of(), List.of(), List.of()));
        when(source.countUnion(any())).thenReturn(new QuestionSourcePort.Counts(0, List.of(), List.of(), List.of()));
    }

    @Test
    void excludedNodesAndTheirDescendantsReachTheSourceFilter() {
        service.count(query(scope(List.of(SCOPE), List.of(EXCLUDED))));
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).count(filter.capture());
        assertThat(filter.getValue().excludedKnowledge()).containsExactlyInAnyOrder(EXCLUDED, EXCLUDED_CHILD);
        assertThat(filter.getValue().knowledgeGroups()).singleElement()
                .satisfies(group -> assertThat(group).containsExactly(SCOPE));
    }

    @Test
    void theSubjectUnionCountsEveryGroupWithItsOwnExclusions() {
        service.countSubject(new SubjectQuery("MATH", List.of(
                query(scope(List.of(SCOPE), List.of(EXCLUDED))),
                query(scope(List.of(SCOPE), List.of(OTHER))))));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionSourcePort.Filter>> filters = ArgumentCaptor.forClass(List.class);
        verify(source).countUnion(filters.capture());
        assertThat(filters.getValue()).extracting(QuestionSourcePort.Filter::excludedKnowledge).containsExactly(
                List.of(EXCLUDED, EXCLUDED_CHILD), List.of(OTHER));
    }

    @Test
    void aScopeWithoutExclusionsFiltersNothingOut() {
        service.count(query(scope(List.of(SCOPE), List.of())));
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).count(filter.capture());
        assertThat(filter.getValue().excludedKnowledge()).isEmpty();
    }

    @Test
    void theSameNodeSourceModeOfTheScopeReachesTheSourceFilter() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var scope = json.readValue("{\"nodeIds\":[\"" + SCOPE + "\"],\"everyChildInScope\":true,"
                + "\"everyChildSameNode\":true}", KnowledgeScope.class);
        service.count(query(scope));
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).count(filter.capture());
        // Read by name: the flag is the filter mode of a SOURCE rule (all children carry the same node).
        var component = java.util.Arrays.stream(QuestionSourcePort.Filter.class.getRecordComponents())
                .filter(c -> c.getName().equals("everyChildSameNode")).findFirst().orElse(null);
        assertThat(component).as("Filter.everyChildSameNode").isNotNull();
        assertThat(component.getAccessor().invoke(filter.getValue())).isEqualTo(true);
    }

    @Test
    void nodeGroupsAreAnAndOfOrGroupsAndTheSameNodeModeAppliesToEachGroup() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var scope = json.readValue("{\"nodeGroups\":[[\"" + SCOPE + "\",\"" + OTHER + "\"],[\"" + EXCLUDED + "\"]],"
                + "\"everyChildSameNode\":true}", KnowledgeScope.class);
        service.count(query(scope));
        var filter = ArgumentCaptor.forClass(QuestionSourcePort.Filter.class);
        verify(source).count(filter.capture());
        // one AND condition per SOURCE rule; A|B inside a rule is an OR of two subtrees
        assertThat(filter.getValue().additionalKnowledgeGroups()).containsExactly(
                List.of(List.of(SCOPE), List.of(OTHER)), List.of(List.of(EXCLUDED, EXCLUDED_CHILD)));
        assertThat(filter.getValue().additionalEveryChildSameNode()).containsExactly(true, true);
        assertThat(filter.getValue().knowledgeGroups()).isNullOrEmpty();
    }

    /** Read from JSON, as the web sends it: {@code excludedNodeIds} is the new, optional field of the scope. */
    private static KnowledgeScope scope(List<UUID> nodeIds, List<UUID> excluded) {
        try {
            var json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var node = json.createObjectNode();
            node.set("nodeIds", json.valueToTree(nodeIds));
            if (!excluded.isEmpty()) node.set("excludedNodeIds", json.valueToTree(excluded));
            return json.treeToValue(node, KnowledgeScope.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Query query(KnowledgeScope scope) {
        return new Query("MATH", QuestionType.SINGLE_CHOICE, null, null, scope, null, null, null, null, null, null,
                null);
    }

    private static CatalogItemView item(UUID id, String path) {
        var item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(id);
        when(item.path()).thenReturn(path);
        return item;
    }
}
