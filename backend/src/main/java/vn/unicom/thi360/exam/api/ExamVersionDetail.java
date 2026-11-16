package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;

@Schema(name = "ExamVersionDetail")
public record ExamVersionDetail(UUID examId, ExamVersionResponse row, JsonNode config,
                                @ArraySchema(schema = @Schema(implementation = ExamPart.class)) List<JsonNode> parts,
                                List<ExamVersionChange> changes) { }
