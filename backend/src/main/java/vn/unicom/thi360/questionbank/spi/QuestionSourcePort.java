package vn.unicom.thi360.questionbank.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Serving question source for exam checks, preview and publication. */
public interface QuestionSourcePort {
    int MAX_SET_CHILDREN = 20;

    /** Each knowledge subtree contains a selected node and its descendants; additional groups are ANDed. */
    record Filter(
            UUID subjectId,
            List<List<UUID>> knowledgeGroups,
            List<QuestionType> answerTypes,
            QuestionType questionType,
            String taskTypeCode,
            QuestionType childAnswerType,
            List<String> tagCodes,
            List<String> competencyCodes,
            BigDecimal requiredPointsPerQuestion,
            ItemShape itemShape,
            List<AnswerFormat> answerFormats,
            List<ChildCondition> childConditions,
            List<List<String>> additionalTagGroups,
            List<List<List<UUID>>> additionalKnowledgeGroups,
            List<List<String>> additionalCompetencyGroups,
            Boolean everyChildInScope) {
        public Filter(UUID subjectId, List<List<UUID>> knowledgeGroups, List<QuestionType> answerTypes,
                QuestionType questionType, String taskTypeCode, QuestionType childAnswerType,
                List<String> tagCodes, List<String> competencyCodes, BigDecimal requiredPointsPerQuestion,
                ItemShape itemShape, List<AnswerFormat> answerFormats, List<ChildCondition> childConditions,
                List<List<String>> additionalTagGroups, List<List<List<UUID>>> additionalKnowledgeGroups,
                List<List<String>> additionalCompetencyGroups) {
            this(subjectId, knowledgeGroups, answerTypes, questionType, taskTypeCode, childAnswerType,
                    tagCodes, competencyCodes, requiredPointsPerQuestion, itemShape, answerFormats,
                    childConditions, additionalTagGroups, additionalKnowledgeGroups, additionalCompetencyGroups, null);
        }
        public Filter(UUID subjectId, List<List<UUID>> knowledgeGroups, List<QuestionType> answerTypes,
                QuestionType questionType, String taskTypeCode, QuestionType childAnswerType,
                List<String> tagCodes, List<String> competencyCodes, BigDecimal requiredPointsPerQuestion,
                ItemShape itemShape, List<AnswerFormat> answerFormats, List<ChildCondition> childConditions,
                Boolean everyChildInScope) {
            this(subjectId, knowledgeGroups, answerTypes, questionType, taskTypeCode, childAnswerType,
                    tagCodes, competencyCodes, requiredPointsPerQuestion, itemShape, answerFormats,
                    childConditions, List.of(), List.of(), List.of(), everyChildInScope);
        }
        public Filter(UUID subjectId, List<List<UUID>> knowledgeGroups, List<QuestionType> answerTypes,
                QuestionType questionType, String taskTypeCode, QuestionType childAnswerType,
                List<String> tagCodes, List<String> competencyCodes, BigDecimal requiredPointsPerQuestion,
                ItemShape itemShape, List<AnswerFormat> answerFormats, List<ChildCondition> childConditions) {
            this(subjectId, knowledgeGroups, answerTypes, questionType, taskTypeCode, childAnswerType,
                    tagCodes, competencyCodes, requiredPointsPerQuestion, itemShape, answerFormats,
                    childConditions, List.of(), List.of(), List.of());
        }
    }

    record ItemShape(Integer statements, Integer leftItems, Integer rightItems) {}
    record ChildCondition(int from, int to, List<UUID> nodeIds) {
        public ChildCondition {
            if (from < 1 || to < from || to > MAX_SET_CHILDREN) {
                throw new IllegalArgumentException("Child positions must be an ordered range within 1.." + MAX_SET_CHILDREN);
            }
            nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        }
    }


    record CellCount(String gradeCode, String difficultyCode, long value) {}
    record AnswerTypeCount(QuestionType answerType, long value) {}
    record CompetencyCount(String competencyCode, long value) {}
    record Counts(long total, long commandCount, List<CellCount> byCell, List<AnswerTypeCount> byAnswerType,
                  List<CompetencyCount> byCompetency) {
        public Counts(long total, List<CellCount> byCell, List<AnswerTypeCount> byAnswerType,
                      List<CompetencyCount> byCompetency) {
            this(total, total, byCell, byAnswerType, byCompetency);
        }
    }

    /** Scoring information from the serving snapshot; null fields do not apply to that type. */
    record ScoringDetails(String trueFalseMethod, List<BigDecimal> trueFalseTable,
                          String multipleChoiceRule) {
        public ScoringDetails {
            trueFalseTable = trueFalseTable == null ? null : List.copyOf(trueFalseTable);
        }
    }

    record Unit(UUID questionId, String reference, QuestionType answerType, String gradeCode,
                String difficultyCode, List<UUID> mapidIds, List<String> competencyCodes,
                BigDecimal points, ScoringDetails scoring, String code, int commandCount,
                Integer statementCount, Integer leftCount, Integer rightCount,
                AnswerFormat answerFormat, boolean hasAttachment) {
        public Unit(UUID questionId, String reference, QuestionType answerType, String gradeCode,
                    String difficultyCode, List<UUID> mapidIds, List<String> competencyCodes,
                    BigDecimal points, ScoringDetails scoring, String code) {
            this(questionId, reference, answerType, gradeCode, difficultyCode, mapidIds, competencyCodes,
                    points, scoring, code, 1, null, null, null, null, false);
        }

        public Unit(UUID questionId, String reference, QuestionType answerType, String gradeCode,
                    String difficultyCode, List<UUID> mapidIds, List<String> competencyCodes,
                    BigDecimal points, ScoringDetails scoring) {
            this(questionId, reference, answerType, gradeCode, difficultyCode, mapidIds, competencyCodes,
                    points, scoring, null);
        }

        public Unit(UUID questionId, String reference, QuestionType answerType, String gradeCode,
                    String difficultyCode, List<UUID> mapidIds, List<String> competencyCodes, BigDecimal points) {
            this(questionId, reference, answerType, gradeCode, difficultyCode, mapidIds, competencyCodes,
                    points, null, null);
        }
    }

    record Candidate(UUID questionId, int versionNo, UUID setId, List<Unit> units,
                     String taskTypeCode, String scoringRule, String trueFalseMethod,
                     List<BigDecimal> trueFalseTable, String multipleChoiceRule, String code) {
        public Candidate {
            trueFalseTable = trueFalseTable == null ? null : List.copyOf(trueFalseTable);
        }
        public Candidate(UUID questionId, int versionNo, UUID setId, List<Unit> units,
                         String taskTypeCode, String scoringRule, String trueFalseMethod,
                         List<BigDecimal> trueFalseTable, String multipleChoiceRule) {
            this(questionId, versionNo, setId, units, taskTypeCode, scoringRule, trueFalseMethod,
                    trueFalseTable, multipleChoiceRule, null);
        }

        public Candidate(UUID questionId, int versionNo, UUID setId, List<Unit> units,
                         String taskTypeCode, String scoringRule) {
            this(questionId, versionNo, setId, units, taskTypeCode, scoringRule, null, null, null, null);
        }
    }
    record VersionKey(UUID questionId, int versionNo) {}
    /** Snapshot and stable question codes, including children of a question set. */
    record Version(VersionKey key, JsonNode snapshot, String code, Map<UUID, String> childCodes) {
        public Version {
            childCodes = childCodes == null ? Map.of() : Map.copyOf(childCodes);
        }

        public Version(VersionKey key, JsonNode snapshot) {
            this(key, snapshot, null, Map.of());
        }
    }

    /** Coarse serving-source metadata; children describe their serving root and retain their parent ID. */
    record Description(UUID questionId, UUID parentId, UUID subjectId, QuestionType questionType,
                       List<QuestionType> answerTypes, List<UUID> knowledgeIds) {}

    /** Only READY, non-deleted questions with a serving root/version are returned. */
    Map<UUID, Description> describe(Collection<UUID> questionIds);

    Counts count(Filter filter);

    /** Union of sources, counting each question and each of its units once. */
    Counts countUnion(List<Filter> filters);

    List<Candidate> findCandidates(Filter filter, int limit);

    /** Requested root or child IDs belonging to candidates selected by the filter. */
    Set<UUID> matching(Filter filter, Collection<UUID> questionIds);

    List<Version> loadVersions(List<VersionKey> versions);

    Map<UUID, Long> sourceRevisions(Collection<UUID> subjectIds);

    /** Lock question rows in ascending ID order and record usage in the caller's transaction. */
    void recordUsage(UUID examId, QuestionUsageKind kind, List<VersionKey> versions);
}
