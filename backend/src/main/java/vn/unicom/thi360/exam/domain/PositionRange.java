package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PositionRange(Integer from, Integer to) {
}
