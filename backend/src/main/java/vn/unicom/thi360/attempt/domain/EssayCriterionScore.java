package vn.unicom.thi360.attempt.domain;

import java.math.BigDecimal;

/** Per-criterion manual score stored inside {@code essay_grade.criteria} and {@code attempt_item.grading_detail}. */
public record EssayCriterionScore(String id, BigDecimal points, String comment) {}
