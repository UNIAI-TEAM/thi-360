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
        QuestionSourcePort.Match match = QuestionSourcePort.Match.ANY;
        if (scope != null && scope.nodeIds() != null && !scope.nodeIds().isEmpty()) {
            List<CatalogItemView> nodes = catalog.listActiveGroupItems("KNOWLEDGE");
            groups = scope.nodeIds().stream().map(id -> {
                var root = nodes.stream().filter(node -> node.id().equals(id)).findFirst();
                return root.map(item -> nodes.stream().filter(node -> node.path().startsWith(item.path()))
                        .map(CatalogItemView::id).toList()).orElse(List.of());
            }).toList();
        }
        List<String> competencies = query.competencyCodes();
        if (competencies == null && scope != null) competencies = scope.competencyCodes();
        List<QuestionSourcePort.ChildCondition> childConditions = query.childConditions() == null
                ? null : query.childConditions().stream()
                .map(c -> new QuestionSourcePort.ChildCondition(c.from(), c.to(), c.nodeIds())).toList();
        QuestionSourcePort.ItemShape itemShape = query.itemShape() == null ? null
                : new QuestionSourcePort.ItemShape(query.itemShape().statements(),
                        query.itemShape().leftItems(), query.itemShape().rightItems());
        return new QuestionSourcePort.Filter(subjectId, groups, match, query.answerTypes(), query.questionType(),
                query.taskTypeCode(), query.childAnswerType(), query.tagCodes(), competencies,
                query.requiredPointsPerQuestion(), itemShape, query.answerFormats(), childConditions);
    }

    private AvailableQuestionCountController.Response response(QuestionSourcePort.Counts counts) {
        return new AvailableQuestionCountController.Response(counts.total(), counts.commandCount(),
                counts.byCell().stream().map(c -> new AvailableQuestionCountController.Cell(c.gradeCode(), c.difficultyCode(), c.value())).toList(),
                counts.byAnswerType().stream().map(c -> new AvailableQuestionCountController.AnswerType(c.answerType(), c.value())).toList(),
                counts.byCompetency().stream().map(c -> new AvailableQuestionCountController.Competency(c.competencyCode(), c.value())).toList());
    }

}
