package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "ExamVersionDetail")
public record ExamVersionDetail(UUID examId, ExamVersionResponse row, JsonNode config, JsonNode parts,
                                List<ExamVersionChange> changes) { }
