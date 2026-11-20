package vn.unicom.thi360.exam.domain;

/**
 * The columns of an exam template that the exam check reads, without its structure JSON: which template decides
 * publishing and which locks the template turns on (FU-BE15-R1-5).
 */
public record ExamTemplateCheckFacts(String code, boolean defaultTemplate, String partCode, Integer structureYear,
                                     boolean publishable, boolean fixedCounts, boolean fixedBlockCounts,
                                     boolean omittedLocked) {
}
