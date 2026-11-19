package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemShape(Integer statements, Integer leftItems, Integer rightItems, Integer options) {
    public ItemShape(Integer statements, Integer leftItems, Integer rightItems) {
        this(statements, leftItems, rightItems, null);
    }
}
