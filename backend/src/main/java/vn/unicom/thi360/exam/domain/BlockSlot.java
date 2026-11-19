package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockSlot(Integer index, KnowledgeScope knowledgeScope, List<String> tagCodes) {
    public BlockSlot {
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
    }
}
