package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.QuotaRef;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Translates proven solver failures without inferring feasibility from aggregate counts. */
final class ExamCheckSolverIssues {
    private final ExamCheckIssues issues;
    private final ExamStructureDocument document;
    private final long timeLimitSeconds;

    ExamCheckSolverIssues(ExamCheckIssues issues, ExamStructureDocument document, long timeLimitSeconds) {
        this.issues = issues;
        this.document = document;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    ObjectNode failure(SolveReason reason, String subjectCode, String groupId, QuotaRef quota,
                       List<SubjectPick> picks) {
        boolean uncertain = reason == SolveReason.DEADLINE_EXCEEDED
                || reason == SolveReason.CANDIDATE_LIMIT_EXCEEDED
                || reason == SolveReason.PATH_LIMIT_EXCEEDED;
        Location where = find(subjectCode, groupId, quota == null ? null : quota.sectionId(), picks);
        String code;
        String group;
        if (uncertain) {
            code = "OVERLAP_UNCONFIRMED";
            group = "OVERLAP";
        } else if (reason == SolveReason.DUPLICATE_GROUP_ID || reason == SolveReason.CHOICE_SUBJECT_LIMIT_EXCEEDED) {
            code = reason == SolveReason.DUPLICATE_GROUP_ID ? "OPTION_EMPTY" : "SUBJECT_CHOICE_COUNTS_INVALID";
            group = "STRUCTURE";
        } else if (quota != null && "ANSWER".equals(quota.kind())) {
            code = "ANSWER_TYPE_SHORTAGE";
            group = "SOURCE";
        } else if (quota != null || reason == SolveReason.SUBJECT_QUOTA_INFEASIBLE
                || reason == SolveReason.NO_JOINT_SOLUTION) {
            code = "SUBJECT_QUOTA_INFEASIBLE";
            group = "SOURCE";
        } else if (where != null && where.block != null && isSet(where.block)) {
            code = "SET_SHORTAGE";
            group = "QUESTION_SET";
        } else {
            code = "CELL_SHORTAGE";
            group = "SOURCE";
        }
        ObjectNode issue = issues.create(code, group, uncertain ? "UNCONFIRMED" : "ERROR");
        if (where != null) {
            issue.put("partId", where.part.id());
            issue.put("partName", where.part.name());
            issue.put("optionId", where.optionId);
            if (where.section != null) {
                issue.put("sectionId", where.section.id());
                issue.put("subjectCode", where.section.subjectCode());
            }
            if (where.block != null) {
                issue.put("blockId", where.block.id());
                issue.put("blockName", where.block.name());
            }
            if (where.block == null && groupId == null && reason == SolveReason.NO_JOINT_SOLUTION
                    && where.section != null && where.section.blocks().size() == 1) {
                QuestionBlock only = where.section.blocks().getFirst();
                issue.put("blockId", only.id());
                issue.put("blockName", only.name());
            }
        }
        if (subjectCode != null) issue.put("subjectCode", subjectCode);
        if (groupId != null) issue.put("blockId", groupId);
        if (uncertain) issue.put("timeLimitSeconds", timeLimitSeconds);
        if (quota != null) {
            switch (quota.kind()) {
                case "ANSWER" -> issue.put("answerType", quota.key());
                case "GRADE" -> { issue.put("dimension", "GRADE"); issue.put("gradeCode", quota.key()); }
                case "DIFFICULTY" -> { issue.put("dimension", "DIFFICULTY"); issue.put("difficultyCode", quota.key()); }
                case "CELL" -> {
                    String[] cell = quota.key().split("/", 2);
                    issue.put("gradeCode", cell[0]);
                    if (cell.length > 1) issue.put("difficultyCode", cell[1]);
                }
                default -> { }
            }
        }
        for (SubjectPick pick : picks) {
            for (int i = 0; i < pick.sections().size(); i++) {
                var section = pick.sections().get(i);
                if ((quota != null && Objects.equals(quota.sectionId(), section.sectionId()))
                        || (subjectCode != null && subjectCode.equals(section.subjectCode()))) {
                    issue.put("subjectCount", pick.counts().get(i));
                    break;
                }
            }
        }
        return issues.key(issue);
    }

    private Location find(String subject, String group, String sectionId, List<SubjectPick> picks) {
        if (sectionId == null && subject != null) {
            sectionId = picks.stream().flatMap(pick -> pick.sections().stream())
                    .filter(section -> subject.equals(section.subjectCode()))
                    .map(section -> section.sectionId()).findFirst().orElse(null);
        }
        for (ExamPart part : document.parts()) {
            Location found = find(part, null, part.sections(), subject, group, sectionId);
            if (found != null) return found;
            for (ExamPartOption option : part.options()) {
                found = find(part, option.id(), option.sections(), subject, group, sectionId);
                if (found != null) return found;
                for (QuestionBlock block : option.blocks()) {
                    if (Objects.equals(block.id(), group)) return new Location(part, null, option.id(), block);
                }
            }
        }
        return null;
    }

    private static Location find(ExamPart part, String option, List<ExamSection> sections,
                                 String subject, String group, String sectionId) {
        for (ExamSection section : sections) {
            if (sectionId != null && !sectionId.equals(section.id())) continue;
            if (subject != null && !subject.equals(section.subjectCode())) continue;
            if (group != null) {
                for (QuestionBlock block : section.blocks()) {
                    if (group.equals(block.id())) return new Location(part, section, option, block);
                }
            } else if (sectionId != null || subject != null) {
                return new Location(part, section, option, null);
            }
        }
        return null;
    }

    private static boolean isSet(QuestionBlock block) {
        return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
    }

    private record Location(ExamPart part, ExamSection section, String optionId, QuestionBlock block) { }
}
