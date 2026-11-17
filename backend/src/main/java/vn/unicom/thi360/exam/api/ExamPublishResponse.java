package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "ExamPublishResponse")
public record ExamPublishResponse(UUID examId, int version, List<String> publishedPathIds,
                                  List<KeptPath> keptPaths) {
    @Schema(name = "ExamPublishKeptPath")
    public record KeptPath(String pathId, int version) { }
}
