package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "ExamVersionList")
public record ExamVersionList(UUID examId, WorkingCopy workingCopy,
                              List<ExamVersionChange> changes, List<ExamVersionResponse> items) {
    @Schema(name = "ExamVersionWorkingCopy")
    public record WorkingCopy(PersonRef editedBy, Instant savedAt, Integer baseVersion, String note,
                              ExamCheckSummary check, List<ExamVersionPath> unpublishedPaths) {}
}
