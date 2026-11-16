package vn.unicom.thi360.questionbank.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serving question source for exam checks, preview and publication. */
public interface QuestionSourcePort {
    /** Each inner knowledge group contains one selected node and its active descendants. */
    record Filter(
            UUID subjectId,
            List<List<UUID>> knowledgeGroups,
            Match knowledgeMatch,
            List<QuestionType> answerTypes,
            QuestionType questionType,
            String taskTypeCode,
            QuestionType childAnswerType,
            List<String> tagCodes,
            List<String> competencyCodes,
            BigDecimal requiredPointsPerQuestion) {}

    enum Match { ANY, ALL }

    record CellCount(String gradeCode, String difficultyCode, long value) {}
    record AnswerTypeCount(QuestionType answerType, long value) {}
    record CompetencyCount(String competencyCode, long value) {}
    record Counts(long total, List<CellCount> byCell, List<AnswerTypeCount> byAnswerType,
                  List<CompetencyCount> byCompetency) {}

    record Unit(UUID questionId, String reference, QuestionType answerType, String gradeCode,
                String difficultyCode, List<UUID> mapidIds, List<String> competencyCodes,
                BigDecimal points) {}
    record Candidate(UUID questionId, int versionNo, UUID setId, List<Unit> units,
                     String taskTypeCode, String scoringRule) {}
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

    Counts count(Filter filter);

    /** Union of sources, counting each question and each of its units once. */
    Counts countUnion(List<Filter> filters);

    List<Candidate> findCandidates(Filter filter, int limit);

    List<Version> loadVersions(List<VersionKey> versions);

    Map<UUID, Long> sourceRevisions(Collection<UUID> subjectIds);

    /** Lock question rows in ascending ID order and record usage in the caller's transaction. */
    void recordUsage(UUID examId, QuestionUsageKind kind, List<VersionKey> versions);
}
