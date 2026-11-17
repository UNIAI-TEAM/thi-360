package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Builds the stable issue shape shared by all check groups. */
final class ExamCheckIssues {
    static final List<String> GROUPS = List.of("INFO", "STRUCTURE", "MATRIX", "SOURCE",
            "QUESTION_SET", "OVERLAP", "POINTS", "DISPLAY");
    private static final List<String> FIELDS = List.of("partId", "partName", "sectionId", "subjectCode",
            "blockId", "blockName", "optionId", "gradeCode", "difficultyCode", "dimension",
            "competencyCode", "answerType", "subjectCount", "required", "available",
            "timeLimitSeconds", "questionCodes");
    private static final List<String> KEY_FIELDS = List.of("optionId", "gradeCode", "difficultyCode",
            "dimension", "competencyCode", "answerType", "subjectCount");
    private final ObjectMapper mapper;

    ExamCheckIssues(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode create(String code, String group, String severity) {
        ObjectNode issue = mapper.createObjectNode();
        issue.put("code", code);
        issue.put("severity", severity);
        issue.put("group", group);
        FIELDS.forEach(issue::putNull);
        return issue;
    }

    ObjectNode key(ObjectNode issue) {
        String context = text(issue, "blockId");
        if (context.isEmpty()) context = text(issue, "sectionId");
        if (context.isEmpty()) context = text(issue, "partId");
        if (context.isEmpty()) context = "exam";
        StringBuilder key = new StringBuilder(issue.path("code").asText()).append('|').append(context);
        for (String field : KEY_FIELDS) key.append('|').append(text(issue, field));
        issue.put("key", key.toString());
        return issue;
    }

    private static String text(ObjectNode issue, String field) {
        return issue.path(field).isNull() || issue.path(field).isMissingNode() ? "" : issue.path(field).asText();
    }
}
