package vn.unicom.thi360.exam.spi;

import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider.ExamUsageRef;

/** Reverse lookup of working exam blocks whose source contains a question. */
public interface ExamUsagePort {
    List<ExamUsageRef> examUsagesOf(UUID questionId);
}
