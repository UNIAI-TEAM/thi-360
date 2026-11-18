package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Điều kiện của các câu con ở vị trí {@code from}..{@code to} trong chùm. {@code answerType} và
 * {@code itemShape} cho chùm trộn dạng (spec H39); khi có {@code answerType}, {@code nodeIds} được rỗng.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChildCondition(Integer from, Integer to, List<UUID> nodeIds, QuestionType answerType,
        ItemShape itemShape) {
    public ChildCondition {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
    }

    public ChildCondition(Integer from, Integer to, List<UUID> nodeIds) {
        this(from, to, nodeIds, null, null);
    }
}
