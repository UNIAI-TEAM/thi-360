package vn.unicom.thi360.questionbank.application;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.questionbank.api.AvailableQuestionCountController;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
@Service
public class AvailableQuestionCountService {
    private final QuestionSourcePort source;
    private final CatalogLookup catalog;

    public AvailableQuestionCountService(QuestionSourcePort source, CatalogLookup catalog) {
        this.source = source;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public AvailableQuestionCountController.Response count(AvailableQuestionCountController.Query query) {
        var subject = catalog.findActiveItem("SUBJECT", query.subjectCode());
        if (subject.isEmpty()) return response(source.countUnion(List.of()));
        return response(source.count(filter(query, subject.get().id())));
    }

    @Transactional(readOnly = true)
    public AvailableQuestionCountController.Response countSubject(AvailableQuestionCountController.SubjectQuery query) {
        var subject = catalog.findActiveItem("SUBJECT", query.subjectCode());
        if (subject.isEmpty()) return response(source.countUnion(List.of()));
        for (var group : query.groups()) {
            if (!query.subjectCode().equals(group.subjectCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group subject differs from subject");
            }
        }
        return response(source.countUnion(query.groups().stream().map(group -> filter(group, subject.get().id())).toList()));
    }

    private QuestionSourcePort.Filter filter(AvailableQuestionCountController.Query query, UUID subjectId) {
        var scope = query.knowledgeScope();
        List<List<UUID>> groups = null;
        List<UUID> excluded = List.of();
        List<List<List<UUID>>> additionalGroups = List.of();
        List<Boolean> additionalSameNode = List.of();
        List<Boolean> additionalEveryChild = List.of();
        boolean scoped = scope != null && scope.nodeIds() != null && !scope.nodeIds().isEmpty();
        boolean excluding = scope != null && scope.excludedNodeIds() != null && !scope.excludedNodeIds().isEmpty();
        boolean grouped = scope != null && scope.nodeGroups() != null && !scope.nodeGroups().isEmpty();
        if (scoped || excluding || grouped) {
            List<CatalogItemView> nodes = catalog.listActiveGroupItems("KNOWLEDGE");
            if (scoped) groups = scope.nodeIds().stream().map(id -> subtree(nodes, id)).toList();
            // The same expansion generation uses (SolverInputBuilder.withDescendants).
            if (excluding) excluded = scope.excludedNodeIds().stream().flatMap(id -> subtree(nodes, id).stream())
                    .distinct().toList();
            if (grouped) {
                // One AND condition per SOURCE rule, A|B|C inside it an OR, exactly as generation builds them.
                additionalGroups = scope.nodeGroups().stream()
                        .map(group -> group.stream().map(id -> subtree(nodes, id)).toList()).toList();
                boolean sameNode = Boolean.TRUE.equals(scope.everyChildSameNode());
                additionalSameNode = java.util.Collections.nCopies(additionalGroups.size(), sameNode);
                additionalEveryChild = java.util.Collections.nCopies(additionalGroups.size(),
                        sameNode || Boolean.TRUE.equals(scope.everyChildInScope()));
            }
        }
        List<String> competencies = query.competencyCodes();
        if (competencies == null && scope != null) competencies = scope.competencyCodes();
        List<QuestionSourcePort.ChildCondition> childConditions = query.childConditions() == null
                ? null : query.childConditions().stream()
                .map(c -> new QuestionSourcePort.ChildCondition(c.from(), c.to(), c.nodeIds(), c.answerType(),
                        itemShape(c.itemShape()))).toList();
        return new QuestionSourcePort.Filter(subjectId, groups, query.answerTypes(), query.questionType(),
                null, query.childAnswerType(), query.tagCodes(), competencies,
                query.requiredPointsPerQuestion(), itemShape(query.itemShape()), query.answerFormats(),
                childConditions, List.of(), additionalGroups, List.of(),
                scope == null ? null : scope.everyChildInScope(), additionalEveryChild,
                query.audioRequirement(), excluded,
                scope == null ? null : scope.everyChildSameNode(), additionalSameNode);
    }

    /** The node and every descendant; an unknown node is empty, as for generation. */
    private static List<UUID> subtree(List<CatalogItemView> nodes, UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst()
                .map(root -> nodes.stream().filter(node -> node.path().startsWith(root.path()))
                        .map(CatalogItemView::id).toList())
                .orElse(List.of());
    }

    private static QuestionSourcePort.ItemShape itemShape(AvailableQuestionCountController.ItemShape shape) {
        return shape == null ? null : new QuestionSourcePort.ItemShape(shape.statements(), shape.leftItems(),
                shape.rightItems(), shape.options());
    }

    private AvailableQuestionCountController.Response response(QuestionSourcePort.Counts counts) {
        return new AvailableQuestionCountController.Response(counts.total(), counts.commandCount(),
                counts.byCell().stream().map(c -> new AvailableQuestionCountController.Cell(c.gradeCode(), c.difficultyCode(), c.value())).toList(),
                counts.byAnswerType().stream().map(c -> new AvailableQuestionCountController.AnswerType(c.answerType(), c.value())).toList(),
                counts.byCompetency().stream().map(c -> new AvailableQuestionCountController.Competency(c.competencyCode(), c.value())).toList());
    }

}
