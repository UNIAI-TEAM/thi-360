package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Nút kiến thức (MAPID) gắn với câu; đúng một nút primary khi có ít nhất một")
public record KnowledgeRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID nodeId,
        String code,
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean primary) {}
