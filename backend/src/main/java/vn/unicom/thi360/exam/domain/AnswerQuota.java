package vn.unicom.thi360.exam.domain;

import java.math.BigDecimal;
import vn.unicom.thi360.questionbank.spi.QuestionType;

public record AnswerQuota(QuestionType answerType, BigDecimal value) {
}
